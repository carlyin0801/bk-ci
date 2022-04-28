package com.tencent.devops.metrics.service

import com.tencent.devops.common.api.pojo.Page
import com.tencent.metrics.pojo.`do`.PipelineFailDetailInfoDO
import com.tencent.metrics.pojo.`do`.PipelineFailInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineFailDTO
import com.tencent.metrics.pojo.dto.QueryPipelineFailTrendInfoDTO
import com.tencent.metrics.pojo.vo.PipelineFailTrendInfoVO
import com.tencent.metrics.pojo.vo.PipelineFailSumInfoVO

interface PipelineFailManageService {

    /**
     * 查询流水线失败趋势数据
     * @param queryPipelineOverviewDTO 查询流水线失败趋势数据传输对象
     * @return 流水线失败趋势数据列表
     */
    fun queryPipelineFailTrendInfo(
        queryPipelineOverviewDTO: QueryPipelineFailTrendInfoDTO
    ): List<PipelineFailTrendInfoVO>

    /**
     * 查询流水线错误类型统计数据
     * @param queryPipelineFailDTO 查询流水线错误信息传输对象
     * @return 流水线错误类型统计数据集合
     */
    fun queryPipelineFailSumInfo(
        queryPipelineFailDTO: QueryPipelineFailDTO
    ): List<PipelineFailInfoDO>

    /**
     * 查询流水线失败详情数据
     * @param queryPipelineFailDTO 查询流水线错误信息传输对象
     * @return 流水线失败详情数据
     */
    fun queryPipelineFailDetailInfo(
        queryPipelineFailDTO: QueryPipelineFailDTO
    ): Page<PipelineFailDetailInfoDO>
}