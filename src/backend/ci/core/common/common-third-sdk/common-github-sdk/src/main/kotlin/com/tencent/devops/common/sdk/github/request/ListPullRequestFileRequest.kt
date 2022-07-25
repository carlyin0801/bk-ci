package com.tencent.devops.common.sdk.github.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.tencent.devops.common.sdk.enums.HttpMethod
import com.tencent.devops.common.sdk.github.GithubRequest
import com.tencent.devops.common.sdk.github.response.PullRequestFileResponse

data class ListPullRequestFileRequest(
    // val owner: String,
    // val repo: String,
    val repoId: Long,
    val pullNumber: String,
    @JsonProperty("per_page")
    val perPage: Int = 30,
    val page: Int = 1
) : GithubRequest<List<PullRequestFileResponse>>() {
    override fun getHttpMethod() = HttpMethod.GET

    override fun getApiPath() = "repositories/$repoId/pulls/$pullNumber/files"
}
