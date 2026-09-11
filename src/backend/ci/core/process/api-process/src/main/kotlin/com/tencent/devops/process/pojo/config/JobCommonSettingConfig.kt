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

package com.tencent.devops.process.pojo.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class JobCommonSettingConfig {

    @Value("\${pipeline.setting.common.stage.job.maxTaskNum:20}")
    val maxTaskNum: Int = 20

    @Value("\${pipeline.setting.common.stage.job.maxPostTaskNum:5}")
    val maxPostTaskNum: Int = 5

    /**
     * #13602 Job收尾步骤禁用的挂起类插件atomCode清单，逗号分隔。
     *
     * 收尾步骤运行时Job结论已定，此时再挂起会把已终态的Job重新拖住，延后资源释放与构建结论落定，
     * 与「收尾不影响主流程」的定位冲突。人工审核、质量红线卡点、前置暂停已按类型直接拦截，
     * 这里用来补充定时等待一类无法按类型识别的市场插件，按部署环境配置。
     */
    @Value("\${pipeline.setting.common.stage.job.postStepForbiddenAtoms:}")
    val postStepForbiddenAtoms: String = ""
}
