package com.tencent.metrics.api

import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.auth.AUTH_HEADER_USER_ID
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.annotation.BkField
import com.tencent.metrics.pojo.`do`.ErrorCodeInfoDO
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

@Api(tags = ["USER__ERRORCODE_INFOS"], description = "插件-错误码信息")
@Path("/user/errorCode/infos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
interface UserErrorCodeInfoResource {

    @ApiOperation("获取错误码列表")
    @Path("/list")
    @POST
    fun getErrorCodeInfo(
        @ApiParam("项目ID", required = true)
        @HeaderParam(AUTH_HEADER_DEVOPS_PROJECT_ID)
        projectId: String,
        @ApiParam("userId", required = true)
        @HeaderParam(AUTH_HEADER_USER_ID)
        userId: String,
        @ApiParam("错误类型", required = false)
        errorTypes: List<Int>?,
        @ApiParam("页码", required = true, defaultValue = "1")
        @BkField(minLength = 1)
        @QueryParam("page")
        page: Int,
        @ApiParam("每页大小", required = true, defaultValue = "10")
        @BkField(minLength = 10, maxLength = 100)
        @QueryParam("pageSize")
        pageSize: Int
    ): Result<List<ErrorCodeInfoDO>>
}