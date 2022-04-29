package com.tencent.metrics.pojo.`do`

import com.tencent.metrics.pojo.`do`.StageDayAvgCostTimeInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线stage耗时信息")
data class PipelineStageCostTimeInfoDO(
    @ApiModelProperty("流水线名称")
    val pipelineName: String,
    @ApiModelProperty("stage耗时信息")
    val stageCostTimeSumInfos: List<StageDayAvgCostTimeInfoDO>
)
