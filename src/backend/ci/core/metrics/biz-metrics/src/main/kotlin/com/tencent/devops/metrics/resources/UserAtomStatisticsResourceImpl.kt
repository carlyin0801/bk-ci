package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.api.UserAtomStatisticsResource
import com.tencent.metrics.pojo.`do`.AtomExecutionStatisticsInfoDO
import com.tencent.metrics.pojo.vo.AtomStatisticsInfoReqVO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import com.tencent.metrics.pojo.vo.ListPageVO

class UserAtomStatisticsResourceImpl: UserAtomStatisticsResource {
    override fun queryAtomTrendInfo(
        projectId: String,
        userId: String,
        condition: AtomStatisticsInfoReqVO
    ): Result<AtomTrendInfoVO> {
        TODO("Not yet implemented")
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