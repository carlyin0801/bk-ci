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

package com.tencent.devops.process.engine.control

import com.tencent.devops.common.api.pojo.ErrorCode
import com.tencent.devops.common.api.pojo.ErrorInfo
import com.tencent.devops.common.client.Client
import com.tencent.devops.common.pipeline.Model
import com.tencent.devops.common.pipeline.enums.BuildEndCategory
import com.tencent.devops.common.pipeline.enums.BuildEndType
import com.tencent.devops.common.pipeline.enums.BuildStatus
import com.tencent.devops.common.pipeline.enums.ManualReviewAction
import com.tencent.devops.common.pipeline.pojo.BuildEndInfo
import com.tencent.devops.common.pipeline.pojo.EndPosition
import com.tencent.devops.common.pipeline.pojo.StagePauseCheck
import com.tencent.devops.common.pipeline.pojo.SubPipelineInfo
import com.tencent.devops.common.pipeline.pojo.element.Element
import com.tencent.devops.common.pipeline.pojo.element.SubPipelineCallElement
import com.tencent.devops.common.pipeline.pojo.element.agent.ManualReviewUserTaskElement
import com.tencent.devops.common.pipeline.pojo.element.quality.QualityGateInElement
import com.tencent.devops.common.pipeline.pojo.element.quality.QualityGateOutElement
import com.tencent.devops.common.quality.pojo.enums.QualityOperation
import com.tencent.devops.process.constant.ProcessMessageCode
import com.tencent.devops.process.engine.common.BS_MANUAL_ACTION_SUGGEST
import com.tencent.devops.process.engine.common.BS_MANUAL_ACTION_USERID
import com.tencent.devops.process.engine.pojo.PipelineBuildStage
import com.tencent.devops.process.engine.pojo.PipelineBuildTask
import com.tencent.devops.quality.api.v2.ServiceQualityInterceptResource
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * 构建终态解析上下文，聚合解析所需的全部输入，避免方法参数过多。
 *
 * [buildTasks] 与 [buildStages] 由调用方在构建结束流程中一次性加载后传入，
 * 避免解析器重复查询构建任务/阶段表。
 */
data class BuildEndContext(
    val projectId: String,
    val pipelineId: String,
    val buildId: String,
    val buildStatus: BuildStatus,
    val model: Model,
    val errorInfoList: List<ErrorInfo>?,
    val buildTasks: Collection<PipelineBuildTask>,
    val buildStages: Collection<PipelineBuildStage>
)

/**
 * 构建终态详情解析器：在构建结束时把失败/超时/成功的原因与位置归纳为 [BuildEndInfo]。
 *
 * 设计要点：
 * 1. 失败位置以 errorInfoList 为准（构建结束时已有的权威聚合结果），不改动其生成逻辑；
 *    人工审核驳回因不写 errorType 而不在该列表中，单独从构建任务补齐。
 * 2. 位置的编码与路径统一走 [EndPositionUtils] 的 Model 索引，与取消场景保持一致口径。
 * 3. 质量红线的指标详情需跨模块调用 quality 服务，仅在确实存在红线失败时调用一次。
 */
@Component
class BuildEndInfoResolver @Autowired constructor(
    private val client: Client
) {

    companion object {
        private val LOG = LoggerFactory.getLogger(BuildEndInfoResolver::class.java)

        private val QUALITY_ATOM_CODES = setOf(
            QualityGateInElement.classType,
            QualityGateOutElement.classType
        )

        /** 单条终态原因的最大长度，避免异常长的插件错误信息撑爆 MODEL_VAR */
        private const val REASON_MAX_LENGTH = 512

        /** 位置列表上限，超大流水线全量失败时防止 MODEL_VAR 过大 */
        private const val POSITION_MAX_SIZE = 50
    }

    /**
     * 解析构建终态详情。返回 null 表示无需落库（取消场景由取消链路自行记录，
     * 普通成功场景在读取时合成，避免每次构建成功都额外写一次库）。
     */
    fun resolve(context: BuildEndContext): BuildEndInfo? {
        val status = context.buildStatus
        return when {
            // CANCELED 可能来自用户取消/父流水线级联，也可能来自Job超时、Agent心跳超时的
            // TERMINATE 链路（见 StartActionTaskContainerCmd 对 QUEUE_CACHE 任务的处理）。
            // 这些入口都已在各自时点落库了更精确的成因，此处按错误信息重新归类反而会误判为执行失败
            status.isCancel() -> null
            status.isSuccess() || status == BuildStatus.STAGE_SUCCESS -> resolveSuccess(context)
            else -> resolveAbnormal(context)
        }
    }

    /**
     * 成功态：仅在存在阶段准入/准出被驳回时才记录，普通成功不落库。
     */
    private fun resolveSuccess(context: BuildEndContext): BuildEndInfo? {
        // 绝大多数成功构建没有阶段驳回，先做一次廉价判断，避免为其遍历整个 Model 建索引
        if (context.buildStages.none { it.hasReviewAbort() }) return null
        val index = EndPositionUtils.buildPositionIndex(context.model)
        val positions = collectStageAbortPositions(context, index)
        if (positions.isEmpty()) return null
        // 驳回意见作为终态原因展示，多个驳回时取第一个
        val suggest = positions.firstNotNullOfOrNull { it.reviewSuggest?.takeIf { s -> s.isNotBlank() } }
        return BuildEndInfo.of(
            endType = BuildEndType.SUCCESS_STAGE_ABORT,
            reason = suggest?.take(REASON_MAX_LENGTH),
            reasonCode = if (suggest.isNullOrBlank()) ProcessMessageCode.BK_BUILD_END_STAGE_REVIEW_ABORT else null
        ).withPositions(positions)
    }

    /**
     * 失败/超时态：归纳每个受影响位置的子类型，再汇总为构建级子类型。
     */
    private fun resolveAbnormal(context: BuildEndContext): BuildEndInfo? {
        // 既无错误信息也无审核驳回时无位置可记录，提前返回避免无谓的 Model 遍历
        val hasReviewAbort = context.buildTasks.any { it.status == BuildStatus.REVIEW_ABORT }
        if (context.errorInfoList.isNullOrEmpty() && !hasReviewAbort) return null

        val index = EndPositionUtils.buildPositionIndex(context.model)
        val positions = collectAbnormalPositions(context, index)
        if (positions.isEmpty()) return null

        val endType = aggregateEndType(positions)
        return buildAbnormalEndInfo(context, endType, positions).withPositions(positions)
    }

    /**
     * 汇总构建级终态子类型：位置子类型唯一时直接采用；
     * 全部为超时类时取第一个具体超时类型；否则视为多类失败。
     */
    private fun aggregateEndType(positions: List<EndPosition>): BuildEndType {
        val distinct = positions.mapNotNull { it.endType }.distinct()
        return when {
            distinct.isEmpty() -> BuildEndType.FAIL_EXEC
            distinct.size == 1 -> distinct.first()
            distinct.all { it.category == BuildEndCategory.TIMEOUT } -> distinct.first()
            else -> BuildEndType.FAIL_MULTIPLE
        }
    }

    /**
     * 按终态子类型组装原因文案。能拿到具体运行时文案（红线指标、驳回意见、插件错误信息）时优先使用，
     * 否则退化为国际化兜底文案。
     */
    private fun buildAbnormalEndInfo(
        context: BuildEndContext,
        endType: BuildEndType,
        positions: List<EndPosition>
    ): BuildEndInfo {
        return when (endType) {
            BuildEndType.FAIL_QUALITY -> buildQualityEndInfo(context)
            BuildEndType.FAIL_REVIEW -> failPreferringReason(
                endType = endType,
                reason = positions.firstNotNullOfOrNull { it.reviewSuggest?.takeIf { s -> s.isNotBlank() } },
                fallbackCode = ProcessMessageCode.BK_BUILD_END_FAIL_REVIEW
            )
            BuildEndType.FAIL_SUB_PIPELINE -> failPreferringReason(
                endType = endType,
                reason = firstErrorMsg(positions),
                fallbackCode = ProcessMessageCode.BK_BUILD_END_FAIL_SUB_PIPELINE
            )
            BuildEndType.FAIL_MULTIPLE -> BuildEndInfo.of(
                endType = endType,
                reasonCode = ProcessMessageCode.BK_BUILD_END_FAIL_MULTIPLE
            )
            BuildEndType.TIMEOUT_STEP -> buildStepTimeoutEndInfo(context, positions)
            // Job超时的时限来自 JobControlOption，构建任务上取不到，
            // 正常路径已由 BuildMonitorControl 携带准确分钟数先行落库，此处兜底不编造数值
            else -> BuildEndInfo.of(endType = endType, reason = firstErrorMsg(positions))
        }
    }

    /**
     * reason 与 reasonCode 互斥：读取侧一旦发现 reasonCode 就会用国际化文案覆盖 reason，
     * 因此拿得到运行时具体文案时只填 reason，拿不到时才退化为国际化兜底词条。
     */
    private fun failPreferringReason(endType: BuildEndType, reason: String?, fallbackCode: String): BuildEndInfo {
        return if (reason.isNullOrBlank()) {
            BuildEndInfo.of(endType = endType, reasonCode = fallbackCode)
        } else {
            BuildEndInfo.of(endType = endType, reason = reason.take(REASON_MAX_LENGTH))
        }
    }

    private fun firstErrorMsg(positions: List<EndPosition>): String? =
        positions.firstNotNullOfOrNull { it.errorMsg?.takeIf { msg -> msg.isNotBlank() } }?.take(REASON_MAX_LENGTH)

    /**
     * 收集失败/超时位置：以 errorInfoList 为主，再补齐不在其中的人工审核驳回位置。
     */
    private fun collectAbnormalPositions(
        context: BuildEndContext,
        index: ModelPositionIndex
    ): List<EndPosition> {
        val positions = mutableListOf<EndPosition>()
        val coveredTaskIds = mutableSetOf<String>()
        val taskMap = context.buildTasks.associateBy { it.taskId }

        context.errorInfoList?.forEach { errorInfo ->
            buildPositionFromError(errorInfo, index, taskMap)?.let { position ->
                positions.add(position)
                position.taskId?.takeIf { it.isNotBlank() }?.let { coveredTaskIds.add(it) }
            }
        }
        // 人工审核驳回的插件不会写 errorType，因此不在 errorInfoList 中，需单独补齐
        positions.addAll(collectReviewAbortPositions(context, index, coveredTaskIds))

        return positions.take(POSITION_MAX_SIZE)
    }

    /**
     * 将一条错误信息映射为位置详情。taskId 为空表示 Job 级或 Stage 级问题。
     * 日志跳转参数优先取构建任务上的值，Model 中的编排数据仅作兜底。
     */
    private fun buildPositionFromError(
        errorInfo: ErrorInfo,
        index: ModelPositionIndex,
        taskMap: Map<String, PipelineBuildTask>
    ): EndPosition? {
        val endType = classifyError(errorInfo)
        val containerLocation = index.locateContainer(errorInfo.containerId)
        // 容器定位不到时退化为 Stage 级位置（如 Stage 级质量红线失败，containerId 为空）
            ?: return buildStageLevelPosition(errorInfo, index, endType)

        val taskId = errorInfo.taskId.takeIf { it.isNotBlank() }
        val buildTask = taskId?.let { taskMap[it] }
        // taskId 存在但在 Model 中找不到对应插件时 element 为空，此时退化为 Job 级位置，保证仍可定位
        val element = taskId?.let { id -> containerLocation.container.elements.firstOrNull { it.id == id } }
        val taskSeq = element?.let { containerLocation.taskSeqOf(taskId) }

        return EndPosition(
            position = taskSeq?.let { "${containerLocation.position}-$it" } ?: containerLocation.position,
            componentPath = element?.let { "${containerLocation.componentPath}/${it.name}" }
                ?: containerLocation.componentPath,
            statusAtEnd = resolveStatusAtEnd(element, buildTask, errorInfo),
            endType = endType,
            stageId = containerLocation.stagePosition.stageId,
            containerId = containerLocation.container.containerId ?: errorInfo.containerId.orEmpty(),
            taskId = taskId.takeIf { element != null },
            matrixFlag = (errorInfo.matrixFlag == true || containerLocation.matrixFlag).takeIf { it },
            errorType = errorInfo.errorType,
            errorCode = errorInfo.errorCode,
            errorMsg = errorInfo.errorMsg.takeIf { it.isNotBlank() },
            containerHashId = buildTask?.containerHashId ?: containerLocation.container.containerHashId,
            stepId = buildTask?.stepId ?: element?.stepId,
            subPipelineInfo = element?.let { resolveSubPipelineInfo(it) }
        )
    }

    /**
     * Stage 级位置：用于 Stage 准入/准出质量红线失败等没有具体 Job 的场景。
     */
    private fun buildStageLevelPosition(
        errorInfo: ErrorInfo,
        index: ModelPositionIndex,
        endType: BuildEndType
    ): EndPosition? {
        val stagePosition = index.locateStage(errorInfo.stageId) ?: return null
        val statusAtEnd = if (endType == BuildEndType.FAIL_QUALITY) {
            BuildStatus.QUALITY_CHECK_FAIL
        } else {
            BuildStatus.FAILED
        }
        return EndPosition(
            position = stagePosition.position,
            componentPath = stagePosition.stageName,
            statusAtEnd = statusAtEnd.name,
            endType = endType,
            stageId = stagePosition.stageId,
            containerId = "",
            errorType = errorInfo.errorType,
            errorCode = errorInfo.errorCode,
            errorMsg = errorInfo.errorMsg.takeIf { it.isNotBlank() }
        )
    }

    /**
     * 补齐人工审核驳回位置：这类插件以 REVIEW_ABORT 结束且不写 errorType，
     * 不会进入 errorInfoList，因此从构建任务中识别，并取出驳回人与驳回意见。
     */
    private fun collectReviewAbortPositions(
        context: BuildEndContext,
        index: ModelPositionIndex,
        coveredTaskIds: Set<String>
    ): List<EndPosition> {
        return context.buildTasks.asSequence()
            .filter { it.status == BuildStatus.REVIEW_ABORT && it.taskId !in coveredTaskIds }
            .mapNotNull { task -> buildReviewAbortPosition(task, index) }
            .toList()
    }

    private fun buildReviewAbortPosition(task: PipelineBuildTask, index: ModelPositionIndex): EndPosition? {
        val containerLocation = index.locateContainer(task.containerId) ?: return null
        val taskSeq = containerLocation.taskSeqOf(task.taskId)
        val element = containerLocation.container.elements.firstOrNull { it.id == task.taskId }
        val suggest = task.taskParams[BS_MANUAL_ACTION_SUGGEST] as? String
            ?: (element as? ManualReviewUserTaskElement)?.suggest
        return EndPosition(
            position = taskSeq?.let { "${containerLocation.position}-$it" } ?: containerLocation.position,
            componentPath = element?.let { "${containerLocation.componentPath}/${it.name}" }
                ?: "${containerLocation.componentPath}/${task.taskName}",
            statusAtEnd = BuildStatus.REVIEW_ABORT.name,
            endType = BuildEndType.FAIL_REVIEW,
            stageId = containerLocation.stagePosition.stageId,
            containerId = containerLocation.container.containerId ?: task.containerId,
            taskId = task.taskId,
            matrixFlag = containerLocation.matrixFlag.takeIf { it },
            operator = task.taskParams[BS_MANUAL_ACTION_USERID] as? String,
            reviewSuggest = suggest?.takeIf { it.isNotBlank() },
            containerHashId = containerLocation.container.containerHashId,
            stepId = element?.stepId
        )
    }

    /**
     * 收集阶段准入/准出被驳回的位置。
     */
    private fun collectStageAbortPositions(
        context: BuildEndContext,
        index: ModelPositionIndex
    ): List<EndPosition> {
        val positions = mutableListOf<EndPosition>()
        context.buildStages.forEach { stage ->
            val stagePosition = index.locateStage(stage.stageId) ?: return@forEach
            stage.abortedChecks().forEach { check ->
                val abortGroup = check.reviewGroups?.lastOrNull { it.status == ManualReviewAction.ABORT.name }
                positions.add(
                    EndPosition(
                        position = stagePosition.position,
                        componentPath = stagePosition.stageName,
                        statusAtEnd = BuildStatus.REVIEW_ABORT.name,
                        endType = BuildEndType.SUCCESS_STAGE_ABORT,
                        stageId = stagePosition.stageId,
                        containerId = "",
                        operator = abortGroup?.operator,
                        reviewSuggest = abortGroup?.suggest?.takeIf { it.isNotBlank() }
                    )
                )
            }
        }
        return positions
    }

    /** 阶段准入/准出中被驳回的审核配置，准入与准出可能同时存在 */
    private fun PipelineBuildStage.abortedChecks(): List<StagePauseCheck> =
        listOfNotNull(checkIn, checkOut).filter { it.status == BuildStatus.REVIEW_ABORT.name }

    private fun PipelineBuildStage.hasReviewAbort(): Boolean = abortedChecks().isNotEmpty()

    /**
     * 判定单条错误信息属于哪种终态子类型。判定顺序由具体到通用，先命中先返回。
     */
    private fun classifyError(errorInfo: ErrorInfo): BuildEndType {
        return when {
            errorInfo.errorCode == ErrorCode.USER_TASK_OUTTIME_LIMIT -> BuildEndType.TIMEOUT_STEP
            errorInfo.errorCode == ErrorCode.USER_JOB_OUTTIME_LIMIT -> BuildEndType.TIMEOUT_JOB
            errorInfo.errorCode == ErrorCode.USER_QUALITY_CHECK_FAIL ||
                errorInfo.atomCode in QUALITY_ATOM_CODES -> BuildEndType.FAIL_QUALITY
            errorInfo.atomCode == ManualReviewUserTaskElement.classType -> BuildEndType.FAIL_REVIEW
            errorInfo.atomCode == SubPipelineCallElement.classType -> BuildEndType.FAIL_SUB_PIPELINE
            else -> BuildEndType.FAIL_EXEC
        }
    }

    /**
     * 终态时的组件状态：优先取构建任务的真实终态，其次取 Model 中的插件状态，
     * 两者都不可用时按错误码推断，避免出现空值。
     */
    private fun resolveStatusAtEnd(element: Element?, buildTask: PipelineBuildTask?, errorInfo: ErrorInfo): String {
        buildTask?.status?.takeIf { it.isFinish() }?.let { return it.name }
        element?.status?.let { BuildStatus.parse(it) }?.takeIf { it.isFinish() }?.let { return it.name }
        return if (errorInfo.errorCode == ErrorCode.USER_TASK_OUTTIME_LIMIT) {
            BuildStatus.EXEC_TIMEOUT.name
        } else {
            BuildStatus.FAILED.name
        }
    }

    private fun resolveSubPipelineInfo(element: Element): SubPipelineInfo? {
        val subInfo = element.subPipelineBuildInfo ?: return null
        return SubPipelineInfo(
            projectId = subInfo.projectId,
            pipelineId = subInfo.pipelineId,
            buildId = subInfo.buildId
        )
    }

    /**
     * 步骤超时：时限取自超时插件自身的 additionalOptions.timeout（分钟）。
     * 取不到或配置为0（表示不限制）时退化为插件错误信息，避免展示出空的时限。
     */
    private fun buildStepTimeoutEndInfo(context: BuildEndContext, positions: List<EndPosition>): BuildEndInfo {
        val taskId = positions.firstOrNull { it.endType == BuildEndType.TIMEOUT_STEP }?.taskId
        val minutes = taskId
            ?.let { id -> context.buildTasks.firstOrNull { it.taskId == id } }
            ?.additionalOptions?.timeout
            ?.takeIf { it > 0 }
        return if (minutes != null) {
            BuildEndInfo.of(
                endType = BuildEndType.TIMEOUT_STEP,
                reasonCode = ProcessMessageCode.BK_BUILD_END_TIMEOUT_STEP,
                reasonParams = listOf(minutes.toString())
            )
        } else {
            BuildEndInfo.of(
                endType = BuildEndType.TIMEOUT_STEP,
                reason = firstErrorMsg(positions)
            )
        }
    }

    /**
     * 组装质量红线终态信息。指标详情存放在 quality 模块，需跨服务查询，
     * 因此仅在确认为红线失败时调用一次，结果随 BuildEndInfo 一并落库，读取详情时不再跨模块。
     */
    private fun buildQualityEndInfo(context: BuildEndContext): BuildEndInfo {
        val failedRecords = try {
            client.get(ServiceQualityInterceptResource::class).listHistory(
                projectId = context.projectId,
                pipelineId = context.pipelineId,
                buildId = context.buildId
            ).data.orEmpty()
                .flatMap { it.resultMsg }
                .filter { !it.pass }
        } catch (ignored: Exception) {
            // 质量服务不可用不应影响构建结束流程，退化为无指标详情
            LOG.warn("ENGINE|${context.buildId}|BUILD_END_INFO|fetch quality intercept failed", ignored)
            emptyList()
        }

        return when (failedRecords.size) {
            0 -> BuildEndInfo.of(
                endType = BuildEndType.FAIL_QUALITY,
                reasonCode = ProcessMessageCode.BK_BUILD_END_FAIL_QUALITY
            )
            1 -> failedRecords.first().let { record ->
                BuildEndInfo.of(
                    endType = BuildEndType.FAIL_QUALITY,
                    reasonCode = ProcessMessageCode.BK_BUILD_END_FAIL_QUALITY_INDICATOR,
                    reasonParams = listOf(
                        record.indicatorName,
                        record.actualValue.orEmpty(),
                        "${QualityOperation.convertToSymbol(record.operation)}${record.value.orEmpty()}"
                    )
                )
            }
            else -> BuildEndInfo.of(
                endType = BuildEndType.FAIL_QUALITY,
                reasonCode = ProcessMessageCode.BK_BUILD_END_FAIL_QUALITY_INDICATORS,
                reasonParams = listOf(
                    failedRecords.first().indicatorName,
                    failedRecords.size.toString()
                )
            )
        }
    }
}
