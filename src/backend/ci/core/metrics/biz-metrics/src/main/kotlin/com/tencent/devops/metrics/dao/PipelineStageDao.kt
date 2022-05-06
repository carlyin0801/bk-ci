package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.model.metrics.tables.TPipelineStageOverviewData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.constant.BK_AVG_COST_TIME
import com.tencent.metrics.constant.BK_PIPELINE_ID
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.pojo.qo.QueryPipelineStageTrendInfoQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record3
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class PipelineStageDao {
    fun getStageTrendPipelineInfo(
        dslContext: DSLContext,
        queryInfoQO: QueryPipelineStageTrendInfoQO
    ): MutableList<String> {
        with(TPipelineStageOverviewData.T_PIPELINE_STAGE_OVERVIEW_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryInfoQO, tProjectPipelineLabelInfo)
            val step = dslContext.select(PIPELINE_ID).from(this)
            val conditionStep = if (!queryInfoQO.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(tProjectPipelineLabelInfo)
                    .on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .groupBy(PIPELINE_ID)
                .orderBy(AVG_COST_TIME.desc())
                .limit(10)
                .fetch()
                .map {
                    it.value1()
                }
        }
    }

    fun queryPipelineStageTrendInfo(
        dslContext: DSLContext,
        queryInfoQO: QueryPipelineStageTrendInfoQO
    ): Result<Record3<String, LocalDateTime, Long>> {
        with(TPipelineStageOverviewData.T_PIPELINE_STAGE_OVERVIEW_DATA) {
            val pipelineInfos = getStageTrendPipelineInfo(dslContext, queryInfoQO)
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryInfoQO, tProjectPipelineLabelInfo)
            conditions.add(PIPELINE_ID.`in`(pipelineInfos))
            val step =
                dslContext.select(
                PIPELINE_ID.`as`(BK_PIPELINE_ID),
                STATISTICS_TIME.`as`(BK_STATISTICS_TIME),
                    AVG_COST_TIME.`as`(BK_AVG_COST_TIME)
                ).from(this)
            val conditionStep = if (!queryInfoQO.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(tProjectPipelineLabelInfo)
                    .on(this.PROJECT_ID.eq(tProjectPipelineLabelInfo.PROJECT_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }

            return conditionStep.groupBy(PIPELINE_ID, STATISTICS_TIME).fetch()

        }
    }

    fun getStageTag(dslContext: DSLContext, projectId: String): MutableList<String> {
        with(TPipelineStageOverviewData.T_PIPELINE_STAGE_OVERVIEW_DATA) {
            return dslContext
                .select(STAGE_TAG_NAME)
                .where(PROJECT_ID.eq(projectId))
                .groupBy(STAGE_TAG_NAME)
                .fetch()
                .map { it.value1() }
        }
    }

    private fun TPipelineStageOverviewData.getConditions(
        queryCondition: QueryPipelineStageTrendInfoQO,
        tProjectPipelineLabelInfo: TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val conditions = mutableListOf<Condition>()
        val pipelineIds = queryCondition.baseQueryReq.pipelineIds
        conditions.add(this.PIPELINE_ID.eq(queryCondition.projectId))
        if (!pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(pipelineIds))
        }
        if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(queryCondition.baseQueryReq.pipelineLabelIds))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.startTime)
        val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(queryCondition.baseQueryReq.endTime)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        conditions.add(this.STAGE_TAG_NAME.eq(queryCondition.stageTag))
        return conditions
    }
}