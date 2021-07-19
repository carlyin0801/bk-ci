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

package com.tencent.devops.quality.dao.v2

import com.tencent.devops.common.api.util.HashUtil
import com.tencent.devops.model.quality.tables.TQualityRuleBuildHis
import com.tencent.devops.quality.api.v2.pojo.request.RuleCreateRequest
import com.tencent.devops.quality.api.v3.pojo.request.RuleCreateRequestV3
import org.jooq.DSLContext
import org.springframework.stereotype.Repository

@Repository@Suppress("ALL")
class QualityRuleBuildHisDao {
    fun create(dslContext: DSLContext, userId: String, projectId: String, pipelineId: String, buildId: String,
               ruleId: Long, ruleRequest: RuleCreateRequestV3,
               indicatorIds: List<RuleCreateRequest.CreateRequestIndicator>): Long {
        return with(TQualityRuleBuildHis.T_QUALITY_RULE_BUILD_HIS) {
            dslContext.insertInto(
                this,
                PROJECT_ID,
                PIPELINE_ID,
                BUILD_ID,
                RULE_ID,
                RULE_POS,
                RULE_NAME,
                RULE_DESC,
                GATEWAY_ID,
                INDICATOR_IDS,
                INDICATOR_OPERATIONS,
                INDICATOR_THRESHOLDS,
                OP_TYPE,
                NOTIFY_USER,
                NOTIFY_GROUP_ID,
                AUDIT_USER,
                AUDIT_TIMEOUT
            ).values(
                projectId,
                pipelineId,
                buildId,
                ruleId,
                ruleRequest.position,
                ruleRequest.name,
                ruleRequest.desc,
                ruleRequest.gatewayId,
                indicatorIds.map { HashUtil.decodeIdToLong(it.hashId) }.joinToString(","),
                indicatorIds.map { it.operation }.joinToString(","),
                indicatorIds.map { it.threshold }.joinToString(","),
                ruleRequest.operation.name,
                ruleRequest.notifyUserList?.joinToString(","),
                ruleRequest.notifyGroupList?.joinToString(","),
                ruleRequest.auditUserList?.joinToString(","),
                ruleRequest.auditTimeoutMinutes
            ).onDuplicateKeyUpdate()
                .set(RULE_ID, ruleId)
                .returning(ID)
                .fetchOne()!!.id
        }
    }
}
