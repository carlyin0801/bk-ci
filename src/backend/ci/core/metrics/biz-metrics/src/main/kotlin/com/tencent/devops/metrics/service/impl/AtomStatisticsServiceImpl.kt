package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.AtomStatisticsDao
import com.tencent.devops.metrics.service.AtomStatisticsManageService
import com.tencent.metrics.pojo.`do`.AtomBaseTrendInfoDO
import com.tencent.metrics.pojo.`do`.AtomTrendInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomTrendInfoDTO
import com.tencent.metrics.pojo.qo.QueryAtomStatisticsQO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired

class AtomStatisticsServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val atomStatisticsDao: AtomStatisticsDao
): AtomStatisticsManageService {
    override fun queryAtomTrendInfo(queryAtomTrendInfoDTO: QueryAtomTrendInfoDTO): AtomTrendInfoVO {
        val result = atomStatisticsDao.queryAtomTrendInfo(
            dslContext,
            QueryAtomStatisticsQO(
                projectId = queryAtomTrendInfoDTO.projectId,
                queryReq = queryAtomTrendInfoDTO.queryReq,
                errorTypes = queryAtomTrendInfoDTO.errorTypes,
                atomCodes = queryAtomTrendInfoDTO.atomCodes
            )
        )
        val atomTrendInfoDOMap = mutableMapOf<String, List<AtomBaseTrendInfoDO>>()

        result.forEach { record ->
            if (!atomTrendInfoDOMap.containsKey(record.value1())) {
                    val atomTrendInfoList = mutableListOf(
                        AtomBaseTrendInfoDO(
                        successRate = record.value3().toDouble(),
                            statisticsTime = record.value5(),
                            avgCostTime = record.value4()
                        )
                    )
                }
        }
    }
}