package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.PipelineFailInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("流水线错误统计信息视图")
data class PipelineFailSumInfoVO(
    @ApiModelProperty("流水线错误统计信息")
    val pipelineFailInfoList: List<PipelineFailInfoDO>
)
