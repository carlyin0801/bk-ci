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

package com.tencent.devops.process.yaml.v3.models

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

/**
 * 步骤的运行意图 `when`。
 *
 * 两种书写形态等价，只给 status 时用标量：
 * ```yaml
 * when: always
 * ```
 * ```yaml
 * when:
 *   status: failure
 *   from: [build]
 *   skipped-as: failure
 *   on-cancel: continue
 * ```
 *
 * `status` 是主轴，其余三个是正交维度；把它们拆成独立的键而不是继续堆枚举组合，
 * 是因为三个维度互相独立，合并写法会得到 4x2x2 个名字。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class WhenField(
    val status: Status? = null,
    val from: List<String>? = null,
    @JsonProperty("skipped-as")
    val preFailureSkippedAs: PreFailureSkippedAsValue? = null,
    @JsonProperty("on-cancel")
    val onCancel: OnCancelValue? = null
) {
    /** 主轴：以哪种前序结果作为本步骤运行的前提 */
    enum class Status(val alias: String) {
        SUCCESS("success"),
        FAILURE("failure"),
        ALWAYS("always");

        companion object {
            @JvmStatic
            fun parse(value: String?): Status? = values().find { it.alias == value?.trim()?.lowercase() }
        }
    }

    /** 前序步骤「失败后被放过」时，按成功还是失败计入前序结果 */
    enum class PreFailureSkippedAsValue(val alias: String) {
        SUCCESS("success"),
        FAILURE("failure");

        companion object {
            @JvmStatic
            fun parse(value: String?): PreFailureSkippedAsValue? =
                values().find { it.alias == value?.trim()?.lowercase() }
        }
    }

    /** 构建被取消时，本步骤继续运行还是跟着跳过 */
    enum class OnCancelValue(val alias: String) {
        CONTINUE("continue"),
        SKIP("skip");

        companion object {
            @JvmStatic
            fun parse(value: String?): OnCancelValue? = values().find { it.alias == value?.trim()?.lowercase() }
        }
    }
}
