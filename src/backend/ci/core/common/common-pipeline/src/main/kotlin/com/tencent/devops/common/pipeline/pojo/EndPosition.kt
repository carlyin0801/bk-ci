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

import io.swagger.v3.oas.annotations.media.Schema

/**
 * 构建终态位置详情，统一描述取消/失败/超时时被影响的具体组件。
 */
@Schema(title = "终态位置详情")
data class EndPosition(
    @get:Schema(title = "位置编码(如3-1-1表示Stage3的Job1的Task1, 3-2表示Stage3的Job2)", required = true)
    val position: String,
    @get:Schema(title = "组件路径(如: 质量检查/单元测试/执行单测)", required = true)
    val componentPath: String,
    @get:Schema(title = "终态时的组件状态枚举值(BuildStatus.name)", required = true)
    val statusAtEnd: String,
    @get:Schema(title = "终态时的组件状态描述(国际化)", required = false)
    var statusAtEndDesc: String? = null,
    @get:Schema(title = "阶段ID(锚点定位)", required = true)
    val stageId: String,
    @get:Schema(title = "容器ID(锚点定位)", required = true)
    val containerId: String,
    @get:Schema(title = "插件ID(锚点定位,task级别时填充)", required = false)
    val taskId: String? = null,
    @get:Schema(title = "是否构建矩阵", required = false)
    val matrixFlag: Boolean? = null,
    @get:Schema(title = "依赖Job导航信息(DEPENDENT_WAITING状态时填充)", required = false)
    val dependOnJobs: List<DependOnJobInfo>? = null
)

@Schema(title = "依赖Job导航信息")
data class DependOnJobInfo(
    @get:Schema(title = "依赖的Job名称", required = true)
    val jobName: String,
    @get:Schema(title = "项目ID", required = true)
    val projectId: String,
    @get:Schema(title = "流水线ID", required = true)
    val pipelineId: String,
    @get:Schema(title = "构建ID", required = true)
    val buildId: String,
    @get:Schema(title = "执行次数", required = true)
    val executeCount: Int,
    @get:Schema(title = "依赖Job所在阶段ID(锚点定位)", required = true)
    val stageId: String,
    @get:Schema(title = "依赖Job的容器ID(锚点定位)", required = true)
    val containerId: String,
    @get:Schema(title = "依赖Job是否为构建矩阵", required = false)
    val matrixFlag: Boolean? = null
)
