package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("编译加速度量信息")
data class TurboInfoDO(
    @ApiModelProperty("编译加速节省时间，单位：秒")
    val turboSaveTime: Long
)
