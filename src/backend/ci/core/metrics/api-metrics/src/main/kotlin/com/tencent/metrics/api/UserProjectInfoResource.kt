package com.tencent.metrics.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.pojo.`do`.AtomBaseInfoDO
import com.tencent.metrics.pojo.`do`.PipelineErrorTypeInfoDO
import com.tencent.metrics.pojo.vo.BaseQueryReqVO
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

@Api(tags = ["USER_PROJECT_INFO"], description = "项目-信息")
@Path("/user/project/info")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserProjectInfoResource {
    @ApiOperation("获取项目下插件列表")
    @Path("/atom/list")
    @POST
    fun queryProjectAtomList(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("流水线ID", required = false)
        pipelineIds: List<String>?,
        @ApiParam("页码", required = true, defaultValue = "1")
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<List<AtomBaseInfoDO>>

    @ApiOperation("获取项目下流水线标签列表")
    @Path("/pipeline/label/list")
    @POST
    fun queryProjectPipelineLabels(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("流水线ID", required = false)
        pipelineIds: List<String>?,
        @ApiParam("页码", required = true, defaultValue = "1")
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<List<String>>

    @ApiOperation("获取项目下流水线异常类型列表")
    @Path("/pipeline/errorType/list")
    @POST
    fun queryProjectPipelineErrorTypes(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("流水线ID", required = false)
        pipelineIds: List<String>?,
        @ApiParam("页码", required = true, defaultValue = "1")
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<List<PipelineErrorTypeInfoDO>>
}