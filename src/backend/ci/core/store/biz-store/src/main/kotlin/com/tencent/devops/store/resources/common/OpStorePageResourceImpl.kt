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
package com.tencent.devops.store.resources.common

import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.store.api.common.OpStorePageResource
import com.tencent.devops.store.pojo.common.StorePageInfo
import com.tencent.devops.store.pojo.common.StorePageRequest
import com.tencent.devops.store.service.common.StorePageService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class OpStorePageResourceImpl @Autowired constructor(
    private val storePageService: StorePageService
) : OpStorePageResource {

    override fun getPageList(
        userId: String,
        pageName: String?,
        page: Int?,
        pageSize: Int?
    ): Result<Page<StorePageInfo>?> {
        return storePageService.getStorePages(userId, pageName, page, pageSize)
    }

    override fun addStorePage(userId: String, storeType: String, storePageRequest: StorePageRequest): Result<Boolean> {
        return storePageService.addStorePage(userId, storePageRequest)
    }

    override fun updateStorePage(userId: String, pageId: String, storePageRequest: StorePageRequest): Result<Boolean> {
        return storePageService.updateStorePage(userId, pageId, storePageRequest)
    }

    override fun deleteStorePage(userId: String, pageId: String): Result<Boolean> {
        return storePageService.deleteStorePage(userId, pageId)
    }
}