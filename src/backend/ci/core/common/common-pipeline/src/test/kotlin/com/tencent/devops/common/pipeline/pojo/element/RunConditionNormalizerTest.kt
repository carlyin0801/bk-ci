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

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.NameAndValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class RunConditionNormalizerTest {

    @Test
    fun `retired values fall back to the default intent`() {
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, RunCondition.parse("OTHER_TASK_RUNNING"))
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, RunCondition.parse("WHATEVER_UNKNOWN"))
        Assertions.assertNull(RunCondition.parse(null))
        Assertions.assertNull(RunCondition.parse(""))
        // 合法取值不受兜底影响
        RunCondition.values().forEach { Assertions.assertEquals(it, RunCondition.parse(it.name)) }

        val legacy = """{"enable":true,"runCondition":"OTHER_TASK_RUNNING","otherTask":"e-123"}"""
        val options = JsonUtil.to(legacy, ElementAdditionalOptions::class.java)
        Assertions.assertEquals(RunCondition.PRE_TASK_SUCCESS, options.runCondition)
    }

    @Test
    fun `variable match stays its own intent`() {
        // 带点的 key、空串、${} 占位、单引号若译成 key == 'value'，比较结果都会和现网逐项比较不一致
        listOf(
            listOf(NameAndValue("a", "b"), NameAndValue("c", "d")),
            listOf(NameAndValue("variables.DEPLOY", "true")),
            listOf(NameAndValue("my-key", "b")),
            listOf(NameAndValue("a", "")),
            listOf(NameAndValue("a", "\${BK_CI_BUILD_ID}")),
            listOf(NameAndValue("a", "it's"))
        ).forEach { variables ->
            listOf(
                RunCondition.CUSTOM_VARIABLE_MATCH,
                RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN
            ).forEach { condition ->
                val resolved = RunConditionNormalizer.resolve(options(condition, variables))
                Assertions.assertEquals(StepRunIntent.VARIABLE_MATCH, resolved.intent)
                Assertions.assertNull(resolved.expression)
            }
        }
    }

    @Test
    fun `stored enum resolves to intent and default dimensions`() {
        resolve(RunCondition.PRE_TASK_SUCCESS).let {
            Assertions.assertEquals(StepRunIntent.SUCCESS, it.intent)
            Assertions.assertEquals(PreFailureSkippedAs.SUCCESS, it.preFailureSkippedAs)
        }
        // 存量语义：恒把失败自动跳过计入失败，与新建步骤的默认值相反，必须回填
        resolve(RunCondition.PRE_TASK_FAILED_ONLY).let {
            Assertions.assertEquals(StepRunIntent.FAILURE, it.intent)
            Assertions.assertEquals(PreFailureSkippedAs.FAILURE, it.preFailureSkippedAs)
            Assertions.assertEquals(OnCancel.SKIP, it.onCancel)
        }
        resolve(RunCondition.PRE_TASK_FAILED_ONLY_EXCEPT_SKIP).let {
            Assertions.assertEquals(StepRunIntent.FAILURE, it.intent)
            Assertions.assertEquals(PreFailureSkippedAs.SUCCESS, it.preFailureSkippedAs)
        }
        resolve(RunCondition.PRE_TASK_FAILED_BUT_CANCEL).let {
            Assertions.assertEquals(StepRunIntent.ALWAYS, it.intent)
            Assertions.assertEquals(OnCancel.SKIP, it.onCancel)
            Assertions.assertFalse(it.runOnCancel())
        }
        resolve(RunCondition.PRE_TASK_FAILED_EVEN_CANCEL).let {
            Assertions.assertEquals(StepRunIntent.ALWAYS, it.intent)
            Assertions.assertEquals(OnCancel.CONTINUE, it.onCancel)
            Assertions.assertTrue(it.runOnCancel())
        }
        resolve(RunCondition.PARENT_TASK_FINISH).let {
            Assertions.assertEquals(StepRunIntent.POST_ACTION, it.intent)
        }
        Assertions.assertEquals(StepRunIntent.SUCCESS, RunConditionNormalizer.resolve(null).intent)
    }

    @Test
    fun `explicit fields override the enum defaults`() {
        // 旧枚举无法表达的新组合：前序失败时 + 取消也运行
        val failureOnCancel = RunConditionNormalizer.resolve(
            ElementAdditionalOptions(
                runCondition = RunCondition.PRE_TASK_FAILED_ONLY,
                onCancel = OnCancel.CONTINUE,
                preFailureSkippedAs = PreFailureSkippedAs.SUCCESS
            )
        )
        Assertions.assertEquals(StepRunIntent.FAILURE, failureOnCancel.intent)
        Assertions.assertTrue(failureOnCancel.runOnCancel())
        Assertions.assertEquals(PreFailureSkippedAs.SUCCESS, failureOnCancel.preFailureSkippedAs)

        val scoped = RunConditionNormalizer.resolve(
            ElementAdditionalOptions(
                runCondition = RunCondition.PRE_TASK_FAILED_ONLY,
                fromStepIds = listOf("build", "", "lint")
            )
        )
        Assertions.assertEquals(listOf("build", "lint"), scoped.fromStepIds)
    }

    private fun resolve(runCondition: RunCondition) =
        RunConditionNormalizer.resolve(ElementAdditionalOptions(runCondition = runCondition))

    @Test
    fun `empty variable match stays variable match`() {
        // 空表达式在 evalExpressionAsCode 里恒为 false 即「跳过」，而变量列表为空时现网是「不跳过」
        listOf(RunCondition.CUSTOM_VARIABLE_MATCH, RunCondition.CUSTOM_VARIABLE_MATCH_NOT_RUN).forEach { condition ->
            listOf(
                ElementAdditionalOptions(runCondition = condition, customVariables = null),
                ElementAdditionalOptions(runCondition = condition, customVariables = emptyList())
            ).forEach { origin ->
                val resolved = RunConditionNormalizer.resolve(origin)
                Assertions.assertEquals(StepRunIntent.VARIABLE_MATCH, resolved.intent)
                Assertions.assertNull(resolved.expression)
            }
        }
    }

    @Test
    fun `custom condition expression is kept as written`() {
        val resolved = RunConditionNormalizer.resolve(
            ElementAdditionalOptions(
                runCondition = RunCondition.CUSTOM_CONDITION_MATCH,
                customCondition = "a == 'b'",
                // 自定义条件下残留的变量列表不参与判定
                customVariables = listOf(NameAndValue("x", "y"))
            )
        )
        Assertions.assertEquals("a == 'b'", resolved.expression)
        Assertions.assertEquals(StepRunIntent.EXPRESSION, resolved.intent)
    }

    private fun options(runCondition: RunCondition, customVariables: List<NameAndValue>) =
        ElementAdditionalOptions(runCondition = runCondition, customVariables = customVariables)
}
