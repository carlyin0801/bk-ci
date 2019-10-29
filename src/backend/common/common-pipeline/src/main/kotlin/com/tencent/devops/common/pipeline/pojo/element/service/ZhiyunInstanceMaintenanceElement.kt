package com.tencent.devops.common.pipeline.pojo.element.service

import com.tencent.devops.common.pipeline.enums.ZhiyunOperation
import com.tencent.devops.common.pipeline.pojo.element.Element
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("织云-启动,停止,重启,热重启,卸载", description = ZhiyunInstanceMaintenanceElement.classType)
data class ZhiyunInstanceMaintenanceElement(
    @ApiModelProperty("任务名称", required = true)
    override val name: String = "织云-启动,停止,重启,热重启,卸载",
    @ApiModelProperty("id", required = false)
    override var id: String? = null,
    @ApiModelProperty("状态", required = false)
    override var status: String? = null,
    @ApiModelProperty("业务名", required = true)
    val product: String = "",
    @ApiModelProperty("包名", required = true)
    val pkgName: String = "",
    @ApiModelProperty("安装路径，如 /usr/local/services/taylor-1.0", required = true)
    val installPath: String = "",
    @ApiModelProperty("IP数组，不可重复，逗号分隔", required = true)
    val ips: String = "",
    @ApiModelProperty("\"start\"，\"stop\"，\"restart\"，\"reload\"，\"uninstall\"", required = true)
    val operation: ZhiyunOperation,
    @ApiModelProperty("有效值为\"true\"，不关注请传\"\"，传入为字符串\"true\"时，表示热启动", required = false)
    val graceful: Boolean?,
    @ApiModelProperty("分批升级的每批数量", required = false)
    val batchNum: String?,
    @ApiModelProperty("分批升级的间隔时间（秒）", required = false)
    val batchInterval: String?,
    @ApiModelProperty("当前版本号，当操作类型是ROLLBACK时必选", required = false)
    val curVersion: String?
) : Element(name, id, status) {
    companion object {
        const val classType = "zhiyunInstanceMaintenance"
    }

    override fun getTaskAtom(): String {
        return "zhiyunInstanceMaintenanceTaskAtom"
    }

    override fun getClassType() = classType
}
