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
import com.tencent.devops.model.sharding.tables.TPipelineInfo.T_PIPELINE_INFO
import com.tencent.devops.model.sharding.tables.TPipelineUser
import com.tencent.devops.model.sharding.tables.TPipelineUser.T_PIPELINE_USER
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Record
import org.jooq.Result
import org.jooq.conf.ParamType
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Repository

@Repository
class PipelineManageDao {

    private val logger = LoggerFactory.getLogger(PipelineManageDao::class.java)

    fun getPipelineUserList(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String
    ): Result<out Record>? {
        val tpi = TPipelineInfo.T_PIPELINE_INFO.`as`("tpi")
        val tpu = TPipelineUser.T_PIPELINE_USER.`as`("tpu")
        return dslContext.select(
            tpi.PROJECT_ID,
            tpi.PIPELINE_ID,
            tpi.PIPELINE_NAME,
            tpi.PIPELINE_DESC,
            tpu.CREATE_USER
        )
            .from(tpi).join(tpu).on(tpi.PIPELINE_ID.eq(tpu.PIPELINE_ID))
            .where(tpi.PROJECT_ID.eq(projectId).and(tpi.PIPELINE_ID.eq(pipelineId)))
            .fetch()
    }

    fun countPipelineUserList(
        dslContext: DSLContext,
        projectId: String,
        pipelineId: String
    ): Long {
        val conditions = mutableListOf<Condition>()
        conditions.add(T_PIPELINE_INFO.PROJECT_ID.eq(projectId))
        conditions.add(T_PIPELINE_INFO.PIPELINE_ID.`in`(pipelineId))
        conditions.add(T_PIPELINE_INFO.PIPELINE_ID.`in`(pipelineId))
        conditions.add(T_PIPELINE_INFO.PIPELINE_ID.`in`(pipelineId))
        conditions.add(DSL.trueCondition())
        val baseStep = dslContext.selectCount()
            .from(T_PIPELINE_INFO).join(T_PIPELINE_USER)
            .on(T_PIPELINE_INFO.PIPELINE_ID.eq(T_PIPELINE_USER.PIPELINE_ID))
            .where(conditions)
        logger.info(baseStep.getSQL(ParamType.INLINED))
        return baseStep.fetchOne(0, Long::class.java)!!
    }
}
