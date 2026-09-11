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
    @get:Schema(title = "是否配置前置暂停", required = false)
    var pauseBeforeExec: Boolean? = false, // 是否配置前置暂停
    @get:Schema(title = "订阅暂停通知用户", required = false)
    var subscriptionPauseUser: String? = "", // 订阅暂停通知用户
    @get:Schema(title = "", required = false)
    var otherTask: String? = null,
    @get:Schema(title = "自定义变量", required = false)
    val customVariables: List<NameAndValue>? = null,
    @get:Schema(title = "自定义条件", required = false)
    var customCondition: String? = "",
    @get:Schema(title = "插件post信息", required = false)
    var elementPostInfo: ElementPostInfo? = null,
    @get:Schema(title = "是否为Job收尾步骤（YAML: post-steps）", required = false)
    var jobPostStepFlag: Boolean? = null,
    @get:Schema(title = "Job收尾步骤的运行时机（YAML: when），仅收尾步骤有效，不配按ALWAYS", required = false)
    var runWhen: JobPostRunWhen? = null,
    @get:Schema(title = "是否设置自定义环境变量", required = false)
    @Deprecated("不需要判断是否开启env，使用新字段")
    val enableCustomEnv: Boolean? = true,
    @get:Schema(title = "用户自定义环境变量（插件运行时写入环境）", required = false)
    @Deprecated("移到Element级别来维护，与Job保持层级一致")
    var customEnv: List<NameAndValue>? = null
)

enum class RunCondition {
    PRE_TASK_SUCCESS, // 所有前置插件运行成功时
    PRE_TASK_FAILED_BUT_CANCEL, // 即使前面有插件运行失败也运行，除非被取消才不运行
    PRE_TASK_FAILED_EVEN_CANCEL, // 即使前面有插件运行失败也运行，即使被取消也运行
    PRE_TASK_FAILED_ONLY, // 只有前面有插件运行失败时才运行（包括失败自动跳过的情况）
    PRE_TASK_FAILED_ONLY_EXCEPT_SKIP, // 只有前面有插件运行失败时才运行（不包括失败自动跳过的情况）

    OTHER_TASK_RUNNING, // 指定插件开始运行时 [未实现]
    CUSTOM_VARIABLE_MATCH, // 自定义变量全部满足时运行
    CUSTOM_VARIABLE_MATCH_NOT_RUN, // 自定义变量全部满足时不运行
    CUSTOM_CONDITION_MATCH, // 满足以下自定义条件时运行 [未实现]
    PARENT_TASK_CANCELED_OR_TIMEOUT, // 父任务取消或者超时时才运行
    PARENT_TASK_FINISH // 父任务结束就运行
}

/**
 * 构建被用户取消后，该任务是否仍要运行。
 *
 * 取消是通过[com.tencent.devops.common.pipeline.enums.BuildStatus]与ActionType.END层层传播的：
 * 引擎调度、构建机认领、内置插件收尾、执行记录回写等环节都会把「还没跑的任务」直接置为UNEXEC/CANCELED。
 * 凡是要在取消后仍有机会执行的任务，都必须在这些环节统一放行，否则任务连启动的机会都没有。
 * 调用方一律传[ElementAdditionalOptions]，不要自己去比对某一个枚举值。
 *
 * Job收尾步骤一律放行：取消门禁只负责「别让取消把还没轮到的步骤提前置为UNEXEC」，
 * 而收尾步骤到底跑不跑，由[JobPostRunWhen]比对主步骤区冻结下来的Job主状态单独判定
 * （见ControlUtils.checkJobPostStepSkip），判定只能有这一个出口。
 * 若此处按运行时机放行，收尾段内取消当前卡住的步骤时，后续[JobPostRunWhen.ON_SUCCESS]、
 * [JobPostRunWhen.ON_FAILURE]的步骤会被这道门禁直接判为UNEXEC，连条件都轮不到判。
 * 强杀（ActionType.TERMINATE）在各调用方先行短路，不经过本函数。
 */
fun ElementAdditionalOptions?.runEvenCancel(): Boolean = when {
    this == null -> false
    jobPostStepFlag == true -> true
    else -> runCondition == RunCondition.PRE_TASK_FAILED_EVEN_CANCEL
}
