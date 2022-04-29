package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.model.metrics.tables.TErrorTyppeDict
import com.tencent.devops.model.metrics.tables.TPipelineFailDetailData
import com.tencent.devops.model.metrics.tables.TPipelineFailSummaryData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import com.tencent.metrics.pojo.`do`.PipelineFailDetailInfoDO
import com.tencent.metrics.pojo.qo.QueryPipelineFailQO
import com.tencent.metrics.pojo.qo.QueryPipelineFailTrendQO
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import com.tencent.metrics.pojo.qo.ThirdPartyOverviewInfoQO
import com.tencent.metrics.pojo.vo.PipelineFailSumInfoVO
import org.jooq.*
import org.jooq.impl.DSL.sum
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class PipelineFailDao {
    fun queryPipelineFailTrendInfo(
        dslContext: DSLContext,
        queryPipelineFailTrendQo: QueryPipelineOverviewQO,
        errorType: Int
    ): Result<Record2<LocalDateTime, BigDecimal>> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val t1 = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineFailTrendQo.projectId, queryPipelineFailTrendQo.queryReq, t1)
            val step = dslContext.select(
                this.STATISTICS_TIME,
                sum(this.ERROR_COUNT)
            ).from(this)
            conditions.add(ERROR_TYPE.eq(errorType))
            val conditionStep = if (!queryPipelineFailTrendQo.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(t1).on(this.PROJECT_ID.eq(t1.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.groupBy(this.STATISTICS_TIME).fetch()
        }
    }

    fun queryPipelineFailErrorTypeInfo(
        dslContext: DSLContext,
        queryReqDO: QueryPipelineOverviewQO
    ): Result<Record2<Int, String>> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val t1 = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val t2 = TErrorTyppeDict.T_ERROR_TYPPE_DICT
            val conditions = getConditions(queryReqDO.projectId, queryReqDO.queryReq, t1)
            val step = dslContext.select(this.ERROR_TYPE, t2.NAME)
                .from(this)
            val conditionStep = if (!queryReqDO.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(t2).on(this.ERROR_TYPE.eq(t2.ERROR_TYPE))
                    .leftJoin(t1).on(this.PROJECT_ID.eq(t1.PROJECT_ID))
                    .where(conditions)
            } else {
                step.leftJoin(t2).on(this.ERROR_TYPE.eq(t2.ERROR_TYPE)).where(conditions)
            }
            return conditionStep.groupBy(ERROR_TYPE).fetch()
        }
    }

    fun queryPipelineFailSumInfo(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): Result<Record3<Int, String, BigDecimal>> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val t1 = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val t2 = TErrorTyppeDict.T_ERROR_TYPPE_DICT
            val conditions = getConditions(queryPipelineFailQo.projectId, queryPipelineFailQo.queryReq, t1)
            val step = dslContext.select(
                ERROR_TYPE,
                t2.NAME,
                sum(this.ERROR_COUNT))
                .from(this)
            conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            val conditionStep = if (!queryPipelineFailQo.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(t2).on(this.ERROR_TYPE.eq(t2.ERROR_TYPE))
                    .leftJoin(t1).on(this.PROJECT_ID.eq(t1.PROJECT_ID))
                    .where(conditions)
            } else {
                step.leftJoin(t2).on(this.ERROR_TYPE.eq(t2.ERROR_TYPE)).where(conditions)
            }
            return conditionStep.groupBy(this.ERROR_TYPE).fetch()
        }
    }

    fun queryPipelineFailDetailInfo(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): Result<Record>? {
        with(TPipelineFailDetailData.T_PIPELINE_FAIL_DETAIL_DATA) {
            val t1 = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryPipelineFailQo.projectId, queryPipelineFailQo.queryReq, t1)
            val step = dslContext.select().from(this)
            conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            val conditionStep = if (!queryPipelineFailQo.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(t1).on(this.PROJECT_ID.eq(t1.PROJECT_ID)).where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .limit(queryPipelineFailQo.limit!!.limit)
                .offset(queryPipelineFailQo.limit!!.offset)
                .fetch()
        }
    }

    fun queryPipelineFailDetailCount(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): Int {
        with(TPipelineFailDetailData.T_PIPELINE_FAIL_DETAIL_DATA) {
            val t1 = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val t2 = TErrorTyppeDict.T_ERROR_TYPPE_DICT
            val conditions = getConditions(queryPipelineFailQo.projectId, queryPipelineFailQo.queryReq, t1)
            val step = dslContext.selectCount().from(this)
            conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            val conditionStep = if (!queryPipelineFailQo.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(t2).on(this.ERROR_TYPE.eq(t2.ERROR_TYPE))
                    .leftJoin(t1).on(this.PROJECT_ID.eq(t1.PROJECT_ID))
                    .where(conditions)
            } else {
                step.leftJoin(t2).on(this.ERROR_TYPE.eq(t2.ERROR_TYPE)).where(conditions)
            }
            return conditionStep.fetchOne(0, Int::class.java)?: 0
        }
    }

    private fun TPipelineFailDetailData.getConditions(
        projectId: String,
        queryCondition: BaseQueryReqDO,
        pipelineLabelInfo: TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(projectId))
        if (!queryCondition.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(queryCondition.pipelineIds))
        }
        if (!queryCondition.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(pipelineLabelInfo.LABEL_ID.`in`(queryCondition.pipelineLabelIds))
        }
        val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startTimeDateTime = LocalDateTime.parse(queryCondition.startTime, formatter)
        val endTimeDateTime = LocalDateTime.parse(queryCondition.endTime, formatter)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }


    private fun TPipelineFailSummaryData.getConditions(
        projectId: String,
        queryCondition: BaseQueryReqDO,
        pipelineLabelInfo: TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(projectId))
        if (!queryCondition.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(queryCondition.pipelineIds))
        }
        if (!queryCondition.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(pipelineLabelInfo.LABEL_ID.`in`(queryCondition.pipelineLabelIds))
        }
        val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        val startTimeDateTime = LocalDateTime.parse(queryCondition.startTime, formatter)
        val endTimeDateTime = LocalDateTime.parse(queryCondition.endTime, formatter)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }

}