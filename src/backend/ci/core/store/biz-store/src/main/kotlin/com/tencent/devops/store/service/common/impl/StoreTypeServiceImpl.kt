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

package com.tencent.devops.store.service.common.impl

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.DateTimeUtil
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.redis.RedisLock
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.store.dao.common.StoreTypeDao
import com.tencent.devops.store.pojo.common.BCI_STORE_TYPE_PREFIX
import com.tencent.devops.store.pojo.common.StoreTypeInfo
import com.tencent.devops.store.pojo.common.StoreTypeInfoRequest
import com.tencent.devops.store.service.common.StoreTypeService
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cloud.context.config.annotation.RefreshScope
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
@RefreshScope
class StoreTypeServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val redisOperation: RedisOperation,
    private val storeTypeDao: StoreTypeDao
) : StoreTypeService {

    private val logger = LoggerFactory.getLogger(StoreTypeServiceImpl::class.java)

    override fun getStoreTypes(typeName: String?, page: Int?, pageSize: Int?): Result<Page<StoreTypeInfo>?> {
        logger.info("getStoreTypes typeName is :$typeName, page is :$page, pageSize is :$pageSize")
        val storeTypeList =
            storeTypeDao.getStoreTypes(dslContext, typeName, page, pageSize)?.map {
                StoreTypeInfo(
                    typeId = it.id,
                    typeName = it.typeName,
                    typeCode = it.typeCode,
                    typeValue = it.typeValue,
                    showFlag = it.showFlag,
                    deskFlag = it.deskFlag,
                    htmlTemplateVersion = it.htmlTemplateVersion,
                    createTime = DateTimeUtil.toDateTime(it.createTime),
                    updateTime = DateTimeUtil.toDateTime(it.updateTime),
                    creator = it.creator,
                    modifier = it.modifier
                )
            }
        val commentCount = storeTypeDao.getStoreTypeCount(dslContext, typeName)
        val totalPages = PageUtil.calTotalPage(pageSize, commentCount)
        return Result(
            Page(
                count = commentCount,
                page = page ?: 1,
                pageSize = pageSize ?: -1,
                totalPages = totalPages,
                records = storeTypeList ?: listOf()
            )
        )
    }

    override fun addStoreType(
        userId: String,
        storeTypeInfoRequest: StoreTypeInfoRequest
    ): Result<Boolean> {
        logger.info("addStoreType userId is :$userId, storeTypeInfoRequest is :$storeTypeInfoRequest")
        val typeCode = storeTypeInfoRequest.typeCode
        validateStoreTypeInfoRequest(storeTypeInfoRequest, false)
        val typeValue = storeTypeDao.getMaxTypeValue(dslContext) + 1
        storeTypeDao.addStoreType(
            dslContext = dslContext,
            userId = userId,
            typeValue = typeValue.toByte(),
            storeTypeInfoRequest = storeTypeInfoRequest
        )
        setStoreTypeToRedis(typeCode, typeValue.toString())
        return Result(true)
    }

    private fun validateStoreTypeInfoRequest(storeTypeInfoRequest: StoreTypeInfoRequest, updateFlag: Boolean) {
        val typeCode = storeTypeInfoRequest.typeCode
        val storeTypeRecord = storeTypeDao.getStoreTypeByCode(dslContext, typeCode)
        val codeFlag = if (updateFlag) storeTypeRecord != null && storeTypeRecord.typeCode != typeCode else storeTypeRecord != null
        // 判断组件类型代码是否存在
        if (codeFlag) {
            // 抛出错误提示
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PARAMETER_IS_EXIST,
                params = arrayOf(typeCode)
            )
        }
        val typeName = storeTypeInfoRequest.typeName
        val nameFlag = if (updateFlag) storeTypeRecord != null && storeTypeRecord.typeName != typeName else storeTypeRecord != null
        // 判断组件类型名称是否存在
        if (nameFlag) {
            // 抛出错误提示
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PARAMETER_IS_EXIST,
                params = arrayOf(typeName)
            )
        }
    }

    override fun updateStoreType(
        userId: String,
        typeId: String,
        storeTypeInfoRequest: StoreTypeInfoRequest
    ): Result<Boolean> {
        logger.info("updateStoreType userId is :$userId, typeId is :$typeId, storeTypeInfoRequest is :$storeTypeInfoRequest")
        validateStoreTypeInfoRequest(storeTypeInfoRequest, true)
        storeTypeDao.updateStoreType(
            dslContext = dslContext,
            userId = userId,
            typeId = typeId,
            storeTypeInfoRequest = storeTypeInfoRequest
        )
        return Result(true)
    }

    @Suppress("UNUSED")
    @PostConstruct
    fun initStoreTypeList() {
        logger.info("begin init storeTypeList")
        val redisLock =
            RedisLock(redisOperation = redisOperation, lockKey = "STORE_TYPE_LOCK", expiredTimeInSeconds = 60)

        if (redisLock.tryLock()) {
            try {
                val storeTypeRecords = storeTypeDao.getStoreTypes(dslContext)
                storeTypeRecords?.forEach {
                    // 把研发商店组件类型的标识和值的映射关系存入redis
                    val typeCode = it.typeCode
                    val typeValue = it.typeValue.toString()
                    setStoreTypeToRedis(typeCode, typeValue)
                }
            } finally {
                redisLock.unlock()
            }
        }
    }

    private fun setStoreTypeToRedis(typeCode: String, typeValue: String) {
        redisOperation.set(
            key = BCI_STORE_TYPE_PREFIX + typeCode,
            value = typeValue,
            expired = false
        )
        redisOperation.set(
            key = BCI_STORE_TYPE_PREFIX + typeValue,
            value = typeCode,
            expired = false
        )
    }
}
