package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件趋势信息")
data class AtomTrendInfoDO(
    @ApiModelProperty("插件代码")
    val atomCode: String,
    @ApiModelProperty("插件名称")
    val atomName: String,
    @ApiModelProperty("趋势信息列表")
    val atomTrendInfos: MutableList<AtomBaseTrendInfoDO>
)
