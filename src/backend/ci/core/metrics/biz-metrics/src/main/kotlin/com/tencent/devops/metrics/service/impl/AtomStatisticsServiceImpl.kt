package com.tencent.devops.metrics.service.impl

import com.tencent.devops.metrics.dao.AtomStatisticsDao
import com.tencent.devops.metrics.service.AtomStatisticsManageService
import com.tencent.metrics.pojo.`do`.AtomBaseTrendInfoDO
import com.tencent.metrics.pojo.`do`.AtomTrendInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomStatisticsInfoDTO
import com.tencent.metrics.pojo.qo.QueryAtomStatisticsQO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired

class AtomStatisticsServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val atomStatisticsDao: AtomStatisticsDao
): AtomStatisticsManageService {
    override fun queryAtomTrendInfo(queryAtomTrendInfoDTO: QueryAtomStatisticsInfoDTO): AtomTrendInfoVO {
        val result = atomStatisticsDao.queryAtomTrendInfo(
            dslContext,
            QueryAtomStatisticsQO(
                projectId = queryAtomTrendInfoDTO.projectId,
                queryReq = queryAtomTrendInfoDTO.queryReq,
                errorTypes = queryAtomTrendInfoDTO.errorTypes,
                atomCodes = queryAtomTrendInfoDTO.atomCodes
            )
        )
        val atomTrendInfoMap = mutableMapOf<String, AtomTrendInfoDO>()

        result.forEach { record ->
            if (!atomTrendInfoMap.containsKey(record.value1())) {
                    val atomTrendInfoList = mutableListOf(
                        AtomBaseTrendInfoDO(
                            successRate = record.value3().toDouble(),
                            statisticsTime = record.value5(),
                            avgCostTime = record.value4()
                        )
                    )
                val atomTrendInfoDO = AtomTrendInfoDO(
                    record.value1(),
                    record.value2(),
                    atomTrendInfoList
                )
                atomTrendInfoMap[record.value1()] = atomTrendInfoDO
            } else {
                val atomTrendInfoDO = atomTrendInfoMap[record.value1()]
                atomTrendInfoDO!!.atomTrendInfos.add(
                    AtomBaseTrendInfoDO(
                        successRate = record.value3().toDouble(),
                        avgCostTime = record.value4(),
                        statisticsTime = record.value5()
                    )
                )
            }
        }
        return AtomTrendInfoVO(
            atomTrendInfoMap
        )
    }
}