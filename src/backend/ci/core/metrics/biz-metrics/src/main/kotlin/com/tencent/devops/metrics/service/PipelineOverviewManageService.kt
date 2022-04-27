package com.tencent.devops.metrics.service

import com.tencent.metrics.pojo.`do`.PipelineSumInfoDO
import com.tencent.metrics.pojo.`do`.PipelineTrendInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineOverviewDTO

interface PipelineOverviewManageService {

    /**
     * 查询流水线汇总信息
     * @param queryPipelineOverviewDTO 查询流水线概览传输对象
     * @return 流水线汇总信息
     */
    fun queryPipelineSumInfo(
        queryPipelineOverviewDTO: QueryPipelineOverviewDTO
    ): PipelineSumInfoDO

    /**
     * 查询流水线运行趋势数据
     * @param queryPipelineOverviewDTO 查询流水线概览传输对象
     * @return 流水线趋势信息列表
     */
    fun queryPipelineTrendInfo(
        queryPipelineOverviewDTO: QueryPipelineOverviewDTO
    ): List<PipelineTrendInfoDO>
}