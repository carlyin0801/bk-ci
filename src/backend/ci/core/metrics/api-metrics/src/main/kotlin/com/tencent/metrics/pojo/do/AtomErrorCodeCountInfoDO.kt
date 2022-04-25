package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件错误码统计信息")
data class AtomErrorCodeCountInfoDO(
    @ApiModelProperty("错误的类型标识")
    val errorType: Int,
    @ApiModelProperty("错误的标识码")
    val errorCode: Int,
    @ApiModelProperty("插件信息")
    val errorMsg: String,
    @ApiModelProperty("插件次数")
    val errorCount: Int
)
