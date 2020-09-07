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

package com.tencent.devops.store.api.common

import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.annotation.BkField
import com.tencent.devops.common.web.constant.BkStyleEnum
import com.tencent.devops.store.pojo.common.StorePageModelInfo
import com.tencent.devops.store.pojo.common.StorePageModelRequest
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.validation.Valid
import javax.ws.rs.Consumes
import javax.ws.rs.DELETE
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.POST
import javax.ws.rs.PUT
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["OP_STORE_PAGE_MODEL"], description = "OP-研发商店-前端页面模型")
@Path("/op/store/page/models")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface OpStorePageModelResource {

    @ApiOperation("获取页面对应的model列表")
    @Path("/list")
    @GET
    fun getPageModelList(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("模型名称", required = false)
        @QueryParam("modelName")
        @BkField(patternStyle = BkStyleEnum.NAME_STYLE, required = false)
        modelName: String?,
        @ApiParam("页码", required = false)
        @QueryParam("page")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        page: Int?,
        @ApiParam("每页数量", required = false)
        @QueryParam("pageSize")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        pageSize: Int?
    ): Result<Page<StorePageModelInfo>?>

    @ApiOperation("新增页面模型")
    @POST
    @Path("/types/{storeType}/add")
    fun addStorePageModel(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("组件类型", required = true)
        @PathParam("storeType")
        @BkField(patternStyle = BkStyleEnum.CODE_STYLE)
        storeType: String,
        @ApiParam("store页面模型请求信息", required = true)
        @Valid
        storePageModelRequest: StorePageModelRequest
    ): Result<Boolean>

    @ApiOperation("更新页面模型")
    @PUT
    @Path("/{modelId}")
    fun updateStorePageModel(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("模型ID", required = true)
        @PathParam("modelId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE)
        modelId: String,
        @ApiParam("store页面模型请求信息", required = true)
        @Valid
        storePageModelRequest: StorePageModelRequest
    ): Result<Boolean>

    @ApiOperation("删除页面模型")
    @DELETE
    @Path("/{modelId}")
    fun deleteStorePageModel(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("模型ID", required = true)
        @PathParam("modelId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE)
        modelId: String
    ): Result<Boolean>
}