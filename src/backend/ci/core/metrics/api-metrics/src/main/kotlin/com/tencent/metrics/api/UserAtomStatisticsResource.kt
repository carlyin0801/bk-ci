package com.tencent.metrics.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.pojo.`do`.AtomExecutionStatisticsInfoDO
import com.tencent.metrics.pojo.`vo`.AtomStatisticsInfoReqVO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import com.tencent.metrics.pojo.vo.ListPageVO
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.Consumes
import javax.ws.rs.HeaderParam
import javax.ws.rs.QueryParam
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.Produces
import javax.ws.rs.core.MediaType

@Api(tags = ["USER_ATOM_TREND_INFO"], description = "插件-统计信息")
@Path("/user/atom/trend")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserAtomStatisticsResource {
    @ApiOperation("查询插件趋势信息")
    @Path("/info")
    @POST
    fun queryAtomTrendInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("查询条件", required = true)
        condition: AtomStatisticsInfoReqVO
    ): Result<AtomTrendInfoVO>

    @ApiOperation("查询插件执行统计信息")
    @Path("execute/statistics/info")
    @POST
    fun queryAtomExecuteStatisticsInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("查询条件", required = true)
        condition: AtomStatisticsInfoReqVO,
        @ApiParam("页码", required = true, defaultValue = "1")
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<ListPageVO<AtomExecutionStatisticsInfoDO>>

}