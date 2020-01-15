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

package com.tencent.devops.message.pojo

import com.tencent.devops.message.pojo.enums.MessageDataTypeEnum
import com.tencent.devops.message.pojo.enums.MessageStatusEnum
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty
import java.time.LocalDateTime

@ApiModel("事务消息")
data class TransactionMessage(

    @ApiModelProperty("消息ID", required = true)
    val messageId: String,

    @ApiModelProperty("消息内容", required = true)
    val messageBody: String,

    @ApiModelProperty("版本号", required = true)
    val version: Int,

    @ApiModelProperty("消息数据类型", required = true)
    val messageDataType: MessageDataTypeEnum,

    @ApiModelProperty("消息队列名称", required = true)
    val consumerQueue: String,

    @ApiModelProperty("消息发送次数", required = true)
    var messageSendTimes: Int,

    @ApiModelProperty("消息是否死亡", required = true)
    var isDead: Boolean,

    @ApiModelProperty("消息状态", required = true)
    var status: MessageStatusEnum,

    @ApiModelProperty("备注", required = false)
    val remark: String ? = null,

    @ApiModelProperty("扩展字段，存贮用来查询接口发起方业务逻辑处理结果的关键信息", required = false)
    val data: String ? = null,

    @ApiModelProperty("创建人", required = true)
    val creator: String,

    @ApiModelProperty("创建时间", required = true)
    val createTime: String,

    @ApiModelProperty("修改人", required = true)
    val modifier: String,

    @ApiModelProperty("修改时间", required = true)
    var updateTime: String
)