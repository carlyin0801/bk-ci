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

import com.tencent.devops.sharding.dao.process.PipelineUserDao
import com.tencent.devops.sharding.pojo.process.PipelineUser
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class PipelineUserService @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineUserDao: PipelineUserDao
) {

    fun addPipelineUser(
        pipelineUser: PipelineUser
    ): Boolean {
        val currentTime = LocalDateTime.now()
        pipelineUserDao.addPipelineInfo(
            dslContext = dslContext,
            projectId = pipelineUser.projectId,
            pipelineId = pipelineUser.pipelineId,
            currentTime = currentTime,
            userId = pipelineUser.userId
        )
        return true
    }

    fun getPipelineUserListByProjectId(
        projectId: String
    ): List<PipelineUser>? {
        val pipelineUserRecords = pipelineUserDao.getPipelineUserByProjectId(
            dslContext = dslContext,
            projectId = projectId
        )
        val dataList = mutableListOf<PipelineUser>()
        pipelineUserRecords?.forEach {
            dataList.add(
                PipelineUser(
                    projectId = it.projectId,
                    pipelineId = it.pipelineId,
                    userId = it.createUser,
                    createTime = it.createTime,
                    updateTime = it.updateTime
                )
            )
        }
        return dataList
    }

    fun getPipelineUserByPipelineId(
        pipelineId: String
    ): PipelineUser? {
        val pipelineUserRecord = pipelineUserDao.getPipelineUserByPipelineId(
            dslContext = dslContext,
            pipelineId = pipelineId
        )
        return if (pipelineUserRecord != null) {
            PipelineUser(
                projectId = pipelineUserRecord.projectId,
                pipelineId = pipelineUserRecord.pipelineId,
                userId = pipelineUserRecord.createUser
            )
        } else {
            null
        }
    }
}
