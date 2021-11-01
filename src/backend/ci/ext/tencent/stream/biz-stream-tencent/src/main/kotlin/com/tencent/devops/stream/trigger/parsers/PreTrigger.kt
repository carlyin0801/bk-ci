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

package com.tencent.devops.stream.trigger.parsers

import com.tencent.devops.common.ci.v2.enums.gitEventKind.TGitPushOperationKind
import com.tencent.devops.stream.config.StreamPreTriggerConfig
import com.tencent.devops.stream.pojo.GitRequestEvent
import com.tencent.devops.stream.pojo.git.GitCommitRepository
import com.tencent.devops.stream.utils.GitCommonUtils
import com.tencent.devops.stream.v2.service.StreamBasicSettingService
import com.tencent.devops.stream.v2.service.StreamScmService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class PreTrigger @Autowired constructor(
    private val config: StreamPreTriggerConfig,
    private val scmService: StreamScmService,
    private val gitBasicSettingService: StreamBasicSettingService
) {
    companion object {
        private val logger = LoggerFactory.getLogger(PreTrigger::class.java)
        private const val DEVELOPER = 30
    }

    // 开启研发商店插件的ci
    fun enableAtomCi(
        requestEvent: GitRequestEvent,
        repository: GitCommitRepository
    ) {
        with(requestEvent) {
            if (!isCreate(repository)) {
                return
            }

            gitBasicSettingService.getGitCIConf(gitProjectId).let {
                if (it != null && it.enableCi) {
                    return
                }
            }

            val token = scmService.getToken(gitProjectId.toString()).accessToken
            // 因为用户是 devops 所以需要修改
            val realUser = getRealUser(this, token)
            if (realUser.isNullOrBlank()) {
                logger.warn("create from store atom get project members error: no develop user")
                return
            }

            try {
                gitBasicSettingService.initGitCIConf(
                    userId = realUser,
                    projectId = GitCommonUtils.getCiProjectId(gitProjectId),
                    gitProjectId = gitProjectId,
                    enabled = true
                )
            } catch (e: Throwable) {
                logger.error("create from store atom error: ${e.message}")
            }
        }
    }

    private fun getRealUser(requestEvent: GitRequestEvent, token: String): String? {
        val projectMember = scmService.getProjectMembersAllRetry(
            token = token,
            gitProjectId = requestEvent.gitProjectId.toString(),
            page = 1,
            pageSize = 20,
            search = null
        )
        if (projectMember.isNullOrEmpty()) {
            logger.warn("create from store atom get project members error")
            return null
        }
        var realUser: String? = null
        run breaking@{
            projectMember.forEach { member ->
                if (member.accessLevel >= DEVELOPER) {
                    realUser = member.username
                    return@breaking
                }
            }
        }
        return realUser
    }

    private fun GitRequestEvent.isCreate(repository: GitCommitRepository): Boolean {
        if (operationKind != TGitPushOperationKind.CREAT.value || userId != config.username) {
            return false
        }

        if (repository.git_http_url.isNotBlank() && repository.git_http_url.startsWith(config.gitPrefix!!)) {
            return true
        }

        return false
    }
}
