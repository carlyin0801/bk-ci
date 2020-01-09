package com.tencent.devops.store.resources

import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.store.api.UserExtServiceResource
import com.tencent.devops.store.pojo.dto.InitExtServiceDTO
import com.tencent.devops.store.pojo.dto.SubmitDTO
import com.tencent.devops.store.pojo.vo.ExtensionAndVersionVO
import com.tencent.devops.store.pojo.vo.ExtensionServiceVO
import com.tencent.devops.store.service.ExtServiceBaseService
import org.springframework.beans.factory.annotation.Autowired


class UserExtServiceResourceImpl @Autowired constructor(
    private val extServiceBaseService: ExtServiceBaseService
): UserExtServiceResource{
    override fun initExtensionService(
        userId: String,
        serviceCode: String,
        extensionInfo: InitExtServiceDTO
    ): Result<Boolean> {
        return extServiceBaseService.addExtService(
            userId = userId,
            serviceCode = serviceCode,
            extensionInfo = extensionInfo
        )
    }

//    override fun submitExtensionService(
//        userId: String,
//        serviceId: String,
//        projectCode: String,
//        extensionInfo: SubmitDTO
//    ): Result<String?> {
//        return extServiceBaseService.updateExtService(
//            userId = userId,
//            projectCode = projectCode,
//            submitDTO = extensionInfo
//        )
//    }

    override fun submitExtensionService(
        userId: String,
        serviceId: String,
        projectCode: String,
        extensionInfo: SubmitDTO
    ): Result<String?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getExtensionServiceInfo(userId: String, serviceId: String): Result<ExtensionServiceVO> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listDeskExtService(
        userId: String,
        serviceCode: String?,
        page: Int?,
        pageSize: Int?
    ): Result<ExtensionAndVersionVO?> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.

//        return extServiceBaseService.getMyService(
//            userId = userId,
//            serviceCode = serviceCode,
//            page = page,
//            pageSize = pageSize
//        )
    }

    override fun getExtensionServiceInfoList(
        serviceId: String?,
        category: String?,
        page: Int?,
        pageSize: Int?
    ): Result<ExtensionServiceVO> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}