package com.tencent.devops.common.api.util

import com.tencent.devops.common.api.util.script.CommandLineUtils
import java.io.File
import kotlin.math.roundToLong

fun main() {
    val atomArchivePath = "D:\\project\\bkdevops\\ext\\bkstream"
    File(atomArchivePath).walk().filter { it.path != atomArchivePath }.forEach {
       println(it.absolutePath)
    }
    val pkgFileDir = File("F:\\test123")
    println(pkgFileDir.exists())
    if (pkgFileDir.exists()) {
        pkgFileDir.deleteRecursively()
    }
    println(pkgFileDir.exists())
    val page = 2
    val pageSize = 10
    val pageOffsetNum = 0
    val offset = page?.let { (it - 1) * (pageSize ?: 10) + (pageOffsetNum ?: 0) }
    val limit = if (pageSize == -1) null else pageSize
    println("offset:$offset,limit:$limit")
    var labelIdList = arrayListOf(null, "dsfsdf")
    val data = labelIdList?.filter { !it.isNullOrBlank() }
    println("labelIdList:$data")
    val str = ""
    val dataList = mutableListOf<String>()
    if (str.isNotBlank()) {
        dataList.add(str)
    }
    dataList.add("chmod +x app")
    println(dataList)
    val activeProfiles = arrayOf("prod", "prod-satream")
    println(isStream(activeProfiles))
    val queryParamSb = StringBuilder()
    queryParamSb.append("atomStatus=123&")
    queryParamSb.append("osName=456&")
    println(queryParamSb.removeSuffix("&"))
    println(System.getProperty("os.name")+"----------"+System.getProperty("os.arch"))
    val a = 8.toDouble()
    val b = 3.toLong()
    val result = a.div(b).roundToLong()
    println(result)
    val file = File("F:/data123/nodeJsEnvAtom-1.0.0.tar.gz")
    val headers = mapOf(
        "X-BKREPO-UID" to "carlyin",
        "X-DEVOPS-PROJECT-ID" to "carltest123",
        "Content-Type" to "application/octet-stream",
        "Authorization" to "Basic Z19ia3N0b3JlOmJrc3RvcmUyMDIx"
    )
  OkhttpUtils.downloadFile("http://dev.bkrepo.woa.com/generic/ext/bkstore/atom/bk-store-dev/bk-plugin/nodeJsEnvAtom/1.0.0/nodeJsEnvAtom-1.0.0.tar.gz", file, headers)
    CommandLineUtils.execute("tar -xzvf nodeJsEnvAtom-1.0.0.tar.gz", File("F:/data123"), true)
    println(getFileType("http://radosgw.open.oa.com/paas_backend/ieod/dev/logo/default_rdengtest4.png"))
    println(getFileType("http://radosgw.open.oa.com/paas_backend/ieod/dev/file/jpg/random_15543674917031572088165271600145.jpg?v=1554367491"))
}

private fun getFileType(logoUrl: String): String {
    val url = logoUrl.substring(0, logoUrl.lastIndexOf("?"))
    println(url)
    val index = url.lastIndexOf(".")
    return url.substring(index + 1).toLowerCase()
}

private fun isStream(activeProfiles: Array<String>): Boolean {
    activeProfiles.forEach { activeProfile ->
        if (activeProfile.contains("prod")) {
            return true
        }
    }
    return false
}
