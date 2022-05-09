package com.tencent.devops.metrics.resources;

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.metrics.service.AtomFailInfoManageService
import com.tencent.metrics.api.UserAtomFailInfoResource
import com.tencent.metrics.pojo.`do`.AtomFailDetailInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomFailInfoDTO
import com.tencent.metrics.pojo.vo.AtomErrorCodeStatisticsInfoVO
import com.tencent.metrics.pojo.vo.AtomFailInfoReqVO
import org.springframework.beans.factory.annotation.Autowired


class UserAtomFailInfoResourceImpl @Autowired constructor(
    private val atomFailInfoManageService: AtomFailInfoManageService
): UserAtomFailInfoResource {
    override fun queryAtomErrorCodeStatisticsInfo(
        projectId: String,
        userId: String,
        atomFailInfoReq: AtomFailInfoReqVO
    ): Result<AtomErrorCodeStatisticsInfoVO> {
        return Result(
            atomFailInfoManageService.queryAtomErrorCodeStatisticsInfo(
                QueryAtomFailInfoDTO(
                    projectId,
                    atomFailInfoReq.baseQueryReq,
                    atomFailInfoReq.errorTypes,
                    atomFailInfoReq.errorCodes
                )
            )
        )
    }

    override fun queryPipelineFailDetailInfo(
        projectId: String,
        userId: String,
        atomFailInfoReq: AtomFailInfoReqVO,
        page: Int,
        pageSize: Int
    ): Result<Page<AtomFailDetailInfoDO>> {
        return Result(
            atomFailInfoManageService.queryPipelineFailDetailInfo(
                QueryAtomFailInfoDTO(
                    projectId,
                    atomFailInfoReq.baseQueryReq,
                    atomFailInfoReq.errorTypes,
                    atomFailInfoReq.errorCodes,
                    page,
                    pageSize
                )
            )
        )
    }
}
