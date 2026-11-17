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

import com.tencent.devops.common.pipeline.pojo.element.OnCancel
import com.tencent.devops.common.pipeline.pojo.element.PreFailureSkippedAs
import com.tencent.devops.common.pipeline.pojo.element.RunCondition
import com.tencent.devops.process.yaml.v3.models.IfField
import com.tencent.devops.process.yaml.v3.models.WhenField
import com.tencent.devops.process.yaml.v3.models.step.Step
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StepRunConditionResolverTest {

    private fun step(ifField: IfField? = null, whenField: WhenField? = null) = Step(
        name = "demo",
        id = null,
        ifField = ifField,
        whenField = whenField,
        uses = "demo@latest",
        with = null,
        continueOnError = null,
        retryTimes = null,
        run = null,
        runAdditionalOptions = null,
        checkout = null
    )

    private fun resolve(
        ifField: IfField? = null,
        whenField: WhenField? = null,
        preFailureSkippedAs: PreFailureSkippedAs? = null,
        onCancel: OnCancel? = null
    ) = full(ifField, whenField, preFailureSkippedAs, onCancel).runCondition

    private fun full(
        ifField: IfField? = null,
        whenField: WhenField? = null,
        preFailureSkippedAs: PreFailureSkippedAs? = null,
        onCancel: OnCancel? = null
    ) = StepRunConditionResolver.resolve(step(ifField, whenField), preFailureSkippedAs, onCancel)

    private fun varMatch(run: Boolean) = IfField(
        mode = if (run) IfField.Mode.RUN_WHEN_ALL_PARAMS_MATCH else IfField.Mode.NOT_RUN_WHEN_ALL_PARAMS_MATCH,
        params = mapOf("DEPLOY" to "true")
    )

    @Test
    fun `no when and no if means previous success`() {
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, resolve())
    }

    @Test
    fun `when scalar maps to the matching intent`() {
        Assertions.assertEquals(
            RunCondition.PRE_TASK_SUCCESS,
            resolve(whenField = WhenField(status = WhenField.Status.SUCCESS))
        )
        // failure 的 skipped-as 默认 success，对应「不含失败自动跳过」
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP,
            resolve(whenField = WhenField(status = WhenField.Status.FAILURE))
        )
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_ONLY,
            resolve(
                whenField = WhenField(status = WhenField.Status.FAILURE),
                preFailureSkippedAs = PreFailureSkippedAs.FAILURE
            )
        )
        // always 的 on-cancel 默认 continue，等于旧的 EVEN_CANCEL
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL,
            resolve(whenField = WhenField(status = WhenField.Status.ALWAYS))
        )
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_BUT_CANCEL,
            resolve(whenField = WhenField(status = WhenField.Status.ALWAYS), onCancel = OnCancel.SKIP)
        )
    }

    @Test
    fun `legacy if magic string still readable without when`() {
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL,
            resolve(ifField = IfField(expression = "ALWAYS"))
        )
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_BUT_CANCEL,
            resolve(ifField = IfField(expression = "ALWAYS_UNLESS_CANCELLED"))
        )
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_ONLY,
            resolve(ifField = IfField(expression = "FAILURE"))
        )
    }

    @Test
    fun `plain expression becomes custom condition`() {
        Assertions.assertEquals(
            RunCondition.CUSTOM_CONDITION_MATCH,
            resolve(ifField = IfField(expression = "DEPLOY == 'true'"))
        )
        // when: success 是自定义条件本来就隐含的前置门，两者同现仍是自定义条件
        Assertions.assertEquals(
            RunCondition.CUSTOM_CONDITION_MATCH,
            resolve(
                ifField = IfField(expression = "DEPLOY == 'true'"),
                whenField = WhenField(status = WhenField.Status.SUCCESS)
            )
        )
    }

    @Test
    fun `variable match object stays variable match`() {
        Assertions.assertEquals(RunCondition.CUSTOM_VARIABLE_MATCH, resolve(ifField = varMatch(run = true)))
        Assertions.assertEquals(
            RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN,
            resolve(ifField = varMatch(run = false))
        )
    }

    @Test
    fun `variable match with when success stays variable match`() {
        // 变量匹配本来就隐含「前序成功」，合并不改变判定结果，不该报错也不该被 when 覆盖
        Assertions.assertEquals(
            RunCondition.CUSTOM_VARIABLE_MATCH,
            resolve(ifField = varMatch(run = true), whenField = WhenField(status = WhenField.Status.SUCCESS))
        )
    }

    @Test
    fun `variable match with when failure or always is rejected`() {
        listOf(WhenField.Status.FAILURE, WhenField.Status.ALWAYS).forEach { status ->
            listOf(true, false).forEach { run ->
                Assertions.assertThrows(PipelineTransferException::class.java) {
                    resolve(ifField = varMatch(run), whenField = WhenField(status = status))
                }
            }
        }
    }

    @Test
    fun `plain expression with when failure or always takes AND`() {
        // 意图落 runCondition，表达式落 andExpression，各占一格互不覆盖
        val failure = full(
            ifField = IfField(expression = "DEPLOY == 'true'"),
            whenField = WhenField(status = WhenField.Status.FAILURE)
        )
        Assertions.assertEquals(RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP, failure.runCondition)
        Assertions.assertEquals("DEPLOY == 'true'", failure.andExpression)

        val always = full(
            ifField = IfField(expression = "DEPLOY == 'true'"),
            whenField = WhenField(status = WhenField.Status.ALWAYS)
        )
        Assertions.assertEquals(RunCondition.PRE_TASK_FAILED_EVEN_CANCEL, always.runCondition)
        Assertions.assertEquals("DEPLOY == 'true'", always.andExpression)
    }

    @Test
    fun `merged custom condition does not also set andExpression`() {
        // when: success + 不含状态函数的表达式 ≡ 纯自定义条件，合并成一格即可
        val merged = full(
            ifField = IfField(expression = "DEPLOY == 'true'"),
            whenField = WhenField(status = WhenField.Status.SUCCESS)
        )
        Assertions.assertEquals(RunCondition.CUSTOM_CONDITION_MATCH, merged.runCondition)
        Assertions.assertNull(merged.andExpression)
    }

    @Test
    fun `when success plus status function is not merged`() {
        // 含状态函数的表达式免掉成功前置，若并成 CUSTOM_CONDITION_MATCH，`when: success` 就白写了
        val resolved = full(
            ifField = IfField(expression = "always()"),
            whenField = WhenField(status = WhenField.Status.SUCCESS)
        )
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, resolved.runCondition)
        Assertions.assertEquals("always()", resolved.andExpression)
    }

    @Test
    fun `status function alone is a valid if`() {
        // 落 CUSTOM_CONDITION_MATCH，由 ControlUtils 按「含状态函数则不叠加成功前置」求值
        Assertions.assertEquals(
            RunCondition.CUSTOM_CONDITION_MATCH,
            resolve(ifField = IfField(expression = "failure() || cancelled()"))
        )
    }

    @Test
    fun `on-cancel under success is rejected`() {
        listOf(OnCancel.CONTINUE, OnCancel.SKIP).forEach { onCancel ->
            Assertions.assertThrows(PipelineTransferException::class.java) {
                resolve(whenField = WhenField(status = WhenField.Status.SUCCESS), onCancel = onCancel)
            }
        }
        // failure / always 下合法
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL,
            resolve(whenField = WhenField(status = WhenField.Status.ALWAYS), onCancel = OnCancel.CONTINUE)
        )
    }

    @Test
    fun `skipped-as under non-failure is rejected`() {
        listOf(WhenField.Status.SUCCESS, WhenField.Status.ALWAYS).forEach { status ->
            Assertions.assertThrows(PipelineTransferException::class.java) {
                resolve(
                    whenField = WhenField(status = status),
                    preFailureSkippedAs = PreFailureSkippedAs.SUCCESS
                )
            }
        }
    }

    @Test
    fun `omitted when status counts as success and still rejects attached keys`() {
        // schema 已把 status 列为必填，这里兜的是没过 schema 校验的入口
        Assertions.assertThrows(PipelineTransferException::class.java) {
            resolve(whenField = WhenField(from = listOf("build")), onCancel = OnCancel.CONTINUE)
        }
        Assertions.assertEquals(
            RunCondition.PRE_TASK_SUCCESS,
            resolve(whenField = WhenField(from = listOf("build")))
        )
    }

    @Test
    fun `attached keys without when do not reject legacy if or plain expression`() {
        // 附属键只会随 when 一起解析出来，这里防的是把 null status 当成 success 判
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL,
            resolve(ifField = IfField(expression = "ALWAYS"), onCancel = OnCancel.CONTINUE)
        )
    }

    @Test
    fun `when overrides the legacy if magic string`() {
        // 同时写了 when 和旧魔术串时以 when 为准；魔术串不是真表达式，不触发互斥报错
        Assertions.assertEquals(
            RunCondition.PRE_TASK_FAILED_EVEN_CANCEL,
            resolve(
                ifField = IfField(expression = "FAILURE"),
                whenField = WhenField(status = WhenField.Status.ALWAYS)
            )
        )
    }

    @Test
    fun `blank expression is not treated as an expression`() {
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, resolve(ifField = IfField(expression = "   ")))
        Assertions.assertNull(StepRunConditionResolver.plainExpressionOf(step(IfField(expression = "ALWAYS"))))
        Assertions.assertEquals(
            "DEPLOY == 'true'",
            StepRunConditionResolver.plainExpressionOf(step(IfField(expression = "DEPLOY == 'true'")))
        )
    }
}
