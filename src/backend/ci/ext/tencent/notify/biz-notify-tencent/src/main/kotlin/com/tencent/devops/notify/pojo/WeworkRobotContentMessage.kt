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

package com.tencent.devops.notify.pojo

import com.fasterxml.jackson.annotation.JsonProperty
import io.swagger.annotations.ApiModel
import io.swagger.annotations.ApiModelProperty

@ApiModel("企业微信机器人消息内容")
data class WeworkRobotContentMessage(
    /**
     * 文本内容，最长不超过2048个字节，必须是utf8编码
     */
    @ApiModelProperty("文本内容")
    val content: String,

    /**
     * 提醒群中的指定成员(@某个成员)，@all表示提醒所有人，如果开发者获取不到userid，可以使用mentioned_mobile_list，目前 mentioned_list 暂不支持小黑板
     */
    @JsonProperty("mentioned_list")
    @ApiModelProperty("userid的列表", name = "mentioned_list")
    val mentionedList: Set<String>?,

    /**
     * 手机号列表，提醒手机号对应的群成员(@某个成员)，@all表示提醒所有人，目前 mentioned_mobile_list 暂不支持小黑板
     */
    @JsonProperty("mentioned_mobile_list")
    @ApiModelProperty("手机号列表，提醒手机号对应的群成员(@某个成员)，@all表示提醒所有人", name = "mentioned_mobile_list")
    val mentionedMobileList: Set<String>?
)
