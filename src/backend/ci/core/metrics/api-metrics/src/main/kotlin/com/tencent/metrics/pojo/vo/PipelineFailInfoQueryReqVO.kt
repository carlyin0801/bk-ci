package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("流水线失败信息查询请求报文")
data class PipelineFailInfoQueryReqVO(
    @ApiParam("查询条件请求信息", required = true)
    val baseQueryReq: BaseQueryReqDO,
    @ApiParam("错误类型", required = false)
    val errorTypes: List<Int>?
)
