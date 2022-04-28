package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.metrics.service.PipelineFailManageService
import com.tencent.metrics.api.UserPipelineFailResource
import com.tencent.metrics.pojo.`do`.PipelineFailDetailInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineFailDTO
import com.tencent.metrics.pojo.dto.QueryPipelineFailTrendInfoDTO
import com.tencent.metrics.pojo.vo.PipelineFailTrendInfoVO
import com.tencent.metrics.pojo.vo.PipelineFailInfoQueryReqVO
import com.tencent.metrics.pojo.vo.PipelineFailSumInfoVO
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class UserPipelineFailResourceImpl @Autowired constructor(
    private val pipelineFailManageService: PipelineFailManageService
): UserPipelineFailResource {
    override fun queryPipelineFailTrendInfo(
        projectId: String,
        userId: String,
        queryReqVo: PipelineFailInfoQueryReqVO
    ): Result<List<PipelineFailTrendInfoVO>> {
        return Result(
            pipelineFailManageService.queryPipelineFailTrendInfo(
                QueryPipelineFailTrendInfoDTO(
                    projectId,
                    queryReqVo.queryReq
                )
            )
        )
    }

    override fun queryPipelineFailSumInfo(
        projectId: String,
        userId: String,
        queryReqVo: PipelineFailInfoQueryReqVO
    ): Result<PipelineFailSumInfoVO> {
        return Result(
            PipelineFailSumInfoVO(
                pipelineFailManageService.queryPipelineFailSumInfo(
                    QueryPipelineFailDTO(
                        projectId,
                        queryReqVo.queryReq,
                        queryReqVo.errorTypes
                    )
                )
            )

        )
    }

    override fun queryPipelineFailDetailInfo(
        projectId: String,
        userId: String,
        queryReqVo: PipelineFailInfoQueryReqVO,
        page: Int,
        pageSize: Int
    ): Result<Page<PipelineFailDetailInfoDO>> {
        val limit = PageUtil.convertPageSizeToSQLMAXLimit(page, pageSize)
        return Result(
            pipelineFailManageService.queryPipelineFailDetailInfo(
                QueryPipelineFailDTO(
                    projectId,
                    queryReqVo.queryReq,
                    queryReqVo.errorTypes,
                    page,
                    pageSize
                )
            )
        )
    }
}