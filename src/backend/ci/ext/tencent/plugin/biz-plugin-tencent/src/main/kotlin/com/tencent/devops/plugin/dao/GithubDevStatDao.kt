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

package com.tencent.devops.plugin.dao

import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.model.plugin.tables.TPluginGithubDevStat
import com.tencent.devops.model.plugin.tables.records.TPluginGithubDevStatRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Repository
class GithubDevStatDao {

    /**
     * 不存在则新增，否则更新
     */
    fun createOrUpdate(
        dslContext: DSLContext,
        owner: String,
        repo: String,
        statDate: String,
        author: String,
        commits: Int
    ) {
        with(TPluginGithubDevStat.T_PLUGIN_GITHUB_DEV_STAT) {
            dslContext.insertInto(this,
                    ID,
                    OWNER,
                    REPO,
                    STAT_DATE,
                    AUTHOR,
                    COMMITS
            ).values(
                    UUIDUtil.generate(),
                    owner,
                    repo,
                    LocalDate.parse(statDate, DateTimeFormatter.ISO_DATE),
                    author,
                    commits
            )
                    .onDuplicateKeyUpdate()
                    .set(COMMITS, commits)
                    .set(UPDATE_TIME, java.time.LocalDateTime.now())
                    .execute()
        }
    }
}