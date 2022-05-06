package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.PipelineStageDao
import com.tencent.devops.metrics.service.PipelineStageManageService
import com.tencent.metrics.constant.BK_AVG_COST_TIME
import com.tencent.metrics.constant.BK_PIPELINE_ID
import com.tencent.metrics.constant.BK_PIPELINE_NAME
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.pojo.`do`.PipelineStageCostTimeInfoDO
import com.tencent.metrics.pojo.`do`.StageAvgCostTimeInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.qo.QueryPipelineStageTrendInfoQO
import com.tencent.metrics.pojo.vo.StageTrendSumInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PipelineStageServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineStageDao: PipelineStageDao
): PipelineStageManageService {

    override fun queryPipelineStageTrendInfo(
        queryPipelineOverviewDTO: QueryPipelineOverviewDTO
    ): List<StageTrendSumInfoVO> {

        val stageTrendSumInfos = mutableMapOf<String, List<StageAvgCostTimeInfoDO>>()
        val tags = pipelineStageDao.getStageTag(dslContext, queryPipelineOverviewDTO.projectId)
        return tags.map { tag ->
            val result = pipelineStageDao.queryPipelineStageTrendInfo(
                dslContext,
                QueryPipelineStageTrendInfoQO(
                    queryPipelineOverviewDTO.projectId,
                    queryPipelineOverviewDTO.baseQueryReq,
                    tag
                )
            )
            result.map {
                if (!stageTrendSumInfos.containsKey(it[BK_PIPELINE_ID] as String)) {
                    val listOf = mutableListOf(
                        StageAvgCostTimeInfoDO(
                            it[BK_STATISTICS_TIME] as LocalDateTime,
                            it[BK_AVG_COST_TIME] as Long
                        )
                    )
                    stageTrendSumInfos.put(it[BK_PIPELINE_ID] as String, listOf)
                } else {
                    val listOf = stageTrendSumInfos[it[BK_PIPELINE_ID] as String]!!.toMutableList()
                    listOf.add(
                        StageAvgCostTimeInfoDO(
                            it[BK_STATISTICS_TIME] as LocalDateTime,
                            it[BK_AVG_COST_TIME] as Long
                        )
                    )
                }
            }
            val pipelineStageCostTimeInfoDOs = stageTrendSumInfos.map {
                PipelineStageCostTimeInfoDO(it.key, it.value)
            }
            StageTrendSumInfoVO(tag, pipelineStageCostTimeInfoDOs)
        }
    }
}