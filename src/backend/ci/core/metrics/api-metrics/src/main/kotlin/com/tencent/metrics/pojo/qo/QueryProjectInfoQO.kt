package com.tencent.metrics.pojo.qo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("获取项目下信息列表信息查询条件对象")
data class QueryProjectInfoQO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("流水线ID")
    val pipelineIds: List<String>?,
    @ApiModelProperty("页码")
    val page: Int,
    @ApiModelProperty("页数")
    val pageSize: Int
)
