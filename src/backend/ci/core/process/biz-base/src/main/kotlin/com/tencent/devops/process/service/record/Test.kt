package com.tencent.devops.process.service.record

fun main() {
    val taskBaseMaps: MutableList<Map<String, Any>> = mutableListOf()
    taskBaseMaps.add(mapOf("key1" to "value1"))
    taskBaseMaps.add(mapOf("key2" to "value2"))
    taskBaseMaps.add(mapOf("key3" to "value3"))
    taskBaseMaps.add(mapOf("key4" to "value2"))
    taskBaseMaps.add(mapOf("key5" to "value5"))
    println(taskBaseMaps)
    taskBaseMaps.add(4, mapOf("key6" to "value6"))
    println(taskBaseMaps)
}