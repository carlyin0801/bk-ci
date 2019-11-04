/*
 * Tencent is pleased to support the open source community by making BK-REPO 蓝鲸制品库 available.
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
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.store.service.atom.impl

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.repository.api.ServiceGitRepositoryResource
import com.tencent.devops.repository.pojo.enums.GitAccessLevelEnum
import com.tencent.devops.repository.pojo.enums.TokenTypeEnum
import com.tencent.devops.store.pojo.common.StoreMemberReq
import com.tencent.devops.store.pojo.common.enums.StoreMemberTypeEnum
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class TxAtomMemberServiceImpl : AtomMemberServiceImpl() {

    private val logger = LoggerFactory.getLogger(TxAtomMemberServiceImpl::class.java)

    override fun addRepoMember(storeMemberReq: StoreMemberReq, userId: String, repositoryHashId: String): Result<Boolean> {
        logger.info("addRepoMember storeMemberReq is:$storeMemberReq,userId is:$userId,repositoryHashId is:$repositoryHashId")
        if (repositoryHashId.isNotBlank()) {
            val gitAccessLevel = if (storeMemberReq.type == StoreMemberTypeEnum.ADMIN) GitAccessLevelEnum.MASTER else GitAccessLevelEnum.DEVELOPER
            val addGitProjectMemberResult = client.get(ServiceGitRepositoryResource::class)
                .addGitProjectMember(userId, storeMemberReq.member, repositoryHashId, gitAccessLevel, TokenTypeEnum.PRIVATE_KEY)
            logger.info("addGitProjectMemberResult is:$addGitProjectMemberResult")
            return addGitProjectMemberResult
        }
        return Result(true)
    }

    override fun deleteRepoMember(userId: String, username: String, repositoryHashId: String): Result<Boolean> {
        logger.info("deleteRepoMember userId is:$userId,username is:$username,repositoryHashId is:$repositoryHashId")
        if (repositoryHashId.isNotBlank()) {
            val deleteGitProjectMemberResult = client.get(ServiceGitRepositoryResource::class)
                .deleteGitProjectMember(userId, listOf(username), repositoryHashId, TokenTypeEnum.PRIVATE_KEY)
            logger.info("deleteGitProjectMemberResult is:$deleteGitProjectMemberResult")
            return deleteGitProjectMemberResult
        }
        return Result(true)
    }
}
