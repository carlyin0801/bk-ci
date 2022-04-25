package com.tencent.metrics.pojo.vo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("插件错误查询请求报文")
data class AtomFailInfoReqVO(
    @ApiParam("流水线ID", required = false)
    val pipelineIds: List<String>?,
    @ApiParam("流水线标签", required = false)
    val pipelineLabelIds: List<String>?,
    @ApiParam("错误类型", required = false)
    val errorTypes: List<String>?,
    @ApiParam("错误码", required = false)
    val errorCodes: List<String>?,
    @ApiParam("开始时间", required = true)
    val startTime: String,
    @ApiParam("结束时间", required = true)
    val endTime: String
)
