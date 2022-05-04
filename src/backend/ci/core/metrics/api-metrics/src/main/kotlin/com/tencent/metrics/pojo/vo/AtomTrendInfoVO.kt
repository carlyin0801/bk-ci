package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.AtomTrendInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件趋势信息视图")
data class AtomTrendInfoVO(
    @ApiModelProperty("趋势信息列表")
    val atomTrendInfos: Map<String, AtomTrendInfoDO>
)
