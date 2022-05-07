package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.model.metrics.tables.TErrorTyppeDict
import com.tencent.devops.model.metrics.tables.TPipelineFailDetailData
import com.tencent.devops.model.metrics.tables.TPipelineFailSummaryData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.constant.BK_ERROR_COUNT_SUM
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.pojo.`do`.BaseQueryReqDO
import com.tencent.metrics.pojo.po.PipelineFailDetailDataPO
import com.tencent.metrics.pojo.qo.QueryPipelineFailQO
import com.tencent.metrics.pojo.qo.QueryPipelineOverviewQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Record2
import org.jooq.Record3
import org.jooq.impl.DSL.sum
import org.springframework.stereotype.Repository
import org.jooq.Result
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

            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryPipelineFailTrendQo.projectId,
                queryPipelineFailTrendQo.baseQueryReq,
                tProjectPipelineLabelInfo
            )
            val step = dslContext.select(
                this.STATISTICS_TIME.`as`(BK_STATISTICS_TIME),
                sum(this.ERROR_COUNT).`as`(BK_ERROR_COUNT_SUM)
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
        queryReq: QueryPipelineOverviewQO
    ): Result<Record2<Int, String>> {
        with(TPipelineFailSummaryData.T_PIPELINE_FAIL_SUMMARY_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val tErrorTyppeDict = TErrorTyppeDict.T_ERROR_TYPPE_DICT
            val conditions = getConditions(queryReq.projectId, queryReq.baseQueryReq, tProjectPipelineLabelInfo)
            val step = dslContext.select(this.ERROR_TYPE, tErrorTyppeDict.NAME)
                .from(this)
            val conditionStep = if (!queryReq.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tErrorTyppeDict).on(this.ERROR_TYPE.eq(tErrorTyppeDict.ERROR_TYPE))
                    .leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.leftJoin(tErrorTyppeDict).on(this.ERROR_TYPE.eq(tErrorTyppeDict.ERROR_TYPE)).where(conditions)
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
            val conditions = getConditions(queryPipelineFailQo.projectId, queryPipelineFailQo.baseQueryReq, t1)
            val step = dslContext.select(
                ERROR_TYPE,
                t2.NAME,
                sum(this.ERROR_COUNT))
                .from(this)
            conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            val conditionStep = if (!queryPipelineFailQo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
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
    ): List<PipelineFailDetailDataPO> {
        with(TPipelineFailDetailData.T_PIPELINE_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryPipelineFailQo.projectId,
                queryPipelineFailQo.baseQueryReq,
                tProjectPipelineLabelInfo
            )
            val step = dslContext.select().from(this)
            conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            val conditionStep = if (!queryPipelineFailQo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .groupBy(this.PIPELINE_ID, this.BUILD_NUM)
                .limit((queryPipelineFailQo.page - 1) * queryPipelineFailQo.pageSize, queryPipelineFailQo.pageSize)
                .fetch().into(PipelineFailDetailDataPO::class.java)
        }
    }

    fun queryPipelineFailDetailCount(
        dslContext: DSLContext,
        queryPipelineFailQo: QueryPipelineFailQO
    ): Long {
        with(TPipelineFailDetailData.T_PIPELINE_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val tErrorTypeDict = TErrorTyppeDict.T_ERROR_TYPPE_DICT
            val conditions = getConditions(
                queryPipelineFailQo.projectId,
                queryPipelineFailQo.baseQueryReq,
                tProjectPipelineLabelInfo
            )
            val step = dslContext.selectCount().from(this)
            conditions.add(ERROR_TYPE.`in`(queryPipelineFailQo.errorTypes))
            val conditionStep = if (!queryPipelineFailQo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tErrorTypeDict).on(this.ERROR_TYPE.eq(tErrorTypeDict.ERROR_TYPE))
                    .leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.leftJoin(tErrorTypeDict).on(this.ERROR_TYPE.eq(tErrorTypeDict.ERROR_TYPE)).where(conditions)
            }
            return conditionStep.fetchOne(0, Long::class.java)?: 0L
        }
    }

    private fun TPipelineFailDetailData.getConditions(
        projectId: String,
        baseQueryReq: BaseQueryReqDO,
        tProjectPipelineLabelInfo: TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(projectId))
        if (!baseQueryReq.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(baseQueryReq.pipelineIds))
        }
        if (!baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(baseQueryReq.pipelineLabelIds))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(baseQueryReq.startTime)
        val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(baseQueryReq.endTime)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }


    private fun TPipelineFailSummaryData.getConditions(
        projectId: String,
        baseQueryReq: BaseQueryReqDO,
        tProjectPipelineLabelInfo: TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(projectId))
        if (!baseQueryReq.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(baseQueryReq.pipelineIds))
        }
        if (!baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(baseQueryReq.pipelineLabelIds))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(baseQueryReq.startTime)
        val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(baseQueryReq.endTime)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }

}