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

package com.tencent.devops.common.expression.expression.functions

import com.tencent.devops.common.expression.expression.FunctionInfo
import com.tencent.devops.common.expression.expression.IFunctionInfo
import com.tencent.devops.common.expression.expression.sdk.EvaluationContext
import com.tencent.devops.common.expression.expression.sdk.Function
import com.tencent.devops.common.expression.expression.sdk.ResultMemory

/**
 * 状态函数 `success()` / `failure()` / `cancelled()` / `always()`，命名与语义对齐 GitHub Actions。
 *
 * 它们**不进** [com.tencent.devops.common.expression.expression.ExpressionConstants.WELL_KNOWN_FUNCTIONS]：
 * 内置表是全局单例、所有求值点共用，而状态函数的取值依赖「本次是给哪个步骤判条件」。
 * 注册进全局表会让 Stage / Job 条件也能写出这几个名字，却只能恒定拿到一个编不出来的值。
 * 改为每次求值现场构造一份扩展函数传给 [com.tencent.devops.common.expression.ExpressionParser.evaluateByMap]，
 * 不传就是原样行为，对所有既有调用方零影响。
 *
 * 取值由构造参数携带：[Function.createNode] 会在解析期为每个调用点克隆一次节点，
 * 克隆时把同一个值带下去，于是「函数实例」天然是每次求值独立的，不存在跨构建串值。
 */
object StatusFunctions {

    const val SUCCESS = "success"
    const val FAILURE = "failure"
    const val CANCELLED = "cancelled"
    const val ALWAYS = "always"

    /** 四个函数名，供保存期校验放行与回显折叠使用 */
    val names: Set<String> = setOf(SUCCESS, FAILURE, CANCELLED, ALWAYS)

    /**
     * 表达式里是否出现了状态函数。
     *
     * 判据是「标识符紧跟左括号」，与 [ExpressionParser] 的词法一致：分组用的 `(` 前面不会是标识符。
     * 先把字符串字面量抹平，避免 `contains(X, 'always()')` 这种把函数名写在文本里的误判。
     *
     * 这个判断决定一条表达式要不要叠加「前序成功」的隐含前置（见 ControlUtils），
     * 所以**宁可多判、不可漏判**：漏判会让 `cancelled()` 恒不成立，等于函数白给。
     */
    fun referencedIn(expression: String?): Boolean {
        if (expression.isNullOrBlank()) {
            return false
        }
        val withoutLiterals = STRING_LITERAL.replace(expression, "''")
        return FUNCTION_CALL.findAll(withoutLiterals).any { it.groupValues[1].lowercase() in names }
    }

    /**
     * 本函数在取消时可能为真吗。决定了取消后还要不要给这个步骤求值机会——
     * 只有这两个函数能在取消时成立，其余写法给了机会也只会求出 false。
     */
    fun mayRunOnCancel(expression: String?): Boolean {
        if (expression.isNullOrBlank()) {
            return false
        }
        val withoutLiterals = STRING_LITERAL.replace(expression, "''")
        return FUNCTION_CALL.findAll(withoutLiterals)
            .any { it.groupValues[1].lowercase() == CANCELLED || it.groupValues[1].lowercase() == ALWAYS }
    }

    /**
     * 构造本次判定用的扩展函数表。
     *
     * **取消这一格要单独排除**：`success()` / `failure()` 必须与对应的 `when` 意图逐格等价。
     * `when: success` 取消时不跑，若 `success()` 在取消时取 `!preFailed` 便是 true，两种写法会分叉。
     * 排除之后下面几条等价同时成立：
     *
     * | 表达式 | 等价意图 | 取消时 |
     * |---|---|---|
     * | `success()` | `when: success` | false，不跑 |
     * | `failure()` | `when: failure`（on-cancel 默认 skip） | false，不跑 |
     * | `failure() \|\| cancelled()` | `when: {status: failure, on-cancel: continue}` | true，跑 |
     * | `always()` | `when: always`（on-cancel 默认 continue） | true，跑 |
     *
     * @param preFailed 观察范围内是否存在「算失败」的前序步骤，口径与 `when: failure` 同源
     * @param canceled 本 Job 是否已被取消（终态 `CANCELED`；超时不落这里，它算失败）
     */
    fun of(preFailed: Boolean, canceled: Boolean): List<IFunctionInfo> = listOf(
        info(SUCCESS, !preFailed && !canceled),
        info(FAILURE, preFailed && !canceled),
        info(CANCELLED, canceled),
        info(ALWAYS, true)
    )

    private fun info(name: String, value: Boolean) = FunctionInfo(name, 0, 0, StatusFunction(name, value))

    private val STRING_LITERAL = Regex("""'(?:[^']|'')*'""")
    private val FUNCTION_CALL = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*\(""")

    /**
     * 无参、返回定值的布尔函数。值在构造时固定，[createNode] 原样传递。
     */
    private class StatusFunction(
        private val functionName: String,
        private val value: Boolean
    ) : Function() {

        override val traceFullyRealized: Boolean get() = false

        override fun createNode(): Function = StatusFunction(functionName, value)

        override fun evaluateCore(context: EvaluationContext): Pair<ResultMemory?, Any?> = Pair(null, value)

        override fun subNameValueEvaluateCore(context: EvaluationContext): Pair<Any?, Boolean> =
            Pair("$functionName()", false)
    }
}
