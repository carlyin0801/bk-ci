package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线标签信息")
class PipelineLabelInfoDO (
    @ApiModelProperty("标签ID")
    val labelId: Long,
    @ApiModelProperty("标签名称")
    val labelName: String
)