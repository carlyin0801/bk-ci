package com.tencent.devops.common.websocket.dispatch.message

import com.tencent.devops.common.websocket.pojo.NotifyPost
import com.tencent.devops.common.websocket.pojo.WebSocketType

data class NotifyMqMessage(
    val buildId: String?,
    val projectId: String,
    val pipelineId: String,
    override val userId: String,
    override val pushType: WebSocketType,
    override var page: String?,
    override var notifyPost: NotifyPost
) : MqMessage(userId, pushType, page, notifyPost)