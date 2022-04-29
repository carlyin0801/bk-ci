package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("流水线失败详情信息")
data class PipelineFailDetailInfoDO(
    @ApiModelProperty("流水线基本信息")
    val pipelineBaseInfo: PipelineBuildInfoDO,
    @ApiModelProperty("构建代码库分支")
    val branch: String,
    @ApiModelProperty("启动用户")
    val startUser: String,
    @ApiModelProperty("启动时间")
    val startTime: LocalDateTime,
    @ApiModelProperty("结束时间")
    val endTime: LocalDateTime,
    @ApiModelProperty("错误类型标识")
    val errorType: Int,
    @ApiModelProperty("错误信息")
    val errorMsg: String
)