package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.PipelineStageDao
import com.tencent.devops.metrics.service.PipelineStageManageService
import com.tencent.metrics.pojo.`do`.PipelineStageCostTimeInfoDO
import com.tencent.metrics.pojo.`do`.StageDayAvgCostTimeInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.qo.QueryPipelineStageTrendInfoQO
import com.tencent.metrics.pojo.vo.StageTrendSumInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineStageServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineStageDao: PipelineStageDao
): PipelineStageManageService {

    override fun queryPipelineStageTrendInfo(
        queryPipelineOverviewDTO: QueryPipelineOverviewDTO
    ): List<StageTrendSumInfoVO> {

        val stageTrendSumInfos = mutableMapOf<String, List<StageDayAvgCostTimeInfoDO>>()
        val tags = pipelineStageDao.getStageTag(dslContext, queryPipelineOverviewDTO.projectId)
        return tags.map { tag ->
            val result = pipelineStageDao.queryPipelineStageTrendInfo(
                dslContext,
                QueryPipelineStageTrendInfoQO(
                    queryPipelineOverviewDTO.projectId,
                    queryPipelineOverviewDTO.queryReq,
                    tag
                )
            )
            result.map {
                if (!stageTrendSumInfos.containsKey(it.value1())) {
                    val listOf = mutableListOf(StageDayAvgCostTimeInfoDO(it.value2(), it.value3()))
                    stageTrendSumInfos.put(it.value1(), listOf)
                } else {
                    val listOf = stageTrendSumInfos[it.value1()]!!.toMutableList()
                    listOf.add(StageDayAvgCostTimeInfoDO(it.value2(), it.value3()))
                }
            }
            val pipelineStageCostTimeInfoDOs = stageTrendSumInfos.map {
                PipelineStageCostTimeInfoDO(it.key, it.value)
            }
            StageTrendSumInfoVO(tag, pipelineStageCostTimeInfoDOs)
        }
    }
}