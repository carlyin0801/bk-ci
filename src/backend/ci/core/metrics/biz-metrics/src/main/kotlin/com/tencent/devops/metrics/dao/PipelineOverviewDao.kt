package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TPipelineOverviewData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.pojo.`do`.PipelineTrendInfoDO
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import org.jooq.*
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
            val t = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineOverview, t)
            val step = dslContext.select(
                sum(TOTAL_EXECUTE_COUNT).`as`("totalExecuteCountSum"),
                sum(SUCESS_EXECUTE_COUNT).`as`("sucessExecuteCountSum"),
                sum(TOTAL_AVG_COST_TIME).`as`("totalAvgCostTimeSum")
            ).from(this)
            val conditionStep = if (!queryPipelineOverview.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                 step.join(t).on(this.PROJECT_ID.eq(t.PROJECT_ID)).where(conditions)
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
            val t = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineOverview, t)
            val step = dslContext.select(
                STATISTICS_TIME.`as`("STATISTICS_TIME"),
                sum(TOTAL_EXECUTE_COUNT).`as`("TOTAL_EXECUTE_COUNT"),
                sum(FAIL_EXECUTE_COUNT).`as`("FAIL_EXECUTE_COUNT"),
                sum(TOTAL_AVG_COST_TIME).`as`("TOTAL_AVG_COST_TIME"),
                sum(FAIL_AVG_COST_TIME).`as`("FAIL_AVG_COST_TIME")
            ).from(this, t)
            val conditionStep = if (!queryPipelineOverview.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(t).on(this.PROJECT_ID.eq(t.PROJECT_ID)).where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(this.STATISTICS_TIME).fetch()
        }
    }

    private fun TPipelineOverviewData.getConditions(
        queryCondition: QueryPipelineOverviewQO,
        pipelineLabelInfo:  TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(queryCondition.projectId))
        if (!queryCondition.queryReq.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(queryCondition.queryReq.pipelineIds))
        }
        if (!queryCondition.queryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(pipelineLabelInfo.LABEL_ID.`in`(queryCondition.queryReq.pipelineLabelIds))
        }
        val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.startTime, formatter)
        val endTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.endTime, formatter)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }
}