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

package com.tencent.devops.stream.service.transfer

import com.tencent.devops.common.api.exception.OauthForbiddenException
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.sdk.github.request.CreateOrUpdateFileContentsRequest
import com.tencent.devops.common.sdk.github.request.GetRepositoryContentRequest
import com.tencent.devops.common.sdk.github.request.GetRepositoryRequest
import com.tencent.devops.common.sdk.github.request.ListBranchesRequest
import com.tencent.devops.common.sdk.github.request.ListCommitRequest
import com.tencent.devops.common.sdk.github.request.ListOrganizationsRequest
import com.tencent.devops.common.sdk.github.request.ListRepositoryCollaboratorsRequest
import com.tencent.devops.common.sdk.github.request.SearchRepositoriesRequest
import com.tencent.devops.repository.api.ServiceOauthResource
import com.tencent.devops.repository.api.github.ServiceGithubBranchResource
import com.tencent.devops.repository.api.github.ServiceGithubCommitsResource
import com.tencent.devops.repository.api.github.ServiceGithubOrganizationResource
import com.tencent.devops.repository.api.github.ServiceGithubRepositoryResource
import com.tencent.devops.repository.pojo.AuthorizeResult
import com.tencent.devops.repository.pojo.enums.RedirectUrlTypeEnum
import com.tencent.devops.scm.enums.GitAccessLevelEnum
import com.tencent.devops.stream.dao.StreamBasicSettingDao
import com.tencent.devops.stream.pojo.StreamCommitInfo
import com.tencent.devops.stream.pojo.StreamCreateFileInfo
import com.tencent.devops.stream.pojo.StreamGitGroup
import com.tencent.devops.stream.pojo.StreamGitMember
import com.tencent.devops.stream.pojo.StreamGitProjectBaseInfoCache
import com.tencent.devops.stream.pojo.StreamGitProjectInfoWithProject
import com.tencent.devops.stream.pojo.StreamProjectGitInfo
import com.tencent.devops.stream.pojo.enums.StreamBranchesOrder
import com.tencent.devops.stream.pojo.enums.StreamProjectsOrder
import com.tencent.devops.stream.pojo.enums.StreamSortAscOrDesc
import com.tencent.devops.stream.service.StreamGitTransferService
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import java.util.Base64

class StreamGithubTransferService @Autowired constructor(
    private val dslContext: DSLContext,
    private val client: Client,
    private val streamBasicSettingDao: StreamBasicSettingDao
) : StreamGitTransferService {

    // github 组织白名单列表
    @Value("\${github.orgWhite:}")
    private var githubOrgWhite: String = ""

    // gitProjectId在github中必须为项目名字
    override fun getGitProjectCache(
        gitProjectId: String,
        useAccessToken: Boolean,
        userId: String?,
        accessToken: String?
    ): StreamGitProjectBaseInfoCache {
        return client.get(ServiceGithubRepositoryResource::class).getRepository(
            request = GetRepositoryRequest(
               id = gitProjectId.toLong()
            ),
            userId = userId!!
        ).data?.let {
            StreamGitProjectBaseInfoCache(
                gitProjectId = it.id.toString(),
                gitHttpUrl = it.cloneUrl,
                homepage = it.homepage,
                pathWithNamespace = it.fullName,
                defaultBranch = it.defaultBranch
            )
        } ?: throw OauthForbiddenException(
            message = "get git project($gitProjectId) info error|useAccessToken=$useAccessToken"
        )
    }

    override fun getGitProjectInfo(gitProjectId: String, userId: String?): StreamGitProjectInfoWithProject? {
        val realUserId = userId ?: try {
            streamBasicSettingDao.getSetting(dslContext, gitProjectId.toLong())?.enableUserId ?: return null
        } catch (e: NumberFormatException) {
            streamBasicSettingDao.getSettingByPathWithNameSpace(dslContext, gitProjectId)?.enableUserId ?: return null
        }
        return client.get(ServiceGithubRepositoryResource::class).getRepository(
            request = GetRepositoryRequest(
                id = gitProjectId.toLong()
            ),
            userId = realUserId
        ).data?.let {
            StreamGitProjectInfoWithProject(
                gitProjectId = it.id,
                name = it.name,
                homepage = it.htmlUrl,
                gitHttpUrl = it.cloneUrl,
                gitHttpsUrl = it.cloneUrl,
                gitSshUrl = it.sshUrl,
                nameWithNamespace = it.fullName,
                pathWithNamespace = it.fullName,
                defaultBranch = it.defaultBranch,
                description = it.description,
                avatarUrl = it.owner.avatarUrl,
                routerTag = null
            )
        }
    }

    override fun getYamlContent(
        gitProjectId: String,
        userId: String,
        fileName: String,
        ref: String
    ): String {
        return client.get(ServiceGithubRepositoryResource::class).getRepositoryContent(
            request = GetRepositoryContentRequest(
                id = gitProjectId.toLong(),
                path = fileName,
                ref = ref
            ),
            userId = userId
        ).data?.getDecodedContentAsString() ?: ""
    }

    override fun getProjectList(
        userId: String,
        page: Int?,
        pageSize: Int?,
        search: String?,
        orderBy: StreamProjectsOrder?,
        sort: StreamSortAscOrDesc?,
        owned: Boolean?,
        minAccessLevel: GitAccessLevelEnum?
    ): List<StreamProjectGitInfo>? {
        // search  owned  minAccessLevel 参数暂时没使用
        val request = SearchRepositoriesRequest(
            page = page ?: 1,
            perPage = pageSize ?: 30,
            sort = sort?.value
        )
        if (!search.isNullOrBlank()) {
            request.name(search)
        }
        request.org(githubOrgWhite)

        return client.get(ServiceGithubRepositoryResource::class).searchRepositories(
            request = request,
            userId = userId
        ).data?.map { StreamProjectGitInfo(it) }
    }

    override fun getProjectMember(
        gitProjectId: String,
        userId: String,
        page: Int?,
        pageSize: Int?,
        search: String?
    ): List<StreamGitMember> {
        val request = ListRepositoryCollaboratorsRequest(
            id = gitProjectId.toLong(),
            page = page ?: 1,
            perPage = pageSize ?: 30
        )
        return client.get(ServiceGithubRepositoryResource::class).listRepositoryCollaborators(
            request = request,
            userId = userId
        ).data!!.map {
            // state 属性无
            StreamGitMember(
                id = it.id,
                username = it.login,
                state = ""
            )
        }
    }

    override fun isOAuth(
        userId: String,
        redirectUrlType: RedirectUrlTypeEnum?,
        redirectUrl: String?,
        gitProjectId: Long?,
        refreshToken: Boolean?
    ): Result<AuthorizeResult> {
        // todo 未实现
        return client.get(ServiceOauthResource::class).isOAuth(
            userId = userId,
            redirectUrlType = redirectUrlType,
            redirectUrl = redirectUrl,
            gitProjectId = gitProjectId,
            refreshToken = refreshToken
        )
    }

    override fun getCommits(
        userId: String,
        gitProjectId: Long,
        filePath: String?,
        branch: String?,
        since: String?,
        until: String?,
        page: Int?,
        perPage: Int?
    ): List<StreamCommitInfo>? {
        return client.get(ServiceGithubCommitsResource::class).listCommits(
            request = ListCommitRequest(
                id = gitProjectId,
                page = page ?: 1,
                perPage = perPage ?: 30
            ),
            userId = userId
        // todo commit 信息严重不足
        ).data?.map { StreamCommitInfo(it) }
    }

    override fun createNewFile(
        userId: String,
        gitProjectId: String,
        streamCreateFile: StreamCreateFileInfo
    ): Boolean {
        client.get(ServiceGithubRepositoryResource::class).createOrUpdateFile(
            request = with(streamCreateFile) {
                CreateOrUpdateFileContentsRequest(
                    id = gitProjectId.toLong(),
                    message = commitMessage,
                    content = Base64.getEncoder().encodeToString(content.toByteArray()),
                    path = filePath,
                    branch = branch
                )
            },
            userId = userId
        )
        return true
    }

    override fun getProjectBranches(
        userId: String,
        gitProjectId: String,
        page: Int?,
        pageSize: Int?,
        search: String?,
        orderBy: StreamBranchesOrder?,
        sort: StreamSortAscOrDesc?
    ): List<String>? {
        return client.get(ServiceGithubBranchResource::class).listBranch(
            request = ListBranchesRequest(
                id = gitProjectId.toLong(),
                page = page ?: 1,
                perPage = pageSize ?: 30
            ),
            userId = userId
        ).data?.map { it.name }
    }

    override fun getProjectGroupsList(
        userId: String,
        page: Int,
        pageSize: Int
    ): List<StreamGitGroup>? {
        return client.get(ServiceGithubOrganizationResource::class).listOrganizations(
            request = ListOrganizationsRequest(
                page = page,
                perPage = pageSize
            ),
            userId = userId
        ).data?.ifEmpty { null }?.map {
            StreamGitGroup(it)
        }
    }
}
