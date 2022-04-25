package com.tencent.metrics.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.pojo.`do`.AtomErrorCodeCountInfoDO
import com.tencent.metrics.pojo.`do`.AtomFailDetailInfoDO
import com.tencent.metrics.pojo.vo.PipelineAtomInfoReqVO
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

@Api(tags = ["USER_ATOM_INFO"], description = "插件-失败信息")
@Path("/user/pipeline/atom/fail")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserAtomFailInfoResource {
    @ApiOperation("查询流水线失败错误码统计信息")
    @Path("/code")
    @POST
    fun queryPipelineAtomErrorCodeInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("查询条件", required = true)
        QueryCondition: PipelineAtomInfoReqVO
    ): Result<AtomErrorCodeCountInfoDO>

    @ApiOperation("查询流水线插件失败详情数据")
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
        QueryCondition: PipelineAtomInfoReqVO,
        @ApiParam("页码", required = true, defaultValue = "1")
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<ListPageVO<AtomFailDetailInfoDO>>

}