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

import com.tencent.devops.common.api.util.EnvUtils
import com.tencent.devops.common.expression.ExpressionParser
import com.tencent.devops.common.expression.expression.EvaluationResult
import com.tencent.devops.common.expression.expression.functions.StatusFunctions
import com.tencent.devops.common.pipeline.EnvReplacementParser
import com.tencent.devops.common.pipeline.NameAndValue
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.JobRunCondition
import com.tencent.devops.common.pipeline.enums.StageRunCondition
import com.tencent.devops.common.pipeline.pojo.element.ElementAdditionalOptions
import com.tencent.devops.common.pipeline.pojo.element.ResolvedRunIntent
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.common.pipeline.pojo.element.RunConditionNormalizer
import com.tencent.devops.common.pipeline.pojo.element.PreFailureSkippedAs
import com.tencent.devops.common.pipeline.pojo.element.StepRunIntent
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.constant.ProcessMessageCode.BK_CHECK_JOB_RUN_CONDITION
import com.tencent.devops.process.constant.ProcessMessageCode.BK_CHECK_TASK_RUN_CONDITION
import com.tencent.devops.process.constant.ProcessMessageCode.BK_CUSTOM_VARIABLES_ARE_ALL_SATISFIED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_IT_DOES_NOT_RUN_UNLESS_IT_IS_CANCELED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_PRE_STATE_BUILD_CANCELED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_PRE_STATE_JOB_TERMINATED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_PRE_STATE_PRE_TASK_FAILED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_PRE_STATE_PRE_TASK_TIMEOUT
import com.tencent.devops.process.constant.ProcessMessageCode.BK_ONLY_WHEN_PREVIOUS_TASK_FAILED_EXCEPT_SKIP
import com.tencent.devops.process.constant.ProcessMessageCode.BK_ONLY_WHEN_PREVIOUS_TASK_HAS_FAILED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_RUNS_EVEN_IF_CANCELED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_TASK_DISABLED
import com.tencent.devops.process.constant.ProcessMessageCode.BK_WHEN_THE_CUSTOM_VARIABLES_ARE_ALL_SATISFIED
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.utils.PipelineVarUtil
import com.tencent.devops.process.utils.TASK_FAIL_RETRY_MAX_COUNT
import com.tencent.devops.process.utils.TASK_FAIL_RETRY_MIN_COUNT
import org.slf4j.LoggerFactory

@Suppress("ALL")
object ControlUtils {

    private val logger = LoggerFactory.getLogger(javaClass)

    // 是否使用
    fun isEnable(additionalOptions: ElementAdditionalOptions?): Boolean {
        if (additionalOptions == null) {
            return true
        }
        return additionalOptions.enable
    }

    // 插件是否失败时自动跳过/继续
    fun continueWhenFailure(additionalOptions: ElementAdditionalOptions?): Boolean {
        return (additionalOptions?.continueWhenFailed ?: false) && additionalOptions?.manualSkip != true // 手动跳过不算
    }

    // 是否失败时自动重试
    fun retryWhenFailure(additionalOptions: ElementAdditionalOptions?, retryCount: Int): Boolean {
        if (additionalOptions == null || !isEnable(additionalOptions)) {
            return false
        }
        val retryWhenFailed = additionalOptions.retryWhenFailed

        return if (retryWhenFailed) {
            var settingRetryCount = additionalOptions.retryCount
            if (settingRetryCount > TASK_FAIL_RETRY_MAX_COUNT) {
                settingRetryCount = TASK_FAIL_RETRY_MAX_COUNT
            }
            if (settingRetryCount < TASK_FAIL_RETRY_MIN_COUNT) {
                settingRetryCount = TASK_FAIL_RETRY_MIN_COUNT
            }
            retryCount < settingRetryCount
        } else {
            false
        }
    }

    // 需要暂停，且没有暂停过
    fun pauseBeforeExec(additionalOptions: ElementAdditionalOptions?, alreadyPauseFlag: Boolean): Boolean {
        return pauseFlag(additionalOptions) && !alreadyPauseFlag
    }

    // 暂停标识位
    fun pauseFlag(additionalOptions: ElementAdditionalOptions?): Boolean {
        return additionalOptions?.pauseBeforeExec == true
    }

    fun checkCustomVariableSkip(
        buildId: String,
        additionalOptions: ElementAdditionalOptions?,
        variables: Map<String, String>,
        message: StringBuilder = StringBuilder()
    ): Boolean {

        var skip = true // 所有自定义条件都满足，则跳过
        // 自定义变量全部满足时不运行
        if (skipWhenCustomVarMatch(additionalOptions)) {
            message.append(
                I18nUtil.getCodeLanMessage(BK_WHEN_THE_CUSTOM_VARIABLES_ARE_ALL_SATISFIED)
            )
            for (names in additionalOptions!!.customVariables!!) {
                val key = names.key
                val value = EnvUtils.parseEnv(names.value, variables)
                val existValue = variables[key]
                if (value != existValue) {
                    message.append("key=$key, expect=$value, actual=$existValue, (expect != actual)=true, skip=false")
                    logger.info("[$buildId]|NOT_MATCH|key=$key|exists=$existValue|exp=$value|o=${names.value}")
                    skip = false
                    break
                }
            }
            return skip
        }

        skip = false // 所有自定义条件都满足，则不能跳过
        // 自定义变量全部满足时运行
        if (notSkipWhenCustomVarMatch(additionalOptions)) {
            message.append(
                I18nUtil.getCodeLanMessage(BK_CUSTOM_VARIABLES_ARE_ALL_SATISFIED)
            )
            for (names in additionalOptions!!.customVariables!!) {
                val key = names.key
                val value = EnvUtils.parseEnv(names.value, variables)
                val existValue = variables[key]
                if (value != existValue) {
                    message.append("key=$key, expect=$value, actual=$existValue, (expect != actual)=true, skip=true")
                    logger.info("[$buildId]|MATCH|key=$key|exists=$existValue|exp=$value|o=${names.value}")
                    skip = true
                    break
                }
            }
        }
        return skip
    }

    private fun notSkipWhenCustomVarMatch(additionalOptions: ElementAdditionalOptions?) =
        additionalOptions != null && additionalOptions.runCondition == RunCondition.CUSTOM_VARIABLE_MATCH &&
            additionalOptions.customVariables != null && additionalOptions.customVariables!!.isNotEmpty()

    private fun skipWhenCustomVarMatch(additionalOptions: ElementAdditionalOptions?) =
        additionalOptions != null && additionalOptions.runCondition == RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN &&
            additionalOptions.customVariables != null && additionalOptions.customVariables!!.isNotEmpty()

    /**
     * 对构建[buildId]的任务的流程控制条件[additionalOptions]进行排查，结合当前容器状态[containerFinalStatus]
     * 以及是否当前容器存在失败任务[hasFailedTaskInSuccessContainer]等条件，排查[RunCondition]下各个条件
     * 是否满足跳过条件，如果满足返回true,表示跳过。
     *
     * 判定按「运行意图」组织，每个意图各自声明它对前序状态的要求，不再有一条拦在中间的
     * 「容器失败或取消就跳过」兜底分支——条件判定一律以构建环境已正常启动为前提，
     * 要不要跑只取决于本意图对前序成败的要求。
     *
     * 意图轴与各取值的对应：
     * - 前序成功时：[RunCondition.PRE_TASK_SUCCESS]（及未配置）
     * - 前序失败时：[RunCondition.PRE_TASK_FAILED_ONLY]（含失败自动跳过）、
     *   [RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP]（不含失败自动跳过）
     * - 总是运行：[RunCondition.PRE_TASK_FAILED_BUT_CANCEL]（取消时不跑）、
     *   [RunCondition.PRE_TASK_FAILED_EVEN_CANCEL]（取消时也跑）
     * - 自定义条件：[RunCondition.CUSTOM_CONDITION_MATCH]
     * - 自定义变量匹配：[RunCondition.CUSTOM_VARIABLE_MATCH] 与
     *   [RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN]，保持逐项字符串比较，不译成表达式
     */
    fun checkTaskSkip(
        buildId: String,
        additionalOptions: ElementAdditionalOptions?,
        containerFinalStatus: BuildStatus,
        variables: Map<String, String>,
        hasFailedTaskInSuccessContainer: Boolean,
        preTasks: List<PreTaskStatus> = emptyList(),
        message: StringBuilder = StringBuilder()
    ): Boolean {
        message.append(
            I18nUtil.getCodeLanMessage(BK_CHECK_TASK_RUN_CONDITION)
        )
        if (!isEnable(additionalOptions)) {
            message.append(
                I18nUtil.getCodeLanMessage(BK_TASK_DISABLED)
            )
            return true
        }
        // 把落库枚举拆回正交的几维。变量匹配保持原比较，不在这里译成表达式
        val intent = RunConditionNormalizer.resolve(additionalOptions)
        val canceled = containerFinalStatus.isCancel()
        val preFailed = preTaskFailed(
            intent = intent,
            preTasks = preTasks,
            containerFinalStatus = containerFinalStatus,
            hasFailedTaskInSuccessContainer = hasFailedTaskInSuccessContainer
        )

        val skip = when (intent.intent) {
            // [前序失败时]。取消不是失败：除非显式开了「取消时也运行」，否则取消一律不跑——
            // 构建机此时已关机，若仍领取这类插件会停在 QUEUE_CACHE，Job 无法结束。
            StepRunIntent.FAILURE -> {
                val result = if (canceled) !intent.runOnCancel() else !preFailed
                message.append(
                    "${I18nUtil.getCodeLanMessage(
                        if (intent.preFailureSkippedAs == PreFailureSkippedAs.FAILURE) {
                            BK_ONLY_WHEN_PREVIOUS_TASK_HAS_FAILED
                        } else {
                            BK_ONLY_WHEN_PREVIOUS_TASK_FAILED_EXCEPT_SKIP
                        }
                    )} skip=$result"
                )
                result
            }
            // [总是运行]。超时属失败终态，由意图本身覆盖，只有取消才看开关
            StepRunIntent.ALWAYS -> {
                val result = canceled && !intent.runOnCancel()
                message.append(
                    I18nUtil.getCodeLanMessage(
                        if (intent.runOnCancel()) BK_RUNS_EVEN_IF_CANCELED else BK_IT_DOES_NOT_RUN_UNLESS_IT_IS_CANCELED
                    ) + " skip=$result"
                )
                result
            }
            // [自定义条件]。是否隐含「前序成功」这道门，取决于表达式自己写没写状态函数：
            //  - 不含：维持存量行为，前序已失败或已取消时直接跳过，连求值都不做。这一支占存量的全部，
            //    等价于自动 AND 上 success()，所以对存量是零变更；
            //  - 含：免掉这道门，把判定权整个交给表达式，否则 cancelled() 永远等不到成立的机会。
            // 口径与 GitHub Actions 的绑定规则相同。
            StepRunIntent.EXPRESSION -> {
                when {
                    !StatusFunctions.referencedIn(intent.expression) && (preFailed || canceled) -> {
                        message.append(preStateMessage(containerFinalStatus))
                        true
                    }
                    else -> !evalExpressionAsCode(
                        customCondition = intent.expression,
                        buildId = buildId,
                        variables = variables,
                        message = message,
                        preFailed = preFailed,
                        canceled = canceled
                    )
                }
            }
            // [自定义变量全部满足时运行 / 不运行]。比较口径与现网一致：原始 key 逐项相等，
            // 期望值先做 ${} 替换。容器已失败或已取消时直接跳过，不对变量求值。
            StepRunIntent.VARIABLE_MATCH -> {
                if (preFailed || canceled) {
                    message.append(preStateMessage(containerFinalStatus))
                    true
                } else {
                    checkCustomVariableSkip(
                        buildId = buildId,
                        additionalOptions = additionalOptions,
                        variables = variables,
                        message = message
                    )
                }
            }
            // [前序成功时]（含未配置），以及内置插件 post-action 专用的 PARENT_TASK_* ——
            // 后者真正的调度与否由 TaskUtils.getPostTaskAndExecuteFlag 按父插件状态决定，
            // 此处与「前序成功时」同判，维持其在快速跳过预检里的既有行为
            StepRunIntent.SUCCESS, StepRunIntent.POST_ACTION -> {
                val result = preFailed || canceled
                if (result) {
                    message.append(preStateMessage(containerFinalStatus))
                } else {
                    message.clear()
                }
                result
            }
        }

        // `when` 与 `if` 同现时取 AND：意图先给出调度机会，表达式再收窄。
        // 意图没通过就不必再求值，也避免在日志里多打一条看着像是另一个跳过原因的记录。
        if (!skip && intent.andExpression != null) {
            return !evalExpressionAsCode(
                customCondition = intent.andExpression,
                buildId = buildId,
                variables = variables,
                message = message,
                preFailed = preFailed,
                canceled = canceled
            )
        }
        return skip
    }

    /**
     * 本步骤没跑，是因为前面出了什么事。
     *
     * 现网这四种情形共用一句「Job失败或被取消」，用户从构建详情里分不出到底是哪一种，
     * 而这四种的后续处理完全不同（取消要看是谁点的、超时要调时长、环境不可用要看构建机）。
     * 判定现场这几个信号本来就是分开的，这里按终态各给各的话。
     */
    private fun preStateMessage(containerFinalStatus: BuildStatus): String = I18nUtil.getCodeLanMessage(
        when {
            containerFinalStatus.isCancel() -> BK_PRE_STATE_BUILD_CANCELED
            containerFinalStatus.isTimeout() -> BK_PRE_STATE_PRE_TASK_TIMEOUT
            // TERMINATE 同时兜着 Job 执行超时与构建环境不可用，两者在这里无法再细分
            containerFinalStatus.isTerminate() -> BK_PRE_STATE_JOB_TERMINATED
            else -> BK_PRE_STATE_PRE_TASK_FAILED
        }
    )

    /**
     * 判断前序范围内是否已出现「算失败」的步骤。
     *
     * 指定了前序范围（`from`）时只看名单内步骤的终态；未指定时沿用容器聚合状态，
     * 再按本步骤的 [PreFailureSkippedAs] 口径补上「被跳过的失败」——聚合状态读不出这一维。
     *
     * 未指定范围时之所以不改为纯逐步骤扫描：容器可能因构建机启动失败等非用户步骤的原因置为失败，
     * 这类失败不在用户步骤列表里，纯扫描会把它漏掉，导致「前序失败时」的步骤在环境故障时反而不跑。
     */
    private fun preTaskFailed(
        intent: ResolvedRunIntent,
        preTasks: List<PreTaskStatus>,
        containerFinalStatus: BuildStatus,
        hasFailedTaskInSuccessContainer: Boolean
    ): Boolean {
        if (intent.fromStepIds.isNotEmpty()) {
            return preTasks.any {
                it.stepId != null && it.stepId in intent.fromStepIds && it.countsAsFailure(intent.preFailureSkippedAs)
            }
        }
        if (containerFinalStatus.isFailure()) {
            return true
        }
        return intent.preFailureSkippedAs == PreFailureSkippedAs.FAILURE &&
            (hasFailedTaskInSuccessContainer || preTasks.any { it.failureSkipped })
    }

    // Job是否跳过判断
    fun checkJobSkipCondition(
        conditions: List<NameAndValue>,
        variables: Map<String, String>,
        buildId: String,
        runCondition: JobRunCondition,
        customCondition: String? = null,
        message: StringBuilder = StringBuilder()
    ): Boolean {
        message.append(
            I18nUtil.getCodeLanMessage(BK_CHECK_JOB_RUN_CONDITION)
        )
        var skip = when (runCondition) {
            JobRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> {
                message.append(
                    I18nUtil.getCodeLanMessage(BK_WHEN_THE_CUSTOM_VARIABLES_ARE_ALL_SATISFIED)
                )
                true
            } // 条件匹配就跳过
            JobRunCondition.CUSTOM_VARIABLE_MATCH -> {
                message.append(I18nUtil.getCodeLanMessage(BK_CUSTOM_VARIABLES_ARE_ALL_SATISFIED))
                false
            } // 条件全匹配就运行
            JobRunCondition.CUSTOM_CONDITION_MATCH -> { // 满足以下自定义条件时运行
                return !evalExpressionAsCode(customCondition, buildId, variables, message)
            }
            else -> {
                message.append(runCondition)
                return false
            } // 其它类型直接返回不跳过
        }
        for (names in conditions) {
            val key = names.key
            val value = names.value
            val existValue = variables[key]
            val env = EnvUtils.parseEnv(value, variables)
            if (env != existValue) {
                skip = !skip // 不满足则取反
                logger.info("[$buildId]|JOB_CONDITION|$skip|$runCondition|key=$key|actual=$existValue|expect=$value")
                message.append("\nkey=$key, expect=$env, actual=$existValue, (expect!=actual)=true, skip=$skip")
                break
            }
        }
        return skip
    }

    // stage是否跳过判断
    fun checkStageSkipCondition(
        conditions: List<NameAndValue>,
        variables: Map<String, String>,
        buildId: String,
        runCondition: StageRunCondition,
        customCondition: String? = null,
        message: StringBuilder = StringBuilder()
    ): Boolean {
        var skip = when (runCondition) {
            StageRunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> true // 条件匹配就跳过
            StageRunCondition.CUSTOM_VARIABLE_MATCH -> false // 条件全匹配就运行
            StageRunCondition.CUSTOM_CONDITION_MATCH -> { // 满足以下自定义条件时运行
                return !evalExpressionAsCode(customCondition, buildId, variables, message)
            }
            else -> return false // 其它类型直接返回不跳过
        }
        for (names in conditions) {
            val key = names.key
            val value = names.value
            val existValue = variables[key]
            if (value != existValue) {
                skip = !skip // 不满足则取反
                logger.info("[$buildId]|STAGE_CONDITION|$skip|$runCondition|key=$key|actual=$existValue|expect=$value")
                break
            }
        }
        return skip
    }

    /**
     * @param preFailed / [canceled] 仅步骤级传入，用于把前序状态喂给 success()/failure()/cancelled()/always()。
     *   两者都为 null 时不注册状态函数，表达式里写了这几个名字会按未知函数报错——
     *   Stage / Job 条件走的就是这条路：那两层没有「前序步骤」这个概念，给了函数也编不出值。
     */
    private fun evalExpressionAsCode(
        customCondition: String?,
        buildId: String,
        variables: Map<String, String>,
        message: StringBuilder,
        preFailed: Boolean? = null,
        canceled: Boolean? = null
    ): Boolean {
        return if (!customCondition.isNullOrBlank()) {
            try {
                // 新增的表达式调用需要去掉兼容老流水线变量
                val variablesWithOutOld = variables.filter { PipelineVarUtil.oldVarToNewVar(it.key) == null }
                val expressionResult = ExpressionParser.evaluateByMap(
                    expression = EnvReplacementParser.parse(customCondition, variables),
                    contextMap = variablesWithOutOld,
                    fetchValue = false,
                    functions = if (preFailed != null && canceled != null) {
                        StatusFunctions.of(preFailed = preFailed, canceled = canceled)
                    } else {
                        null
                    }
                )
                logger.info(
                    "[$buildId]|EXPRESSION_CONDITION|skip|CUSTOM_CONDITION_MATCH|expression=$customCondition" +
                        "|result=$expressionResult"
                )
                val resultIsTrue = if (expressionResult is EvaluationResult) {
                    expressionResult.equalsTrue
                } else {
                    expressionResult.toString().toBoolean()
                }
                message.append(
                    I18nUtil.getCodeLanMessage(
                        messageCode = ProcessMessageCode.BK_PIPELINE_RUN_CONDITION_RESULT,
                        language = I18nUtil.getDefaultLocaleLanguage(),
                        params = arrayOf(customCondition, resultIsTrue.toString())
                    ) + if (!resultIsTrue) {
                        I18nUtil.getCodeLanMessage(
                            messageCode = ProcessMessageCode.BK_PIPELINE_RUN_CONDITION_NOT_MATCH,
                            language = I18nUtil.getDefaultLocaleLanguage()
                        )
                    } else {
                        ""
                    }
                )
                resultIsTrue
            } catch (ignore: Throwable) {
                // 异常，则任务表达式为false
                logger.warn(
                    "[$buildId]|EXPRESSION_CONDITION|skip|CUSTOM_CONDITION_MATCH|expression=$customCondition" +
                        "|result=exception: ${ignore.message}",
                    ignore
                )
                message.append(
                    I18nUtil.getCodeLanMessage(
                        messageCode = ProcessMessageCode.BK_PIPELINE_RUN_CONDITION_WITH_ERROR,
                        language = I18nUtil.getDefaultLocaleLanguage(),
                        params = arrayOf(ignore.message ?: "")
                    ) + I18nUtil.getCodeLanMessage(
                        messageCode = ProcessMessageCode.BK_PIPELINE_RUN_CONDITION_NOT_MATCH,
                        language = I18nUtil.getDefaultLocaleLanguage()
                    )
                )
                false
            }
        } else {
            // 空表达式也认为是false
            logger.info("[$buildId]|EXPRESSION_CONDITION|skip|CUSTOM_CONDITION_MATCH|expression is empty!")
            message.append("Custom condition is empty, will be skipped!")
            false
        }
    }

    fun checkContainerFailure(c: PipelineBuildContainer) =
        c.status.isFailure() && c.controlOption.jobControlOption.continueWhenFailed != true
}
