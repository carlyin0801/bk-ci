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

package com.tencent.devops.process.pojo

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 取消构建的预检结果，供前端在弹出确认框前判断该给出哪一种提示。
 *
 * #13602 取消不再等于「立刻停下所有东西」：插件post动作、Job收尾步骤在取消后仍会继续跑完。
 * 用户第二次点取消才会升级为强制终止，把这些收尾现场一并掐掉——这是不可逆的，
 * 必须在点下去之前就告诉用户，而不是事后在构建日志里才看得到。
 */
@Schema(title = "构建模型-取消预检")
data class BuildCancelPreCheck(
    @get:Schema(title = "本次取消是否会升级为强制终止（强杀）", required = true)
    val terminate: Boolean,
    @get:Schema(title = "距离本次取消可被受理还需等待的秒数，大于0表示此刻取消会被拒绝", required = true)
    val retryAfterSecond: Long,
    @get:Schema(title = "上一次取消的操作人，未被取消过时为空", required = false)
    val lastCancelUserId: String? = null,
    @get:Schema(title = "尚未结束的Job收尾步骤数，强制终止会跳过它们", required = true)
    val pendingPostStepCount: Int = 0
)
