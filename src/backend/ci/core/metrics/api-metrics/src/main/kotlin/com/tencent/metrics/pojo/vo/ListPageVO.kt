package com.tencent.metrics.pojo.vo

import io.swagger.annotations.ApiModelProperty
import kotlin.math.ceil

data class ListPageVO<out T>(
    @ApiModelProperty("总记录行数", required = true)
    val count: Long,
    @ApiModelProperty("第几页", required = true)
    val page: Int,
    @ApiModelProperty("每页多少条", required = true)
    val pageSize: Int,
    @ApiModelProperty("总共多少页", required = true)
    val totalPages: Int,
    @ApiModelProperty("数据", required = true)
    val records: List<T>
) {
    constructor(page: Int = 1, pageSize: Int = 10, count: Long, records: List<T>) :
            this(count, page, pageSize, ceil(count * 1.0 / pageSize).toInt(), records)
}
