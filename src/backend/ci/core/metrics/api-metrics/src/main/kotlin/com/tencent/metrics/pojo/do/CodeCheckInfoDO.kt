package com.tencent.metrics.pojo.`do`

import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("CodeCC度量信息")
data class CodeCheckInfoDO (
    @ApiModelProperty("已解决缺陷数")
    val resolvedDefectNum: Int,
    @ApiModelProperty("codecc检查代码库平均分")
    val repoCodeccAvgScore: Double
)