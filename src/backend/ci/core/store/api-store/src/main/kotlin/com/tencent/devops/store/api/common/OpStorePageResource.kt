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
import com.tencent.devops.store.pojo.common.StorePageInfo
import com.tencent.devops.store.pojo.common.StorePageRequest
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

@Api(tags = ["OP_STORE_PAGE"], description = "OP-研发商店-前端页面")
@Path("/op/store/pages")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface OpStorePageResource {

    @ApiOperation("获取页面列表")
    @Path("/list")
    @GET
    fun getPageList(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("页面名称", required = false)
        @QueryParam("pageName")
        @BkField(patternStyle = BkStyleEnum.NAME_STYLE, required = false)
        pageName: String?,
        @ApiParam("页码", required = false)
        @QueryParam("page")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        page: Int?,
        @ApiParam("每页数量", required = false)
        @QueryParam("pageSize")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        pageSize: Int?
    ): Result<Page<StorePageInfo>?>

    @ApiOperation("新增页面")
    @POST
    @Path("/types/{storeType}/add")
    fun addStorePage(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("组件类型", required = true)
        @PathParam("storeType")
        @BkField(patternStyle = BkStyleEnum.CODE_STYLE)
        storeType: String,
        @ApiParam("store页面请求信息", required = true)
        @Valid
        storePageRequest: StorePageRequest
    ): Result<Boolean>

    @ApiOperation("更新页面")
    @PUT
    @Path("/{pageId}")
    fun updateStorePage(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("页面ID", required = true)
        @PathParam("pageId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE)
        pageId: String,
        @ApiParam("store页面请求信息", required = true)
        @Valid
        storePageRequest: StorePageRequest
    ): Result<Boolean>

    @ApiOperation("删除页面")
    @DELETE
    @Path("/{pageId}")
    fun deleteStorePage(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("页面ID", required = true)
        @PathParam("pageId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE)
        pageId: String
    ): Result<Boolean>
}