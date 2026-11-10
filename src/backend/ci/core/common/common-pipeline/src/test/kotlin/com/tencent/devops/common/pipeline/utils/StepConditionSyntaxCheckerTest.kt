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

package com.tencent.devops.common.pipeline.utils

import com.tencent.devops.common.expression.ExpressionParser
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StepConditionSyntaxCheckerTest {

    @Test
    fun `GitHub 状态函数给出蓝盾对应写法`() {
        Assertions.assertEquals("when: success", StepConditionSyntaxChecker.check("success()")?.suggestion)
        Assertions.assertEquals("when: failure", StepConditionSyntaxChecker.check("failure()")?.suggestion)
        Assertions.assertEquals("when: always", StepConditionSyntaxChecker.check("always()")?.suggestion)
        Assertions.assertEquals(
            "when: {status: always, on-cancel: skip}",
            StepConditionSyntaxChecker.check("!cancelled()")?.suggestion
        )
        // 大小写与空格不影响识别
        Assertions.assertEquals("when: always", StepConditionSyntaxChecker.check("  ALWAYS ( )  ")?.suggestion)
    }

    @Test
    fun `报错回显用户原文而不是归一结果`() {
        val violation = StepConditionSyntaxChecker.check("  ALWAYS ( )  ")
        Assertions.assertEquals("ALWAYS ( )", violation?.origin)
    }

    @Test
    fun `cancelled 不给等价建议只陈述不支持`() {
        val violation = StepConditionSyntaxChecker.check("cancelled()")
        Assertions.assertNotNull(violation)
        Assertions.assertTrue(violation!!.suggestion.startsWith("cancelled() 不是受支持的函数"))
    }

    @Test
    fun `未知函数被拦下并列出可用函数`() {
        val violation = StepConditionSyntaxChecker.check("hashFiles('a.lock') == 'x'")
        Assertions.assertNotNull(violation)
        Assertions.assertTrue(violation!!.suggestion.contains("fromJSON"))
    }

    @Test
    fun `受支持的函数不拦`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check("contains(VAR, 'a')"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("startsWith(A, 'x') && endsWith(B, 'y')"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("format('{0}', A) == 'x'"))
    }

    @Test
    fun `字符串字面量里的括号不会被误判成函数调用`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check("contains(VAR, 'foo(bar')"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("VAR == 'hashFiles(x)'"))
    }

    @Test
    fun `分组括号不会被误判成函数调用`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check("(A == 'x') || (B == 'y')"))
    }

    @Test
    fun `整体包壳被拦下并给出裸写法`() {
        Assertions.assertEquals(
            "DO_DEPLOY == 'true'",
            StepConditionSyntaxChecker.check("\${{ DO_DEPLOY == 'true' }}")?.suggestion
        )
        Assertions.assertEquals("when: success", StepConditionSyntaxChecker.check("\${{ success() }}")?.suggestion)
    }

    @Test
    fun `壳内是纯变量引用的存量写法不拦`() {
        // 这类与 ${var} 同义，EnvUtils 会把它替换成变量值，现网可能真能跑通，拦了就是误杀
        Assertions.assertNull(StepConditionSyntaxChecker.check("\${{ DO_DEPLOY }} == 'true'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("\${{ variables.V }} == 'yes'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("\${{ matrix.os[0] }} == 'linux'"))
    }

    @Test
    fun `未知上下文前缀与未知属性故意不拦`() {
        // 保存期分不清这是 GitHub 语法还是用户自己的运行期变量名
        Assertions.assertNull(StepConditionSyntaxChecker.check("github.ref == 'refs/heads/main'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("steps.build.outcome == 'success'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("MY_VAR == 'true'"))
    }

    @Test
    fun `空与空白不拦`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check(null))
        Assertions.assertNull(StepConditionSyntaxChecker.check(""))
        Assertions.assertNull(StepConditionSyntaxChecker.check("   "))
    }

    @Test
    fun `被拦下的写法在运行期确实跑不出结果`() {
        // 锁住本校验器的立论前提：拦的都是运行期必抛异常、而后被 ControlUtils 静默吞掉的写法
        val variables = mapOf("DO_DEPLOY" to "true")
        listOf(
            "success()",
            "always()",
            "cancelled()",
            "hashFiles('a.lock') == 'x'",
            "\${{ DO_DEPLOY == 'true' }}"
        ).forEach { expression ->
            Assertions.assertNotNull(StepConditionSyntaxChecker.check(expression), "应被拦下: $expression")
            Assertions.assertThrows(Throwable::class.java, {
                ExpressionParser.evaluateByMap(expression, variables, false)
            }, "运行期应当抛错: $expression")
        }
    }

    @Test
    fun `放过的写法在运行期可以跑通`() {
        val variables = mapOf("DO_DEPLOY" to "true", "MY_VAR" to "true")
        listOf("DO_DEPLOY == 'true'", "MY_VAR == 'true'", "contains(DO_DEPLOY, 'ru')").forEach { expression ->
            Assertions.assertNull(StepConditionSyntaxChecker.check(expression), "不应拦下: $expression")
            Assertions.assertNotNull(ExpressionParser.evaluateByMap(expression, variables, false))
        }
    }
}
