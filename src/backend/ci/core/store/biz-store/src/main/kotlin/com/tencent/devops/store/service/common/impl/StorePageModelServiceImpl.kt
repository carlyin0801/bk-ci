/*
 * Tencentpleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台licensed under the MIT license.
 *
 * A copy of the MIT Licenseincluded in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permissionhereby granted, free of charge, to any person obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the
 * Softwarefurnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWAREPROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
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
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.model.store.tables.records.TStorePageModelRecord
import com.tencent.devops.store.api.utils.StoreTypeUtils
import com.tencent.devops.store.dao.common.StorePageModelDao
import com.tencent.devops.store.dao.common.StorePageModelRelDao
import com.tencent.devops.store.pojo.common.StorePageModelInfo
import com.tencent.devops.store.pojo.common.StorePageModelRequest
import com.tencent.devops.store.service.common.StorePageModelService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class StorePageModelServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val storePageModelDao: StorePageModelDao,
    private val storePageModelRelDao: StorePageModelRelDao
) : StorePageModelService {

    private val logger = LoggerFactory.getLogger(StorePageModelServiceImpl::class.java)

    override fun getStorePageModels(
        userId: String,
        modelName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<Page<StorePageModelInfo>?> {
        logger.info("getStorePageModels userId:$userId, modelName:$modelName, page:$page, pageSize:$pageSize")
        val modelList =
            storePageModelDao.getStorePageModels(dslContext, modelName, page, pageSize)?.map {
                StorePageModelInfo(
                    modelId = it.id,
                    modelName = it.modelName,
                    modelCode = it.modelCode,
                    content = it.content
                )
            }
        val modelCount = storePageModelDao.getStorePageModelCount(dslContext, modelName)
        val totalPages = PageUtil.calTotalPage(pageSize, modelCount)
        return Result(
            Page(
                count = modelCount,
                page = page ?: 1,
                pageSize = pageSize ?: -1,
                totalPages = totalPages,
                records = modelList ?: listOf()
            )
        )
    }

    override fun addStorePageModel(
        userId: String,
        storeType: String,
        storePageModelRequest: StorePageModelRequest
    ): Result<Boolean> {
        logger.info("addStorePageModel userId:$userId, storeType:$storeType, storePageModelRequest:$storePageModelRequest")
        val storeTypeValue = StoreTypeUtils.getStoreTypeValueByCode(storeType)
        val storePageModelRecord = storePageModelDao.getStorePageModelByCode(
            dslContext = dslContext,
            modelCode = storePageModelRequest.modelCode,
            storeType = storeTypeValue
        )
        validateStorePageModelRequest(storePageModelRequest, storePageModelRecord, false)
        storePageModelDao.addStorePageModel(
            dslContext = dslContext,
            userId = userId,
            storeType = storeTypeValue,
            storePageModelRequest = storePageModelRequest
        )
        return Result(true)
    }

    override fun updateStorePageModel(
        userId: String,
        modelId: String,
        storePageModelRequest: StorePageModelRequest
    ): Result<Boolean> {
        logger.info("updateStorePageModel userId:$userId, modelId:$modelId, storePageModelRequest:$storePageModelRequest")
        val storePageModelRecord = storePageModelDao.getStorePageModelById(dslContext, modelId)
        validateStorePageModelRequest(storePageModelRequest, storePageModelRecord, true)
        storePageModelDao.updateStorePageModel(
            dslContext = dslContext,
            userId = userId,
            modelId = modelId,
            storePageModelRequest = storePageModelRequest
        )
        return Result(true)
    }

    override fun deleteStorePageModel(userId: String, modelId: String): Result<Boolean> {
        logger.info("deleteStorePageModel userId:$userId, modelId:$modelId")
        dslContext.transaction { t ->
            val context = DSL.using(t)
            storePageModelDao.deleteStorePageModel(context, userId, modelId)
            storePageModelRelDao.deleteByModelId(context, modelId)
        }
        return Result(true)
    }

    override fun getStorePageModelsByPageCode(
        userId: String,
        pageCode: String,
        storeType: String
    ): Result<List<StorePageModelInfo>?> {
        logger.info("getStorePageModelsByPageCode userId:$userId, pageCode:$pageCode, storeType:$storeType")
        val modelList =
            storePageModelDao.getStorePageModelsByPageCode(
                dslContext = dslContext,
                pageCode = pageCode,
                storeType = StoreTypeUtils.getStoreTypeValueByCode(storeType)
            )?.map {
                StorePageModelInfo(
                    modelId = it.id,
                    modelName = it.modelName,
                    modelCode = it.modelCode,
                    content = it.content
                )
            }
        return Result(data = modelList)
    }

    private fun validateStorePageModelRequest(
        storePageModelRequest: StorePageModelRequest,
        storePageModelRecord: TStorePageModelRecord?,
        updateFlag: Boolean
    ) {
        val modelCode = storePageModelRequest.modelCode
        val codeFlag =
            if (updateFlag) storePageModelRecord != null && storePageModelRecord.modelCode != modelCode else storePageModelRecord != null
        // 判断组件页面模型代码是否存在
        if (codeFlag) {
            // 抛出错误提示
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PARAMETER_IS_EXIST,
                params = arrayOf(modelCode)
            )
        }
        val modelName = storePageModelRequest.modelName
        val nameFlag =
            if (updateFlag) storePageModelRecord != null && storePageModelRecord.modelName != modelName else storePageModelRecord != null
        // 判断组件页面模型名称是否存在
        if (nameFlag) {
            // 抛出错误提示
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PARAMETER_IS_EXIST,
                params = arrayOf(modelName)
            )
        }
    }
}
