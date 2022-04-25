package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.CodeCheckInfoDO
import com.tencent.metrics.pojo.`do`.QualityInfoDO
import com.tencent.metrics.pojo.`do`.TurboInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("第三方度量概览")
data class ThirdPartyOverviewInfoVO(
    @ApiModelProperty("CodeCC度量信息")
    val codeCheckInfo: CodeCheckInfoDO,
    @ApiModelProperty("质量红线度量信息")
    val qualityInfo: QualityInfoDO,
    @ApiModelProperty("编译加速度量信息")
    val turboInfo: TurboInfoDO
)
