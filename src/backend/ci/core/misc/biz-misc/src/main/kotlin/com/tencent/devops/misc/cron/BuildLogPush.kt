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

package com.tencent.devops.misc.cron

import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.log.model.pojo.LogPushEvent
import com.tencent.devops.log.model.pojo.PushType
import com.tencent.devops.log.utils.LogDispatcher
import com.tencent.devops.log.websocket.BuildLogPageBuild
import com.tencent.devops.log.websocket.LogPushRedisUtlis
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class BuildLogPush @Autowired constructor(
    private val rabbitTemplate: RabbitTemplate,
    private val redisOperation: RedisOperation
) {
    companion object {
        private val logger = LoggerFactory.getLogger(BuildLogPush::class.java)
        private const val LOCK_KEY = "env_cron_updateCanUpgradeAgentList"
    }

    @Scheduled(fixedDelay = 3000)
    fun excuteTagLogPush() {
        val pushStatus = LogPushRedisUtlis.getAllPushStatusByTag(redisOperation)
        pushStatus?.forEach {
            with(it) {
                val page = BuildLogPageBuild().buildTagPage(buildId, id, sessionId)
                logger.info("Job build log websocket: page[$page], buildId:[$buildId],tag:[$id]")
                // 发送该Tag的一个空log事件
                LogDispatcher.dispatch(
                    rabbitTemplate = rabbitTemplate,
                    event = LogPushEvent(
                        buildId = buildId,
                        logs = listOf(),
                        type = PushType.TAG,
                        pushStatus = it
                    ))
                LogPushRedisUtlis.writePushStatusByTag(redisOperation, buildId, id, lastLineNum, sessionId)
            }
        }
    }

    @Scheduled(fixedDelay = 3000)
    fun excuteJobLogPush() {
        val pushStatus = LogPushRedisUtlis.getAllPushStatusByJobId(redisOperation)
        pushStatus?.forEach {
            with(it) {
                val page = BuildLogPageBuild().buildJobPage(buildId, id ,sessionId)
                logger.info("Job build log websocket: page[$page], buildId:[$buildId],tag:[$id]")
                // 发送该Job的一个空log事件
                LogDispatcher.dispatch(
                    rabbitTemplate = rabbitTemplate,
                    event = LogPushEvent(
                        buildId = buildId,
                        logs = listOf(),
                        type = PushType.JOB,
                        pushStatus = it
                    ))
                LogPushRedisUtlis.writePushStatusByJobId(redisOperation, buildId, id, lastLineNum, sessionId)
            }
        }
    }
}