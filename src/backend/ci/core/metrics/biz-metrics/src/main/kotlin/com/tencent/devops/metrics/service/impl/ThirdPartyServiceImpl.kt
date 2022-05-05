package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.ThirdPartyOverviewInfoDao
import com.tencent.devops.metrics.service.ThirdPartyManageService
import com.tencent.metrics.constant.*
import com.tencent.metrics.pojo.`do`.CodeCheckInfoDO
import com.tencent.metrics.pojo.`do`.QualityInfoDO
import com.tencent.metrics.pojo.`do`.TurboInfoDO
import com.tencent.metrics.pojo.dto.QueryPipelineSummaryInfoDTO
import com.tencent.metrics.pojo.qo.ThirdPartyOverviewInfoQO
import com.tencent.metrics.pojo.vo.ThirdPartyOverviewInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ThirdPartyServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val thirdPartyOverviewInfoDao: ThirdPartyOverviewInfoDao
): ThirdPartyManageService {
    override fun queryPipelineSummaryInfo(
        queryPipelineSummaryInfoDTO: QueryPipelineSummaryInfoDTO
    ): ThirdPartyOverviewInfoVO {
        val result = thirdPartyOverviewInfoDao.queryPipelineSummaryInfo(
            ThirdPartyOverviewInfoQO(
                queryPipelineSummaryInfoDTO.projectId,
                queryPipelineSummaryInfoDTO.startTime,
                queryPipelineSummaryInfoDTO.endTime
            ),
            dslContext
        )
        val totalExecuteCount = thirdPartyOverviewInfoDao.queryPipelineSummaryCount(
            ThirdPartyOverviewInfoQO(
                queryPipelineSummaryInfoDTO.projectId,
                queryPipelineSummaryInfoDTO.startTime,
                queryPipelineSummaryInfoDTO.endTime
            ),
            dslContext
        )
        result?.let {
            return ThirdPartyOverviewInfoVO(
                CodeCheckInfoDO(
                    resolvedDefectNum = it.get(BK_RESOLVED_DEFECT_NUM, Int::class.java),
                    repoCodeccAvgScore = it.get(BK_REPO_CODECC_AVG_SCORE, Double::class.java) / totalExecuteCount
                ),
                QualityInfoDO(
                    it.get(
                        BK_QUALITY_PIPELINE_INTERCEPTION_NUM,
                        Double::class.java
                    ) / it.get(BK_QUALITY_PIPELINE_EXECUTE_NUM, Double::class.java)
                ),
                TurboInfoDO(it.get(BK_TURBO_SAVE_TIME, Long::class.java))
            )
        }
        return ThirdPartyOverviewInfoVO(
            CodeCheckInfoDO(
                repoCodeccAvgScore = 0.0,
                resolvedDefectNum = 0
            ),
            QualityInfoDO(0.0),
            TurboInfoDO(0L)
        )
    }
}