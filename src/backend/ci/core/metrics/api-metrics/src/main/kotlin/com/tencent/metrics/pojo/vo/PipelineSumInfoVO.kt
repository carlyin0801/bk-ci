package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.PipelineSumInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线汇总信息视图")
data class PipelineSumInfoVO(
    @ApiModelProperty("流水线汇总信息")
    val pipelineSumInfoDO: PipelineSumInfoDO,
)
