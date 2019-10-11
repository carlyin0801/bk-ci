package com.tencent.devops.process.service.scm

import com.tencent.devops.common.api.util.DHUtil
import com.tencent.devops.common.client.Client
import com.tencent.devops.repository.api.ServiceOauthResource
import com.tencent.devops.repository.pojo.CodeGitRepository
import com.tencent.devops.repository.pojo.Repository
import com.tencent.devops.repository.pojo.enums.RepoAuthType
import com.tencent.devops.repository.pojo.enums.TokenTypeEnum
import com.tencent.devops.repository.pojo.git.GitMrChangeInfo
import com.tencent.devops.repository.pojo.git.GitMrInfo
import com.tencent.devops.repository.pojo.git.GitMrReviewInfo
import com.tencent.devops.scm.api.ServiceGitResource
import com.tencent.devops.ticket.api.ServiceCredentialResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.Base64

@Service
class GitScmService @Autowired constructor(
    private val client: Client
) {

    companion object {
        private val logger = LoggerFactory.getLogger(GitScmService::class.java)
    }

    fun getMergeRequestReviewersInfo(
        projectId: String,
        mrId: Long?,
        repo: Repository
    ): GitMrReviewInfo? {
        if (mrId == null) return null
        if (repo !is CodeGitRepository) return null

        return try {
            val tokenType = if (repo.authType == RepoAuthType.OAUTH) TokenTypeEnum.OAUTH else TokenTypeEnum.PRIVATE_KEY
            val token = getToken(projectId, repo.credentialId, repo.userName, tokenType)
            client.getScm(ServiceGitResource::class).getMergeRequestReviewersInfo(repo.projectName, mrId, tokenType, token).data!!
        } catch (e: Exception) {
            logger.error("fail to get mr reviews info", e)
            null
        }
    }

    fun getMergeRequestInfo(
        projectId: String,
        mrId: Long?,
        repo: Repository
    ): GitMrInfo? {
        if (mrId == null) return null
        if (repo !is CodeGitRepository) return null

        return try {
            val tokenType = if (repo.authType == RepoAuthType.OAUTH) TokenTypeEnum.OAUTH else TokenTypeEnum.PRIVATE_KEY
            val token = getToken(projectId, repo.credentialId, repo.userName, tokenType)
            return client.getScm(ServiceGitResource::class).getMergeRequestInfo(repo.projectName, mrId, tokenType, token).data!!
        } catch (e: Exception) {
            logger.error("fail to get mr info", e)
            null
        }
    }

    fun getMergeRequestChangeInfo(
        projectId: String,
        mrId: Long?,
        repo: Repository
    ): GitMrChangeInfo? {
        if (mrId == null) return null
        if (repo !is CodeGitRepository) return null

        return try {
            val tokenType = if (repo.authType == RepoAuthType.OAUTH) TokenTypeEnum.OAUTH else TokenTypeEnum.PRIVATE_KEY
            val token = getToken(projectId, repo.credentialId, repo.userName, tokenType)
            return client.getScm(ServiceGitResource::class).getMergeRequestChangeInfo(repo.projectName, mrId, tokenType, token).data!!
        } catch (e: Exception) {
            logger.error("fail to get mr info", e)
            null
        }
    }

    private fun getToken(projectId: String, credentialId: String, userName: String, authType: TokenTypeEnum): String {
        return if (authType == TokenTypeEnum.OAUTH) {
            client.get(ServiceOauthResource::class).gitGet(userName).data?.accessToken ?: ""
        } else {
            val pair = DHUtil.initKey()
            val encoder = Base64.getEncoder()
            val decoder = Base64.getDecoder()
            val credentialResult = client.get(ServiceCredentialResource::class).get(projectId, credentialId,
                encoder.encodeToString(pair.publicKey))
            if (credentialResult.isNotOk() || credentialResult.data == null) {
                logger.error("Fail to get the credential($credentialId) of project($projectId) because of ${credentialResult.message}")
                throw RuntimeException("Fail to get the credential($credentialId) of project($projectId)")
            }

            val credential = credentialResult.data!!

            String(DHUtil.decrypt(
                decoder.decode(credential.v1),
                decoder.decode(credential.publicKey),
                pair.privateKey))
        }
    }
}