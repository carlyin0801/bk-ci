package com.tencent.devops.sharding.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.leaf.common.Status
import com.tencent.devops.leaf.service.SegmentService
import com.tencent.devops.sharding.api.ServiceAllocIdResource
import com.tencent.devops.sharding.api.ServiceTsIdResource
import com.tencent.devops.sharding.dao.leaf.JooqIDAllocDaoImpl
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class ServiceTsIdResourceImpl @Autowired constructor(
    private val jooqIDAllocDaoImpl: JooqIDAllocDaoImpl
) : ServiceTsIdResource {

    override fun generateSegmentIdTsTest(bizTag: String): Result<Long?> {
        val result = jooqIDAllocDaoImpl.updateMaxIdAndGetLeafAlloc(bizTag)
        println("generateSegmentId bizTag:$bizTag:result:$result")
        return Result(result.maxId)
    }
}
