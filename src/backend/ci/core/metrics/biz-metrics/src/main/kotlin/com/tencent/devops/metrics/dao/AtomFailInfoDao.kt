package com.tencent.devops.metrics.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.model.metrics.tables.TAtomFailDetailData
import com.tencent.devops.model.metrics.tables.TErrorTyppeDict
import com.tencent.devops.model.metrics.tables.TPipelineFailDetailData
import com.tencent.devops.model.metrics.tables.TProjectPipelineLabelInfo
import com.tencent.metrics.constant.BK_ATOM_CODE
import com.tencent.metrics.constant.BK_ATOM_NAME
import com.tencent.metrics.constant.BK_BUILD_ID
import com.tencent.metrics.constant.BK_BUILD_NUM
import com.tencent.metrics.constant.BK_CLASSIFY_CODE
import com.tencent.metrics.constant.BK_END_TIME
import com.tencent.metrics.constant.BK_ERROR_CODE
import com.tencent.metrics.constant.BK_ERROR_COUNT
import com.tencent.metrics.constant.BK_ERROR_COUNT_SUM
import com.tencent.metrics.constant.BK_ERROR_MSG
import com.tencent.metrics.constant.BK_ERROR_TYPE
import com.tencent.metrics.constant.BK_PIPELINE_ID
import com.tencent.metrics.constant.BK_PIPELINE_NAME
import com.tencent.metrics.constant.BK_START_TIME
import com.tencent.metrics.constant.BK_START_USER
import com.tencent.metrics.pojo.`do`.AtomFailDetailInfoDO
import com.tencent.metrics.pojo.qo.QueryAtomFailInfoQO
import com.tencent.metrics.pojo.qo.QueryPipelineFailQO
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record4
import org.jooq.Result
import org.jooq.impl.DSL.count
import org.springframework.stereotype.Repository

@Repository
class AtomFailInfoDao {

    fun queryAtomErrorCodeStatisticsInfo(
        dslContext: DSLContext,
        queryAtomFailInfo: QueryAtomFailInfoQO
    ): Result<Record4<Int, Int, String, Int>> {
        with(TAtomFailDetailData.T_ATOM_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryAtomFailInfo,
                tProjectPipelineLabelInfo
            )
            val step = dslContext.select(
                this.ERROR_TYPE.`as`(BK_ERROR_TYPE),
                this.ERROR_CODE.`as`(BK_ERROR_CODE),
                this.ERROR_MSG.`as`(BK_ERROR_MSG),
                count(this.ERROR_CODE).`as`(BK_ERROR_COUNT)
            ).from(this)
            val conditionStep
            = if (!queryAtomFailInfo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .groupBy(this.ERROR_CODE)
                .orderBy(conditionStep.field(BK_ERROR_COUNT)!!.desc())
                .limit(9)
                .fetch()
        }

    }

    fun queryAtomErrorCodeOverviewCount(
        dslContext: DSLContext,
        queryAtomFailInfo: QueryAtomFailInfoQO,
        errorCodes: List<Int>
    ): Int {
        with(TAtomFailDetailData.T_ATOM_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryAtomFailInfo,
                tProjectPipelineLabelInfo
            )
            conditions.add(this.ERROR_CODE.notIn(errorCodes))
            val step = dslContext.selectCount().from(this)
            val conditionStep
                    = if (!queryAtomFailInfo.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep.fetchOne(0, Int::class.java)?: 0
        }

    }

    private fun TAtomFailDetailData.getConditions(
        queryAtomFailInfo: QueryAtomFailInfoQO,
        tProjectPipelineLabelInfo: TProjectPipelineLabelInfo
    ): MutableList<Condition> {
        val baseQueryReq = queryAtomFailInfo.baseQueryReq
        val conditions = mutableListOf<Condition>()
        conditions.add(this.PIPELINE_ID.eq(queryAtomFailInfo.projectId))
        if (!baseQueryReq.pipelineIds.isNullOrEmpty()) {
            conditions.add(this.PIPELINE_ID.`in`(baseQueryReq.pipelineIds))
        }
        if (!baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
            conditions.add(tProjectPipelineLabelInfo.LABEL_ID.`in`(baseQueryReq.pipelineLabelIds))
        }
        if (!queryAtomFailInfo.errorTypes.isNullOrEmpty()) {
            conditions.add(this.ERROR_TYPE.`in`(queryAtomFailInfo.errorTypes))
        }
        if (!queryAtomFailInfo.errorCodes.isNullOrEmpty()) {
            conditions.add(this.ERROR_CODE.`in`(queryAtomFailInfo.errorCodes))
        }
        val startTimeDateTime = DateTimeUtil.stringToLocalDateTime(baseQueryReq.startTime)
        val endTimeDateTime = DateTimeUtil.stringToLocalDateTime(baseQueryReq.endTime)
        conditions.add(this.STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
        return conditions
    }

    fun queryPipelineFailDetailInfo(
        dslContext: DSLContext,
        queryCondition: QueryAtomFailInfoQO
    ): List<AtomFailDetailInfoDO> {
        with(TAtomFailDetailData.T_ATOM_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryCondition,
                tProjectPipelineLabelInfo
            )
            val step = dslContext.select(
                this.PIPELINE_ID.`as`(BK_PIPELINE_ID),
                this.PIPELINE_NAME.`as`(BK_PIPELINE_NAME),
                this.BUILD_ID.`as`(BK_BUILD_ID),
                this.BUILD_NUM.`as`(BK_BUILD_NUM),
                this.ATOM_CODE.`as`(BK_ATOM_CODE),
                this.ATOM_NAME.`as`(BK_ATOM_NAME),
                this.CLASSIFY_CODE.`as`(BK_CLASSIFY_CODE),
                this.START_USER.`as`(BK_START_USER),
                this.START_TIME.`as`(BK_START_TIME),
                this.END_TIME.`as`(BK_END_TIME),
                this.ERROR_TYPE.`as`(BK_ERROR_TYPE),
                this.ERROR_CODE.`as`(BK_ERROR_CODE),
                this.ERROR_MSG.`as`(BK_ERROR_MSG)
            ).from(this)
            val conditionStep
                    = if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .groupBy(this.PIPELINE_ID, this.BUILD_NUM)
                .limit((queryCondition.page!! - 1) * queryCondition.pageSize!!, queryCondition.pageSize)
                .fetchInto(AtomFailDetailInfoDO::class.java)
        }
    }

    fun queryPipelineFailDetailCount(
        dslContext: DSLContext,
        queryCondition: QueryAtomFailInfoQO
    ): Long {
        with(TAtomFailDetailData.T_ATOM_FAIL_DETAIL_DATA) {
            val tProjectPipelineLabelInfo = TProjectPipelineLabelInfo.T_PROJECT_PIPELINE_LABEL_INFO
            val conditions = getConditions(
                queryCondition,
                tProjectPipelineLabelInfo
            )
            val step = dslContext.selectCount().from(this)
            val conditionStep
                    = if (!queryCondition.baseQueryReq.pipelineLabelIds.isNullOrEmpty()) {
                step.leftJoin(tProjectPipelineLabelInfo)
                    .on(this.PIPELINE_ID.eq(tProjectPipelineLabelInfo.PIPELINE_ID))
                    .where(conditions)
            } else {
                step.where(conditions)
            }
            return conditionStep
                .groupBy(this.PIPELINE_ID, this.BUILD_NUM)
                .fetchOne(0, Long::class.java)?: 0L
        }
    }
}