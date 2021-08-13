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

package com.tencent.devops.gitci.dao

import com.tencent.devops.gitci.pojo.GitProjectPipeline
import com.tencent.devops.model.gitci.tables.TGitPipelineResource
import com.tencent.devops.model.gitci.tables.records.TGitPipelineResourceRecord
import com.tencent.devops.process.pojo.PipelineSortType
import org.jooq.DSLContext
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class GitPipelineResourceDao {

    fun createPipeline(
        dslContext: DSLContext,
        gitProjectId: Long,
        pipeline: GitProjectPipeline,
        version: String?
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.insertInto(
                this,
                PIPELINE_ID,
                GIT_PROJECT_ID,
                FILE_PATH,
                DISPLAY_NAME,
                CREATOR,
                ENABLED,
                LATEST_BUILD_ID,
                VERSION,
                CREATE_TIME,
                UPDATE_TIME
            ).values(
                pipeline.pipelineId,
                gitProjectId,
                pipeline.filePath,
                pipeline.displayName,
                pipeline.creator,
                pipeline.enabled,
                null,
                version,
                LocalDateTime.now(),
                LocalDateTime.now()
            ).execute()
        }
    }

    fun updatePipeline(
        dslContext: DSLContext,
        gitProjectId: Long,
        pipelineId: String,
        displayName: String,
        version: String?
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.update(this)
                .set(DISPLAY_NAME, displayName)
                .set(VERSION, version)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(PIPELINE_ID.eq(pipelineId))
                .execute()
        }
    }

    fun savePipeline(
        dslContext: DSLContext,
        gitProjectId: Long,
        projectCode: String,
        pipelineId: String,
        filePath: String,
        displayName: String,
        enabled: Boolean,
        creator: String?,
        latestBuildId: String?,
        manualTrigger: Boolean? = false,
        version: String?
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.insertInto(
                this,
                PIPELINE_ID,
                GIT_PROJECT_ID,
                FILE_PATH,
                DISPLAY_NAME,
                CREATOR,
                ENABLED,
                LATEST_BUILD_ID,
                VERSION,
                CREATE_TIME,
                UPDATE_TIME
            ).values(
                pipelineId,
                gitProjectId,
                filePath,
                displayName,
                creator,
                enabled,
                latestBuildId,
                version,
                LocalDateTime.now(),
                LocalDateTime.now()
            ).execute()
        }
    }

    fun updatePipelineBuildInfo(
        dslContext: DSLContext,
        pipeline: GitProjectPipeline,
        buildId: String,
        version: String?
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.update(this)
                .set(LATEST_BUILD_ID, buildId)
                .set(UPDATE_TIME, LocalDateTime.now())
                .set(PIPELINE_ID, pipeline.pipelineId)
                .set(VERSION, version)
                .where(GIT_PROJECT_ID.eq(pipeline.gitProjectId))
                .and(FILE_PATH.eq(pipeline.filePath))
                .execute()
        }
    }

    fun getPageByGitProjectId(
        dslContext: DSLContext,
        gitProjectId: Long,
        keyword: String?,
        offset: Int,
        limit: Int
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            val dsl = dslContext.selectFrom(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
            if (!keyword.isNullOrBlank()) {
                dsl.and(DISPLAY_NAME.like("%$keyword%"))
            }
            return dsl.orderBy(ENABLED.desc(), DISPLAY_NAME)
                .limit(limit).offset(offset)
                .fetch()
        }
    }

    fun getAllByGitProjectId(
        dslContext: DSLContext,
        gitProjectId: Long
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.selectFrom(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
                .orderBy(ENABLED.desc(), DISPLAY_NAME)
                .fetch()
        }
    }

    fun getPipelinesInIds(
        dslContext: DSLContext,
        gitProjectId: Long?,
        pipelineIds: List<String>
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            val dsl = dslContext.selectFrom(this)
                .where(PIPELINE_ID.`in`(pipelineIds))
            if (gitProjectId != null) {
                dsl.and(GIT_PROJECT_ID.eq(gitProjectId))
            }
            return dsl.orderBy(UPDATE_TIME.desc())
                .fetch()
        }
    }

    fun getPipelineById(
        dslContext: DSLContext,
        gitProjectId: Long,
        pipelineId: String
    ): TGitPipelineResourceRecord? {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.selectFrom(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
                .and(PIPELINE_ID.eq(pipelineId))
                .fetchAny()
        }
    }

    fun getPipelineCount(
        dslContext: DSLContext,
        gitProjectId: Long
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.selectCount()
                .from(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
                .fetchOne(0, Int::class.java)!!
        }
    }

    fun deleteByGitProjectId(
        dslContext: DSLContext,
        gitProjectId: Long
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.delete(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
                .execute()
        }
    }

    fun enablePipelineById(
        dslContext: DSLContext,
        pipelineId: String,
        enabled: Boolean
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.update(this)
                .set(ENABLED, enabled)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(PIPELINE_ID.eq(pipelineId))
                .execute()
        }
    }

    fun deleteByPipelineId(
        dslContext: DSLContext,
        pipelineId: String
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.delete(this)
                .where(PIPELINE_ID.eq(pipelineId))
                .execute()
        }
    }

    fun getAllPipeline(
        dslContext: DSLContext
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.selectFrom(this)
                .fetch()
        }
    }

    fun fixPipelineVersion(
        dslContext: DSLContext,
        pipelineId: String,
        version: String
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.update(this)
                .set(VERSION, version)
                .where(PIPELINE_ID.eq(pipelineId))
                .execute()
        }
    }

    fun getLastUpdatePipelines(
        dslContext: DSLContext,
        limit: Int,
        lastUpdateTime: LocalDateTime
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.selectFrom(this)
                .where(UPDATE_TIME.le(lastUpdateTime))
                .limit(limit)
                .fetch()
        }
    }

    fun deleteLastUpdatePipelines(
        dslContext: DSLContext,
        ids: Set<Long>
    ): Int {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.deleteFrom(this)
                .where(ID.`in`(ids))
                .execute()
        }
    }

    fun getPipelines(
        dslContext: DSLContext,
        gitProjectId: Long
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            return dslContext.selectFrom(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
                .fetch()
        }
    }

    fun getAppDataByGitProjectId(
        dslContext: DSLContext,
        gitProjectId: Long,
        keyword: String?,
        offset: Int,
        limit: Int,
        orderBy: PipelineSortType
    ): List<TGitPipelineResourceRecord> {
        with(TGitPipelineResource.T_GIT_PIPELINE_RESOURCE) {
            val dsl = dslContext.selectFrom(this)
                .where(GIT_PROJECT_ID.eq(gitProjectId))
                .and(ENABLED.eq(true))
            if (!keyword.isNullOrBlank()) {
                dsl.and(DISPLAY_NAME.like("%$keyword%"))
            }
            when (orderBy) {
                PipelineSortType.NAME -> {
                    dsl.orderBy(DISPLAY_NAME)
                }
                PipelineSortType.UPDATE_TIME -> {
                    dsl.orderBy(UPDATE_TIME)
                }
                PipelineSortType.CREATE_TIME -> {
                    dsl.orderBy(CREATE_TIME)
                }
            }
            return dsl.limit(limit).offset(offset)
                .fetch()
        }
    }
}
