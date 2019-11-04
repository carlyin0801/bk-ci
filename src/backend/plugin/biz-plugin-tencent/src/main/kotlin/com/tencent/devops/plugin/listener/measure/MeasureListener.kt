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

package com.tencent.devops.plugin.listener.measure

import com.tencent.devops.common.api.util.OkhttpUtils
import com.tencent.devops.common.event.listener.Listener
import com.tencent.devops.common.event.pojo.measure.MeasureRequest
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * deng
 * 2019-05-15
 */
@Component
class MeasureListener : Listener<MeasureRequest> {

    override fun execute(event: MeasureRequest) {
        val startEpoch = System.currentTimeMillis()
        try {
            logger.info("[${event.projectId}|${event.pipelineId}|${event.buildId}] Start to send the measure listener")
            val request = Request.Builder()
                .url(event.url)
                .post(RequestBody.create(JSON, event.request))
                .build()

            OkhttpUtils.doHttp(request).use { response ->
                    val body = response.body()?.string()
                    if (!response.isSuccessful) {
                        logger.warn("[${event.projectId}|${event.pipelineId}|${event.buildId}] " +
                            "Fail to send the measure data - (${event.url}|${response.code()}|${response.message()}|$body)")
                    }
                }
        } finally {
            logger.info("It took ${System.currentTimeMillis() - startEpoch}ms to send the measure data")
        }
    }

    companion object {
        private val JSON = MediaType.parse("application/json;charset=utf-8")
        private val logger = LoggerFactory.getLogger(MeasureListener::class.java)
    }
}