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

package com.tencent.devops.dispatch.kubernetes.utils

import com.tencent.devops.dispatch.kubernetes.pojo.KubernetesLabel
import com.tencent.devops.dispatch.kubernetes.pojo.KubernetesLimitRange
import com.tencent.devops.dispatch.kubernetes.pojo.KubernetesRepo
import io.fabric8.kubernetes.api.model.LimitRangeBuilder
import io.fabric8.kubernetes.api.model.LimitRangeItem
import io.fabric8.kubernetes.api.model.Namespace
import io.fabric8.kubernetes.api.model.NamespaceBuilder
import io.fabric8.kubernetes.api.model.Quantity
import io.fabric8.kubernetes.api.model.Secret
import io.fabric8.kubernetes.api.model.SecretBuilder
import io.fabric8.kubernetes.api.model.Service
import io.fabric8.kubernetes.api.model.apps.Deployment
import io.fabric8.kubernetes.api.model.extensions.Ingress
import io.fabric8.kubernetes.client.ConfigBuilder
import io.fabric8.kubernetes.client.DefaultKubernetesClient
import io.fabric8.kubernetes.client.KubernetesClient
import org.apache.commons.codec.binary.Base64
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

@SuppressWarnings("ALL")
object KubernetesApiUtils {

    private val logger = LoggerFactory.getLogger(KubernetesApiUtils::class.java)

    private const val cpu = "cpu"

    private const val memory = "memory"

    private val kubernetesClientMap = ConcurrentHashMap<String, KubernetesClient>()

    fun getKubernetesClientMap(): ConcurrentHashMap<String, KubernetesClient> {
        return kubernetesClientMap
    }

    private fun createKubernetesClient(
        apiUrl: String,
        token: String
    ): KubernetesClient {
        val config = ConfigBuilder()
            .withMasterUrl(apiUrl)
            .withTrustCerts(true)
            .withOauthToken(token)
            .build()
        return DefaultKubernetesClient(config)
    }

    fun getKubernetesClient(
        apiUrl: String,
        token: String
    ): KubernetesClient {
        val kubernetesClientKey = "$apiUrl;$token"
        var kubernetesClient = kubernetesClientMap[kubernetesClientKey]
        if (kubernetesClient == null) {
            // 删除缓存中bcUrl相同但token因为过期不相同的记录
            kubernetesClientMap.entries.removeIf { entry -> entry.key.startsWith("$apiUrl;") }
            kubernetesClient = createKubernetesClient(apiUrl, token)
            kubernetesClientMap[kubernetesClientKey] = kubernetesClient
        }
        return kubernetesClient
    }

    /**
     * 创建k8s命名空间
     * @param apiUrl API接口路径
     * @param token token
     * @param namespaceName 命名空间名称
     * @param labelInfo 标签信息
     * @param limitRangeInfo k8s资源限制信息
     */
    fun createNamespace(
        apiUrl: String,
        token: String,
        namespaceName: String,
        labelInfo: KubernetesLabel,
        limitRangeInfo: KubernetesLimitRange? = null
    ): Namespace {
        logger.info("createNamespace params: [$apiUrl|$namespaceName|$labelInfo|$limitRangeInfo]")
        val kubernetesClient = getKubernetesClient(apiUrl, token)
        var ns = kubernetesClient.namespaces().withName(namespaceName).get()
        if (ns == null) {
            ns =
                NamespaceBuilder().withNewMetadata().withName(namespaceName)
                    .addToLabels(labelInfo.labelKey, labelInfo.labelValue).endMetadata()
                    .build()
            kubernetesClient.namespaces().createOrReplace(ns)
        }
        val limitRangeItem = LimitRangeItem()
        if (null != limitRangeInfo) {
            limitRangeItem.default = mapOf(
                cpu to Quantity(limitRangeInfo.defaultCpu),
                memory to Quantity(limitRangeInfo.defaultMemory)
            )
            limitRangeItem.defaultRequest = mapOf(
                cpu to Quantity(limitRangeInfo.defaultRequestCpu),
                memory to Quantity(limitRangeInfo.defaultRequestMemory)
            )
            limitRangeItem.type = limitRangeInfo.limitType
            val limitRange = LimitRangeBuilder().withNewMetadata().withName("$namespaceName-limit")
                .endMetadata().withNewSpec().addToLimits(limitRangeItem).endSpec().build()
            kubernetesClient.limitRanges().inNamespace(namespaceName).createOrReplace(limitRange)
        }
        return ns
    }

    /**
     * 创建k8s拉取镜像secret
     * @param apiUrl API接口路径
     * @param token token
     * @param namespaceName 命名空间名称
     * @param secretName 秘钥名称
     * @param kubernetesRepoInfo k8s仓库信息
     */
    fun createImagePullSecret(
        apiUrl: String,
        token: String,
        secretName: String,
        namespaceName: String,
        kubernetesRepoInfo: KubernetesRepo
    ): Secret {
        logger.info("createImagePullSecret params: [$apiUrl|$secretName|$namespaceName|$kubernetesRepoInfo]")
        val kubernetesClient = getKubernetesClient(apiUrl, token)
        var secret = kubernetesClient.secrets().inNamespace(namespaceName).withName(secretName).get()
        if (secret == null) {
            val secretData: HashMap<String, String> = HashMap(1)
            val username = kubernetesRepoInfo.username
            val password = kubernetesRepoInfo.password
            val basicAuth = String(Base64.encodeBase64("$username:$password".toByteArray()))
            var dockerCfg = String.format(
                "{ " +
                    " \"auths\": { " +
                    "  \"%s\": { " +
                    "   \"username\": \"%s\", " +
                    "   \"password\": \"%s\", " +
                    "   \"email\": \"%s\", " +
                    "   \"auth\": \"%s\" " +
                    "  } " +
                    " } " +
                    "}",
                kubernetesRepoInfo.registryUrl,
                username,
                password,
                kubernetesRepoInfo.email,
                basicAuth
            )
            dockerCfg = String(Base64.encodeBase64(dockerCfg.toByteArray(Charsets.UTF_8)), Charsets.UTF_8)
            secretData[".dockerconfigjson"] = dockerCfg
            val secretBuilder = SecretBuilder()
                .withNewMetadata()
                .withName(secretName)
                .withNamespace(namespaceName)
                .endMetadata()
                .withData(secretData)
                .withType("kubernetes.io/dockerconfigjson")
            secret = kubernetesClient.secrets().inNamespace(namespaceName).createOrReplace(secretBuilder.build())
        }
        return secret
    }

    /**
     * 创建deployment
     * @param apiUrl API接口路径
     * @param token token
     * @param namespaceName 命名空间名称
     * @param deployment 无状态部署对象
     */
    fun createDeployment(
        apiUrl: String,
        token: String,
        namespaceName: String,
        deployment: Deployment
    ): Deployment {
        val kubernetesClient = getKubernetesClient(apiUrl, token)
        return kubernetesClient.apps().deployments().inNamespace(namespaceName).createOrReplace(deployment)
    }

    /**
     * 创建service
     * @param apiUrl API接口路径
     * @param token token
     * @param namespaceName 命名空间名称
     * @param service service对象
     */
    fun createService(
        apiUrl: String,
        token: String,
        namespaceName: String,
        service: Service
    ): Service {
        val kubernetesClient = getKubernetesClient(apiUrl, token)
        return kubernetesClient.services().inNamespace(namespaceName).createOrReplace(service)
    }

    /**
     * 创建ingress
     * @param apiUrl API接口路径
     * @param token token
     * @param namespaceName 命名空间名称
     * @param ingress ingress对象
     */
    fun createIngress(
        apiUrl: String,
        token: String,
        namespaceName: String,
        ingress: Ingress
    ): Ingress {
        val kubernetesClient = getKubernetesClient(apiUrl, token)
        return kubernetesClient.extensions().ingresses().inNamespace(namespaceName).createOrReplace(ingress)
    }
}
