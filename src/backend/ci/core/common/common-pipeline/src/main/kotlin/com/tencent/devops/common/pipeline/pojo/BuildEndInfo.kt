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

package com.tencent.devops.common.pipeline.pojo

import com.tencent.devops.common.pipeline.enums.BuildEndType
import io.swagger.v3.oas.annotations.media.Schema

/**
 * 构建终态详情——统一描述取消/失败/超时等所有非正常结束场景。
 *
 * 当前阶段实现取消（CANCEL_USER / CANCEL_SYSTEM / CANCEL_PARENT_PIPELINE），
 * 后续可无缝扩展失败（FAIL_*）和超时（TIMEOUT_*）等类型，
 * 复用同一套 endType / reason / positions / parentPipelineInfo 字段。
 */
@Schema(title = "构建终态详情")
data class BuildEndInfo(
    @get:Schema(title = "终态子类型", required = true)
    val endType: BuildEndType,
    @get:Schema(title = "操作人(仅用户主动操作时有值)", required = false)
    val operator: String? = null,
    @get:Schema(title = "终态原因(兜底文案)", required = false)
    var reason: String? = null,
    @get:Schema(title = "终态子类型描述(国际化)", required = false)
    var endTypeDesc: String? = null,
    @get:Schema(title = "终态原因国际化标识", required = false)
    val reasonCode: String? = null,
    @get:Schema(title = "终态原因国际化占位符参数", required = false)
    val reasonParams: List<String>? = null,
    @get:Schema(title = "终态时间戳(毫秒)", required = false)
    val endTime: Long? = null,
    @get:Schema(title = "被影响的组件位置列表", required = false)
    var positions: List<EndPosition>? = null,
    @get:Schema(title = "被影响位置总数", required = false)
    var positionCount: Int = 0,
    @get:Schema(title = "父流水线信息(父流水线级联终止时)", required = false)
    val parentPipelineInfo: ParentPipelineInfo? = null
) {
    companion object {
        const val MODEL_VAR_KEY = "buildEndInfo"

        /**
         * 用户主动取消
         */
        fun ofCancelUser(
            operator: String,
            reasonCode: String,
            reasonParams: List<String>? = null,
            reasonDefault: String? = null
        ): BuildEndInfo {
            return BuildEndInfo(
                endType = BuildEndType.CANCEL_USER,
                operator = operator,
                reason = reasonDefault,
                reasonCode = reasonCode,
                reasonParams = reasonParams,
                endTime = System.currentTimeMillis()
            )
        }

        /**
         * 系统自动取消（心跳超时、执行超时、排队满、并发互斥等）
         */
        fun ofCancelSystem(
            reasonCode: String,
            reasonParams: List<String>? = null,
            reasonDefault: String? = null
        ): BuildEndInfo {
            return BuildEndInfo(
                endType = BuildEndType.CANCEL_SYSTEM,
                reason = reasonDefault,
                reasonCode = reasonCode,
                reasonParams = reasonParams,
                endTime = System.currentTimeMillis()
            )
        }

        /**
         * 父流水线级联取消
         */
        fun ofCancelParentPipeline(
            reasonCode: String,
            parentPipelineInfo: ParentPipelineInfo,
            reasonParams: List<String>? = null,
            reasonDefault: String? = null
        ): BuildEndInfo {
            return BuildEndInfo(
                endType = BuildEndType.CANCEL_PARENT_PIPELINE,
                reason = reasonDefault,
                reasonCode = reasonCode,
                reasonParams = reasonParams,
                endTime = System.currentTimeMillis(),
                parentPipelineInfo = parentPipelineInfo
            )
        }
    }
}

@Schema(title = "父流水线信息")
data class ParentPipelineInfo(
    @get:Schema(title = "父流水线ID", required = true)
    val pipelineId: String,
    @get:Schema(title = "父流水线名称", required = false)
    val pipelineName: String? = null,
    @get:Schema(title = "父构建ID", required = true)
    val buildId: String,
    @get:Schema(title = "父构建号", required = false)
    val buildNum: Int? = null
)
