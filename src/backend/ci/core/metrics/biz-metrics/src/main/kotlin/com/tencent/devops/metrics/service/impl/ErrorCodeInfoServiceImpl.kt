package com.tencent.devops.metrics.service.impl

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.metrics.dao.ErrorCodeInfoDao
import com.tencent.devops.metrics.service.ErrorCodeInfoManageService
import com.tencent.metrics.pojo.`do`.ErrorCodeInfoDO
import com.tencent.metrics.pojo.dto.QueryErrorCodeInfoDTO
import com.tencent.metrics.pojo.qo.QueryErrorCodeInfoQO
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class ErrorCodeInfoServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val errorCodeInfoDao: ErrorCodeInfoDao
): ErrorCodeInfoManageService {
    override fun getErrorCodeInfo(queryErrorCodeInfoDTO: QueryErrorCodeInfoDTO): Page<ErrorCodeInfoDO> {
        return Page(
            page = queryErrorCodeInfoDTO.page,
            pageSize = queryErrorCodeInfoDTO.pageSize,
            count = errorCodeInfoDao.getErrorCodeInfoCount(
                dslContext,
                QueryErrorCodeInfoQO(
                    queryErrorCodeInfoDTO.errorTypes,
                    queryErrorCodeInfoDTO.page,
                    queryErrorCodeInfoDTO.pageSize
                )
            ),
            records = errorCodeInfoDao.getErrorCodeInfo(
                    dslContext,
                    QueryErrorCodeInfoQO(
                        queryErrorCodeInfoDTO.errorTypes,
                        queryErrorCodeInfoDTO.page,
                        queryErrorCodeInfoDTO.pageSize
                    )
                )
        )

    }
}