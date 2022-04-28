package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线错误信息")
data class PipelineFailInfoDO(
    @ApiModelProperty("错误类型名称")
    val name: String,
    @ApiModelProperty("错误类型标识")
    val errorType: Int,
    @ApiModelProperty("错误次数")
    val errorCount: Int
)
