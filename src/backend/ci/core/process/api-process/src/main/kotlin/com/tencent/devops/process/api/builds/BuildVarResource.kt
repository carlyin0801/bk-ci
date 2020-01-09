package com.tencent.devops.process.api.builds

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_BUILD_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PIPELINE_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.pojo.Result
import io.swagger.annotations.ApiOperation
import io.swagger.annotations.ApiParam
import javax.ws.rs.GET
import javax.ws.rs.HeaderParam
import javax.ws.rs.Path

interface BuildVarResource {
    @ApiOperation("获取指定构建或指定流水线下的构建变量")
    @Path("/getBuildVar")
    @GET
    fun getBuildVar(
            @ApiParam(value = "构建ID", required = false)
            @HeaderParam(AUTH_HEADER_DEVOPS_BUILD_ID)
            buildId: String?,
            @ApiParam(value = "项目ID", required = false)
            @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
            projectId: String?,
            @ApiParam(value = "流水线ID", required = false)
            @HeaderParam(AUTH_HEADER_DEVOPS_PIPELINE_ID)
            pipelineId: String?,
            @ApiParam(value = "构建参数key值", required = false)
            @HeaderParam("key")
            key: String?
    ): Result<MutableMap<String, String>>
}