package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("质量红线度量信息")
data class QualityInfoDO(
    @ApiModelProperty("质量红线拦截比例")
    val qualityInterceptionRate: Double
)
