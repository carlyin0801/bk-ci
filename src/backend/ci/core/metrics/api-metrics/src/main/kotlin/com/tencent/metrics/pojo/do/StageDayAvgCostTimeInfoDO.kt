package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("stage每日平均耗时信息")
data class StageDayAvgCostTimeInfoDO(
    @ApiModelProperty("统计时间")
    val statisticsTime: LocalDateTime,
    @ApiModelProperty("平均耗时")
    val avgCostTime: Long
)
