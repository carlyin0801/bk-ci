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

package com.tencent.devops.process.engine.atom.vm.modelcheck

import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.enums.ChannelCode
import com.tencent.devops.common.pipeline.type.StoreDispatchType
import com.tencent.devops.common.pipeline.type.docker.ImageType
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.engine.service.store.StoreImageService
import com.tencent.devops.process.plugin.ContainerBizPlugin
import com.tencent.devops.process.plugin.annotation.ContainerBiz
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired

/**
 * @Description
 * @Date 2019/12/15
 * @Version 1.0
 */
@ContainerBiz
class DispatchTypeBizPlugin @Autowired constructor(
    val storeImageService: StoreImageService
) : ContainerBizPlugin<VMBuildContainer> {

    private val logger = LoggerFactory.getLogger(DispatchTypeBizPlugin::class.java)

    override fun containerClass(): Class<VMBuildContainer> {
        return VMBuildContainer::class.java
    }

    override fun afterCreate(container: VMBuildContainer, projectId: String, pipelineId: String, pipelineName: String, userId: String, channelCode: ChannelCode) {
    }

    override fun beforeDelete(container: VMBuildContainer, userId: String, pipelineId: String?) {
    }

    override fun check(container: VMBuildContainer, appearedCnt: Int, projectId: String, userId: String, pipelineId: String?) {
        if (container.elements.isEmpty()) {
            throw ErrorCodeException(defaultMessage = "Job需要至少有一个任务插件", errorCode = ProcessMessageCode.ERROR_PIPELINE_JOB_NEED_TASK)
        }
        val dispatchType = container.dispatchType
        if (dispatchType is StoreDispatchType) {
            if (dispatchType.imageType == ImageType.BKSTORE) {
                // BKSTORE的镜像确保code与version不为空
                if (dispatchType.imageCode.isNullOrBlank()) {
                    throw ErrorCodeException(defaultMessage = "从研发商店选择的镜像code不可为空", errorCode = ProcessMessageCode.ERROR_PIPELINE_DISPATCH_STORE_IMAGE_CODE_BLANK)
                }
                if (dispatchType.imageVersion.isNullOrBlank()) {
                    throw ErrorCodeException(defaultMessage = "从研发商店选择的镜像version不可为空", errorCode = ProcessMessageCode.ERROR_PIPELINE_DISPATCH_STORE_IMAGE_VERSION_BLANK)
                }
                //根据商店镜像信息回填value，兼容v1
                val imageRepoInfo = storeImageService.getImageRepoInfo(
                    userId = userId,
                    projectId = projectId,
                    pipelineId = pipelineId ?: "",
                    buildId = "",
                    imageCode = dispatchType.imageCode,
                    imageVersion = dispatchType.imageVersion,
                    defaultPrefix = ""
                )
                if (imageRepoInfo.sourceType == ImageType.BKDEVOPS) {
                    var v1Value = imageRepoInfo.repoName + ":" + imageRepoInfo.repoTag
                    v1Value = v1Value.removePrefix("paas/bkdevops/")
                    dispatchType.value = v1Value
                } else if (imageRepoInfo.sourceType == ImageType.THIRD) {
                    if (imageRepoInfo.repoUrl.isBlank()) {
                        dispatchType.value = imageRepoInfo.repoName + ":" + imageRepoInfo.repoTag
                    } else {
                        dispatchType.value = imageRepoInfo.repoUrl + "/" + imageRepoInfo.repoName + ":" + imageRepoInfo.repoTag
                    }
                    dispatchType.credentialId = imageRepoInfo.ticketId
                    if (imageRepoInfo.ticketId != projectId) {
                        logger.warn("Pipeline $pipelineId use third image with ticket from other project:${imageRepoInfo.ticketId}, which should be used in v2")
                    }
                } else {
                    logger.error("Unknown ImageSourceType:${imageRepoInfo.sourceType}")
                }
            } else {
                // 其余类型的镜像确保value不为空
                if (dispatchType.value.isBlank()) {
                    throw ErrorCodeException(defaultMessage = "非商店蓝盾源/第三方源的镜像value不可为空", errorCode = ProcessMessageCode.ERROR_PIPELINE_DISPATCH_VALUE_BLANK)
                }
            }
        }
    }
}
