package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件错误码统计信息")
data class AtomErrorCodeStatisticsInfoDO(
    @ApiModelProperty("错误码信息")
    val errorCodeInfo: ErrorCodeInfoDO,
    @ApiModelProperty("错误次数")
    val errorCount: Int
)
