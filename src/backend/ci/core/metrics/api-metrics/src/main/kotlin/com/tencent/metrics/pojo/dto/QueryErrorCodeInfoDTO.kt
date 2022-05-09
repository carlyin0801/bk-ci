package com.tencent.metrics.pojo.dto

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询错误码信息传输对象")
data class QueryErrorCodeInfoDTO(
    @ApiModelProperty("错误类型")
    val errorTypes: List<Int>?,
    @ApiModelProperty("页码")
    val page: Int = 1,
    @ApiModelProperty("页数")
    val pageSize: Int = 10
)
