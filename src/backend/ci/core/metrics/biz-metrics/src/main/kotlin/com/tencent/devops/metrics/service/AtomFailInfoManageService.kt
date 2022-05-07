package com.tencent.devops.metrics.service

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.metrics.pojo.`do`.AtomFailDetailInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomFailInfoDTO
import com.tencent.metrics.pojo.vo.AtomErrorCodeStatisticsInfoVO
import com.tencent.metrics.pojo.vo.AtomFailInfoReqVO

interface AtomFailInfoManageService {

    /**
     * 查询插件错误码统计信息
     *@param queryAtomFailInfoDTO 插件失败信息查询传输对象
     * @return 插件错误码统计信息视图
     */
    fun queryAtomErrorCodeStatisticsInfo(
        queryAtomFailInfoDTO: QueryAtomFailInfoDTO
    ): AtomErrorCodeStatisticsInfoVO

    /**
     * 查询流水线插件失败详情数据
     * @param queryAtomFailInfoDTO 插件失败信息查询传输对象
     * @return 插件失败详情信息分页数据
     */
    fun queryPipelineFailDetailInfo(
        queryAtomFailInfoDTO: QueryAtomFailInfoDTO
    ): Page<AtomFailDetailInfoDO>
}