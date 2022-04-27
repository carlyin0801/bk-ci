package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("基本查询条件请求报文")
data class BaseQueryReqVO(
    @ApiParam("查询条件请求信息", required = true)
    val queryReq: BaseQueryReqDO
)
