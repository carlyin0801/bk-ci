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

import com.tencent.devops.common.pipeline.enums.BuildCancelType
import io.swagger.v3.oas.annotations.media.Schema

@Schema(title = "构建取消详情")
data class BuildCancelInfo(
    @get:Schema(title = "取消类型", required = true)
    val cancelType: BuildCancelType,
    @get:Schema(title = "取消人(仅用户主动取消时有值)", required = false)
    val cancelUser: String? = null,
    @get:Schema(title = "取消原因", required = false)
    var cancelReason: String? = null,
    @get:Schema(title = "取消类型描述(国际化)", required = false)
    var cancelTypeDesc: String? = null,
    @get:Schema(title = "取消原因标识", required = false)
    val cancelReasonCode: String? = null,
    @get:Schema(title = "取消原因国际化占位符参数", required = false)
    val cancelReasonParams: List<String>? = null,
    @get:Schema(title = "取消时间戳(毫秒)", required = false)
    val cancelTime: Long? = null,
    @get:Schema(title = "取消时被终止的组件位置列表", required = false)
    var cancelPositions: List<CancelPosition>? = null,
    @get:Schema(title = "取消位置总数", required = false)
    var cancelPositionCount: Int = 0,
    @get:Schema(title = "父流水线信息(父流水线取消时)", required = false)
    val parentPipelineInfo: ParentPipelineInfo? = null
) {
    companion object {
        const val MODEL_VAR_KEY = "cancelInfo"

        /**
         * 用户主动取消，cancelUser 必填
         */
        fun ofUser(
            cancelUser: String,
            cancelReasonCode: String,
            cancelReasonParams: List<String>? = null,
            cancelReasonDefault: String? = null
        ): BuildCancelInfo {
            return BuildCancelInfo(
                cancelType = BuildCancelType.USER,
                cancelUser = cancelUser,
                cancelReason = cancelReasonDefault,
                cancelReasonCode = cancelReasonCode,
                cancelReasonParams = cancelReasonParams,
                cancelTime = System.currentTimeMillis()
            )
        }

        /**
         * 系统自动取消（心跳超时、执行超时、排队满、并发互斥等），无真实取消人
         */
        fun ofSystem(
            cancelReasonCode: String,
            cancelReasonParams: List<String>? = null,
            cancelReasonDefault: String? = null
        ): BuildCancelInfo {
            return BuildCancelInfo(
                cancelType = BuildCancelType.SYSTEM,
                cancelReason = cancelReasonDefault,
                cancelReasonCode = cancelReasonCode,
                cancelReasonParams = cancelReasonParams,
                cancelTime = System.currentTimeMillis()
            )
        }

        /**
         * 父流水线级联取消，取消动作由父流水线发起，无需指定取消人
         */
        fun ofParentPipeline(
            cancelReasonCode: String,
            parentPipelineInfo: ParentPipelineInfo,
            cancelReasonParams: List<String>? = null,
            cancelReasonDefault: String? = null
        ): BuildCancelInfo {
            return BuildCancelInfo(
                cancelType = BuildCancelType.PARENT_PIPELINE,
                cancelReason = cancelReasonDefault,
                cancelReasonCode = cancelReasonCode,
                cancelReasonParams = cancelReasonParams,
                cancelTime = System.currentTimeMillis(),
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
