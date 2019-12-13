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

package com.tencent.devops.log.websocket

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.log.model.pojo.PushStatus

object LogPushRedisUtlis {

    // 记录tag-pushStatus映射。 user: session = 1:1 。同一个user，可以在不同端登录，可能产生多个session
    val LOG_PUSH_TAG_STATUS_REDIS_KEY = "BK:logPush:pushStatus:key"
    // 记录tag-pushStatus映射。 user: session = 1:1 。同一个user，可以在不同端登录，可能产生多个session
    val LOG_PUSH_JOB_STATUS_REDIS_KEY = "BK:logPush:pushStatus:key"
    // 记录tag-pushStatus映射。 user: session = 1:1 。同一个user，可以在不同端登录，可能产生多个session
    val LOG_PUSH_TAG_SESSION_REDIS_KEY = "BK:logPush:tag:sessionId:key:"
    // 记录jobId-pushStatus映射。 session: page = 1:1。 同一个session一次只能停留在一个页面
    val LOG_PUSH_JOB_SESSION_REDIS_KEY = "BK:logPush:job:sessionId:key:"
    // 记录pushStatus-timeout映射。  session : timeout = 1:1。 同一个session，超时于登录后5天
    val STATUS_TIMEOUT_REDIS_KEY = "BK:logPush:pushStatus:timeOut:key"
    // 每个PushStatus的有效时间上限
    val TIMEOUT_LIMITED: Long = 86400

    // 写入tag,sessionId,pushStatus映射
    fun writePushStatusByTag(redisOperation: RedisOperation, buildId: String, tag: String, lineNo: Long, sessionId: String) {
        redisOperation.hset(
            key = LOG_PUSH_TAG_STATUS_REDIS_KEY,
            hashKey = sessionId,
            values = JsonUtil.toJson(PushStatus(buildId, tag, sessionId, lineNo, System.currentTimeMillis()))
        )
        redisOperation.hset(
            key = "$LOG_PUSH_TAG_SESSION_REDIS_KEY$buildId:$tag",
            hashKey = sessionId,
            values = sessionId
        )
    }

    // 写入jobId,sessionId,pushStatus映射
    fun writePushStatusByJobId(redisOperation: RedisOperation, buildId: String, jobId: String, lineNo: Long, sessionId: String) {
        redisOperation.hset(
            key = LOG_PUSH_JOB_STATUS_REDIS_KEY,
            hashKey = sessionId,
            values = JsonUtil.toJson(PushStatus(buildId, jobId, sessionId, lineNo, System.currentTimeMillis()))
        )
        redisOperation.hset(
            key = "$LOG_PUSH_JOB_SESSION_REDIS_KEY$buildId:$jobId",
            hashKey = sessionId,
            values = sessionId
        )
    }

    // 获取tag下某个session的PushStatus
    fun getPushStatusListByTagSession(redisOperation: RedisOperation, sessionId: String): PushStatus? {
        val str = redisOperation.hget(LOG_PUSH_TAG_STATUS_REDIS_KEY, sessionId)
        return if (str != null) {
            JsonUtil.to(str, PushStatus::class.java)
        } else null
    }

    // 获取Job下某个session的PushStatus
    fun getPushStatusListByJobIdSession(redisOperation: RedisOperation, sessionId: String): PushStatus? {
        val str = redisOperation.hget(LOG_PUSH_JOB_STATUS_REDIS_KEY, sessionId)
        return if (str != null) {
            JsonUtil.to(str, PushStatus::class.java)
        } else null
    }

    // 获取tag对应的所有Session
    fun getSessionListByTag(redisOperation: RedisOperation, buildId: String, tag: String): List<String>? {
        return redisOperation.hvalues("$LOG_PUSH_TAG_SESSION_REDIS_KEY$buildId:$tag")
    }

    // 获取jobId对应的Session
    fun getSessionListByJobId(redisOperation: RedisOperation, buildId: String, jobId: String): List<String>? {
        return redisOperation.hvalues("$LOG_PUSH_JOB_SESSION_REDIS_KEY$buildId:$jobId")
    }

    // 获取所有Session的PushStatus
    fun getAllPushStatusByTag(redisOperation: RedisOperation): Set<PushStatus>? {
        val result = redisOperation.hvalues(LOG_PUSH_TAG_STATUS_REDIS_KEY)
        val allStatus = setOf<PushStatus>()
        if (result == null) return null
        result.forEach {
            allStatus.plus(JsonUtil.to(it, PushStatus::class.java))
        }
        return allStatus
    }

    // 获取所有Session的PushStatus
    fun getAllPushStatusByJobId(redisOperation: RedisOperation): Set<PushStatus>? {
        val result = redisOperation.hvalues(LOG_PUSH_JOB_STATUS_REDIS_KEY)
        val allStatus = setOf<PushStatus>()
        if (result == null) return null
        result.forEach {
            allStatus.plus(JsonUtil.to(it, PushStatus::class.java))
        }
        return allStatus
    }

    // 根据tag清理pushStatus对应的记录
    fun cleanPushStatusByTag(redisOperation: RedisOperation, buildId: String, tag: String) {
        getSessionListByTag(redisOperation, buildId, tag)?.forEach {
            redisOperation.hdelete(
                key = LOG_PUSH_TAG_STATUS_REDIS_KEY,
                hashKey = it
            )
            redisOperation.hdelete(
                key = "$LOG_PUSH_TAG_SESSION_REDIS_KEY$buildId:$tag",
                hashKey = it
            )
        }
    }

    // 根据jobId清理pushStatus对应的记录
    fun cleanPushStatusByJobId(redisOperation: RedisOperation, buildId: String, jobId: String) {
        getSessionListByJobId(redisOperation, buildId, jobId)?.forEach {
            redisOperation.hdelete(
                key = LOG_PUSH_JOB_STATUS_REDIS_KEY,
                hashKey = it
            )
            redisOperation.hdelete(
                key = "$LOG_PUSH_JOB_SESSION_REDIS_KEY$buildId:$jobId",
                hashKey = it
            )
        }
    }
}