package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("流水线趋势信息")
data class PipelineTrendInfoDO(
    @ApiModelProperty("统计时间")
    val statisticsTime: LocalDateTime,
    @ApiModelProperty("流水线总执行次数")
    val totalExecuteCount: Int,
    @ApiModelProperty("流水线执行失败数")
    val failedExecuteCount: Int,
    @ApiModelProperty("总平均耗时，单位：毫秒")
    val totalAvgCostTime: Long,
    @ApiModelProperty("失败平均耗时，单位：毫秒")
    val failAvgCostTime: Long
)
