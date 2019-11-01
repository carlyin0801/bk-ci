package com.tencent.devops.gitci.pojo.task

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.gitci.TASK_TYPE
import com.tencent.devops.gitci.service.BuildConfig

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = TASK_TYPE)
@JsonSubTypes(
    JsonSubTypes.Type(value = BashTask::class, name = BashTask.taskType + BashTask.taskVersion),
    JsonSubTypes.Type(value = CodeCCScanTask::class, name = CodeCCScanTask.taskType + CodeCCScanTask.taskVersion),
    JsonSubTypes.Type(value = DockerRunDevCloudTask::class, name = DockerRunDevCloudTask.taskType + DockerRunDevCloudTask.taskVersion),
    JsonSubTypes.Type(value = MarketBuildTask::class, name = MarketBuildTask.taskType + MarketBuildTask.taskVersion)
)

abstract class AbstractTask(
    open val displayName: String?,
    open val inputs: AbstractInput?,
    open val condition: String?
) {
    abstract fun getTaskType(): String
    abstract fun getTaskVersion(): String

    abstract fun covertToElement(config: BuildConfig): Element
}

abstract class AbstractInput