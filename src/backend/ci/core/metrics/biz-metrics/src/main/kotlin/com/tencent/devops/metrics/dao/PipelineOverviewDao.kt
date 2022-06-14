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

package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.service.utils.JooqUtils.sum
import com.tencent.devops.metrics.constant.Constants.BK_FAIL_AVG_COST_TIME
import com.tencent.devops.metrics.constant.Constants.BK_FAIL_EXECUTE_COUNT
import com.tencent.devops.metrics.constant.Constants.BK_STATISTICS_TIME
import com.tencent.devops.metrics.constant.Constants.BK_SUCCESS_EXECUTE_COUNT_SUM
import com.tencent.devops.metrics.constant.Constants.BK_TOTAL_AVG_COST_TIME
import com.tencent.devops.metrics.constant.Constants.BK_TOTAL_AVG_COST_TIME_SUM
import com.tencent.devops.metrics.constant.Constants.BK_TOTAL_EXECUTE_COUNT
import com.tencent.devops.metrics.constant.Constants.BK_TOTAL_EXECUTE_COUNT_SUM
import com.tencent.devops.metrics.constant.Constants.DEFAULT_LIMIT_NUM
import com.tencent.devops.model.metrics.tables.TPipelineOverviewData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.devops.metrics.pojo.qo.QueryPipelineOverviewQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Record3
import org.jooq.Record5
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class PipelineOverviewDao {

    fun getPipelineIdByTotalExecuteCount(
        dslContext: DSLContext,
        queryPipelineOverview: QueryPipelineOverviewQO
    ): List<String> {
        with(TPipelineOverviewData.T_PIPELINE_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val step = dslContext.select(PIPELINE_ID).from(this)
            val conditions = getConditions(queryPipelineOverview, tProjectPipelineLabelInfo, null)
            val pipelineLabelIds = queryPipelineOverview.baseQueryReq.pipelineLabelIds
            val conditionStep =
                if (!pipelineLabelIds.isNullOrEmpty()) {
                    step.join(tProjectPipelineLabelInfo)
                        .on(PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                        .where(conditions)
                } else {
                    step.where(conditions)
                }
            return conditionStep.orderBy(TOTAL_EXECUTE_COUNT.desc()).limit(DEFAULT_LIMIT_NUM).fetchInto(String::class.java)
        }
    }

    fun queryPipelineSumInfo(
        dslContext: DSLContext,
        queryPipelineOverview: QueryPipelineOverviewQO
    ): Record3<BigDecimal, BigDecimal, BigDecimal>? {
        with(TPipelineOverviewData.T_PIPELINE_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            var pipelineIds = queryPipelineOverview.baseQueryReq.pipelineIds
            if (pipelineIds.isNullOrEmpty()) {
                pipelineIds =
                    getPipelineIdByTotalExecuteCount(dslContext, queryPipelineOverview)
            }
            val conditions = getConditions(queryPipelineOverview, tProjectPipelineLabelInfo, pipelineIds)
            val step = dslContext.select(
                sum<Long>(TOTAL_EXECUTE_COUNT).`as`(BK_TOTAL_EXECUTE_COUNT_SUM),
                sum<Long>(SUCCESS_EXECUTE_COUNT).`as`(BK_SUCCESS_EXECUTE_COUNT_SUM),
                sum<Long>(TOTAL_AVG_COST_TIME).`as`(BK_TOTAL_AVG_COST_TIME_SUM)
            ).from(this)
            val conditionStep =
                if (!queryPipelineOverview.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                 step.join(tProjectPipelineLabelInfo)
                     .on(PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                     .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.fetchOne()
        }
    }

    fun queryPipelineTrendInfo(
        dslContext: DSLContext,
        queryPipelineOverview: QueryPipelineOverviewQO
    ): Result<Record5<LocalDateTime, BigDecimal, BigDecimal, BigDecimal, BigDecimal>>? {
        with(TPipelineOverviewData.T_PIPELINE_OVERVIEW_DATA) {
            var pipelineIds = queryPipelineOverview.baseQueryReq.pipelineIds
            if (pipelineIds.isNullOrEmpty()) {
                pipelineIds = getPipelineIdByTotalExecuteCount(dslContext, queryPipelineOverview)
            }
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineOverview, tProjectPipelineLabelInfo, pipelineIds)
            val step = dslContext.select(
                STATISTICS_TIME.`as`(BK_STATISTICS_TIME),
                sum<Long>(TOTAL_EXECUTE_COUNT) .`as`(BK_TOTAL_EXECUTE_COUNT),
                sum<Long>(FAIL_EXECUTE_COUNT).`as`(BK_FAIL_EXECUTE_COUNT),
                sum<Long>(TOTAL_AVG_COST_TIME).`as`(BK_TOTAL_AVG_COST_TIME),
                sum<Long>(FAIL_AVG_COST_TIME).`as`(BK_FAIL_AVG_COST_TIME)
            ).from(this)
            val conditionStep =
                if (!queryPipelineOverview.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(this.STATISTICS_TIME).fetch()
        }
    }

    private fun TPipelineOverviewData.getConditions(
        queryCondition: QueryPipelineOverviewQO,
        tProjectPipelineLabelInfo:  TProjectPipelineLabelInfo,
        pipelineIds: List<String>?
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        val pipelineLabelIds = queryCondition.baseQueryReq.pipelineLabelIds
        conditions.add(this.PROJECT_ID.eq(queryCondition.projectId))
        if (!pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(pipelineIds))
        }
        if (!pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(pipelineLabelIds))
        }
        val startTimeDateTime =
            DateTimeUtil.stringToLocalDate(queryCondition.baseQueryReq.startTime!!)!!.atStartOfDay()
        val endTimeDateTime =
            DateTimeUtil.stringToLocalDate(queryCondition.baseQueryReq.endTime!!)!!.atStartOfDay()
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }
}
