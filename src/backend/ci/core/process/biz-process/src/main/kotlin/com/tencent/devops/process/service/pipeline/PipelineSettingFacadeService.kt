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

package com.tencent.devops.process.service.pipeline

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.constant.KEY_DEFAULT
import com.tencent.devops.common.api.exception.PermissionForbiddenException
import com.tencent.devops.common.api.pojo.PipelineAsCodeSettings
import com.tencent.devops.common.api.util.MessageUtil
import com.tencent.devops.common.auth.api.AuthPermission
import com.tencent.devops.common.auth.api.AuthResourceType
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.api.service.ServicePipelineResource
import com.tencent.devops.process.audit.service.AuditService
import com.tencent.devops.process.engine.atom.AtomUtils
import com.tencent.devops.process.engine.pojo.event.PipelineUpdateEvent
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import com.tencent.devops.process.permission.PipelinePermissionService
import com.tencent.devops.process.pojo.PipelineDetailInfo
import com.tencent.devops.process.pojo.audit.Audit
import com.tencent.devops.process.pojo.config.JobCommonSettingConfig
import com.tencent.devops.process.pojo.config.PipelineCommonSettingConfig
import com.tencent.devops.process.pojo.config.StageCommonSettingConfig
import com.tencent.devops.process.pojo.config.TaskCommonSettingConfig
import com.tencent.devops.process.pojo.setting.JobCommonSetting
import com.tencent.devops.process.pojo.setting.PipelineCommonSetting
import com.tencent.devops.common.pipeline.pojo.setting.PipelineRunLockType
import com.tencent.devops.common.pipeline.pojo.setting.PipelineSetting
import com.tencent.devops.process.pojo.setting.StageCommonSetting
import com.tencent.devops.common.pipeline.pojo.setting.Subscription
import com.tencent.devops.process.pojo.setting.PipelineSettingVersion
import com.tencent.devops.process.pojo.setting.TaskCommonSetting
import com.tencent.devops.process.pojo.setting.TaskComponentCommonSetting
import com.tencent.devops.process.pojo.setting.UpdatePipelineModelRequest
import com.tencent.devops.process.service.PipelineSettingVersionService
import com.tencent.devops.process.service.label.PipelineGroupService
import com.tencent.devops.process.service.view.PipelineViewGroupService
import com.tencent.devops.process.utils.PipelineVersionUtils
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Suppress("ALL")
@Service
class PipelineSettingFacadeService @Autowired constructor(
    private val pipelinePermissionService: PipelinePermissionService,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val pipelineGroupService: PipelineGroupService,
    private val pipelineSettingVersionService: PipelineSettingVersionService,
    private val pipelineViewGroupService: PipelineViewGroupService,
    private val pipelineCommonSettingConfig: PipelineCommonSettingConfig,
    private val stageCommonSettingConfig: StageCommonSettingConfig,
    private val jobCommonSettingConfig: JobCommonSettingConfig,
    private val taskCommonSettingConfig: TaskCommonSettingConfig,
    private val auditService: AuditService,
    private val client: Client,
    private val pipelineEventDispatcher: PipelineEventDispatcher
) {

    private val logger = LoggerFactory.getLogger(PipelineSettingFacadeService::class.java)

    /**
     * 修改配置时需要返回具体的版本号用于传递
     */
    fun saveSetting(
        userId: String,
        projectId: String,
        pipelineId: String,
        setting: PipelineSetting,
        checkPermission: Boolean = true,
        updateLastModifyUser: Boolean? = true,
        dispatchPipelineUpdateEvent: Boolean = true,
        updateLabels: Boolean = true
    ): PipelineSetting {
        if (checkPermission) {
            val language = I18nUtil.getLanguage(userId)
            val permission = AuthPermission.EDIT
            checkEditPermission(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                message = MessageUtil.getMessageByLocale(
                    CommonMessageCode.USER_NOT_PERMISSIONS_OPERATE_PIPELINE,
                    language,
                    arrayOf(
                        userId,
                        projectId,
                        permission.getI18n(language),
                        pipelineId
                    )
                )
            )
        }
        val settingVersion = pipelineSettingVersionService.getLatestSettingVersion(
            projectId = projectId,
            pipelineId = pipelineId
        )?.let { latest ->
            PipelineVersionUtils.getSettingVersion(
                currVersion = latest.version,
                originSetting = latest,
                newSetting = PipelineSettingVersion(
                    projectId = setting.projectId,
                    pipelineId = setting.pipelineId,
                    pipelineName = setting.pipelineName,
                    desc = setting.desc,
                    runLockType = setting.runLockType,
                    failSubscriptionList = setting.failSubscriptionList,
                    labels = setting.labels,
                    waitQueueTimeMinute = setting.waitQueueTimeMinute,
                    maxQueueSize = setting.maxQueueSize,
                    buildNumRule = setting.buildNumRule,
                    concurrencyCancelInProgress = setting.concurrencyCancelInProgress,
                    concurrencyGroup = setting.concurrencyGroup,
                    pipelineAsCodeSettings = setting.pipelineAsCodeSettings
                )
            )
        } ?: 1

        val pipelineName = pipelineRepositoryService.saveSetting(
            userId = userId,
            setting = setting,
            version = settingVersion,
            updateLastModifyUser = updateLastModifyUser
        )

        if (pipelineName.name != pipelineName.oldName) {
            auditService.createAudit(
                Audit(
                    resourceType = AuthResourceType.PIPELINE_DEFAULT.value,
                    resourceId = setting.pipelineId,
                    resourceName = pipelineName.name,
                    userId = userId,
                    action = "edit",
                    actionContent = "Rename (${pipelineName.oldName})",
                    projectId = setting.projectId
                )
            )

            if (checkPermission) {
                pipelinePermissionService.modifyResource(
                    projectId = setting.projectId,
                    pipelineId = setting.pipelineId,
                    pipelineName = setting.pipelineName
                )
            }
        }

        if (updateLabels) {
            pipelineGroupService.updatePipelineLabel(
                userId = userId,
                projectId = setting.projectId,
                pipelineId = setting.pipelineId,
                labelIds = setting.labels
            )
        }

        // 刷新流水线组
        pipelineViewGroupService.updateGroupAfterPipelineUpdate(
            projectId = setting.projectId,
            pipelineId = setting.pipelineId,
            pipelineName = setting.pipelineName,
            creator = userId,
            userId = userId
        )

        if (dispatchPipelineUpdateEvent) {
            pipelineEventDispatcher.dispatch(
                PipelineUpdateEvent(
                    source = "update_pipeline",
                    projectId = setting.projectId,
                    pipelineId = setting.pipelineId,
                    version = settingVersion,
                    userId = userId
                )
            )
        }
        return setting.copy(version = settingVersion)
    }

    fun userGetSetting(
        userId: String,
        projectId: String,
        pipelineId: String,
        channelCode: ChannelCode = ChannelCode.BS,
        version: Int = 0,
        checkPermission: Boolean = false,
        detailInfo: PipelineDetailInfo? = null
    ): PipelineSetting {

        if (checkPermission) {
            val language = I18nUtil.getLanguage(userId)
            val permission = AuthPermission.VIEW
            pipelinePermissionService.validPipelinePermission(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                permission = permission,
                message = MessageUtil.getMessageByLocale(
                    CommonMessageCode.USER_NOT_PERMISSIONS_OPERATE_PIPELINE,
                    language,
                    arrayOf(
                        userId,
                        projectId,
                        permission.getI18n(language),
                        pipelineId
                    )
                )
            )
        }

        var settingInfo = pipelineRepositoryService.getSetting(projectId, pipelineId)
        val groups = pipelineGroupService.getGroups(userId, projectId, pipelineId)
        val labels = ArrayList<String>()
        groups.forEach {
            labels.addAll(it.labels)
        }
        if (settingInfo == null) {
            val (pipelineName, pipelineDesc) = detailInfo?.let {
                Pair(it.pipelineName, it.pipelineDesc)
            } ?: client.get(ServicePipelineResource::class).getPipelineInfo(
                projectId = projectId,
                pipelineId = pipelineId,
                channelCode = channelCode
            ).data?.let {
                Pair(it.pipelineName, it.pipelineDesc)
            } ?: Pair(null, null)
            settingInfo = PipelineSetting(
                projectId = projectId,
                pipelineId = pipelineId,
                pipelineName = pipelineName ?: "unknown pipeline name",
                desc = pipelineDesc ?: "",
                runLockType = PipelineRunLockType.MULTIPLE,
                successSubscription = Subscription(),
                failSubscription = Subscription(),
                labels = labels,
                pipelineAsCodeSettings = PipelineAsCodeSettings()
            )
        } else {
            settingInfo.labels = labels
        }

        if (version > 0) { // #671 目前只接受通知设置的版本管理, 其他属于公共设置不接受版本管理
            val ve = pipelineSettingVersionService.getPipelineSettingVersion(projectId, pipelineId, version)
            settingInfo.successSubscriptionList = ve.successSubscriptionList
            settingInfo.failSubscriptionList = ve.failSubscriptionList
        }

        return settingInfo
    }

    fun getCommonSetting(userId: String): PipelineCommonSetting {
        val inputComponentCommonSettings = mutableListOf<TaskComponentCommonSetting>()
        val inputTypeConfigMap = AtomUtils.getInputTypeConfigMap(taskCommonSettingConfig)
        inputTypeConfigMap.forEach { (componentType, maxSize) ->
            inputComponentCommonSettings.add(
                TaskComponentCommonSetting(
                    componentType = componentType,
                    maxSize = maxSize
                )
            )
        }
        val outputComponentCommonSettings = listOf(
            TaskComponentCommonSetting(
                componentType = KEY_DEFAULT,
                maxSize = taskCommonSettingConfig.maxDefaultOutputComponentSize
            )
        )
        val taskCommonSetting = TaskCommonSetting(
            maxInputNum = taskCommonSettingConfig.maxInputNum,
            maxOutputNum = taskCommonSettingConfig.maxOutputNum,
            inputComponentCommonSettings = inputComponentCommonSettings,
            outputComponentCommonSettings = outputComponentCommonSettings
        )
        return PipelineCommonSetting(
            maxStageNum = pipelineCommonSettingConfig.maxStageNum,
            stageCommonSetting = StageCommonSetting(
                maxJobNum = stageCommonSettingConfig.maxJobNum,
                jobCommonSetting = JobCommonSetting(
                    maxTaskNum = jobCommonSettingConfig.maxTaskNum,
                    taskCommonSetting = taskCommonSetting
                )
            )
        )
    }

    fun getSettingInfo(projectId: String, pipelineId: String): PipelineSetting? {
        return pipelineRepositoryService.getSetting(projectId, pipelineId)
    }

    private fun checkEditPermission(userId: String, projectId: String, pipelineId: String, message: String) {
        if (!pipelinePermissionService.checkPipelinePermission(
                userId = userId,
                projectId = projectId,
                pipelineId = pipelineId,
                permission = AuthPermission.EDIT
            )
        ) {
            throw PermissionForbiddenException(message)
        }
    }

    fun updatePipelineModel(
        userId: String,
        updatePipelineModelRequest: UpdatePipelineModelRequest,
        checkPermission: Boolean = true
    ): Boolean {
        val pipelineModelVersionList = updatePipelineModelRequest.pipelineModelVersionList
        if (checkPermission) {
            pipelineModelVersionList.forEach {
                checkEditPermission(
                    userId = it.creator,
                    projectId = it.projectId,
                    pipelineId = it.pipelineId,
                    message = "Need edit permission"
                )
            }
        }

        pipelineRepositoryService.batchUpdatePipelineModel(
            userId = userId,
            pipelineModelVersionList = pipelineModelVersionList
        )
        return true
    }

    fun rebuildSetting(
        oldSetting: PipelineSetting,
        projectId: String,
        newPipelineId: String,
        pipelineName: String
    ): PipelineSetting {
        return oldSetting.copy(
            projectId = projectId,
            pipelineId = newPipelineId,
            pipelineName = pipelineName
        )
    }

    fun updateMaxConRunningQueueSize(
        userId: String,
        projectId: String,
        pipelineId: String,
        maxConRunningQueueSize: Int
    ): Int {
        return pipelineRepositoryService.updateMaxConRunningQueueSize(
            userId = userId,
            projectId = projectId,
            pipelineId = pipelineId,
            maxConRunningQueueSize = maxConRunningQueueSize
        )
    }
}
