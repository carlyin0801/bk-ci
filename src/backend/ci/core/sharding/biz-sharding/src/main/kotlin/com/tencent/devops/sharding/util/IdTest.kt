package com.tencent.devops.sharding.util

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future

fun main() {
    val executor = Executors.newFixedThreadPool(1000)
    val set = mutableSetOf<String?>()
    val futureList = mutableListOf<Future<String?>>()
    for (i in 0 until 10000) {
        futureList.add(
        executor.submit(Callable<String?> {
         val response = OkhttpUtils.doGet("http://127.0.0.1:21950/api/service/alloc/ids/types/segment/tags/PIPELINE_INFO/generate")
            val data = response.body()?.string()
            val jsonObject = if (data != null) JsonUtil.toMap(data) else null
            val id = jsonObject?.get("data")?.toString()
         println("${DateTimeUtil.formatDate(Date())} id:$id")
            return@Callable  id
        })
        )
    }
    futureList.forEach {
        set.add(it.get())
    }
    println("${DateTimeUtil.formatDate(Date())} setSize:${set.size}")
    println("${DateTimeUtil.formatDate(Date())} set:${set}")
}
