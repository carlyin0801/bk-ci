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

package com.tencent.devops.sharding.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.sharding.api.ProcessResource
import com.tencent.devops.sharding.pojo.process.PipelineInfo
import com.tencent.devops.sharding.pojo.process.PipelineUser
import com.tencent.devops.sharding.pojo.process.PipelineUserItem
import com.tencent.devops.sharding.service.process.PipelineInfoService
import com.tencent.devops.sharding.service.process.PipelineManageService
import com.tencent.devops.sharding.service.process.PipelineUserService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ProcessResourceImpl @Autowired constructor(
    private val pipelineInfoService: PipelineInfoService,
    private val pipelineUserService: PipelineUserService,
    private val pipelineManageService: PipelineManageService
) : ProcessResource {

    override fun addPipelineInfo(userId: String, pipelineInfo: PipelineInfo): Result<Boolean> {
        return Result(pipelineInfoService.addPipelineInfo(pipelineInfo))
    }

    override fun getPipelineIdListByProjectId(projectId: String): Result<List<PipelineInfo>?> {
        return Result(pipelineInfoService.getPipelineInfoListByProjectId(projectId))
    }

    override fun getPipelineInfoByPipelineId(pipelineId: String): Result<PipelineInfo?> {
        return Result(pipelineInfoService.getPipelineInfoByPipelineId(pipelineId))
    }

    override fun addPipelineUser(userId: String, pipelineUser: PipelineUser): Result<Boolean> {
        return Result(pipelineUserService.addPipelineUser(pipelineUser))
    }

    override fun getPipelineUserListByProjectId(projectId: String): Result<List<PipelineUser>?> {
        return Result(pipelineUserService.getPipelineUserListByProjectId(projectId))
    }

    override fun getPipelineUserByPipelineId(pipelineId: String): Result<PipelineUser?> {
        return Result(pipelineUserService.getPipelineUserByPipelineId(pipelineId))
    }

    override fun getPipelineUserDetailListByProjectId(
        projectId: String,
        pipelineId: String
    ): Result<List<PipelineUserItem>?> {
        return Result(pipelineManageService.getPipelineUserList(projectId, pipelineId))
    }
}
