package com.tencent.devops.process.api.builds

import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.process.engine.service.PipelineRuntimeService

import org.apache.commons.lang.StringUtils
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class BuildVarResourceImpl @Autowired constructor(
    private val pipelineRuntimeService: PipelineRuntimeService
) : BuildVarResource {
    override fun getBuildVar(buildId: String?, projectId: String?, pipelineId: String?, key: String?): Result<MutableMap<String, String>> {
        throw ParamBlankException("buildId && projectId && pipelineId are all null or blank")
        checkParam(buildId = buildId, projectId = projectId, pipelineId = pipelineId)
        return Result(pipelineRuntimeService.getVariable(buildId = buildId, projectId = projectId, pipelineId = pipelineId, key = key))
    }

    fun checkParam(buildId: String?, projectId: String?, pipelineId: String?) {
        if (StringUtils.isBlank(buildId) && StringUtils.isBlank(projectId) && StringUtils.isBlank(pipelineId))
            throw ParamBlankException("buildId && projectId && pipelineId are all null or blank")
    }
}