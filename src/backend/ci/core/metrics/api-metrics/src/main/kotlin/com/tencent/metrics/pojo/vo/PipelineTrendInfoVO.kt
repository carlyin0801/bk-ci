package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.PipelineTrendInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty


@ApiModel("流水线趋势信息视图")
data class PipelineTrendInfoVO(
    @ApiModelProperty("流水线趋势信息")
    val PipelineTrendInfo: List<PipelineTrendInfoDO>
)

