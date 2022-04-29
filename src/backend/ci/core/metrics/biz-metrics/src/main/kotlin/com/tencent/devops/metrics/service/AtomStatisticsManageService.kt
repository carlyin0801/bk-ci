package com.tencent.devops.metrics.service

import com.tencent.metrics.pojo.dto.QueryAtomTrendInfoDTO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO

interface AtomStatisticsManageService {

    /**
     * 查询插件趋势信息
     * @param queryAtomTrendInfoDTO 查询插件趋势信息
     * @return 插件趋势信息视图
     */
    fun queryAtomTrendInfo(
        queryAtomTrendInfoDTO: QueryAtomTrendInfoDTO
    ): AtomTrendInfoVO

    fun queryAtomExecuteStatisticsInfo()
}