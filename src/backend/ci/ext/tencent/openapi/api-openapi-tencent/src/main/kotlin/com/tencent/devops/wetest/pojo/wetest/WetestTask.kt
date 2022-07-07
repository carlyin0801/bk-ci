/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
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

package com.tencent.devops.wetest.pojo.wetest

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("WeTest测试任务")
data class WetestTask(
    @ApiModelProperty("ID")
    val id: Int,
    @ApiModelProperty("项目Id")
    val projectId: String,
    @ApiModelProperty("名称")
    val name: String,
    @ApiModelProperty("手机类别")
    val mobileCategory: String,
    @ApiModelProperty("手机类别ID")
    val mobileCategoryId: String,
    @ApiModelProperty("选择机型")
    val mobileModel: String,
    @ApiModelProperty("选择机型ID")
    val mobileModelId: String,
    @ApiModelProperty("设备类型 Android 0和ios 2")
    val deviceType: String,
    @ApiModelProperty("描述")
    val description: String?,
    @ApiModelProperty("凭证ID")
    val ticketsId: String?,
    @ApiModelProperty("创建时间")
    val createTime: String,
    @ApiModelProperty("修改时间")
    val updateTime: String

)