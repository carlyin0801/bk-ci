/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.sharding.service.process

import com.tencent.devops.sharding.dao.process.PipelineInfoDao
import com.tencent.devops.sharding.pojo.process.PipelineInfo
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PipelineInfoService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineInfoDao: PipelineInfoDao
) {

    fun addPipelineInfo(
        pipelineInfo: PipelineInfo
    ): Boolean {
        pipelineInfoDao.addPipelineInfo(
            dslContext = dslContext,
            projectId = pipelineInfo.projectId,
            pipelineId = pipelineInfo.pipelineId,
            pipelineName = pipelineInfo.pipelineName,
            pipelineDesc = pipelineInfo.pipelineDesc
        )
        return true
    }

    fun getPipelineInfoListByProjectId(
        projectId: String
    ): List<PipelineInfo>? {
        val pipelineRecords = pipelineInfoDao.getPipelineInfoByProjectId(
            dslContext = dslContext,
            projectId = projectId
        )
        val dataList = mutableListOf<PipelineInfo>()
        pipelineRecords?.forEach {
            dataList.add(
                PipelineInfo(
                    projectId = it.projectId,
                    pipelineId = it.pipelineId,
                    pipelineName = it.pipelineName,
                    pipelineDesc = it.pipelineDesc
                )
            )
        }
        return dataList
    }

    fun getPipelineInfoByPipelineId(
        pipelineId: String
    ): PipelineInfo? {
        val pipelineRecord = pipelineInfoDao.getPipelineInfoByPipelineId(
            dslContext = dslContext,
            pipelineId = pipelineId
        )
        return if (pipelineRecord != null) {
            PipelineInfo(
                projectId = pipelineRecord.projectId,
                pipelineId = pipelineRecord.pipelineId,
                pipelineName = pipelineRecord.pipelineName,
                pipelineDesc = pipelineRecord.pipelineDesc
            )
        } else {
            null
        }
    }
}
