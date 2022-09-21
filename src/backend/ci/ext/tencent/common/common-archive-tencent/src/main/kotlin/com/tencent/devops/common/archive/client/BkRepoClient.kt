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

package com.tencent.devops.common.archive.client

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonTypeRef
import com.fasterxml.jackson.module.kotlin.readValue
import com.tencent.bkrepo.common.api.pojo.Page
import com.tencent.bkrepo.common.api.pojo.Response
import com.tencent.bkrepo.common.artifact.pojo.RepositoryCategory
import com.tencent.bkrepo.common.artifact.pojo.RepositoryType
import com.tencent.bkrepo.common.query.enums.OperationType
import com.tencent.bkrepo.common.query.model.PageLimit
import com.tencent.bkrepo.common.query.model.QueryModel
import com.tencent.bkrepo.common.query.model.Rule
import com.tencent.bkrepo.common.query.model.Sort
import com.tencent.bkrepo.generic.pojo.FileInfo
import com.tencent.bkrepo.generic.pojo.TemporaryAccessToken
import com.tencent.bkrepo.generic.pojo.TemporaryAccessUrl
import com.tencent.bkrepo.repository.pojo.metadata.UserMetadataSaveRequest
import com.tencent.bkrepo.repository.pojo.node.NodeDetail
import com.tencent.bkrepo.repository.pojo.node.NodeInfo
import com.tencent.bkrepo.repository.pojo.node.NodeSizeInfo
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeMoveCopyRequest
import com.tencent.bkrepo.repository.pojo.node.user.UserNodeRenameRequest
import com.tencent.bkrepo.repository.pojo.project.UserProjectCreateRequest
import com.tencent.bkrepo.repository.pojo.repo.RepoCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordCreateRequest
import com.tencent.bkrepo.repository.pojo.share.ShareRecordInfo
import com.tencent.bkrepo.repository.pojo.token.TemporaryTokenCreateRequest
import com.tencent.bkrepo.repository.pojo.token.TokenType
import com.tencent.devops.common.api.auth.AUTH_HEADER_DEVOPS_PROJECT_ID
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.exception.RemoteServiceException
import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.archive.config.BkRepoConfig
import com.tencent.devops.common.archive.constant.REPO_CUSTOM
import com.tencent.devops.common.archive.constant.REPO_LOG
import com.tencent.devops.common.archive.constant.REPO_PIPELINE
import com.tencent.devops.common.archive.constant.REPO_REPORT
import com.tencent.devops.common.archive.pojo.ArtifactorySearchParam
import com.tencent.devops.common.archive.pojo.BkRepoFile
import com.tencent.devops.common.archive.pojo.QueryData
import com.tencent.devops.common.archive.pojo.QueryNodeInfo
import com.tencent.devops.common.archive.util.PathUtil
import com.tencent.devops.common.archive.util.STREAM_BUFFER_SIZE
import com.tencent.devops.common.archive.util.closeQuietly
import com.tencent.devops.common.service.config.CommonConfig
import com.tencent.devops.common.service.utils.HomeHostUtil
import okhttp3.Credentials
import okhttp3.Headers
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import okio.BufferedSink
import org.slf4j.LoggerFactory
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLEncoder
import java.nio.file.FileSystems
import java.nio.file.Paths

class BkRepoClient constructor(
    private val objectMapper: ObjectMapper,
    private val commonConfig: CommonConfig,
    private val bkRepoConfig: BkRepoConfig
) {
    private fun getGatewayUrl(): String {
        return HomeHostUtil.getHost(commonConfig.devopsHostGateway!!)
    }

    fun createBkRepoResource(userId: String, projectId: String): Boolean {
        val logRepoCredentialsKey = bkRepoConfig.logRepoCredentialsKey.ifBlank { null }
        return try {
            createProject(userId, projectId)
            createGenericRepo(userId, projectId, REPO_PIPELINE)
            createGenericRepo(userId, projectId, REPO_CUSTOM)
            createGenericRepo(userId, projectId, REPO_REPORT)
            createGenericRepo(userId, projectId, REPO_LOG, logRepoCredentialsKey)
            true
        } catch (e: Exception) {
            logger.error("BKSystemErrorMonitor|BK_REPO|create repo resource error", e)
            false
        }
    }

    private fun createProject(userId: String, projectId: String) {
        logger.info("createProject, userId: $userId, projectId: $projectId")
        val requestData = UserProjectCreateRequest(
            name = projectId,
            displayName = projectId,
            description = projectId
        )
        val request = Request.Builder()
            .url("${getGatewayUrl()}/bkrepo/api/service/repository/api/project")
            .header(BK_REPO_UID, userId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestData)
                )
            )
            .build()
        doRequest(request).resolveResponse<Response<Void>>(ERROR_PROJECT_EXISTED)
    }

    private fun createGenericRepo(
        userId: String,
        projectId: String,
        repoName: String,
        storageCredentialsKey: String? = null
    ) {
        logger.info("createRepo, userId: $userId, projectId: $projectId, repoName: $repoName")
        val requestData = RepoCreateRequest(
            category = RepositoryCategory.LOCAL,
            name = repoName,
            projectId = projectId,
            type = RepositoryType.GENERIC,
            public = false,
            description = "storage for devops ci $repoName",
            storageCredentialsKey = storageCredentialsKey
        )
        val request = Request.Builder()
            .url("${getGatewayUrl()}/bkrepo/api/service/repository/api/repo")
            .header(BK_REPO_UID, userId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestData)
                )
            )
            .build()
        doRequest(request).resolveResponse<Response<Void>>(ERROR_REPO_EXISTED)
    }

    fun getFileSize(userId: String, projectId: String, repoName: String, path: String): NodeSizeInfo {
        logger.info("getFileSize, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/size/$projectId/$repoName/$path"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .get()
            .build()
        return doRequest(request).resolveResponse<Response<NodeSizeInfo>>()!!.data!!
    }

    fun setMetadata(userId: String, projectId: String, repoName: String, path: String, metadata: Map<String, String>) {
        logger.info(
            "setMetadata, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path," +
                " metadata: $metadata"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/metadata/$projectId/$repoName/$path"
        val requestData = UserMetadataSaveRequest(
            metadata = metadata
        )
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestData)
                )
            ).build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun listMetadata(userId: String, projectId: String, repoName: String, path: String): Map<String, String> {
        logger.info("listMetadata, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/metadata/$projectId/$repoName/$path"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .get()
            .build()
        return doRequest(request).resolveResponse<Response<Map<String, String>>>()!!.data!!
    }

    @Deprecated(message = "api已废弃", replaceWith = ReplaceWith("listFilePage"))
    fun listFile(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        includeFolders: Boolean = false,
        deep: Boolean = false
    ): List<FileInfo> {
        logger.info(
            "listFile, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path," +
                    " includeFolders: $includeFolders, deep: $deep"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/generic/list/$projectId/$repoName/$path" +
                "?deep=$deep&includeFolder=$includeFolders"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .get()
            .build()
        return doRequest(request).resolveResponse<Response<List<FileInfo>>>()!!.data!!
    }

    fun listFilePage(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        includeFolders: Boolean = false,
        deep: Boolean = false,
        page: Int,
        pageSize: Int
    ): Page<NodeInfo> {
        logger.info(
            "listFilePage, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path," +
                " includeFolders: $includeFolders, deep: $deep, page: $page, pageSize: $pageSize"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/page/$projectId/$repoName/$path" +
            "?deep=$deep&includeFolder=$includeFolders&includeMetadata=true&pageNumber=$page&pageSize=$pageSize"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .get()
            .build()
        return doRequest(request).resolveResponse<Response<Page<NodeInfo>>>()!!.data!!
    }

    fun uploadFile(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        inputStream: InputStream,
        properties: Map<String, String>? = null,
        gatewayUrl: String? = null,
        fileSizeLimitInMB: Int = 0
    ) {
        logger.info(
            "uploadFile, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path," +
                    " fileSizeLimitInMB: $fileSizeLimitInMB"
        )
        if (PathUtil.isFolder(path)) {
            throw ErrorCodeException(errorCode = INVALID_CUSTOM_ARTIFACTORY_PATH)
        }
        val gateway = gatewayUrl ?: getGatewayUrl()
        val url = "$gateway/bkrepo/api/service/generic/$projectId/$repoName/$path"
        val requestBody = object : RequestBody() {
            override fun writeTo(sink: BufferedSink) {
                val limit = if (fileSizeLimitInMB > 0) {
                    fileSizeLimitInMB * 1024 * 1024L
                } else {
                    Long.MAX_VALUE
                }
                var bytesCopied: Long = 0
                val buffer = ByteArray(STREAM_BUFFER_SIZE)
                var bytes = inputStream.read(buffer)
                while (bytes >= 0) {
                    sink.write(buffer, 0, bytes)
                    bytesCopied += bytes
                    if (bytesCopied > limit) {
                        sink.closeQuietly()
                        throw ErrorCodeException(
                            errorCode = FILE_SIZE_EXCEEDS_LIMIT,
                            params = arrayOf("${fileSizeLimitInMB}MB")
                        )
                    }
                    bytes = inputStream.read(buffer)
                }
                sink.flush()
            }

            override fun contentType(): MediaType? {
                return MediaType.parse("application/octet-stream")
            }
        }

        // 生成归档头部
        val header = mutableMapOf<String, String>()
        header[BK_REPO_UID] = userId
        header[AUTH_HEADER_DEVOPS_PROJECT_ID] = projectId
        header[BK_REPO_OVERRIDE] = "true"
        properties?.forEach {
            header["$METADATA_PREFIX${it.key}"] = it.value
        }
        val request = Request.Builder()
            .url(url)
            .headers(Headers.of(header))
            .put(requestBody).build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun uploadLocalFile(userId: String, projectId: String, repoName: String, path: String, file: File) {
        logger.info(
            "uploadLocalFile, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path," +
                    " localFile: ${file.canonicalPath}"
        )
        uploadLocalFile(
            userId = userId,
            projectId = projectId,
            repoName = repoName,
            path = path,
            file = file,
            gatewayFlag = true
        )
    }

    fun uploadLocalFile(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        file: File,
        gatewayFlag: Boolean = true,
        bkrepoApiUrl: String? = null,
        userName: String? = null,
        password: String? = null,
        properties: Map<String, String>? = null,
        gatewayUrl: String? = null
    ) {
        logger.info(
            "uploadLocalFile, projectId: $projectId, repoName: $repoName, path: $path," +
                    " localFile: ${file.canonicalPath}"
        )
        val gateway = gatewayUrl ?: getGatewayUrl()
        val repoUrlPrefix = if (gatewayFlag) "$gateway/bkrepo/api/service/generic" else bkrepoApiUrl
        val url = "$repoUrlPrefix/$projectId/$repoName/${path.removePrefix("/")}"
        val requestBuilder = Request.Builder()
            .url(url)
        // 生成归档头部
        val header = mutableMapOf<String, String>()
        if (userName != null && password != null) {
            header["Authorization"] = Credentials.basic(userName, password)
        }
        header[BK_REPO_UID] = userId
        header[AUTH_HEADER_DEVOPS_PROJECT_ID] = projectId
        header[BK_REPO_OVERRIDE] = "true"
        properties?.forEach {
            header["$METADATA_PREFIX${it.key}"] = tryEncode(it.value)
        }
        requestBuilder.headers(Headers.of(header))
            .put(RequestBody.create(MediaType.parse("application/octet-stream"), file))
        val request = requestBuilder.build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    private fun tryEncode(str: String?): String {
        return if (str.isNullOrBlank()) {
            ""
        } else {
            URLEncoder.encode(str, "UTF-8")
        }
    }

    fun delete(userId: String, projectId: String, repoName: String, path: String) {
        logger.info("delete, userId: $userId, projectId: $projectId, repoName: $repoName, path: $path")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/$projectId/$repoName/$path"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .delete()
            .build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun deleteNode(userName: String, projectId: String, repoName: String, path: String, authorization: String) {
        logger.info("delete,  projectId: $projectId, repoName: $repoName, path: $path")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/delete/$projectId/$repoName/$path"
        val request = Request.Builder()
            .url(url)
            .header("Authorization", authorization)
            .header(BK_REPO_UID, userName)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .delete()
            .build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun move(userId: String, projectId: String, repoName: String, fromPath: String, toPath: String) {
        // todo 校验path参数
        logger.info(
            "move, userId: $userId, projectId: $projectId, repoName: $repoName, fromPath: $fromPath," +
                " toPath: $toPath"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/move"
        val requestData = UserNodeMoveCopyRequest(
            srcProjectId = projectId,
            srcRepoName = repoName,
            srcFullPath = fromPath,
            destProjectId = projectId,
            destRepoName = repoName,
            destFullPath = toPath,
            overwrite = true
        )
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestData)
                )
            ).build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun copy(
        userId: String,
        fromProject: String,
        fromRepo: String,
        fromPath: String,
        toProject: String,
        toRepo: String,
        toPath: String
    ) {
        // todo 校验path参数
        logger.info(
            "copy, userId: $userId, fromProject: $fromProject, fromRepo: $fromRepo, fromPath: $fromPath," +
                " toProject: $toProject, toRepo: $toRepo, toPath: $toPath"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/copy"
        val requestData = UserNodeMoveCopyRequest(
            srcProjectId = fromProject,
            srcRepoName = fromRepo,
            srcFullPath = fromPath,
            destProjectId = toProject,
            destRepoName = toRepo,
            destFullPath = toPath,
            overwrite = true
        )
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, fromProject)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestData)
                )
            ).build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun rename(userId: String, projectId: String, repoName: String, fromPath: String, toPath: String) {
        // todo 校验path参数
        logger.info(
            "rename, userId: $userId, projectId: $projectId, repoName: $repoName, fromPath: $fromPath," +
                    " toPath: $toPath"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/rename"
        val requestData = UserNodeRenameRequest(projectId, repoName, fromPath, toPath)
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(requestData)
                )
            ).build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun mkdir(userId: String, projectId: String, repoName: String, path: String) {
        logger.info("mkdir, path: $path")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/$projectId/$repoName/$path"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(RequestBody.create(null, ""))
            .build()
        doRequest(request).resolveResponse<Response<Void>>()
    }

    fun getFileDetail(userId: String, projectId: String, repoName: String, path: String): NodeDetail? {
        logger.info("getFileInfo, projectId:$projectId, repoName: $repoName, path: $path")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/$projectId/$repoName/${
            path.replace(
                "#",
                "%23"
            )
        }"
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .get()
            .build()
        return doRequest(request).resolveResponse<Response<NodeDetail>>()!!.data
    }

    fun matchBkRepoFile(
        userId: String,
        srcPath: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        isCustom: Boolean
    ): List<BkRepoFile> {
        logger.info(
            "matchBkRepoFile, userId: $userId, srcPath: $srcPath, projectId: $projectId," +
                    " pipelineId: $pipelineId, buildId: $buildId, isCustom: $isCustom"
        )
        val repoName: String
        val filePath: String
        val fileName: String
        if (isCustom) {
            val normalizedPath = "/${srcPath.removePrefix("./").removePrefix("/")}"
            repoName = "custom"
            filePath = PathUtil.getParentFolder(normalizedPath)
            fileName = PathUtil.getFileName(normalizedPath)
        } else {
            repoName = "pipeline"
            filePath = "/$pipelineId/$buildId/"
            fileName = PathUtil.getFileName(srcPath)
        }

        return queryByPathEqOrNameMatchOrMetadataEqAnd(
            userId = userId,
            projectId = projectId,
            repoNames = listOf(repoName),
            filePaths = listOf(filePath),
            fileNames = listOf(fileName),
            metadata = mapOf(),
            page = 0,
            pageSize = 10000
        ).map {
            BkRepoFile(
                fullPath = it.fullPath,
                displayPath = it.fullPath,
                size = it.size,
                folder = it.folder
            )
        }
    }

    fun getFileDownloadUrl(param: ArtifactorySearchParam): List<String> {
        logger.info("getFileDownloadUrl, param: $param")
        val repoName = if (param.custom) "custom" else "pipeline"
        val files = matchBkRepoFile(
            userId = param.userId,
            srcPath = param.regexPath,
            projectId = param.projectId,
            pipelineId = param.pipelineId,
            buildId = param.buildId,
            isCustom = param.custom
        )
        logger.info("match files: $files")
        return files.map {
            "${getGatewayUrl()}/bkrepo/api/service/generic/${param.projectId}/$repoName${it.fullPath}"
        }
    }

    fun downloadFile(userId: String, projectId: String, repoName: String, fullPath: String, destFile: File) {
        val url = "${getGatewayUrl()}/bkrepo/api/service/generic/$projectId/$repoName/${fullPath.removePrefix("/")}"
        OkhttpUtils.downloadFile(
            url,
            destFile,
            mapOf(
                BK_REPO_UID to userId,
                AUTH_HEADER_DEVOPS_PROJECT_ID to projectId
            )
        )
    }

    fun listFileByPattern(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        repoName: String,
        pathPattern: String
    ): List<FileInfo> {
        logger.info(
            "listFileByPattern, userId: $userId, projectId: $projectId, pipelineId: $pipelineId," +
                    " buildId: $buildId, repoName: $repoName, pathPattern: $pathPattern"
        )
        return if (pathPattern.endsWith("/")) {
            val path = if (repoName == "pipeline") {
                "$pipelineId/$buildId/${pathPattern.removeSuffix("/")}"
            } else {
                pathPattern.removeSuffix("/")
            }
            listFile(userId, projectId, repoName, path, includeFolders = false, deep = false)
        } else {
            val f = File(pathPattern)
            val path = if (f.parent.isNullOrBlank()) {
                if (repoName == "pipeline") {
                    "$pipelineId/$buildId"
                } else {
                    ""
                }
            } else {
                if (repoName == "pipeline") {
                    "$pipelineId/$buildId/${f.parent}"
                } else {
                    f.parent
                }
            }
            val regex = f.name
            val matcher = FileSystems.getDefault().getPathMatcher("glob:$regex")
            listFile(userId, projectId, repoName, path, includeFolders = false, deep = false).filter {
                matcher.matches(Paths.get(it.name))
            }
        }
    }

    fun downloadFileByPattern(
        userId: String,
        projectId: String,
        pipelineId: String,
        buildId: String,
        repoName: String,
        pathPattern: String,
        destPath: String
    ): List<File> {
        logger.info(
            "downloadFileByPattern, userId: $userId, projectId: $projectId, pipelineId: $pipelineId," +
                " buildId: $buildId, repoName: $repoName, pathPattern: $pathPattern, destPath: $destPath"
        )
        val fileList = listFileByPattern(
            userId,
            projectId,
            pipelineId,
            buildId,
            repoName,
            pathPattern
        )
        logger.info("match files: ${fileList.map { it.fullPath }}")

        val destFiles = mutableListOf<File>()
        fileList.forEach {
            val destFile = File(destPath, it.name)
            downloadFile(userId, projectId, repoName, it.fullPath, destFile)
            destFiles.add(destFile)
            logger.info("save file : ${destFile.canonicalPath} (${destFile.length()})")
        }
        return destFiles
    }

    fun createShareUri(
        creatorId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        downloadUsers: List<String>,
        downloadIps: List<String>,
        timeoutInSeconds: Long
    ): String {
        logger.info(
            "createShareUri, creatorId: $creatorId, projectId: $projectId, repoName: $repoName, " +
                "fullPath: $fullPath, downloadUsers: $downloadUsers, downloadIps: $downloadIps, " +
                "timeoutInSeconds: $timeoutInSeconds"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/share/$projectId/$repoName/${
            fullPath.removePrefix("/").replace(
                "#",
                "%23"
            )
        }"
        val requestData = ShareRecordCreateRequest(
            authorizedUserList = downloadUsers,
            authorizedIpList = downloadIps,
            expireSeconds = timeoutInSeconds
        )
        val requestBody = objectMapper.writeValueAsString(requestData)
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, creatorId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody
                )
            ).build()
        return doRequest(request).resolveResponse<Response<ShareRecordInfo>>()!!.data!!.shareUrl
    }

    fun createTemporaryToken(
        userId: String,
        projectId: String,
        repoName: String,
        fullPath: String,
        timeoutInSeconds: Long,
        type: TokenType
    ): String {
        val url = "${getGatewayUrl()}/bkrepo/api/service/generic/temporary/token/create"
        val requestData = TemporaryTokenCreateRequest(
            projectId = projectId,
            repoName = repoName,
            fullPathSet = setOf(fullPath),
            expireSeconds = timeoutInSeconds,
            type = type
        )
        val requestBody = objectMapper.writeValueAsString(requestData)
        val request = Request.Builder().url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody
                )
            ).build()
        return doRequest(request).resolveResponse<Response<List<TemporaryAccessToken>>>()!!.data!!.first().token
    }

    fun createTemporaryAccessUrls(
        userId: String,
        projectId: String,
        repoName: String,
        fullPathSet: Set<String>,
        downloadUsersSet: Set<String>,
        downloadIpsSet: Set<String>,
        permits: Int?,
        timeoutInSeconds: Long
    ): List<String> {
        logger.info(
            "createTemporaryAccessUrl, userId: $userId, projectId: $projectId, repoName: $repoName, " +
                    "fullPathSet: $fullPathSet, downloadUsersSet: $downloadUsersSet, downloadIps: $downloadIpsSet," +
                    " timeoutInSeconds: $timeoutInSeconds"
        )
        val url = "${getGatewayUrl()}/bkrepo/api/service/generic/temporary/url/create"
        val requestData = TemporaryTokenCreateRequest(
            projectId = projectId,
            repoName = repoName,
            fullPathSet = fullPathSet,
            authorizedUserSet = downloadUsersSet,
            authorizedIpSet = downloadIpsSet,
            expireSeconds = timeoutInSeconds,
            permits = permits,
            type = TokenType.DOWNLOAD
        )
        val requestBody = objectMapper.writeValueAsString(requestData)
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody
                )
            ).build()
        return doRequest(request).resolveResponse<Response<List<TemporaryAccessUrl>>>()!!.data!!
            .map { "${it.url}&download=true" }
    }

    fun queryByNameAndMetadata(
        userId: String,
        projectId: String, // eq
        repoNames: List<String>, // eq or
        fileNames: List<String>, // match or
        metadata: Map<String, String>, // eq and
        page: Int,
        pageSize: Int
    ): List<QueryNodeInfo> {
        logger.info(
            "queryByRepoAndMetadata, userId: $userId, projectId: $projectId, repoNames: $repoNames," +
                " fileNames: $fileNames, metadata: $metadata, page: $page, pageSize: $pageSize"
        )
        val projectRule = Rule.QueryRule("projectId", projectId, OperationType.EQ)
        val repoRule = Rule.QueryRule("repoName", repoNames, OperationType.IN)
        val ruleList = mutableListOf<Rule>(projectRule, repoRule, Rule.QueryRule("folder", false, OperationType.EQ))
        if (fileNames.isNotEmpty()) {
            val fileNameRule = Rule.NestedRule(fileNames.map {
                Rule.QueryRule("name", it, OperationType.MATCH)
            }.toMutableList(), Rule.NestedRule.RelationType.OR)
            ruleList.add(fileNameRule)
        }
        if (metadata.isNotEmpty()) {
            val metadataRule = Rule.NestedRule(metadata.map {
                Rule.QueryRule("metadata.${it.key}", it.value, OperationType.EQ)
            }.toMutableList())
            ruleList.add(metadataRule)
        }
        val rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)

        return query(userId, projectId, rule, page, pageSize)
    }

    fun queryByPathEqOrNameMatchOrMetadataEqAnd(
        userId: String,
        projectId: String, // eq
        repoNames: List<String>, // eq or
        filePaths: List<String>, // eq or
        fileNames: List<String>, // match or
        metadata: Map<String, String>, // eq and
        page: Int,
        pageSize: Int
    ): List<QueryNodeInfo> {
        logger.info(
            "queryByPathEqOrNameMatchOrMetadataEqAnd, userId: $userId, projectId: $projectId," +
                    " repoNames: $repoNames, filePaths: $filePaths, fileNames: $fileNames, metadata: $metadata," +
                    " page: $page, pageSize: $pageSize"
        )
        val projectRule = Rule.QueryRule("projectId", projectId, OperationType.EQ)
        val repoRule = Rule.QueryRule("repoName", repoNames, OperationType.IN)
        val ruleList = mutableListOf<Rule>(projectRule, repoRule, Rule.QueryRule("folder", false, OperationType.EQ))
        if (filePaths.isNotEmpty()) {
            val filePathRule = Rule.NestedRule(filePaths.map {
                Rule.QueryRule("path", it, OperationType.EQ)
            }.toMutableList(), Rule.NestedRule.RelationType.OR)
            ruleList.add(filePathRule)
        }
        if (fileNames.isNotEmpty()) {
            val fileNameRule = Rule.NestedRule(fileNames.map {
                Rule.QueryRule("name", it, OperationType.MATCH)
            }.toMutableList(), Rule.NestedRule.RelationType.OR)
            ruleList.add(fileNameRule)
        }
        if (metadata.isNotEmpty()) {
            val metadataRule = Rule.NestedRule(metadata.map {
                Rule.QueryRule("metadata.${it.key}", it.value, OperationType.EQ)
            }.toMutableList(), Rule.NestedRule.RelationType.AND)
            ruleList.add(metadataRule)
        }
        val rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)

        return query(userId, projectId, rule, page, pageSize)
    }

    private fun getPathNameAndRule(path: String, name: String): Rule.NestedRule {
        val pathRule = Rule.QueryRule("path", path, OperationType.EQ)
        val nameRule = Rule.QueryRule("name", name, if (name.contains('*')) OperationType.MATCH else OperationType.EQ)
        return Rule.NestedRule(mutableListOf(pathRule, nameRule), Rule.NestedRule.RelationType.AND)
    }

    fun queryByPathNamePairOrMetadataEqAnd(
        userId: String,
        projectId: String, // eq
        repoNames: List<String>, // eq or
        pathNamePairs: List<Pair<String, String>>, // (path eq and name match) or
        metadata: Map<String, String>, // eq and
        page: Int,
        pageSize: Int
    ): List<QueryNodeInfo> {
        logger.info(
            "queryByPathNamePairOrMetadataEqAnd, userId: $userId, projectId: $projectId," +
                " repoNames: $repoNames, pathNamePairs: $pathNamePairs, metadata: $metadata," +
                " page: $page, pageSize: $pageSize"
        )
        val projectRule = Rule.QueryRule("projectId", projectId, OperationType.EQ)
        val repoRule = Rule.QueryRule("repoName", repoNames, OperationType.IN)
        val ruleList = mutableListOf<Rule>(projectRule, repoRule, Rule.QueryRule("folder", false, OperationType.EQ))

        if (pathNamePairs.size == 1) {
            ruleList.add(getPathNameAndRule(pathNamePairs[0].first, pathNamePairs[0].second))
        } else if (pathNamePairs.size > 1) {
            val pathNameRules = mutableListOf<Rule>()
            pathNamePairs.forEach {
                pathNameRules.add(getPathNameAndRule(it.first, it.second))
            }
            ruleList.add(Rule.NestedRule(pathNameRules, Rule.NestedRule.RelationType.OR))
        }

        if (metadata.isNotEmpty()) {
            val metadataRule = Rule.NestedRule(metadata.map {
                Rule.QueryRule("metadata.${it.key}", it.value, OperationType.EQ)
            }.toMutableList(), Rule.NestedRule.RelationType.AND)
            ruleList.add(metadataRule)
        }
        val rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)

        return query(userId, projectId, rule, page, pageSize)
    }

    fun queryByPattern(
        userId: String,
        projectId: String,
        repoNames: List<String>,
        fullPathPatterns: List<String>,
        metadata: Map<String, String>
    ): List<QueryNodeInfo> {
        logger.info(
            "queryByPattern, userId: $userId, projectId: $projectId, repoNames: $repoNames," +
                    " fullPathPatterns: $fullPathPatterns, metadata: $metadata"
        )
        val projectRule = Rule.QueryRule("projectId", projectId, OperationType.EQ)
        val repoRule = Rule.QueryRule("repoName", repoNames, OperationType.IN)
        val ruleList = mutableListOf<Rule>(projectRule, repoRule, Rule.QueryRule("folder", false, OperationType.EQ))
        if (fullPathPatterns.isNotEmpty()) {
            val fullPathRule = Rule.NestedRule(fullPathPatterns.map {
                Rule.QueryRule("fullPath", it, OperationType.MATCH)
            }.toMutableList(), Rule.NestedRule.RelationType.OR)
            ruleList.add(fullPathRule)
        }
        if (metadata.isNotEmpty()) {
            val metadataRule = Rule.NestedRule(metadata.map {
                Rule.QueryRule("metadata.${it.key}", it.value, OperationType.EQ)
            }.toMutableList())
            ruleList.add(metadataRule)
        }
        val rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)

        return query(userId, projectId, rule, 0, 10000)
    }

    fun listFileByQuery(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        includeFolders: Boolean = false,
        page: Int = 1,
        pageSize: Int = 10000
    ): List<QueryNodeInfo> {
        logger.info(
            "listFileByQuery, userId: $userId, projectId: $projectId, repoName: $repoName," +
                    " path: $path, includeFolders: $includeFolders"
        )
        val projectRule = Rule.QueryRule("projectId", projectId, OperationType.EQ)
        val repoRule = Rule.QueryRule("repoName", repoName, OperationType.EQ)
        val pathRule = Rule.QueryRule("path", "${path.removeSuffix("/")}/", OperationType.EQ)
        val ruleList = mutableListOf<Rule>(projectRule, repoRule, pathRule)
        if (!includeFolders) {
            ruleList.add(Rule.QueryRule("folder", false, OperationType.EQ))
        }
        val rule = Rule.NestedRule(ruleList, Rule.NestedRule.RelationType.AND)

        val queryModel = QueryModel(
            page = PageLimit(page, pageSize),
            sort = Sort(listOf("lastModifiedDate"), Sort.Direction.DESC),
            select = mutableListOf(),
            rule = rule
        )
        return query(userId, projectId, queryModel)
    }

    // 更新文件(目前只支持更新过期时间)
    fun update(
        userId: String,
        projectId: String,
        repoName: String,
        path: String,
        expires: Int
    ) {
        logger.info("update , userId:$userId, projectId:$projectId , repo:$repoName , path:$path , expires:$expires")
        val request = Request.Builder()
            .url("${getGatewayUrl()}/bkrepo/api/service/repository/api/node/update/$projectId/$repoName/$path")
            .header(BK_REPO_UID, userId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    objectMapper.writeValueAsString(mapOf("expires" to expires))
                )
            )
            .build()
        doRequest(request)
    }

    private fun query(userId: String, projectId: String, rule: Rule, page: Int, pageSize: Int): List<QueryNodeInfo> {
        logger.info("query, userId: $userId, rule: $rule, page: $page, pageSize: $pageSize")
        val queryModel = QueryModel(
            page = PageLimit(page, pageSize),
            sort = Sort(listOf("fullPath"), Sort.Direction.ASC),
            select = mutableListOf(),
            rule = rule
        )
        return query(userId, projectId, queryModel)
    }

    private fun query(userId: String, projectId: String, queryModel: QueryModel): List<QueryNodeInfo> {
        logger.info("query, userId: $userId, queryModel: $queryModel")
        val url = "${getGatewayUrl()}/bkrepo/api/service/repository/api/node/search"
        val requestBody = objectMapper.writeValueAsString(queryModel)
        logger.info("requestBody: $requestBody")
        val request = Request.Builder()
            .url(url)
            .header(BK_REPO_UID, userId)
            .header(AUTH_HEADER_DEVOPS_PROJECT_ID, projectId)
            .post(
                RequestBody.create(
                    MediaType.parse("application/json; charset=utf-8"),
                    requestBody
                )
            ).build()
        return doRequest(request).resolveResponse<Response<QueryData>>()!!.data!!.records
    }

    private fun doRequest(request: Request): okhttp3.Response {
        try {
            return OkhttpUtils.doHttp(request)
        } catch (e: IOException) {
            throw RemoteServiceException("request api[${request.url().url()}] error: ${e.localizedMessage}")
        }
    }

    private inline fun <reified T> okhttp3.Response.resolveResponse(allowCode: Int? = null): T? {
        this.use {
            val responseContent = this.body()!!.string()
            if (this.isSuccessful) {
                return objectMapper.readValue(responseContent, jacksonTypeRef<T>())
            }

            val responseData = try {
                objectMapper.readValue<Response<Void>>(responseContent)
            } catch (e: JacksonException) {
                throw RemoteServiceException(responseContent, this.code())
            }
            if (allowCode == responseData.code) {
                logger.info("request bkrepo api failed but it can be allowed: ${responseData.message}")
                return null
            }
            throw RemoteServiceException(responseData.message ?: responseData.code.toString(), this.code())
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(BkRepoClient::class.java)
        private const val METADATA_PREFIX = "X-BKREPO-META-"

        private const val BK_REPO_UID = "X-BKREPO-UID"
        private const val BK_REPO_OVERRIDE = "X-BKREPO-OVERWRITE"

        private const val ERROR_PROJECT_EXISTED = 251005
        private const val ERROR_REPO_EXISTED = 251007

        const val FILE_SIZE_EXCEEDS_LIMIT = "2102003" // 文件大小不能超过{0}
        const val INVALID_CUSTOM_ARTIFACTORY_PATH = "2102004" // 非法自定义仓库路径
    }
}