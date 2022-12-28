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

package com.tencent.devops.dispatch.kubernetes.listener

import com.tencent.devops.common.api.pojo.ErrorType
import com.tencent.devops.common.dispatch.sdk.BuildFailureException
import com.tencent.devops.common.dispatch.sdk.service.DispatchService
import com.tencent.devops.common.event.dispatcher.pipeline.PipelineEventDispatcher
import com.tencent.devops.common.remotedev.RemoteDevDispatcher
import com.tencent.devops.common.service.prometheus.BkTimed
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.dispatch.kubernetes.pojo.mq.WorkspaceCreateEvent
import com.tencent.devops.dispatch.kubernetes.pojo.mq.WorkspaceOperateEvent
import com.tencent.devops.dispatch.kubernetes.pojo.remotedev.WorkspaceReq
import com.tencent.devops.dispatch.kubernetes.service.RemoteDevService
import com.tencent.devops.remotedev.pojo.event.RemoteDevUpdateEvent
import com.tencent.devops.remotedev.pojo.event.UpdateEventType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component@Suppress("ALL")
class WorkspaceListener @Autowired constructor(
    private val remoteDevService: RemoteDevService,
    private val remoteDevDispatcher: RemoteDevDispatcher
) {

    @BkTimed
    fun handleWorkspaceCreate(event: WorkspaceCreateEvent) {
        var status = false
        try {
            logger.info("Start to handle workspace create ($event)")
            remoteDevService.createWorkspace(
                userId = event.userId,
                workspaceReq = WorkspaceReq(
                    workspaceId = 0L,
                    name = event.workspaceName,
                    repositoryUrl = event.repositoryUrl,
                    branch = event.branch,
                    devFilePath = event.devFilePath,
                    devFile = event.devFile,
                    oAuthToken = event.oAuthToken,
                    image = event.image
                )
            )

            status = true
        } catch (e: BuildFailureException) {
            status = false
            logger.error("Handle workspace create error.", e)
        } catch (t: Throwable) {
            status = false
            logger.error("Handle workspace create error.", t)
        } finally {
            // 业务逻辑处理完成回调remotedev事件
            remoteDevDispatcher.dispatch(RemoteDevUpdateEvent(
                traceId = event.traceId,
                userId = event.userId,
                workspaceName = event.workspaceName,
                type = UpdateEventType.CREATE,
                status = status
            ))
        }
    }

    @BkTimed
    fun handleWorkspaceOperate(event: WorkspaceOperateEvent) {
        try {
            logger.info("Start to handle workspace operate ($event)")
            when (event.type) {
                UpdateEventType.START -> {
                    remoteDevService.startWorkspace(event.userId, event.workspaceName)
                }
                UpdateEventType.STOP -> {
                    remoteDevService.stopWorkspace(event.userId, event.workspaceName)
                }
                UpdateEventType.DELETE -> {
                    remoteDevService.deleteWorkspace(event.userId, event.workspaceName)
                }
                else -> {

                }
            }
        } catch (t: Throwable) {
            logger.warn("Fail to handle workspace operate ($event)", t)
        } finally {
            // 业务逻辑处理完成回调remotedev事件
            remoteDevDispatcher.dispatch(RemoteDevUpdateEvent(
                traceId = event.traceId,
                userId = event.userId,
                workspaceName = event.workspaceName,
                type = event.type,
                status = true
            ))
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(WorkspaceListener::class.java)
    }
}
