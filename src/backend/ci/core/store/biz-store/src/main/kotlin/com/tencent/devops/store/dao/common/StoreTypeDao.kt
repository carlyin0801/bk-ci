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
import com.tencent.devops.model.store.tables.TStoreType
import com.tencent.devops.model.store.tables.records.TStoreTypeRecord
import com.tencent.devops.store.pojo.common.StoreTypeInfoRequest
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Result
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class StoreTypeDao {

    fun countByName(dslContext: DSLContext, typeName: String): Int {
        with(TStoreType.T_STORE_TYPE) {
            return dslContext.selectCount().from(this).where(TYPE_NAME.eq(typeName)).fetchOne(0, Int::class.java)
        }
    }

    fun countByCode(dslContext: DSLContext, typeCode: String): Int {
        with(TStoreType.T_STORE_TYPE) {
            return dslContext.selectCount().from(this).where(TYPE_CODE.eq(typeCode)).fetchOne(0, Int::class.java)
        }
    }

    fun getStoreTypeByCode(dslContext: DSLContext, typeCode: String): TStoreTypeRecord? {
        with(TStoreType.T_STORE_TYPE) {
            return dslContext.selectFrom(this).where(TYPE_CODE.eq(typeCode)).fetchOne()
        }
    }

    fun getMaxTypeValue(dslContext: DSLContext): Byte {
        with(TStoreType.T_STORE_TYPE) {
            return dslContext.select(DSL.max(TYPE_VALUE)).from(this).fetchOne(0, Byte::class.java)
        }
    }

    fun getStoreTypes(
        dslContext: DSLContext,
        typeName: String? = null,
        page: Int? = null,
        pageSize: Int? = null
    ): Result<TStoreTypeRecord>? {
        with(TStoreType.T_STORE_TYPE) {
            val conditions = mutableListOf<Condition>()
            if (!typeName.isNullOrBlank()) {
                conditions.add(TYPE_NAME.contains(typeName))
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

    fun getStoreTypeCount(
        dslContext: DSLContext,
        typeName: String?
    ): Long {
        with(TStoreType.T_STORE_TYPE) {
            val conditions = mutableListOf<Condition>()
            if (!typeName.isNullOrBlank()) {
                conditions.add(TYPE_NAME.contains(typeName))
            }
            return dslContext.selectCount().from(this).where(conditions)
                .fetchOne(0, Long::class.java)
        }
    }

    fun addStoreType(
        dslContext: DSLContext,
        userId: String,
        typeValue: Byte,
        storeTypeInfoRequest: StoreTypeInfoRequest
    ) {
        with(TStoreType.T_STORE_TYPE) {
            dslContext.insertInto(
                this,
                ID,
                TYPE_NAME,
                TYPE_CODE,
                TYPE_VALUE,
                SHOW_FLAG,
                DESK_FLAG,
                HTML_TEMPLATE_VERSION,
                CREATOR,
                MODIFIER
            ).values(
                UUIDUtil.generate(),
                storeTypeInfoRequest.typeName,
                storeTypeInfoRequest.typeCode,
                typeValue,
                storeTypeInfoRequest.showFlag,
                storeTypeInfoRequest.deskFlag,
                storeTypeInfoRequest.htmlTemplateVersion,
                userId,
                userId
            ).execute()
        }
    }

    fun updateStoreType(
        dslContext: DSLContext,
        userId: String,
        typeId: String,
        storeTypeInfoRequest: StoreTypeInfoRequest
    ) {
        with(TStoreType.T_STORE_TYPE) {
            dslContext.update(this)
                .set(TYPE_NAME, storeTypeInfoRequest.typeName)
                .set(SHOW_FLAG, storeTypeInfoRequest.showFlag)
                .set(DESK_FLAG, storeTypeInfoRequest.deskFlag)
                .set(HTML_TEMPLATE_VERSION, storeTypeInfoRequest.htmlTemplateVersion)
                .set(MODIFIER, userId)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(ID.eq(typeId))
                .execute()
        }
    }
}