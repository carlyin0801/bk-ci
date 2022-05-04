package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("基本查询条件请求报文")
data class BaseQueryReqDO(
    @ApiParam("流水线ID", required = false)
    val pipelineIds: List<String>?,
    @ApiParam("流水线标签", required = false)
    val pipelineLabelIds: List<Long>?,
    @ApiParam("开始时间", required = true)
    val startTime: String,
    @ApiParam("结束时间", required = true)
    val endTime: String
)
