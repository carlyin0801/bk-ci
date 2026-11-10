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

package com.tencent.devops.process.engine.control

import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.pojo.element.PreFailureSkippedAs
import com.tencent.devops.process.engine.pojo.PipelineBuildTask

/**
 * 一个前序步骤在判定时刻的终态快照。
 *
 * 判定「前序成功 / 前序失败」需要逐个步骤的终态，而不只是容器的聚合状态：
 * 容器聚合状态读不出「是哪一步失败的」，因此支撑不了前序范围（`from`）。
 *
 * @param failureSkipped 该步骤的失败被「失败时继续」放过了（自动跳过或人工跳过）。
 *   这类步骤算成功还是算失败，由本步骤配置的 [PreFailureSkippedAs] 决定，而不是由谁放过的决定。
 */
data class PreTaskStatus(
    val taskId: String,
    val stepId: String?,
    val taskName: String,
    val status: BuildStatus,
    val failureSkipped: Boolean
) {

    /** 未被放过的真失败 */
    fun hardFailure() = status.isFailure() && !failureSkipped

    /**
     * 按 [preFailureSkippedAs] 口径判断本步骤是否「算失败」。
     * 被放过的失败：口径为 [PreFailureSkippedAs.FAILURE] 时算失败，为 [PreFailureSkippedAs.SUCCESS]（默认）时不算。
     */
    fun countsAsFailure(preFailureSkippedAs: PreFailureSkippedAs): Boolean = when {
        failureSkipped -> preFailureSkippedAs == PreFailureSkippedAs.FAILURE
        else -> status.isFailure()
    }

    companion object {

        /**
         * 任务参数里的人工跳过标记。两种跳过的终态同为 [BuildStatus.SKIP]，
         * 仅靠 errorMsg 文案区分不可靠，重试时在任务参数上落一个明确标记。
         */
        const val TASK_PARAM_MANUALLY_SKIPPED = "__manuallySkipped"

        /**
         * 取出当前步骤之前的所有前序步骤快照。
         */
        fun snapshotOf(containerTasks: List<PipelineBuildTask>, currentIndex: Int): List<PreTaskStatus> {
            if (currentIndex <= 0) {
                return emptyList()
            }
            return containerTasks.subList(0, currentIndex).mapNotNull { of(it) }
        }

        /**
         * 单个任务的快照。post-action 任务与引擎内置任务（开关机、结束节点）不是用户编排里的
         * 「前序步骤」，不参与成败判定，对它们返回 null。
         */
        fun of(task: PipelineBuildTask): PreTaskStatus? {
            if (task.additionalOptions?.elementPostInfo != null || task.taskAtom.isNotBlank()) {
                return null
            }
            return PreTaskStatus(
                taskId = task.taskId,
                stepId = task.stepId,
                taskName = task.taskName,
                status = task.status,
                failureSkipped = isFailureSkipped(task)
            )
        }

        /**
         * 该步骤是否属于「失败后被放过」。
         *
         * 自动跳过：插件失败后由引擎直接放行，终态仍是失败态，靠「失败时继续且非人工」识别。
         * 人工跳过：插件失败后由人点跳过，重试时以 [BuildStatus.SKIP] 重建，靠任务参数上的标记识别——
         * 条件不满足的跳过终态同样是 SKIP，不能混为一谈。
         */
        private fun isFailureSkipped(task: PipelineBuildTask): Boolean {
            if (task.status == BuildStatus.SKIP) {
                return task.taskParams[TASK_PARAM_MANUALLY_SKIPPED] == true
            }
            return task.status.isFailure() && ControlUtils.continueWhenFailure(task.additionalOptions)
        }
    }
}
