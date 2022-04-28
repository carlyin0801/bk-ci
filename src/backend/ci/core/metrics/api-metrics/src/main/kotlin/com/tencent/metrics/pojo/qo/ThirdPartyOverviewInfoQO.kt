package com.tencent.metrics.pojo.qo

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("查询第三方汇总信息查询条件信息对象")
data class ThirdPartyOverviewInfoQO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("开始时间", required = true)
    val startTime: String,
    @ApiModelProperty("结束时间", required = true)
    val endTime: String
)
