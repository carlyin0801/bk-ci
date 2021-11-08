/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.sharding.configuration

import com.mysql.cj.jdbc.Driver
import com.zaxxer.hikari.HikariDataSource
import org.apache.shardingsphere.driver.api.ShardingSphereDataSourceFactory
import org.apache.shardingsphere.infra.config.algorithm.ShardingSphereAlgorithmConfiguration
import org.apache.shardingsphere.sharding.api.config.ShardingRuleConfiguration
import org.apache.shardingsphere.sharding.api.config.rule.ShardingTableRuleConfiguration
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.NoneShardingStrategyConfiguration
import org.apache.shardingsphere.sharding.api.config.strategy.sharding.StandardShardingStrategyConfiguration
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.Properties
import javax.sql.DataSource

/**
 *
 * Powered By Tencent
 */
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@AutoConfigureBefore(DataSourceAutoConfiguration::class, JooqAutoConfiguration::class)
@EnableConfigurationProperties(DataSourceProperties::class)
@EnableTransactionManagement
class BkShardingDataSourceConfiguration {

    companion object {
        private const val PROJECT_ID_FIELD = "PROJECT_ID"
        private const val DATA_SOURCE_NAME_PREFIX = "ds_"
    }

    private fun dataSourceMap(config: DataSourceProperties): Map<String, DataSource> {
        val dataSourceMap: MutableMap<String, DataSource> = mutableMapOf()
        val dataSourceConfigs = config.dataSourceConfigs
        dataSourceConfigs.forEachIndexed { index, dataSourceConfig ->
            val dataSourceName = "$DATA_SOURCE_NAME_PREFIX$index"
            dataSourceMap[dataSourceName] = createHikariDataSource(
                datasourcePoolName = dataSourceName,
                datasourceUrl = dataSourceConfig.url,
                datasourceUsername = dataSourceConfig.username,
                datasourcePassword = dataSourceConfig.password,
                datasourceInitSql = dataSourceConfig.initSql,
                datasouceLeakDetectionThreshold = dataSourceConfig.leakDetectionThreshold
            )
        }
        return dataSourceMap
    }

    private fun createHikariDataSource(
        datasourcePoolName: String,
        datasourceUrl: String,
        datasourceUsername: String,
        datasourcePassword: String,
        datasourceInitSql: String?,
        datasouceLeakDetectionThreshold: Long
    ): HikariDataSource {
        return HikariDataSource().apply {
            poolName = datasourcePoolName
            jdbcUrl = datasourceUrl
            username = datasourceUsername
            password = datasourcePassword
            driverClassName = Driver::class.java.name
            minimumIdle = 10
            maximumPoolSize = 50
            idleTimeout = 60000
            connectionInitSql = datasourceInitSql
            leakDetectionThreshold = datasouceLeakDetectionThreshold
        }
    }

    @Bean
    fun shardingDataSource(config: DataSourceProperties): DataSource {
        val shardingRuleConfig = ShardingRuleConfiguration()
        // 设置表的路由规则
        val tableRuleConfigs = shardingRuleConfig.tables
        val dataSourceSize = config.dataSourceConfigs.size
        tableRuleConfigs.add(getTableRuleConfiguration("t_pipeline_info", dataSourceSize))
        tableRuleConfigs.add(getTableRuleConfiguration("t_pipeline_user", dataSourceSize))
        shardingRuleConfig.broadcastTables.add("leaf_alloc")
        val dbShardingAlgorithmrProps = Properties()
        dbShardingAlgorithmrProps.setProperty("strategy", "STANDARD")
        dbShardingAlgorithmrProps.setProperty("algorithmClassName",
            "com.tencent.devops.sharding.configuration.BkDatabaseShardingAlgorithm")
        shardingRuleConfig.shardingAlgorithms["bkDatabaseShardingAlgorithm"] =
            ShardingSphereAlgorithmConfiguration("CLASS_BASED", dbShardingAlgorithmrProps)

        shardingRuleConfig.defaultTableShardingStrategy = NoneShardingStrategyConfiguration()
        shardingRuleConfig.defaultDatabaseShardingStrategy =
            StandardShardingStrategyConfiguration(PROJECT_ID_FIELD, "bkProcessDatabaseShardingAlgorithm")
        val properties = Properties()
        // 是否打印SQL解析和改写日志
        properties.setProperty("sql-show", "true")
        return ShardingSphereDataSourceFactory.createDataSource(
            dataSourceMap(config),
            listOf(shardingRuleConfig),
            properties
        )
    }

    fun getTableRuleConfiguration(
        tableName: String,
        dataSourceSize: Int,
        specifyDataSourceName: String? = null
    ): ShardingTableRuleConfiguration? {
        // 生成实际节点规则
        val actualDataNodes = if (specifyDataSourceName != null) {
            "$specifyDataSourceName.$tableName"
        } else {
            val lastIndex = dataSourceSize - 1
            "$DATA_SOURCE_NAME_PREFIX\${0..$lastIndex}.$tableName"
        }
        val tableRuleConfig = ShardingTableRuleConfiguration(tableName, actualDataNodes)
        tableRuleConfig.tableShardingStrategy = NoneShardingStrategyConfiguration()
        tableRuleConfig.databaseShardingStrategy =
            StandardShardingStrategyConfiguration(PROJECT_ID_FIELD, "bkDatabaseShardingAlgorithm")
        return tableRuleConfig
    }
}
