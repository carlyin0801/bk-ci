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
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.store.dao.common.StorePageDao
import com.tencent.devops.store.dao.common.StorePageModelRelDao
import com.tencent.devops.store.pojo.common.StorePageInfo
import com.tencent.devops.store.pojo.common.StorePageRequest
import com.tencent.devops.store.service.common.StorePageService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class StorePageServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val storePageDao: StorePageDao,
    private val storePageModelRelDao: StorePageModelRelDao
) : StorePageService {

    private val logger = LoggerFactory.getLogger(StorePageServiceImpl::class.java)

    override fun getStorePages(
        userId: String,
        pageName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<Page<StorePageInfo>?> {
        logger.info("getStorePages userId:$userId, pageName:$pageName, page:$page, pageSize:$pageSize")
        val pageList =
            storePageDao.getStorePages(dslContext, pageName, page, pageSize)?.map {
                StorePageInfo(
                    pageId = it.id,
                    pageName = it.pageName,
                    pageCode = it.pageCode,
                    pagePath = it.pagePath
                )
            }
        val pageCount = storePageDao.getStorePageCount(dslContext, pageName)
        val totalPages = PageUtil.calTotalPage(pageSize, pageCount)
        return Result(
            Page(
                count = pageCount,
                page = page ?: 1,
                pageSize = pageSize ?: -1,
                totalPages = totalPages,
                records = pageList ?: listOf()
            )
        )
    }

    override fun addStorePage(userId: String, storePageRequest: StorePageRequest): Result<Boolean> {
        logger.info("addStorePage userId:$userId, storePageRequest:$storePageRequest")
        validateStorePageRequest(storePageRequest, false)
        val pageId = UUIDUtil.generate()
        dslContext.transaction { t ->
            val context = DSL.using(t)
            storePageDao.addStorePage(
                dslContext = context,
                userId = userId,
                pageId = pageId,
                storePageRequest = storePageRequest
            )
            // 为页面关联model
            val modelIdList = storePageRequest.modelIdList
            if (null != modelIdList && modelIdList.isNotEmpty()) {
                storePageModelRelDao.batchAdd(
                    dslContext = context,
                    userId = userId,
                    pageId = pageId,
                    modelIdList = modelIdList
                )
            }
        }
        return Result(true)
    }

    override fun updateStorePage(userId: String, pageId: String, storePageRequest: StorePageRequest): Result<Boolean> {
        logger.info("updateStorePage userId:$userId, pageId:$pageId, storePageRequest:$storePageRequest")
        validateStorePageRequest(storePageRequest, true)
        dslContext.transaction { t ->
            val context = DSL.using(t)
            storePageDao.updateStorePage(
                dslContext = context,
                userId = userId,
                pageId = pageId,
                storePageRequest = storePageRequest
            )
            storePageModelRelDao.deleteByPageId(context, pageId)
            val modelIdList = storePageRequest.modelIdList
            if (null != modelIdList && modelIdList.isNotEmpty()) {
                storePageModelRelDao.batchAdd(
                    dslContext = context,
                    userId = userId,
                    pageId = pageId,
                    modelIdList = modelIdList
                )
            }
        }
        return Result(true)
    }

    override fun deleteStorePage(userId: String, pageId: String): Result<Boolean> {
        logger.info("deleteStorePage userId:$userId, pageId:$pageId")
        dslContext.transaction { t ->
            val context = DSL.using(t)
            storePageDao.deleteStorePage(context, userId, pageId)
            storePageModelRelDao.deleteByPageId(context, pageId)
        }
        return Result(true)
    }

    private fun validateStorePageRequest(
        storePageRequest: StorePageRequest,
        updateFlag: Boolean
    ) {
        val pageCode = storePageRequest.pageCode
        val storePageRecord = storePageDao.getStorePageByCode(dslContext, pageCode)
        val codeFlag =
            if (updateFlag) storePageRecord != null && storePageRecord.pageCode != pageCode else storePageRecord != null
        // 判断研发商店页面代码是否存在
        if (codeFlag) {
            // 抛出错误提示
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PARAMETER_IS_EXIST,
                params = arrayOf(pageCode)
            )
        }
        val pageName = storePageRequest.pageName
        val nameFlag =
            if (updateFlag) storePageRecord != null && storePageRecord.pageName != pageName else storePageRecord != null
        // 判断研发商店页面名称是否存在
        if (nameFlag) {
            // 抛出错误提示
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PARAMETER_IS_EXIST,
                params = arrayOf(pageName)
            )
        }
    }
}
