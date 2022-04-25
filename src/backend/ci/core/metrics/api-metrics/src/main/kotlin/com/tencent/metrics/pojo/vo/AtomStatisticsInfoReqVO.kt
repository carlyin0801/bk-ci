package com.tencent.metrics.pojo.vo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("插件统计信息查询请求报文")
data class AtomStatisticsInfoReqVO(
    @ApiParam("流水线ID", required = false)
    val pipelineIds: List<String>?,
    @ApiParam("流水线标签", required = false)
    val pipelineLabelIds: List<String>?,
    @ApiParam("错误类型", required = false)
    val errorTypes: List<String>?,
    @ApiParam("插件代码", required = false)
    val atomCodes: List<String>?,
    @ApiParam("开始时间", required = true)
    val startTime: String,
    @ApiParam("结束时间", required = true)
    val endTime: String
)
