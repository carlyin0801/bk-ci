package com.tencent.devops.sharding.util

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import java.io.File
import java.util.*
import java.util.concurrent.Callable
import java.util.concurrent.Executors

fun main() {
    val file = File("C:\\Users\\carlyin\\Desktop\\test\\gitci_project.txt")
    val lines = file.readLines()
    lines.forEach { line ->
        val headers = mapOf(
            "Cookie" to "bk_ticket=A_PRwxqbZ79j2tVpqWCTo9F0SfCVNHJ_FFSF7aVjeQE;",
            "Content-Type" to "application/json",
            "X-DEVOPS-UID" to "carlyin",
            "X-DEVOPS-TOKEN" to "vC9DOvUtqo4X3DORKis1RRX1XNGu8YFw",
            "X-DEVOPS-PROJECT-ID" to "grayproject"
        )
        val paramMap = mapOf(
            "routingName" to line,
            "routingRule" to "ds_0"
        )
        val response = OkhttpUtils.doPost("http://devops.oa.com/ms/project/api/op/sharding/routing/rules/add", JsonUtil.toJson(paramMap), headers)
        val data = response.body()?.string()
        val jsonObject = if (data != null) JsonUtil.toMap(data) else null
        val result = jsonObject?.get("data")?.toString()
        println("${DateTimeUtil.formatDate(Date())} routingName:$line,result:$result")
    }
}
