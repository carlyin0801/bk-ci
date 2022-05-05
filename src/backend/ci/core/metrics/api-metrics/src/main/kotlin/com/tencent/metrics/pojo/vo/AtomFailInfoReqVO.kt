package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("插件错误查询请求报文")
data class AtomFailInfoReqVO(
    @ApiParam("基本查询条件", required = true)
    val baseQueryReq: BaseQueryReqDO,
    @ApiParam("错误类型", required = false)
    val errorTypes: List<Int>?,
    @ApiParam("错误码", required = false)
    val errorCodes: List<String>?,
)
