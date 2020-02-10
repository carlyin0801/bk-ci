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

package com.tencent.devops.message.cron

import com.tencent.devops.common.redis.RedisLock
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.message.biz.service.AbstractMessageBusinessHandleService
import com.tencent.devops.message.config.TransactionMessageConfig
import com.tencent.devops.message.pojo.QueryTransactionMessageParam
import com.tencent.devops.message.pojo.TransactionMessage
import com.tencent.devops.message.pojo.enums.MessageStatusEnum
import com.tencent.devops.message.service.TransactionMessageService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.LocalDateTime

@Component
class MessageHandleJob @Autowired constructor(
    private val redisOperation: RedisOperation,
    private val transactionMessageService: TransactionMessageService,
    private val transactionMessageConfig: TransactionMessageConfig
) {

    companion object {
        private val logger = LoggerFactory.getLogger(MessageHandleJob::class.java)
        private const val HANDLE_WAITING_CONFIRM_TIME_OUT_MESSAGES_REDIS_KEY = "message:handle:waiting:confirm:time:out:messages:lock:key"
        private const val HANDLE_SENDING_CONFIRM_TIME_OUT_MESSAGES_REDIS_KEY = "message:handle:sending:confirm:time:out:messages:lock:key"
    }

    /**
     * 处理状态为“待确认”但已超时的消息.
     */
    @Scheduled(cron = "*/10 * * * * ?")
    fun handleWaitingConfirmTimeOutMessages() {
        logger.info("handleWaitingConfirmTimeOutMessages begin")
        // 获取配置的开始处理的时间
        val createTimeBefore = getCreateTimeBefore()
        val queryTransactionMessageParam = QueryTransactionMessageParam(
            status = MessageStatusEnum.WAITING_CONFIRM,
            createTime = createTimeBefore,
            descFlag = false
        )
        logger.info("handleWaitingConfirmTimeOutMessages queryTransactionMessageParam is:$queryTransactionMessageParam")
        val redisLock = RedisLock(redisOperation, HANDLE_WAITING_CONFIRM_TIME_OUT_MESSAGES_REDIS_KEY, 20)
        try {
            val lockSuccess = redisLock.tryLock()
            if (!lockSuccess) {
                logger.info("the other process is processing the job")
                return
            }
            val messageMap = getMessageMap(queryTransactionMessageParam)
            handleWaitingConfirmTimeOutMessages(messageMap)
        } catch (e: Exception) {
            logger.error("handleWaitingConfirmTimeOutMessages has error：", e)
        } finally {
            redisLock.unlock()
        }
    }

    /**
     * 处理状态为“待发送”但超时没有被成功消费确认的消息
     */
    @Scheduled(cron = "0/10 * *  * * ?")
    fun handleSendingTimeOutMessage() {
        logger.info("handleSendingTimeOutMessage begin")
        // 获取配置的开始处理的时间
        val createTimeBefore = getCreateTimeBefore()
        val queryTransactionMessageParam = QueryTransactionMessageParam(
            status = MessageStatusEnum.SENDING,
            createTime = createTimeBefore,
            validFlag = true,
            descFlag = false,
            isDead = false
        )
        logger.info("handleSendingTimeOutMessage queryTransactionMessageParam is:$queryTransactionMessageParam")
        val redisLock = RedisLock(redisOperation, HANDLE_SENDING_CONFIRM_TIME_OUT_MESSAGES_REDIS_KEY, 20)
        try {
            val lockSuccess = redisLock.tryLock()
            if (!lockSuccess) {
                logger.info("the other process is processing the job")
                return
            }
            val messageMap = getMessageMap(queryTransactionMessageParam)
            handleSendingTimeOutMessage(messageMap)
        } catch (e: Exception) {
            logger.error("handleSendingTimeOutMessage has error：", e)
        } finally {
            redisLock.unlock()
        }
    }

    private fun getMessageMap(queryTransactionMessageParam: QueryTransactionMessageParam): Map<String, TransactionMessage> {
        val messageMap = HashMap<String, TransactionMessage>()
        val messageList = transactionMessageService.getTransactionMessages(queryTransactionMessageParam).data
        messageList?.forEach { message ->
            messageMap[message.messageId] = message
        }
        return messageMap
    }

    /**
     * 获取消息的开始处理的时间
     */
    private fun getCreateTimeBefore(): LocalDateTime {
        val durationTime = transactionMessageConfig.messageHandleDuration
        return LocalDateTime.now().minusSeconds(durationTime.toLong())
    }

    /**
     * 处理待确认状态的消息
     */
    private fun handleWaitingConfirmTimeOutMessages(messageMap: Map<String, TransactionMessage>) {
        logger.info("handleWaitingConfirmTimeOutMessages begin,messageSize is: ${messageMap.size}")
        messageMap.forEach { (messageId, message) ->
            try {
                // 根据队列名称获取队列对应的逻辑处理器
                val messageBusinessHandleService =
                    SpringContextUtil.getBean(AbstractMessageBusinessHandleService::class.java, message.consumerQueue)
                // 处理逻辑
                messageBusinessHandleService.handleWaitingConfirmTimeOutMessages(transactionMessage = message)
            } catch (e: Exception) {
                logger.error("handle the message error, messageId is:$messageId", e)
            }
        }
    }

    /**
     * 处理待发送状态的消息
     */
    private fun handleSendingTimeOutMessage(messageMap: Map<String, TransactionMessage>) {
        logger.info("handleSendingTimeOutMessage begin,messageSize is : ${messageMap.size}")
        // 单条消息处理
        messageMap.forEach { (messageId, message) ->
            try {
                logger.info("begin handle the message, messageId is:$messageId", messageId)
                // 判断发送次数
                val maxTimes = transactionMessageConfig.messageMaxSendTimes
                logger.info(" messageId:$messageId has send ${message.messageSendTimes} times")
                if (maxTimes <= message.messageSendTimes) {
                    // 标记为死亡
                    transactionMessageService.setMessageToDead(messageId)
                    return@forEach
                }
                // 重新发送消息
                transactionMessageService.reSendMessage(message)
            } catch (e: Exception) {
                logger.error("handle the message error, messageId is:$messageId", e)
            }
        }
    }
}
