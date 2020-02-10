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

package com.tencent.devops.message.dao

import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.message.config.TransactionMessageConfig
import com.tencent.devops.message.pojo.QueryTransactionMessageParam
import com.tencent.devops.message.pojo.TransactionMessage
import com.tencent.devops.message.pojo.UpdateTransactionMessageParam
import com.tencent.devops.message.pojo.enums.MessageDataTypeEnum
import com.tencent.devops.message.pojo.enums.MessageStatusEnum
import com.tencent.devops.model.message.tables.TTransactionMessage
import com.tencent.devops.model.message.tables.records.TTransactionMessageRecord
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.Result
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class TransactionMessageDao @Autowired constructor(
    private val transactionMessageConfig: TransactionMessageConfig
) {

    private val logger = LoggerFactory.getLogger(TransactionMessageDao::class.java)

    /**
     * 添加事务消息
     */
    fun addTransactionMessage(dslContext: DSLContext, transactionMessage: TransactionMessage) {
        with(TTransactionMessage.T_TRANSACTION_MESSAGE) {
            dslContext.insertInto(
                this,
                MESSAGE_ID,
                MESSAGE_BODY,
                VERSION,
                MESSAGE_DATA_TYPE,
                CONSUMER_QUEUE,
                MESSAGE_SEND_TIMES,
                IS_DEAD,
                STATUS,
                REMARK,
                DATA,
                CREATOR,
                MODIFIER,
                CREATE_TIME,
                UPDATE_TIME
            )
                .values(
                    transactionMessage.messageId,
                    transactionMessage.messageBody,
                    transactionMessage.version,
                    transactionMessage.messageDataType.name,
                    transactionMessage.consumerQueue,
                    transactionMessage.messageSendTimes,
                    transactionMessage.isDead,
                    transactionMessage.status.name,
                    transactionMessage.remark,
                    transactionMessage.data,
                    transactionMessage.creator,
                    transactionMessage.modifier,
                    DateTimeUtil.stringToLocalDateTime(transactionMessage.createTime),
                    DateTimeUtil.stringToLocalDateTime(transactionMessage.updateTime)
                ).execute()
        }
    }

    /**
     * 获取事务消息
     */
    fun getTransactionMessageById(dslContext: DSLContext, messageId: String): TTransactionMessageRecord? {
        with(TTransactionMessage.T_TRANSACTION_MESSAGE) {
            return dslContext.selectFrom(this)
                .where(MESSAGE_ID.eq(messageId))
                .fetchOne()
        }
    }

    /**
     * 删除事务消息
     */
    fun deleteTransactionMessageById(dslContext: DSLContext, messageId: String) {
        with(TTransactionMessage.T_TRANSACTION_MESSAGE) {
            val baseStep = dslContext.delete(this)
                .where(MESSAGE_ID.eq(messageId))
            logger.info(baseStep.getSQL(true))
            val result = baseStep.execute()
            logger.info("deleteTransactionMessageById result is:$result")
        }
    }

    /**
     * 更新事务消息
     */
    fun updateTransactionMessage(
        dslContext: DSLContext,
        userId: String,
        messageId: String,
        updateTransactionMessageParam: UpdateTransactionMessageParam
    ) {
        with(TTransactionMessage.T_TRANSACTION_MESSAGE) {
            val baseStep = dslContext.update(this)
            val messageBody = updateTransactionMessageParam.messageBody
            if (null != messageBody) {
                baseStep.set(MESSAGE_BODY, messageBody)
            }
            val version = updateTransactionMessageParam.version
            if (null != version) {
                baseStep.set(VERSION, version)
            }
            val messageDataType = updateTransactionMessageParam.messageDataType
            if (null != messageDataType) {
                baseStep.set(MESSAGE_DATA_TYPE, messageDataType.name)
            }
            val consumerQueue = updateTransactionMessageParam.consumerQueue
            if (null != consumerQueue) {
                baseStep.set(CONSUMER_QUEUE, consumerQueue)
            }
            val messageSendTimes = updateTransactionMessageParam.messageSendTimes
            if (null != messageSendTimes) {
                baseStep.set(MESSAGE_SEND_TIMES, messageSendTimes)
            }
            val isDead = updateTransactionMessageParam.isDead
            if (null != isDead) {
                baseStep.set(IS_DEAD, isDead)
            }
            val status = updateTransactionMessageParam.status
            if (null != status) {
                baseStep.set(STATUS, status.name)
            }
            val remark = updateTransactionMessageParam.remark
            if (null != remark) {
                baseStep.set(REMARK, remark)
            }
            val data = updateTransactionMessageParam.data
            if (null != data) {
                baseStep.set(DATA, data)
            }
            baseStep.set(MODIFIER, userId)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(MESSAGE_ID.eq(messageId))
                .execute()
        }
    }

    fun getTransactionMessages(
        dslContext: DSLContext,
        queryTransactionMessageParam: QueryTransactionMessageParam
    ): Result<TTransactionMessageRecord>? {
        with(TTransactionMessage.T_TRANSACTION_MESSAGE) {
            val conditions = mutableListOf<Condition>()
            val messageDataType = queryTransactionMessageParam.messageDataType
            if (messageDataType != null) {
                conditions.add(MESSAGE_DATA_TYPE.eq(messageDataType.name))
            }
            val consumerQueue = queryTransactionMessageParam.consumerQueue
            if (consumerQueue != null) {
                conditions.add(CONSUMER_QUEUE.eq(consumerQueue))
            }
            val isDead = queryTransactionMessageParam.isDead
            if (isDead != null) {
                conditions.add(IS_DEAD.eq(isDead))
            }
            val status = queryTransactionMessageParam.status
            if (status != null) {
                conditions.add(STATUS.eq(status.name))
            }
            val createTime = queryTransactionMessageParam.createTime
            if (createTime != null) {
                conditions.add(CREATE_TIME.lt(createTime))
            }
            val messageSendTimes = queryTransactionMessageParam.messageSendTimes
            if (messageSendTimes != null) {
                conditions.add(MESSAGE_SEND_TIMES.eq(messageSendTimes))
            }
            val validFlag = queryTransactionMessageParam.validFlag
            if (validFlag != null && validFlag) {
                conditions.add(UPDATE_TIME.lt(timestampSubMinute(MESSAGE_SEND_TIMES)))
            }
            val baseStep = dslContext.selectFrom(this)
                .where(conditions)
            if (queryTransactionMessageParam.descFlag) {
                baseStep.orderBy(CREATE_TIME.desc())
            } else {
                baseStep.orderBy(CREATE_TIME.asc())
            }
            logger.info(baseStep.getSQL(true))
            val page = queryTransactionMessageParam.page
            val pageSize = queryTransactionMessageParam.pageSize
            return if (null != page && null != pageSize) {
                baseStep.limit((page - 1) * pageSize, pageSize).fetch()
            } else {
                baseStep.fetch()
            }
        }
    }

    fun timestampSubMinute(sendTimes: Field<Int>): Field<LocalDateTime> {
        return DSL.field(
            "CASE\n" +
                "when MESSAGE_SEND_TIMES = 0 then date_sub(NOW(), interval {0} minute)\n" +
                "when MESSAGE_SEND_TIMES = 1 then date_sub(NOW(), interval {0} minute)\n" +
                "when MESSAGE_SEND_TIMES = 2 then date_sub(NOW(), interval {1} minute)\n" +
                "when MESSAGE_SEND_TIMES = 3 then date_sub(NOW(), interval {2} minute)\n" +
                "when MESSAGE_SEND_TIMES = 4 then date_sub(NOW(), interval {3} minute)\n" +
                "when MESSAGE_SEND_TIMES = 5 then date_sub(NOW(), interval {4} minute)\n" +
                "END",
            LocalDateTime::class.java,
            sendTimes.multiply(transactionMessageConfig.messageFirstSendIntervalTime),
            sendTimes.multiply(transactionMessageConfig.messageSecondSendIntervalTime),
            sendTimes.multiply(transactionMessageConfig.messageThirdSendIntervalTime),
            sendTimes.multiply(transactionMessageConfig.messageFourthSendIntervalTime),
            sendTimes.multiply(transactionMessageConfig.messageFifthSendIntervalTime)
        )
    }

    fun convert(record: TTransactionMessageRecord): TransactionMessage {
        with(record) {
            return TransactionMessage(
                messageId = messageId,
                messageBody = messageBody,
                version = version,
                messageDataType = MessageDataTypeEnum.valueOf(messageDataType),
                consumerQueue = consumerQueue,
                messageSendTimes = messageSendTimes,
                isDead = isDead,
                status = MessageStatusEnum.valueOf(status),
                remark = remark,
                data = data,
                creator = creator,
                createTime = DateTimeUtil.toDateTime(createTime),
                modifier = modifier,
                updateTime = DateTimeUtil.toDateTime(updateTime)
            )
        }
    }
}