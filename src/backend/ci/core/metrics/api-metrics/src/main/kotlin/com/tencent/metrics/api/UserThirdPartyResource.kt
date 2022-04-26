package com.tencent.metrics.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.metrics.pojo.vo.ThirdPartyOverviewInfoVO
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.HeaderParam
import javax.ws.rs.QueryParam
import javax.ws.rs.GET
import javax.ws.rs.Path
import com.tencent.devops.common.api.pojo.Result
import io.swagger.annotations.Api
import javax.ws.rs.Consumes
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Api(tags = ["USER_THIRDPARTY_OVERVIEW_DATAS"], description = "第三方-概览数据")
@Path("/user/thirdparty/overview/datas")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserThirdPartyResource {
    @ApiOperation("获取第三方汇总信息")
    @Path("/summary/data/get")
    @GET
    fun queryPipelineSummaryInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("开始时间", required = true)
        @QueryParam("startTime")
        startTime: String,
        @ApiParam("结束时间", required = true)
        @QueryParam("endTime")
        endTime: String
    ): Result<ThirdPartyOverviewInfoVO>
}