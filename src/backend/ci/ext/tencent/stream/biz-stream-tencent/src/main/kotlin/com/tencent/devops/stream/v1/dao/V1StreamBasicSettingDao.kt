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

package com.tencent.devops.stream.v1.dao

import com.fasterxml.jackson.core.type.TypeReference
import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.model.stream.tables.TGitBasicSetting
import com.tencent.devops.stream.v1.pojo.V1CIInfo
import com.tencent.devops.stream.v1.pojo.V1GitCIBasicSetting
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository
class V1StreamBasicSettingDao {

    fun getSetting(
        dslContext: DSLContext,
        gitProjectId: Long,
        hasLastInfo: Boolean = false
    ): V1GitCIBasicSetting? {
        with(TGitBasicSetting.T_GIT_BASIC_SETTING) {
            val conf = dslContext.selectFrom(this)
                .where(ID.eq(gitProjectId))
                .fetchAny()
            if (conf == null) {
                return null
            } else {
                return V1GitCIBasicSetting(
                    gitProjectId = conf.id,
                    name = conf.name,
                    url = conf.url,
                    homepage = conf.homePage,
                    gitHttpUrl = conf.gitHttpUrl,
                    gitSshUrl = conf.gitSshUrl,
                    enableCi = conf.enableCi,
                    buildPushedBranches = conf.buildPushedBranches,
                    buildPushedPullRequest = conf.buildPushedPullRequest,
                    createTime = conf.createTime.timestampmilli(),
                    updateTime = conf.updateTime.timestampmilli(),
                    projectCode = conf.projectCode,
                    enableMrBlock = conf.enableMrBlock,
                    enableUserId = conf.enableUserId,
                    creatorBgName = conf.creatorBgName,
                    creatorDeptName = conf.creatorDeptName,
                    creatorCenterName = conf.creatorCenterName,
                    gitProjectDesc = conf.gitProjectDesc,
                    gitProjectAvatar = conf.gitProjectAvatar,
                    lastCiInfo = if (hasLastInfo && conf.lastCiInfo != null) {
                        JsonUtil.to(conf.lastCiInfo, object : TypeReference<V1CIInfo>() {})
                    } else {
                        null
                    },
                    enableCommitCheck = conf.enableCommitCheck,
                    nameWithNamespace = conf.nameWithNameSpace ?: "",
                    pathWithNamespace = conf.pathWithNameSpace,
                    enableMrComment = conf.enableMrComment
                )
            }
        }
    }
}