package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.PipelineOverviewDao
import com.tencent.devops.metrics.service.PipelineOverviewManageService
import com.tencent.metrics.pojo.`do`.PipelineSumInfoDO
import com.tencent.metrics.pojo.`do`.PipelineTrendInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
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
                queryPipelineOverviewDTO.queryReq
            )
        )
        val totalExecuteCountSum = result?.get("totalExecuteCountSum", Long::class.java)?: 0
        val sucessExecuteCountSum = result?.get("sucessExecuteCountSum", Long::class.java)?: 0
        val totalAvgCostTimeSum = result?.get("totalAvgCostTimeSum", Long::class.java)?: 0
        return PipelineSumInfoDO(
            totalSuccessRate = sucessExecuteCountSum.toDouble() / totalExecuteCountSum,
            totalAvgCostTime = totalAvgCostTimeSum / totalExecuteCountSum
        )

    }

    override fun queryPipelineTrendInfo(queryPipelineOverviewDTO: QueryPipelineOverviewDTO): List<PipelineTrendInfoDO> {
        val result = pipelineOverviewDao.queryPipelineTrendInfo(
            dslContext,
            QueryPipelineOverviewQO(
                queryPipelineOverviewDTO.projectId,
                queryPipelineOverviewDTO.queryReq
            )
        )
        val trendInfos = mutableListOf<PipelineTrendInfoDO>()
        result.forEach {
            trendInfos.add(
                PipelineTrendInfoDO(
                    statisticsTime = it.get("STATISTICS_TIME", LocalDateTime::class.java),
                    totalExecuteCount = it.get("TOTAL_EXECUTE_COUNT", Long::class.java),
                    failedExecuteCount = it.get("FAIL_EXECUTE_COUNT", Long::class.java),
                    avgCostTime = it.get("TOTAL_AVG_COST_TIME", Long::class.java),
                    avgFailCostTime = it.get("FAIL_AVG_COST_TIME", Long::class.java)
                )
            )
        }
        return trendInfos
    }
}