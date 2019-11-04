/*
 * Tencent is pleased to support the open source community by making BK-REPO 蓝鲸制品库 available.
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

package com.tencent.devops.common.service.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * 通用配置
 */
@Component
class CommonConfig {

    /**
     * DevOps 构建机网关地址
     */
    @Value("\${devopsGateway.build:#{null}}")
    val devopsBuildGateway: String? = null

    /**
     * DevOps API网关地址
     */
    @Value("\${devopsGateway.host:#{null}}")
    val devopsHostGateway: String? = null

    /**
     * DevOps API网关地址
     */
    @Value("\${devopsGateway.api:#{null}}")
    val devopsApiGateway: String? = null

    /**
     * DevOps 外部地址
     */
    @Value("\${devopsGateway.outer:#{null}}")
    val devopsOuterHostGateWay: String? = null

    /**
     * DevOps 外部API地址
     */
    @Value("\${devopsGateway.outerApi:#{null}}")
    val devopsOuteApiHostGateWay: String? = null

    /**
     * 微服务端口
     */
    @Value("\${server.port:80}")
    val serverPort: Int = 80
}