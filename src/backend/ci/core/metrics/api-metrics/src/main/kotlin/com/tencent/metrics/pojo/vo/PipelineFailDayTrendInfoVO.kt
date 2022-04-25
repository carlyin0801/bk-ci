package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.PipelineFailInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("流水线失败日趋势信息视图")
data class PipelineFailDayTrendInfoVO(
    @ApiModelProperty("统计时间")
    val statisticsTime: LocalDateTime,
    @ApiModelProperty("错误信息集合")
    val failInfos: Map<String, PipelineFailInfoDO>
)
