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
import com.tencent.devops.model.store.tables.TStorePageModel
import com.tencent.devops.model.store.tables.TStorePageModelRel
import com.tencent.devops.model.store.tables.records.TStorePageModelRecord
import com.tencent.devops.store.pojo.common.StorePageModelRequest
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Result
import org.springframework.stereotype.Repository
import java.time.LocalDateTime

@Repository
class StorePageModelDao {

    fun countByName(dslContext: DSLContext, modelName: String, storeType: Byte): Int {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            return dslContext.selectCount().from(this)
                .where(MODEL_NAME.eq(modelName))
                .and(STORE_TYPE.eq(storeType))
                .fetchOne(0, Int::class.java)
        }
    }

    fun countByCode(dslContext: DSLContext, modelCode: String, storeType: Byte): Int {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            return dslContext.selectCount().from(this)
                .where(MODEL_CODE.eq(modelCode))
                .and(STORE_TYPE.eq(storeType))
                .fetchOne(0, Int::class.java)
        }
    }

    fun getStorePageModelById(dslContext: DSLContext, modelId: String): TStorePageModelRecord? {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            return dslContext.selectFrom(this)
                .where(ID.eq(modelId))
                .fetchOne()
        }
    }

    fun getStorePageModelByCode(dslContext: DSLContext, modelCode: String, storeType: Byte): TStorePageModelRecord? {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            return dslContext.selectFrom(this)
                .where(MODEL_CODE.eq(modelCode))
                .and(STORE_TYPE.eq(storeType))
                .fetchOne()
        }
    }

    fun getStorePageModels(
        dslContext: DSLContext,
        modelName: String? = null,
        page: Int? = null,
        pageSize: Int? = null
    ): Result<TStorePageModelRecord>? {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            val conditions = mutableListOf<Condition>()
            if (!modelName.isNullOrBlank()) {
                conditions.add(MODEL_NAME.contains(modelName))
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

    fun getStorePageModelCount(
        dslContext: DSLContext,
        modelName: String?
    ): Long {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            val conditions = mutableListOf<Condition>()
            if (!modelName.isNullOrBlank()) {
                conditions.add(MODEL_NAME.contains(modelName))
            }
            return dslContext.selectCount().from(this).where(conditions)
                .fetchOne(0, Long::class.java)
        }
    }

    fun addStorePageModel(
        dslContext: DSLContext,
        userId: String,
        storeType: Byte,
        storePageModelRequest: StorePageModelRequest
    ) {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            dslContext.insertInto(
                this,
                ID,
                MODEL_NAME,
                MODEL_CODE,
                CONTENT,
                STORE_TYPE,
                CREATOR,
                MODIFIER
            ).values(
                UUIDUtil.generate(),
                storePageModelRequest.modelName,
                storePageModelRequest.modelCode,
                storePageModelRequest.content,
                storeType,
                userId,
                userId
            ).execute()
        }
    }

    fun updateStorePageModel(
        dslContext: DSLContext,
        userId: String,
        modelId: String,
        storePageModelRequest: StorePageModelRequest
    ) {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            dslContext.update(this)
                .set(MODEL_NAME, storePageModelRequest.modelName)
                .set(CONTENT, storePageModelRequest.content)
                .set(MODIFIER, userId)
                .set(UPDATE_TIME, LocalDateTime.now())
                .where(ID.eq(modelId))
                .execute()
        }
    }

    fun deleteStorePageModel(
        dslContext: DSLContext,
        userId: String,
        modelId: String
    ) {
        with(TStorePageModel.T_STORE_PAGE_MODEL) {
            dslContext.deleteFrom(this)
                .where(ID.eq(modelId))
                .execute()
        }
    }

    fun getStorePageModelsByPageCode(
        dslContext: DSLContext,
        pageCode: String,
        storeType: Byte
    ): Result<TStorePageModelRecord>? {
        val tspm = TStorePageModel.T_STORE_PAGE_MODEL
        val tspmr = TStorePageModelRel.T_STORE_PAGE_MODEL_REL
        val tsp = TStorePage.T_STORE_PAGE
        val pageId =
            dslContext.select(tsp.ID).from(tsp).where(tsp.PAGE_CODE.eq(pageCode)).fetchOne(0, String::class.java)
        return dslContext.selectFrom(tspm).where(
            tspm.STORE_TYPE.eq(storeType).andExists(
                dslContext.selectOne().from(tspmr).where(tspm.ID.eq(tspmr.MODEL_ID).and(tspmr.PAGE_ID.eq(pageId)))
            )
        ).fetch()
    }
}