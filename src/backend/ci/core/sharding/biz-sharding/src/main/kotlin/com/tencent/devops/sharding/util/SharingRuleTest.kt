package com.tencent.devops.sharding.util

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.OkhttpUtils
import java.io.File
import java.util.*

fun main() {
    val file = File("C:\\Users\\carlyin\\Desktop\\test\\pcg_project_20220118.txt")
    val lines = file.readLines()
    lines.forEach { line ->
        val headers = mapOf(
            "Cookie" to "bk_ticket=XKw17787-U5agVEem2sw6X8Fv-2aoso_uTfuXzj3wDU;",
            "Content-Type" to "application/json",
            "X-DEVOPS-UID" to "carlyin",
            "X-DEVOPS-TOKEN" to "vC9DOvUtqo4X3DORKis1RRX1XNGu8YFw",
            "X-DEVOPS-PROJECT-ID" to "grayproject"
        )
        val paramMap = mapOf(
            "routingName" to line,
            "routingRule" to "ds_2"
        )
        val response = OkhttpUtils.doPost("http://devops.oa.com/ms/project/api/op/sharding/routing/rules/add", JsonUtil.toJson(paramMap), headers)
        val data = response.body()?.string()
        val jsonObject = if (data != null) JsonUtil.toMap(data) else null
        val result = jsonObject?.get("data")?.toString()
        println("${DateTimeUtil.formatDate(Date())} routingName:$line,result:$result")
    }
}
