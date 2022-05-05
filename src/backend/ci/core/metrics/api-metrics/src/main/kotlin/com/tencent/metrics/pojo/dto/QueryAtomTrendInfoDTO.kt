package com.tencent.metrics.pojo.dto

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询插件趋势信息传输对象")
data class QueryAtomTrendInfoDTO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("查询条件请求信息")
    val baseQueryReq: BaseQueryReqDO,
    @ApiModelProperty("错误类型")
    val errorTypes: List<Int>?,
    @ApiModelProperty("插件代码")
    val atomCodes: List<String>?
)