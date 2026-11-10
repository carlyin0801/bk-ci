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

import com.tencent.devops.common.expression.expression.ExpressionConstants

/**
 * 自定义条件表达式的保存期静态检查。
 *
 * 只拦「在运行期 100% 跑不出结果、且失败会被 ControlUtils 静默吞掉」的两类写法：
 *
 * 1. 未知函数名。运行期 `ExpressionParser.createTree` 的 allowUnknownKeywords 为 false，
 *    不在 [ExpressionConstants.WELL_KNOWN_FUNCTIONS] 里的函数一律抛 UnrecognizedFunction。
 * 2. `${{ }}` 整体包壳。引擎把 `${{ x }}` 和 `${x}` 当同一种变量占位符处理
 *    （见 EnvUtils 的 tPattern）：壳内文字查不到同名变量就原样留下，表达式随后以
 *    `Unexpected symbol: '${{'` 解析失败。
 *
 * 故意不拦未知上下文前缀（`github.` / `env.`）和未知属性（`.outcome`）——运行期的
 * 上下文根名是由当次构建变量现场喂进去的，保存期无从区分「GitHub 语法」和
 * 「用户自定义的运行期变量」，拦了就是误杀。
 */
object StepConditionSyntaxChecker {

    /**
     * @param origin 用户原样写下的表达式，用于回显，让用户能在表单里找到对应那一行
     * @param suggestion 建议改成的写法
     */
    data class Violation(val origin: String, val suggestion: String)

    /** 受支持的函数清单，用于在报错里直接告诉用户可以写什么 */
    val supportedFunctions: List<String> = ExpressionConstants.WELL_KNOWN_FUNCTIONS.values.map { it.name }.sorted()

    private val supportedFunctionHint = supportedFunctions.joinToString(" / ")

    /**
     * GitHub Actions 状态函数 -> 蓝盾对应写法，key 已去空格并转小写。
     *
     * 对应关系以现网 IfType 的既有语义为准：`ALWAYS` = 取消也跑、
     * `ALWAYS_UNLESS_CANCELLED` = 除取消外都跑，分别就是 GitHub 的 `always()` 与 `!cancelled()`。
     * 标量 `when: always` 的 on-cancel 默认是 continue，所以它等于 `always()`；
     * `!cancelled()` 必须显式写 on-cancel: skip，写成标量就跑反了。
     *
     * `cancelled()`（仅在取消时运行）在蓝盾步骤级没有等价写法，故意不收进来：
     * 它会落到未知函数那条分支，拿到一句「不支持，可用函数是这些」的事实陈述，
     * 好过在这里硬给一个语义不等价的建议把用户带偏。
     */
    private val githubActionsSyntax = mapOf(
        "success()" to "when: success",
        "failure()" to "when: failure",
        "always()" to "when: always",
        "!cancelled()" to "when: {status: always, on-cancel: skip}"
    )

    // 与 EnvUtils.tPattern 的双花括号分支保持一致：壳内不允许再出现 $ 和花括号
    private val doubleBracePattern = Regex("""\$\{\{([^${'$'}{}]*)}}""")

    // 纯变量引用，形如 a、a.b.c、a.b[0]。这类 ${{ }} 与 ${} 同义，现网可能真能跑通，不拦
    private val pureVariableRef = Regex("""[A-Za-z_][A-Za-z0-9_.-]*(\[[^\[\]]*])?""")

    // 表达式语言的字符串字面量，单引号内用 '' 转义
    private val stringLiteral = Regex("""'(?:[^']|'')*'""")

    // 标识符紧跟左括号即函数调用；分组用的括号前面不会是标识符
    private val functionCall = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*\(""")

    /**
     * @return 命中则返回待报错的 [Violation]，合规或无法判定时返回 null
     */
    fun check(expression: String?): Violation? {
        val origin = expression?.trim()
        if (origin.isNullOrEmpty()) {
            return null
        }

        val unwrapped = stripExpressionWrappers(origin)
        val normalized = unwrapped.lowercase().replace(" ", "")
        githubActionsSyntax[normalized]?.let { return Violation(origin, it) }

        firstUnknownFunction(unwrapped)?.let {
            return Violation(origin, "$it() 不是受支持的函数，可用函数：$supportedFunctionHint")
        }

        // 去壳后没有别的毛病，那问题就只是这层壳，直接把裸写法给出来
        return if (unwrapped != origin) Violation(origin, unwrapped) else null
    }

    /**
     * 剥掉包住表达式的 `${{ }}`，但放过壳内是纯变量引用的形态。
     */
    private fun stripExpressionWrappers(expression: String) =
        doubleBracePattern.replace(expression) { match ->
            val inner = match.groupValues[1].trim()
            if (inner.isEmpty() || pureVariableRef.matches(inner)) match.value else inner
        }

    private fun firstUnknownFunction(expression: String): String? {
        val withoutLiterals = stringLiteral.replace(expression, "''")
        return functionCall.findAll(withoutLiterals)
            .map { it.groupValues[1] }
            .firstOrNull { !ExpressionConstants.WELL_KNOWN_FUNCTIONS.containsKey(it) }
    }
}
