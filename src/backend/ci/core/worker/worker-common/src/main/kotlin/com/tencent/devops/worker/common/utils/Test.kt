package com.tencent.devops.worker.common.utils

import com.tencent.devops.common.api.util.JsonUtil

fun main() {
    val variables = mapOf("a" to "1")
    val inputParams = mapOf("b" to "2")
    var inputVariables = variables.plus(inputParams).toMutableMap<String, Any>()
    val atomSensitiveConfWriteSwitch = System.getProperty("BK_CI_ATOM_PRIVATE_CONFIG_WRITE_SWITCH")?.toBoolean()
    println(atomSensitiveConfWriteSwitch != false)
    if (atomSensitiveConfWriteSwitch != false) {
        // 开关关闭则不再写入插件私有配置到input.json中
        inputVariables.putAll(mapOf("c" to "6"))
    }
    println("inputVariables:${inputVariables}")
    System.setProperty("sysVar", "123456")
    val pkgName = "node-v10.24.1-sunos-x64.tar.gz"
    val pkgFileFolderName = pkgName.removeSuffix(".tar.gz")
    println(pkgFileFolderName +"======"+pkgName)
    val preCmd = "[ \"echo 123\", \"echo hello\", \"echo world\" ]"
    val preCmds = mutableListOf<String>()
    if (preCmd.contains(Regex("^\\s*\\[[\\w\\s\\S\\W]*\\]\\s*$"))) {
        preCmds.addAll(JsonUtil.to(preCmd))
    } else {
        preCmds.add(preCmd)
    }
    val preCommand =  preCmds.joinToString(
        separator = "\n"
    ) { "\n$it" }
    println(preCommand)
    println("sysVar:${System.getProperty("sysVar")}")
    val dataSourceBuildNumInfoMap = mutableMapOf<String, Long>(
        "a" to 8,
        "b" to 5,
        "c" to 10
    )
    val belowAvgBuildNumDataSourceMap = dataSourceBuildNumInfoMap.filter { it.value < 9 }
    println(belowAvgBuildNumDataSourceMap)
    val belowAvgBuildNumDataSourceNames = dataSourceBuildNumInfoMap.entries.minByOrNull { it.value }!!.key
    println(belowAvgBuildNumDataSourceNames)
}
