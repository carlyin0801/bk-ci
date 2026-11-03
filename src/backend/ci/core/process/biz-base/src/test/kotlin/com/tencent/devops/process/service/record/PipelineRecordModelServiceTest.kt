package com.tencent.devops.process.service.record

import com.tencent.devops.common.api.util.JsonUtil
import com.tencent.devops.common.api.util.timestampmilli
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.Container
import com.tencent.devops.common.pipeline.container.Stage
import com.tencent.devops.common.pipeline.container.VMBuildContainer
import com.tencent.devops.common.pipeline.enums.BuildScriptType
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.pojo.element.agent.LinuxScriptElement
import com.tencent.devops.common.pipeline.pojo.time.BuildRecordTimeCost
import com.tencent.devops.common.pipeline.pojo.time.BuildTimestampType
import com.tencent.devops.common.pipeline.enums.BuildRecordTimeStamp
import com.tencent.devops.common.pipeline.utils.ModelUtils
import com.tencent.devops.process.dao.record.BuildRecordContainerDao
import com.tencent.devops.process.dao.record.BuildRecordStageDao
import com.tencent.devops.process.dao.record.BuildRecordTaskDao
import com.tencent.devops.process.engine.cfg.ModelTaskIdGenerator
import com.tencent.devops.process.engine.service.PipelinePostElementService
import com.tencent.devops.process.pojo.pipeline.record.BuildRecordContainer
import com.tencent.devops.process.pojo.pipeline.record.BuildRecordStage
import com.tencent.devops.process.pojo.pipeline.record.BuildRecordTask
import com.tencent.devops.process.pojo.pipeline.record.MergeBuildRecordParam
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDateTime
import org.jooq.DSLContext
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class PipelineRecordModelServiceTest {

    private val stageDao: BuildRecordStageDao = mockk()
    private val containerDao: BuildRecordContainerDao = mockk()
    private val taskDao: BuildRecordTaskDao = mockk()
    private val service = PipelineRecordModelService(
        buildRecordStageDao = stageDao,
        buildRecordContainerDao = containerDao,
        buildRecordTaskDao = taskDao,
        pipelinePostElementService = mockk(relaxed = true),
        dslContext = mockk(relaxed = true),
        modelTaskIdGenerator = mockk<ModelTaskIdGenerator>(relaxed = true)
    )

    @Test
    fun getBuildRecordKeepsJobCostAndExposesRecordStartEnd() {
        val runningStart = LocalDateTime.of(2026, 10, 27, 16, 10, 0)
        val queueStart = LocalDateTime.of(2026, 10, 27, 16, 0, 0)
        val groupStart = LocalDateTime.of(2026, 10, 27, 16, 10, 0)
        val groupEnd = LocalDateTime.of(2026, 10, 27, 16, 20, 0)
        val childStart = LocalDateTime.of(2026, 10, 27, 16, 12, 8)
        val childEnd = LocalDateTime.of(2026, 10, 27, 16, 12, 40)
        val skipEnd = LocalDateTime.of(2026, 10, 27, 16, 11, 0)
        val jobCost = BuildRecordTimeCost(
            systemCost = 12_000,
            executeCost = 130_000,
            totalCost = 142_000
        )

        val baseModel = model()
        val baseMap = JsonUtil.toMutableMap(baseModel)
        every { stageDao.getLatestRecords(any(), any(), any(), any(), any()) } returns listOf(
            stageRecord()
        )
        every {
            containerDao.getLatestNormalRecords(any(), any(), any(), any(), any(), any())
        } returns listOf(
            containerRecord(
                containerId = "1",
                status = BuildStatus.RUNNING,
                startTime = runningStart,
                endTime = null,
                containerVar = mutableMapOf(Container::timeCost.name to jobCost)
            ),
            containerRecord(
                containerId = "2",
                status = BuildStatus.SKIP,
                startTime = null,
                endTime = skipEnd
            ),
            containerRecord(
                containerId = "3",
                status = BuildStatus.QUEUE,
                startTime = queueStart,
                endTime = null
            ),
            containerRecord(
                containerId = "4",
                status = BuildStatus.SUCCEED,
                startTime = groupStart,
                endTime = groupEnd,
                matrixGroupFlag = true
            )
        )
        every { containerDao.getLatestMatrixRecords(any(), any(), any(), any(), any()) } returns listOf(
            containerRecord(
                containerId = "5",
                status = BuildStatus.SUCCEED,
                startTime = childStart,
                endTime = childEnd,
                matrixGroupId = "4"
            )
        )
        every {
            taskDao.getLatestNormalRecords(any(), any(), any(), any(), any(), any())
        } returns emptyList()
        every { taskDao.getLatestMatrixRecords(any(), any(), any(), any(), any()) } returns listOf(
            taskRecord(containerId = "5", taskId = "e-matrix")
        )

        val recordModelMap = mutableMapOf<String, Any>()
        service.generateFieldRecordModelMap(
            MergeBuildRecordParam(
                projectId = PROJECT_ID,
                pipelineId = PIPELINE_ID,
                buildId = BUILD_ID,
                executeCount = 1,
                recordModelMap = recordModelMap,
                pipelineBaseModelMap = baseMap
            ),
            mockk<DSLContext>(relaxed = true)
        )
        val merged = ModelUtils.generatePipelineBuildModel(baseMap, recordModelMap)
        val stage = merged.stages.single()
        assertEquals(STAGE_START.timestampmilli(), stage.startTime)
        assertNull(stage.endTime)
        val jobs = stage.containers

        val running = jobs[0]
        assertEquals(runningStart.timestampmilli(), running.startTime)
        assertNull(running.endTime)
        assertEquals(130_000, running.timeCost?.executeCost)
        assertEquals(12_000, running.timeCost?.systemCost)
        assertEquals(142_000, running.timeCost?.totalCost)

        val skipped = jobs[1]
        assertNull(skipped.startTime)
        assertNull(skipped.endTime)

        val queued = jobs[2]
        assertEquals(queueStart.timestampmilli(), queued.startTime)
        assertNull(queued.endTime)

        val group = jobs[3] as VMBuildContainer
        assertEquals(groupStart.timestampmilli(), group.startTime)
        assertEquals(groupEnd.timestampmilli(), group.endTime)
        val child = group.groupContainers!!.single()
        assertEquals(childStart.timestampmilli(), child.startTime)
        assertEquals(childEnd.timestampmilli(), child.endTime)
        val step = child.elements.single()
        assertEquals(TASK_START.timestampmilli(), step.startTime)
        assertEquals(TASK_END.timestampmilli(), step.endTime)
    }

    @Test
    fun oldContainerJsonWithoutRuntimeTimeStillParses() {
        val container = JsonUtil.to(
            """{"@type":"vmBuild","id":"1","name":"job","elements":[]}""",
            VMBuildContainer::class.java
        )
        assertNull(container.startTime)
        assertNull(container.endTime)

        container.startTime = 1L
        container.endTime = 2L
        container.status = BuildStatus.SUCCEED.name
        container.resetBuildOption(executeCount = 2)
        assertNull(container.startTime)
        assertNull(container.endTime)
        assertNull(container.status)
        assertEquals(2, container.executeCount)
    }

    private fun model(): Model {
        return Model(
            name = "p",
            desc = "",
            stages = listOf(
                Stage(
                    id = STAGE_ID,
                    name = "stage",
                    containers = listOf(
                        job("1", "e-run"),
                        job("2", "e-skip"),
                        job("3", "e-queue"),
                        job("4", "e-matrix", matrixGroup = true)
                    )
                )
            )
        )
    }

    private fun job(id: String, elementId: String, matrixGroup: Boolean = false): VMBuildContainer {
        return VMBuildContainer(
            id = id,
            name = "job-$id",
            matrixGroupFlag = matrixGroup,
            elements = listOf(
                LinuxScriptElement(
                    id = elementId,
                    script = "echo hi",
                    scriptType = BuildScriptType.SHELL,
                    continueNoneZero = false
                )
            )
        )
    }

    private fun stageRecord(): BuildRecordStage {
        return BuildRecordStage(
            buildId = BUILD_ID,
            projectId = PROJECT_ID,
            pipelineId = PIPELINE_ID,
            resourceVersion = 1,
            stageId = STAGE_ID,
            executeCount = 1,
            stageSeq = 1,
            stageVar = mutableMapOf(Stage::name.name to "stage"),
            status = BuildStatus.RUNNING.name,
            startTime = STAGE_START,
            timestamps = emptyMap()
        )
    }

    private fun containerRecord(
        containerId: String,
        status: BuildStatus,
        startTime: LocalDateTime?,
        endTime: LocalDateTime?,
        matrixGroupFlag: Boolean = false,
        matrixGroupId: String? = null,
        containerVar: MutableMap<String, Any> = mutableMapOf()
    ): BuildRecordContainer {
        return BuildRecordContainer(
            buildId = BUILD_ID,
            projectId = PROJECT_ID,
            pipelineId = PIPELINE_ID,
            resourceVersion = 1,
            stageId = STAGE_ID,
            containerId = containerId,
            executeCount = 1,
            containerVar = containerVar,
            containerType = VMBuildContainer.classType,
            status = status.name,
            matrixGroupFlag = matrixGroupFlag,
            matrixGroupId = matrixGroupId,
            startTime = startTime,
            endTime = endTime,
            timestamps = emptyMap<BuildTimestampType, BuildRecordTimeStamp>()
        )
    }

    private fun taskRecord(containerId: String, taskId: String): BuildRecordTask {
        return BuildRecordTask(
            buildId = BUILD_ID,
            projectId = PROJECT_ID,
            pipelineId = PIPELINE_ID,
            resourceVersion = 1,
            stageId = STAGE_ID,
            containerId = containerId,
            taskId = taskId,
            taskSeq = 2,
            executeCount = 1,
            taskVar = mutableMapOf(),
            classType = LinuxScriptElement.classType,
            atomCode = LinuxScriptElement.classType,
            status = BuildStatus.SUCCEED.name,
            startTime = TASK_START,
            endTime = TASK_END,
            timestamps = emptyMap()
        )
    }

    companion object {
        private const val PROJECT_ID = "p"
        private const val PIPELINE_ID = "pipeline"
        private const val BUILD_ID = "b-1"
        private const val STAGE_ID = "stage-1"
        private val STAGE_START: LocalDateTime = LocalDateTime.of(2026, 10, 27, 15, 59, 0)
        private val TASK_START: LocalDateTime = LocalDateTime.of(2026, 10, 27, 16, 12, 10)
        private val TASK_END: LocalDateTime = LocalDateTime.of(2026, 10, 27, 16, 12, 38)
    }
}
