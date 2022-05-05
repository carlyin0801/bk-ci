package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("错误码信息")
data class ErrorCodeInfoDO(
    @ApiModelProperty("错误类型")
    val errorType: Int,
    @ApiModelProperty("错误的标识码")
    val errorCode: Int,
    @ApiModelProperty("错误描述信息——简体中文")
    val errorMsgZhCn: String,
    @ApiModelProperty("错误描述信息——繁体中文")
    val errorMsgZhTw: String,
    @ApiModelProperty("错误描述信息——英文")
    val errorMsgEn: String
)