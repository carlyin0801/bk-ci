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

package com.tencent.devops.stream.trigger.v2

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.ci.v2.YamlTransferData
import com.tencent.devops.common.ci.v2.enums.TemplateType
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.kafka.KafkaClient
import com.tencent.devops.common.kafka.KafkaTopic.STREAM_BUILD_INFO_TOPIC
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.stream.config.StreamGitConfig
import com.tencent.devops.stream.dao.GitPipelineResourceDao
import com.tencent.devops.stream.dao.GitRequestEventBuildDao
import com.tencent.devops.stream.pojo.GitProjectPipeline
import com.tencent.devops.stream.pojo.GitRequestEvent
import com.tencent.devops.stream.pojo.enums.GitCICommitCheckState
import com.tencent.devops.stream.pojo.enums.TriggerReason
import com.tencent.devops.stream.pojo.v2.GitCIBasicSetting
import com.tencent.devops.stream.pojo.v2.StreamBuildInfo
import com.tencent.devops.stream.trigger.GitCIEventService
import com.tencent.devops.stream.trigger.GitCheckService
import com.tencent.devops.stream.utils.GitCIPipelineUtils
import com.tencent.devops.stream.v2.service.StreamWebsocketService
import com.tencent.devops.process.api.service.ServiceBuildResource
import com.tencent.devops.process.api.service.ServicePipelineResource
import com.tencent.devops.process.pojo.BuildId
import com.tencent.devops.common.webhook.enums.code.tgit.TGitObjectKind
import com.tencent.devops.process.api.service.ServiceTemplateAcrossResource
import com.tencent.devops.process.api.user.UserPipelineGroupResource
import com.tencent.devops.process.pojo.BuildTemplateAcrossInfo
import com.tencent.devops.process.pojo.TemplateAcrossInfoType
import com.tencent.devops.process.utils.PIPELINE_NAME
import com.tencent.devops.stream.pojo.isFork
import com.tencent.devops.stream.pojo.isMr
import com.tencent.devops.stream.trigger.StreamTriggerCache
import com.tencent.devops.stream.utils.CommitCheckUtils
import com.tencent.devops.stream.utils.StreamTriggerMessageUtils
import com.tencent.devops.stream.v2.service.StreamOauthService
import com.tencent.devops.stream.v2.service.StreamPipelineBranchService
import com.tencent.devops.stream.v2.service.StreamScmService
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Service
class StreamYamlBaseBuild @Autowired constructor(
    private val client: Client,
    private val kafkaClient: KafkaClient,
    private val dslContext: DSLContext,
    private val gitPipelineResourceDao: GitPipelineResourceDao,
    private val gitRequestEventBuildDao: GitRequestEventBuildDao,
    private val gitCIEventSaveService: GitCIEventService,
    private val websocketService: StreamWebsocketService,
    private val streamPipelineBranchService: StreamPipelineBranchService,
    private val gitCheckService: GitCheckService,
    private val streamGitConfig: StreamGitConfig,
    private val triggerMessageUtil: StreamTriggerMessageUtils,
    private val streamTriggerCache: StreamTriggerCache,
    private val oauthService: StreamOauthService,
    private val streamScmService: StreamScmService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(StreamYamlBaseBuild::class.java)
        private const val ymlVersion = "v2.0"
    }

    private val channelCode = ChannelCode.GIT

    private val buildRunningDesc = "Your pipeline「%s」is running."

    fun savePipeline(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        gitCIBasicSetting: GitCIBasicSetting,
        model: Model
    ) {
        val processClient = client.get(ServicePipelineResource::class)
        if (pipeline.pipelineId.isBlank()) {
            // 直接新建
            logger.info("create newpipeline: $pipeline")

            pipeline.pipelineId =
                processClient.create(event.userId, gitCIBasicSetting.projectCode!!, model, channelCode).data!!.id
            gitPipelineResourceDao.createPipeline(
                dslContext = dslContext,
                gitProjectId = gitCIBasicSetting.gitProjectId,
                pipeline = pipeline,
                version = ymlVersion
            )
            websocketService.pushPipelineWebSocket(
                projectId = "git_${gitCIBasicSetting.gitProjectId}",
                pipelineId = pipeline.pipelineId,
                userId = event.userId
            )
        } else if (needReCreate(processClient, event, gitCIBasicSetting, pipeline)) {
            val oldPipelineId = pipeline.pipelineId
            // 先删除已有数据
            logger.info("recreate pipeline: $pipeline")
            try {
                gitPipelineResourceDao.deleteByPipelineId(dslContext, oldPipelineId)
                processClient.delete(event.userId, gitCIBasicSetting.projectCode!!, oldPipelineId, channelCode)
            } catch (e: Exception) {
                logger.error("failed to delete pipeline resource, pipeline: $pipeline", e)
            }
            // 再次新建
            pipeline.pipelineId =
                processClient.create(event.userId, gitCIBasicSetting.projectCode!!, model, channelCode).data!!.id
            gitPipelineResourceDao.createPipeline(
                dslContext = dslContext,
                gitProjectId = gitCIBasicSetting.gitProjectId,
                pipeline = pipeline,
                version = ymlVersion
            )
            // 对于需要删了重建的，删除旧的流水线-分支记录
            streamPipelineBranchService.deleteBranch(
                gitProjectId = gitCIBasicSetting.gitProjectId,
                pipelineId = oldPipelineId,
                branch = null
            )
            websocketService.pushPipelineWebSocket(
                projectId = "git_${gitCIBasicSetting.gitProjectId}",
                pipelineId = pipeline.pipelineId,
                userId = event.userId
            )
        } else if (pipeline.pipelineId.isNotBlank()) {
            // 编辑流水线model
            processClient.edit(event.userId, gitCIBasicSetting.projectCode!!, pipeline.pipelineId, model, channelCode)
            // 已有的流水线需要更新下Stream这里的状态
            logger.info("update gitPipeline pipeline: $pipeline")
            gitPipelineResourceDao.updatePipeline(
                dslContext = dslContext,
                gitProjectId = gitCIBasicSetting.gitProjectId,
                pipelineId = pipeline.pipelineId,
                displayName = pipeline.displayName,
                version = ymlVersion
            )
        }
    }

    private fun preStartBuild(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        gitCIBasicSetting: GitCIBasicSetting,
        model: Model,
        yamlTransferData: YamlTransferData? = null
    ) {
        // 【ID92537607】 stream 流水线标签不生效
        client.get(UserPipelineGroupResource::class).updatePipelineLabel(
            userId = event.userId,
            projectId = gitCIBasicSetting.projectCode!!,
            pipelineId = pipeline.pipelineId,
            labelIds = model.labels
        )

        // 添加模板跨项目信息
        if (yamlTransferData != null && yamlTransferData.templateData.transferDataMap.isNotEmpty()) {
            client.get(ServiceTemplateAcrossResource::class).batchCreate(
                userId = event.userId,
                projectId = gitCIBasicSetting.projectCode!!,
                pipelineId = pipeline.pipelineId,
                templateAcrossInfos = yamlTransferData.getTemplateAcrossInfo(
                    gitRequestEventId = event.id!!,
                    gitProjectId = event.gitProjectId
                )
            )
        }
    }

    @SuppressWarnings("LongParameterList")
    fun startBuild(
        pipeline: GitProjectPipeline,
        event: GitRequestEvent,
        gitCIBasicSetting: GitCIBasicSetting,
        model: Model,
        gitBuildId: Long,
        yamlTransferData: YamlTransferData? = null
    ): BuildId? {
        preStartBuild(pipeline, event, gitCIBasicSetting, model, yamlTransferData)

        val processClient = client.get(ServicePipelineResource::class)
        // 修改流水线并启动构建，需要加锁保证事务性
        var buildId = ""
        try {
            logger.info(
                "Stream Build start, gitProjectId[${gitCIBasicSetting.gitProjectId}], " +
                    "pipelineId[${pipeline.pipelineId}], gitBuildId[$gitBuildId]"
            )
            buildId = startupPipelineBuild(
                processClient = processClient,
                model = model,
                event = event,
                gitCIBasicSetting = gitCIBasicSetting,
                pipelineId = pipeline.pipelineId,
                pipelineName = pipeline.displayName
            )
            logger.info(
                "Stream Build success, gitProjectId[${gitCIBasicSetting.gitProjectId}], " +
                    "pipelineId[${pipeline.pipelineId}], gitBuildId[$gitBuildId], buildId[$buildId]"
            )
        } catch (ignore: Throwable) {
            errorStartBuild(gitCIBasicSetting, pipeline, gitBuildId, ignore, event, yamlTransferData)
        } finally {
            if (buildId.isNotEmpty()) {
                kafkaClient.send(
                    STREAM_BUILD_INFO_TOPIC, JsonUtil.toJson(
                    StreamBuildInfo(
                        buildId = buildId,
                        streamYamlUrl = "${gitCIBasicSetting.homepage}/blob/${event.commitId}/${pipeline.filePath}",
                        washTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        )
                    )
                ))
            }
        }

        if (buildId.isBlank()) {
            return null
        }

        afterStartBuild(pipeline, buildId, gitBuildId, event, gitCIBasicSetting, yamlTransferData)

        return BuildId(buildId)
    }

    private fun errorStartBuild(
        gitCIBasicSetting: GitCIBasicSetting,
        pipeline: GitProjectPipeline,
        gitBuildId: Long,
        ignore: Throwable,
        event: GitRequestEvent,
        yamlTransferData: YamlTransferData?
    ) {
        logger.error(
            "Stream Build failed, gitProjectId[${gitCIBasicSetting.gitProjectId}], " +
                "pipelineId[${pipeline.pipelineId}], gitBuildId[$gitBuildId]",
            ignore
        )
        // 清除已经保存的构建记录
        val build = gitRequestEventBuildDao.getByGitBuildId(dslContext, gitBuildId)
        if (build != null) gitRequestEventBuildDao.removeBuild(dslContext, gitBuildId)

        // 保存失败构建记录
        gitCIEventSaveService.saveRunNotBuildEvent(
            userId = event.userId,
            eventId = event.id!!,
            pipelineId = pipeline.pipelineId,
            pipelineName = pipeline.displayName,
            filePath = pipeline.filePath,
            originYaml = build?.originYaml,
            normalizedYaml = build?.normalizedYaml,
            reason = TriggerReason.PIPELINE_RUN_ERROR.name,
            reasonDetail = ignore.message ?: TriggerReason.PIPELINE_RUN_ERROR.detail,
            gitProjectId = event.gitProjectId,
            sendCommitCheck = true,
            commitCheckBlock = (event.objectKind == TGitObjectKind.MERGE_REQUEST.value),
            version = ymlVersion,
            branch = event.branch
        )

        // 删除模板跨项目信息
        if (yamlTransferData?.templateData != null) {
            client.get(ServiceTemplateAcrossResource::class).delete(
                projectId = gitCIBasicSetting.projectCode!!,
                pipelineId = pipeline.pipelineId,
                templateId = yamlTransferData.templateData.templateId,
                buildId = null
            )
        }
    }

    private fun afterStartBuild(
        pipeline: GitProjectPipeline,
        buildId: String,
        gitBuildId: Long,
        event: GitRequestEvent,
        gitCIBasicSetting: GitCIBasicSetting,
        yamlTransferData: YamlTransferData?
    ) {
        try {
            // 更新流水线和构建记录状态
            gitPipelineResourceDao.updatePipelineBuildInfo(dslContext, pipeline, buildId, ymlVersion)
            gitRequestEventBuildDao.update(dslContext, gitBuildId, pipeline.pipelineId, buildId, ymlVersion)
            // 成功构建的添加 流水线-分支 记录
            if (!event.isFork() && (event.objectKind == TGitObjectKind.PUSH.value || event.isMr())) {
                streamPipelineBranchService.saveOrUpdate(
                    gitProjectId = gitCIBasicSetting.gitProjectId,
                    pipelineId = pipeline.pipelineId,
                    branch = event.branch
                )
            }
            // 发送commitCheck
            if (CommitCheckUtils.needSendCheck(event, gitCIBasicSetting)) {
                gitCheckService.pushCommitCheck(
                    commitId = event.commitId,
                    description = triggerMessageUtil.getCommitCheckDesc(
                        event,
                        buildRunningDesc.format(pipeline.displayName)
                    ),
                    mergeRequestId = event.mergeRequestId,
                    buildId = buildId,
                    userId = event.userId,
                    status = GitCICommitCheckState.PENDING,
                    context = "${pipeline.filePath}@${event.objectKind.toUpperCase()}",
                    gitCIBasicSetting = gitCIBasicSetting,
                    targetUrl = GitCIPipelineUtils.genGitCIV2BuildUrl(
                        homePage = streamGitConfig.tGitUrl ?: throw ParamBlankException("启动配置缺少 rtx.v2GitUrl"),
                        gitProjectId = gitCIBasicSetting.gitProjectId,
                        pipelineId = pipeline.pipelineId,
                        buildId = buildId
                    ),
                    block = (event.objectKind == TGitObjectKind.MERGE_REQUEST.value && gitCIBasicSetting.enableMrBlock),
                    pipelineId = pipeline.pipelineId
                )
            }

            // 更新跨项目模板信息
            if (yamlTransferData?.templateData != null) {
                client.get(ServiceTemplateAcrossResource::class).update(
                    projectId = gitCIBasicSetting.projectCode!!,
                    pipelineId = pipeline.pipelineId,
                    templateId = yamlTransferData.templateData.templateId,
                    buildId = buildId
                )
            }
        } catch (ignore: Exception) {
            logger.error(
                "Stream after Build failed, gitProjectId[${gitCIBasicSetting.gitProjectId}], " +
                    "pipelineId[${pipeline.pipelineId}], gitBuildId[$gitBuildId]",
                ignore
            )
        }
    }

    private fun startupPipelineBuild(
        processClient: ServicePipelineResource,
        model: Model,
        event: GitRequestEvent,
        gitCIBasicSetting: GitCIBasicSetting,
        pipelineId: String,
        pipelineName: String
    ): String {
        processClient.edit(event.userId, gitCIBasicSetting.projectCode!!, pipelineId, model, channelCode)
        return client.get(ServiceBuildResource::class).manualStartupNew(
            userId = event.userId,
            projectId = gitCIBasicSetting.projectCode!!,
            pipelineId = pipelineId,
            values = mapOf(PIPELINE_NAME to pipelineName),
            channelCode = channelCode,
            startType = StartType.SERVICE
        ).data!!.id
    }

    private fun needReCreate(
        processClient: ServicePipelineResource,
        event: GitRequestEvent,
        gitCIBasicSetting: GitCIBasicSetting,
        pipeline: GitProjectPipeline
    ): Boolean {
        try {
            val response =
                processClient.get(event.userId, gitCIBasicSetting.projectCode!!, pipeline.pipelineId, channelCode)
            if (response.isNotOk()) {
                logger.error("get pipeline failed, msg: ${response.message}")
                return true
            }
        } catch (e: Exception) {
            logger.error(
                "get pipeline failed, pipelineId: ${pipeline.pipelineId}, " +
                    "projectCode: ${gitCIBasicSetting.projectCode}, error msg: ${e.message}"
            )
            return true
        }
        return false
    }

    private fun YamlTransferData.getTemplateAcrossInfo(
        gitRequestEventId: Long,
        gitProjectId: Long
    ): List<BuildTemplateAcrossInfo> {
        val remoteProjectIdMap = mutableMapOf<String, BuildTemplateAcrossInfo>()
        templateData.transferDataMap.values.forEach { objectData ->
            if (objectData.remoteProjectId !in remoteProjectIdMap.keys) {
                // 将pathWithPathSpace转为数字id
                val remoteProjectIdLong = streamTriggerCache.getAndSaveRequestGitProjectInfo(
                    gitRequestEventId = gitRequestEventId,
                    gitProjectId = objectData.remoteProjectId,
                    token = oauthService.getGitCIEnableToken(gitProjectId).accessToken,
                    useAccessToken = true,
                    getProjectInfo = streamScmService::getProjectInfoRetry
                ).gitProjectId.toString().let { "git_$it" }

                remoteProjectIdMap[objectData.remoteProjectId] = BuildTemplateAcrossInfo(
                    templateId = templateData.templateId,
                    templateType = objectData.templateType.toAcrossType()!!,
                    templateInstancesIds = listOf(objectData.objectId),
                    targetProjectId = remoteProjectIdLong
                )
            } else {
                remoteProjectIdMap[objectData.remoteProjectId]!!.templateInstancesIds
                    .toMutableList().add(objectData.objectId)
            }
        }

        val results = mutableListOf<BuildTemplateAcrossInfo>()
        remoteProjectIdMap.forEach { (_, data) -> results.add(data) }
        return results
    }

    private fun TemplateType.toAcrossType(): TemplateAcrossInfoType? {
        return when (this) {
            TemplateType.STEP -> TemplateAcrossInfoType.STEP
            TemplateType.JOB -> TemplateAcrossInfoType.JOB
            else -> null
        }
    }
}
