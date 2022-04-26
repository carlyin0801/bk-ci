package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("流水线趋势信息")
data class PipelineTrendInfoDO(
    @ApiModelProperty("统计时间")
    val statisticsTime: LocalDateTime,
    @ApiModelProperty("流水线总执行次数")
    val totalExecuteCount: Long,
    @ApiModelProperty("流水线执行失败数")
    val failedExecuteCount: Long,
    @ApiModelProperty("平均执行耗时")
    val avgCostTime: Long,
    @ApiModelProperty("平均失败执行耗时")
    val avgFailCostTime: Long
)
