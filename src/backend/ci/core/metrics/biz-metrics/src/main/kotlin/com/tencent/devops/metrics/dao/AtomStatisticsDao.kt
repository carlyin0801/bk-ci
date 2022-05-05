package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TAtomFailSummaryData
import com.tencent.devops.model.metrics.tables.TAtomOverviewData
import com.tencent.devops.model.metrics.tables.TPipelineFailSummaryData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import com.tencent.metrics.pojo.qo.QueryAtomStatisticsQO
import org.jetbrains.annotations.NotNull
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

        if (!queryAtomStatisticsQO.errorTypes.isNullOrEmpty()) {
            val atomCodes = getAtomCodesByErrorType(dslContext, queryAtomStatisticsQO)
        }
        with(TAtomOverviewData.T_ATOM_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryAtomStatisticsQO, tProjectPipelineLabelInfo, atomCodes)
            val step = dslContext.select(
                this.ATOM_CODE,
                this.ATOM_NAME,
                this.SUCCESS_RATE,
                this.AVG_COST_TIME,
                this.STATISTICS_TIME
            ).from(this)
            val conditionStep = if (!queryAtomStatisticsQO.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo).on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(ATOM_CODE, STATISTICS_TIME).fetch()

        }
    }

    private fun TAtomOverviewData.getConditions(
        queryCondition: QueryAtomStatisticsQO,
        pipelineLabelInfo: TProjectPipelineLabelInfo,
        atomCodes: List<String>
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PROJECT_ID.eq(queryCondition.projectId))
        if (!queryCondition.queryReq.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(queryCondition.queryReq.pipelineIds))
        }
        if (!queryCondition.queryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(pipelineLabelInfo.LABEL_ID.`in`(queryCondition.queryReq.pipelineLabelIds))
        }
        conditions.add(this.ATOM_CODE.`in`(atomCodes))
        val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.startTime, formatter)
        val endTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.endTime, formatter)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }

    fun getAtomCodesByErrorType(dslContext: DSLContext, queryCondition: QueryAtomStatisticsQO,): List<String> {
        with(TAtomFailSummaryData.T_ATOM_FAIL_SUMMARY_DATA) {
            val conditions = mutableListOf<Condition>()
            conditions.add(this.PROJECT_ID.eq(queryCondition.projectId))
            if (!queryCondition.queryReq.pipelineIds.isNullOrEmpty()) {
                conditions.add(this.PIPELINE_ID.`in`(queryCondition.queryReq.pipelineIds))
            }
            conditions.add(this.ATOM_CODE.`in`(queryCondition.atomCodes))
            conditions.add(this.ERROR_TYPE.`in`(queryCondition.errorTypes))
            val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val startTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.startTime, formatter)
            val endTimeDateTime = LocalDateTime.parse(queryCondition.queryReq.endTime, formatter)
            conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
            val fetch = dslContext.select(ATOM_CODE).from(this).where(conditions).groupBy(ATOM_CODE).fetch()
            if (fetch.isNotEmpty) {
                return fetch.map { it.value1() }
            }
            return emptyList()
        }
    }
}