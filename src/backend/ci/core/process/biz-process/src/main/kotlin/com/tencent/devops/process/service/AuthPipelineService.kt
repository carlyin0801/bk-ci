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

package com.tencent.devops.process.service

import com.tencent.bk.sdk.iam.constants.CallbackMethodEnum
import com.tencent.bk.sdk.iam.dto.callback.request.CallbackRequestDTO
import com.tencent.bk.sdk.iam.dto.callback.response.CallbackBaseResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.FetchInstanceInfoResponseDTO
import com.tencent.bk.sdk.iam.dto.callback.response.InstanceInfoDTO
import com.tencent.bk.sdk.iam.dto.callback.response.ListInstanceResponseDTO
import com.tencent.devops.common.auth.api.AuthTokenApi
import com.tencent.devops.common.auth.callback.FetchInstanceInfo
import com.tencent.devops.common.auth.callback.ListInstanceInfo
import com.tencent.devops.common.auth.callback.SearchInstanceInfo
import com.tencent.devops.process.dao.label.PipelineViewDao
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
@SuppressWarnings("LongParameterList")
class AuthPipelineService @Autowired constructor(
    val authTokenApi: AuthTokenApi,
    val pipelineListFacadeService: PipelineListFacadeService,
    val pipelineViewDao: PipelineViewDao,
    val dslContext: DSLContext
) {
    fun pipelineInfo(
        callBackInfo: CallbackRequestDTO,
        token: String,
        returnPipelineId: Boolean? = false
    ): CallbackBaseResponseDTO? {
        logger.info("iam流水线回调信息:$callBackInfo")
        val method = callBackInfo.method
        val page = callBackInfo.page
        val projectId = callBackInfo.filter.parent?.id ?: "" // FETCH_INSTANCE_INFO场景下iam不会传parentId
        // todo 得区别父类的资源是 流水线组还是项目
        // todo 必须得传递项目id过来，仅仅传递流水线组id，不能查询
        when (method) {
            CallbackMethodEnum.LIST_INSTANCE -> {
                return getPipeline(
                    projectId = projectId,
                    offset = page.offset.toInt(),
                    limit = page.limit.toInt(),
                    token = token,
                    returnPipelineId = returnPipelineId!!
                )
            }
            CallbackMethodEnum.FETCH_INSTANCE_INFO -> {
                val ids = callBackInfo.filter.idList.map { it.toString() }
                return getPipelineInfo(ids, token, returnPipelineId!!)
            }
            CallbackMethodEnum.SEARCH_INSTANCE -> {
                return searchPipeline(
                    projectId = projectId,
                    keyword = callBackInfo.filter.keyword,
                    limit = page.limit.toInt(),
                    offset = page.offset.toInt(),
                    token = token,
                    returnPipelineId = returnPipelineId!!
                )
            }
            else -> {}
        }
        return null
    }

    fun pipelineGroupInfo(
        callBackInfo: CallbackRequestDTO,
        token: String
    ): CallbackBaseResponseDTO? {
        logger.info("iam流水线组回调信息:$callBackInfo")
        val method = callBackInfo.method
        val page = callBackInfo.page
        val projectId = callBackInfo.filter.parent?.id ?: "" // FETCH_INSTANCE_INFO场景下iam不会传parentId
        when (method) {
            CallbackMethodEnum.LIST_INSTANCE -> {
                return getPipelineGroup(
                    projectId = projectId,
                    offset = page.offset.toInt(),
                    limit = page.limit.toInt(),
                    token = token
                )
            }
            CallbackMethodEnum.FETCH_INSTANCE_INFO -> {
                val ids = callBackInfo.filter.idList.map { it.toString().toLong() }
                return getPipelineGroupInfo(ids.toSet(), token)
            }
            CallbackMethodEnum.SEARCH_INSTANCE -> {
                return searchPipelineGroupInfo(
                    projectId = projectId,
                    keyword = callBackInfo.filter.keyword,
                    limit = page.limit.toInt(),
                    offset = page.offset.toInt(),
                    token = token
                )
            }
            else -> {}
        }
        return null
    }

    private fun searchPipeline(
        projectId: String,
        keyword: String,
        limit: Int,
        offset: Int,
        token: String,
        returnPipelineId: Boolean
    ): SearchInstanceInfo {
        authTokenApi.checkToken(token)
        val pipelineInfos = pipelineListFacadeService.searchByPipelineName(
            projectId = projectId,
            pipelineName = keyword,
            limit = limit,
            offset = offset
        )
        val result = SearchInstanceInfo()
        if (pipelineInfos?.records == null) {
            logger.info("$projectId 项目下无流水线")
            return result.buildSearchInstanceFailResult()
        }
        val entityInfo = mutableListOf<InstanceInfoDTO>()
        pipelineInfos?.records?.map {
            val entityId = if (returnPipelineId) {
                it.pipelineId
            } else {
                it.id?.toString() ?: "0"
            }
            val entity = InstanceInfoDTO()
            entity.id = entityId
            entity.displayName = it.pipelineName
            entityInfo.add(entity)
        }
        logger.info("entityInfo $entityInfo, count ${pipelineInfos?.count}")
        return result.buildSearchInstanceResult(entityInfo, pipelineInfos.count)
    }

    private fun getPipeline(
        projectId: String,
        offset: Int,
        limit: Int,
        token: String,
        returnPipelineId: Boolean
    ): ListInstanceResponseDTO {
        authTokenApi.checkToken(token)
        val pipelineInfos = pipelineListFacadeService.getPipelinePage(
            projectId = projectId,
            limit = limit,
            offset = offset
        )
        val result = ListInstanceInfo()
        if (pipelineInfos?.records == null) {
            logger.info("$projectId 项目下无流水线")
            return result.buildListInstanceFailResult()
        }
        val entityInfo = mutableListOf<InstanceInfoDTO>()
        pipelineInfos?.records?.map {
            val entityId = if (returnPipelineId) {
                it.pipelineId
            } else {
                it.id?.toString() ?: "0"
            }
            val entity = InstanceInfoDTO()
            entity.id = entityId
            entity.displayName = it.pipelineName
            entityInfo.add(entity)
        }
        logger.info("entityInfo $entityInfo, count ${pipelineInfos?.count}")
        return result.buildListInstanceResult(entityInfo, pipelineInfos.count)
    }

    private fun getPipelineGroup(
        projectId: String,
        offset: Int,
        limit: Int,
        token: String
    ): ListInstanceResponseDTO {
        authTokenApi.checkToken(token)
        val pipelineGroupList = pipelineViewDao.list(
            dslContext = dslContext,
            projectId = projectId,
            limit = limit,
            offset = offset
        )
        val result = ListInstanceInfo()
        if (pipelineGroupList.isEmpty()) {
            logger.info("$projectId 项目下无流水线组")
            return result.buildListInstanceFailResult()
        }
        val entityInfo = mutableListOf<InstanceInfoDTO>()
        pipelineGroupList.map {
            val entity = InstanceInfoDTO()
            entity.id = it.id.toString()
            entity.displayName = it.name
            entityInfo.add(entity)
        }
        logger.info("entityInfo $entityInfo, count ${entityInfo.size}")
        return result.buildListInstanceResult(entityInfo, entityInfo.size.toLong())

    }

    private fun getPipelineInfo(
        ids: List<Any>?,
        token: String,
        returnPipelineId: Boolean
    ): FetchInstanceInfoResponseDTO {
        authTokenApi.checkToken(token)

        val pipelineId = ids!!.first().toString()
        val idNumType = pipelineId.matches("-?\\d+(\\.\\d+)?".toRegex()) // 判断是否为纯数字

        val pipelineInfos = if (idNumType) {
            // 纯数字按自增id获取
            pipelineListFacadeService.getByAutoIds(ids.map { it.toString().toInt() })
        } else {
            // 非纯数字按pipelineId获取
            pipelineListFacadeService.getByPipelineIds(pipelineIds = ids!!.toSet() as Set<String>)
        }
        val result = FetchInstanceInfo()

        if (pipelineInfos == null || pipelineInfos.isEmpty()) {
            logger.info("$ids 未匹配到启用流水线")
            return result.buildFetchInstanceFailResult()
        }

        val entityInfo = mutableListOf<InstanceInfoDTO>()
        pipelineInfos?.map {
            val entityId = if (returnPipelineId) {
                it.pipelineId
            } else {
                it.id?.toString() ?: "0"
            }
            val entity = InstanceInfoDTO()
            entity.id = entityId
            entity.displayName = it.pipelineName
            entity.iamApprover = arrayListOf(it.createUser)
            entityInfo.add(entity)
        }
        logger.info("entityInfo $entityInfo, count ${pipelineInfos.size.toLong()}")
        return result.buildFetchInstanceResult(entityInfo)
    }

    private fun getPipelineGroupInfo(
        ids: Set<Long>,
        token: String,
    ): FetchInstanceInfoResponseDTO {
        authTokenApi.checkToken(token)
        val pipelineGroupList = pipelineViewDao.list(
            dslContext = dslContext,
            viewIds = ids
        )
        val result = FetchInstanceInfo()
        if (pipelineGroupList.isEmpty()) {
            logger.info("$ids 未匹配到启用流水线组")
            return result.buildFetchInstanceFailResult()
        }

        val entityInfo = mutableListOf<InstanceInfoDTO>()
        pipelineGroupList.map {
            val entity = InstanceInfoDTO()
            entity.id = it.id.toString()
            entity.iamApprover = arrayListOf(it.createUser)
            entity.displayName = it.name
            entityInfo.add(entity)
        }
        logger.info("entityInfo $entityInfo, count ${entityInfo.size.toLong()}")
        return result.buildFetchInstanceResult(entityInfo)
    }

    private fun searchPipelineGroupInfo(
        projectId: String,
        keyword: String,
        limit: Int,
        offset: Int,
        token: String,
    ): SearchInstanceInfo {
        authTokenApi.checkToken(token)
        val pipelineGroupInfo = pipelineViewDao.list(
            dslContext = dslContext,
            projectId = projectId,
            viewName = keyword,
            limit = limit,
            offset = offset
        )
        val result = SearchInstanceInfo()
        if (pipelineGroupInfo.isEmpty()) {
            logger.info("$projectId 项目下无流水线组")
            return result.buildSearchInstanceFailResult()
        }
        val entityInfo = mutableListOf<InstanceInfoDTO>()
        pipelineGroupInfo.map {
            val entity = InstanceInfoDTO()
            entity.id = it.id.toString()
            entity.displayName = it.name
            entityInfo.add(entity)
        }
        logger.info("entityInfo $entityInfo, count ${pipelineGroupInfo.size}")
        return result.buildSearchInstanceResult(entityInfo, pipelineGroupInfo.size.toLong())
    }

    companion object {
        val logger = LoggerFactory.getLogger(AuthPipelineService::class.java)
    }
}
