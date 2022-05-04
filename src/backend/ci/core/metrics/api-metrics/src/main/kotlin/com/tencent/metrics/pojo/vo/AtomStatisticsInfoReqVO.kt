package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiParam

@ApiModel("插件统计信息查询请求报文")
data class AtomStatisticsInfoReqVO(
    @ApiParam("基本查询条件", required = true)
    val baseQueryReqDO: BaseQueryReqDO,
    @ApiParam("错误类型", required = false)
    val errorTypes: List<Int>?,
    @ApiParam("插件代码", required = false)
    val atomCodes: List<String>?
)
