package com.tencent.metrics.pojo.qo

import com.tencent.devops.common.api.model.SQLLimit
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询插件汇总信息传输对象")
data class QueryAtomStatisticsQO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("查询条件请求信息")
    val queryReq: BaseQueryReqDO,
    @ApiModelProperty("错误类型")
    val errorTypes: List<Int>?,
    @ApiModelProperty("插件代码")
    val atomCodes: List<String>?,
    @ApiModelProperty("分页信息")
    val limit: SQLLimit? = null
)