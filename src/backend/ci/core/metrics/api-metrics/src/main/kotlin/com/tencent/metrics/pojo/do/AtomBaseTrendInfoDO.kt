package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("插件基本趋势信息")
data class AtomBaseTrendInfoDO(
    @ApiModelProperty("成功率")
    val successRate: Double,
    @ApiModelProperty("统计时间")
    val statisticsTime: LocalDateTime,
    @ApiModelProperty("平均耗时")
    val avgCostTime: Long
)
