package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件执行统计信息")
data class AtomExecutionStatisticsInfoDO(
    @ApiModelProperty("项目ID")
    val projectId: String,
    @ApiModelProperty("插件基本信息")
    val atomBaseInfo: AtomBaseInfoDO,
    @ApiModelProperty("插件分类代码")
    val classifyCode: String,
    @ApiModelProperty("平均耗时")
    val avgCostTime: Long,
    @ApiModelProperty("总执行次数")
    val totalExecuteCount: Int,
    @ApiModelProperty("成功执行次数")
    val successExecuteCount: Int,
    @ApiModelProperty("成功率")
    val successRate: Double,
    @ApiModelProperty("插件错误信息")
    val atomFailInfos: List<AtomFailInfoDO>
)