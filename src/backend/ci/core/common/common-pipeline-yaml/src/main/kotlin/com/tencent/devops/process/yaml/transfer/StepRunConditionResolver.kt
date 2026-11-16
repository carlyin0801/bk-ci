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

package com.tencent.devops.process.yaml.transfer

import com.tencent.devops.common.api.constant.CommonMessageCode.YAML_NOT_VALID
import com.tencent.devops.common.pipeline.pojo.element.OnCancel
import com.tencent.devops.common.pipeline.pojo.element.PreFailureSkippedAs
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.common.pipeline.pojo.transfer.IfType
import com.tencent.devops.process.yaml.v3.models.IfField.Mode
import com.tencent.devops.process.yaml.v3.models.WhenField
import com.tencent.devops.process.yaml.v3.models.step.Step

/**
 * 从 YAML 的 `when` / `if` 两个键决定落库的 [RunCondition]。
 *
 * 抽成独立对象是为了能脱离 Spring 容器单测——[ElementTransfer] 要四个注入依赖才能构造，
 * 而这段决策本身是纯数据到数据的映射，是本次改造里分支最密、最需要逐格锁住的一处。
 *
 * `if` 在现网有三种形态，这里都要认：
 * 1. 字符串 = 裸表达式（自定义条件）；
 * 2. 字符串 = `ALWAYS` / `FAILURE` / `ALWAYS_UNLESS_CANCELLED` 三个旧魔术串，表达的是意图；
 * 3. 对象 = 变量匹配（`{ mode, params }`）。
 *
 * 目标态下线形态 2（改用 `when`），保留 1 和 3。形态 3 是现网稳定的 PAC 写法，
 * 与 `when` 是两个正交的键，不并入自定义条件。
 */
internal object StepRunConditionResolver {

    /**
     * @param preFailureSkippedAs 已从 `when.skipped-as` 解出的值，未写时为 null
     * @param onCancel 已从 `when.on-cancel` 解出的值，未写时为 null
     * @throws PipelineTransferException `when` 与 `if` 表达的条件无法同时落进单值枚举时
     */
    fun resolve(
        step: Step,
        preFailureSkippedAs: PreFailureSkippedAs?,
        onCancel: OnCancel?
    ): RunCondition {
        val plainExpression = plainExpressionOf(step)
        val variableMatch = variableMatchOf(step, plainExpression)
        val whenStatus = step.whenField?.status

        // when 是运行意图的新入口；没写 when 时才去读旧的 if 魔术串
        val runCondition = when {
            // 对象形态的变量匹配优先于 when.status。`when: success` 与它同现时仍是变量匹配：
            // 变量匹配本来就隐含「前序成功」这道门，不会改变比较结果
            variableMatch != null -> variableMatch

            // `when: success` + `if: 表达式` 取 AND，而这个组合正好能等价落到「自定义条件」：
            // 表达式里还读不到前序状态，自定义条件本就隐含「前序成功」这道门，两者判定逐格相同
            whenStatus == WhenField.Status.SUCCESS ->
                if (plainExpression != null) RunCondition.CUSTOM_CONDITION_MATCH else RunCondition.PRE_TASK_SUCCESS

            whenStatus == WhenField.Status.FAILURE ->
                if (preFailureSkippedAs == PreFailureSkippedAs.FAILURE) {
                    RunCondition.PRE_TASK_FAILED_ONLY
                } else {
                    RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP
                }

            // always 的 on-cancel 默认是 continue（取消也跑），只有显式写 skip 才不跑。
            // 这与旧写法 `if: ALWAYS` 的语义一致，两条读入路径必须给出同一个结果
            whenStatus == WhenField.Status.ALWAYS ->
                if (onCancel == OnCancel.SKIP) {
                    RunCondition.PRE_TASK_FAILED_BUT_CANCEL
                } else {
                    RunCondition.PRE_TASK_FAILED_EVEN_CANCEL
                }

            step.ifField == null -> RunCondition.PRE_TASK_SUCCESS

            IfType.ALWAYS_UNLESS_CANCELLED.name == step.ifField.expression ->
                RunCondition.PRE_TASK_FAILED_BUT_CANCEL

            IfType.ALWAYS.name == step.ifField.expression ->
                RunCondition.PRE_TASK_FAILED_EVEN_CANCEL

            IfType.FAILURE.name == step.ifField.expression ->
                RunCondition.PRE_TASK_FAILED_ONLY

            !step.ifField.expression.isNullOrBlank() -> RunCondition.CUSTOM_CONDITION_MATCH

            else -> RunCondition.PRE_TASK_SUCCESS
        }

        checkWhenAndIfCompatible(
            step = step,
            whenStatus = whenStatus,
            plainExpression = plainExpression,
            variableMatch = variableMatch,
            runCondition = runCondition
        )
        return runCondition
    }

    /** `if` 里写的是不是一个真表达式——排除三个旧魔术串，它们表达的是意图而不是条件 */
    fun plainExpressionOf(step: Step): String? = step.ifField?.expression?.takeIf {
        it.isNotBlank() && it != IfType.ALWAYS.name &&
            it != IfType.ALWAYS_UNLESS_CANCELLED.name && it != IfType.FAILURE.name
    }

    private fun variableMatchOf(step: Step, plainExpression: String?): RunCondition? {
        // 表达式与对象形态互斥，有表达式时不当变量匹配看
        if (plainExpression != null) {
            return null
        }
        return when (step.ifField?.mode) {
            Mode.RUN_WHEN_ALL_PARAMS_MATCH -> RunCondition.CUSTOM_VARIABLE_MATCH
            Mode.NOT_RUN_WHEN_ALL_PARAMS_MATCH -> RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN
            else -> null
        }
    }

    /**
     * `failure` / `always` 与表达式、以及与变量匹配对象，落库模型都承载不了（runCondition 是单值枚举）。
     * 与其把其中一边悄悄丢掉、让步骤比用户预期跑得更频繁，不如在这里明确报错。
     * `when: success` 除外：它就是这两类条件本来就隐含的前置门，合并不改变判定结果。
     */
    private fun checkWhenAndIfCompatible(
        step: Step,
        whenStatus: WhenField.Status?,
        plainExpression: String?,
        variableMatch: RunCondition?,
        runCondition: RunCondition
    ) {
        val stepName = step.name ?: step.id
        if (plainExpression != null && whenStatus != null && runCondition != RunCondition.CUSTOM_CONDITION_MATCH) {
            throw PipelineTransferException(
                YAML_NOT_VALID,
                arrayOf(
                    "step($stepName): `when: ${whenStatus.alias}` and `if` cannot be used " +
                        "together, please merge the condition into `if` or remove one of them"
                )
            )
        }
        if (variableMatch != null &&
            (whenStatus == WhenField.Status.FAILURE || whenStatus == WhenField.Status.ALWAYS)
        ) {
            throw PipelineTransferException(
                YAML_NOT_VALID,
                arrayOf(
                    "step($stepName): `when: ${whenStatus.alias}` and variable-match `if` " +
                        "cannot be used together, please keep only one of them"
                )
            )
        }
    }
}
