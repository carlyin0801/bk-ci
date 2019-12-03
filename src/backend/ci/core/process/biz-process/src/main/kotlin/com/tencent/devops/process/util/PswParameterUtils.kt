package com.tencent.devops.process.util

import com.tencent.devops.common.api.exception.OperationException
import com.tencent.devops.common.api.util.AESUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * password参数类型工具类
 */
@Component
class PswParameterUtils {

    // @Value("\${parameter.password.pswKey}")
    private val pswKey: String = ""

    private val logger = LoggerFactory.getLogger(PswParameterUtils::class.java)

    fun decrypt(content: String): String {
        try {
            return AESUtil.decrypt(pswKey, content)
        } catch (e: Exception) {
            logger.error("password: $content decrypt error. $pswKey", e)
            throw OperationException("password: $content decrypt error.")
        }
    }

    fun encrypt(content: String): String {
        try {
            return AESUtil.encrypt(pswKey, content)
        } catch (e: Exception) {
            logger.error("password: $content encrypt error. $pswKey", e)
            throw OperationException("password: $content encrypt error.")
        }
    }
}