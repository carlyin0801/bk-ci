package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.PipelineFailSumInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线stage耗时信息视图")
data class PipelineStageCostTimeInfoVO(
    @ApiModelProperty("流水线名称")
    val pipelineName: String,
    @ApiModelProperty("stage耗时信息")
    val stageCostTimeSumInfos: List<PipelineFailSumInfoDO>
)
