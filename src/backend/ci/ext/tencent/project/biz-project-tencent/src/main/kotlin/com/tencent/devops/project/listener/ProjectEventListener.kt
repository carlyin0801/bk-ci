package com.tencent.devops.project.listener

import com.tencent.devops.project.pojo.mq.ProjectCreateBroadCastEvent
import com.tencent.devops.project.pojo.mq.ProjectUpdateBroadCastEvent
import com.tencent.devops.project.pojo.mq.ProjectUpdateLogoBroadCastEvent
import org.springframework.stereotype.Component

/**
 * deng
 * 2019-12-17
 */
@Component
class ProjectEventListener {

    fun onReceiveProjectCreate(event: ProjectCreateBroadCastEvent) {

    }

    fun onReceiveProjectUpdate(event: ProjectUpdateBroadCastEvent) {

    }

    fun onReceiveProjectUpdateLogo(event: ProjectUpdateLogoBroadCastEvent) {

    }
}