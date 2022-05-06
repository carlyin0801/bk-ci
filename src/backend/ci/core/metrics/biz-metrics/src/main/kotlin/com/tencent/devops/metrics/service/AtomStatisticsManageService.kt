package com.tencent.devops.metrics.service

import com.tencent.metrics.pojo.`do`.AtomExecutionStatisticsInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomStatisticsInfoDTO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import com.tencent.metrics.pojo.vo.ListPageVO

interface AtomStatisticsManageService {

    /**
     * 查询插件趋势信息
     * @param queryAtomTrendInfoDTO 插件统计信息查询传输对象
     * @return 插件趋势信息视图
     */
    fun queryAtomTrendInfo(
        queryAtomTrendInfoDTO: QueryAtomStatisticsInfoDTO
    ): AtomTrendInfoVO

    /**
     * 查询插件执行统计信息
     * @param queryAtomTrendInfoDTO 插件统计信息查询传输对象
     * @return 插件执行统计信息
     */
    fun queryAtomExecuteStatisticsInfo(
        queryAtomTrendInfoDTO: QueryAtomStatisticsInfoDTO
    ): ListPageVO<AtomExecutionStatisticsInfoDO>
}