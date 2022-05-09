package com.tencent.metrics.pojo.dto

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("获取项目下流水线标签信息传输对象")
data class QueryProjectAtomListDTO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("页码")
    val page: Int,
    @ApiModelProperty("页数")
    val pageSize: Int
)
