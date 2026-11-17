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
import com.tencent.devops.common.expression.expression.functions.StatusFunctions
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class StepConditionSyntaxCheckerTest {

    @Test
    fun `status functions are accepted`() {
        listOf("success()", "failure()", "cancelled()", "always()", "!cancelled()").forEach {
            Assertions.assertNull(StepConditionSyntaxChecker.check(it), "不应拦下: $it")
        }
        // 大小写不敏感，与表达式引擎的函数查表口径一致
        Assertions.assertNull(StepConditionSyntaxChecker.check("  ALWAYS ( )  "))
        Assertions.assertNull(StepConditionSyntaxChecker.check("failure() || cancelled()"))
    }

    @Test
    fun `violation echoes the original text`() {
        val violation = StepConditionSyntaxChecker.check("  hashFiles ( 'a' )  ")
        Assertions.assertEquals("hashFiles ( 'a' )", violation?.origin)
    }

    @Test
    fun `supported function list includes status functions`() {
        Assertions.assertTrue(StepConditionSyntaxChecker.supportedFunctions.containsAll(
            listOf("always", "cancelled", "failure", "success")
        ))
    }

    @Test
    fun `unknown function is rejected with the supported list`() {
        val violation = StepConditionSyntaxChecker.check("hashFiles('a.lock') == 'x'")
        Assertions.assertNotNull(violation)
        Assertions.assertTrue(violation!!.suggestion.contains("fromJSON"))
    }

    @Test
    fun `supported functions are accepted`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check("contains(VAR, 'a')"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("startsWith(A, 'x') && endsWith(B, 'y')"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("format('{0}', A) == 'x'"))
    }

    @Test
    fun `parentheses inside string literals are not function calls`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check("contains(VAR, 'foo(bar')"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("VAR == 'hashFiles(x)'"))
    }

    @Test
    fun `grouping parentheses are not function calls`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check("(A == 'x') || (B == 'y')"))
    }

    @Test
    fun `wrapping the whole expression is rejected`() {
        Assertions.assertEquals(
            "DO_DEPLOY == 'true'",
            StepConditionSyntaxChecker.check("\${{ DO_DEPLOY == 'true' }}")?.suggestion
        )
        // 状态函数本身合法，但照抄 GitHub 的花括号仍要拦：引擎不认这层壳
        Assertions.assertEquals("success()", StepConditionSyntaxChecker.check("\${{ success() }}")?.suggestion)
    }

    @Test
    fun `a variable placeholder inside the wrapper is accepted`() {
        // 这类与 ${var} 同义，EnvUtils 会把它替换成变量值，现网可能真能跑通，拦了就是误杀
        Assertions.assertNull(StepConditionSyntaxChecker.check("\${{ DO_DEPLOY }} == 'true'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("\${{ variables.V }} == 'yes'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("\${{ matrix.os[0] }} == 'linux'"))
    }

    @Test
    fun `unknown context prefixes and properties are accepted`() {
        // 保存期分不清这是 GitHub 语法还是用户自己的运行期变量名
        Assertions.assertNull(StepConditionSyntaxChecker.check("github.ref == 'refs/heads/main'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("steps.build.outcome == 'success'"))
        Assertions.assertNull(StepConditionSyntaxChecker.check("MY_VAR == 'true'"))
    }

    @Test
    fun `blank input is accepted`() {
        Assertions.assertNull(StepConditionSyntaxChecker.check(null))
        Assertions.assertNull(StepConditionSyntaxChecker.check(""))
        Assertions.assertNull(StepConditionSyntaxChecker.check("   "))
    }

    @Test
    fun `rejected expressions fail at runtime`() {
        // 锁住本校验器的立论前提：拦的都是运行期必抛异常、而后被 ControlUtils 静默吞掉的写法
        val variables = mapOf("DO_DEPLOY" to "true")
        listOf(
            "hashFiles('a.lock') == 'x'",
            "nosuchfn(A)",
            "\${{ DO_DEPLOY == 'true' }}"
        ).forEach { expression ->
            Assertions.assertNotNull(StepConditionSyntaxChecker.check(expression), "应被拦下: $expression")
            Assertions.assertThrows(Throwable::class.java, {
                ExpressionParser.evaluateByMap(expression, variables, false)
            }, "运行期应当抛错: $expression")
        }
    }

    @Test
    fun `accepted expressions evaluate at runtime`() {
        val variables = mapOf("DO_DEPLOY" to "true", "MY_VAR" to "true")
        listOf("DO_DEPLOY == 'true'", "MY_VAR == 'true'", "contains(DO_DEPLOY, 'ru')").forEach { expression ->
            Assertions.assertNull(StepConditionSyntaxChecker.check(expression), "不应拦下: $expression")
            Assertions.assertNotNull(ExpressionParser.evaluateByMap(expression, variables, false))
        }
    }

    @Test
    fun `status functions evaluate only when registered`() {
        // 锁住这条依赖：校验器放行它们，是因为 ControlUtils 判定步骤条件时一定会把扩展函数传进去。
        // 哪天那条链路断了，这个用例会提醒「放行」与「能跑」已经脱节
        listOf("success()", "failure()", "cancelled()", "always()").forEach { expression ->
            Assertions.assertNull(StepConditionSyntaxChecker.check(expression))
            Assertions.assertThrows(Throwable::class.java, {
                ExpressionParser.evaluateByMap(expression, emptyMap(), false)
            }, "不传扩展函数时应抛错: $expression")
            Assertions.assertNotNull(
                ExpressionParser.evaluateByMap(
                    expression = expression,
                    contextMap = emptyMap(),
                    fetchValue = false,
                    functions = StatusFunctions.of(preFailed = false, canceled = false)
                )
            )
        }
    }
}
