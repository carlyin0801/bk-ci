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

package com.tencent.devops.message.service.impl

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.service.utils.MessageCodeUtil
import com.tencent.devops.message.config.TransactionMessageConfig
import com.tencent.devops.message.dao.TransactionMessageDao
import com.tencent.devops.message.pojo.QueryTransactionMessageParam
import com.tencent.devops.message.pojo.TransactionMessage
import com.tencent.devops.message.pojo.UpdateTransactionMessageParam
import com.tencent.devops.message.pojo.enums.MessageStatusEnum
import com.tencent.devops.message.service.TransactionMessageService
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/**
 * 事务消息逻辑处理
 *
 * since: 2019-12-28
 */
@Service
class TransactionMessageServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val transactionMessageDao: TransactionMessageDao,
    private val rabbitTemplate: RabbitTemplate,
    private val transactionMessageConfig: TransactionMessageConfig
    ) : TransactionMessageService {

    private val logger = LoggerFactory.getLogger(TransactionMessageServiceImpl::class.java)

    override fun saveMessageWaitingConfirm(message: TransactionMessage): Result<Boolean> {
        logger.info("saveMessageWaitingConfirm message is:$message")
        transactionMessageDao.addTransactionMessage(dslContext, message)
        return Result(true)
    }


    override fun confirmAndSendMessage(messageId: String): Result<Boolean> {
        logger.info("confirmAndSendMessage messageId is:$messageId")
        val message = getMessageByMessageId(messageId).data ?: return MessageCodeUtil.generateResponseDataObject(
            CommonMessageCode.PARAMETER_IS_INVALID,
            arrayOf(messageId),
            false
        )
        logger.info("confirmAndSendMessage message is:$message")
        transactionMessageDao.updateTransactionMessage(
            dslContext = dslContext,
            userId = message.creator,
            messageId = messageId,
            updateTransactionMessageParam = UpdateTransactionMessageParam(
              status = MessageStatusEnum.SENDING
            )
        )
        rabbitTemplate.convertAndSend(message.consumerQueue, message.messageBody)
        return Result(true)
    }


    override fun saveAndSendMessage(message: TransactionMessage): Result<Boolean> {
        logger.info("saveAndSendMessage message is:$message")
        transactionMessageDao.addTransactionMessage(dslContext, message)
        rabbitTemplate.convertAndSend(message.consumerQueue, message.messageBody)
        return Result(true)
    }


    override fun directSendMessage(message: TransactionMessage): Result<Boolean> {
        logger.info("directSendMessage message is:$message")
        rabbitTemplate.convertAndSend(message.consumerQueue, message.messageBody)
        return Result(true)
    }


    override fun reSendMessage(message: TransactionMessage): Result<Boolean> {
        logger.info("reSendMessage message is:$message")
        transactionMessageDao.updateTransactionMessage(
            dslContext = dslContext,
            userId = message.creator,
            messageId = message.messageId,
            updateTransactionMessageParam = UpdateTransactionMessageParam(
                messageSendTimes = message.messageSendTimes + 1
            )
        )
        rabbitTemplate.convertAndSend(message.consumerQueue, message.messageBody)
        return Result(true)
    }


    override fun reSendMessageByMessageId(messageId: String): Result<Boolean> {
        logger.info("reSendMessageByMessageId messageId is:$messageId")
        val message = getMessageByMessageId(messageId).data ?: return MessageCodeUtil.generateResponseDataObject(
            CommonMessageCode.PARAMETER_IS_INVALID,
            arrayOf(messageId),
            false
        )
        logger.info("reSendMessageByMessageId message is:$message")
        val  updateTransactionMessageParam = UpdateTransactionMessageParam(
            messageSendTimes = message.messageSendTimes + 1
        )
        val maxTimes = transactionMessageConfig.messageMaxSendTimes
        if (message.messageSendTimes >= maxTimes) {
            updateTransactionMessageParam.isDead = true  // 重发五次失败则设置为死亡队列
        }
        logger.info("reSendMessageByMessageId updateTransactionMessageParam is:$updateTransactionMessageParam")
        transactionMessageDao.updateTransactionMessage(
            dslContext = dslContext,
            userId = message.creator,
            messageId = message.messageId,
            updateTransactionMessageParam = updateTransactionMessageParam
        )
        rabbitTemplate.convertAndSend(message.consumerQueue, message.messageBody)
        return Result(true)
    }


    override fun setMessageToDead(messageId: String): Result<Boolean> {
        logger.info("setMessageToDead messageId is:$messageId")
        val message = getMessageByMessageId(messageId).data ?: return MessageCodeUtil.generateResponseDataObject(
            CommonMessageCode.PARAMETER_IS_INVALID,
            arrayOf(messageId),
            false
        )
        logger.info("setMessageToDead message is:$message")
        transactionMessageDao.updateTransactionMessage(
            dslContext = dslContext,
            userId = message.creator,
            messageId = message.messageId,
            updateTransactionMessageParam = UpdateTransactionMessageParam(
                isDead = true
            )
        )
        return Result(true)
    }


    override fun getMessageByMessageId(messageId: String): Result<TransactionMessage?> {
        logger.info("getMessageByMessageId messageId is:$messageId")
        val messageRecord = transactionMessageDao.getTransactionMessageById(dslContext, messageId)
        logger.info("getMessageByMessageId messageRecord is:$messageRecord")
        return Result(
            if (messageRecord == null) {
                null
            } else {
                transactionMessageDao.convert(messageRecord)
            }
        )
    }


    override fun deleteMessageByMessageId(messageId: String): Result<Boolean> {
        logger.info("deleteMessageByMessageId messageId is:$messageId")
        transactionMessageDao.deleteTransactionMessageById(dslContext, messageId)
        return Result(true)
    }


    override fun reSendAllDeadMessageByQueueName(queueName: String): Result<Boolean> {
        logger.info("reSendAllDeadMessageByQueueName queueName is:$queueName")
        val queryTransactionMessageParam = QueryTransactionMessageParam(
            consumerQueue = queueName,
            isDead = true,
            descFlag = false
        )
        val messageList = transactionMessageDao.getTransactionMessages(dslContext, queryTransactionMessageParam)
        messageList?.forEach { message ->
            transactionMessageDao.updateTransactionMessage(
                dslContext = dslContext,
                userId = message.creator,
                messageId = message.messageId,
                updateTransactionMessageParam = UpdateTransactionMessageParam(
                    messageSendTimes = message.messageSendTimes + 1
                )
            )
            rabbitTemplate.convertAndSend(message.consumerQueue, message.messageBody)
        }
        return Result(true)
    }


    override fun getTransactionMessages(queryTransactionMessageParam: QueryTransactionMessageParam): Result<List<TransactionMessage>?> {
        logger.info("getTransactionMessages queryTransactionMessageParam is :$queryTransactionMessageParam")
        val transactionMessageList = mutableListOf<TransactionMessage>()
        val transactionMessageRecords = transactionMessageDao.getTransactionMessages(dslContext, queryTransactionMessageParam)
        transactionMessageRecords?.forEach {
            transactionMessageList.add(
                transactionMessageDao.convert(it)
            )
        }
        return Result(transactionMessageList)
    }
}
