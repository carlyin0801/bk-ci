package com.tencent.metrics.pojo.qo

import com.tencent.devops.common.api.model.SQLLimit
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线错误信息查询条件信息对象")
data class QueryPipelineFailQO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("查询条件请求信息")
    val baseQueryReq: BaseQueryReqDO,
    @ApiModelProperty("错误类型")
    val errorTypes: List<Int>?,
    @ApiModelProperty("分页信息")
    val limit: SQLLimit? = null
)
