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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.store.pojo.common

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("研发商店组件页面展示项信息")
data class StoreMainPageItemInfo(
    @ApiModelProperty("组件ID", required = true)
    val storeId: String,
    @ApiModelProperty("组件名称", required = true)
    val storeName: String,
    @ApiModelProperty("组件标识", required = true)
    val storeCode: String,
    @ApiModelProperty("研发来源", required = true)
    val rdType: String,
    @ApiModelProperty("所属分类", required = false)
    val classifyId: String? = null,
    @ApiModelProperty("logo链接", required = false)
    val logoUrl: String?,
    @ApiModelProperty("发布者", required = false)
    val publisher: String?,
    @ApiModelProperty("发布时间，格式为yyyy-MM-dd HH:mm:ss", required = false)
    val pubTime: String?,
    @ApiModelProperty("下载量", required = false)
    val downloads: Int? = 0,
    @ApiModelProperty("评分", required = false)
    val score: Double?,
    @ApiModelProperty("简介", required = false)
    val summary: String?,
    @ApiModelProperty("是否是公共组件", required = true)
    val publicFlag: Boolean,
    @ApiModelProperty("帮助文档", required = false)
    val docsLink: String? = null,
    @ApiModelProperty("是否推荐标识 true：推荐，false：不推荐", required = false)
    val recommendFlag: Boolean? = null
)