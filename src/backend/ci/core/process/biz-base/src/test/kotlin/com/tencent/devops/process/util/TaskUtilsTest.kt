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

package com.tencent.devops.process.util

import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.pojo.element.ElementPostInfo
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.process.TestBase
import com.tencent.devops.process.engine.common.VMUtils
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TaskUtilsTest : TestBase() {

    private val taskList = mutableListOf<PipelineBuildTask>()

    private var vmBuildContainer: PipelineBuildContainer =
        genVmBuildContainer(id = firstContainerIdInt, status = BuildStatus.RUNNING)

    @BeforeEach
    override fun setUp() {
        super.setUp()
        vmBuildContainer = genVmBuildContainer(id = firstContainerIdInt, status = BuildStatus.RUNNING)
        taskList.add(genTask(taskId = "e-11", vmContainer = vmBuildContainer)
            .copy(taskSeq = 1, status = BuildStatus.SUCCEED)
        )
        taskList.add(genTask(taskId = "e-12", vmContainer = vmBuildContainer)
            .copy(taskSeq = 2, status = BuildStatus.SUCCEED)
        )
        taskList.add(genTask(taskId = "e-13", vmContainer = vmBuildContainer)
            .copy(taskSeq = 4, status = BuildStatus.QUEUE)
        )
    }

    @Test
    fun getPostTaskAndExecuteFlag() {
        val task = genTask(taskId = "e-12345678901234567890123456789012", vmContainer = vmBuildContainer,
            elementAdditionalOptions = elementAdditionalOptions().copy(elementPostInfo = nullObject))
        Assertions.assertFalse(
            TaskUtils.getPostExecuteFlag(
                task = task, taskList = taskList, isContainerFailed = true, hasFailedTaskInInSuccessContainer = true
            )
        )
    }

    /**
     * #13602 收尾步骤的post任务只与其父插件（收尾步骤）是否真正执行过有关，
     * 不再叠加主步骤算出的Job终态与「失败继续」标识
     */
    @Test
    fun `post task of job post step only depends on its parent`() {
        val parentTaskId = "e-post-step-parent"
        val postStepOptions = elementAdditionalOptions().copy(jobPostStepFlag = true)
        val postTaskOptions = elementAdditionalOptions(
            runCondition = RunCondition.PRE_TASK_SUCCESS,
            elementPostInfo = ElementPostInfo(
                parentElementId = parentTaskId,
                parentElementName = "post-step",
                parentElementJobIndex = 3,
                postEntryParam = "post.sh",
                postCondition = "success()"
            )
        ).copy(jobPostStepFlag = true)
        val postTask = genTask(
            taskId = "e-post-step-post", vmContainer = vmBuildContainer, elementAdditionalOptions = postTaskOptions
        )

        // 父插件执行过：即使Job已失败、且存在失败继续的插件，post任务仍要运行
        val executedParent = genTask(
            taskId = parentTaskId, vmContainer = vmBuildContainer, elementAdditionalOptions = postStepOptions
        ).copy(taskSeq = 5, status = BuildStatus.FAILED)
        Assertions.assertTrue(
            TaskUtils.getPostExecuteFlag(
                task = postTask, taskList = taskList.plus(executedParent),
                isContainerFailed = true, hasFailedTaskInInSuccessContainer = true
            )
        )

        // 父插件没有真正执行过：post任务不运行
        val unExecParent = executedParent.copy(status = BuildStatus.UNEXEC)
        Assertions.assertFalse(
            TaskUtils.getPostExecuteFlag(
                task = postTask, taskList = taskList.plus(unExecParent),
                isContainerFailed = false, hasFailedTaskInInSuccessContainer = false
            )
        )
    }

    @Test
    fun isStartVMTask() {
        var taskId = "mockId"
        Assertions.assertFalse(
            TaskUtils.isStartVMTask(
                genTask(taskId = taskId, vmContainer = genVmBuildContainer(id = firstContainerIdInt))
            )
        )
        // startVM-xxxx
        taskId = VMUtils.genStartVMTaskId(firstContainerId)
        Assertions.assertTrue(
            TaskUtils.isStartVMTask(
                genTask(taskId = taskId, vmContainer = genVmBuildContainer(id = firstContainerIdInt))
            )
        )
    }
}
