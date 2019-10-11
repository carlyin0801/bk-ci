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

package com.tencent.devops.scm.utils.code.git

import com.tencent.devops.common.api.enums.ScmType
import com.tencent.devops.scm.exception.ScmException
import java.net.URLDecoder
import java.net.URLEncoder

object GitUtils {

    fun urlDecode(s: String) = URLDecoder.decode(s, "UTF-8")

    fun urlEncode(s: String) = URLEncoder.encode(s, "UTF-8")

    fun getProjectName(gitUrl: String) = getDomainAndRepoName(gitUrl).second

    fun getDomainAndRepoName(gitUrl: String): Pair<String/*domain*/, String/*repoName*/> {
        val groups = Regex("git@([-.a-z0-9A-Z]+):(.*).git").find(gitUrl)?.groups
            ?: Regex("http[s]?://([-.a-z0-9A-Z]+)/(.*).git").find(gitUrl)?.groups
            ?: throw ScmException("Invalid git url $gitUrl", ScmType.CODE_GIT.name)

        if (groups.size < 3) {
            throw ScmException("Invalid git url $gitUrl", ScmType.CODE_GIT.name)
        }
        return groups[1]!!.value to groups[2]!!.value
    }

    /**
     * 根据apiUrl与真正的仓库url，判断出真正的apiUrl
     */
    fun getGitApiUrl(apiUrl: String, repoUrl: String): String {
        val urlDomainAndRepoName = getDomainAndRepoName(repoUrl)
        val parseApiUri = partApiUrl(apiUrl) ?: return apiUrl
        return if (urlDomainAndRepoName.second != parseApiUri.second) { // 如果域名不一样，则以仓库域名为准
            "${parseApiUri.first}${urlDomainAndRepoName.first}/${parseApiUri.third}"
        } else {
            apiUrl
        }
    }

    private fun partApiUrl(apiUrl: String): Triple<String, String, String>? {
        val groups = Regex("(http[s]?://)([-.a-z0-9A-Z]+)/(.*)").find(apiUrl)?.groups
            ?: return null
        return Triple(groups[1]!!.value, groups[2]!!.value, groups[3]!!.value) // http[s]//, xxx.com, api/v4
    }
}