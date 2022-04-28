package com.tencent.devops.metrics.service

import com.tencent.metrics.pojo.dto.QueryPipelineSummaryInfoDTO
import com.tencent.metrics.pojo.vo.ThirdPartyOverviewInfoVO

interface ThirdPartyManageService {

    /**
     * 查询第三方汇总信息
     * @param queryPipelineSummaryInfoDTO 查询第三方汇总信息传输对象
     * @return 第三方度量信息概览
     */
    fun queryPipelineSummaryInfo(
        queryPipelineSummaryInfoDTO: QueryPipelineSummaryInfoDTO
    ): ThirdPartyOverviewInfoVO
}