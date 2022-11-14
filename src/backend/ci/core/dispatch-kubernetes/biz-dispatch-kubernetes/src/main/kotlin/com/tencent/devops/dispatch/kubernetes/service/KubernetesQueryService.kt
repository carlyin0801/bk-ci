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

package com.tencent.devops.dispatch.kubernetes.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.dispatch.kubernetes.utils.KubernetesApiUtils
import io.fabric8.kubernetes.api.model.apps.Deployment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class KubernetesQueryService @Autowired constructor() {

    private val logger = LoggerFactory.getLogger(KubernetesQueryService::class.java)

    fun getKubernetesDeploymentInfo(
        userId: String,
        namespaceName: String,
        deploymentName: String,
        apiUrl: String,
        token: String
    ): Result<Deployment> {
        logger.info("getKubernetesDeploymentInfo params: [$userId|$namespaceName|$deploymentName|$apiUrl")
        val kubernetesClient = KubernetesApiUtils.getKubernetesClient(apiUrl, token)
        val deployment = kubernetesClient.apps().deployments().inNamespace(namespaceName)
            .withName(deploymentName).get()
        return Result(deployment)
    }

    fun getKubernetesDeploymentInfos(
        userId: String,
        namespaceName: String,
        deploymentNames: String,
        apiUrl: String,
        token: String
    ): Result<Map<String, Deployment>> {
        logger.info("getKubernetesDeploymentInfos params: [$userId|$namespaceName|$deploymentNames|$apiUrl")
        val kubernetesClient = KubernetesApiUtils.getKubernetesClient(apiUrl, token)
        val deploymentNameList = deploymentNames.split(",")
        val deploymentMap = mutableMapOf<String, Deployment>()
        deploymentNameList.forEach {
            val deployment = kubernetesClient.apps().deployments().inNamespace(namespaceName).withName(it).get()
            deploymentMap[it] = deployment
        }
        return Result(deploymentMap)
    }
}
