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

/**
 * Job收尾步骤(post-steps)的运行时机，对应YAML的`when`标量。
 *
 * 与主步骤的[RunCondition]是**两套独立的判定**，不要混用：
 * - 主步骤的[RunCondition]判的是「本插件之前的那些主步骤」的成败；
 * - 本枚举判的是「主步骤区全部结束时冻结下来的Job主状态」，不扫前序、也不看同段其它收尾步骤的结果。
 *
 * 因此收尾步骤上的[ElementAdditionalOptions.runCondition]一律被忽略（用户从YAML误写也不生效），
 * 判定入口统一收敛到[com.tencent.devops.process.engine.control.ControlUtils.checkTaskSkip]的收尾分支。
 */
enum class JobPostRunWhen(val yamlValue: String) {
    /** 总是运行：主步骤区到达任意终态（成功/失败/取消/超时）都运行。未配置时的默认值 */
    ALWAYS("always"),

    /** Job成功时 */
    ON_SUCCESS("success"),

    /** Job失败类终态时（含Job/步骤超时）。被「失败时继续」放过的失败不使Job失败，故不触发 */
    ON_FAILURE("failure"),

    /** Job被取消时 */
    ON_CANCEL("cancel");

    companion object {
        /** 未配置[ElementAdditionalOptions.runWhen]时按[ALWAYS]处理（产品规范：默认值ALWAYS） */
        val DEFAULT = ALWAYS

        /** YAML `when`标量的合法取值，用于报错提示与JSON Schema对齐 */
        val yamlValues: List<String> = values().map { it.yamlValue }

        /**
         * 解析YAML `when`标量。省略（null/空）时返回[DEFAULT]；写了但不认识则返回null由调用方报错。
         */
        fun parseYaml(yamlValue: String?): JobPostRunWhen? {
            if (yamlValue.isNullOrBlank()) return DEFAULT
            val normalized = yamlValue.trim().lowercase()
            return values().firstOrNull { it.yamlValue == normalized }
        }
    }
}
