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

@ApiModel("研发商店组件详细信息")
data class StoreDetailInfo(
    @ApiModelProperty("组件ID", required = true)
    val storeId: String,
    @ApiModelProperty("组件名称", required = true)
    val storeName: String,
    @ApiModelProperty("组件标识", required = true)
    val storeCode: String,
    @ApiModelProperty("版本号")
    val version: String?,
    @ApiModelProperty("组件状态", required = true)
    val storeStatus: String,
    @ApiModelProperty("研发来源", required = true)
    val rdType: String,
    @ApiModelProperty("所属分类", required = false)
    val classify: Classify? = null,
    @ApiModelProperty("logo链接", required = false)
    val logoUrl: String?,
    @ApiModelProperty("发布者", required = false)
    val publisher: String?,
    @ApiModelProperty("发布时间", required = false)
    val pubTime: String?,
    @ApiModelProperty("下载量", required = false)
    val downloads: Int? = 0,
    @ApiModelProperty("评分", required = false)
    val score: Double?,
    @ApiModelProperty("简介", required = false)
    val summary: String?,
    @ApiModelProperty("描述", required = false)
    val description: String?,
    @ApiModelProperty("范畴列表", required = false)
    val categoryList: List<Category>?,
    @ApiModelProperty("标签列表", required = false)
    val labelList: List<Label>?,
    @ApiModelProperty("是否为最新版本 true：最新 false：非最新", required = true)
    val latestFlag: Boolean,
    @ApiModelProperty("是否有权限安装标识", required = true)
    val installFlag: Boolean,
    @ApiModelProperty("是否是公共组件", required = true)
    val publicFlag: Boolean,
    @ApiModelProperty("是否推荐标识 true：推荐，false：不推荐", required = true)
    val recommendFlag: Boolean,
    @ApiModelProperty("是否官方认证 true：是 false：否", required = true)
    val certificationFlag: Boolean,
    @ApiModelProperty("是否有处于上架状态的模板版本", required = true)
    val releaseFlag: Boolean,
    @ApiModelProperty("用户评论信息", required = true)
    val userCommentInfo: StoreUserCommentInfo,
    @ApiModelProperty("扩展字段集合", required = false)
    val extData: Map<String, Any>? = null
)