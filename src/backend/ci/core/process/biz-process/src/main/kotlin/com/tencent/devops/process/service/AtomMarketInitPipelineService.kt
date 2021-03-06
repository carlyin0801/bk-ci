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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.Container
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.container.TriggerContainer
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.enums.BuildFormPropertyType
import com.tencent.devops.common.pipeline.enums.BuildScriptType
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.enums.CodePullStrategy
import com.tencent.devops.common.pipeline.enums.DockerVersion
import com.tencent.devops.common.pipeline.enums.GitPullModeType
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.enums.VMBaseOS
import com.tencent.devops.common.pipeline.pojo.AtomBaseInfo
import com.tencent.devops.common.pipeline.pojo.BuildFormProperty
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.agent.CodeGitElement
import com.tencent.devops.common.pipeline.pojo.element.agent.LinuxScriptElement
import com.tencent.devops.common.pipeline.pojo.element.market.AtomBuildArchiveElement
import com.tencent.devops.common.pipeline.pojo.element.trigger.ManualTriggerElement
import com.tencent.devops.common.pipeline.pojo.git.GitPullMode
import com.tencent.devops.common.pipeline.type.docker.DockerDispatchType
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.service.PipelineBuildService
import com.tencent.devops.process.engine.service.PipelineService
import com.tencent.devops.process.pojo.AtomMarketInitPipelineResp
import com.tencent.devops.store.pojo.atom.enums.AtomStatusEnum
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 初始化流水线进行打包归档
 * since: 2019-01-08
 */
@Service
class AtomMarketInitPipelineService @Autowired constructor(
    private val pipelineService: PipelineService,
    private val buildService: PipelineBuildService
) {
    private val logger = LoggerFactory.getLogger(AtomMarketInitPipelineService::class.java)

    /**
     * 初始化流水线进行打包归档
     */
    fun initPipeline(
        userId: String,
        projectCode: String,
        atomBaseInfo: AtomBaseInfo,
        repositoryHashId: String,
        repositoryPath: String?,
        script: String,
        buildEnv: Map<String, String>?
    ): Result<AtomMarketInitPipelineResp> {
        var containerSeqId = 0
        // stage-1
        val stageFirstElement = ManualTriggerElement(id = "T-1-1-1")
        val stageFirstElements = listOf<Element>(stageFirstElement)
        val params = mutableListOf<BuildFormProperty>()
        params.add(BuildFormProperty(
            id = "atomCode",
            required = true,
            type = BuildFormPropertyType.STRING,
            defaultValue = atomBaseInfo.atomCode,
            options = null,
            desc = null,
            repoHashId = null,
            relativePath = null,
            scmType = null,
            containerType = null,
            glob = null,
            properties = null
        ))
        params.add(BuildFormProperty(
            id = "version",
            required = true,
            type = BuildFormPropertyType.STRING,
            defaultValue = atomBaseInfo.version,
            options = null,
            desc = null,
            repoHashId = null,
            relativePath = null,
            scmType = null,
            containerType = null,
            glob = null,
            properties = null
        ))
        params.add(BuildFormProperty(
            id = "script",
            required = true,
            type = BuildFormPropertyType.STRING,
            defaultValue = script,
            options = null,
            desc = null,
            repoHashId = null,
            relativePath = null,
            scmType = null,
            containerType = null,
            glob = null,
            properties = null
        ))
        val stageFirstContainer = TriggerContainer(
            id = containerSeqId.toString(),
            name = "构建触发",
            elements = stageFirstElements,
            status = null,
            startEpoch = null,
            systemElapsed = null,
            elementElapsed = null,
            params = params,
            buildNo = null
        )
        containerSeqId++
        val stageFirstContainers = listOf<Container>(stageFirstContainer)
        val stageFirst = Stage(stageFirstContainers, VMUtils.genStageId(1))
        // stage-2
        val stageSecondPullCodeElement = CodeGitElement(
            name = "拉取Git仓库代码",
            id = "T-2-1-1",
            status = null,
            repositoryHashId = repositoryHashId,
            branchName = "",
            revision = "",
            strategy = CodePullStrategy.FRESH_CHECKOUT,
            path = repositoryPath,
            enableSubmodule = true,
            gitPullMode = GitPullMode(GitPullModeType.BRANCH, "master")
        )
        val stageSecondLinuxScriptElement = LinuxScriptElement(
            id = "T-2-1-2",
            status = null,
            scriptType = BuildScriptType.SHELL,
            script = "\${script}",
            continueNoneZero = false
        )
        val stageSecondAtomBuildArchiveElement = AtomBuildArchiveElement(id = "T-2-1-3")
        val stageSecondElements = listOf(stageSecondPullCodeElement, stageSecondLinuxScriptElement, stageSecondAtomBuildArchiveElement)
        val stageSecondContainer = VMBuildContainer(
            id = containerSeqId.toString(),
            elements = stageSecondElements,
            baseOS = VMBaseOS.LINUX,
            vmNames = emptySet(),
            maxQueueMinutes = 60,
            maxRunningMinutes = 480,
            buildEnv = buildEnv,
            customBuildEnv = null,
            thirdPartyAgentId = null,
            thirdPartyAgentEnvId = null,
            thirdPartyWorkspace = null,
            dockerBuildVersion = null,
            tstackAgentId = null,
            dispatchType = DockerDispatchType(DockerVersion.TLINUX2_2.value)
        )
        val stageSecondContainers = listOf<Container>(stageSecondContainer)
        val stageSecond = Stage(stageSecondContainers, VMUtils.genStageId(2))
        val stages = mutableListOf(stageFirst, stageSecond)
        val atomCode = atomBaseInfo.atomCode
        val pipelineName = "am-$projectCode-$atomCode-${System.currentTimeMillis()}"
        val model = Model(pipelineName, pipelineName, stages)
        logger.info("model is:$model")
        // 保存流水线信息
        val pipelineId = pipelineService.createPipeline(userId, projectCode, model, ChannelCode.AM)
        logger.info("createPipeline result is:$pipelineId")
        // 异步启动流水线
        val startParams = mutableMapOf<String, String>() // 启动参数
        startParams["atomCode"] = atomCode
        startParams["version"] = atomBaseInfo.version
        startParams["script"] = script
        var atomBuildStatus = AtomStatusEnum.BUILDING
        var buildId: String? = null
        try {
            buildId = buildService.buildManualStartup(
                userId = userId,
                startType = StartType.SERVICE,
                projectId = projectCode,
                pipelineId = pipelineId,
                values = startParams,
                channelCode = ChannelCode.AM,
                checkPermission = false,
                isMobile = false,
                startByMessage = null
            )
            logger.info("atomMarketBuildManualStartup result is:$buildId")
        } catch (e: Exception) {
            logger.info("buildManualStartup error is :$e", e)
            atomBuildStatus = AtomStatusEnum.BUILD_FAIL
        }
        return Result(AtomMarketInitPipelineResp(pipelineId, buildId, atomBuildStatus))
    }
}
