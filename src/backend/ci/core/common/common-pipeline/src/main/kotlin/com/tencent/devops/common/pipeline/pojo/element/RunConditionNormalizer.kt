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

import com.tencent.devops.common.pipeline.NameAndValue

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
    /**
     * 存量变量匹配配置未能等价归一为表达式时，这里带出原始变量列表，
     * 引擎按旧的逐个比较逻辑判定。正常情况下为 null。
     */
    val legacyVariables: List<NameAndValue>?,
    /** 变量匹配语义：true=全部满足时运行，false=全部满足时不运行。仅 [legacyVariables] 非空时有意义 */
    val legacyMatchToRun: Boolean = true
) {
    /** 取消时是否仍调度 */
    fun runOnCancel() = onCancel == OnCancel.CONTINUE
}

/**
 * 把 [ElementAdditionalOptions] 解析为 [ResolvedRunIntent]，并把存量的「自定义变量匹配」归一到 if 表达式。
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
 * | CUSTOM_VARIABLE_MATCH(_NOT_RUN) | EXPRESSION（归一为表达式） | SUCCESS | SKIP |
 * | PARENT_TASK_* | POST_ACTION | —— | —— |
 *
 * `PRE_TASK_FAILED_ONLY` 的 preFailureSkippedAs 默认值与新建步骤相反，这是存量行为：
 * 它恒把失败自动跳过计入失败且不可关。不在此回填即会静默改变存量行为。
 */
object RunConditionNormalizer {

    private val IDENTIFIER_PATH = Regex("[A-Za-z_][A-Za-z0-9_]*(\\.[A-Za-z_][A-Za-z0-9_]*)*")

    @Suppress("DEPRECATION", "ComplexMethod")
    fun resolve(additionalOptions: ElementAdditionalOptions?): ResolvedRunIntent {
        val runCondition = additionalOptions?.runCondition
        val intent = when (runCondition) {
            RunCondition.PRE_TASK_FAILED_ONLY,
            RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP -> StepRunIntent.FAILURE

            RunCondition.PRE_TASK_FAILED_BUT_CANCEL,
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL -> StepRunIntent.ALWAYS

            RunCondition.CUSTOM_CONDITION_MATCH,
            RunCondition.CUSTOM_VARIABLE_MATCH,
            RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> StepRunIntent.EXPRESSION

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
        val normalized = toExpression(runCondition, additionalOptions?.customVariables)
        // 变量匹配未能等价归一时，带出原始变量让引擎走旧比较。
        // 这里必须给到非 null 的空列表而不是直接取 customVariables——变量列表为空时
        // 旧逻辑是「不跳过」，而空表达式在 evalExpressionAsCode 里恒为 false 即「跳过」，两者相反。
        val legacyVariables = if (isVariableMatch(runCondition) && normalized == null) {
            additionalOptions?.customVariables ?: emptyList()
        } else {
            null
        }
        return ResolvedRunIntent(
            intent = intent,
            fromStepIds = additionalOptions?.fromStepIds?.filter { it.isNotBlank() } ?: emptyList(),
            preFailureSkippedAs = preFailureSkippedAs,
            onCancel = onCancel,
            expression = if (intent == StepRunIntent.EXPRESSION) {
                normalized ?: additionalOptions?.customCondition
            } else {
                null
            },
            legacyVariables = legacyVariables,
            legacyMatchToRun = runCondition == RunCondition.CUSTOM_VARIABLE_MATCH
        )
    }

    @Suppress("DEPRECATION")
    private fun isVariableMatch(runCondition: RunCondition?) =
        runCondition == RunCondition.CUSTOM_VARIABLE_MATCH ||
            runCondition == RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN

    /**
     * 把变量匹配配置翻译成等价表达式；非变量匹配或无法保证等价时返回 null。
     *
     * 变量匹配比对的是构建变量表里的原始 key，而表达式求值用的就是同一张表、同一个 key 作为上下文名，
     * 所以 `KEY == 'value'` 与原判定逐格等价。
     *
     * 以下三种情形转换后行为会变，宁可不转，由调用方继续走旧的逐个比较：
     * - key 不是合法的表达式标识符（含中划线、以数字开头等），表达式引擎解析不了；
     * - 期望值为空串——变量缺失时旧逻辑判「不相等」，而表达式引擎把缺失上下文视为空串判「相等」；
     * - 期望值里含 `${...}` 老式变量占位——求值前会对整串做一次替换，
     *   替换结果若带单引号会破坏字面量，而旧逻辑的字符串比较不存在这个风险。
     */
    @Suppress("DEPRECATION")
    fun toExpression(runCondition: RunCondition?, customVariables: List<NameAndValue>?): String? {
        val matchToRun = when (runCondition) {
            RunCondition.CUSTOM_VARIABLE_MATCH -> true
            RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN -> false
            else -> return null
        }
        if (customVariables.isNullOrEmpty()) {
            return null
        }
        val clauses = customVariables.map { pair ->
            val key = pair.key
            val value = pair.value
            if (key.isNullOrBlank() || !IDENTIFIER_PATH.matches(key)) {
                return null
            }
            if (value.isNullOrBlank() || value.contains("\${")) {
                return null
            }
            "$key == '${value.replace("'", "''")}'"
        }
        val allMatch = clauses.joinToString(" && ")
        return if (matchToRun) allMatch else "!($allMatch)"
    }
}
