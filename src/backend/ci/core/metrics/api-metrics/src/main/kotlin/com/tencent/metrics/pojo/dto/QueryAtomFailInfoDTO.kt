package com.tencent.metrics.pojo.dto

import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import io.swagger.annotations.ApiParam

@ApiModel("插件失败信息查询传输对象")
class QueryAtomFailInfoDTO (
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("基本查询条件", required = true)
    val baseQueryReq: BaseQueryReqDO,
    @ApiModelProperty("错误类型", required = false)
    val errorTypes: List<Int>?,
    @ApiModelProperty("错误码", required = false)
    val errorCodes: List<Int>?,
    @ApiModelProperty("页码")
    val page: Int? = 1,
    @ApiModelProperty("页数")
    val pageSize: Int? = 10
)