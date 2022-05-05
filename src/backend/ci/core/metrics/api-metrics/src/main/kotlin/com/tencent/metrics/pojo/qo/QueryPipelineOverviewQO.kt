package com.tencent.metrics.pojo.qo

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询流水线概览数据查询条件信息对象")
data class QueryPipelineOverviewQO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("查询条件信息")
    val baseQueryReq: BaseQueryReqDO
)
