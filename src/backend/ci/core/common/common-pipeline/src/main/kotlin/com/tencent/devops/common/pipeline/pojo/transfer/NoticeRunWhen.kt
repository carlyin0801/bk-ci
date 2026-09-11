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

package com.tencent.devops.common.pipeline.pojo.transfer

/**
 * 完成通知(`notices`)的触发时机，对应YAML的`when`标量。
 *
 * #13602 / ADR C-3：通知条件原先复用步骤那套大写的[IfType]写在`if:`下，
 * 与Job收尾步骤新引入的`when:`并存会让PAC用户在同一份YAML里看到两种拼法表达同一件事。
 * 本枚举把通知条件收敛到`when:`，并沿用收尾步骤的小写取值风格。
 *
 * 存量`if:`（大写[IfType]）仍可解析，见[parseYaml]；但导出YAML时一律写`when:`。
 */
enum class NoticeRunWhen(val yamlValue: String) {
    /** 构建成功、失败都通知 */
    ALWAYS("always"),

    /** 仅构建成功时通知 */
    ON_SUCCESS("success"),

    /** 仅构建失败时通知 */
    ON_FAILURE("failure");

    fun notifyForSuccess() = this == ALWAYS || this == ON_SUCCESS

    fun notifyForFail() = this == ALWAYS || this == ON_FAILURE

    companion object {
        /** 不配时成功失败都通知，与收敛前`if`为空的行为一致 */
        val DEFAULT = ALWAYS

        val yamlValues: List<String> = values().map { it.yamlValue }

        /**
         * 解析通知的运行时机。同时吃三种写法，均映射到同一套语义：
         * - 新写法`when: success`（推荐）
         * - 存量`if: SUCCESS`（大写[IfType]，历史PAC仓库里的既有数据）
         * - 空值（成功失败都通知）
         *
         * 不认识的取值按[DEFAULT]处理而不是报错：通知是旁路能力，
         * 不该因为一个拼错的条件把整条流水线的解析卡死。
         */
        fun parseYaml(yamlValue: String?): NoticeRunWhen {
            if (yamlValue.isNullOrBlank()) return DEFAULT
            val normalized = yamlValue.trim().lowercase()
            return values().firstOrNull { it.yamlValue == normalized } ?: DEFAULT
        }
    }
}
