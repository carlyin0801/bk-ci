package com.tencent.metrics.pojo.dto

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询第三方汇总信息传输对象")
data class QueryPipelineSummaryInfoDTO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("userId")
    val userId: String,
    @ApiModelProperty("开始时间", required = true)
    val startTime: String,
    @ApiModelProperty("结束时间", required = true)
    val endTime: String
)
