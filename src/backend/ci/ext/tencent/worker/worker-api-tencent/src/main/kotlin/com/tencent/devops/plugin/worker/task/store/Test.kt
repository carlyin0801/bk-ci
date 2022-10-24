package com.tencent.devops.plugin.worker.task.store

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.pipeline.element.store.AtomRunEnvPrepareElement
import com.tencent.devops.worker.common.task.TaskFactory

fun main() {
    TaskFactory.init()
    val task = TaskFactory.create(AtomRunEnvPrepareElement.classType)
    println(JsonUtil.toJson(task))
}
