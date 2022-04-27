package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线汇总信息")
data class PipelineSumInfoDO(
    @ApiModelProperty("流水线总执行成功率")
    val totalSuccessRate: Double,
    @ApiModelProperty("流水线总平均执行耗时")
    val totalAvgCostTime: Long
)
