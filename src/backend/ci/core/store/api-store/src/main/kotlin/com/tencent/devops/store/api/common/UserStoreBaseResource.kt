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
import com.tencent.devops.store.pojo.common.StoreDetailInfo
import com.tencent.devops.store.pojo.common.StoreMainPageItemInfo
import com.tencent.devops.store.pojo.common.StoreTypeInfo
import com.tencent.devops.store.pojo.common.enums.RdTypeEnum
import com.tencent.devops.store.pojo.common.enums.StoreSortTypeEnum
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path
import javax.ws.rs.PathParam
import javax.ws.rs.Produces
import javax.ws.rs.QueryParam
import javax.ws.rs.core.MediaType

@Api(tags = ["USER_STORE"], description = "研发商店")
@Path("/user/store")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserStoreBaseResource {

    @ApiOperation("获取组件类型列表")
    @GET
    @Path("/types/list")
    fun getStoreTypeList(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String
    ): Result<List<StoreTypeInfo>>

    @ApiOperation("获取研发商店首页组件列表")
    @Path("/types/{storeType}/main/page/list")
    @GET
    fun getMainPageList(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("组件类型", required = true)
        @PathParam("storeType")
        @BkField(patternStyle = BkStyleEnum.CODE_STYLE)
        storeType: String,
        @ApiParam("搜索关键字", required = false)
        @QueryParam("keyword")
        @BkField(patternStyle = BkStyleEnum.COMMON_STYLE, required = false)
        keyword: String?,
        @ApiParam("分类ID", required = false)
        @QueryParam("classifyId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE, required = false)
        classifyId: String?,
        @ApiParam("标签ID", required = false)
        @QueryParam("labelId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE, required = false)
        labelId: String?,
        @ApiParam("评分", required = false)
        @QueryParam("score")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        score: Int?,
        @ApiParam("研发来源", required = false)
        @QueryParam("rdType")
        rdType: RdTypeEnum?,
        @ApiParam("是否推荐标识 true：推荐，false：不推荐", required = false)
        @QueryParam("recommendFlag")
        @BkField(patternStyle = BkStyleEnum.BOOLEAN_STYLE, required = false)
        recommendFlag: Boolean?,
        @ApiParam("排序", required = false)
        @QueryParam("sortType")
        sortType: StoreSortTypeEnum? = StoreSortTypeEnum.CREATE_TIME,
        @ApiParam("扩展数据", required = false)
        @QueryParam("extData")
        @BkField(patternStyle = BkStyleEnum.COMMON_STYLE, required = false)
        extData: String?,
        @ApiParam("页码", required = false)
        @QueryParam("page")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        page: Int?,
        @ApiParam("每页数量", required = false)
        @QueryParam("pageSize")
        @BkField(patternStyle = BkStyleEnum.NUMBER_STYLE, required = false)
        pageSize: Int?
    ): Result<Page<StoreMainPageItemInfo>>

    @ApiOperation("根据组件ID获取组件详情")
    @GET
    @Path("/types/{storeType}/ids/{storeId}/detail")
    fun getStoreDetailInfoById(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("组件类型", required = true)
        @PathParam("storeType")
        @BkField(patternStyle = BkStyleEnum.CODE_STYLE)
        storeType: String,
        @ApiParam("组件ID", required = true)
        @PathParam("storeId")
        @BkField(patternStyle = BkStyleEnum.ID_STYLE, required = false)
        storeId: String
    ): Result<StoreDetailInfo?>

    @ApiOperation("根据组件代码获取组件详情")
    @GET
    @Path("/types/{storeType}/codes/{storeCode}/detail")
    fun getStoreDetailInfoByCode(
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("组件类型", required = true)
        @PathParam("storeType")
        @BkField(patternStyle = BkStyleEnum.CODE_STYLE)
        storeType: String,
        @ApiParam("组件代码", required = true)
        @PathParam("storeCode")
        @BkField(patternStyle = BkStyleEnum.CODE_STYLE)
        storeCode: String
    ): Result<StoreDetailInfo?>
}