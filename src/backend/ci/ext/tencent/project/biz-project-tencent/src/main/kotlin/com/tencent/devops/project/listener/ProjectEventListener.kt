package com.tencent.devops.project.listener

import com.tencent.devops.common.event.listener.Listener
import com.tencent.devops.project.pojo.ProjectUpdateLogoInfo
import com.tencent.devops.project.pojo.mq.ProjectBroadCastEvent
import com.tencent.devops.project.pojo.mq.ProjectCreateBroadCastEvent
import com.tencent.devops.project.pojo.mq.ProjectUpdateBroadCastEvent
import com.tencent.devops.project.pojo.mq.ProjectUpdateLogoBroadCastEvent
import com.tencent.devops.project.service.ProjectPaasCCService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * deng
 * 2019-12-17
 */
@Component
class ProjectEventListener @Autowired constructor(
    val projectPaasCCService: ProjectPaasCCService
) : Listener<ProjectBroadCastEvent> {

    override fun execute(event: ProjectBroadCastEvent) {
       if (event is ProjectCreateBroadCastEvent) {
           onReceiveProjectCreate(event)
       } else if (event is ProjectUpdateBroadCastEvent) {
           onReceiveProjectUpdate(event)
       } else if (event is ProjectUpdateLogoBroadCastEvent) {
           onReceiveProjectUpdateLogo(event)
       }
    }

    fun onReceiveProjectCreate(event: ProjectCreateBroadCastEvent) {
        projectPaasCCService.createPaasCCProject(
            userId = event.userId,
            accessToken = event.userId,
            projectId = event.projectId,
            projectCreateInfo = event.projectInfo
        )
    }

    fun onReceiveProjectUpdate(event: ProjectUpdateBroadCastEvent) {
        projectPaasCCService.updatePaasCCProject(
            userId = event.userId,
            accessToken = event.userId,
            projectId = event.projectId,
            projectUpdateInfo = event.projectInfo
        )
    }

    fun onReceiveProjectUpdateLogo(event: ProjectUpdateLogoBroadCastEvent) {
        val projectUpdateLogoInfo = ProjectUpdateLogoInfo(
            logo_addr = event.logoAddr,
            updator = event.userId
        )
        projectPaasCCService.updatePaasCCProjectLogo(
            userId = event.userId,
            accessToken = event.userId,
            projectId = event.projectId,
            projectUpdateLogoInfo = projectUpdateLogoInfo
        )
    }
}