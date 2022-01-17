package com.tencent.devops.sharding.util

import java.io.File
import java.lang.StringBuilder

fun main() {
    val file = File("C:\\Users\\carlyin\\Desktop\\test\\pcg_project_notify.txt")
    val lines = file.readLines()
    val sb = StringBuilder("(")
    lines.forEach { line ->
        sb.append("'$line',")
    }
    sb.append(")")
    println(sb.toString())
}
