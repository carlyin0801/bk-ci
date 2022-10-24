package com.tencent.devops.metrics.dao

import java.math.BigDecimal
import kotlin.math.roundToLong

fun main() {
    val currentSuccessExecuteCount = 2
    val currentTotalExecuteCount = 3
    println(currentSuccessExecuteCount.toDouble().div(currentTotalExecuteCount).roundToLong())
    val currentSuccessRate = currentSuccessExecuteCount.toBigDecimal()
        .divide(currentTotalExecuteCount.toBigDecimal(), 4, BigDecimal.ROUND_HALF_UP)
    val formatSuccessRate = String.format("%.2f", currentSuccessRate.multiply(100.toBigDecimal()))
        .toBigDecimal()
    println(formatSuccessRate)
}
