package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.metrics.service.ProjectInfoManageService
import com.tencent.metrics.api.UserProjectInfoResource
import com.tencent.metrics.pojo.`do`.AtomBaseInfoDO
import com.tencent.metrics.pojo.`do`.PipelineErrorTypeInfoDO
import com.tencent.metrics.pojo.`do`.PipelineLabelInfoDO
import com.tencent.metrics.pojo.dto.QueryProjectAtomListDTO
import com.tencent.metrics.pojo.dto.QueryProjectPipelineLabelDTO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired

class UserProjectInfoResourceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val projectInfoManageService: ProjectInfoManageService
): UserProjectInfoResource {
    override fun queryProjectAtomList(
        projectId: String,
        userId: String,
        page: Int,
        pageSize: Int
    ): Result<Page<AtomBaseInfoDO>> {
        return Result(
            projectInfoManageService.queryProjectAtomList(
                QueryProjectAtomListDTO(
                    projectId = projectId,
                    page = page,
                    pageSize = pageSize
                )
            )
        )
    }

    override fun queryProjectPipelineLabels(
        projectId: String,
        userId: String,
        pipelineIds: List<String>?,
        page: Int,
        pageSize: Int
    ): Result<List<PipelineLabelInfoDO>> {
        return Result(
            projectInfoManageService.queryProjectPipelineLabels(
                QueryProjectPipelineLabelDTO(
                    projectId,
                    pipelineIds,
                    page,
                    pageSize
                )
            )
        )
    }

    override fun queryProjectPipelineErrorTypes(userId: String): Result<List<PipelineErrorTypeInfoDO>> {
        return Result(projectInfoManageService.queryPipelineErrorTypes())
    }
}