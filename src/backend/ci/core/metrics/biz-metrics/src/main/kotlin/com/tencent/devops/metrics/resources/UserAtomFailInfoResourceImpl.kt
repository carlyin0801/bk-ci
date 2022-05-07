package com.tencent.devops.metrics.resources;

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.api.UserAtomFailInfoResource
import com.tencent.metrics.pojo.`do`.AtomFailDetailInfoDO
import com.tencent.metrics.pojo.vo.AtomErrorCodeStatisticsInfoVO
import com.tencent.metrics.pojo.vo.AtomFailInfoReqVO

class UserAtomFailInfoResourceImpl: UserAtomFailInfoResource {
    override fun queryAtomErrorCodeStatisticsInfo(
        projectId: String,
        userId: String,
        atomFailInfoReq: AtomFailInfoReqVO
    ): Result<AtomErrorCodeStatisticsInfoVO> {
        TODO("Not yet implemented")
    }

    override fun queryPipelineFailDetailInfo(
        projectId: String,
        userId: String,
        atomFailInfoReq: AtomFailInfoReqVO,
        page: Int,
        pageSize: Int
    ): Result<Page<AtomFailDetailInfoDO>> {
        TODO("Not yet implemented")
    }
}
