package com.tencent.metrics.pojo.vo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("stage耗时趋势视图")
data class StageTrendSumInfoVO(
    @ApiModelProperty("stage标签名称")
    val stageTagName: String,
    @ApiModelProperty("流水线stage耗时信息")
    val data: List<PipelineStageCostTimeInfoVO>
)
