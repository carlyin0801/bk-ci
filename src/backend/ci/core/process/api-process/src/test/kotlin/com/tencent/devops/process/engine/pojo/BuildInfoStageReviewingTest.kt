package com.tencent.devops.process.engine.pojo

import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ChannelCode
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildInfoStageReviewingTest {

    @Test
    fun `stage success without endTime is reviewing and not finished`() {
        val buildInfo = buildInfo(status = BuildStatus.STAGE_SUCCESS, startTime = 1000L, endTime = null)

        assertTrue(buildInfo.isStageSuccess())
        assertTrue(buildInfo.isStageReviewing())
        assertFalse(buildInfo.isFinish())
    }

    @Test
    fun `stage success with endTime is finished and not reviewing`() {
        val buildInfo = buildInfo(status = BuildStatus.STAGE_SUCCESS, startTime = 1000L, endTime = 2000L)

        assertTrue(buildInfo.isStageSuccess())
        assertFalse(buildInfo.isStageReviewing())
        assertTrue(buildInfo.isFinish())
    }

    @Test
    fun `running build is not stage reviewing`() {
        val buildInfo = buildInfo(status = BuildStatus.RUNNING, startTime = 1000L, endTime = null)

        assertFalse(buildInfo.isStageSuccess())
        assertFalse(buildInfo.isStageReviewing())
        assertFalse(buildInfo.isFinish())
    }

    private fun buildInfo(
        status: BuildStatus,
        startTime: Long?,
        endTime: Long?
    ) = BuildInfo(
        projectId = "p",
        pipelineId = "pipeline",
        buildId = "b",
        version = 1,
        versionName = null,
        yamlVersion = null,
        buildNum = 1,
        trigger = "MANUAL",
        status = status,
        queueTime = 1L,
        executeTime = 0L,
        startUser = "u",
        triggerUser = "u",
        startTime = startTime,
        endTime = endTime,
        taskCount = 1,
        firstTaskId = "t",
        parentBuildId = null,
        parentTaskId = null,
        channelCode = ChannelCode.BS,
        buildParameters = null,
        errorInfoList = null,
        stageStatus = null,
        debug = false,
        webhookType = null,
        recommendVersion = null,
        buildNumAlias = null
    )
}
