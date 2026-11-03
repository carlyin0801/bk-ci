/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.devops.process.engine.control.command.container.impl

import com.tencent.devops.common.api.util.Watcher
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.event.enums.ActionType
import com.tencent.devops.common.log.utils.BuildLogPrinter
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.config.CommonConfig
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.process.engine.control.command.container.ContainerContext
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.process.engine.pojo.event.PipelineBuildContainerEvent
import com.tencent.devops.process.engine.service.PipelineTaskService
import com.tencent.devops.process.engine.service.record.TaskBuildRecordService
import com.tencent.devops.process.engine.utils.TestTool
import com.tencent.devops.process.service.BuildVariableService
import com.tencent.devops.process.service.PipelineContextService
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * 校验构建取消时，Job下插件任务的状态流转
 */
class StartActionTaskContainerCmdTest {

    private val redisOperation: RedisOperation = mockk()
    private val pipelineTaskService: PipelineTaskService = mockk()
    private val taskBuildRecordService: TaskBuildRecordService = mockk()
    private val pipelineEventDispatcher: PipelineEventDispatcher = mockk(relaxed = true)
    private val buildLogPrinter: BuildLogPrinter = mockk(relaxed = true)
    private val pipelineContextService: PipelineContextService = mockk()
    private val buildVariableService: BuildVariableService = mockk(relaxed = true)

    private val startActionTaskContainerCmd = StartActionTaskContainerCmd(
        redisOperation = redisOperation,
        pipelineTaskService = pipelineTaskService,
        taskBuildRecordService = taskBuildRecordService,
        pipelineEventDispatcher = pipelineEventDispatcher,
        buildLogPrinter = buildLogPrinter,
        pipelineContextService = pipelineContextService,
        buildVariableService = buildVariableService
    )

    /** 记录每个插件任务被写入的状态，key为taskId */
    private val updatedTaskStatus = mutableMapOf<String, BuildStatus>()

    @BeforeEach
    fun setUp() {
        // ControlUtils#checkTaskSkip 会读取国际化文案，需要提供对应的Bean
        val commonConfig: CommonConfig = mockk()
        every { commonConfig.devopsDefaultLocaleLanguage } returns "zh_CN"
        mockkObject(SpringContextUtil)
        every { SpringContextUtil.getBean(CommonConfig::class.java) } returns commonConfig
        every { SpringContextUtil.getBean(RedisOperation::class.java) } returns redisOperation
        every { redisOperation.get(any(), any()) } returns null

        every {
            pipelineContextService.buildContext(
                any(), any(), any(), any(), any(), any(), any(), any(), any()
            )
        } returns mutableMapOf()

        updatedTaskStatus.clear()
        val taskSlot = mutableListOf<PipelineBuildTask>()
        val statusSlot = mutableListOf<BuildStatus>()
        every {
            pipelineTaskService.updateTaskStatus(
                capture(taskSlot), any(), capture(statusSlot), any(), any(), any(), any(), any()
            )
        } answers {
            updatedTaskStatus[taskSlot.last().taskId] = statusSlot.last()
        }
        every { taskBuildRecordService.taskEnd(any()) } returns Pair(emptyList(), null)
        every {
            taskBuildRecordService.updateTaskStatus(any(), any(), any(), any(), any(), any(), any(), any())
        } just Runs
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    /**
     * #13579 取消运行中的插件后，Job下还未开始执行的插件只能是UNEXEC，不能被置为取消状态。
     * 已取消的插件本身即可作为Container取消状态的抓手，无需再额外挑一个未执行的插件置为取消。
     */
    @Test
    fun `given canceled task in job then pending tasks are unexec`() {
        val container = TestTool.genVmBuildContainer(status = BuildStatus.RUNNING)
        val containerTasks = listOf(
            genTask(container, taskId = "e-1", status = BuildStatus.CANCELED), // 被取消的“拉取SVN”
            genTask(container, taskId = "e-2", status = BuildStatus.QUEUE),
            genTask(container, taskId = "e-3", status = BuildStatus.QUEUE),
            genTask(container, taskId = "e-4", status = BuildStatus.QUEUE)
        )

        startActionTaskContainerCmd.execute(genContext(container, containerTasks))

        Assertions.assertEquals(
            mapOf(
                "e-2" to BuildStatus.UNEXEC,
                "e-3" to BuildStatus.UNEXEC,
                "e-4" to BuildStatus.UNEXEC
            ),
            updatedTaskStatus
        )
    }

    /**
     * #5048 Job下没有任何插件处于取消态时，需要将第一个未执行的插件置为取消，
     * 作为后续Container状态的抓手，否则Job会被判定为成功。
     */
    @Test
    fun `given no canceled task in job then first pending task is canceled`() {
        val container = TestTool.genVmBuildContainer(status = BuildStatus.RUNNING)
        val containerTasks = listOf(
            genTask(container, taskId = "e-1", status = BuildStatus.QUEUE),
            genTask(container, taskId = "e-2", status = BuildStatus.QUEUE),
            genTask(container, taskId = "e-3", status = BuildStatus.QUEUE)
        )

        startActionTaskContainerCmd.execute(genContext(container, containerTasks))

        Assertions.assertEquals(
            mapOf(
                "e-1" to BuildStatus.CANCELED,
                "e-2" to BuildStatus.UNEXEC,
                "e-3" to BuildStatus.UNEXEC
            ),
            updatedTaskStatus
        )
    }

    private fun genTask(
        container: PipelineBuildContainer,
        taskId: String,
        status: BuildStatus
    ) = TestTool.genTask(taskId = taskId, vmContainer = container).copy(status = status)

    private fun genContext(
        container: PipelineBuildContainer,
        containerTasks: List<PipelineBuildTask>
    ) = ContainerContext(
        container = container,
        containerTasks = containerTasks,
        buildStatus = container.status,
        event = PipelineBuildContainerEvent(
            source = "cancel-test",
            projectId = container.projectId,
            pipelineId = container.pipelineId,
            userId = "user1",
            buildId = container.buildId,
            stageId = container.stageId,
            containerId = container.containerId,
            containerHashId = container.containerHashId,
            containerType = container.containerType,
            executeCount = 1,
            actionType = ActionType.END
        ),
        latestSummary = "init",
        variables = emptyMap(),
        watcher = Watcher(id = "StartActionTaskContainerCmdTest")
    )
}
