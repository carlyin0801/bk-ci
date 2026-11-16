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
    ) = StepRunConditionResolver.resolve(step(ifField, whenField), preFailureSkippedAs, onCancel)

    private fun varMatch(run: Boolean) = IfField(
        mode = if (run) IfField.Mode.RUN_WHEN_ALL_PARAMS_MATCH else IfField.Mode.NOT_RUN_WHEN_ALL_PARAMS_MATCH,
        params = mapOf("DEPLOY" to "true")
    )

    @Test
    fun `没有 when 也没有 if 时是前序成功`() {
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, resolve())
    }

    @Test
    fun `when 标量映射到对应意图`() {
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
    fun `旧 if 魔术串在没有 when 时仍然可读`() {
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
    fun `裸表达式落为自定义条件`() {
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
    fun `变量匹配的对象形态保持为变量匹配，不并入自定义条件`() {
        Assertions.assertEquals(RunCondition.CUSTOM_VARIABLE_MATCH, resolve(ifField = varMatch(run = true)))
        Assertions.assertEquals(
            RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN,
            resolve(ifField = varMatch(run = false))
        )
    }

    @Test
    fun `变量匹配与 when success 同现时仍是变量匹配`() {
        // 变量匹配本来就隐含「前序成功」，合并不改变判定结果，不该报错也不该被 when 覆盖
        Assertions.assertEquals(
            RunCondition.CUSTOM_VARIABLE_MATCH,
            resolve(ifField = varMatch(run = true), whenField = WhenField(status = WhenField.Status.SUCCESS))
        )
    }

    @Test
    fun `变量匹配与 when failure 或 always 同现要报错`() {
        listOf(WhenField.Status.FAILURE, WhenField.Status.ALWAYS).forEach { status ->
            listOf(true, false).forEach { run ->
                Assertions.assertThrows(PipelineTransferException::class.java) {
                    resolve(ifField = varMatch(run), whenField = WhenField(status = status))
                }
            }
        }
    }

    @Test
    fun `裸表达式与 when failure 或 always 同现要报错`() {
        listOf(WhenField.Status.FAILURE, WhenField.Status.ALWAYS).forEach { status ->
            Assertions.assertThrows(PipelineTransferException::class.java) {
                resolve(
                    ifField = IfField(expression = "DEPLOY == 'true'"),
                    whenField = WhenField(status = status)
                )
            }
        }
    }

    @Test
    fun `when 覆盖旧魔术串，不会两套语义打架`() {
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
    fun `空白表达式不当表达式看`() {
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, resolve(ifField = IfField(expression = "   ")))
        Assertions.assertNull(StepRunConditionResolver.plainExpressionOf(step(IfField(expression = "ALWAYS"))))
        Assertions.assertEquals(
            "DEPLOY == 'true'",
            StepRunConditionResolver.plainExpressionOf(step(IfField(expression = "DEPLOY == 'true'")))
        )
    }
}
