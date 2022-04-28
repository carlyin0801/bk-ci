package com.tencent.metrics.pojo.qo

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询流水线概览数据查询条件信息对象")
data class QueryPipelineFailTrendQO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("基本查询条件信息")
    val queryReq: BaseQueryReqDO,
    @ApiModelProperty("错误类型")
    val errorType: Int
)
