package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.metrics.service.ThirdPartyManageService
import com.tencent.metrics.api.UserThirdPartyResource
import com.tencent.metrics.pojo.dto.QueryPipelineSummaryInfoDTO
import com.tencent.metrics.pojo.vo.ThirdPartyOverviewInfoVO
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class UserThirdPartyResourceImpl @Autowired constructor(
    private val thirdPartyManageService: ThirdPartyManageService
): UserThirdPartyResource {
    override fun queryPipelineSummaryInfo(
        projectId: String,
        userId: String,
        startTime: String,
        endTime: String
    ): Result<ThirdPartyOverviewInfoVO> {
        return Result(
            thirdPartyManageService.queryPipelineSummaryInfo(
                QueryPipelineSummaryInfoDTO(
                    projectId,
                    userId,
                    startTime,
                    endTime
                )
            )
        )
    }
}