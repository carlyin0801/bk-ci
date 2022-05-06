package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.model.metrics.tables.TAtomFailSummaryData
import com.tencent.devops.model.metrics.tables.TAtomOverviewData
import com.tencent.devops.model.metrics.tables.TErrorTyppeDict
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.constant.BK_ATOM_CODE
import com.tencent.metrics.constant.BK_ATOM_NAME
import com.tencent.metrics.constant.BK_AVG_COST_TIME
import com.tencent.metrics.constant.BK_CLASSIFY_CODE
import com.tencent.metrics.constant.BK_ERROR_COUNT_SUM
import com.tencent.metrics.constant.BK_ERROR_TYPE
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.constant.BK_SUCCESS_RATE
import com.tencent.metrics.constant.BK_SUCESS_EXECUTE_COUNT_SUM
import com.tencent.metrics.constant.BK_TOTAL_AVG_COST_TIME_SUM
import com.tencent.metrics.constant.BK_TOTAL_EXECUTE_COUNT_SUM
import com.tencent.metrics.pojo.qo.QueryAtomStatisticsQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record5
import org.jooq.Record6
import org.jooq.Result
import org.jooq.impl.DSL
import org.jooq.impl.DSL.sum
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime

@Repository
class AtomStatisticsDao {

    fun queryAtomTrendInfo(
        dslContext: DSLContext,
        queryCondition: QueryAtomStatisticsQO
    ): Result<Record5<String, String, BigDecimal, Long, LocalDateTime>> {

        val atomCodes = if (!queryCondition.errorTypes.isNullOrEmpty()) {
            getAtomCodesByErrorType(dslContext, queryCondition)
        } else queryCondition.atomCodes
        with(TAtomOverviewData.T_ATOM_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryCondition, tProjectPipelineLabelInfo, atomCodes)
            val step = dslContext.select(
                this.ATOM_CODE.`as`(BK_ATOM_CODE),
                this.ATOM_NAME.`as`(BK_ATOM_NAME),
                this.SUCCESS_RATE.`as`(BK_SUCCESS_RATE),
                this.AVG_COST_TIME.`as`(BK_AVG_COST_TIME),
                this.STATISTICS_TIME.`as`(BK_STATISTICS_TIME)
            ).from(this)
            val conditionStep = if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
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
        atomCodes: List<String>?
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PROJECT_ID.eq(queryCondition.projectId))
        val pipelineIds = queryCondition.baseQueryReq.pipelineIds
        if (!pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(pipelineIds))
        }
        if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(pipelineLabelInfo.LABEL_ID.`in`(queryCondition.baseQueryReq.pipelineLabelIds))
        }
        if (!atomCodes.isNullOrEmpty()) {
            conditions.add(this.ATOM_CODE.`in`(atomCodes))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.startTime)
        val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.endTime)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }

    fun getAtomCodesByErrorType(dslContext: DSLContext, queryCondition: QueryAtomStatisticsQO,): List<String> {
        with(TAtomFailSummaryData.T_ATOM_FAIL_SUMMARY_DATA) {
            val conditions = mutableListOf<Condition>()
            val pipelineIds = queryCondition.baseQueryReq.pipelineIds
            conditions.add(this.PROJECT_ID.eq(queryCondition.projectId))
            if (!pipelineIds.isNullOrEmpty()) {
                conditions.add(this.PIPELINE_ID.`in`(pipelineIds))
            }
            conditions.add(this.ATOM_CODE.`in`(queryCondition.atomCodes))
            conditions.add(this.ERROR_TYPE.`in`(queryCondition.errorTypes))
            val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.startTime)
            val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.endTime)
            conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
            val fetch = dslContext.select(ATOM_CODE).from(this).where(conditions).groupBy(ATOM_CODE).fetch()
            if (fetch.isNotEmpty) {
                return fetch.map { it.value1() }
            }
            return emptyList()
        }
    }

    fun queryAtomExecuteStatisticsInfo(
        dslContext: DSLContext,
        queryCondition: QueryAtomStatisticsQO
    ): Result<Record6<String, String, String, BigDecimal, BigDecimal, BigDecimal>> {
        with(TAtomOverviewData.T_ATOM_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val atomCodes = if (!queryCondition.errorTypes.isNullOrEmpty()) {
                getAtomCodesByErrorType(dslContext, queryCondition)
            } else queryCondition.atomCodes
            val conditions = getConditions(queryCondition, tProjectPipelineLabelInfo, atomCodes)
            val step = dslContext.select(
                this.ATOM_CODE.`as`(BK_ATOM_CODE),
                this.ATOM_NAME.`as`(BK_ATOM_NAME),
                this.CLASSIFY_CODE.`as`(BK_CLASSIFY_CODE),
                sum(this.TOTAL_EXECUTE_COUNT.`as`(BK_TOTAL_EXECUTE_COUNT_SUM)),
                sum(this.SUCESS_EXECUTE_COUNT.`as`(BK_SUCESS_EXECUTE_COUNT_SUM)),
                sum(this.AVG_COST_TIME.`as`(BK_TOTAL_AVG_COST_TIME_SUM))
            ).from(this)
            val conditionStep = if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo).on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            val statisticsTable = conditionStep.groupBy(ATOM_CODE).asTable("statisticsTable")
            val tAtomFailSummaryData = TAtomFailSummaryData.T_ATOM_FAIL_SUMMARY_DATA
            val tErrorTyppeDict = TErrorTyppeDict.T_ERROR_TYPPE_DICT
            dslContext.select(
                statisticsTable.field(BK_ATOM_CODE),
                statisticsTable.field(BK_ATOM_NAME),
                statisticsTable.field(BK_CLASSIFY_CODE),
                statisticsTable.field(BK_TOTAL_EXECUTE_COUNT_SUM),
                statisticsTable.field(BK_SUCESS_EXECUTE_COUNT_SUM),
                statisticsTable.field(BK_TOTAL_AVG_COST_TIME_SUM),
                tAtomFailSummaryData.ERROR_TYPE.`as`(BK_ERROR_TYPE),
                sum(tAtomFailSummaryData.ERROR_COUNT).`as`(BK_ERROR_COUNT_SUM),
                tErrorTyppeDict.NAME
            ).from(statisticsTable)
                .join(tAtomFailSummaryData)
                .on(statisticsTable.field(BK_ATOM_CODE, String::class.java)!!.eq(tAtomFailSummaryData.ATOM_CODE))
                .join(tErrorTyppeDict)
                .on(tAtomFailSummaryData.ERROR_TYPE.eq(tErrorTyppeDict.ERROR_TYPE))
                .where()
        }
    }

    fun queryAtomExecuteStatisticsInfoCount(
        dslContext: DSLContext,
        queryCondition: QueryAtomStatisticsQO
    ): Long {
        with(TAtomOverviewData.T_ATOM_OVERVIEW_DATA) {
            val atomCodes = if (!queryCondition.errorTypes.isNullOrEmpty()) {
                getAtomCodesByErrorType(dslContext, queryCondition)
            } else queryCondition.atomCodes
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryCondition, tProjectPipelineLabelInfo, atomCodes)
            val step = dslContext.selectCount().from(this)
            val conditionStep = if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo).on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(ATOM_CODE).fetchOne(0, Long::class.java) ?: 0L
        }
    }
}