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

package com.tencent.devops.sharding.dao.process

import com.tencent.devops.model.sharding.tables.TPipelineInfo
import com.tencent.devops.model.sharding.tables.records.TPipelineInfoRecord
import org.jooq.DSLContext
import org.jooq.Result
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository

@Repository
class PipelineInfoDao {

    fun addPipelineInfo(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String,
        pipelineName: String,
        pipelineDesc: String
    ) {
        with(TPipelineInfo.T_PIPELINE_INFO) {
            dslContext.insertInto(
                this,
                PIPELINE_ID,
                PROJECT_ID,
                PIPELINE_NAME,
                PIPELINE_DESC
            ).values(
                pipelineId,
                projectId,
                pipelineName,
                pipelineDesc
            ).execute()
        }
    }

    fun getPipelineInfoByPipelineId(
        dslContext: DSLContext,
        pipelineId: String
    ): TPipelineInfoRecord? {
        with(TPipelineInfo.T_PIPELINE_INFO) {
            return dslContext.selectFrom(this)
                .where(PIPELINE_ID.eq(pipelineId))
                .fetchAny()
        }
    }

    fun countByProjectId(
        dslContext: DSLContext,
        projectId: String
    ): Int {
        with(TPipelineInfo.T_PIPELINE_INFO) {
            val count1 = dslContext
                .select(DSL.countDistinct(PIPELINE_DESC))
                .from(this)
                .where(PROJECT_ID.eq(projectId))
                .fetchOne(0, Int::class.java)!!
            val count2 = dslContext
                .select(DSL.count(PIPELINE_DESC))
                .from(this)
                .where(PIPELINE_DESC.eq("dfdf123"))
                .fetchOne(0, Int::class.java)!!
            return count1 + count2
        }
    }

    fun getPipelineInfoByProjectId(
        dslContext: DSLContext,
        projectId: String
    ): Result<TPipelineInfoRecord>? {
        with(TPipelineInfo.T_PIPELINE_INFO) {
            return dslContext.selectFrom(this)
                .where(PROJECT_ID.eq(projectId))
                .fetch()
        }
    }

    fun updatePipelineInfo(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String,
        pipelineName: String,
        pipelineDesc: String
    ) {
        with(TPipelineInfo.T_PIPELINE_INFO) {
            dslContext.update(this)
                .set(PIPELINE_NAME, pipelineName)
                .set(PIPELINE_DESC, pipelineDesc)
                .where(PROJECT_ID.eq(projectId).and(PIPELINE_ID.eq(pipelineId)))
                .execute()
        }
    }
}
