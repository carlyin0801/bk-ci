package com.tencent.devops.metrics.dao

import com.tencent.devops.model.metrics.tables.TProjectThirdPlatformData
import com.tencent.metrics.constant.*
import com.tencent.metrics.pojo.qo.ThirdPartyOverviewInfoQO
import org.jooq.DSLContext
import org.jooq.Record5
import org.jooq.impl.DSL.sum
import org.springframework.stereotype.Repository
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Repository
class ThirdPartyOverviewInfoDao {
    fun queryPipelineSummaryInfo(
        thirdPartyOverviewInfoQO: ThirdPartyOverviewInfoQO,
        dslContext: DSLContext
    ): Record5<BigDecimal, BigDecimal, BigDecimal, BigDecimal, BigDecimal>? {
        with(TProjectThirdPlatformData.T_PROJECT_THIRD_PLATFORM_DATA) {
            val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val startTimeDateTime = LocalDateTime.parse(thirdPartyOverviewInfoQO.startTime, formatter)
            val endTimeDateTime = LocalDateTime.parse(thirdPartyOverviewInfoQO.endTime, formatter)
            return dslContext.select(
                sum(REPO_CODECC_AVG_SCORE).`as`(BK_REPO_CODECC_AVG_SCORE),
                sum(RESOLVED_DEFECT_NUM).`as`(BK_RESOLVED_DEFECT_NUM),

                sum(QUALITY_PIPELINE_INTERCEPTION_NUM).`as`(BK_QUALITY_PIPELINE_INTERCEPTION_NUM),
                sum(QUALITY_PIPELINE_EXECUTE_NUM).`as`(BK_QUALITY_PIPELINE_EXECUTE_NUM),
                sum(TURBO_SAVE_TIME).`as`(BK_TURBO_SAVE_TIME)
            ).from(this)
                .where(PROJECT_ID.eq(thirdPartyOverviewInfoQO.projectId))
                .and(STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
                .fetchOne()
        }
    }

    fun queryPipelineSummaryCount(
        thirdPartyOverviewInfoQO: ThirdPartyOverviewInfoQO,
        dslContext: DSLContext
    ): Int {
        with(TProjectThirdPlatformData.T_PROJECT_THIRD_PLATFORM_DATA) {
            val formatter  = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val startTimeDateTime = LocalDateTime.parse(thirdPartyOverviewInfoQO.startTime, formatter)
            val endTimeDateTime = LocalDateTime.parse(thirdPartyOverviewInfoQO.endTime, formatter)
            return dslContext.selectCount().from(this)
                .where(PROJECT_ID.eq(thirdPartyOverviewInfoQO.projectId))
                .and(STATISTICS_TIME.between(startTimeDateTime, endTimeDateTime))
                .fetchOne(0, Int::class.java)?: 0
        }
    }
}