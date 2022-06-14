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
import com.tencent.devops.model.metrics.tables.TErrorTypeDict
import com.tencent.devops.model.metrics.tables.TPipelineFailDetailData
import com.tencent.devops.model.metrics.tables.TPipelineFailSummaryData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.devops.metrics.constant.Constants.BK_ERROR_COUNT_SUM
import com.tencent.devops.metrics.constant.Constants.BK_ERROR_NAME
import com.tencent.devops.metrics.constant.Constants.BK_ERROR_TYPE
import com.tencent.devops.metrics.constant.Constants.BK_ERROR_TYPE_NAME
import com.tencent.devops.metrics.constant.Constants.BK_STATISTICS_TIME
import com.tencent.devops.metrics.constant.Constants.DEFAULT_LIMIT_NUM
import com.tencent.devops.metrics.pojo.po.PipelineFailDetailDataPO
import com.tencent.devops.metrics.pojo.qo.QueryPipelineFailQO
import com.tencent.devops.metrics.pojo.qo.QueryPipelineOverviewQO
import com.tencent.devops.metrics.pojo.vo.BaseQueryReqVO
import com.tencent.devops.model.metrics.tables.TPipelineOverviewData
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record1
import org.jooq.Record2
import org.springframework.stereotype.Repository
import org.jooq.Result
import org.jooq.SelectConditionStep
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class PipelineFailDao {

    fun getPipelineIdByTotalExecuteCount(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO,
    ): List<String> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val pipelineIds = queryPipelineFailQo.baseQueryReq.pipelineIds
            val pipelineLabelIds = queryPipelineFailQo.baseQueryReq.pipelineLabelIds
            val conditions = getConditions(
                queryPipelineFailQo.projectId,
                queryPipelineFailQo.baseQueryReq,
                pipelineIds,
                tProjectPipelineLabelInfo,
                mutableListOf()
            )
            val step = dslContext.select(PIPELINE_ID).from(this)
            val conditionStep =
                if (!pipelineLabelIds.isNullOrEmpty()) {
                    step.leftJoin(tProjectPipelineLabelInfo)
                        .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                        .where(conditions)
                } else {
                    step.where(conditions)
                }
            return conditionStep.orderBy(ERROR_COUNT.desc()).limit(DEFAULT_LIMIT_NUM).fetchInto(String::class.java)
        }
    }

    fun queryPipelineFailTrendInfo(
        dslContext: DSLContext,
        queryPipelineFailTrendQo: QueryPipelineOverviewQO,
        errorType: Int
    ): Result<Record2<LocalDateTime, BigDecimal>> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            var pipelineIds = queryPipelineFailTrendQo.baseQueryReq.pipelineIds
            val conditions = getConditions(
                queryPipelineFailTrendQo.projectId,
                queryPipelineFailTrendQo.baseQueryReq,
                pipelineIds,
                tProjectPipelineLabelInfo,
                mutableListOf()
            )
            val step = dslContext.select(
                this.STATISTICS_TIME.`as`(BK_STATISTICS_TIME),
                sum<Int>(ERROR_COUNT).`as`(BK_ERROR_COUNT_SUM)
            ).from(this)
            conditions.add(ERROR_TYPE.eq(errorType))
            val conditionStep =
                if (!queryPipelineFailTrendQo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(this.STATISTICS_TIME).fetch()
        }
    }

    fun queryPipelineFailErrorTypeInfo(
        dslContext: DSLContext,
        queryPipelineFailTrendQo: QueryPipelineOverviewQO
    ): List<Int> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            var pipelineIds = queryPipelineFailTrendQo.baseQueryReq.pipelineIds
            val pipelineLabelIds = queryPipelineFailTrendQo.baseQueryReq.pipelineLabelIds
            val conditions = getConditions(
                queryPipelineFailTrendQo.projectId,
                queryPipelineFailTrendQo.baseQueryReq,
                pipelineIds,
                tProjectPipelineLabelInfo,
                mutableListOf()
            )
            val step = dslContext.select(this.ERROR_TYPE)
                .from(this)
            val conditionStep =
                if (!pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(ERROR_TYPE).fetchInto(Int::class.java)
        }
    }

    fun queryPipelineFailSumInfo(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): Result<Record2<Int, BigDecimal>> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            var pipelineIds = queryPipelineFailQo.baseQueryReq.pipelineIds
            val labelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val pipelineLabelIds = queryPipelineFailQo.baseQueryReq.pipelineLabelIds
            if (!pipelineIds.isNullOrEmpty()) {
                pipelineIds = getPipelineIdByTotalExecuteCount(dslContext, queryPipelineFailQo)
            }
            val conditions =
                getConditions(
                    queryPipelineFailQo.projectId,
                    queryPipelineFailQo.baseQueryReq,
                    pipelineIds,
                    labelInfo,
                    mutableListOf()
                )
            if (!queryPipelineFailQo.errorTypes.isNullOrEmpty()) {
                conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            }
            val step = dslContext.select(
                ERROR_TYPE.`as`(BK_ERROR_TYPE),
                sum<Int>(ERROR_COUNT).`as`(BK_ERROR_COUNT_SUM)
            )
                .from(this)
            val conditionStep =
                if (!pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(labelInfo).on(this.PIPELINE_ID.eq(labelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(this.ERROR_TYPE).fetch()
        }
    }

    fun queryPipelineFailDetailInfo(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): List<PipelineFailDetailDataPO> {
        with(TPipelineFailDetailData.T_PIPELINE_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryPipelineFailQo.projectId,
                queryPipelineFailQo.baseQueryReq,
                tProjectPipelineLabelInfo,
                mutableListOf()
            )
            if (!queryPipelineFailQo.errorTypes.isNullOrEmpty()) {
                conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            }
            val step = dslContext.select(
                PROJECT_ID,
                PIPELINE_ID,
                PIPELINE_NAME,
                BUILD_ID,
                BUILD_NUM,
                REPO_URL,
                BRANCH,
                START_USER,
                START_TIME,
                END_TIME,
                ERROR_TYPE,
                ERROR_CODE,
                ERROR_MSG,
                STATISTICS_TIME
            ).from(this)
            val conditionStep =
                if (!queryPipelineFailQo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                    step.where(conditions)
            }
            return conditionStep
                .groupBy(this.PIPELINE_ID, this.BUILD_NUM)
                .offset((queryPipelineFailQo.page - 1) * queryPipelineFailQo.pageSize)
                .limit(queryPipelineFailQo.pageSize)
                .fetchInto(PipelineFailDetailDataPO::class.java)
        }
    }

    fun queryPipelineFailDetailCount(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): Long {
        with(TPipelineFailDetailData.T_PIPELINE_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val pipelineLabelIds = queryPipelineFailQo.baseQueryReq.pipelineLabelIds
            val conditions = getConditions(
                queryPipelineFailQo.projectId,
                queryPipelineFailQo.baseQueryReq,
                tProjectPipelineLabelInfo,
                mutableListOf()
            )
            if (!queryPipelineFailQo.errorTypes.isNullOrEmpty()) {
                conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            }
            val step = dslContext.select().from(this)
            val conditionStep =
                if (!pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .groupBy(this.PIPELINE_ID, this.BUILD_NUM)
                .execute().toLong()
        }
    }

    private fun TPipelineFailDetailData.getConditions(
        projectId: String,
        pipelineFailDetailDataBaseQuery: BaseQueryReqVO,
        tProjectPipelineLabelInfo: TProjectPipelineLabelInfo,
        conditions: MutableList<Condition>
    ): MutableList<Condition> {
        conditions.add(this.PROJECT_ID.eq(projectId))
        if (!pipelineFailDetailDataBaseQuery.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(pipelineFailDetailDataBaseQuery.pipelineIds))
        }
        val startTimeDateTime =
            DateTimeUtil.stringToLocalDate(pipelineFailDetailDataBaseQuery.startTime!!)!!.atStartOfDay()
        val endTimeDateTime =
            DateTimeUtil.stringToLocalDate(pipelineFailDetailDataBaseQuery.endTime!!)!!.atStartOfDay()
        if (!pipelineFailDetailDataBaseQuery.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(pipelineFailDetailDataBaseQuery.pipelineLabelIds))
        }
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }


    private fun TPipelineFailSummaryData.getConditions(
        projectId: String,
        baseQueryReq: BaseQueryReqVO,
        pipelineIds: List<String>?,
        tProjectPipelineLabelInfo: TProjectPipelineLabelInfo,
        conditions: MutableList<Condition>
    ): MutableList<Condition> {
        conditions.add(PROJECT_ID.eq(projectId))
        if (!pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(pipelineIds))
        }
        if (!baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(baseQueryReq.pipelineLabelIds))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDate(baseQueryReq.startTime!!)!!.atStartOfDay()
        val endTimeDateTime = DateTimeUtil.stringToLocalDate(baseQueryReq.endTime!!)!!.atStartOfDay()
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }
}