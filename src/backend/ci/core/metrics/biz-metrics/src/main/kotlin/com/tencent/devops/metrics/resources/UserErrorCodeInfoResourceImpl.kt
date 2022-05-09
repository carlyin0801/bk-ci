package com.tencent.devops.metrics.resources

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.metrics.service.ErrorCodeInfoManageService
import com.tencent.metrics.api.UserErrorCodeInfoResource
import com.tencent.metrics.pojo.`do`.ErrorCodeInfoDO
import com.tencent.metrics.pojo.dto.QueryErrorCodeInfoDTO

class UserErrorCodeInfoResourceImpl constructor(
    private val errorCodeInfoManageService: ErrorCodeInfoManageService
): UserErrorCodeInfoResource {
    override fun getErrorCodeInfo(
        projectId: String,
        userId: String,
        errorTypes: List<Int>?,
        page: Int,
        pageSize: Int
    ): Result<Page<ErrorCodeInfoDO>> {
        return Result(
            errorCodeInfoManageService.getErrorCodeInfo(
                QueryErrorCodeInfoDTO(
                    errorTypes,
                    page,
                    pageSize
                )
            )
        )
    }
}