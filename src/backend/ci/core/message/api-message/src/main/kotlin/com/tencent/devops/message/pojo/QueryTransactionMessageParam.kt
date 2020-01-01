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

@ApiModel("查询事务消息参数")
data class QueryTransactionMessageParam(

    @ApiModelProperty("消息数据类型", required = false)
    val messageDataType: MessageDataTypeEnum ? = null,

    @ApiModelProperty("消息队列名称", required = false)
    val consumerQueue: String ? = null,

    @ApiModelProperty("消息发送次数", required = false)
    val messageSendTimes: Int ? = null,

    @ApiModelProperty("消息是否死亡", required = false)
    val isDead: Boolean ? = null,

    @ApiModelProperty("消息状态", required = false)
    val status: MessageStatusEnum ? = null,

    @ApiModelProperty("创建时间", required = false)
    val createTime: LocalDateTime ? = null,

    @ApiModelProperty("修改时间", required = false)
    val updateTime: LocalDateTime ? = null,

    @ApiModelProperty("查询待处理消息开关", required = false)
    val validFlag: Boolean ? = null,

    @ApiModelProperty("是否倒序查询", required = false)
    val descFlag: Boolean  = true,

    @ApiModelProperty("页码", required = false)
    val page: Int ? = null,

    @ApiModelProperty("每页大小", required = false)
    val pageSize: Int ? = null
)