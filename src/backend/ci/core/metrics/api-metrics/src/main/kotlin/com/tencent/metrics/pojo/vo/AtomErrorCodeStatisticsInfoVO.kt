package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.AtomErrorCodeStatisticsInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件错误码统计信息视图")
data class AtomErrorCodeStatisticsInfoVO(
    @ApiModelProperty("插件错误码统计信息列表")
    val statisticsInfos: List<AtomErrorCodeStatisticsInfoDO>
)
