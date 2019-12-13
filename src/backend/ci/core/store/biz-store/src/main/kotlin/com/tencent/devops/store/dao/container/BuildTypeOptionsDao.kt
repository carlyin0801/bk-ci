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

package com.tencent.devops.store.dao.container

import com.tencent.devops.model.store.tables.TBuildTypeOptions
import com.tencent.devops.model.store.tables.records.TBuildTypeOptionsRecord
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class BuildTypeOptionsDao {

    fun create(
        dslContext: DSLContext,
        projectId: String,
        buildType: String,
        pipelineId: String?,
        osList: String?,
        enableApp: Boolean?,
        clickable: Boolean?,
        visable: Boolean?,
        creator: String,
        modifier: String
    ) {
        with(TBuildTypeOptions.T_BUILD_TYPE_OPTIONS) {
            dslContext.insertInto(this,
                PROJECT_ID,
                PIPELINE_ID,
                BUILD_TYPE,
                OS_LIST,
                ENABLE_APP,
                CLICKABLE,
                VISABLE,
                CREATOR,
                MODIFIER,
                CREATE_TIME,
                UPDATE_TIME
            ).values(
                projectId,
                pipelineId,
                buildType,
                osList,
                enableApp,
                clickable,
                visable,
                creator,
                modifier,
                LocalDateTime.now(),
                LocalDateTime.now()
            ).execute()
        }
    }

    fun update(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String?,
        buildType: String,
        osList: String?,
        enableApp: Boolean?,
        clickable: Boolean?,
        visable: Boolean?,
        creator: String,
        modifier: String
    ) {
        with(TBuildTypeOptions.T_BUILD_TYPE_OPTIONS) {
            dslContext.update(this)
                    .set(PIPELINE_ID, pipelineId)
                    .set(BUILD_TYPE, buildType)
                    .set(OS_LIST, osList)
                    .set(ENABLE_APP, enableApp)
                    .set(CLICKABLE, clickable)
                    .set(VISABLE, visable)
                    .set(UPDATE_TIME, LocalDateTime.now())
                    .where(PROJECT_ID.eq(projectId))
                    .execute()
        }
    }

    fun get(dslContext: DSLContext, projectId: String, buildType: String, pipelineId: String?): TBuildTypeOptionsRecord? {
        with(TBuildTypeOptions.T_BUILD_TYPE_OPTIONS) {
            return if (pipelineId.isNullOrBlank()) {
                dslContext.selectFrom(this)
                        .where(PROJECT_ID.eq(projectId))
                        .and(BUILD_TYPE.eq(buildType))
                        .fetchOne()
            } else {
                dslContext.selectFrom(this)
                        .where(PROJECT_ID.eq(projectId))
                        .and(BUILD_TYPE.eq(buildType))
                        .and(PIPELINE_ID.eq(pipelineId))
                        .fetchOne()
            }
        }
    }

    fun delete(
        dslContext: DSLContext,
        projectId: String,
        buildType: String
    ) {
        with(TBuildTypeOptions.T_BUILD_TYPE_OPTIONS) {
            dslContext.deleteFrom(this)
                    .where(PROJECT_ID.eq(projectId))
                    .and(BUILD_TYPE.eq(buildType))
                    .execute()
        }
    }
}
