package com.tencent.metrics.pojo.dto

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询流水线stage趋势信息")
data class QueryPipelineOverviewDTO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("userId")
    val userId: String,
    @ApiModelProperty("查询条件信息")
    val queryReq: BaseQueryReqDO
)
