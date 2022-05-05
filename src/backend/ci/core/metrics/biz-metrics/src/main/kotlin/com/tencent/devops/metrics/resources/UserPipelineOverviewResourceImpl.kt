package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.metrics.service.PipelineOverviewManageService
import com.tencent.metrics.api.UserPipelineOverviewResource
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.vo.PipelineSumInfoVO
import com.tencent.metrics.pojo.vo.PipelineTrendInfoVO
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class UserPipelineOverviewResourceImpl @Autowired constructor(
    private val pipelineOverviewManageService: PipelineOverviewManageService
    ): UserPipelineOverviewResource {
    override fun queryPipelineSumInfo(
        projectId: String,
        userId: String,
        baseQueryReq: BaseQueryReqDO
    ): Result<PipelineSumInfoVO> {

        return Result(
            PipelineSumInfoVO(
                pipelineOverviewManageService.queryPipelineSumInfo(
                    QueryPipelineOverviewDTO(
                        projectId = projectId,
                        userId = userId,
                        baseQueryReq = baseQueryReq
                    )
                )
            )
        )
    }

    override fun queryPipelineTrendInfo(
        projectId: String,
        userId: String,
        baseQueryReq: BaseQueryReqDO
    ): Result<PipelineTrendInfoVO> {

        return Result(
            PipelineTrendInfoVO(
                pipelineOverviewManageService.queryPipelineTrendInfo(
                    QueryPipelineOverviewDTO(
                        projectId = projectId,
                        userId = userId,
                        baseQueryReq = baseQueryReq
                    )
                )
            )
        )
    }
}