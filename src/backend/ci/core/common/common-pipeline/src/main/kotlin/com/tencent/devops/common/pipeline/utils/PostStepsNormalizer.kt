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

package com.tencent.devops.common.pipeline.utils

import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.Container

/**
 * 把Job收尾步骤(post-steps)归位到[Container.elements]末尾。
 *
 * 收尾步骤与主步骤共用同一个[Container.elements]，靠
 * [com.tencent.devops.common.pipeline.pojo.element.Element.isJobPostStep]区分，
 * 而引擎依赖「收尾段连续排在主步骤之后」来切分两段：`findTask`按taskSeq顺序扫描，
 * 扫到第一个收尾步骤时就认为主步骤区已结束、并把此刻的容器状态冻结为Job主状态。
 * 顺序错乱会让这个切分点提前，把还没跑的主步骤误当成收尾步骤。
 *
 * **为什么不给收尾步骤单开一个字段**：引擎有一条 taskSeq ↔ [Container.elements]下标 的不变式，
 * 执行记录回写(taskSeq-2索引elements)、插件post-action的parentElementJobIndex、
 * 矩阵子Job与父模板按下标对齐、位置索引与进度条都依赖它，所以执行态无论如何都得是单列表。
 * 再给编排态开第二种形态，等于让每一个读模型的出口都多背一次「记得转换」的义务——
 * 那是隐式契约，漏一处不报错不崩溃，只会让收尾步骤静悄悄显示在主步骤区。
 *
 * 因此这里只做一件事：保存前把顺序理顺。调用方（前端、YAML转换）按`主步骤 + 收尾步骤`拼好提交即可，
 * 万一穿插了也能自愈，不必让用户吃一个「必须排在最后」的报错。
 * 挂载点是`DefaultModelCheckPlugin.checkModelIntegrity`——保存与模板校验都必经此处，
 * 比挂在各个保存入口更难被绕过。幂等，可重复调用。
 */
object PostStepsNormalizer {

    fun normalize(model: Model) {
        model.stages.forEach { stage ->
            stage.containers.forEach { container -> normalize(container) }
        }
    }

    fun normalize(container: Container) {
        val firstPostStepIndex = container.elements.indexOfFirst { it.isJobPostStep() }
        if (firstPostStepIndex < 0) {
            return
        }
        // 已经连续在末尾时不重建列表，避免给没动过收尾步骤的流水线制造无意义的版本diff
        if (container.elements.drop(firstPostStepIndex).all { it.isJobPostStep() }) {
            return
        }
        val (postSteps, mainSteps) = container.elements.partition { it.isJobPostStep() }
        container.elements = mainSteps + postSteps
    }
}
