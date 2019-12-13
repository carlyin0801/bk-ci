package com.tencent.devops.process.api.builds

import com.tencent.devops.common.web.RestResource
import com.tencent.devops.process.api.service.BuildOperationResource
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import org.springframework.beans.factory.annotation.Autowired
import com.tencent.devops.common.api.pojo.Result


@RestResource
class BuildOperationResourceImpl @Autowired constructor(
		private val pipelineRepositoryService: PipelineRepositoryService
): BuildOperationResource {

	override fun getUpdateUser(pipelineId: String): Result<String> {
		val pipelineInfo = pipelineRepositoryService.getPipelineInfo(pipelineId)
		if(pipelineInfo != null) {
			return Result(pipelineInfo!!.lastModifyUser)
		}
		return Result("")
	}
}