package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TErrorCodeInfo
import com.tencent.metrics.pojo.`do`.ErrorCodeInfoDO
import com.tencent.metrics.pojo.qo.QueryErrorCodeInfoQO
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class ErrorCodeInfoDao {

    fun getErrorCodeInfo(
        dslContext: DSLContext,
        queryCondition: QueryErrorCodeInfoQO
    ): List<ErrorCodeInfoDO> {
        with(TErrorCodeInfo.T_ERROR_CODE_INFO) {
            val step = dslContext.select(ERROR_TYPE, ERROR_CODE, ERROR_MSG)
            val conditionStep = if (!queryCondition.errorTypes.isNullOrEmpty()) {
                step.where(ERROR_TYPE.`in`(queryCondition.errorTypes))
                    .groupBy(ERROR_CODE)
            } else {
                step.groupBy(ERROR_CODE)
            }
            return conditionStep.limit(queryCondition.page - 1 * queryCondition.pageSize, queryCondition.pageSize)
                .fetchInto(ErrorCodeInfoDO::class.java)
        }
    }

    fun getErrorCodeInfoCount(
        dslContext: DSLContext,
        queryCondition: QueryErrorCodeInfoQO
    ): Long {
        with(TErrorCodeInfo.T_ERROR_CODE_INFO) {
            val step = dslContext.selectCount()
            val conditionStep = if (!queryCondition.errorTypes.isNullOrEmpty()) {
                step.where(ERROR_TYPE.`in`(queryCondition.errorTypes))
                    .groupBy(ERROR_CODE)
            } else {
                step.groupBy(ERROR_CODE)
            }
            return conditionStep.fetchOne(0, Long::class.java)?: 0L
        }
    }
}