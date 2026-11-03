package com.tencent.devops.process.util

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.ReflectUtil
import com.tencent.devops.common.notify.pojo.elements.SendRTXNotifyElement
import com.tencent.devops.common.pipeline.pojo.element.ElementAdditionalOptions
import com.tencent.devops.common.pipeline.pojo.element.market.MarketBuildAtomElement

fun main() {
    val jsonString = "{\n" +
            "  \"@type\": \"SendRTXNotifyElement\",\n" +
            "  \"name\": \"发送企业微信通知\",\n" +
            "  \"id\": \"e-34c56b3846ca47b28ec2656deebd59b5\",\n" +
            "  \"receivers\": [\n" +
            "    \"carlyin\"\n" +
            "  ],\n" +
            "  \"title\": \"蓝盾流水线构建通知\",\n" +
            "  \"body\": \"嘎嘎嘎\",\n" +
            "  \"wechatGroupFlag\": false,\n" +
            "  \"wechatGroup\": \"\",\n" +
            "  \"detailFlag\": false,\n" +
            "  \"executeCount\": 1,\n" +
            "  \"version\": \"1.*\",\n" +
            "  \"additionalOptions\": {\n" +
            "    \"enable\": true,\n" +
            "    \"continueWhenFailed\": false,\n" +
            "    \"manualSkip\": false,\n" +
            "    \"retryWhenFailed\": false,\n" +
            "    \"retryCount\": 1,\n" +
            "    \"manualRetry\": false,\n" +
            "    \"timeout\": 600,\n" +
            "    \"timeoutVar\": \"600\",\n" +
            "    \"runCondition\": \"PRE_TASK_SUCCESS\",\n" +
            "    \"pauseBeforeExec\": false,\n" +
            "    \"subscriptionPauseUser\": \"carlyin\",\n" +
            "    \"otherTask\": \"\",\n" +
            "    \"customVariables\": [\n" +
            "      {\n" +
            "        \"key\": \"param1\",\n" +
            "        \"value\": \"\"\n" +
            "      }\n" +
            "    ],\n" +
            "    \"customCondition\": \"\",\n" +
            "    \"enableCustomEnv\": true\n" +
            "  },\n" +
            "  \"stepId\": \"step_3\",\n" +
            "  \"fromTemplate\": false,\n" +
            "  \"classType\": \"sendRTXNotify\",\n" +
            "  \"taskAtom\": \"rtxTaskAtom\",\n" +
            "  \"atomCode\": \"sendRTXNotify\"\n" +
            "}"
    val obj = JsonUtil.to(jsonString, SendRTXNotifyElement::class.java)
    println(obj.stepId)

    (obj as? MarketBuildAtomElement)?.apply {
        println("-----no")
    } ?: MarketBuildAtomElement().apply {
        println("-----yes")
    }

    val additionalOptions = ElementAdditionalOptions(
        retryCount = 50
    )
    val sendRTXNotifyElement = SendRTXNotifyElement(
        id = "e1",
        name = "企业微信",
        receivers = setOf("carlyin"),
        title = "echo",
        body = "echo 123",
        status = "WAIT",
        wechatGroupFlag = false,
        wechatGroup = null,
        detailFlag = null
    )
    val marketBuildAtomElement = MarketBuildAtomElement()
    println(marketBuildAtomElement.atomCode)
    ReflectUtil.copyMatchingProperties(obj, marketBuildAtomElement)
    println(JsonUtil.toJson(marketBuildAtomElement))
}