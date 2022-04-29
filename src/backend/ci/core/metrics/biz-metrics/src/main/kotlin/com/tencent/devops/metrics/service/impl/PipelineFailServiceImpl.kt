package com.tencent.devops.metrics.service.impl

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.metrics.service.PipelineFailManageService
import com.tencent.devops.metrics.dao.PipelineFailDao
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
                queryReq = queryPipelineFailTrendInfoDTO.queryReq
            )
        )
        val failTrendInfos = typeInfos.map { failTrendInfo ->
            val result = pipelineFailDao.queryPipelineFailTrendInfo(
                dslContext,
                QueryPipelineOverviewQO(
                    projectId = queryPipelineFailTrendInfoDTO.projectId,
                    queryReq = queryPipelineFailTrendInfoDTO.queryReq
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
                queryPipelineFailDTO.queryReq,
                queryPipelineFailDTO.errorTypes
            )
        )
        val list = result.map {
            PipelineFailInfoDO(
                errorType = it.value1(),
                name = it.value2(),
                errorCount = it.value3().toInt()
            )
        }
        return list
    }

    override fun queryPipelineFailDetailInfo(queryPipelineFailDTO: QueryPipelineFailDTO): Page<PipelineFailDetailInfoDO> {
        val queryPipelineFailDetailCount = pipelineFailDao.queryPipelineFailDetailCount(
            dslContext,
            QueryPipelineFailQO(
                queryPipelineFailDTO.projectId,
                queryPipelineFailDTO.queryReq,
                queryPipelineFailDTO.errorTypes
            )
        )
        if (queryPipelineFailDetailCount > 10000) {
            throw ErrorCodeException(
                errorCode = MetricsMessageCode.QUERY_DETAILS_COUNT_BEYOND
            )
        }
        val page = if (queryPipelineFailDTO.page <= 0) 1 else queryPipelineFailDTO.page
        var pageSize = if (queryPipelineFailDTO.pageSize <= 0) 10 else queryPipelineFailDTO.pageSize
        when {
            queryPipelineFailDTO.pageSize <= 0 -> {
                10
            }
            queryPipelineFailDTO.pageSize > 100 -> {
                100
            }
            else -> {
                queryPipelineFailDTO.pageSize
            }
        }
        val result = pipelineFailDao.queryPipelineFailDetailInfo(
            dslContext,
            QueryPipelineFailQO(
                queryPipelineFailDTO.projectId,
                queryPipelineFailDTO.queryReq,
                queryPipelineFailDTO.errorTypes,
                page,
                pageSize
            )
        )?.map {
            PipelineFailDetailInfoDO(
                projectId = it.get("PROJECT_ID") as String,
                PipelineBaseInfoDO(
                    pipelineId = it.get("PIPELINE_ID") as String,
                    pipelineName = it.get("PIPELINE_NAME") as String,
                    buildId = it.get("BUILD_ID") as String,
                    buildNum = it.get("BUILD_NUM") as Int
                ),
                branch = it.get("BRANCH") as String,
                startUser = it.get("START_USER") as String,
                startTime = it.get("START_TIME") as LocalDateTime,
                endTime = it.get("END_TIME") as LocalDateTime,
                errorType = it.get("ERROR_TYPE") as Int,
                errorMsg = it.get("ERROR_MSG") as String
            )
        }?: listOf<PipelineFailDetailInfoDO>()
        return Page(
            page = page,
            pageSize = pageSize,
            count = queryPipelineFailDetailCount.toLong(),
            records = result
        )
    }
}