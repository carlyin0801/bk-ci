package com.tencent.devops.metrics.service

import com.tencent.devops.common.api.pojo.Page
import com.tencent.metrics.pojo.`do`.ErrorCodeInfoDO
import com.tencent.metrics.pojo.dto.QueryErrorCodeInfoDTO

interface ErrorCodeInfoManageService {

    /**
     * 获取错误码列表
     *@param queryErrorCodeInfoDTO 查询错误码信息传输对象
     * @return 错误码信息列表视图
     */
    fun getErrorCodeInfo(queryErrorCodeInfoDTO: QueryErrorCodeInfoDTO) : Page<ErrorCodeInfoDO>
}