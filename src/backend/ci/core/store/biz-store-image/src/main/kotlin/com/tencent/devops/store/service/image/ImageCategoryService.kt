package com.tencent.devops.store.service.image

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_CODE
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_ICON_URL
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_ID
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_NAME
import com.tencent.devops.store.dao.image.Constants.KEY_CATEGORY_TYPE
import com.tencent.devops.store.dao.image.Constants.KEY_CREATE_TIME
import com.tencent.devops.store.dao.image.Constants.KEY_UPDATE_TIME
import com.tencent.devops.store.dao.image.ImageCategoryRelDao
import com.tencent.devops.store.pojo.common.Category
import com.tencent.devops.store.pojo.common.enums.StoreTypeEnum
import com.tencent.devops.store.service.common.StoreCommonService
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime

@Service
class ImageCategoryService @Autowired constructor(
    private val dslContext: DSLContext,
    private val imageCategoryRelDao: ImageCategoryRelDao,
    private val storeCommonService: StoreCommonService
) {
    private val logger = LoggerFactory.getLogger(ImageCategoryService::class.java)

    /**
     * 查找镜像范畴
     */
    fun getCategorysByImageId(imageId: String): Result<List<Category>?> {
        logger.info("the imageId is :$imageId")
        val imageCategoryList = mutableListOf<Category>()
        val imageCategoryRecords = imageCategoryRelDao.getCategorysByImageId(dslContext, imageId) // 查询镜像范畴信息
        imageCategoryRecords?.forEach {
            imageCategoryList.add(
                Category(
                    id = it[KEY_CATEGORY_ID] as String,
                    categoryCode = it[KEY_CATEGORY_CODE] as String,
                    categoryName = it[KEY_CATEGORY_NAME] as String,
                    iconUrl = it[KEY_CATEGORY_ICON_URL] as? String,
                    categoryType = StoreTypeEnum.getStoreType((it[KEY_CATEGORY_TYPE] as Byte).toInt()),
                    createTime = (it[KEY_CREATE_TIME] as LocalDateTime).timestampmilli(),
                    updateTime = (it[KEY_UPDATE_TIME] as LocalDateTime).timestampmilli()
                )
            )
        }
        return Result(imageCategoryList)
    }
}
