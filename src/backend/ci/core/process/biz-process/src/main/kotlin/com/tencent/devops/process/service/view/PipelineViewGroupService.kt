/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.service.view

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.common.api.util.Watcher
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.utils.LogUtils
import com.tencent.devops.model.process.tables.records.TPipelineInfoRecord
import com.tencent.devops.model.process.tables.records.TPipelineViewRecord
import com.tencent.devops.process.constant.PipelineViewType
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.label.PipelineViewDao
import com.tencent.devops.process.dao.label.PipelineViewGroupDao
import com.tencent.devops.process.dao.label.PipelineViewTopDao
import com.tencent.devops.process.engine.dao.PipelineInfoDao
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.classify.PipelineNewViewSummary
import com.tencent.devops.process.pojo.classify.PipelineViewDict
import com.tencent.devops.process.pojo.classify.PipelineViewFilter
import com.tencent.devops.process.pojo.classify.PipelineViewForm
import com.tencent.devops.process.pojo.classify.PipelineViewPreview
import com.tencent.devops.process.service.label.PipelineGroupService
import com.tencent.devops.process.service.view.lock.PipelineViewGroupLock
import org.apache.commons.lang3.StringUtils
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
@SuppressWarnings("LoopWithTooManyJumpStatements")
class PipelineViewGroupService @Autowired constructor(
    private val pipelineViewService: PipelineViewService,
    private val pipelineGroupService: PipelineGroupService,
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineViewDao: PipelineViewDao,
    private val pipelineViewGroupDao: PipelineViewGroupDao,
    private val pipelineViewTopDao: PipelineViewTopDao,
    private val pipelineInfoDao: PipelineInfoDao,
    private val dslContext: DSLContext,
    private val redisOperation: RedisOperation,
    private val objectMapper: ObjectMapper
) {
    fun getViewNameMap(
        projectId: String,
        pipelineIds: MutableSet<String>
    ): Map<String/*pipelineId*/, MutableList<String>/*viewNames*/> {
        val pipelineViewGroups = pipelineViewGroupDao.listByPipelineIds(dslContext, projectId, pipelineIds)
        if (pipelineViewGroups.isEmpty()) {
            return emptyMap()
        }
        val viewIds = pipelineViewGroups.map { it.viewId }.toSet()
        val views = pipelineViewDao.list(dslContext, projectId, viewIds)
        if (viewIds.isEmpty()) {
            return emptyMap()
        }
        val viewId2Name = views.filter { it.isProject }.associate { it.id to it.name }
        val result = mutableMapOf<String, MutableList<String>>()
        for (p in pipelineViewGroups) {
            if (!viewId2Name.containsKey(p.viewId)) {
                continue
            }
            if (!result.containsKey(p.pipelineId)) {
                result[p.pipelineId] = mutableListOf()
            }
            result[p.pipelineId]!!.add(viewId2Name[p.viewId]!!)
        }

        return result
    }

    fun addViewGroup(projectId: String, userId: String, pipelineView: PipelineViewForm): String {
        checkPermission(userId, projectId, pipelineView.projected)
        var viewId = 0L
        dslContext.transaction { t ->
            val context = DSL.using(t)
            viewId = pipelineViewService.addView(userId, projectId, pipelineView, context)
            initViewGroup(
                context = context,
                pipelineView = pipelineView,
                projectId = projectId,
                viewId = viewId,
                userId = userId
            )
        }
        return HashUtil.encodeLongId(viewId)
    }

    fun updateViewGroup(
        projectId: String,
        userId: String,
        viewIdEncode: String,
        pipelineView: PipelineViewForm
    ): Boolean {
        // 获取老视图
        val viewId = HashUtil.decodeIdToLong(viewIdEncode)
        val oldView = pipelineViewDao.get(dslContext, projectId, viewId) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_PIPELINE_VIEW_NOT_FOUND,
            params = arrayOf(viewIdEncode)
        )
        // 校验
        checkPermission(userId, projectId, pipelineView.projected, oldView.createUser)
        if (pipelineView.projected != oldView.isProject) {
            throw ErrorCodeException(
                errorCode = ProcessMessageCode.ERROR_VIEW_GROUP_IS_PROJECT_NO_SAME,
                defaultMessage = "view scope can`t change , user:$userId , view:$viewIdEncode , project:${projectId}"
            )
        }
        // 更新视图
        var result = false
        dslContext.transaction { t ->
            val context = DSL.using(t)
            result = pipelineViewService.updateView(userId, projectId, viewId, pipelineView, context)
            if (result) {
                pipelineViewGroupDao.remove(context, projectId, viewId)
                redisOperation.delete(firstInitMark(projectId, viewId))
                initViewGroup(
                    context = context,
                    pipelineView = pipelineView,
                    projectId = projectId,
                    viewId = viewId,
                    userId = userId
                )
            }
        }
        return result
    }

    fun deleteViewGroup(
        projectId: String,
        userId: String,
        viewIdEncode: String
    ): Boolean {
        val viewId = HashUtil.decodeIdToLong(viewIdEncode)
        val oldView = pipelineViewDao.get(dslContext, projectId, viewId) ?: throw ErrorCodeException(
            errorCode = ProcessMessageCode.ERROR_PIPELINE_VIEW_NOT_FOUND,
            params = arrayOf(viewIdEncode)
        )
        checkPermission(userId, projectId, oldView.isProject, oldView.createUser)
        var result = false
        dslContext.transaction { t ->
            val context = DSL.using(t)
            result = pipelineViewService.deleteView(userId, projectId, viewId)
            if (result) {
                pipelineViewGroupDao.remove(context, projectId, viewId)
            }
        }
        return result
    }

    fun listPipelineIdsByViewId(projectId: String, viewIdEncode: String): List<String> {
        val viewId = HashUtil.decodeIdToLong(viewIdEncode)
        val view = pipelineViewDao.get(dslContext, projectId, viewId)
        if (view == null) {
            logger.warn("null view , project:$projectId , view:$viewId")
            return emptyList()
        }
        val isStatic = view.viewType == PipelineViewType.STATIC
        val pipelineIds = mutableListOf<String>()
        val viewGroups = pipelineViewGroupDao.listByViewId(dslContext, projectId, viewId)
        if (viewGroups.isEmpty()) {
            pipelineIds.addAll(if (isStatic) emptyList() else initDynamicViewGroup(view, view.createUser))
        } else {
            pipelineIds.addAll(viewGroups.map { it.pipelineId }.toList())
        }
        if (pipelineIds.isEmpty()) {
            pipelineIds.add("##NONE##") // 特殊标志,避免有些判空逻辑导致过滤器没有执行
        }
        return pipelineIds
    }

    fun updateGroupAfterPipelineCreate(projectId: String, pipelineId: String, userId: String) {
        PipelineViewGroupLock(redisOperation, projectId).lockAround {
            logger.info("updateGroupAfterPipelineCreate, projectId:$projectId, pipelineId:$pipelineId , userId:$userId")
            val pipelineInfo = pipelineInfoDao.getPipelineId(dslContext, projectId, pipelineId)!!
            val viewGroupCount =
                pipelineViewGroupDao.countByPipelineId(dslContext, pipelineInfo.projectId, pipelineInfo.pipelineId)
            if (viewGroupCount == 0) {
                val dynamicProjectViews =
                    pipelineViewDao.list(dslContext, pipelineInfo.projectId, PipelineViewType.DYNAMIC)
                val matchViewIds = dynamicProjectViews.asSequence()
                    .filter { pipelineViewService.matchView(it, pipelineInfo) }
                    .map { it.id }
                    .toSet()
                matchViewIds.forEach {
                    pipelineViewGroupDao.create(
                        dslContext = dslContext,
                        projectId = projectId,
                        pipelineId = pipelineId,
                        viewId = it,
                        userId = userId
                    )
                }
            }
        }
    }

    fun updateGroupAfterPipelineDelete(projectId: String, pipelineId: String) {
        PipelineViewGroupLock(redisOperation, projectId).lockAround {
            logger.info("updateGroupAfterPipelineDelete, projectId:$projectId, pipelineId:$pipelineId")
            pipelineViewGroupDao.listByPipelineId(dslContext, projectId, pipelineId).forEach {
                pipelineViewGroupDao.remove(dslContext, it.projectId, it.viewId, it.pipelineId)
            }
        }
    }

    fun updateGroupAfterPipelineUpdate(projectId: String, pipelineId: String, userId: String) {
        PipelineViewGroupLock(redisOperation, projectId).lockAround {
            logger.info("updateGroupAfterPipelineUpdate, projectId:$projectId, pipelineId:$pipelineId , userId:$userId")
            val pipelineInfo = pipelineInfoDao.getPipelineId(dslContext, projectId, pipelineId)!!
            // 所有的动态项目组
            val dynamicProjectViews = pipelineViewDao.list(dslContext, pipelineInfo.projectId, PipelineViewType.DYNAMIC)
            val dynamicProjectViewIds = dynamicProjectViews.asSequence()
                .map { it.id }
                .toSet()
            // 命中的动态项目组
            val matchViewIds = dynamicProjectViews.asSequence()
                .filter { pipelineViewService.matchView(it, pipelineInfo) }
                .map { it.id }
                .toSet()
            // 已有的动态项目组
            val baseViewGroups =
                pipelineViewGroupDao.listByPipelineId(dslContext, pipelineInfo.projectId, pipelineInfo.pipelineId)
                    .filter { dynamicProjectViewIds.contains(it.viewId) }
                    .toSet()
            val baseViewIds = baseViewGroups.map { it.viewId }.toSet()
            // 新增新命中的项目组
            matchViewIds.filterNot { baseViewIds.contains(it) }.forEach {
                pipelineViewGroupDao.create(
                    dslContext = dslContext,
                    projectId = projectId,
                    pipelineId = pipelineId,
                    viewId = it,
                    userId = userId
                )
            }
            // 删除未命中的老项目组
            baseViewGroups.filterNot { matchViewIds.contains(it.viewId) }.forEach {
                pipelineViewGroupDao.remove(dslContext, it.projectId, it.viewId, it.pipelineId)
            }
        }
    }

    private fun initViewGroup(
        context: DSLContext,
        pipelineView: PipelineViewForm,
        projectId: String,
        viewId: Long,
        userId: String,
    ) {
        val watcher = Watcher("initViewGroup|$projectId|$viewId|$userId")
        if (pipelineView.viewType == PipelineViewType.DYNAMIC) {
            watcher.start("initDynamicViewGroup")
            initDynamicViewGroup(pipelineViewDao.get(context, projectId, viewId)!!, userId, context)
            watcher.stop()
        } else {
            watcher.start("initStaticViewGroup")
            pipelineView.pipelineIds.forEach {
                pipelineViewGroupDao.create(
                    dslContext = dslContext,
                    projectId = projectId,
                    pipelineId = it,
                    viewId = viewId,
                    userId = userId
                )
            }
            watcher.stop()
        }
        LogUtils.printCostTimeWE(watcher)
    }

    private fun initDynamicViewGroup(
        view: TPipelineViewRecord,
        userId: String,
        context: DSLContext? = null
    ): List<String> {
        val projectId = view.projectId
        return PipelineViewGroupLock(redisOperation, projectId).lockAround {
            val firstInit = redisOperation.setIfAbsent(firstInitMark(projectId, view.id), "1")
            if (!firstInit) {
                return@lockAround emptyList()
            }
            val pipelineIds = allPipelineInfos(projectId)
                .filter { pipelineViewService.matchView(view, it) }
                .map { it.pipelineId }
            pipelineIds.forEach {
                pipelineViewGroupDao.create(
                    dslContext = context ?: dslContext,
                    projectId = projectId,
                    pipelineId = it,
                    viewId = view.id,
                    userId = userId
                )
            }
            return@lockAround pipelineIds
        }
    }

    private fun firstInitMark(
        projectId: String?,
        viewId: Long
    ) = "initDynamicViewGroup:$projectId:$viewId"

    private fun checkPermission(userId: String, projectId: String, isProject: Boolean, creator: String? = null) {
        if (isProject) {
            if (!pipelinePermissionService.checkProjectManager(userId, projectId)) {
                throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_VIEW_GROUP_NO_PERMISSION,
                    defaultMessage = "user:$userId has no permission to edit view group, project:$projectId"
                )
            }
        } else {
            if (creator != null && userId != creator) {
                throw ErrorCodeException(
                    errorCode = ProcessMessageCode.ERROR_DEL_PIPELINE_VIEW_NO_PERM,
                    defaultMessage = "user:$userId has no permission to edit view group, project:$projectId"
                )
            }
        }
    }

    fun preview(
        userId: String,
        projectId: String,
        viewId: String?,
        pipelineView: PipelineViewForm
    ): PipelineViewPreview {
        // 获取所有流水线信息
        val allPipelineInfoMap = allPipelineInfos(projectId).associateBy { it.pipelineId }
        if (allPipelineInfoMap.isEmpty()) {
            return PipelineViewPreview.EMPTY
        }

        //获取老流水线组的流水线
        val oldPipelineIds = if (null == viewId) {
            emptyList<String>()
        } else {
            pipelineViewGroupDao
                .listByViewId(dslContext, projectId, HashUtil.decodeIdToLong(viewId))
                .map { it.pipelineId }
                .filter { allPipelineInfoMap.containsKey(it) }
        }

        // 获取新流水线组的流水线
        val newPipelineIds = if (pipelineView.viewType == PipelineViewType.DYNAMIC) {
            val previewCondition = TPipelineViewRecord()
            previewCondition.logic = pipelineView.logic.name
            previewCondition.filterByPipeineName = StringUtils.EMPTY
            previewCondition.filterByCreator = StringUtils.EMPTY
            previewCondition.filters = objectMapper
                .writerFor(object : TypeReference<List<PipelineViewFilter>>() {})
                .writeValueAsString(pipelineView.filters)
            allPipelineInfoMap.values
                .filter { pipelineViewService.matchView(previewCondition, it) }
                .map { it.pipelineId }
        } else {
            pipelineView.pipelineIds.filter { allPipelineInfoMap.containsKey(it) }
        }

        //新增流水线 = 新流水线 - 老流水线
        val addedPipelineIds = newPipelineIds.filterNot { oldPipelineIds.contains(it) }

        // 移除流水线 = 老流水线 - 新流水线
        val removedPipelineIds = oldPipelineIds.filterNot { newPipelineIds.contains(it) }

        return PipelineViewPreview(addedPipelineIds, removedPipelineIds)
    }

    fun dict(userId: String, projectId: String): PipelineViewDict {
        // 流水线组信息
        val viewInfos = pipelineViewDao.list(dslContext, projectId)
        if (viewInfos.isEmpty()) {
            return PipelineViewDict.EMPTY
        }
        // 流水线组映射关系
        val viewGroups = pipelineViewGroupDao.listByProjectId(dslContext, projectId)
        if (viewGroups.isEmpty()) {
            return PipelineViewDict.EMPTY
        }
        val viewGroupMap = mutableMapOf<Long/*viewId*/, MutableList<String>/*pipelineIds*/>()
        viewGroups.forEach {
            val viewId = it.viewId
            if (!viewGroupMap.containsKey(viewId)) {
                viewGroupMap[viewId] = mutableListOf()
            }
            viewGroupMap[viewId]!!.add(it.pipelineId)
        }
        //流水线信息
        val pipelineInfoMap = allPipelineInfos(projectId).associateBy { it.pipelineId }
        if (pipelineInfoMap.isEmpty()) {
            return PipelineViewDict.EMPTY
        }
        // 拼装返回结果
        val personalViewList = mutableListOf<PipelineViewDict.ViewInfo>()
        val projectViewList = mutableListOf<PipelineViewDict.ViewInfo>()
        for (view in viewInfos) {
            if (!view.isProject && view.createUser != userId) {
                continue
            }
            if (!viewGroupMap.containsKey(view.id)) {
                continue
            }
            val pipelineList = viewGroupMap[view.id]!!.filter { pipelineInfoMap.containsKey(it) }.map {
                val pipelineInfo = pipelineInfoMap[it]!!
                PipelineViewDict.ViewInfo.PipelineInfo(pipelineInfo.pipelineId, pipelineInfo.pipelineName)
            }
            val viewList = if (view.isProject) projectViewList else personalViewList
            viewList.add(
                PipelineViewDict.ViewInfo(
                    viewId = HashUtil.encodeLongId(view.id),
                    viewName = view.name,
                    pipelineList = pipelineList
                )
            )
        }
        return PipelineViewDict(personalViewList, projectViewList)
    }

    private fun allPipelineInfos(projectId: String): List<TPipelineInfoRecord> {
        val pipelineInfos = mutableListOf<TPipelineInfoRecord>()
        val step = 200
        var offset = 0
        var hasNext = true
        while (hasNext) {
            val subPipelineInfos = pipelineInfoDao.listPipelineInfoByProject(
                dslContext = dslContext,
                projectId = projectId,
                offset = offset,
                limit = step
            ) ?: emptyList<TPipelineInfoRecord>()
            if (subPipelineInfos.isEmpty()) {
                break
            }
            pipelineInfos.addAll(subPipelineInfos)
            offset += step
            hasNext = subPipelineInfos.size == step
        }
        return pipelineInfos
    }

    companion object {
        private val logger = LoggerFactory.getLogger(PipelineViewGroupService::class.java)
    }
}
