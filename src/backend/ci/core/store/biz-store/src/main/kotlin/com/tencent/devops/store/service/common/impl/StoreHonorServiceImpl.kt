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

package com.tencent.devops.store.service.common.impl

import com.tencent.devops.common.api.constant.CommonMessageCode
import com.tencent.devops.common.api.exception.ErrorCodeException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.UUIDUtil
import com.tencent.devops.common.service.utils.MessageCodeUtil
import com.tencent.devops.common.service.utils.SpringContextUtil
import com.tencent.devops.model.store.tables.TStoreHonorInfo
import com.tencent.devops.model.store.tables.TStoreHonorRel
import com.tencent.devops.model.store.tables.records.TStoreHonorInfoRecord
import com.tencent.devops.model.store.tables.records.TStoreHonorRelRecord
import com.tencent.devops.store.dao.common.AbstractStoreCommonDao
import com.tencent.devops.store.dao.common.StoreHonorDao
import com.tencent.devops.store.dao.common.StoreMemberDao
import com.tencent.devops.store.pojo.common.AddStoreHonorRequest
import com.tencent.devops.store.pojo.common.HonorInfo
import com.tencent.devops.store.pojo.common.StoreHonorManageInfo
import com.tencent.devops.store.pojo.common.StoreHonorRel
import com.tencent.devops.store.pojo.common.enums.StoreTypeEnum
import com.tencent.devops.store.service.common.StoreHonorService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class StoreHonorServiceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val storeHonorDao: StoreHonorDao,
    private val storeMemberDao: StoreMemberDao
) : StoreHonorService {

    override fun list(userId: String, keyWords: String?, page: Int, pageSize: Int): Page<StoreHonorManageInfo> {
        // 权限校验


        return Page(
            count = storeHonorDao.count(dslContext, keyWords),
            page = page,
            pageSize = pageSize,
            records = storeHonorDao.list(
                dslContext = dslContext,
                keyWords = keyWords,
                page = page,
                pageSize = pageSize
            )
        )
    }

    override fun batchDelete(userId: String, storeHonorRelList: List<StoreHonorRel>): Boolean {
        // 权限校验

        if (storeHonorRelList.isEmpty()) {
            return false
        }
        val delHonorIds = storeHonorRelList.map { it.honorId }.toMutableList()
        dslContext.transaction { t ->
            val context = DSL.using(t)
            storeHonorDao.batchDeleteStoreHonorRel(context, storeHonorRelList)
            val honorIds = storeHonorDao.getByIds(context, delHonorIds)
            storeHonorDao.batchDeleteStoreHonorInfo(context, delHonorIds.subtract(honorIds).toList())
        }
        return true
    }

    override fun add(userId: String, addStoreHonorRequest: AddStoreHonorRequest): Result<Boolean> {
        // 权限校验

        logger.info("create storeHonor userid:$userId||honorTitle:${addStoreHonorRequest.honorTitle}")
        val honorTitleCount = storeHonorDao.countByhonorTitle(dslContext, addStoreHonorRequest.honorTitle)
        if (honorTitleCount > 0) {
            // 抛出错误提示
            return MessageCodeUtil.generateResponseDataObject(
                CommonMessageCode.PARAMETER_IS_EXIST,
                arrayOf(addStoreHonorRequest.honorTitle)
            )
        }
        val id = UUIDUtil.generate()
        val storeHonorInfo = TStoreHonorInfoRecord()
        storeHonorInfo.id = id
        storeHonorInfo.honorTitle = addStoreHonorRequest.honorTitle
        storeHonorInfo.honorName = addStoreHonorRequest.honorName
        storeHonorInfo.storeType = addStoreHonorRequest.storeType.type.toByte()
        storeHonorInfo.creator = userId
        storeHonorInfo.modifier = userId
        storeHonorInfo.createTime = LocalDateTime.now()
        storeHonorInfo.updateTime = LocalDateTime.now()
        val tStoreHonorRelList = addStoreHonorRequest.storeCodes.split(",").map {
            val atomName = getStoreCommonDao(addStoreHonorRequest.storeType.name).getStoreNameByCode(dslContext, it)!!
            val tStoreHonorRelRecord = TStoreHonorRelRecord()
            tStoreHonorRelRecord.id = UUIDUtil.generate()
            tStoreHonorRelRecord.storeCode = it
            tStoreHonorRelRecord.storeName = atomName
            tStoreHonorRelRecord.storeType = addStoreHonorRequest.storeType.type.toByte()
            tStoreHonorRelRecord.honorId = id
            tStoreHonorRelRecord.creator = userId
            tStoreHonorRelRecord.modifier = userId
            tStoreHonorRelRecord.createTime = LocalDateTime.now()
            tStoreHonorRelRecord.updateTime = LocalDateTime.now()
            tStoreHonorRelRecord
        }
        dslContext.transaction { t ->
            val context = DSL.using(t)
            storeHonorDao.createStoreHonorInfo(context, userId, storeHonorInfo)
            storeHonorDao.batchCreateStoreHonorRel(context,tStoreHonorRelList)
        }
        return Result(true)
    }

    override fun getStoreHonor(userId: String, storeType: StoreTypeEnum, storeCode: String): List<HonorInfo> {
        return storeHonorDao.getHonorByStoreCode(dslContext, storeType, storeCode)
    }

    override fun installStoreHonor(
        userId: String,
        storeType: StoreTypeEnum,
        storeCode: String,
        honorId: String
    ): Boolean {

        if (!storeMemberDao.isStoreMember(
                dslContext = dslContext,
                userId = userId,
                storeType = storeType.type.toByte(),
                storeCode = storeCode
            )
        ) {
            throw ErrorCodeException(
                errorCode = CommonMessageCode.PERMISSION_DENIED,
                params = arrayOf(storeCode)
            )
        }
        storeHonorDao.installStoreHonor(
            dslContext = dslContext,
            storeCode = storeCode,
            storeType = storeType,
            honorId = honorId
        )
        return true
    }

    override fun getHonorInfosByStoreCodes(
        storeType: StoreTypeEnum,
        storeCodes: List<String>
    ): Map<String, List<HonorInfo>> {
        val records = storeHonorDao.getHonorInfosByStoreCodes(dslContext, storeType, storeCodes)
        val storeHonorInfoMap = mutableMapOf<String, List<HonorInfo>>()
        val tStoreHonorInfo = TStoreHonorInfo.T_STORE_HONOR_INFO
        val tStoreHonorRel = TStoreHonorRel.T_STORE_HONOR_REL
        records.forEach {
            val storeCode = it.value1() as String
            val honorInfo = HonorInfo(
                honorId = it.get(tStoreHonorInfo.ID),
                honorTitle = it.get(tStoreHonorInfo.HONOR_TITLE),
                honorName = it.get(tStoreHonorInfo.HONOR_NAME),
                mountFlag = it.get(tStoreHonorRel.MOUNT_FLAG),
                createTime = it.get(tStoreHonorRel.CREATE_TIME)
            )
            if (storeHonorInfoMap[storeCode].isNullOrEmpty()) {
                storeHonorInfoMap[storeCode] = listOf(honorInfo)
            } else {
                val honorInfos = storeHonorInfoMap[storeCode]!!.toMutableList()
                honorInfos.add(honorInfo)
            }
        }
        return  storeHonorInfoMap
    }

    private fun getStoreCommonDao(storeType: String): AbstractStoreCommonDao {
        return SpringContextUtil.getBean(AbstractStoreCommonDao::class.java, "${storeType}_COMMON_DAO")
    }

    companion object {
        private val logger = LoggerFactory.getLogger(StoreHonorServiceImpl::class.java)
    }
}