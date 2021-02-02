package com.tencent.devops.experience.resources.op

import com.fasterxml.jackson.databind.ObjectMapper
import com.tencent.devops.common.api.enums.PlatformEnum
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.redis.RedisOperation
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.experience.api.op.OpExperienceResource
import com.tencent.devops.experience.dao.ExperienceGroupDao
import com.tencent.devops.experience.dao.ExperienceGroupInnerDao
import com.tencent.devops.experience.dao.ExperienceInnerDao
import com.tencent.devops.experience.dao.ExperiencePublicDao
import com.tencent.devops.experience.dao.ExperienceSearchRecommendDao
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import javax.ws.rs.NotFoundException

@RestResource
class OpExperienceResourceImpl @Autowired constructor(
    private val dslContext: DSLContext,
    private val objectMapper: ObjectMapper,
    private val experienceGroupDao: ExperienceGroupDao,
    private val experienceInnerDao: ExperienceInnerDao,
    private val experienceGroupInnerDao: ExperienceGroupInnerDao,
    private val experiencePublicDao: ExperiencePublicDao,
    private val experienceSearchRecommendDao: ExperienceSearchRecommendDao,
    private val redisOperation: RedisOperation
) : OpExperienceResource {
    override fun switchNecessary(userId: String, id: Long): Result<String> {
        val record = experiencePublicDao.getById(dslContext, id) ?: throw NotFoundException("找不到该记录")

        experiencePublicDao.updateById(
            dslContext = dslContext,
            id = id,
            necessary = record.necessary.not()
        )

        return Result("更新成功,已置为${record.necessary.not()}")
    }

    override fun setNecessaryIndex(userId: String, id: Long, necessaryIndex: Int): Result<String> {
        experiencePublicDao.getById(dslContext, id) ?: throw NotFoundException("找不到该记录")

        experiencePublicDao.updateById(
            dslContext = dslContext,
            id = id,
            necessaryIndex = necessaryIndex
        )

        return Result("更新成功")
    }

    override fun setBannerUrl(userId: String, id: Long, bannerUrl: String): Result<String> {
        experiencePublicDao.getById(dslContext, id) ?: throw NotFoundException("找不到该记录")

        experiencePublicDao.updateById(
            dslContext = dslContext,
            id = id,
            bannerUrl = bannerUrl
        )

        return Result("更新成功,已置为$bannerUrl")
    }

    override fun setBannerUrlIndex(userId: String, id: Long, bannerIndex: Int): Result<String> {
        experiencePublicDao.getById(dslContext, id) ?: throw NotFoundException("找不到该记录")

        experiencePublicDao.updateById(
            dslContext = dslContext,
            id = id,
            bannerIndex = bannerIndex
        )

        return Result("更新成功")
    }

    override fun switchOnline(userId: String, id: Long): Result<String> {
        val record = experiencePublicDao.getById(dslContext, id) ?: throw NotFoundException("找不到该记录")

        experiencePublicDao.updateById(
            dslContext = dslContext,
            id = id,
            online = record.online.not()
        )

        return Result("更新成功,已置为${record.online.not()}")
    }

    override fun addRecommend(userId: String, content: String, platform: PlatformEnum): Result<String> {
        experienceSearchRecommendDao.add(dslContext, content, platform.name)
        return Result("新增搜索推荐成功")
    }

    override fun removeRecommend(userId: String, id: Long): Result<String> {
        experienceSearchRecommendDao.remove(dslContext, id)
        return Result("删除搜索推荐成功")
    }
}
