package com.tencent.devops.metrics.service.impl

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.metrics.dao.AtomFailInfoDao
import com.tencent.devops.metrics.service.AtomFailInfoManageService
import com.tencent.metrics.constant.BK_ERROR_CODE
import com.tencent.metrics.constant.BK_ERROR_COUNT
import com.tencent.metrics.constant.BK_ERROR_MSG
import com.tencent.metrics.constant.BK_ERROR_TYPE
import com.tencent.metrics.constant.BK_QUERY_COUNT_MAX
import com.tencent.metrics.constant.MetricsMessageCode
import com.tencent.metrics.pojo.`do`.AtomErrorCodeStatisticsInfoDO
import com.tencent.metrics.pojo.`do`.AtomFailDetailInfoDO
import com.tencent.metrics.pojo.`do`.ErrorCodeInfoDO
import com.tencent.metrics.pojo.dto.QueryAtomFailInfoDTO
import com.tencent.metrics.pojo.qo.QueryAtomFailInfoQO
import com.tencent.metrics.pojo.vo.AtomErrorCodeStatisticsInfoVO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class AtomFailInfoServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val atomFailInfoDao: AtomFailInfoDao
): AtomFailInfoManageService {

    override fun queryAtomErrorCodeStatisticsInfo(
        queryAtomFailInfoDTO: QueryAtomFailInfoDTO
    ): AtomErrorCodeStatisticsInfoVO {
        val errorCodeStatisticsInfo = atomFailInfoDao.queryAtomErrorCodeStatisticsInfo(
            dslContext,
            QueryAtomFailInfoQO(
                queryAtomFailInfoDTO.projectId,
                queryAtomFailInfoDTO.baseQueryReq,
                queryAtomFailInfoDTO.errorTypes,
                queryAtomFailInfoDTO.errorCodes,
            )
        )
        val errorCodes = mutableListOf<Int>()
        val atomErrorCodeStatisticsInfos = errorCodeStatisticsInfo.map {
            errorCodes.add(it[BK_ERROR_CODE] as Int)
            AtomErrorCodeStatisticsInfoDO(
                ErrorCodeInfoDO(
                    errorType = it[BK_ERROR_TYPE] as Int,
                    errorCode = it[BK_ERROR_CODE] as Int,
                    errorMsg = it[BK_ERROR_MSG] as String
                ),
                errorCount = it[BK_ERROR_COUNT] as Int
            )
        }
        val atomErrorCodeOverviewCount = atomFailInfoDao.queryAtomErrorCodeOverviewCount(
            dslContext,
            QueryAtomFailInfoQO(
                queryAtomFailInfoDTO.projectId,
                queryAtomFailInfoDTO.baseQueryReq,
                queryAtomFailInfoDTO.errorTypes,
                queryAtomFailInfoDTO.errorCodes
            ),
            errorCodes
        )
        atomErrorCodeStatisticsInfos.add(
            AtomErrorCodeStatisticsInfoDO(
                ErrorCodeInfoDO(
                    errorType = 0,
                    errorCode = 0,
                    errorMsg = "其他",
                ),
                errorCount = atomErrorCodeOverviewCount
            )
        )
        return AtomErrorCodeStatisticsInfoVO(
            atomErrorCodeStatisticsInfos
        )
    }

    override fun queryPipelineFailDetailInfo(
        queryAtomFailInfoDTO: QueryAtomFailInfoDTO
    ): Page<AtomFailDetailInfoDO> {
        // 查询符合查询条件的记录数
        val pipelineFailDetailCount = atomFailInfoDao.queryPipelineFailDetailCount(
            dslContext,
            QueryAtomFailInfoQO(
                queryAtomFailInfoDTO.projectId,
                queryAtomFailInfoDTO.baseQueryReq,
                queryAtomFailInfoDTO.errorTypes,
                queryAtomFailInfoDTO.errorCodes
            )
        )
        if (pipelineFailDetailCount > BK_QUERY_COUNT_MAX) {
            throw ErrorCodeException(
                errorCode = MetricsMessageCode.QUERY_DETAILS_COUNT_BEYOND
            )
        }
        val result = atomFailInfoDao.queryPipelineFailDetailInfo(
            dslContext,
            QueryAtomFailInfoQO(
                queryAtomFailInfoDTO.projectId,
                queryAtomFailInfoDTO.baseQueryReq,
                queryAtomFailInfoDTO.errorTypes,
                queryAtomFailInfoDTO.errorCodes,
                page = queryAtomFailInfoDTO.page,
                pageSize = queryAtomFailInfoDTO.pageSize
            )
        )
        return Page(
            page = queryAtomFailInfoDTO.page!!,
            pageSize = queryAtomFailInfoDTO.pageSize!!,
            count = pipelineFailDetailCount,
            records = result
        )
    }
}