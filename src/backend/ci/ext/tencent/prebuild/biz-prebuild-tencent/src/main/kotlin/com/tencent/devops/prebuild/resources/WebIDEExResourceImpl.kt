package com.tencent.devops.prebuild.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.prebuild.api.WebIDEExResource
import com.tencent.devops.prebuild.service.WebIDEService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class WebIDEExResourceImpl @Autowired constructor(private val webIDEService: WebIDEService) : WebIDEExResource {
    override fun heartBeat(userId: String, ip: String, version: String): Result<Boolean> {
        return Result(webIDEService.heartBeat(userId, ip, version))
    }

    override fun devcloudIp(ip: String): Result<Boolean> {
        return Result(webIDEService.reportDevcloudIp(ip))
    }
}