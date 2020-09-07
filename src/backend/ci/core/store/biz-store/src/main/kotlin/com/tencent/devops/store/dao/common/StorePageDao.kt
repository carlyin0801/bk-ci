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

package com.tencent.devops.store.dao.common

import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.model.store.tables.TStorePage
import com.tencent.devops.model.store.tables.records.TStorePageRecord
import com.tencent.devops.store.pojo.common.StorePageRequest
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class StorePageDao {

    fun countByName(dslContext: DSLContext, pageName: String): Int {
        with(TStorePage.T_STORE_PAGE) {
            return dslContext.selectCount().from(this).where(PAGE_NAME.eq(pageName)).fetchOne(0, Int::class.java)
        }
    }

    fun countByCode(dslContext: DSLContext, pageCode: String): Int {
        with(TStorePage.T_STORE_PAGE) {
            return dslContext.selectCount().from(this).where(PAGE_CODE.eq(pageCode)).fetchOne(0, Int::class.java)
        }
    }

    fun getStorePageByCode(dslContext: DSLContext, pageCode: String): TStorePageRecord? {
        with(TStorePage.T_STORE_PAGE) {
            return dslContext.selectFrom(this).where(PAGE_CODE.eq(pageCode)).fetchOne()
        }
    }

    fun getStorePages(
        dslContext: DSLContext,
        pageName: String? = null,
        page: Int? = null,
        pageSize: Int? = null
    ): Result<TStorePageRecord>? {
        with(TStorePage.T_STORE_PAGE) {
            val conditions = mutableListOf<Condition>()
            if (!pageName.isNullOrBlank()) {
                conditions.add(PAGE_NAME.contains(pageName))
            }
            val baseStep = dslContext.selectFrom(this).where(conditions)
                .orderBy(CREATE_TIME.desc())
            return if (null != page && null != pageSize) {
                baseStep.limit((page - 1) * pageSize, pageSize).fetch()
            } else {
                baseStep.fetch()
            }
        }
    }

    fun getStorePageCount(
        dslContext: DSLContext,
        pageName: String?
    ): Long {
        with(TStorePage.T_STORE_PAGE) {
            val conditions = mutableListOf<Condition>()
            if (!pageName.isNullOrBlank()) {
                conditions.add(PAGE_NAME.contains(pageName))
            }
            return dslContext.selectCount().from(this).where(conditions)
                .fetchOne(0, Long::class.java)
        }
    }

    fun addStorePage(
        dslContext: DSLContext,
        userId: String,
        pageId: String,
        storePageRequest: StorePageRequest
    ) {
        with(TStorePage.T_STORE_PAGE) {
            dslContext.insertInto(
                this,
                ID,
                PAGE_NAME,
                PAGE_CODE,
                PAGE_PATH,
                CREATOR,
                MODIFIER
            ).values(
                UUIDUtil.generate(),
                storePageRequest.pageName,
                storePageRequest.pageCode,
                storePageRequest.pagePath,
                userId,
                userId
            ).execute()
        }
    }

    fun updateStorePage(
        dslContext: DSLContext,
        userId: String,
        pageId: String,
        storePageRequest: StorePageRequest
    ) {
        with(TStorePage.T_STORE_PAGE) {
            dslContext.update(this)
                .set(PAGE_NAME, storePageRequest.pageName)
                .set(PAGE_PATH, storePageRequest.pagePath)
                .set(MODIFIER, userId)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(ID.eq(pageId))
                .execute()
        }
    }

    fun deleteStorePage(
        dslContext: DSLContext,
        userId: String,
        pageId: String
    ) {
        with(TStorePage.T_STORE_PAGE) {
            dslContext.deleteFrom(this)
                .where(ID.eq(pageId))
                .execute()
        }
    }
}