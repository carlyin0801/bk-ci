/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
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

package com.tencent.devops.common.pipeline.enums

import com.tencent.devops.common.api.annotation.BkFieldI18n
import com.tencent.devops.common.api.enums.I18nTranslateTypeEnum

/**
 * 构建终态子类型，覆盖取消/失败/超时/成功等所有结束场景。
 * 命名规范: {大类}_{子类型}，如 CANCEL_USER、FAIL_EXEC、TIMEOUT_JOB。
 *
 * 新增子类型只需在此追加枚举项并补充 buildEndType.{displayName} 国际化词条，
 * 前端可直接按 category 归类展示，无需感知具体子类型。
 */
enum class BuildEndType(
    @BkFieldI18n(
        translateType = I18nTranslateTypeEnum.VALUE,
        keyPrefixName = "buildEndType",
        reusePrefixFlag = false
    )
    val displayName: String,
    val category: BuildEndCategory
) {
    // ---- 取消类 ----
    CANCEL_USER("cancelUser", BuildEndCategory.CANCEL),
    CANCEL_SYSTEM("cancelSystem", BuildEndCategory.CANCEL),
    CANCEL_PARENT_PIPELINE("cancelParentPipeline", BuildEndCategory.CANCEL),

    // ---- 失败类 ----
    // 插件执行失败等常规失败
    FAIL_EXEC("failExec", BuildEndCategory.FAIL),
    // 质量红线拦截未达标
    FAIL_QUALITY("failQuality", BuildEndCategory.FAIL),
    // 人工审核驳回
    FAIL_REVIEW("failReview", BuildEndCategory.FAIL),
    // 子流水线执行失败导致父构建失败
    FAIL_SUB_PIPELINE("failSubPipeline", BuildEndCategory.FAIL),
    // 同一次构建中同时存在多种失败子类型
    FAIL_MULTIPLE("failMultiple", BuildEndCategory.FAIL),

    // ---- 超时类 ----
    // Job 超过执行时限，其下步骤被终止
    TIMEOUT_JOB("timeoutJob", BuildEndCategory.TIMEOUT),
    // 单个步骤超过自身执行时限
    TIMEOUT_STEP("timeoutStep", BuildEndCategory.TIMEOUT),
    // 构建排队超时
    TIMEOUT_QUEUE("timeoutQueue", BuildEndCategory.TIMEOUT),
    // 构建机Agent心跳超时
    TIMEOUT_HEARTBEAT("timeoutHeartbeat", BuildEndCategory.TIMEOUT),

    // ---- 成功类 ----
    SUCCESS("success", BuildEndCategory.SUCCESS),
    // 阶段准入被驳回，后续阶段不再执行，构建按成功结束
    SUCCESS_STAGE_ABORT("successStageAbort", BuildEndCategory.SUCCESS);

    companion object {
        fun parse(name: String?): BuildEndType? {
            return try {
                if (name == null) null else valueOf(name)
            } catch (ignored: Exception) {
                null
            }
        }
    }
}

/**
 * 构建终态大类，供前端按类别渲染不同样式的详情卡片（失败卡片/超时卡片/取消卡片/成功卡片）。
 *
 * 归类依据是**结束成因**（[BuildEndType.category]），而非构建最终状态。二者并不总是一致：
 * Job执行超时、Agent心跳超时走的是 TERMINATE 事件链路，构建最终状态可能是
 * CANCELED / TERMINATE / FAILED 中的任意一种，但对用户而言它就是一次超时，
 * 应当渲染成超时卡片并展示超时时限与终止位置。构建的真实状态由 `ModelRecord.status` 单独表达。
 */
enum class BuildEndCategory {
    CANCEL,
    FAIL,
    TIMEOUT,
    SUCCESS
}
