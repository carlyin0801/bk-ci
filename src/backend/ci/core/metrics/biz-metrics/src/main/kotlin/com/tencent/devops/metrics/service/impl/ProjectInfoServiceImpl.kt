package com.tencent.devops.metrics.service.impl

import com.github.benmanes.caffeine.cache.Caffeine
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.metrics.dao.ProjectInfoDao
import com.tencent.devops.metrics.service.ProjectInfoManageService
import com.tencent.metrics.pojo.*
import com.tencent.metrics.pojo.`do`.AtomBaseInfoDO
import com.tencent.metrics.pojo.`do`.PipelineErrorTypeInfoDO
import com.tencent.metrics.pojo.`do`.PipelineLabelInfoDO
import com.tencent.metrics.pojo.dto.QueryProjectAtomListDTO
import com.tencent.metrics.pojo.dto.QueryProjectPipelineLabelDTO
import com.tencent.metrics.pojo.qo.QueryProjectInfoQO
import org.apache.commons.collections4.ListUtils
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.TimeUnit

@Service
class ProjectInfoServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val projectInfoDao: ProjectInfoDao
): ProjectInfoManageService {

    private val atomCodeCache = Caffeine.newBuilder()
        .maximumSize(5000)
        .expireAfterWrite(getTodayStartTime() - System.currentTimeMillis(), TimeUnit.MILLISECONDS)
        .build<String, List<List<AtomBaseInfoDO>>>()

    override fun queryProjectAtomList(queryProjectAtomList: QueryProjectAtomListDTO): Page<AtomBaseInfoDO> {
        // 从缓存中查找插件属性信息
        val atomCodesList = atomCodeCache.getIfPresent(queryProjectAtomList.projectId)
        if (!atomCodesList.isNullOrEmpty()) {
            // 无需从db查数据则直接返回结果数据
            var count = 0
            atomCodesList.forEach{ count += it.size}
            return Page(
                page = queryProjectAtomList.page,
                pageSize = queryProjectAtomList.pageSize,
                count = count.toLong(),
                records = atomCodesList[queryProjectAtomList.page - 1]
            )
        } else {

            val result = projectInfoDao.queryProjectAtomList(
                    dslContext,
                queryProjectAtomList.projectId
                )
            if (!result.isNullOrEmpty()) {
                val partition = ListUtils.partition(result, queryProjectAtomList.pageSize)
                atomCodeCache.put(queryProjectAtomList.projectId, partition)
                var count = 0
                partition.forEach{ count += it.size}
                return Page(
                    page = queryProjectAtomList.page,
                    pageSize = queryProjectAtomList.pageSize,
                    count = count.toLong(),
                    records = partition[queryProjectAtomList.page - 1]
                )
            }
            return Page(
                page = queryProjectAtomList.page,
                pageSize = 10,
                count = 0L,
                records = emptyList()
            )
        }
    }

    override fun queryProjectPipelineLabels(queryProjectPipelineLabelDTO: QueryProjectPipelineLabelDTO): List<PipelineLabelInfoDO> {
        return projectInfoDao.queryProjectPipelineLabels(
            dslContext,
            QueryProjectInfoQO(
                projectId = queryProjectPipelineLabelDTO.projectId,
                pipelineIds = queryProjectPipelineLabelDTO.pipelineIds,
                page = queryProjectPipelineLabelDTO.page,
                pageSize = queryProjectPipelineLabelDTO.pageSize
            )
        )
    }

    override fun queryPipelineErrorTypes(): List<PipelineErrorTypeInfoDO> {
        return projectInfoDao.queryPipelineErrorTypes(
            dslContext
        )
    }

    /**
     * 获取当天的最晚时间戳
     *
     * @return 当天的最晚时间戳
     */
    fun getTodayStartTime(): Long {
        //设置时区
        val calendar: Calendar = Calendar.getInstance(TimeZone.getTimeZone("GMT+8"))
        calendar.set(Calendar.HOUR_OF_DAY, 24)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        return calendar.timeInMillis
    }

}