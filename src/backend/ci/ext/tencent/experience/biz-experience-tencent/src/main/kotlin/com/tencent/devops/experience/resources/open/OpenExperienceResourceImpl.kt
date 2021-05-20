package com.tencent.devops.experience.resources.open

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.experience.api.open.OpenExperienceResource
import com.tencent.devops.experience.pojo.outer.OuterLoginParam
import com.tencent.devops.experience.pojo.outer.OuterProfileVO
import com.tencent.devops.experience.service.ExperienceOuterService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class OpenExperienceResourceImpl @Autowired constructor(
    private val experienceOuterService: ExperienceOuterService
) : OpenExperienceResource {
    override fun outerLogin(
        platform: Int,
        appVersion: String?,
        realIp: String,
        params: OuterLoginParam
    ): Result<String> {
        return Result(experienceOuterService.outerLogin(platform, appVersion, realIp, params))
    }

    override fun outerAuth(token: String): Result<OuterProfileVO> {
        return Result(experienceOuterService.outerAuth(token))
    }
}
