package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线基本信息")
class PipelineBaseInfoDO (
    @ApiModelProperty("流水线ID")
    val pipelineId: String,
    @ApiModelProperty("流水线名称")
    val pipelineName: String
)