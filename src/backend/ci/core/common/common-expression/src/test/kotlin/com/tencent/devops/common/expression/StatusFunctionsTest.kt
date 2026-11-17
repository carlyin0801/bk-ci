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

package com.tencent.devops.common.expression

import com.tencent.devops.common.expression.expression.EvaluationResult
import com.tencent.devops.common.expression.expression.functions.StatusFunctions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StatusFunctionsTest {

    private fun eval(
        expression: String,
        preFailed: Boolean = false,
        canceled: Boolean = false,
        variables: Map<String, String> = emptyMap()
    ): Boolean {
        val result = ExpressionParser.evaluateByMap(
            expression = expression,
            contextMap = variables,
            fetchValue = false,
            functions = StatusFunctions.of(preFailed = preFailed, canceled = canceled)
        )
        return (result as EvaluationResult).equalsTrue
    }

    @Test
    fun `each status function returns its own value`() {
        assertTrue(eval("success()", preFailed = false))
        assertFalse(eval("success()", preFailed = true))

        assertFalse(eval("failure()", preFailed = false))
        assertTrue(eval("failure()", preFailed = true))

        assertFalse(eval("cancelled()", canceled = false))
        assertTrue(eval("cancelled()", canceled = true))

        assertTrue(eval("always()"))
        assertTrue(eval("always()", preFailed = true, canceled = true))
    }

    @Test
    fun `cancel wins when both cancel and failure are true`() {
        // 引擎里 isCancel() 与 isFailure() 互斥，这里只是防御性锁住：不会同时判成失败和取消
        assertFalse(eval("failure()", preFailed = true, canceled = true))
        assertTrue(eval("cancelled()", preFailed = true, canceled = true))
    }

    @Test
    fun `only cancelled and always are true when canceled`() {
        assertFalse(eval("success()", canceled = true))
        assertFalse(eval("failure()", canceled = true))
        assertTrue(eval("cancelled()", canceled = true))
        assertTrue(eval("always()", canceled = true))
    }

    /**
     * 裸状态函数与 when 意图逐格等价，这里逐格锁住。
     * 三列依次是：前序全成功 / 前序有失败 / 被取消。
     */
    @Test
    fun `bare status function matches the corresponding when intent`() {
        // success() ≡ when: success —— 取消时不跑
        assertEquals(listOf(true, false, false), grid("success()"))
        // failure() ≡ when: failure（on-cancel 默认 skip）—— 取消时不跑
        assertEquals(listOf(false, true, false), grid("failure()"))
        // always() ≡ when: always（on-cancel 默认 continue）—— 取消时也跑
        assertEquals(listOf(true, true, true), grid("always()"))
        // failure() || cancelled() ≡ when: {status: failure, on-cancel: continue}
        assertEquals(listOf(false, true, true), grid("failure() || cancelled()"))
        // !cancelled() ≡ when: {status: always, on-cancel: skip}
        assertEquals(listOf(true, true, false), grid("!cancelled()"))
    }

    private fun grid(expression: String) = listOf(
        eval(expression),
        eval(expression, preFailed = true),
        eval(expression, canceled = true)
    )

    @Test
    fun `mayRunOnCancel only accepts cancelled and always`() {
        assertTrue(StatusFunctions.mayRunOnCancel("cancelled()"))
        assertTrue(StatusFunctions.mayRunOnCancel("always()"))
        assertTrue(StatusFunctions.mayRunOnCancel("failure() || cancelled()"))
        // success()/failure() 取消时恒为 false，给了调度机会也只会求出不跑
        assertFalse(StatusFunctions.mayRunOnCancel("failure()"))
        assertFalse(StatusFunctions.mayRunOnCancel("success() && variables.X == '1'"))
        assertFalse(StatusFunctions.mayRunOnCancel("variables.X == '1'"))
        assertFalse(StatusFunctions.mayRunOnCancel(null))
    }

    @Test
    fun `status functions combine with operators and variables`() {
        assertTrue(eval("failure() || cancelled()", preFailed = true))
        assertTrue(eval("failure() || cancelled()", canceled = true))
        assertFalse(eval("failure() || cancelled()"))

        assertTrue(eval("!cancelled()"))
        assertFalse(eval("!cancelled()", canceled = true))

        val expr = "failure() && variables.DO_CLEAN == 'true'"
        assertTrue(eval(expr, preFailed = true, variables = mapOf("variables.DO_CLEAN" to "true")))
        assertFalse(eval(expr, preFailed = true, variables = mapOf("variables.DO_CLEAN" to "no")))
        assertFalse(eval(expr, preFailed = false, variables = mapOf("variables.DO_CLEAN" to "true")))
    }

    @Test
    fun `function names are case insensitive`() {
        assertTrue(eval("Cancelled()", canceled = true))
        assertTrue(eval("ALWAYS()"))
    }

    @Test
    fun `status functions stay unknown without extension functions`() {
        assertThrows<ExpressionParseException> {
            ExpressionParser.evaluateByMap("always()", emptyMap(), false)
        }
    }

    @Test
    fun `arguments are rejected instead of ignored`() {
        assertThrows<ExpressionParseException> {
            eval("failure('build')", preFailed = true)
        }
    }

    @Test
    fun `referencedIn detects status functions`() {
        assertTrue(StatusFunctions.referencedIn("cancelled()"))
        assertTrue(StatusFunctions.referencedIn("failure() || variables.X == '1'"))
        assertTrue(StatusFunctions.referencedIn("  ALWAYS( ) "))
        assertFalse(StatusFunctions.referencedIn("variables.X == 'true'"))
        assertFalse(StatusFunctions.referencedIn(null))
        assertFalse(StatusFunctions.referencedIn(""))
    }

    @Test
    fun `referencedIn ignores function names inside string literals`() {
        assertFalse(StatusFunctions.referencedIn("contains(variables.MSG, 'always()')"))
        // 变量名里含 success 不算调用：判据是标识符紧跟左括号
        assertFalse(StatusFunctions.referencedIn("variables.success == 'true'"))
    }

    @Test
    fun `repeated calls in one expression share the same value`() {
        assertTrue(eval("always() && always()"))
        assertEquals(true, eval("failure() || failure()", preFailed = true))
    }
}
