package com.tencent.devops.process.api.builds

import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.engine.service.PipelineRuntimeService

import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class BuildVarResourceImpl @Autowired constructor(
    private val pipelineRuntimeService: PipelineRuntimeService
) : BuildVarResource {
    override fun getBuildVar(buildId: String, projectId: String, pipelineId: String, userId: String): Result<Map<String, String>> {
        checkParam(buildId = buildId, projectId = projectId, pipelineId = pipelineId, userId = userId)
        return Result(pipelineRuntimeService.getAllVariable(buildId = buildId, projectId = projectId, pipelineId = pipelineId, userId = userId))
    }

    fun checkParam(buildId: String, projectId: String, pipelineId: String, userId: String) {
        if (StringUtils.isBlank(buildId))
            throw ParamBlankException("build Id is null or blank")
        if (StringUtils.isBlank(userId))
            throw ParamBlankException("user Id is null or blank")
        if (StringUtils.isBlank(pipelineId))
            throw ParamBlankException("pipeline Id is null or blank")
    }
}