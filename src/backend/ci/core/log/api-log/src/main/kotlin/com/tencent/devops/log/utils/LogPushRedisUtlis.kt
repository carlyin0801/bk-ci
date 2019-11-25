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

package com.tencent.devops.log.utils

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.log.model.pojo.PushStatus

object LogPushRedisUtlis {

    // 记录tag-pushStatus映射。 user: session = 1:1 。同一个user，可以在不同端登录，可能产生多个session
    val LOG_PUSH_TAG_REDIS_KEY = "BK:logPush:tag:pushStatus:key:"
    // 记录jobId-pushStatus映射。 session: page = 1:1。 同一个session一次只能停留在一个页面。
    val LOG_PUSH_JOBID_REDIS_KEY = "BK:logPush:job:pushStatus:key:"
    // 记录pushStatus-timeout映射。  session : timeout = 1:1。 同一个session，超时于登录后5天。
    val STATUS_TIMEOUT_REDIS_KEY = "BK:logPush:pushStatus:timeOut:key:"

    // 写入tag,pushStatus映射
    fun writePushStatusByTag(redisOperation: RedisOperation, buildId: String, tag: String, lineNo: Long) {
        redisOperation.set(
            "$LOG_PUSH_TAG_REDIS_KEY$buildId:$tag",
            JsonUtil.toJson(PushStatus(lineNo, System.currentTimeMillis())),
            86400,
            true
        )
    }

    // 写入jobId,pushStatus映射
    fun writePushStatusByJobId(redisOperation: RedisOperation, buildId: String, jobId: String, lineNo: Long) {
        redisOperation.set(
            "$LOG_PUSH_JOBID_REDIS_KEY$buildId:$jobId",
            JsonUtil.toJson(PushStatus(lineNo, System.currentTimeMillis())),
            86400,
            true
        )
    }

    // 获取tag对应的pushStatus
    fun getPushStatusByTag(redisOperation: RedisOperation, buildId: String, tag: String): PushStatus? {
        val result = redisOperation.get("$LOG_PUSH_TAG_REDIS_KEY$buildId:$tag")
        return if (result == null) null
        else JsonUtil.to(result, PushStatus::class.java)
    }

    // 获取jobId对应的pushStatus
    fun getPushStatusByJobId(redisOperation: RedisOperation, buildId: String, jobId: String): PushStatus? {
        val result = redisOperation.get("$LOG_PUSH_JOBID_REDIS_KEY$buildId:$jobId")
        return if (result == null) null
        else JsonUtil.to(result, PushStatus::class.java)
    }

    // 获取所有和构建

    // 根据tag清理pushStatus对应的记录
    fun cleanPushStatusByTag(redisOperation: RedisOperation, buildId: String, tag: String) {
        redisOperation.delete("$LOG_PUSH_TAG_REDIS_KEY$buildId:$tag")
    }

    // 根据jobId清理pushStatus对应的记录
    fun cleanPushStatusByJobId(redisOperation: RedisOperation, buildId: String, jobId: String) {
        redisOperation.delete("$LOG_PUSH_JOBID_REDIS_KEY$buildId:$jobId")
    }
}