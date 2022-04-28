package com.tencent.metrics.pojo.dto

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询流水线错误信息传输对象")
data class QueryPipelineFailDTO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("查询条件请求信息")
    val queryReq: BaseQueryReqDO,
    @ApiModelProperty("错误类型")
    val errorTypes: List<Int>?,
    @ApiModelProperty("页码")
    val page: Int = 1,
    @ApiModelProperty("页数")
    val pageSize: Int = 10
)
