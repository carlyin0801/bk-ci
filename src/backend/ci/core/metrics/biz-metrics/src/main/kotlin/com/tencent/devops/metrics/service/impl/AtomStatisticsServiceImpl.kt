package com.tencent.devops.metrics.service.impl

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.metrics.dao.AtomStatisticsDao
import com.tencent.devops.metrics.service.AtomStatisticsManageService
import com.tencent.metrics.constant.*
import com.tencent.metrics.pojo.`do`.*
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
        val queryAtomExecuteStatisticsCount =
            atomStatisticsDao.queryAtomExecuteStatisticsInfoCount(
                dslContext,
                QueryAtomStatisticsQO(
                    projectId = queryAtomTrendInfoDTO.projectId,
                    baseQueryReq = queryAtomTrendInfoDTO.baseQueryReq,
                    errorTypes = queryAtomTrendInfoDTO.errorTypes,
                    atomCodes = queryAtomTrendInfoDTO.atomCodes
                )
            )
        if (queryAtomExecuteStatisticsCount > BK_QUERY_COUNT_MAX) {
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
        val queryAtomCodes = atomStatisticResult.map { it[BK_ATOM_CODE] as String }
        val queryAtomFailStatisticsInfo = atomStatisticsDao.queryAtomFailStatisticsInfo(
            dslContext,
            QueryAtomStatisticsQO(
                projectId = queryAtomTrendInfoDTO.projectId,
                baseQueryReq = queryAtomTrendInfoDTO.baseQueryReq,
                errorTypes = queryAtomTrendInfoDTO.errorTypes,
                atomCodes = queryAtomCodes
            )
        )
        val headerInfo = mutableMapOf<String, String>()
        val atomFailInfos = mutableMapOf<String, MutableList<AtomFailInfoDO>>()
        queryAtomFailStatisticsInfo.map {
            val atomCode = it[BK_ATOM_CODE].toString()
            if (!headerInfo.containsKey(it[BK_ERROR_TYPE].toString())) {
                headerInfo.put(it[BK_ERROR_TYPE].toString(), it[BK_ERROR_NAME].toString())
            }
            if (!atomFailInfos.containsKey(atomCode)) {
                atomFailInfos.put(
                    atomCode,
                    mutableListOf(
                        AtomFailInfoDO(
                            errorType = it[BK_ERROR_TYPE] as Int,
                            name = it[BK_ERROR_NAME] as String,
                            errorCount = (it[BK_ERROR_COUNT_SUM] as BigDecimal).toInt()
                        )
                    )
                )
            } else {
                atomFailInfos[atomCode]!!.add(
                    AtomFailInfoDO(
                        errorType = it[BK_ERROR_TYPE] as Int,
                        name = it[BK_ERROR_NAME] as String,
                        errorCount = (it[BK_ERROR_COUNT_SUM] as BigDecimal).toInt()
                    )
                )
            }
        }

        val records = atomStatisticResult.map {
            val totalExecuteCount = (it[BK_TOTAL_EXECUTE_COUNT_SUM] as BigDecimal).toInt()
            val successExecuteCount = (it[BK_SUCESS_EXECUTE_COUNT_SUM] as BigDecimal).toInt()
            AtomExecutionStatisticsInfoDO(
                projectId = queryAtomTrendInfoDTO.projectId,
                atomBaseInfo = AtomBaseInfoDO(
                    atomCode = it[BK_ATOM_CODE] as String,
                    atomName = it[BK_ATOM_NAME] as String
                ),
                classifyCode = it[BK_CLASSIFY_CODE] as String,
                totalExecuteCount = totalExecuteCount,
                successExecuteCount = successExecuteCount,
                successRate = String.format("%.2f", successExecuteCount.toDouble() * 100 / totalExecuteCount)
                    .toDouble(),
                atomFailInfos = atomFailInfos[it[BK_ATOM_CODE] as String]?.toList()?: emptyList()
            )
        }

        return ListPageVO(
            count = queryAtomExecuteStatisticsCount,
            page = queryAtomTrendInfoDTO.page!!,
            pageSize = queryAtomTrendInfoDTO.pageSize!!,
            headerInfo = headerInfo,
            records = records
        )

    }
}