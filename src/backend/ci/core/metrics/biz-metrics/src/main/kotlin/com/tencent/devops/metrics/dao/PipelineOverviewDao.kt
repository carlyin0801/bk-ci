package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.model.metrics.tables.TPipelineOverviewData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.constant.BK_FAIL_AVG_COST_TIME
import com.tencent.metrics.constant.BK_FAIL_EXECUTE_COUNT
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.constant.BK_SUCESS_EXECUTE_COUNT_SUM
import com.tencent.metrics.constant.BK_TOTAL_AVG_COST_TIME
import com.tencent.metrics.constant.BK_TOTAL_AVG_COST_TIME_SUM
import com.tencent.metrics.constant.BK_TOTAL_EXECUTE_COUNT
import com.tencent.metrics.constant.BK_TOTAL_EXECUTE_COUNT_SUM
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record3
import org.jooq.Record5
import org.jooq.Result
import org.jooq.impl.DSL.sum
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class PipelineOverviewDao {

    fun queryPipelineSumInfo(
        dslContext: DSLContext,
        queryPipelineOverview: QueryPipelineOverviewQO
    ): Record3<BigDecimal, BigDecimal, BigDecimal>? {
        with(TPipelineOverviewData.T_PIPELINE_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineOverview, tProjectPipelineLabelInfo)
            val step = dslContext.select(
                sum(TOTAL_EXECUTE_COUNT).`as`(BK_TOTAL_EXECUTE_COUNT_SUM),
                sum(SUCESS_EXECUTE_COUNT).`as`(BK_SUCESS_EXECUTE_COUNT_SUM),
                sum(TOTAL_AVG_COST_TIME).`as`(BK_TOTAL_AVG_COST_TIME_SUM)
            ).from(this)
            val conditionStep = if (!queryPipelineOverview.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                 step.join(tProjectPipelineLabelInfo)
                     .on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
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
    ): Result<Record5<LocalDateTime, BigDecimal, BigDecimal, BigDecimal, BigDecimal>> {
        with(TPipelineOverviewData.T_PIPELINE_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineOverview, tProjectPipelineLabelInfo)
            val step = dslContext.select(
                STATISTICS_TIME.`as`(BK_STATISTICS_TIME),
                sum(TOTAL_EXECUTE_COUNT).`as`(BK_TOTAL_EXECUTE_COUNT),
                sum(FAIL_EXECUTE_COUNT).`as`(BK_FAIL_EXECUTE_COUNT),
                sum(TOTAL_AVG_COST_TIME).`as`(BK_TOTAL_AVG_COST_TIME),
                sum(FAIL_AVG_COST_TIME).`as`(BK_FAIL_AVG_COST_TIME)
            ).from(this)
            val conditionStep = if (!queryPipelineOverview.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(tProjectPipelineLabelInfo)
                    .on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(this.STATISTICS_TIME).fetch()
        }
    }

    private fun TPipelineOverviewData.getConditions(
        queryCondition: QueryPipelineOverviewQO,
        tProjectPipelineLabelInfo:  TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        val pipelineIds = queryCondition.baseQueryReq.pipelineIds
        val pipelineLabelIds = queryCondition.baseQueryReq.pipelineLabelIds
        conditions.add(this.PIPELINE_ID.eq(queryCondition.projectId))
        if (!pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(pipelineIds))
        }
        if (!pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(pipelineLabelIds))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.startTime)
        val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.endTime)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }
}