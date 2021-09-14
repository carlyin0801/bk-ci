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

import com.tencent.devops.sharding.util.DataSourceUtil
import org.apache.shardingsphere.api.config.sharding.ShardingRuleConfiguration
import org.apache.shardingsphere.api.config.sharding.TableRuleConfiguration
import org.apache.shardingsphere.api.config.sharding.strategy.NoneShardingStrategyConfiguration
import org.apache.shardingsphere.api.config.sharding.strategy.StandardShardingStrategyConfiguration
import org.apache.shardingsphere.shardingjdbc.api.ShardingDataSourceFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.AutoConfigureBefore
import org.springframework.boot.autoconfigure.AutoConfigureOrder
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jooq.JooqAutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.Ordered
import org.springframework.transaction.annotation.EnableTransactionManagement
import java.util.HashMap
import java.util.Properties
import javax.sql.DataSource

/**
 *
 * Powered By Tencent
 */
@Configuration
@AutoConfigureOrder(Ordered.HIGHEST_PRECEDENCE)
@AutoConfigureBefore(DataSourceAutoConfiguration::class, JooqAutoConfiguration::class)
@EnableTransactionManagement
class BkShardingDataSourceConfiguration {

    @Value("\${spring.datasource.process1.url}")
    val processDatasourceUrl1: String = ""
    @Value("\${spring.datasource.process1.username}")
    val processDatasourceUsername1: String = ""
    @Value("\${spring.datasource.process1.password}")
    val processDatasourcePassword1: String = ""
    @Value("\${spring.datasource.process1.initSql:#{null}}")
    val processDatasourceInitSql1: String? = null
    @Value("\${spring.datasource.process1.leakDetectionThreshold:#{0}}")
    val processDatasourceLeakDetectionThreshold1: Long = 0

    @Value("\${spring.datasource.process2.url}")
    val processDatasourceUrl2: String = ""
    @Value("\${spring.datasource.process2.username}")
    val processDatasourceUsername2: String = ""
    @Value("\${spring.datasource.process2.password}")
    val processDatasourcePassword2: String = ""
    @Value("\${spring.datasource.process2.initSql:#{null}}")
    val processDatasourceInitSql2: String? = null
    @Value("\${spring.datasource.process2.leakDetectionThreshold:#{0}}")
    val processDatasourceLeakDetectionThreshold2: Long = 0

    private fun dataSourceMap(): Map<String, DataSource> {
        val dataSourceMap: MutableMap<String, DataSource> = HashMap(2)
        dataSourceMap["ds_0"] = DataSourceUtil.hikariDataSource(
            datasourcePoolName = "DBPool-Process1",
            datasourceUrl = processDatasourceUrl1,
            datasourceUsername = processDatasourceUsername1,
            datasourcePassword = processDatasourcePassword1,
            datasourceInitSql = processDatasourceInitSql1,
            datasouceLeakDetectionThreshold = processDatasourceLeakDetectionThreshold1
        )
        dataSourceMap["ds_1"] = DataSourceUtil.hikariDataSource(
            datasourcePoolName = "DBPool-Process2",
            datasourceUrl = processDatasourceUrl2,
            datasourceUsername = processDatasourceUsername2,
            datasourcePassword = processDatasourcePassword2,
            datasourceInitSql = processDatasourceInitSql2,
            datasouceLeakDetectionThreshold = processDatasourceLeakDetectionThreshold2
        )
        return dataSourceMap
    }

    @Bean
    @Primary
    fun shardingDataSource(): DataSource {
        println("------------------init dataSource-----------")
        val shardingRuleConfig = ShardingRuleConfiguration()
        shardingRuleConfig.tableRuleConfigs.add(getPipelineInfoConfiguration())
        shardingRuleConfig.defaultTableShardingStrategyConfig = NoneShardingStrategyConfiguration()
        shardingRuleConfig.defaultDatabaseShardingStrategyConfig =
            StandardShardingStrategyConfiguration("PROJECT_ID", BkDatabaseShardingAlgorithm())
        val properties = Properties()
        // 是否打印SQL解析和改写日志
        properties.setProperty("sql.show", "true")
        return ShardingDataSourceFactory.createDataSource(dataSourceMap(), shardingRuleConfig, properties)
    }

    fun getPipelineInfoConfiguration(): TableRuleConfiguration? {
        val tableRuleConfig = TableRuleConfiguration("t_pipeline_info", "ds_\${0..1}.t_pipeline_info")
        return tableRuleConfig
    }
}
