package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.metrics.service.AtomStatisticsManageService
import com.tencent.metrics.api.UserAtomStatisticsResource
import com.tencent.metrics.pojo.`do`.AtomExecutionStatisticsInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomStatisticsInfoDTO
import com.tencent.metrics.pojo.vo.AtomStatisticsInfoReqVO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import com.tencent.metrics.pojo.vo.ListPageVO
import org.springframework.beans.factory.annotation.Autowired

class UserAtomStatisticsResourceImpl @Autowired constructor(
    private val atomStatisticsManageService: AtomStatisticsManageService
): UserAtomStatisticsResource {
    override fun queryAtomTrendInfo(
        projectId: String,
        userId: String,
        condition: AtomStatisticsInfoReqVO
    ): Result<AtomTrendInfoVO> {
        return Result(
            atomStatisticsManageService.queryAtomTrendInfo(
                QueryAtomStatisticsInfoDTO(
                    projectId,
                    condition.baseQueryReqDO,
                    condition.errorTypes,
                    condition.atomCodes
                )
            )
        )
    }

    override fun queryAtomExecuteStatisticsInfo(
        projectId: String,
        userId: String,
        condition: AtomStatisticsInfoReqVO,
        page: Int,
        pageSize: Int
    ): Result<ListPageVO<AtomExecutionStatisticsInfoDO>> {
        TODO("Not yet implemented")
    }
}