package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.metrics.service.PipelineStageManageService
import com.tencent.metrics.api.UserPipelineStageResource
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.vo.BaseQueryReqVO
import com.tencent.metrics.pojo.vo.StageTrendSumInfoVO
import org.springframework.beans.factory.annotation.Autowired


class UserPipelineStageResourceImpl @Autowired constructor(
    private val pipelineStageManageService: PipelineStageManageService
): UserPipelineStageResource {
    override fun queryPipelineStageTrendInfo(
        projectId: String,
        userId: String,
        queryReqVo: BaseQueryReqVO
    ): Result<List<StageTrendSumInfoVO>> {
        return Result(
            pipelineStageManageService.queryPipelineStageTrendInfo(
                QueryPipelineOverviewDTO(
                    projectId,
                    userId,
                    queryReqVo.queryReq
                )
            )
        )
    }
}