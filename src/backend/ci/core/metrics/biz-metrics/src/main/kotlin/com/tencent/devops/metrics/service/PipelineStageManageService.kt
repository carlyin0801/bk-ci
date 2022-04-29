package com.tencent.devops.metrics.service

import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO
import com.tencent.metrics.pojo.vo.StageTrendSumInfoVO

interface PipelineStageManageService {

    /**
     * 查询流水线stage趋势信息
     * @param queryPipelineOverviewDTO 查询流水线stage趋势信息
     * @return stage耗时趋势信息列表
     */
    fun queryPipelineStageTrendInfo(
        queryPipelineOverviewDTO: QueryPipelineOverviewDTO
    ): List<StageTrendSumInfoVO>
}