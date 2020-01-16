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

package com.tencent.devops.message.service

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.message.pojo.QueryTransactionMessageParam
import com.tencent.devops.message.pojo.TransactionMessage

/**
 * 事务消息逻辑处理
 *
 * since: 2019-12-28
 */
interface TransactionMessageService {

    /**
     * 预存储消息.
     */
    fun saveMessageWaitingConfirm(message: TransactionMessage): Result<Boolean>

    /**
     * 确认并发送消息.
     */
    fun confirmAndSendMessage(messageId: String): Result<Boolean>

    /**
     * 存储并发送消息.
     */
    fun saveAndSendMessage(message: TransactionMessage): Result<Boolean>

    /**
     * 直接发送消息.
     */
    fun directSendMessage(message: TransactionMessage): Result<Boolean>

    /**
     * 重发消息.
     */
    fun reSendMessage(message: TransactionMessage): Result<Boolean>

    /**
     * 根据messageId重发某条消息.
     */
    fun reSendMessageByMessageId(messageId: String): Result<Boolean>

    /**
     * 将消息标记为死亡消息.
     */
    fun setMessageToDead(messageId: String): Result<Boolean>

    /**
     * 根据消息ID获取消息
     */
    fun getMessageByMessageId(messageId: String): Result<TransactionMessage?>

    /**
     * 根据消息ID删除消息
     */
    fun deleteMessageByMessageId(messageId: String): Result<Boolean>

    /**
     * 重发某个消息队列中的全部已死亡的消息.
     */
    fun reSendAllDeadMessageByQueueName(queueName: String): Result<Boolean>

    /**
     * 查找事务消息列表
     */
    fun getTransactionMessages(queryTransactionMessageParam: QueryTransactionMessageParam): Result<List<TransactionMessage>?>
}
