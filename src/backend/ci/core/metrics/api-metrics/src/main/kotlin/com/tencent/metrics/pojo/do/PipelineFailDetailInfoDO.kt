package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("流水线失败详情信息")
data class PipelineFailDetailInfoDO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("流水线ID")
    val pipelineId: String,
    @ApiModelProperty("流水线名称")
    val pipelineName: String,
    @ApiModelProperty("构建ID")
    val buildId: String,
    @ApiModelProperty("构建序号")
    val buildNum: Int,
    @ApiModelProperty("构建代码库分支")
    val branch: String,
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
//    @ApiModelProperty("统计时间")
//    val statisticsTime: LocalDateTime,
//    @ApiModelProperty("创建者")
//    val creator: String,
//    @ApiModelProperty("修改者")
//    val modifier: String,
//    @ApiModelProperty("修改时间")
//    val updateTime: LocalDateTime,
//    @ApiModelProperty("创建时间")
//    val createTime: LocalDateTime
)