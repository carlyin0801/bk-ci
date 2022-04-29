package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TAtomFailSummaryData
import com.tencent.devops.model.metrics.tables.TAtomOverviewData
import com.tencent.devops.model.metrics.tables.TPipelineFailSummaryData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import com.tencent.metrics.pojo.qo.QueryAtomStatisticsQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record5
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class AtomStatisticsDao {

    fun queryAtomTrendInfo(
        dslContext: DSLContext,
        queryAtomStatisticsQO: QueryAtomStatisticsQO
    ): Result<Record5<String, String, BigDecimal, Long, LocalDateTime>> {
        with(TAtomOverviewData.T_ATOM_OVERVIEW_DATA) {
            val t1 = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val t2 = TAtomFailSummaryData.T_ATOM_FAIL_SUMMARY_DATA
            val conditions = getConditions(queryAtomStatisticsQO, t1, t2)
            val step = dslContext.select(
                this.ATOM_CODE,
                this.ATOM_NAME,
                this.SUCCESS_RATE,
                this.AVG_COST_TIME,
                this.STATISTICS_TIME
            ).from(this)
            val conditionStep = if (!queryAtomStatisticsQO.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(t2).on(this.PIPELINE_ID.eq(t2.PIPELINE_ID))
                    .leftJoin(t1).on(this.PROJECT_ID.eq(t1.PROJECT_ID))
                    .where(conditions)
            } else {
                step.leftJoin(t2).on(this.PIPELINE_ID.eq(t2.PIPELINE_ID)).where(conditions)
            }
            return conditionStep.groupBy(ATOM_CODE, STATISTICS_TIME).fetch()

        }
    }

    private fun TAtomOverviewData.getConditions(
        queryCondition: QueryAtomStatisticsQO,
        pipelineLabelInfo: TProjectPipelineLabelInfo,
        atomFailSummaryInfo: TAtomFailSummaryData
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(queryCondition.projectId))
        if (!queryCondition.queryReq.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(queryCondition.queryReq.pipelineIds))
        }
        if (!queryCondition.queryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(pipelineLabelInfo.LABEL_ID.`in`(queryCondition.queryReq.pipelineLabelIds))
        }
        if (!queryCondition.errorTypes.isNullOrEmpty()) {
            conditions.add(atomFailSummaryInfo.ERROR_TYPE.`in`(queryCondition.errorTypes))
        }
        conditions.add(this.ATOM_CODE.`in`(queryCondition.atomCodes))
        val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.startTime, formatter)
        val endTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.endTime, formatter)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }
}