package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.PipelineFailStatisticsInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线失败趋势信息视图")
data class PipelineFailTrendInfoVO(
    @ApiModelProperty("错误类型名称")
    val name: String,
    @ApiModelProperty("错误类型标识")
    val errorType: Int,
    @ApiModelProperty("错误信息集合")
    val failInfos: List<PipelineFailStatisticsInfoDO>
)
