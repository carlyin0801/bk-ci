package com.tencent.metrics.pojo.vo

import com.tencent.metrics.pojo.`do`.AtomErrorCodeCountInfoDO
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("插件错误码统计信息视图")
data class AtomErrorCodeSumInfoVO(
    @ApiModelProperty("错误的类型标识")
    val data: List<AtomErrorCodeCountInfoDO>
)
