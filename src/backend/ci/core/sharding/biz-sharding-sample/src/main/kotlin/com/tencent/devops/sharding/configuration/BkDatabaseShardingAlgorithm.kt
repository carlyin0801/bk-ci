package com.tencent.devops.sharding.configuration

import org.apache.shardingsphere.api.sharding.standard.PreciseShardingAlgorithm
import org.apache.shardingsphere.api.sharding.standard.PreciseShardingValue

class BkDatabaseShardingAlgorithm : PreciseShardingAlgorithm<String> {

    override fun doSharding(
        availableTargetNames: MutableCollection<String>,
        shardingValue: PreciseShardingValue<String>
    ): String {
        println("-------------doSharding shardingValue:$shardingValue")
        val suffix = if (shardingValue.value.contains("devops")) 0 else 1
        for (targetName in availableTargetNames) {
           if (targetName.endsWith(suffix.toString())) {
               return targetName
           }
        }
        throw IllegalArgumentException("错误的参数")
    }
}
