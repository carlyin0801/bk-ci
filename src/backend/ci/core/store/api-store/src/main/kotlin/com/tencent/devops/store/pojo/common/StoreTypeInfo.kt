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

@ApiModel("组件类型信息")
data class StoreTypeInfo(
    @ApiModelProperty("主键ID", required = true)
    val typeId: String,
    @ApiModelProperty("组件类型名称", required = true)
    val typeName: String,
    @ApiModelProperty("组件类型代码", required = true)
    val typeCode: String,
    @ApiModelProperty("是否展示", required = true)
    val showFlag: Boolean,
    @ApiModelProperty("前端渲染模板版本（1.0代表历史存量组件渲染模板版本）")
    val htmlTemplateVersion: String,
    @ApiModelProperty("创建日期，格式为yyyy-MM-dd HH:mm:ss")
    val createTime: String,
    @ApiModelProperty("更新日期，格式为yyyy-MM-dd HH:mm:ss")
    val updateTime: String,
    @ApiModelProperty("创建人", required = true)
    val creator: String,
    @ApiModelProperty("最近修改人", required = true)
    val modifier: String
)