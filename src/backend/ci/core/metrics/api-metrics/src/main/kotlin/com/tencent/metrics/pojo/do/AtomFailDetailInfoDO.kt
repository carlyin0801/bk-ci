package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("插件失败详情信息")
data class AtomFailDetailInfoDO(
    @ApiModelProperty("流水线构建信息")
    val pipelineBaseInfo: PipelineBaseInfoDO,
    @ApiModelProperty("插件代码")
    val atomCode: String,
    @ApiModelProperty("插件名称")
    val atomName: String,
    @ApiModelProperty("插件分类代码")
    val classifyCode: String,
    @ApiModelProperty("启动用户")
    val startUser: String,
    @ApiModelProperty("启动时间")
    val startTime: LocalDateTime,
    @ApiModelProperty("结束时间")
    val endTime: LocalDateTime,
    @ApiModelProperty("错误的类型标识")
    val errorType: Int,
    @ApiModelProperty("错误的标识码")
    val errorCode: Int,
    @ApiModelProperty("错误描述")
    val errorMsg: String,
)