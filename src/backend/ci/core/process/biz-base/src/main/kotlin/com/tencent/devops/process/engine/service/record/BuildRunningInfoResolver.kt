/*
 * Tencent is pleased to support the open source community by making BK-CI 蓝鲸持续集成平台 available.
 *
 * Copyright (C) 2019 Tencent.  All rights reserved.
 *
 * BK-CI 蓝鲸持续集成平台 is licensed under the MIT license.
 *
 * A copy of the MIT License is included in this file.
 *
 *
 * Terms of the MIT License:
 * ---------------------------------------------------
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of
 * the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN
 * NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY,
 * WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package com.tencent.devops.process.engine.service.record

import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.container.MutexGroup
import com.tencent.devops.common.pipeline.enums.BuildRunningType
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.StartType
import com.tencent.devops.common.pipeline.pojo.BuildQueueDetail
import com.tencent.devops.common.pipeline.pojo.BuildRunningInfo
import com.tencent.devops.common.pipeline.pojo.DependOnJobInfo
import com.tencent.devops.common.pipeline.pojo.JobWaitType
import com.tencent.devops.common.pipeline.pojo.PendingItemType
import com.tencent.devops.common.pipeline.pojo.PendingManualItem
import com.tencent.devops.common.pipeline.pojo.RelatedBuildBrief
import com.tencent.devops.common.pipeline.pojo.StagePauseCheck
import com.tencent.devops.common.pipeline.pojo.WaitingJobInfo
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.agent.ManualReviewUserTaskElement
import com.tencent.devops.common.pipeline.pojo.element.matrix.MatrixStatusElement
import com.tencent.devops.common.pipeline.pojo.element.quality.QualityGateInElement
import com.tencent.devops.common.pipeline.pojo.element.quality.QualityGateOutElement
import com.tencent.devops.common.pipeline.pojo.time.BuildTimestampType
import com.tencent.devops.common.web.utils.I18nUtil
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.dao.record.BuildRecordContainerDao
import com.tencent.devops.process.engine.control.ContainerLocation
import com.tencent.devops.process.engine.control.EndPositionUtils
import com.tencent.devops.process.engine.control.ModelPositionIndex
import com.tencent.devops.process.engine.control.StagePosition
import com.tencent.devops.process.engine.dao.PipelineBuildDao
import com.tencent.devops.process.engine.pojo.BuildInfo
import com.tencent.devops.process.engine.pojo.PipelineBuildContainer
import com.tencent.devops.process.engine.service.PipelineBuildQualityService
import com.tencent.devops.process.engine.service.PipelineContainerService
import com.tencent.devops.process.engine.service.PipelineRepositoryService
import org.jooq.DSLContext
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 构建运行态解析上下文。
 *
 * [model] 为读取侧已合并过构建记录的编排模型，其中的 Stage/Job/Task 状态即页面所见状态，
 * 因此待人工处理项可直接从中扫描，无需再查任务表与阶段表。
 */
data class BuildRunningContext(
    val buildInfo: BuildInfo,
    val model: Model,
    val executeCount: Int,
    val queueTime: Long,
    val startTime: Long?,
    /** 触发方式描述，由读取侧统一解析后传入，避免此处重复处理 webhook 触发器的解析逻辑 */
    val triggerDesc: String?
)

/**
 * 构建运行态详情解析器：为排队中/执行中的构建实时计算 [BuildRunningInfo]。
 *
 * 设计要点：
 * 1. **不落库，只在读取时算**。排队位置、前方排队、已等待时长都随时间变化，
 *    落库拿到的必然是过期快照，且排队构建每秒都在变会造成严重写放大。
 * 2. **按构建状态提前返回**。构建一旦结束就不进入本解析器，已结束构建的详情由 BuildEndInfo 表达，
 *    因此对绝大多数（已结束的）详情请求零额外开销。
 * 3. **位置口径与终态完全一致**，均复用 [EndPositionUtils] 的 Model 位置索引，
 *    保证同一个 Job 在运行态和终态卡片里的编码与路径不会冲突。
 */
@Suppress("TooManyFunctions", "LongParameterList")
@Component
class BuildRunningInfoResolver @Autowired constructor(
    private val dslContext: DSLContext,
    private val pipelineBuildDao: PipelineBuildDao,
    private val recordContainerDao: BuildRecordContainerDao,
    private val pipelineContainerService: PipelineContainerService,
    private val pipelineRepositoryService: PipelineRepositoryService,
    private val pipelineBuildQualityService: PipelineBuildQualityService
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(BuildRunningInfoResolver::class.java)

        /** 排队中的构建状态集合，与引擎出队时的查询口径保持一致 */
        private val QUEUE_STATUS_SET = listOf(BuildStatus.QUEUE, BuildStatus.QUEUE_CACHE)

        /**
         * 互斥类排队时间戳。[BuildTimestampType.containerCheckQueue] 圈定的排队时间戳中，
         * 除这两种外均为等待构建资源，据此推导等待类型而不必枚举各调度方式。
         */
        private val MUTEX_QUEUE_TYPES = setOf(
            BuildTimestampType.JOB_MUTEX_QUEUE,
            BuildTimestampType.JOB_AGENT_REUSE_MUTEX_QUEUE
        )

        /** 关联构建列表（占用中/前方排队）的展示上限，超出部分只计数不返回明细 */
        private const val RELATED_BUILD_MAX_SIZE = 10

        /** 等待中的Job与待人工处理项的展示上限，避免超大流水线撑爆响应体 */
        private const val LIST_MAX_SIZE = 50
    }

    /**
     * 解析构建运行态详情。返回 null 表示构建已结束或处于不需要展示运行态的状态。
     *
     * 整体做异常兜底：运行态详情属于展示增强，任何异常都不应让构建详情页打不开。
     */
    fun resolve(context: BuildRunningContext): BuildRunningInfo? {
        return try {
            val status = context.buildInfo.status
            when {
                status in QUEUE_STATUS_SET -> resolveQueue(context)
                status.isRunning() -> resolveRunning(context)
                // 已结束、从未执行、触发待审核等状态不属于运行态范畴
                else -> null
            }?.also { translate(it) }
        } catch (ignored: Throwable) {
            LOG.warn("ENGINE|${context.buildInfo.buildId}|BUILD_RUNNING_INFO|resolve failed", ignored)
            null
        }
    }

    /**
     * 统一填充展示用的国际化描述。
     *
     * 运行态对象在读取时现造，因此这里就是它唯一的生命周期节点，直接按请求方语言翻译即可，
     * 无需像终态那样在落库文案与展示文案之间来回转换。
     */
    private fun translate(info: BuildRunningInfo) {
        info.runningCategory = info.runningType.category
        info.runningTypeDesc = i18n("buildRunningType.${info.runningType.displayName}")
        info.waitingJobs?.forEach { job ->
            job.waitTypeDesc = i18n("jobWaitType.${job.waitType.displayName}")
            job.statusDesc = i18n("buildStatus.${BuildStatus.parse(job.status).statusName}", job.status)
        }
        info.pendingItems?.forEach { item ->
            item.itemTypeDesc = i18n("pendingItemType.${item.itemType.displayName}")
            item.handlerDesc = item.handlerDescCode?.let { i18n(it) }
        }
    }

    private fun resolveQueue(context: BuildRunningContext): BuildRunningInfo {
        val queueDetail = resolveQueueDetail(context)
        // 仅当确实配置了并发组且组内有构建在占用时才算并发组排队；
        // 流水线串行(SINGLE_LOCK)导致的排队同样会有"占用中"的构建，但成因不同，不能混为一谈
        val runningType = if (queueDetail?.concurrencyGroup != null && queueDetail.occupyingCount > 0) {
            BuildRunningType.QUEUE_CONCURRENCY
        } else {
            BuildRunningType.QUEUE_WAITING
        }
        return BuildRunningInfo(
            runningType = runningType,
            queueTime = context.queueTime,
            waitingTime = System.currentTimeMillis() - context.queueTime,
            triggerUser = context.buildInfo.triggerUser,
            triggerDesc = context.triggerDesc,
            queueDetail = queueDetail
        )
    }

    /**
     * 解析排队详情。分两种排队口径，与 `RunLockInterceptor` 的判定保持一致：
     * 配置了并发组时按并发组统计（可跨流水线），否则按当前流水线统计。
     */
    private fun resolveQueueDetail(context: BuildRunningContext): BuildQueueDetail? {
        val buildInfo = context.buildInfo
        val concurrencyGroup = buildInfo.concurrencyGroup?.takeIf { it.isNotBlank() }
        val relatedBuilds = if (concurrencyGroup != null) {
            loadConcurrencyGroupBuilds(buildInfo.projectId, concurrencyGroup)
        } else {
            loadPipelineBuilds(buildInfo.projectId, buildInfo.pipelineId)
        }
        if (relatedBuilds.isEmpty()) return null

        // 出队顺序以入队时间为准（与 PipelineBuildDao.getOneConcurrencyQueueBuild 的排序口径一致），
        // 构建号作为同一毫秒入队时的稳定次序兜底
        val queued = relatedBuilds
            .filter { it.status in QUEUE_STATUS_SET }
            .sortedWith(compareBy({ it.queueTime }, { it.buildNum }))
        val running = relatedBuilds.filter { it.status == BuildStatus.RUNNING }

        val selfIndex = queued.indexOfFirst { it.buildId == buildInfo.buildId }
        val ahead = if (selfIndex > 0) queued.subList(0, selfIndex) else emptyList()
        val shownAhead = ahead.take(RELATED_BUILD_MAX_SIZE)
        val shownRunning = running.take(RELATED_BUILD_MAX_SIZE)
        // 只为真正要展示的构建补全流水线名与触发描述，超出上限的部分只参与计数
        val pipelineNames = loadPipelineNames(buildInfo.projectId, shownAhead + shownRunning)
        val language = requestLanguage()

        return BuildQueueDetail(
            // 定位不到自身时（并发组配置刚变更等边界情况）不编造位置，返回0由前端隐藏该项
            queuePosition = if (selfIndex < 0) 0 else selfIndex + 1,
            concurrencyGroup = concurrencyGroup
        ).apply {
            occupyingBuilds = shownRunning.map { toBrief(it, pipelineNames, language) }.takeIf { it.isNotEmpty() }
            occupyingCount = running.size
            aheadBuilds = shownAhead.map { toBrief(it, pipelineNames, language) }.takeIf { it.isNotEmpty() }
            aheadCount = ahead.size
        }
    }

    /**
     * 加载并发组内排队中与运行中的构建。
     *
     * 并发组维度的查询只返回 (pipelineId, buildId)，需再回表补全展示所需字段；
     * 一次性把排队态和运行态一起查出来，避免分两次扫描。
     */
    private fun loadConcurrencyGroupBuilds(projectId: String, concurrencyGroup: String): List<BuildInfo> {
        val buildIds = pipelineBuildDao.getBuildTasksByConcurrencyGroup(
            dslContext = dslContext,
            projectId = projectId,
            concurrencyGroup = concurrencyGroup,
            statusSet = QUEUE_STATUS_SET + BuildStatus.RUNNING
        ).map { it.value2() }
        if (buildIds.isEmpty()) return emptyList()
        return pipelineBuildDao.listBuildInfoByBuildIds(
            dslContext = dslContext,
            buildIds = buildIds,
            projectId = projectId
        )
    }

    private fun loadPipelineBuilds(projectId: String, pipelineId: String): List<BuildInfo> {
        return pipelineBuildDao.getBuildTasksByStatus(
            dslContext = dslContext,
            projectId = projectId,
            pipelineId = pipelineId,
            statusSet = (QUEUE_STATUS_SET + BuildStatus.RUNNING).toSet()
        )
    }

    /** 并发组可跨流水线，需批量补全流水线名称用于列表展示 */
    private fun loadPipelineNames(projectId: String, builds: List<BuildInfo>): Map<String, String> {
        val pipelineIds = builds.map { it.pipelineId }.toSet()
        if (pipelineIds.isEmpty()) return emptyMap()
        return try {
            pipelineRepositoryService.listPipelineNameByIds(projectId, pipelineIds)
        } catch (ignored: Throwable) {
            LOG.warn("BUILD_RUNNING_INFO|load pipeline names failed|$projectId", ignored)
            emptyMap()
        }
    }

    private fun toBrief(
        buildInfo: BuildInfo,
        pipelineNames: Map<String, String>,
        language: String
    ): RelatedBuildBrief {
        val now = System.currentTimeMillis()
        // 运行中的算已运行时长，排队中的算已等待时长
        val costTime = if (buildInfo.status == BuildStatus.RUNNING) {
            buildInfo.startTime?.let { now - it }
        } else {
            now - buildInfo.queueTime
        }
        return RelatedBuildBrief(
            projectId = buildInfo.projectId,
            pipelineId = buildInfo.pipelineId,
            pipelineName = pipelineNames[buildInfo.pipelineId],
            buildId = buildInfo.buildId,
            buildNum = buildInfo.buildNum,
            triggerUser = buildInfo.triggerUser,
            triggerDesc = readableStartType(buildInfo, language),
            buildMsg = buildInfo.buildMsg,
            status = buildInfo.status.name,
            costTime = costTime?.takeIf { it >= 0 }
        )
    }

    /** 请求方语言只需解析一次，避免为列表中的每个构建重复查询用户语言设置 */
    private fun requestLanguage(): String = I18nUtil.getLanguage(I18nUtil.getRequestUserId())

    private fun readableStartType(buildInfo: BuildInfo, language: String): String? = try {
        StartType.toReadableString(buildInfo.trigger, buildInfo.channelCode, language)
    } catch (ignored: Throwable) {
        buildInfo.trigger
    }

    private fun resolveRunning(context: BuildRunningContext): BuildRunningInfo {
        val index = EndPositionUtils.buildPositionIndex(context.model)
        val waitingJobs = collectWaitingJobs(context, index)
        val pendingItems = collectPendingItems(context, index)
        val runningType = if (waitingJobs.isNotEmpty()) {
            BuildRunningType.RUNNING_JOB_WAITING
        } else {
            BuildRunningType.RUNNING_NORMAL
        }
        return BuildRunningInfo(
            runningType = runningType,
            currentPhase = resolveCurrentPhase(index, waitingJobs, pendingItems),
            queueTime = context.queueTime,
            startTime = context.startTime,
            runningTime = context.startTime?.let { System.currentTimeMillis() - it },
            triggerUser = context.buildInfo.triggerUser,
            triggerDesc = context.triggerDesc
        ).withWaitingJobs(waitingJobs).withPendingItems(pendingItems)
    }

    /**
     * 当前阶段描述：优先展示特殊场景描述，不是特殊场景则默认展示正在运行的Stage名。
     */
    private fun resolveCurrentPhase(
        index: ModelPositionIndex,
        waitingJobs: List<WaitingJobInfo>,
        pendingItems: List<PendingManualItem>
    ): String? {
        return when {
            waitingJobs.isNotEmpty() -> i18n(ProcessMessageCode.BK_BUILD_RUNNING_JOB_QUEUING)
            pendingItems.isNotEmpty() -> i18n(ProcessMessageCode.BK_BUILD_RUNNING_PENDING_MANUAL)
            else -> index.containers()
                .firstOrNull { BuildStatus.parse(it.container.status) == BuildStatus.RUNNING }
                ?.stagePosition?.stageName
        }
    }

    /**
     * 收集等待中的 Job。
     *
     * 以引擎容器表为准而非 Model：互斥组名在运行时才解析（`runtimeMutexGroup`），只存在于引擎侧；
     * 排队时长则取自构建记录的排队时间戳。
     */
    private fun collectWaitingJobs(context: BuildRunningContext, index: ModelPositionIndex): List<WaitingJobInfo> {
        val buildInfo = context.buildInfo
        val containers = pipelineContainerService.listContainers(
            projectId = buildInfo.projectId,
            buildId = buildInfo.buildId,
            executeCount = context.executeCount
        )
        // 绝大多数运行中的构建没有等待的 Job，先做一次廉价判断，避免为其多查一次记录表
        val candidates = containers.filter {
            it.status == BuildStatus.DEPENDENT_WAITING ||
                it.status == BuildStatus.QUEUE ||
                it.status == BuildStatus.PREPARE_ENV
        }
        if (candidates.isEmpty()) return emptyList()

        val queueTimestamps = loadQueueTimestamps(context)
        val now = System.currentTimeMillis()
        return candidates.mapNotNull { container ->
            buildWaitingJob(
                context = context,
                container = container,
                index = index,
                activeQueueStamps = queueTimestamps[container.containerId],
                now = now
            )
        }.take(LIST_MAX_SIZE)
    }

    /**
     * 加载各容器的排队时间戳。
     *
     * 时间戳集合以 [BuildTimestampType.containerCheckQueue] 为准——这是引擎侧对
     * 「什么情况算 Job 在排队」的权威定义，与之对齐可保证运行态展示和引擎判定始终一致。
     *
     * 这也是区分「资源排队」与「正常准备环境」的唯一可靠依据：二者的容器状态同为 PREPARE_ENV，
     * 只有确实进入了构建资源队列才会写入排队时间戳。若仅按状态判定，
     * 每个构建在正常拉起构建机的几秒内都会被误报成「资源排队」。
     */
    private fun loadQueueTimestamps(context: BuildRunningContext): Map<String, Map<BuildTimestampType, Long>> {
        return try {
            recordContainerDao.getRecords(
                dslContext = dslContext,
                projectId = context.buildInfo.projectId,
                pipelineId = context.buildInfo.pipelineId,
                buildId = context.buildInfo.buildId,
                executeCount = context.executeCount
            ).associate { record ->
                // 只保留仍在进行中的排队时间戳（endTime 为空表示尚未出队）
                record.containerId to record.timestamps
                    .filterKeys { it.containerCheckQueue() }
                    .mapNotNull { (type, stamp) ->
                        if (stamp.endTime == null && stamp.startTime != null) type to stamp.startTime!! else null
                    }.toMap()
            }
        } catch (ignored: Throwable) {
            LOG.warn("BUILD_RUNNING_INFO|${context.buildInfo.buildId}|load queue timestamps failed", ignored)
            emptyMap()
        }
    }

    private fun buildWaitingJob(
        context: BuildRunningContext,
        container: PipelineBuildContainer,
        index: ModelPositionIndex,
        activeQueueStamps: Map<BuildTimestampType, Long>?,
        now: Long
    ): WaitingJobInfo? {
        val location = index.locateContainer(container.containerId) ?: return null
        val mutexGroup = container.controlOption.mutexGroup
        val waitType = resolveWaitType(container, mutexGroup, activeQueueStamps) ?: return null
        val queueStartTime = activeQueueStamps?.values?.minOrNull()
        return WaitingJobInfo(
            waitType = waitType,
            position = location.position,
            componentPath = location.componentPath,
            status = container.status.name,
            stageId = container.stageId,
            containerId = container.containerId,
            containerHashId = container.containerHashId ?: location.container.containerHashId,
            matrixFlag = (container.matrixGroupFlag == true || location.matrixFlag).takeIf { it },
            mutexGroup = mutexGroup?.fetchRuntimeMutexGroup()?.takeIf { it.isNotBlank() },
            waitingTime = queueStartTime?.let { now - it },
            dependOnJobs = if (waitType == JobWaitType.DEPENDENT) {
                resolveDependOnJobs(context, container, index)
            } else null
        )
    }

    /**
     * 判定 Job 的等待类型，判定顺序由确定到模糊：
     * 依赖等待有独立状态最明确；互斥其次（有互斥组配置且在排队）；
     * 等待构建资源最模糊，必须有排队时间戳佐证，否则宁可不报。
     */
    private fun resolveWaitType(
        container: PipelineBuildContainer,
        mutexGroup: MutexGroup?,
        activeQueueStamps: Map<BuildTimestampType, Long>?
    ): JobWaitType? {
        if (container.status == BuildStatus.DEPENDENT_WAITING) return JobWaitType.DEPENDENT
        val queueTypes = activeQueueStamps?.keys.orEmpty()
        if (queueTypes.any { it in MUTEX_QUEUE_TYPES } ||
            (container.status == BuildStatus.QUEUE && mutexGroup?.enable == true)
        ) {
            return JobWaitType.MUTEX
        }
        // 互斥之外的排队时间戳一律归为等待构建资源，这里刻意不枚举具体调度方式：
        // 候选集已由 containerCheckQueue() 圈定(见 loadQueueTimestamps)，将来新增调度方式的
        // 排队时间戳只要纳入该判定，此处即可自动识别，不必再逐个补充调度类型
        if (queueTypes.any { it !in MUTEX_QUEUE_TYPES }) {
            return JobWaitType.RESOURCE
        }
        return null
    }

    /**
     * 解析依赖等待的 Job 导航信息，口径与取消场景的依赖解析保持一致。
     */
    private fun resolveDependOnJobs(
        context: BuildRunningContext,
        container: PipelineBuildContainer,
        index: ModelPositionIndex
    ): List<DependOnJobInfo>? {
        val depMap = container.controlOption.jobControlOption.dependOnContainerId2JobIds
        if (depMap.isNullOrEmpty()) return null
        return depMap.mapNotNull { (depContainerId, _) ->
            val depLocation = index.locateContainer(depContainerId) ?: return@mapNotNull null
            DependOnJobInfo(
                jobName = depLocation.container.name,
                projectId = context.buildInfo.projectId,
                pipelineId = context.buildInfo.pipelineId,
                buildId = context.buildInfo.buildId,
                executeCount = context.executeCount,
                stageId = depLocation.stagePosition.stageId,
                containerId = depContainerId,
                matrixFlag = depLocation.matrixFlag.takeIf { it }
            )
        }.ifEmpty { null }
    }

    /**
     * 收集待人工处理项。全部从读取侧已合并记录的 Model 中扫描，不额外查任务表与阶段表——
     * Model 里的 Stage/Job/Task 状态就是页面所见状态，与前端展示天然一致。
     */
    private fun collectPendingItems(context: BuildRunningContext, index: ModelPositionIndex): List<PendingManualItem> {
        val items = mutableListOf<PendingManualItem>()
        context.model.stages.forEach { stage ->
            // locateStage 已跳过触发器阶段，取不到位置的阶段直接略过
            val stagePosition = index.locateStage(stage.id) ?: return@forEach
            listOfNotNull(stage.checkIn, stage.checkOut).forEach { check ->
                buildStagePendingItem(check, stagePosition)?.let { items.add(it) }
            }
        }
        index.containers().forEach { location ->
            // 矩阵分组容器只是子容器的占位，其下的插件不会真正执行，
            // 真实待处理项都在矩阵子容器中，跳过父容器避免同一项被统计两次
            if (location.container.matrixGroupFlag == true) return@forEach
            location.container.elements.forEach { element ->
                buildTaskPendingItem(context, element, location)?.let { items.add(it) }
            }
        }
        return items.take(LIST_MAX_SIZE)
    }

    /**
     * 阶段级待处理项：准入/准出的人工审核与质量红线把关。
     */
    private fun buildStagePendingItem(check: StagePauseCheck, stagePosition: StagePosition): PendingManualItem? {
        return when (BuildStatus.parse(check.status)) {
            BuildStatus.REVIEWING -> PendingManualItem(
                itemType = PendingItemType.STAGE_REVIEW,
                // 多级审核流时只有当前待审核组的人能处理，因此取当前组而非全部审核人
                handlers = check.groupToReview()?.reviewers?.filter { it.isNotBlank() }?.takeIf { it.isNotEmpty() },
                position = stagePosition.position,
                componentPath = stagePosition.stageName,
                stageId = stagePosition.stageId,
                reviewDesc = check.reviewDesc?.takeIf { it.isNotBlank() }
            )
            BuildStatus.QUALITY_CHECK_WAIT -> PendingManualItem(
                itemType = PendingItemType.STAGE_QUALITY_GATE,
                position = stagePosition.position,
                componentPath = stagePosition.stageName,
                stageId = stagePosition.stageId
            )
            else -> null
        }
    }

    /**
     * 任务级待处理项：执行前暂停、人工审核、插件级质量红线拦截。
     */
    private fun buildTaskPendingItem(
        context: BuildRunningContext,
        element: Element,
        location: ContainerLocation
    ): PendingManualItem? {
        val status = BuildStatus.parse(element.status)
        val taskId = element.id ?: return null
        val itemType = when {
            status == BuildStatus.PAUSE -> PendingItemType.TASK_PAUSE
            status != BuildStatus.REVIEWING -> return null
            isQualityGate(element) -> PendingItemType.TASK_QUALITY_GATE
            isManualReview(element) -> PendingItemType.TASK_REVIEW
            // REVIEWING 但既不是审核插件也不是红线插件时不猜测归类
            else -> return null
        }
        val taskSeq = location.taskSeqOf(taskId)
        return PendingManualItem(
            itemType = itemType,
            handlers = resolveHandlers(context, itemType, element),
            // 暂停无固定处理人名单，由是否具备执行权限决定
            handlerDescCode = ProcessMessageCode.BK_BUILD_RUNNING_HANDLER_EXECUTE_PERM
                .takeIf { itemType == PendingItemType.TASK_PAUSE },
            position = taskSeq?.let { "${location.position}-$it" } ?: location.position,
            componentPath = "${location.componentPath}/${element.name}",
            stageId = location.stagePosition.stageId,
            containerId = location.container.containerId ?: location.container.id,
            taskId = taskId,
            containerHashId = location.container.containerHashId,
            stepId = element.stepId,
            matrixFlag = location.matrixFlag.takeIf { it },
            reviewDesc = reviewDescOf(element)
        )
    }

    private fun reviewDescOf(element: Element): String? = when (element) {
        is ManualReviewUserTaskElement -> element.desc
        is MatrixStatusElement -> element.desc
        else -> null
    }?.takeIf { it.isNotBlank() }

    private fun resolveHandlers(
        context: BuildRunningContext,
        itemType: PendingItemType,
        element: Element
    ): List<String>? {
        val handlers = when (itemType) {
            PendingItemType.TASK_REVIEW -> reviewUsersOf(element)
            PendingItemType.TASK_QUALITY_GATE -> resolveQualityAuditUsers(context, element)
            else -> null
        }
        return handlers?.filter { it.isNotBlank() }?.distinct()?.takeIf { it.isNotEmpty() }
    }

    /** 审核人可能来自模板变量，运行时解析后的实际名单在 actualReviewUsers 中，优先取之 */
    private fun reviewUsersOf(element: Element): List<String>? = when (element) {
        is ManualReviewUserTaskElement -> element.actualReviewUsers ?: element.reviewUsers
        is MatrixStatusElement -> element.actualReviewUsers ?: element.reviewUsers
        else -> null
    }

    /**
     * 质量红线的把关人配置在 quality 模块的规则上，Model 中没有，需跨模块查询。
     * 仅对确实处于待把关状态的红线插件发起查询，正常构建不会触发。
     */
    private fun resolveQualityAuditUsers(context: BuildRunningContext, element: Element): List<String>? {
        val interceptTask = when (element) {
            is QualityGateInElement -> element.interceptTask
            is QualityGateOutElement -> element.interceptTask
            is MatrixStatusElement -> element.interceptTask
            else -> null
        }?.takeIf { it.isNotBlank() } ?: return null
        return try {
            pipelineBuildQualityService.getAuditUserList(
                projectId = context.buildInfo.projectId,
                pipelineId = context.buildInfo.pipelineId,
                buildId = context.buildInfo.buildId,
                taskId = interceptTask
            ).toList()
        } catch (ignored: Throwable) {
            // 质量服务不可用时退化为不展示把关人，不影响其余待处理项
            LOG.warn("BUILD_RUNNING_INFO|${context.buildInfo.buildId}|load quality audit users failed", ignored)
            null
        }
    }

    private fun isManualReview(element: Element): Boolean =
        element is ManualReviewUserTaskElement ||
            (element as? MatrixStatusElement)?.originClassType == ManualReviewUserTaskElement.classType

    private fun isQualityGate(element: Element): Boolean = when (element) {
        is QualityGateInElement, is QualityGateOutElement -> true
        is MatrixStatusElement -> element.originClassType == QualityGateInElement.classType ||
            element.originClassType == QualityGateOutElement.classType
        else -> false
    }

    private fun i18n(messageCode: String, defaultMessage: String = messageCode): String =
        I18nUtil.getCodeLanMessage(messageCode = messageCode, defaultMessage = defaultMessage)
}
