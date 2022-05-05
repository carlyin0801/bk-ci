package com.tencent.metrics.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.pojo.`do`.PipelineFailDetailInfoDO
import com.tencent.metrics.pojo.vo.PipelineFailInfoQueryReqVO
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.web.annotation.BkField
import com.tencent.metrics.pojo.vo.PipelineFailSumInfoVO
import com.tencent.metrics.pojo.vo.PipelineFailTrendInfoVO
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

@Api(tags = ["USER_PIPELINE_FAIL_INFOS"], description = "流水线-失败统计")
@Path("/user/pipeline/fail/infos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserPipelineFailResource {

    @ApiOperation("查询流水线失败趋势数据")
    @Path("/trend/info")
    @POST
    fun queryPipelineFailTrendInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("查询条件", required = true)
        queryReqVo: PipelineFailInfoQueryReqVO
    ): Result<List<PipelineFailTrendInfoVO>>

    @ApiOperation("查询流水线错误类型统计数据")
    @Path("/errorType/summary/data/get")
    @POST
    fun queryPipelineFailSumInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("查询条件", required = true)
        queryReqVo: PipelineFailInfoQueryReqVO
    ): Result<PipelineFailSumInfoVO>

    @ApiOperation("查询流水线失败详情数据")
    @Path("/details")
    @POST
    fun queryPipelineFailDetailInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("查询条件", required = true)
        queryReqVo: PipelineFailInfoQueryReqVO,
        @ApiParam("页码", required = true, defaultValue = "1")
        @BkField(minLength = 1)
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @BkField(minLength = 10, maxLength = 100)
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<Page<PipelineFailDetailInfoDO>>
}