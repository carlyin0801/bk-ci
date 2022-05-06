package com.tencent.devops.metrics.service.impl

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.metrics.service.PipelineFailManageService
import com.tencent.devops.metrics.dao.PipelineFailDao
import com.tencent.metrics.constant.BK_QUERY_COUNT_MAX
import com.tencent.metrics.constant.MetricsMessageCode
import com.tencent.metrics.pojo.`do`.*
import com.tencent.metrics.pojo.dto.QueryPipelineFailDTO
import com.tencent.metrics.pojo.dto.QueryPipelineFailTrendInfoDTO
import com.tencent.metrics.pojo.qo.QueryPipelineFailQO
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import com.tencent.metrics.pojo.vo.PipelineFailTrendInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PipelineFailServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineFailDao: PipelineFailDao
): PipelineFailManageService {

    override fun queryPipelineFailTrendInfo(
        queryPipelineFailTrendInfoDTO: QueryPipelineFailTrendInfoDTO
    ): List<PipelineFailTrendInfoVO> {

        val typeInfos = pipelineFailDao.queryPipelineFailErrorTypeInfo(
            dslContext,
            QueryPipelineOverviewQO(
                projectId = queryPipelineFailTrendInfoDTO.projectId,
                baseQueryReq = queryPipelineFailTrendInfoDTO.baseQueryReq
            )
        )
        val failTrendInfos = typeInfos.map { failTrendInfo ->
            val result = pipelineFailDao.queryPipelineFailTrendInfo(
                dslContext,
                QueryPipelineOverviewQO(
                    projectId = queryPipelineFailTrendInfoDTO.projectId,
                    baseQueryReq = queryPipelineFailTrendInfoDTO.baseQueryReq
                ),
                failTrendInfo.value1()
            )
            val failStatisticsInfos = result.map { failStatisticsInfo ->
                PipelineFailStatisticsInfoDO(
                    statisticsTime = failStatisticsInfo.value1(),
                    errorCount = failStatisticsInfo.value2().toInt()
                )
            }
            PipelineFailTrendInfoVO(
                errorType = failTrendInfo.value1(),
                name = failTrendInfo.value2(),
                failInfos = failStatisticsInfos
            )
        }
        return failTrendInfos
    }

    override fun queryPipelineFailSumInfo(queryPipelineFailDTO: QueryPipelineFailDTO): List<PipelineFailInfoDO> {
        val result = pipelineFailDao.queryPipelineFailSumInfo(
            dslContext,
            QueryPipelineFailQO(
                queryPipelineFailDTO.projectId,
                queryPipelineFailDTO.baseQueryReq,
                queryPipelineFailDTO.errorTypes
            )
        )
        return result.map {
            PipelineFailInfoDO(
                errorType = it.value1(),
                name = it.value2(),
                errorCount = it.value3().toInt()
            )
        }

    }

    override fun queryPipelineFailDetailInfo(queryPipelineFailDTO: QueryPipelineFailDTO): Page<PipelineFailDetailInfoDO> {
        // 查询符合查询条件的记录数
        val queryPipelineFailDetailCount = pipelineFailDao.queryPipelineFailDetailCount(
            dslContext,
            QueryPipelineFailQO(
                queryPipelineFailDTO.projectId,
                queryPipelineFailDTO.baseQueryReq,
                queryPipelineFailDTO.errorTypes
            )
        )
        if (queryPipelineFailDetailCount > BK_QUERY_COUNT_MAX) {
            throw ErrorCodeException(
                errorCode = MetricsMessageCode.QUERY_DETAILS_COUNT_BEYOND
            )
        }
        val result = pipelineFailDao.queryPipelineFailDetailInfo(
            dslContext,
            QueryPipelineFailQO(
                queryPipelineFailDTO.projectId,
                queryPipelineFailDTO.baseQueryReq,
                queryPipelineFailDTO.errorTypes,
                PageUtil.convertPageSizeToSQLMAXLimit(
                    queryPipelineFailDTO.page,
                    queryPipelineFailDTO.pageSize
                )
            )
        ).map {
            PipelineFailDetailInfoDO(
                PipelineBuildInfoDO(
                    projectId = it.projectId,
                    pipelineId = it.pipelineId,
                    pipelineName = it.pipelineName,
                    buildId = it.buildid,
                    buildNum = it.buildNum,
                    branch = it.branch
                ),
                startUser = it.startUser,
                startTime = it.startTime,
                endTime = it.endTime,
                errorInfo = ErrorCodeInfoDO(
                    errorType = it.errorType,
                    errorCode = it.errorCode,
                    errorMsg = it.errorMsg
                )
            )
        }
        return Page(
            queryPipelineFailDTO.page,
            queryPipelineFailDTO.pageSize,
            count = queryPipelineFailDetailCount,
            records = result
        )
    }
}