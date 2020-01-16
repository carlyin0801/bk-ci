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

package com.tencent.devops.message.biz.service.impl

import com.tencent.devops.common.client.Client
import com.tencent.devops.common.event.dispatcher.pipeline.mq.MQ
import com.tencent.devops.message.biz.service.AbstractMessageBusinessHandleService
import com.tencent.devops.message.pojo.TransactionMessage
import com.tencent.devops.message.service.TransactionMessageService
import com.tencent.devops.store.api.template.ServiceTemplateResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service(MQ.QUEUE_TEMPLATE_REL)
class TemplateRelMessageBusinessHandleServiceImpl @Autowired constructor(
    private val transactionMessageService: TransactionMessageService,
    private val client: Client
) : AbstractMessageBusinessHandleService() {

    private val logger = LoggerFactory.getLogger(TemplateRelMessageBusinessHandleServiceImpl::class.java)

    override fun handleWaitingConfirmTimeOutMessages(transactionMessage: TransactionMessage) {
        logger.info("handleWaitingConfirmTimeOutMessages transactionMessage is:$transactionMessage")
        val messageId = transactionMessage.messageId
        logger.info("begin handle the message, messageId is:$messageId")
        val templateCode = transactionMessage.data
        val template = client.get(ServiceTemplateResource::class).getTemplateBaseInfoByCode(templateCode!!).data
        // 如果模板关联成功，把消息改为待处理，并发送消息
        if (template != null) {
            // 确认并发送消息
            transactionMessageService.confirmAndSendMessage(messageId)
        } else {
            // 模板关联失败，则直接删除消息数据
            logger.info("template rel fail, messageId:$messageId delete")
            transactionMessageService.deleteMessageByMessageId(messageId)
        }
    }
}
