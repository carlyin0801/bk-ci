package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TPipelineStageOverviewData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.pojo.qo.QueryPipelineStageTrendInfoQO
import org.jooq.*
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class PipelineStageDao {
    fun getStageTrendPipelineInfo(
        dslContext: DSLContext,
        queryInfoQO: QueryPipelineStageTrendInfoQO
    ): MutableList<String> {
        with(TPipelineStageOverviewData.T_PIPELINE_STAGE_OVERVIEW_DATA) {
            val t = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryInfoQO, t)
            val step = dslContext.select(PIPELINE_ID).from(this)
            val conditionStep = if (!queryInfoQO.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(t).on(this.PROJECT_ID.eq(t.PROJECT_ID)).where(conditions)
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
            val t = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(queryInfoQO, t)
            conditions.add(PIPELINE_ID.`in`(pipelineInfos))
            val step =
                dslContext.select(
                PIPELINE_NAME,
                STATISTICS_TIME,
                    AVG_COST_TIME
                ).from(this)
            val conditionStep = if (!queryInfoQO.queryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.join(t).on(this.PROJECT_ID.eq(t.PROJECT_ID)).where(conditions)
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
        pipelineLabelInfo: TProjectPipelineLabelInfo
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
        conditions.add(this.STAGE_TAG_NAME.eq(queryCondition.stageTag))
        return conditions
    }
}