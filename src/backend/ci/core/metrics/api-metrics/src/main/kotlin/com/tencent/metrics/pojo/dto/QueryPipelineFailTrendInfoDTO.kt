package com.tencent.metrics.pojo.dto

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询流水线失败趋势数据传输对象")
class QueryPipelineFailTrendInfoDTO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("查询条件请求信息", required = true)
    val queryReq: BaseQueryReqDO
)