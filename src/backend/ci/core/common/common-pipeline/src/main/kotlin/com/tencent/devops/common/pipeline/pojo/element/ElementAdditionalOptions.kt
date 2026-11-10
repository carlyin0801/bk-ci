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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.tencent.devops.common.pipeline.NameAndValue
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "插件级别流程控制模型")
data class ElementAdditionalOptions(
    @get:Schema(title = "是否启用", required = false)
    var enable: Boolean = true,
    @get:Schema(title = "是否失败时继续", required = false)
    var continueWhenFailed: Boolean = false, // 失败时继续  continueWhenFailed = true &&  manualSkip != true（自动继续）
    @get:Schema(title = "是否出现跳过按钮（手动继续）", required = false)
    val manualSkip: Boolean? = null, // (continueWhenFailed = true && manualSkip = true) 出现跳过按钮（手动继续）
    @get:Schema(title = "是否失败时重试", required = false)
    var retryWhenFailed: Boolean = false,
    @get:Schema(title = "重试计数", required = false)
    var retryCount: Int = 0,
    @get:Schema(title = "是否允许手动重试", required = false)
    val manualRetry: Boolean = true, // 自动重试一直失败后，界面出现重试按钮, 默认允许手动重试（为了兼容旧数据使用习惯）
    @get:Schema(title = "超时分钟", required = false)
    var timeout: Long? = 100, // 超时分钟
    @get:Schema(title = "新的执行的超时时间，支持变量(分钟Minutes)，出错则取timeout的值", required = false)
    var timeoutVar: String? = null, // Job执行的超时时间 分钟Minutes
    @JsonIgnore // 表示是否有修改，比如timeout. 注解 @JsonIgnore 表示本字段不会持久到数据库存储，只做临时的校验字段，不做任何保证
    var change: Boolean = false,
    @get:Schema(title = "执行条件", required = false)
    var runCondition: RunCondition? = null,
    @get:Schema(title = "前序范围：参与成败判定的前序步骤 ID(stepId) 列表，空表示同 Job 全部前序", required = false)
    var fromStepIds: List<String>? = null,
    @get:Schema(title = "前序失败被跳过时视为成功还是失败，空表示按执行条件取默认值", required = false)
    var preFailureSkippedAs: PreFailureSkippedAs? = null,
    @get:Schema(title = "本 Job 被取消时是否仍运行本步骤，空表示按执行条件取默认值", required = false)
    var onCancel: OnCancel? = null,
    @get:Schema(title = "是否配置前置暂停", required = false)
    var pauseBeforeExec: Boolean? = false, // 是否配置前置暂停
    @get:Schema(title = "订阅暂停通知用户", required = false)
    var subscriptionPauseUser: String? = "", // 订阅暂停通知用户
    @get:Schema(title = "自定义变量", required = false)
    val customVariables: List<NameAndValue>? = null,
    @get:Schema(title = "自定义条件", required = false)
    var customCondition: String? = "",
    @get:Schema(title = "插件post信息", required = false)
    var elementPostInfo: ElementPostInfo? = null,
    @get:Schema(title = "是否设置自定义环境变量", required = false)
    @Deprecated("不需要判断是否开启env，使用新字段")
    val enableCustomEnv: Boolean? = true,
    @get:Schema(title = "用户自定义环境变量（插件运行时写入环境）", required = false)
    @Deprecated("移到Element级别来维护，与Job保持层级一致")
    var customEnv: List<NameAndValue>? = null
)

/**
 * 「前序失败被跳过时」的口径。指「失败时继续」放过的失败，自动跳过与手动跳过同等对待。
 * 新建步骤默认 [SUCCESS]，对齐 GitHub Actions 的 continue-on-error 口径；
 * 存量 [RunCondition.PRE_TASK_FAILED_ONLY] 的默认值是 [FAILURE]，与之相反，由意图解析时回填。
 */
enum class PreFailureSkippedAs {
    SUCCESS, // 被跳过的失败视为成功，不触发「前序失败时」
    FAILURE // 被跳过的失败视为失败，触发「前序失败时」
}

/**
 * 本 Job 被取消时，尚未开跑的本步骤是否仍被调度。
 * 取消是唯一既不算成功也不算失败的终态，状态意图判不出结果，才单列这一维；
 * 超时属失败终态，由意图本身判定，不受本项影响。
 */
enum class OnCancel {
    CONTINUE, // 取消后仍调度
    SKIP // 取消后不调度
}

/**
 * 步骤运行意图（判定轴）。[RunCondition] 是落库形态，本枚举是判定时的归一形态。
 */
enum class StepRunIntent {
    SUCCESS, // 前序成功时（默认）
    FAILURE, // 前序失败时
    ALWAYS, // 总是运行
    EXPRESSION, // 自定义条件（if 表达式）
    POST_ACTION // 内置插件 post-action 专用，由父插件状态决定，不参与前序判定
}

/**
 * 插件（步骤）的运行意图。
 *
 * 取值分三类：
 * 1. 意图轴——页面可配的四种「何时运行本步骤」，前三项为状态意图，[CUSTOM_CONDITION_MATCH] 为自定义条件；
 * 2. 内置 post-action 专用——[PARENT_TASK_CANCELED_OR_TIMEOUT] 与 [PARENT_TASK_FINISH]，
 *    由插件 post 脚本声明，不参与主流程步骤的状态判定；
 * 3. 只读兼容值——存量数据里还在用，读入时由 RunConditionNormalizer 归一到意图轴，不再作为新配置写入。
 */
enum class RunCondition {
    // ---- 意图轴：前序成功时（默认）----
    PRE_TASK_SUCCESS, // 所有前置插件运行成功时

    // ---- 意图轴：前序失败时 ----
    PRE_TASK_FAILED_ONLY, // 只有前面有插件运行失败时才运行（包括失败自动跳过的情况）
    PRE_TASK_FAILED_ONLY_EXCEPT_SKIP, // 只有前面有插件运行失败时才运行（不包括失败自动跳过的情况）

    // ---- 意图轴：总是运行 ----
    PRE_TASK_FAILED_BUT_CANCEL, // 即使前面有插件运行失败也运行，除非被取消才不运行
    PRE_TASK_FAILED_EVEN_CANCEL, // 即使前面有插件运行失败也运行，即使被取消也运行

    // ---- 意图轴：自定义条件（if 表达式）----
    CUSTOM_CONDITION_MATCH, // 满足以下自定义条件时运行

    // ---- 内置 post-action 专用，不暴露为主流程步骤的运行意图 ----
    PARENT_TASK_CANCELED_OR_TIMEOUT, // 父任务取消或者超时时才运行
    PARENT_TASK_FINISH, // 父任务结束就运行

    // ---- 只读兼容值：读入即归一为 CUSTOM_CONDITION_MATCH + 等价表达式 ----
    @Deprecated("已迁移到 if 表达式，读入时由 RunConditionNormalizer 归一，不要用于新配置")
    CUSTOM_VARIABLE_MATCH, // 自定义变量全部满足时运行

    @Deprecated("已迁移到 if 表达式，读入时由 RunConditionNormalizer 归一，不要用于新配置")
    CUSTOM_VARIABLE_MATCH_NOT_RUN; // 自定义变量全部满足时不运行

    companion object {

        /**
         * 存量数据里可能残留已下线的取值（如从未实现过的 OTHER_TASK_RUNNING）。
         * 枚举反序列化默认会对未知取值抛异常，历史流水线将无法打开、历史构建无法重试，
         * 故在此兜底为默认意图——OTHER_TASK_RUNNING 从未参与过判定，其实际行为本就等同默认意图。
         */
        @JvmStatic
        @JsonCreator
        fun parse(value: String?): RunCondition? {
            if (value.isNullOrBlank()) {
                return null
            }
            return entries.firstOrNull { it.name == value } ?: PRE_TASK_SUCCESS
        }
    }
}
