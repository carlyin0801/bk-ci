package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.PipelineOverviewDao
import com.tencent.devops.metrics.service.PipelineOverviewManageService
import com.tencent.metrics.constant.BK_FAIL_AVG_COST_TIME
import com.tencent.metrics.constant.BK_FAIL_EXECUTE_COUNT
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.constant.BK_SUCESS_EXECUTE_COUNT_SUM
import com.tencent.metrics.constant.BK_TOTAL_AVG_COST_TIME
import com.tencent.metrics.constant.BK_TOTAL_AVG_COST_TIME_SUM
import com.tencent.metrics.constant.BK_TOTAL_EXECUTE_COUNT
import com.tencent.metrics.constant.BK_TOTAL_EXECUTE_COUNT_SUM
import com.tencent.metrics.pojo.`do`.PipelineSumInfoDO
import com.tencent.metrics.pojo.`do`.PipelineTrendInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.time.LocalDateTime

@Service
class PipelineOverviewServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineOverviewDao: PipelineOverviewDao
): PipelineOverviewManageService {
    override fun queryPipelineSumInfo(queryPipelineOverviewDTO: QueryPipelineOverviewDTO): PipelineSumInfoDO {
        val result = pipelineOverviewDao.queryPipelineSumInfo(
            dslContext,
            QueryPipelineOverviewQO(
                queryPipelineOverviewDTO.projectId,
                queryPipelineOverviewDTO.baseQueryReq
            )
        )
        val totalExecuteCountSum = result?.get(BK_TOTAL_EXECUTE_COUNT_SUM, Long::class.java)?: 0
        val sucessExecuteCountSum = result?.get(BK_SUCESS_EXECUTE_COUNT_SUM, Long::class.java)?: 0
        val totalAvgCostTimeSum = result?.get(BK_TOTAL_AVG_COST_TIME_SUM, Long::class.java)?: 0
        return PipelineSumInfoDO(
            totalSuccessRate =
            String.format("%.2f", sucessExecuteCountSum.toDouble() * 100 / totalExecuteCountSum)
                .toDouble(),
            totalAvgCostTime = totalAvgCostTimeSum / totalExecuteCountSum
        )

    }

    override fun queryPipelineTrendInfo(queryPipelineOverviewDTO: QueryPipelineOverviewDTO): List<PipelineTrendInfoDO> {
        val result = pipelineOverviewDao.queryPipelineTrendInfo(
            dslContext,
            QueryPipelineOverviewQO(
                queryPipelineOverviewDTO.projectId,
                queryPipelineOverviewDTO.baseQueryReq
            )
        )
        val trendInfos = result.map {
            PipelineTrendInfoDO(
                statisticsTime = it.get(BK_STATISTICS_TIME, LocalDateTime::class.java),
                totalExecuteCount = it.get(BK_TOTAL_EXECUTE_COUNT, Int::class.java),
                failedExecuteCount = it.get(BK_FAIL_EXECUTE_COUNT, Int::class.java),
                totalAvgCostTime = it.get(BK_TOTAL_AVG_COST_TIME, Long::class.java),
                failAvgCostTime = it.get(BK_FAIL_AVG_COST_TIME, Long::class.java)
            )
        }
        return trendInfos
    }
}