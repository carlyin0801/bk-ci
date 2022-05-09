package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TAtomOverviewData
import com.tencent.devops.model.metrics.tables.TErrorCodeInfo
import com.tencent.devops.model.metrics.tables.TErrorTyppeDict
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.pojo.`do`.AtomBaseInfoDO
import com.tencent.metrics.pojo.`do`.PipelineErrorTypeInfoDO
import com.tencent.metrics.pojo.`do`.PipelineLabelInfoDO
import com.tencent.metrics.pojo.qo.QueryProjectInfoQO
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ProjectInfoDao {
    fun queryProjectAtomList(
        dslContext: DSLContext,
        projectId: String
    ): List<AtomBaseInfoDO> {
        with(TAtomOverviewData.T_ATOM_OVERVIEW_DATA) {
            return dslContext.select(ATOM_CODE, ATOM_NAME)
                .where(PROJECT_ID.eq(projectId))
                .groupBy(ATOM_CODE)
                .fetchInto(AtomBaseInfoDO::class.java)
        }
    }

    fun queryProjectPipelineLabels(
        dslContext: DSLContext,
        queryCondition: QueryProjectInfoQO
    ): List<PipelineLabelInfoDO> {
        with(TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO) {
            val step = dslContext.select(LABEL_ID, LABEL_NAME)
            val conditionStep = if (!queryCondition.pipelineIds.isNullOrEmpty()) {
                step.where(PROJECT_ID.eq(queryCondition.projectId))
                    .and(PIPELINE_ID.`in`(queryCondition.pipelineIds))
                    .groupBy(LABEL_ID)
            } else {
                step.where(PROJECT_ID.eq(queryCondition.projectId))
                    .groupBy(LABEL_ID)
            }
            return conditionStep.limit(queryCondition.page - 1 * queryCondition.pageSize, queryCondition.pageSize)
                .fetchInto(PipelineLabelInfoDO::class.java)
        }
    }

    fun queryPipelineErrorTypes(dslContext: DSLContext): List<PipelineErrorTypeInfoDO> {
        with(TErrorTyppeDict.T_ERROR_TYPPE_DICT) {
            return dslContext.select(ERROR_TYPE, NAME)
                .groupBy(ERROR_TYPE)
                .fetchInto(PipelineErrorTypeInfoDO::class.java)
        }
    }
}