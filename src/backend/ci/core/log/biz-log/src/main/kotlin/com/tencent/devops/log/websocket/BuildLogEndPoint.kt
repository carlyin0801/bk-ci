package com.tencent.devops.log.websocket

import java.util.concurrent.ConcurrentHashMap

object BuildLogEndPoint {
    private val lineNums = ConcurrentHashMap<String/*buildId*/, MutableMap<String/*tag/tag*/, Long/*last line num*/>>()

    fun getLastAddedLogNo(buildId: String, jobIdOrTag: String): Long? {
        val buildMap = lineNums[buildId]
        return if (buildMap == null) null
        else buildMap[jobIdOrTag]
    }

    fun refreshBuildEndLineNo(buildId: String, jobIdOrTag: String, lineNum: Long) {
        val buildMap = lineNums[buildId]
        if (buildMap == null) lineNums[buildId] = mutableMapOf(jobIdOrTag to lineNum)
        else buildMap[jobIdOrTag] = lineNum
    }

    fun removeBuildEndPoint(buildId: String) {
        lineNums.remove(buildId)
    }

    fun removeBuildElementEndPoint(buildId: String, jobIdOrTag: String) {
        val buildMap = lineNums[buildId]
        if (buildMap != null) lineNums.remove(jobIdOrTag)
    }
}