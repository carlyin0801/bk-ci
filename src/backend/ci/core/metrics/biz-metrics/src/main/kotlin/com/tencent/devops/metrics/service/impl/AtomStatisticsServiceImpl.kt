package com.tencent.devops.metrics.service.impl

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.metrics.dao.AtomStatisticsDao
import com.tencent.devops.metrics.service.AtomStatisticsManageService
import com.tencent.metrics.constant.BK_ATOM_CODE
import com.tencent.metrics.constant.BK_ATOM_NAME
import com.tencent.metrics.constant.BK_AVG_COST_TIME
import com.tencent.metrics.constant.BK_QUERY_COUNT_MAX
import com.tencent.metrics.constant.BK_STATISTICS_TIME
import com.tencent.metrics.constant.BK_SUCCESS_RATE
import com.tencent.metrics.constant.MetricsMessageCode
import com.tencent.metrics.pojo.`do`.AtomBaseTrendInfoDO
import com.tencent.metrics.pojo.`do`.AtomExecutionStatisticsInfoDO
import com.tencent.metrics.pojo.`do`.AtomTrendInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomStatisticsInfoDTO
import com.tencent.metrics.pojo.qo.QueryAtomStatisticsQO
import com.tencent.metrics.pojo.vo.AtomTrendInfoVO
import com.tencent.metrics.pojo.vo.ListPageVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import java.math.BigDecimal
import java.time.LocalDateTime

class AtomStatisticsServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val atomStatisticsDao: AtomStatisticsDao
): AtomStatisticsManageService {
    override fun queryAtomTrendInfo(queryAtomTrendInfoDTO: QueryAtomStatisticsInfoDTO): AtomTrendInfoVO {
        //  查询插件趋势信息
        val result = atomStatisticsDao.queryAtomTrendInfo(
            dslContext,
            QueryAtomStatisticsQO(
                projectId = queryAtomTrendInfoDTO.projectId,
                baseQueryReq = queryAtomTrendInfoDTO.baseQueryReq,
                errorTypes = queryAtomTrendInfoDTO.errorTypes,
                atomCodes = queryAtomTrendInfoDTO.atomCodes
            )
        )
        val atomTrendInfoMap = mutableMapOf<String, AtomTrendInfoDO>()
        result.forEach { record ->
            if (!atomTrendInfoMap.containsKey(record[BK_ATOM_CODE] as String)) {
                    val atomTrendInfoList = mutableListOf(
                        AtomBaseTrendInfoDO(
                            successRate = (record[BK_SUCCESS_RATE] as BigDecimal).toDouble(),
                            statisticsTime = record[BK_STATISTICS_TIME] as LocalDateTime,
                            avgCostTime = record[BK_AVG_COST_TIME] as Long
                        )
                    )
                val atomTrendInfoDO = AtomTrendInfoDO(
                    atomCode = record[BK_ATOM_CODE] as String,
                   atomName = record[BK_ATOM_NAME] as String,
                    atomTrendInfoList
                )
                atomTrendInfoMap[record[BK_ATOM_CODE] as String] = atomTrendInfoDO
            } else {
                val atomTrendInfoDO = atomTrendInfoMap[record[BK_ATOM_CODE] as String]
                atomTrendInfoDO!!.atomTrendInfos.add(
                    AtomBaseTrendInfoDO(
                        successRate = (record[BK_SUCCESS_RATE] as BigDecimal).toDouble(),
                        avgCostTime = record[BK_AVG_COST_TIME] as Long,
                        statisticsTime = record[BK_STATISTICS_TIME] as LocalDateTime
                    )
                )
            }
        }
        return AtomTrendInfoVO(
            atomTrendInfoMap.values.toList()
        )
    }

    override fun queryAtomExecuteStatisticsInfo(
        queryAtomTrendInfoDTO: QueryAtomStatisticsInfoDTO
    ): ListPageVO<AtomExecutionStatisticsInfoDO> {
        // 查询符合查询条件的记录数
        val queryAtomExecuteStatisticsInfoCount =
            atomStatisticsDao.queryAtomExecuteStatisticsInfoCount(
                dslContext,
                QueryAtomStatisticsQO(
                    projectId = queryAtomTrendInfoDTO.projectId,
                    baseQueryReq = queryAtomTrendInfoDTO.baseQueryReq,
                    errorTypes = queryAtomTrendInfoDTO.errorTypes,
                    atomCodes = queryAtomTrendInfoDTO.atomCodes
                )
            )
        if (queryAtomExecuteStatisticsInfoCount > BK_QUERY_COUNT_MAX) {
            throw ErrorCodeException(
                errorCode = MetricsMessageCode.QUERY_DETAILS_COUNT_BEYOND
            )
        }
        val atomStatisticResult = atomStatisticsDao.queryAtomExecuteStatisticsInfo(
            dslContext,
            QueryAtomStatisticsQO(
                projectId = queryAtomTrendInfoDTO.projectId,
                baseQueryReq = queryAtomTrendInfoDTO.baseQueryReq,
                errorTypes = queryAtomTrendInfoDTO.errorTypes,
                atomCodes = queryAtomTrendInfoDTO.atomCodes
            )
        )



    }
}