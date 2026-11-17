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

package com.tencent.devops.common.pipeline.pojo.element

import com.tencent.devops.common.expression.expression.functions.StatusFunctions

/**
 * 一个步骤的运行意图在判定时的归一形态。
 *
 * [RunCondition] 是落库形态，它把「看什么状态」「取消算不算」「被跳过的失败算不算」几个维度压在了一个枚举里；
 * 本类把它们拆回正交的几维，供引擎判定与 YAML 序列化共用，避免两边各自解释一遍枚举含义。
 */
data class ResolvedRunIntent(
    /** 意图轴 */
    val intent: StepRunIntent,
    /** 前序范围：参与判定的前序步骤 stepId；空表示同 Job 全部前序 */
    val fromStepIds: List<String>,
    /** 前序失败被跳过时，视为成功还是视为失败 */
    val preFailureSkippedAs: PreFailureSkippedAs,
    /** 本 Job 被取消时是否仍调度 */
    val onCancel: OnCancel,
    /** 自定义条件表达式，仅 [StepRunIntent.EXPRESSION] 有值 */
    val expression: String?,
    /** 与意图轴取 AND 的附加表达式（YAML 里 `when` 与 `if` 同现），意图轴为表达式时恒为空 */
    val andExpression: String?
) {
    /**
     * 取消时是否仍调度。
     *
     * 自定义条件走表达式自述：`cancelled()` / `always()` 要在取消后拿到求值机会，
     * 否则函数给了也白给。其余写法取消时恒求出 false，不必多给一次调度机会。
     * 这同时覆盖认领豁免（TaskUtils）与容器终态修正（taskEvenCancel 标志）两处，
     * 两处都读本方法，不会只改一边。
     *
     * [andExpression] 不参与：它是 AND 上去的收窄项，只会让步骤跑得更少，
     * 给不出意图轴之外的调度机会。
     */
    fun runOnCancel() = when (intent) {
        StepRunIntent.EXPRESSION -> StatusFunctions.mayRunOnCancel(expression)
        else -> onCancel == OnCancel.CONTINUE
    }
}

/**
 * 把 [ElementAdditionalOptions] 解析为 [ResolvedRunIntent]。
 *
 * 自定义变量匹配保持为 [StepRunIntent.VARIABLE_MATCH]，不译成表达式。
 * 它比对的是变量表里的原始 key，期望值还会先做 `${}` 替换；
 * 译成 `key == 'value'` 后，带点的 key、空串期望值、`${}` 占位都会和现网比较结果不一致。
 *
 * 落库枚举与意图的对应（显式字段优先于枚举推导出的默认值）：
 *
 * | runCondition | intent | preFailureSkippedAs 默认 | onCancel 默认 |
 * |---|---|---|---|
 * | PRE_TASK_SUCCESS / 未配置 | SUCCESS | SUCCESS | SKIP |
 * | PRE_TASK_FAILED_ONLY | FAILURE | **FAILURE** | SKIP |
 * | PRE_TASK_FAILED_ONLY_EXCEPT_SKIP | FAILURE | SUCCESS | SKIP |
 * | PRE_TASK_FAILED_BUT_CANCEL | ALWAYS | SUCCESS | SKIP |
 * | PRE_TASK_FAILED_EVEN_CANCEL | ALWAYS | SUCCESS | CONTINUE |
 * | CUSTOM_CONDITION_MATCH | EXPRESSION | SUCCESS | SKIP |
 * | CUSTOM_VARIABLE_MATCH(_NOT_RUN) | VARIABLE_MATCH | SUCCESS | SKIP |
 * | PARENT_TASK_* | POST_ACTION | —— | —— |
 *
 * `PRE_TASK_FAILED_ONLY` 的 preFailureSkippedAs 默认值与新建步骤相反，这是存量行为：
 * 它恒把失败自动跳过计入失败且不可关。不在此回填即会静默改变存量行为。
 */
object RunConditionNormalizer {

    @Suppress("ComplexMethod")
    fun resolve(additionalOptions: ElementAdditionalOptions?): ResolvedRunIntent {
        val runCondition = additionalOptions?.runCondition
        val intent = when (runCondition) {
            RunCondition.PRE_TASK_FAILED_ONLY,
            RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP -> StepRunIntent.FAILURE

            RunCondition.PRE_TASK_FAILED_BUT_CANCEL,
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL -> StepRunIntent.ALWAYS

            RunCondition.CUSTOM_CONDITION_MATCH -> StepRunIntent.EXPRESSION

            RunCondition.CUSTOM_VARIABLE_MATCH,
            RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> StepRunIntent.VARIABLE_MATCH

            RunCondition.PARENT_TASK_CANCELED_OR_TIMEOUT,
            RunCondition.PARENT_TASK_FINISH -> StepRunIntent.POST_ACTION

            RunCondition.PRE_TASK_SUCCESS, null -> StepRunIntent.SUCCESS
        }
        // 显式字段优先；没有则按落库枚举推导存量默认值
        val preFailureSkippedAs = additionalOptions?.preFailureSkippedAs ?: when (runCondition) {
            RunCondition.PRE_TASK_FAILED_ONLY -> PreFailureSkippedAs.FAILURE
            else -> PreFailureSkippedAs.SUCCESS
        }
        val onCancel = additionalOptions?.onCancel ?: when (runCondition) {
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL -> OnCancel.CONTINUE
            else -> OnCancel.SKIP
        }
        return ResolvedRunIntent(
            intent = intent,
            fromStepIds = additionalOptions?.fromStepIds?.filter { it.isNotBlank() } ?: emptyList(),
            preFailureSkippedAs = preFailureSkippedAs,
            onCancel = onCancel,
            expression = if (intent == StepRunIntent.EXPRESSION) additionalOptions?.customCondition else null,
            // 意图轴本身就是表达式时不存在「再 AND 一个表达式」，两者会合并在 customCondition 里
            andExpression = if (intent == StepRunIntent.EXPRESSION) {
                null
            } else {
                additionalOptions?.ifExpression?.takeIf { it.isNotBlank() }
            }
        )
    }
}
