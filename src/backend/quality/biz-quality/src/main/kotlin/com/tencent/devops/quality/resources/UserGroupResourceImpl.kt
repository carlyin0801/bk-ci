package com.tencent.devops.quality.resources

import com.tencent.devops.common.api.exception.ParamBlankException
import com.tencent.devops.common.api.pojo.Page
import com.tencent.devops.common.api.pojo.Result
import com.tencent.devops.common.api.util.PageUtil
import com.tencent.devops.common.web.RestResource
import com.tencent.devops.quality.pojo.Group
import com.tencent.devops.quality.pojo.GroupCreate
import com.tencent.devops.quality.api.UserGroupResource
import com.tencent.devops.quality.pojo.GroupSummaryWithPermission
import com.tencent.devops.quality.pojo.GroupUpdate
import com.tencent.devops.quality.pojo.GroupUsers
import com.tencent.devops.quality.pojo.ProjectGroupAndUsers
import com.tencent.devops.quality.service.GroupService
import org.springframework.beans.factory.annotation.Autowired

@RestResource
class UserGroupResourceImpl @Autowired constructor(
    private val groupService: GroupService
) : UserGroupResource {
    override fun list(userId: String, projectId: String, page: Int?, pageSize: Int?): Result<Page<GroupSummaryWithPermission>> {
        checkParam(userId, projectId)
        val pageNotNull = page ?: 0
        val pageSizeNotNull = pageSize ?: 20
        val limit = PageUtil.convertPageSizeToSQLLimit(pageNotNull, pageSizeNotNull)
        val result = groupService.list(userId, projectId, limit.offset, limit.limit)
        return Result(Page(pageNotNull, pageSizeNotNull, result.first, result.second))
    }

    override fun projectGroupAndUsers(userId: String, projectId: String): Result<List<ProjectGroupAndUsers>> {
        checkParam(userId, projectId)
        return Result(groupService.getProjectGroupAndUsers(userId, projectId))
    }

    override fun create(userId: String, projectId: String, group: GroupCreate): Result<Boolean> {
        checkParam(userId, projectId)
        groupService.create(userId, projectId, group)
        return Result(true)
    }

    override fun get(userId: String, projectId: String, groupHashId: String): Result<Group> {
        checkParam(userId, projectId, groupHashId)
        return Result(groupService.get(userId, projectId, groupHashId))
    }

    override fun getUsers(userId: String, projectId: String, groupHashId: String): Result<GroupUsers> {
        checkParam(userId, projectId, groupHashId)
        return Result(groupService.getUsers(userId, projectId, groupHashId))
    }

    override fun edit(userId: String, projectId: String, groupHashId: String, group: GroupUpdate): Result<Boolean> {
        checkParam(userId, projectId, groupHashId)
        groupService.edit(userId, projectId, groupHashId, group)
        return Result(true)
    }

    override fun delete(userId: String, projectId: String, groupHashId: String): Result<Boolean> {
        checkParam(userId, projectId, groupHashId)
        groupService.delete(userId, projectId, groupHashId)
        return Result(true)
    }

    fun checkParam(userId: String, projectId: String) {
        if (userId.isBlank()) {
            throw ParamBlankException("Invalid userId")
        }
        if (projectId.isBlank()) {
            throw ParamBlankException("Invalid projectId")
        }
    }

    fun checkParam(userId: String, projectId: String, groupHashId: String) {
        checkParam(userId, projectId)
        if (groupHashId.isBlank()) {
            throw ParamBlankException("Invalid groupHashId")
        }
    }
}