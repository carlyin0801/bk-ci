package com.tencent.devops.metrics.service

import com.tencent.devops.common.api.pojo.Page
import com.tencent.metrics.pojo.`do`.AtomBaseInfoDO
import com.tencent.metrics.pojo.`do`.PipelineErrorTypeInfoDO
import com.tencent.metrics.pojo.`do`.PipelineLabelInfoDO
import com.tencent.metrics.pojo.dto.QueryProjectAtomListDTO
import com.tencent.metrics.pojo.dto.QueryProjectPipelineLabelDTO

interface ProjectInfoManageService {

    /**
     * 获取项目下插件列表
     * @param queryProjectInfoDTO 获取项目下信息列表信息传输对象
     */
    fun queryProjectAtomList(queryProjectInfoDTO: QueryProjectAtomListDTO): Page<AtomBaseInfoDO>

    /**
     * 获取项目下流水线标签列表
     */
    fun queryProjectPipelineLabels(queryProjectInfoDTO: QueryProjectPipelineLabelDTO): List<PipelineLabelInfoDO>

    /**
     * 获取项目下流水线异常类型列表
     */
    fun queryPipelineErrorTypes(): List<PipelineErrorTypeInfoDO>
}