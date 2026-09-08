package com.tencent.devops.common.pipeline.pojo

import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ManualReviewAction
import com.tencent.devops.common.pipeline.pojo.element.atom.ManualReviewParam
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class StagePauseCheckTest {

    @Test
    fun parseReviewParams() {
        val check = StagePauseCheck(
            manualTrigger = true,
            reviewParams = mutableListOf(
                ManualReviewParam(key = "p1", value = "111"),
                ManualReviewParam(key = "p2", value = "222")
            )
        )
        val originKeys = check.reviewParams?.map { it.key }?.toList()
        val params = mutableListOf(
            ManualReviewParam(key = "p1", value = "123"),
            ManualReviewParam(key = "p2", value = "222")
        )
        Assertions.assertEquals(
            mutableListOf(ManualReviewParam(key = "p1", value = "123")),
            check.parseReviewParams(params)
        )
        Assertions.assertEquals(
            check.reviewParams?.map { it.key }?.toList(),
            originKeys
        )
    }

    @Test
    fun `abort pending review group records operator and suggest`() {
        val check = StagePauseCheck(
            status = BuildStatus.REVIEWING.name,
            reviewGroups = mutableListOf(
                StageReviewGroup(id = "g1", name = "Flow 1", reviewers = listOf("alice"))
            )
        )

        val aborted = check.reviewGroup(
            userId = "bob",
            action = ManualReviewAction.ABORT,
            groupId = "g1",
            suggest = "bob 取消了执行"
        )

        Assertions.assertNotNull(aborted)
        Assertions.assertEquals(ManualReviewAction.ABORT.name, aborted?.status)
        Assertions.assertEquals("bob", aborted?.operator)
        Assertions.assertEquals("bob 取消了执行", aborted?.suggest)
        Assertions.assertNull(check.groupToReview())
    }
}
