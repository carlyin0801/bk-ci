package com.tencent.metrics.pojo.qo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询错误码信息查询条件对象")
data class QueryErrorCodeInfoQO(
    @ApiModelProperty("错误类型")
    val errorTypes: List<Int>?,
    @ApiModelProperty("页码")
    val page: Int = 1,
    @ApiModelProperty("页数")
    val pageSize: Int = 10
)
