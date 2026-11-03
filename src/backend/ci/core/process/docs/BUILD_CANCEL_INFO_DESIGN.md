# 流水线构建终态详情（BuildEndInfo）技术方案

## 一、需求背景

当前流水线构建取消（`BuildStatus.CANCELED`）时，仅记录了取消人（`cancelUser`），缺少取消类型、取消原因等关键信息。
用户在查看流水线执行详情时，无法区分取消场景（用户主动取消/系统自动取消/父流水线级联取消等），也无法了解具体的取消原因。

### 1.1 架构升级：从 CancelInfo 到 BuildEndInfo

取消、失败、超时、成功四类终态场景的数据结构高度同构（均需要：类型、原因、操作人、影响位置），因此采用统一的 `BuildEndInfo`（构建终态详情）替代原有的 `BuildCancelInfo`。

当前已覆盖的终态大类（`BuildEndCategory`）：

| 大类 | 子类型 | 落库时机 |
|------|--------|----------|
| CANCEL | CANCEL_USER / CANCEL_SYSTEM / CANCEL_PARENT_PIPELINE | 取消事件发生时 |
| FAIL | FAIL_EXEC / FAIL_QUALITY / FAIL_REVIEW / FAIL_SUB_PIPELINE / FAIL_FAST_KILL / FAIL_MULTIPLE | 构建结束时 |
| TIMEOUT | TIMEOUT_QUEUE（构建级）/ TIMEOUT_JOB、TIMEOUT_STEP（仅位置级） | 超时判定时（兜底：构建结束时） |
| SUCCESS | SUCCESS / SUCCESS_STAGE_ABORT / SUCCESS_STAGE_REVIEWING | 读取时合成 / 构建结束时 |

**运行态（排队中、执行中）不属于本方案范围**：排队位置、前方排队列表、待人工处理项等数据随时间实时变化，
无法在构建结束时落库，需要在读取详情时实时计算，属于独立机制，不复用 `BuildEndInfo`。

## 二、取消场景全面分析

### 2.1 取消状态产生的所有场景

经过对代码全面分析，`BuildStatus.CANCELED` 产生的场景可归纳为以下三大类：

#### 第一类：用户主动取消（CANCEL_USER）

| 场景 | 入口 | 操作人 | 说明 |
|------|------|--------|------|
| 手动取消构建 | `PipelineBuildFacadeService.buildManualShutdown()` | 操作用户 | 用户在页面点击取消按钮 |
| 强制终止构建 | `PipelineBuildFacadeService.buildManualShutdown(terminateFlag=true)` | 操作用户 | 用户二次取消，强制终止 |
| API/Open接口取消 | `PipelineBuildFacadeService.buildManualShutdown()` | API调用用户 | 通过OpenAPI取消构建 |
| 暂停插件取消 | `PipelineTaskPauseListener` | 操作用户 | 用户取消暂停状态的插件 |

#### 第二类：系统自动取消（CANCEL_SYSTEM）

| 场景 | 入口 | 说明 |
|------|------|------|
| 排队满取消 | `QueueInterceptor` | 队列已满，自动取消排队中的构建 |
| 并发组互斥取消 | `QueueInterceptor` | 并发组策略取消旧的排队构建 |
| 并发组优先级取消 | `PipelineRuntimeService.concurrencyCancelBuildPipeline()` | 更高优先级构建等待执行 |
| Agent心跳超时 | `HeartbeatControl.timeout()` | 构建机心跳超时(10分钟)，终止容器 |
| Job执行超时 | `BuildMonitorControl` | Job运行时间超过设定上限 |
| Model不存在 | `BuildStartControl.buildModel()` | 构建启动时Model不存在，取消构建 |
| 服务端关闭 | `PipelineBuildFacadeService.serviceShutdown()` | 服务端发起的关闭构建 |
| 流水线被删除 | `PipelineRuntimeService.cancelPendingTask()` | 流水线删除时终止运行中的构建 |

构建排队超时（`BuildMonitorControl.monitorQueueBuild()`）不在此列：它的构建最终状态是
`QUEUE_TIMEOUT`（页面标签「排队超时」），按同类原则归 `TIMEOUT_QUEUE` 而非系统取消。

#### 第三类：父流水线级联取消（CANCEL_PARENT_PIPELINE）

| 场景 | 入口 | 说明 |
|------|------|------|
| 父流水线取消级联 | `SubPipelineCallAtom.tryFinish(force=true)` | 父流水线被取消/终止时，级联取消子流水线 |
| 父流水线任务终止 | `PipelineBuildTaskService.terminateSubPipeline()` | 父流水线任务取消时终止子流水线 |

### 2.2 失败场景分析

BK-CI 没有现成的「失败大类」枚举，需要按 `errorCode` + `atomCode` 组合判定。判定顺序由具体到通用，先命中先返回：

| 子类型 | 判定条件 | 原因文案来源 |
|--------|----------|-------------|
| TIMEOUT_STEP | `errorCode == USER_TASK_OUTTIME_LIMIT(2199006)` | 插件 `additionalOptions.timeout` 分钟数（位置级） |
| TIMEOUT_JOB | `errorCode == USER_JOB_OUTTIME_LIMIT(2199005)` | 所属容器 `JobControlOption.timeout` 分钟数（位置级） |
| FAIL_FAST_KILL | `errorCode == USER_STAGE_FASTKILL_TERMINATE(2199010)` | 同阶段引发终止的 Job 名称（位置级），定位不到时无原因 |
| FAIL_QUALITY | `errorCode == USER_QUALITY_CHECK_FAIL(2199007)` 或 `atomCode ∈ {qualityGateInTask, qualityGateOutTask}` | quality 模块拦截指标详情（位置级） |
| FAIL_REVIEW | `atomCode == manualReviewUserTask`，或任务状态为 `REVIEW_ABORT` | 驳回意见 `_bsManualActionSuggest_`（位置级） |
| FAIL_SUB_PIPELINE | `atomCode ∈ {subPipelineCall, SubPipelineExec}` | 子流水线名称与构建号（位置级） |
| FAIL_EXEC | 以上均不命中 | 插件 `errorMsg`（位置级） |
| FAIL_MULTIPLE | 同一次构建的位置中出现多种上述子类型 | 无构建级原因，逐位置展示 |

上表是**位置级**判定。汇总到构建级时两类子类型会变形：`TIMEOUT_*` 按最终状态降级为 `FAIL_EXEC`
（见 4.3.11），`FAIL_FAST_KILL` 作为连带影响不参与汇总（见 2.2.0.1）。

#### 2.2.0 子流水线插件的两种形态

子流水线调用存在内置插件（`SubPipelineCallElement.classType = subPipelineCall`）与市场插件
（`SubPipelineExec`）两种形态，页面上都显示为「子流水线调用」，失败时都应归类为 `FAIL_SUB_PIPELINE`。
不能按错误码判定：市场插件失败走的是 `USER_INPUT_INVAILD(2199002)` 这类通用错误码，其他插件同样会使用。

#### 2.2.0.1 FastKill 是连带影响而非独立成因

开启 FastKill 的阶段中，某个 Job 失败会让同阶段其他 Job 被提前终止，被终止的位置带
`USER_STAGE_FASTKILL_TERMINATE(2199010)` 错误码。
这类位置不是独立的失败原因，因此在汇总构建级子类型时先剔除，
否则「一个插件失败 + 若干个被 FastKill 终止」会被误判为多类失败。

产品口径是**因 FastKill 结束的位置一律标记「因失败即停被终止」**，所以位置级不再展示插件自身的
错误信息（被强制终止时那只是引擎填的占位文案），只展示引发终止的 Job 名；连 Job 名都定位不到时
（如引发终止的是 Stage 级质量红线，错误信息里没有容器）不给原因，页面只留类型标签。

#### 2.2.0.2 引擎侧插件的终止成因原先会丢

只有构建机任务（`taskAtom` 为空、在 Agent 上跑的插件）会按终止事件的错误码落库，
`TaskControl` 里那段分支直接用 `event.errorCode / errorTypeName / reason` 组装 `AtomResponse`。
引擎侧插件（人工审核、开关机、子流水线调用、质量红线等）走的是
`TaskAtomService.tryFinish()` → `IAtomTask.tryFinish()`，被强制终止时插件给不出结果，
引擎只会填 `PLUGIN_DEFAULT_ERROR(2199001)` 加「Force Terminate!」「not definded error」这类占位文案，
**终止成因在这里就丢了**。于是终态详情只能按 `atomCode` 归类，出现两处误判：

| 现象 | 日志 | 误判为 |
|------|------|--------|
| 等待审核中的人工审核插件被 FastKill 终止 | `Terminate Plugin[人工审核]: FastKill` / `Task [人工审核] FAILED (Force Terminate!)!` | 人工审核驳回（命中 `atomCode == manualReviewUserTask`） |
| 卡在 Set up job 的开机任务被 FastKill 终止 | `Terminate Plugin[Prepare_Job#1]: FastKill` / `Task [Prepare_Job#1] FAILED (not definded error)!` | 执行失败（兜底的 `FAIL_EXEC`） |

**处理方式**：把终止成因一路传到引擎侧插件，与构建机任务同一口径——`TaskControl` 从事件里取出
终止成因（仅 `FastKillUtils.isTerminateCode()` 认的被动终止链路才带错误码：FastKill、Job执行超时、
Agent失联；用户取消这类事件不带），`TaskAtomService.tryFinish()` 在插件返回结果后覆盖占位错误。
两条约束保证不会盖错：

1. **只覆盖占位错误**：插件给出了自己的错误码（`errorCode` 非空且不是 `PLUGIN_DEFAULT_ERROR`）时保持原样。
   子流水线真失败、步骤自身超时、质量红线已判定这些真实成因不会被 FastKill 盖掉。
2. **只处理运行中被终止的任务**：`TaskControl` 开头就会拦掉已结束的任务
   （`buildTask.status.isFinish()` 直接 return），所以能走到这里的必然是「被终止」而非「自己失败」的。

顺带修正的还有：Job 执行超时终止的引擎侧插件从 `FAIL_EXEC` 归位到 `TIMEOUT_JOB`（带上时限文案），
被终止的质量红线插件不再按 `atomCode` 误判为「质量红线未达标」（它还没判定就被终止了）。
只能复制不能就地改 `AtomResponse`：强制终止返回的 `defaultFailAtomResponse` 是全局共享实例。

#### 2.2.1 人工审核驳回不在 errorInfoList 中

`ManualReviewTaskAtom.tryFinish()` 的 ABORT 分支返回 `AtomResponse(BuildStatus.REVIEW_ABORT)` 时**不设置 errorType**，
而 `BuildEndControl.fixBuildInfo()` 是按 `task.errorType != null` 过滤生成 `errorInfoList` 的，
因此人工审核驳回不会出现在 `errorInfoList` 里。

**处理方式**：不修改 `ManualReviewTaskAtom`（改动会影响 `errorInfoList` 的既有语义，进而影响通知、度量等下游消费方），
而是在解析器中额外扫描状态为 `REVIEW_ABORT` 的构建任务补齐位置，并从 `taskParams` 中取出驳回人与驳回意见。

#### 2.2.2 暂停插件上取消后构建可能以失败收尾

用户在暂停插件（`PAUSE`）上取消构建时，取消链路会立刻写入 `CANCEL_USER`，位置 `statusAtEnd` 拍的是当时的 `PAUSE`。
随后引擎继续收尾：暂停插件常被置为 `FAILED`（日志仍可能是 `Cancelled by xxx`），同 Job 里尚未执行的人工审核
可能走到 `REVIEW_ABORT`。`REVIEW_ABORT` 属于失败态，构建最终状态因此是失败，不是取消。

若构建结束时仍用 `saveBuildEndInfoIfAbsent` 保留这份取消记录，页面就会出现「状态：失败 / 卡片：用户取消 /
位置：暂停执行」，与插件详情接口里的 `FAILED`、`REVIEW_ABORT` 也不一致。

**处理方式**：

| 时机 | 行为 |
|------|------|
| 写入 | `BuildEndControl` 改为 `saveBuildEndInfoIfCompatible`：已有详情且大类与最终状态同类才保留；冲突则覆盖为解析器结果。解析器无需落库时（取消/普通成功）调用 `clearBuildEndInfoIfIncompatible` 清掉错配残留。 |
| 读取 | `BuildEndInfo.alignedTo(status)`：落库大类与 `ModelRecord.status` 不同类时改写成同类卡片。失败类用 `BuildEndPositionCollector` 按模型插件当前终态重算位置（`FAILED` → `FAIL_EXEC`，`REVIEW_ABORT` → `FAIL_REVIEW`），开机/关机控制插件跳过。 |
| 解析器补齐 | 暂停插件被取消后常是 `FAILED` 且不写 `errorType`，不在 `errorInfoList` 中。`BuildEndInfoResolver` 在审核驳回补齐之后，再按模型终态补齐这类用户插件；FastKill 阶段仍走 Job 级补齐，避免把连带终止误标成独立执行失败。 |

心跳超时、Job 执行超时仍用 `saveBuildEndInfoIfAbsent()` 在运行中落库，避免覆盖用户主动取消。

读取侧大类已经对上时仍会刷新位置 `statusAtEnd`（Job 超时取消卡片里插件可能从 `PAUSE` 落到 `CANCELED`），
并为没有原因的 `FAIL_EXEC` 补「暂停执行期间被终止 / 步骤执行失败」。

#### 2.2.3 执行前暂停点终止、互斥组抢锁失败可能漏写

这两条路径都不走用户取消入口，也常常不写插件 `errorType`：

- 执行前暂停后点终止：`PipelineTaskPauseListener` 把插件置为 `CANCELED`，构建以取消收尾。现改为 `saveBuildEndInfoIfAbsent` 落下 `CANCEL_USER`；读取侧按模型补齐仍停在取消/暂停的插件位置。
- Job 互斥组抢锁失败：容器失败或取消、任务从未启动。解析器与读取合成都会给出 Job 级 `FAIL_EXEC` 及互斥原因，保证失败卡片能打开。

### 2.3 超时场景分析

| 子类型 | 判定位置 | 时限配置 | 适用层级 |
|--------|----------|----------|----------|
| TIMEOUT_QUEUE | `BuildMonitorControl.monitorQueueBuild()` | `PipelineSetting.waitQueueTimeMinute` | 构建级（构建状态确定为 `QUEUE_TIMEOUT`） |
| TIMEOUT_JOB | `BuildMonitorControl.checkNextContainerMonitorIntervals()` | `JobControlOption.timeout` | 仅位置级（位置状态为超时） |
| TIMEOUT_STEP | Worker `TaskDaemon` / Engine `IAtomTask.tryFinish()` | `ElementAdditionalOptions.timeout` | 仅位置级（位置状态为 `EXEC_TIMEOUT`） |

**归类铁律：终态子类型的大类必须与同一层级的状态同类**——构建级 `endType` 跟随构建最终状态，
位置级 `endType` 跟随该位置的 `statusAtEnd`。「超时」这类成因在构建级放不下时由 `reason` 表达，
不允许出现「状态是已取消、类型是 Job 超时」这种自相矛盾的组合。

#### 2.3.0 Agent 失联归类为系统取消而非超时

`HeartbeatControl.timeout()` 处理的是构建机 Agent 心跳中断（默认 10 分钟无心跳）。
虽然内部判定依据是「超时」，但对用户而言这是构建被系统中断，与「配置的时限到了」这类超时语义不同：
用户没有配置过心跳时限，也无法通过调大时限来规避。因此归类为 `CANCEL_SYSTEM`，
原因文案为「构建机Agent异常退出，与节点失联」，与设计稿一致。
（原 `TIMEOUT_HEARTBEAT` 子类型已下线。）

#### 2.3.1 超时的构建最终状态并不固定（重要）

只有**排队超时**的构建状态是确定的：`BuildMonitorControl.monitorQueueBuild()` 直接派发
`PipelineBuildFinishEvent(status = QUEUE_TIMEOUT)`。

**Job执行超时与Agent心跳超时的构建最终状态不确定**。二者都是派发
`PipelineBuildContainerEvent(actionType = ActionType.TERMINATE)`，最终状态可能是
`CANCELED` / `TERMINATE` / `FAILED` 中的任意一种，取决于流水线形态：

| 落成 CANCELED 的路径 | 触发条件 |
|---------------------|----------|
| `StartActionTaskContainerCmd` 中 `needTerminate` 分支 | 有任务卡在 `QUEUE_CACHE`（领取过程中被中断）→ 任务置为 `CANCELED`，经 `jobStatusMaker.forceFinish` 后容器也为 `CANCELED` |
| `StartContainerStageCmd` 中 `newActionType.isEnd()` | `isEnd()` 包含 `TERMINATE`，尚未启动的后续 Stage 直接置为 `CANCELED` |
| `UpdateStateForStageCmdFinally.finishBuild()` | 存在 finally Stage 且上游 Stage 为取消态时，构建继承 `CANCELED` |
| `CheckInterruptStageCmd.detectStageInterruptStatus()` | fastKill 且无失败 Job、但有取消态容器时返回 `CANCELED` |

决定性佐证是 `BuildStatusSwitcher.PipelineBuildStatusMaker` 的构建级合法状态集里
**没有** `EXEC_TIMEOUT` 和 `HEARTBEAT_TIMEOUT`，只有 `QUEUE_TIMEOUT`——
说明这两类超时本就不会以「超时」状态收尾，落到构建级只能是取消/终止/失败。

**因此构建级不能把它们归入 `TIMEOUT_*`**：一旦归成超时类，页面就会出现状态标签「已取消」
配详情卡片「执行超时」的自相矛盾组合。正确做法是让构建级子类型与最终状态同类
（`HeartbeatControl` 与 Job 执行超时都写 `CANCEL_SYSTEM`），把「超时」这个成因放进 `reason`：
「所属 Job 超过执行时限 15m，步骤被终止」「构建机 Agent 异常退出，与节点失联」。

成因并没有因此丢失：位置列表里该 Job/步骤的 `statusAtEnd` 就是超时状态，位置级 `endType`
仍是 `TIMEOUT_JOB`/`TIMEOUT_STEP`，与位置自身状态同类，用户在位置行上能看到准确成因。

### 2.4 成功场景分析

| 子类型 | 判定条件 | 说明 |
|--------|----------|------|
| SUCCESS | 构建状态为成功且无阶段驳回 | **不落库**，读取时按状态合成 |
| SUCCESS_STAGE_ABORT | `stage.checkIn/checkOut.status == REVIEW_ABORT` | 阶段准入被驳回，后续阶段不再执行，构建整体按成功结束 |
| SUCCESS_STAGE_REVIEWING | 构建状态为 `STAGE_SUCCESS` 且某阶段准入/准出 `status == REVIEWING` | **不落库**，读取时按阶段审核状态合成；构建并未结束，审核通过后会继续运行 |

普通成功不落库是刻意设计：成功是最高频的终态，若每次构建成功都额外写一次 `T_PIPELINE_BUILD_RECORD_MODEL`，
会给引擎增加一次无谓的事务写入。而成功态需要展示的总耗时本就在读取时计算，因此读取时合成即可，前端拿到的结构完全一致。

#### 2.4.1 `STAGE_SUCCESS` 同时承载了两种语义

构建状态 `STAGE_SUCCESS`（页面标签「阶段成功」）既表示「阶段准入被驳回、构建按阶段成功收尾」，
也表示「阶段准入正在等待人工审核、构建挂起」。后者构建并未真正结束，此时不存在结束时点，
写入侧无从落库，因此在读取详情时按阶段的审核状态还原：
命中 `REVIEWING` 的审核组时合成 `SUCCESS_STAGE_REVIEWING`，携带审核组序号、组名、待审核人，
以及从 `stage.startEpoch`（阶段暂停进入审核的时刻）算出的已等待时长 `waitCostTime`。
两者都不命中时才退化为普通 `SUCCESS`。

审核位置的编码从 1 开始（详情页不展示触发器阶段，与失败位置的口径一致）。

## 三、设计方案

### 3.1 设计原则

1. **不新增数据库表**：复用现有 `T_PIPELINE_BUILD_RECORD_MODEL` 表的 `MODEL_VAR`（JSON字段）存储终态详情
2. **不新增接口**：复用 `getBuildRecordByExecuteCount` 接口，在 `ModelRecord` 返回体中增加 `buildEndInfo` 字段
3. **向后兼容**：终态详情为可选字段，不影响历史数据展示
4. **统一建模**：`BuildEndInfo` 覆盖取消/失败/超时所有终态类型，通过 `BuildEndType` 区分子类型
5. **扩展性好**：新增终态类型只需扩展 `BuildEndType` 枚举并添加对应工厂方法

### 3.2 数据模型设计

#### 3.2.1 终态子类型枚举 `BuildEndType`

```kotlin
package com.tencent.devops.common.pipeline.enums

enum class BuildEndType(
    @BkFieldI18n(translateType = I18nTranslateTypeEnum.VALUE,
        keyPrefixName = "buildEndType", reusePrefixFlag = false)
    val displayName: String,
    val category: BuildEndCategory
) {
    // ---- 取消类 ----
    CANCEL_USER("cancelUser", CANCEL),
    CANCEL_SYSTEM("cancelSystem", CANCEL),
    CANCEL_PARENT_PIPELINE("cancelParentPipeline", CANCEL),

    // ---- 失败类 ----
    FAIL_EXEC("failExec", FAIL),                  // 插件执行失败等常规失败
    FAIL_QUALITY("failQuality", FAIL),            // 质量红线拦截未达标
    FAIL_REVIEW("failReview", FAIL),              // 人工审核驳回
    FAIL_SUB_PIPELINE("failSubPipeline", FAIL),   // 子流水线执行失败
    FAIL_FAST_KILL("failFastKill", FAIL),         // 同阶段其他Job失败，本位置被FastKill提前终止
    FAIL_MULTIPLE("failMultiple", FAIL),          // 同时存在多种失败子类型

    // ---- 超时类 ----
    TIMEOUT_JOB("timeoutJob", TIMEOUT),
    TIMEOUT_STEP("timeoutStep", TIMEOUT),
    TIMEOUT_QUEUE("timeoutQueue", TIMEOUT),

    // ---- 成功类 ----
    SUCCESS("success", SUCCESS),
    SUCCESS_STAGE_ABORT("successStageAbort", SUCCESS),      // 阶段准入驳回，构建按成功结束
    SUCCESS_STAGE_REVIEWING("successStageReviewing", SUCCESS); // 阶段准入等待审核，构建挂起
}

enum class BuildEndCategory { CANCEL, FAIL, TIMEOUT, SUCCESS }
```

命名规范: `{大类}_{子类型}`，如 `CANCEL_USER`、`FAIL_EXEC`、`TIMEOUT_JOB`。

`category` 是子类型的**成因归类**（如 FastKill 提前终止天然属于失败类），下发给前端的
`BuildEndInfo.endCategory` 直接取它，因此 `endCategory` 与 `endType` 恒定同类。
新增子类型只需在枚举里指定所属大类，前端无需改动。

#### 3.2.2 终态详情数据类 `BuildEndInfo`

```kotlin
package com.tencent.devops.common.pipeline.pojo

data class BuildEndInfo(
    val endType: BuildEndType,                  // 终态子类型
    var endCategory: BuildEndCategory? = null,  // 终态大类(读取时按endType填充)
    val operator: String? = null,               // 操作人(仅用户主动操作时有值)
    var reason: String? = null,                 // 终态原因(国际化后文案，读取时填充；失败类为null)
    var endTypeDesc: String? = null,            // 终态子类型描述(国际化，读取时填充)
    var reasonCode: String? = null,             // 国际化messageCode
    var reasonParams: List<String>? = null,     // 国际化占位符参数
    val endTime: Long? = null,                  // 终态时间戳(毫秒)
    var totalCostTime: Long? = null,            // 构建运行总时长(毫秒，读取时计算)
    var waitCostTime: Long? = null,             // 已等待时长(毫秒，仅阶段准入审核中场景，读取时计算)
    var positions: List<EndPosition>? = null,   // 被影响的组件位置列表
    var positionCount: Int = 0,                 // 被影响位置总数
    val parentPipelineInfo: ParentPipelineInfo? = null  // 父流水线信息(级联终止时)
) {
    companion object {
        const val MODEL_VAR_KEY = "buildEndInfo"

        fun ofCancelUser(operator, reasonCode, ...) // 用户主动取消
        fun ofCancelSystem(reasonCode, ...)          // 系统自动取消
        fun ofCancelParentPipeline(reasonCode, parentPipelineInfo, ...) // 父流水线级联取消
        // 失败/超时/成功等所有非取消场景共用，语义完全由 endType 表达
        fun of(endType, reason, reasonCode, reasonParams)
    }

    // 统一设置位置列表，保证 positions 与 positionCount 不会不一致
    fun withPositions(endPositions: List<EndPosition>): BuildEndInfo

    // 位置收集完成后才能确定占位符参数时，用于补齐/覆盖原因国际化标识
    fun withReason(reasonCode: String, reasonParams: List<String>? = null): BuildEndInfo
}
```

`withPositions()` 是为了消除一类历史隐患：`positions` 和 `positionCount` 分别赋值时容易漏改其一。
所有写入方统一走该方法，空列表时 `positions` 置空、`positionCount` 归零，前端不会渲染出空的位置区块。

`withReason()` 服务于「文案依赖位置数」的场景：用户取消的原因文案需要带出被终止的在途位置数
（设计稿「3 处在途均已停止」），而在途位置要等实际终止动作完成后才收集齐，
无法在创建 `BuildEndInfo` 时确定，因此 `reasonCode`/`reasonParams` 定义为 `var` 并由该方法补齐。

**为什么非取消场景只有一个 `of()`，而不按大类拆成 `ofFail` / `ofTimeout` / `ofSuccess`**：
写入时点只知道**结束成因**（由 `endType` 表达），并不知道构建最终会以什么状态收尾（见 2.3.1）。
带大类语义的方法名会让调用方误以为终态归类已经确定——`HeartbeatControl` 里写 `ofTimeout(...)`，
读起来像是断言「这次构建会以超时结束」，而它实际上很可能以 `CANCELED` 结束。
且这三个方法的实现本就完全相同，保留只会制造「该用哪个」的无谓选择。
取消类的三个工厂方法予以保留，因为它们各自固定了 `endType` 并携带专属字段
（`operator`、`parentPipelineInfo`），是真正的语义差异。

#### 3.2.2.1 父流水线信息 `ParentPipelineInfo`

```kotlin
data class ParentPipelineInfo(
    val projectId: String? = null,     // 父流水线所属项目ID
    val pipelineId: String,            // 父流水线ID
    val pipelineName: String? = null,  // 父流水线名称
    val buildId: String,               // 父构建ID
    val buildNum: Int? = null,         // 父构建号
    val operator: String? = null       // 父流水线取消操作人
)
```

`projectId` 为父流水线所属项目。子流水线插件支持跨项目调用，父子构建不一定同属一个项目，
前端拼接父构建详情页链接时必须使用该字段而非当前页面的项目ID。

该字段定义为可空并带默认值，是为了兼容本次改动前已落库的 `parentPipelineInfo` JSON：
读取侧使用 `JsonUtil.anyToOrNull()`，反序列化失败只会静默返回 `null`，
若定义为必填参数会导致历史记录的整个 `buildEndInfo` 无法解析、终止详情面板整体消失。

`operator` 取自父构建记录中已落库的取消人（`getBuildCancelUser`）。父构建在
`PipelineRuntimeService.cancelBuild()` 中会先同步执行 `updateBuildCancelUser()` 再下发取消事件，
因此子流水线侧读取时该值必然已持久化。若父构建并非由用户取消（如失败级联终止），该字段为 `null`。

#### 3.2.3 位置详情 `EndPosition`

```kotlin
data class EndPosition(
    val position: String,              // 位置编码：3-1-1(task级)、3-2(job级)、3(stage级)
    val componentPath: String,         // 组件路径：Stage名/Job名[/Task名]
    val statusAtEnd: String,           // 终态时组件的 BuildStatus 枚举值
    var statusAtEndDesc: String?,      // 状态描述(国际化，读取时填充)
    val endType: BuildEndType? = null, // 该位置的终态子类型(多类失败时区分每条位置)
    var endTypeDesc: String? = null,   // 子类型描述(国际化，读取时填充)
    var reason: String? = null,        // 该位置的终态原因(驳回意见/红线指标/子流水线名等)
    val reasonCode: String? = null,    // 该位置原因的国际化messageCode
    val reasonParams: List<String>? = null, // 该位置原因的国际化占位符参数
    val stageId: String,               // 锚点定位：阶段ID
    val containerId: String,           // 锚点定位：容器ID
    val taskId: String? = null,        // 锚点定位：插件ID(task级别)
    val matrixFlag: Boolean? = null,   // 是否构建矩阵
    val errorType: Int? = null,        // 错误类型(ErrorType.num，前端据此展示图标)
    val errorCode: Int? = null,        // 错误码
    val errorMsg: String? = null,      // 错误信息
    val operator: String? = null,      // 操作人(人工审核驳回等场景)
    val reviewSuggest: String? = null, // 审核意见(驳回原因)
    val reviewGroupSeq: Int? = null,   // 审核组序号(阶段准入审核位置，从1开始)
    val reviewGroupName: String? = null, // 审核组名称(阶段准入审核位置)
    val reviewers: List<String>? = null, // 待审核人列表(阶段准入审核中位置)
    val containerHashId: String? = null, // 日志跳转参数
    val stepId: String? = null,          // 日志跳转参数
    val subPipelineInfo: SubPipelineInfo? = null,    // 子流水线失败时的跳转信息
    val dependOnJobs: List<DependOnJobInfo>? = null  // 依赖Job导航信息
)

data class SubPipelineInfo(
    val projectId: String,             // 子流水线所属项目ID(可能与父流水线跨项目)
    val pipelineId: String,
    val pipelineName: String? = null,
    val buildId: String,
    val buildNum: Int? = null
)

data class DependOnJobInfo(
    val jobName: String,               // 依赖的Job名称
    val projectId: String,             // 项目ID
    val pipelineId: String,            // 流水线ID
    val buildId: String,               // 构建ID
    val executeCount: Int,             // 执行次数
    val stageId: String,               // 依赖Job所在阶段ID(锚点定位)
    val containerId: String,           // 依赖Job的容器ID(锚点定位)
    val matrixFlag: Boolean? = null    // 依赖Job是否为构建矩阵
)
```

#### 3.2.4 为什么位置级也需要 endType

「多类失败」卡片要求同一次构建的每条失败位置各自展示不同标签（错误码、质量红线未达标、子流水线失败等），
因此子类型必须下沉到位置级。构建级 `BuildEndInfo.endType` 是位置级子类型的汇总结果：

- 所有位置子类型一致 → 直接采用该子类型
- 全部属于超时大类 → 取第一个具体超时类型
- 否则 → `FAIL_MULTIPLE`，前端据此渲染「多类失败」卡片并按位置分别展示标签

#### 3.2.5 定位日志所需参数

前端「定位日志」需要 `buildId + taskId + containerHashId + executeCount + stepId` 五个参数。
其中 `executeCount` 是构建级的，`ModelRecord.executeCount` 已有，不在位置级重复存储；
其余由 `EndPosition` 提供。`containerHashId` 与 `stepId` 优先取自构建任务记录，
Model 中的编排数据仅作兜底——运行时任务上的值才是日志实际写入时使用的值。

### 3.3 存储方案

利用 `T_PIPELINE_BUILD_RECORD_MODEL.MODEL_VAR` JSON字段，以 `buildEndInfo` 作为 key 存储 `BuildEndInfo` 序列化后的JSON。

**优势**：
- 不需要DDL变更
- `MODEL_VAR` 本身就是可扩展的JSON Map，已有 `timeCost` 等信息
- 读取时直接从 `modelVar` 中反序列化即可

### 3.4 接口返回体变更

在 `ModelRecord` 中新增可选字段：

```kotlin
@get:Schema(title = "构建终态详情(取消/失败/超时等)", required = false)
val buildEndInfo: BuildEndInfo? = null
```

### 3.5 终态信息写入时机

在各场景的入口处构造 `BuildEndInfo`，通过 `PipelineBuildCancelEvent` 或 `saveBuildEndInfo` 写入 `MODEL_VAR`。

## 四、详细实现

### 4.1 文件清单

| 文件 | 模块 | 说明 |
|------|------|------|
| `BuildEndType.kt` | common-pipeline | 终态子类型枚举 |
| `BuildEndInfo.kt` | common-pipeline | 终态详情数据类 + `ParentPipelineInfo` |
| `EndPosition.kt` | common-pipeline | 位置详情 + `DependOnJobInfo` |

### 4.2 修改文件清单

| 文件 | 修改内容 |
|------|----------|
| `PipelineBuildCancelEvent.kt` | `cancelInfo` → `buildEndInfo: BuildEndInfo?` |
| `ModelRecord.kt` | `cancelInfo` → `buildEndInfo: BuildEndInfo?` |
| `PipelineRuntimeService.cancelBuild()` | 参数 `cancelInfo` → `buildEndInfo` |
| `PipelineRuntimeService.cancelPendingTask()` | 构造 `BuildEndInfo.ofCancelSystem()` |
| `PipelineRuntimeService.concurrencyCancelBuildPipeline()` | 构造 `BuildEndInfo.ofCancelSystem()` |
| `PipelineBuildFacadeService.buildManualShutdown()` | 构造 `BuildEndInfo.ofCancelUser()` |
| `PipelineBuildFacadeService.serviceShutdown()` | 构造 `BuildEndInfo.ofCancelSystem()` |
| `PipelineBuildFacadeService.buildManualRestart()` | 构造 `BuildEndInfo.ofCancelUser()` |
| `QueueInterceptor` | 构造 `BuildEndInfo.ofCancelSystem()` |
| `SubPipelineCallAtom.tryFinish()` | 构造 `BuildEndInfo.ofCancelParentPipeline()`，读取父构建取消人填充 `operator` |
| `PipelineBuildTaskService.terminateSubPipeline()` | 构造 `BuildEndInfo.ofCancelParentPipeline()`，读取父构建取消人填充 `operator` |
| `BuildStartControl` | 构造 `BuildEndInfo.ofCancelSystem()` |
| `BuildCancelControl` | 从 event 中获取 `buildEndInfo`、补齐在途位置数文案并写入 record |
| `HeartbeatControl.timeout()` | `saveBuildEndInfoIfAbsent()` 写入 Agent 失联信息（`CANCEL_SYSTEM`） |
| `BuildMonitorControl` | `saveBuildEndInfoIfAbsent()` 写入Job超时信息 |
| `PipelineBuildRecordService.buildCancel()` | 参数 `cancelInfo` → `buildEndInfo`，保存到 `modelVar` |
| `PipelineBuildRecordService.saveBuildEndInfo()` | 新方法（原 `saveBuildCancelInfo`），保存后补推记录变更 |
| `PipelineBuildRecordService.saveBuildEndInfoIfAbsent()` | 运行中系统取消入口（心跳/Job超时），仅在尚未存在时保存 |
| `PipelineBuildRecordService.saveBuildEndInfoIfCompatible()` | 构建结束时写入：大类与最终状态同类则保留，冲突则覆盖 |
| `PipelineBuildRecordService.clearBuildEndInfoIfIncompatible()` | 解析器无需落库时清掉与最终状态不同类的残留 |
| `PipelineBuildRecordService.getBuildRecord()` | 从 `modelVar` 读取 `buildEndInfo`，按最终状态对齐并刷新位置状态；缺失时按模型合成失败/取消/成功 |
| `BuildEndPositionCollector` | 按模型插件当前终态收集失败位置，供读取对齐与解析器补齐 |
| `SubPipelineStartUpService` | 启动子构建时记录子流水线名称与构建号，供终态详情展示 |
| `SubPipelineTaskService` | 公开子流水线市场插件 atomCode 常量，供失败归类使用 |

### 4.3 实现要点

#### 4.3.1 向后兼容
- `BuildEndInfo` 所有字段设计为可选
- `ModelRecord.buildEndInfo` 为可选字段；已结束的失败/取消/超时在落库缺失时也会按模型合成
- `PipelineBuildCancelEvent.buildEndInfo` 为可选字段，默认 `null`
- 读取 `modelVar` 中的 `buildEndInfo` 时做空值保护

#### 4.3.2 终态原因国际化
- 原因使用 `ProcessMessageCode` 中的 message code 存储（如 `bkBuildCancelUserManual`）
- 写入时存储 `reasonCode` + `reasonParams`，不存储翻译后的文案
- 读取时通过 `I18nUtil.getCodeLanMessage()` 根据用户语言实时翻译并填充到 `reason` 字段
- 支持中文/英文/日文三种语言，i18n 配置位于 `support-files/i18n/process/message_*.properties`

#### 4.3.3 终态子类型国际化
- `BuildEndType` 通过 `@BkFieldI18n` 注解自动支持 i18n
- i18n key 格式：`buildEndType.{displayName}`，例如 `buildEndType.cancelUser`
- 读取时填充到 `endTypeDesc` 字段

#### 4.3.4 位置信息

位置信息通过 `EndPosition` 结构化数据类记录，包含三类信息：

**1. 展示信息**

| 字段 | 说明 | 示例 |
|------|------|------|
| `position` | 位置编码 `{stageIndex}-{containerSeq}[-{taskSeq}]` | `3-1-1`（task级）、`3-2`（job级） |
| `componentPath` | 组件路径 `Stage名/Job名[/Task名]` | `质量检查/单元测试/执行单测` |
| `statusAtEnd` | 终态时组件的 `BuildStatus` 枚举值 | `RUNNING`、`PREPARE_ENV`、`DEPENDENT_WAITING` |

**2. 锚点定位信息（与ErrorInfo一致，支持前端跳转）**

| 字段 | 说明 |
|------|------|
| `stageId` | 阶段ID |
| `containerId` | 容器ID (seq id) |
| `taskId` | 插件ID（task级别时填充） |
| `matrixFlag` | 是否构建矩阵 |

**3. 依赖Job导航信息（`DEPENDENT_WAITING`状态时填充）**

通过 `dependOnJobs: List<DependOnJobInfo>` 提供依赖Job的完整导航信息。

**记录粒度规则**：
- Job 状态为 `RUNNING` 且有正在运行的 Task → 记录到 **Task 级**（`3-1-1` 格式 + taskId锚点）
- Job 状态为 `RUNNING` 但无具体运行 Task → 记录到 **Job 级**
- Job 状态为 `PREPARE_ENV`/`QUEUE`/`DEPENDENT_WAITING` 等 → 记录到 **Job 级**
- Job 处于 `DEPENDENT_WAITING` 状态 → 额外填充 `dependOnJobs`
- **矩阵容器**：矩阵子容器独立记录，`matrixFlag = true`

#### 4.3.5 系统级取消的写入策略

心跳超时、Job执行超时、Job排队超时等系统级取消通过 `saveBuildEndInfoIfAbsent()` 写入，该方法仅在 `buildEndInfo` 尚未存在时才保存，避免覆盖用户主动取消等高优先级信息。

**位置信息收集策略**：

系统级取消有两类不同的位置收集方式：

| 事件路径 | 位置收集方式 | 说明 |
|---------|-------------|------|
| `PipelineBuildCancelEvent` → `BuildCancelControl` | 遍历 model，完整收集所有受影响容器/任务 | 适用于排队满、并发组互斥等场景 |
| `PipelineBuildContainerEvent(TERMINATE)` | 超时处理器加载 model 定位受影响容器，构造 `EndPosition` | 适用于心跳超时、Job执行超时 |
| `PipelineBuildFinishEvent` | 无位置信息（构建尚在排队中） | 适用于Job排队超时 |

心跳超时和Job执行超时不经过 `BuildCancelControl`，但超时处理器明确知道哪个容器受影响。因此在 `HeartbeatControl.timeout()` 和 `BuildMonitorControl.checkNextContainerMonitorIntervals()` 中，通过 `getRecordModel()` 加载 model，遍历 stages/containers 找到目标容器，与 `BuildCancelControl.collectEndPositions` 采用一致的计算逻辑生成正确的 `position`（如 `2-1`）和 `componentPath`（如 `编译/构建Job`）。超时是低频事件，加载一次 model 代价可接受。

#### 4.3.6 operator 字段语义
- `CANCEL_USER`：`operator` = 执行取消操作的用户ID（必填）
- `CANCEL_SYSTEM`：`operator = null`（系统发起，无真实操作人）
- `CANCEL_PARENT_PIPELINE`：`operator = null`（子构建本身无操作人），父流水线的取消操作人记录在
  `parentPipelineInfo.operator` 中，用于前端展示"父流水线 xxx #130 由 yyy 取消"

#### 4.3.7 构建运行总时长 `totalCostTime`

`totalCostTime` 为构建从开始到结束的总耗时（毫秒），供前端展示"已运行 N 分钟"。

采用**读取时计算**而非写入时持久化：

```kotlin
// PipelineBuildRecordService.getBuildRecord()
val buildRunCostTime = startTime?.let { (endTime ?: System.currentTimeMillis()) - it }
```

选择读取时计算的原因：
- 与 `reason` / `endTypeDesc` / `statusAtEndDesc` 等字段的填充方式保持一致
- 历史数据无需迁移，老记录同样能算出时长
- 写入侧的工厂方法（`ofCancelUser` 等）分散在十余个调用点，均无构建开始时间上下文，
  若改为写入时填充需要层层传递 `startTime`，侵入性大

边界处理：
- 构建尚未结束（`endTime` 为空）时按当前时刻计算
- 构建在排队中从未启动（`startTime` 为空）时返回 `null`，前端不展示时长

> **实现注意**：该计算必须放在 `apply` 块**外部**。因为 `BuildEndInfo` 自身存在 `endTime` 属性，
> 在 `apply { }` 内 `this` 为 `BuildEndInfo`，裸写 `endTime` 会被解析为终态时间戳属性，
> 而非外层的构建结束时间局部变量，从而算出错误结果。

#### 4.3.8 失败/超时/成功的解析入口 `BuildEndInfoResolver`

失败与超时的判定需要综合 `errorInfoList`、构建任务、构建阶段与 Model 四类数据，
统一收敛到 `BuildEndInfoResolver`（biz-engine），由 `BuildEndControl.finish()` 在构建结束时调用。

```
BuildEndControl.finish()
  ├─ getAllBuildTask() / getAllBuildStage()   ← 一次加载，供下面两处共用
  ├─ fixBuildInfo(buildInfo, tasks, stages)   ← 既有逻辑：终止残留任务 + 生成 errorInfoList
  ├─ pipelineBuildRecordService.buildEnd(...) ← 既有逻辑，返回刷新后的 Model
  └─ saveBuildEndInfo(BuildEndContext(...))   ← 解析终态并按最终状态对齐后落库
       └─ BuildEndInfoResolver.resolve()
            ├─ 取消态  → null（取消链路已落库；若最终状态不是取消则清掉错配）
            ├─ 成功态  → 仅阶段驳回时落库，普通成功返回 null
            └─ 失败/超时 → 位置收集 + 子类型汇总 + 原因组装
       └─ saveBuildEndInfoIfCompatible / clearBuildEndInfoIfIncompatible
```

**对既有逻辑的改动仅限于**：把 `getAllBuildTask()` / `getAllBuildStage()` 从 `fixBuildInfo` 内部
上提到 `finish()` 中，作为参数传入。`fixBuildInfo` 是私有方法且只有一个调用点，
其内部处理逻辑一行未改，只是数据来源由自查改为入参，避免解析器重复查询这两张表。

落库使用 `saveBuildEndInfoIfCompatible`：已有详情且大类与最终状态同类时保留
（用户取消、Job 超时等更早更精确的成因，含准确的超时分钟数）；
大类冲突则覆盖——典型是取消链路在暂停插件上提前写入 `CANCEL_USER`，构建却以失败收尾。
解析器返回 `null`（取消态 / 普通成功）时调用 `clearBuildEndInfoIfIncompatible`，
清掉与最终状态不同类的残留（如运行中重试未清干净的系统取消）。
整个解析与落库过程包在 try/catch 中——终态详情属于展示增强，任何异常都不应影响构建结束主流程。

运行中的心跳超时、Job 执行超时仍走 `saveBuildEndInfoIfAbsent()`，只在尚未落库时写入，
避免覆盖用户已经发起的取消。

#### 4.3.9 位置索引 `ModelPositionIndex`

失败场景可能同时存在多条错误信息，若为每条错误重复扫描整个 Model 定位组件，
复杂度会退化为 `O(错误数 × Model规模)`。因此 `EndPositionUtils.buildPositionIndex()`
一次遍历建立 `stageId / containerId → 位置信息` 的映射，后续定位均为 O(1)。

索引与既有的 `resolveEndPositions()` 共用同一套口径：跳过 Model 的第一个 Stage（触发器阶段，
详情页不展示），矩阵子容器沿用父容器的序号。

定位失败时的降级链路，保证任何情况下都能给出可用的位置：

| 情况 | 降级结果 |
|------|----------|
| 容器定位不到（如 Stage 级质量红线失败，containerId 为空） | Stage 级位置，`position = "3"` |
| 容器可定位但 taskId 在 Model 中找不到 | Job 级位置，`position = "3-2"` |
| 正常 | Task 级位置，`position = "3-2-1"` |

#### 4.3.10 质量红线指标详情的取数策略

截图中的失败原因「代码坏味道超过阈值 50」来自质量红线的拦截指标，
数据不在 process 模块，需调用 quality 服务的 `ServiceQualityInterceptResource.listHistory()`。

**仅在确认终态为质量红线失败时调用一次**，结果以 `reasonCode + reasonParams` 形式随 `BuildEndInfo` 落库。
构建详情接口是前端轮询调用的，若每次读取都跨模块查询会持续放大压力，因此绝不在读取侧调用。

存 `reasonCode + reasonParams` 而非直接存拼好的文案，是为了让翻译发生在**读取时**、按读者语言渲染；
若在构建结束时就翻译，用的会是服务端语言环境，多语言用户看到的文案就错了。

指标数量的处理：单个指标未达标时展示「指标名 + 阈值」，与设计稿「代码坏味道 (code smells) 超过阈值 50」一致；
多个指标时展示「首个指标等 N 项未达标」，避免原因文案无限变长。
质量服务不可用时退化为无指标详情的兜底文案，不影响构建结束。

**「超过阈值」与「未达到阈值」按阈值方向选词**：`QualityRuleInterceptRecord.operation` 为 `LE`/`LT`
（阈值是上限，如坏味道数 ≤ 50）时实际值高于阈值，用「超过阈值」；
为 `GE`/`GT`（阈值是下限，如覆盖率 ≥ 80）时实际值低于阈值，用「未达到阈值」。
两者混用会出现「覆盖率超过阈值 80」这类反义文案。

#### 4.3.11 `endCategory` 按结束成因归类

```kotlin
// PipelineBuildRecordService.getBuildRecord()
endCategory = endType.category
```

`endCategory` 恒等于 `endType.category`，二者**不可能出现跨类组合**（如 FAIL 类下出现
`CANCEL_SYSTEM`）。这一约束由枚举定义直接保证，无需运行时校验。

在此之上还有一条**铁律**：选择 `endType` 时必须选与构建最终状态同类的子类型，
详情卡片的大类因此始终与页面状态标签一致。写入侧 `saveBuildEndInfoIfCompatible` 保证新数据不错配；
读取侧 `BuildEndInfo.alignedTo` 再兜底存量数据（取消提前落库、重试残留等）。

`BuildEndCategory.of(status)` 的映射：

| 构建最终状态 | 允许的大类 | 说明 |
|--------------|-----------|------|
| `CANCELED` | CANCEL | 用户取消、父流水线级联、系统取消（含 Job 超时、Agent 失联走终止链路落成取消） |
| `QUEUE_TIMEOUT` | TIMEOUT | 唯一以超时状态收尾的场景，对应 `TIMEOUT_QUEUE` |
| `FAILED` / `TERMINATE` / `REVIEW_ABORT` / `QUALITY_CHECK_FAIL` / `HEARTBEAT_TIMEOUT` / `EXEC_TIMEOUT` | FAIL | 含由超时成因引发的失败，构建级降级为 `FAIL_EXEC`；暂停插件取消后收成失败也归这里 |
| `SUCCEED` / `STAGE_SUCCESS` | SUCCESS | 含阶段驳回、阶段审核中 |

读取对齐时失败类位置按模型插件**当前终态**重算，`statusAtEnd` 与详情页插件状态一致
（如 `FAILED`、`REVIEW_ABORT`），不用取消瞬间的 `PAUSE`。两类失败并存时构建级为 `FAIL_MULTIPLE`。

「超时」这类成因不会因为降级而丢失，它落在两个更精确的位置上：构建级 `reason`
（「所属 Job 超过执行时限 15m，步骤被终止」）与位置级 `endType` + `reason`
（位置的 `statusAtEnd` 本就是超时状态）。参见 [`BuildEndInfoResolver.alignToBuildStatus`]。

三个字段各司其职：

| 字段 | 含义 | 例（Job执行超时且构建以 CANCELED 收尾） |
|------|------|----------------------------------------|
| `ModelRecord.status` | 构建真实状态，页面状态标签 | `CANCELED` → 已取消 |
| `endCategory` | 结束大类，决定卡片样式，与状态同类 | `CANCEL` → 取消卡片 |
| `endType` / `endTypeDesc` | 大类内的具体子类型 | `CANCEL_SYSTEM` → 系统取消 |
| `reason` | 具体成因 | 所属 Job 超过执行时限 15m，步骤被终止 |

##### 待对齐：三个 `CANCEL_SYSTEM` 写入点的状态并非取消

取消链路的四个 `CANCEL_SYSTEM` 写入点中有三个落库的不是 `CANCELED`，与上述铁律不符：

| 写入点 | 成因 | 落库的构建状态 |
|--------|------|----------------|
| `PipelineBuildFacadeService.serviceShutdown()` | 服务端关闭取消构建 | `FAILED` |
| `PipelineRuntimeService`（流水线被删除） | 流水线删除终止构建 | `TERMINATE` |
| `BuildStartControl`（编排不存在） | 编排模型不存在 | `UNEXEC` |
| `PipelineRuntimeService.concurrencyCancelBuildPipeline()` | 并发组优先级 | `CANCELED` ✅ |

这三处的构建状态是既有引擎行为（早于本特性），对齐有两种方式，需产品确认后再动：
改 `endType` 为失败类并相应调整原因文案（不触碰引擎状态，风险低），
或把这些入口的构建状态改成 `CANCELED`（语义最准，但影响历史构建过滤、度量与通知）。

同理，Job 执行超时与 Agent 失联是在**超时判定时点**落库的，那时构建最终状态还没产生，
只能按最可能的取消类写入（也与设计稿一致）。当这类构建最终落成 `FAILED`/`TERMINATE` 时，
大类仍会与状态错位。要彻底消除需在 `BuildEndControl` 拿到最终状态后回头校正已落库的 `endType`，
本期未做——校正会让同一条记录的终态类型在构建结束前后发生变化，前端与 WebSocket 推送都要跟着适配。

##### 为什么不干脆把构建状态改准

根治办法是让Job超时的构建以 `EXEC_TIMEOUT` 收尾，状态与成因自然对齐。
但这要改 `BuildStatusSwitcher.PipelineBuildStatusMaker` 的构建级合法状态集，属于引擎核心状态机变更，
且构建状态是对外契约——度量统计的成功率/失败率、通知模板、Callback 事件、OpenAPI 消费方都依赖它，
新增终态值会破坏既有消费方。本需求的前提是不影响历史合理逻辑，因此不在此范围内处理。

#### 4.3.12 终态详情落库后需补推一次记录变更

`buildEndInfo` 的落库时点晚于构建结束的记录更新：`BuildEndControl` 先更新记录与状态并推送
WebSocket 记录变更事件，随后才由解析器算出终态详情写入 `modelVar`。
前端收到那次推送后立即拉取详情，拿到的是**还没有 `buildEndInfo`** 的数据，
页面便只有状态标签、没有终态详情卡片，需手动刷新才出现。

因此 `saveBuildEndInfo()` / `saveBuildEndInfoIfAbsent()` / `saveBuildEndInfoIfCompatible()` 在保存成功后补派发一次
`pipelineRecordChangeEvent`。`clearBuildEndInfoIfIncompatible` 清掉错配残留后同样补推，以便前端拿到对齐后的卡片。两点注意：

- **必须在事务提交之后派发**，否则推送侧回查可能仍读到旧数据
- 记录不存在、或因已有同类详情而跳过保存时不派发，避免无意义的推送

##### 读取时合成的终态还有第二个坑：判定状态的来源滞后

补推只能救**落库**的终态。普通成功与阶段准入审核中是读取时合成的，
而合成的判定依据原本取 `buildInfo.status`（`T_PIPELINE_BUILD_HISTORY`），
这张表的状态更新**晚于**详情记录表：

| 场景 | 记录表状态 | 构建历史表状态 | 推送时点 |
|------|-----------|----------------|----------|
| 普通成功 | `buildEnd()` 内置为 SUCCEED | `finishLatestRunningBuild()` 之后才置为 SUCCEED | `buildEnd()` 内推送，早于历史表更新 |
| 阶段准入审核中 | `stagePause()` 置为 STAGE_SUCCESS | `pauseStage()` 的事务里随后才置为 STAGE_SUCCESS | `stagePause()` 内推送，早于历史表更新 |

推送时历史表还是运行中，合成条件不成立 → 推送内容里没有 `buildEndInfo`；
而页面状态标签取的是记录表状态（已是终态），于是出现「状态已变、详情打不开，刷新才有」。

修正为**合成与状态标签同源取记录表状态**（`buildRecordModel.status`，回退 `buildInfo.status`），
两者一致后推送即可携带完整详情。同时把 `buildEnd()`、`buildCancel()` 里的记录变更推送移到事务提交之后，
避免推送侧回查读到未提交的运行中状态。

##### 阶段准入审核中不以构建状态为判定前提

同源取值解决了历史表滞后，但阶段挂起这一路还有一层顺序问题：
`stagePause()` 是先写阶段记录（`checkIn.status = REVIEWING`）、再改记录表构建状态、最后推送，
而调用方 `PipelineStageService.pauseStage()` 在这之后才把构建历史表改成 STAGE_SUCCESS。
只要写入顺序有变动，或推送恰好赶在状态改写前，「审核中」就又会漏掉。

因此合成侧不再要求「构建状态必须是 STAGE_SUCCESS」才去找审核位置：
**只要构建还没结束（`!status.isFinish()`），就先按模型里的阶段审核态合成**，
找不到停在审核中的阶段时才回到按状态判定成功态。审核态写在阶段记录里、且先于推送落库，
判定依据比构建状态更稳。审核通过（`checkIn.status` 变为 REVIEW_PROCESSED）或驳回
（REVIEW_ABORT）后自然找不到审核位置，卡片随下一次推送消失；多审核组时下一组仍是 REVIEWING，
卡片继续展示下一组的组名与审核人。

##### 运行中重试要清掉上一轮的终态详情

运行中重试失败步骤（#11807）复用同一 `executeCount`，`saveBuildRuntimeRecord()` 不会新建记录行。
而 Agent 失联、Job 执行超时这两个入口是**构建运行中**就落库终态详情的，重试后这份详情会残留，
构建结束时若仍用 `IfAbsent` 会被它挡住，页面就出现「成功」状态配「系统取消」详情卡片。
因此运行中重试分支调用 `clearBuildEndInfo()` 清除该 key，让构建结束时重新解析；
即便漏清，结束时的 `saveBuildEndInfoIfCompatible` / `clearBuildEndInfoIfIncompatible` 也会按最终状态覆盖或丢掉错配残留。
重试已结束的构建走的是 `executeCount + 1` 新建记录行（`modelVar` 为空），不受影响。

#### 4.3.13 失败原因逐位置表达

失败类的构建级 `reason` 恒为 `null`，原因随各自位置下发（见 6.3 末尾的说明）。
解析器在收集完位置后统一补齐位置级原因：

| 位置子类型 | 原因来源 | 兜底 |
|-----------|---------|------|
| FAIL_REVIEW | 审核驳回意见 `reviewSuggest` | 无（意见为空则不展示原因行） |
| FAIL_QUALITY | quality 模块的未达标指标（单条带指标名与阈值，多条带首条+项数） | `bkBuildEndFailQuality` |
| FAIL_SUB_PIPELINE | 子流水线名称 | `bkBuildEndFailSubPipeline`（存量构建未记录名称） |
| FAIL_FAST_KILL | 同阶段引发终止的 Job 名 | 无（只展示「因失败即停被终止」类型标签，见 2.2.0.1） |
| FAIL_EXEC | 插件 `errorMsg`（位置本身已带该字段） | 无 |

子流水线名称与构建号在启动子构建时随 `SubPipelineBuildInfo` 一并记录，
读取详情时无需再跨流水线查询。存量构建该字段为空，退化为兜底文案。

#### 4.3.14 阶段准入审核中的读取时合成

见 2.4.1。实现要点：

- 仅当构建状态为 `STAGE_SUCCESS` 且 `modelVar` 中没有已落库的终态详情时才尝试合成
- 遍历 Model 的 stages（跳过触发器阶段），取第一个 `checkIn`/`checkOut` 状态为 `REVIEWING` 的阶段
- 待审核组取 `StagePauseCheck.groupToReview()`，与阶段审核链路的取组逻辑一致
- `waitCostTime = 当前时刻 - stage.startEpoch`，`startEpoch` 在阶段暂停进入审核时写入
- 未命中审核中的阶段时退化为普通 `SUCCESS`，即阶段准入驳回等已落库场景不受影响

## 五、终态信息示例

### 5.1 用户手动取消

```json
{
  "endType": "CANCEL_USER",
  "endCategory": "CANCEL",
  "operator": "username",
  "reason": "3处在途均已停止",
  "endTypeDesc": "用户取消",
  "reasonCode": "bkBuildCancelUserInFlightStopped",
  "reasonParams": ["3"],
  "endTime": 1718694000000,
  "positions": [
    {
      "position": "3-1-1",
      "componentPath": "质量检查/单元测试/执行单测",
      "statusAtEnd": "RUNNING",
      "statusAtEndDesc": "运行中",
      "stageId": "stage-3",
      "containerId": "5",
      "taskId": "e-abc123"
    },
    {
      "position": "3-2",
      "componentPath": "质量检查/代码扫描",
      "statusAtEnd": "PREPARE_ENV",
      "statusAtEndDesc": "准备环境",
      "stageId": "stage-3",
      "containerId": "6"
    },
    {
      "position": "3-3",
      "componentPath": "质量检查/通知卡片",
      "statusAtEnd": "DEPENDENT_WAITING",
      "statusAtEndDesc": "等待依赖Job",
      "stageId": "stage-3",
      "containerId": "7",
      "dependOnJobs": [
        {
          "jobName": "单元测试",
          "projectId": "proj-xxx",
          "pipelineId": "p-xxx",
          "buildId": "b-xxx",
          "executeCount": 1,
          "stageId": "stage-3",
          "containerId": "5"
        }
      ]
    }
  ],
  "positionCount": 3,
  "totalCostTime": 300000
}
```

### 5.2 系统取消 - 并发组互斥

```json
{
  "endType": "CANCEL_SYSTEM",
  "reason": "系统取消：并发组[default]互斥，新构建入队导致排队中的构建被自动取消",
  "endTypeDesc": "系统取消",
  "reasonCode": "bkBuildCancelSystemConcurrencyGroup",
  "reasonParams": ["default"],
  "endTime": 1718694000000,
  "positions": [
    {
      "position": "1-1",
      "componentPath": "编译/构建Job",
      "statusAtEnd": "QUEUE",
      "statusAtEndDesc": "排队中",
      "stageId": "stage-1",
      "containerId": "2"
    }
  ],
  "positionCount": 1
}
```

### 5.3 系统取消 - 构建机Agent失联

Agent 心跳中断对用户而言是构建被系统中断，归类为 `CANCEL_SYSTEM`（见 2.3.0）。

```json
{
  "endType": "CANCEL_SYSTEM",
  "endCategory": "CANCEL",
  "reason": "构建机Agent异常退出，与节点失联",
  "endTypeDesc": "系统取消",
  "reasonCode": "bkBuildCancelSystemHeartbeatTimeout",
  "endTime": 1718694000000,
  "positions": [
    {
      "position": "2-1",
      "componentPath": "编译/构建Job",
      "statusAtEnd": "RUNNING",
      "statusAtEndDesc": "运行中",
      "stageId": "stage-2",
      "containerId": "3"
    }
  ],
  "positionCount": 1
}
```

### 5.4 系统取消 - Job执行超时

Job 执行超时派发的是终止事件，构建以 `CANCELED`/`TERMINATE`/`FAILED` 收尾而非超时状态
（见 2.3.1），因此归为取消类，「超时」这个成因由 `reason` 表达。

```json
{
  "endType": "CANCEL_SYSTEM",
  "endCategory": "CANCEL",
  "reason": "所属 Job 超过执行时限 480m，步骤被终止",
  "endTypeDesc": "系统取消",
  "reasonCode": "bkBuildCancelSystemJobExecTimeout",
  "reasonParams": ["480"],
  "endTime": 1718694000000,
  "positions": [
    {
      "position": "3-1",
      "componentPath": "质量检查/单元测试",
      "statusAtEnd": "RUNNING",
      "statusAtEndDesc": "运行中",
      "stageId": "stage-3",
      "containerId": "5"
    }
  ],
  "positionCount": 1
}
```

### 5.5 父流水线级联取消

```json
{
  "endType": "CANCEL_PARENT_PIPELINE",
  "endCategory": "CANCEL",
  "reason": "本次构建随父流水线取消而终止",
  "endTypeDesc": "父流水线取消",
  "reasonCode": "bkBuildCancelParentPipeline",
  "endTime": 1718694000000,
  "positions": [
    {
      "position": "2-1-3",
      "componentPath": "构建/编译Job/执行编译",
      "statusAtEnd": "RUNNING",
      "statusAtEndDesc": "运行中",
      "stageId": "stage-2",
      "containerId": "3",
      "taskId": "e-def456"
    }
  ],
  "positionCount": 1,
  "totalCostTime": 300000,
  "parentPipelineInfo": {
    "projectId": "demo-parent-project",
    "pipelineId": "p-xxx",
    "pipelineName": "Android 发布主流水线",
    "buildId": "b-xxx",
    "buildNum": 130,
    "operator": "fayewang"
  }
}
```

### 5.6 失败 - 质量红线未达标

失败类不设构建级 `reason`，指标详情挂在位置上（见 4.3.13）：

```json
{
  "endType": "FAIL_QUALITY",
  "endCategory": "FAIL",
  "reason": null,
  "endTypeDesc": "质量红线未达标",
  "endTime": 1718694000000,
  "totalCostTime": 300000,
  "positions": [
    {
      "position": "3-2-1",
      "componentPath": "质量检查/代码扫描/质量红线检查",
      "statusAtEnd": "QUALITY_CHECK_FAIL",
      "statusAtEndDesc": "质量红线检查失败",
      "endType": "FAIL_QUALITY",
      "endTypeDesc": "质量红线未达标",
      "reason": "代码坏味道 (code smells) 超过阈值 50",
      "reasonCode": "bkBuildEndFailQualityIndicatorExceed",
      "reasonParams": ["代码坏味道 (code smells)", "50"],
      "stageId": "stage-3",
      "containerId": "6",
      "taskId": "e-quality-1",
      "errorType": 1,
      "errorCode": 2199007,
      "errorMsg": "quality check fail",
      "containerHashId": "c-8a9b",
      "stepId": "quality_gate"
    }
  ],
  "positionCount": 1
}
```

### 5.7 失败 - 人工审核驳回（多个驳回位置）

驳回意见逐位置表达，构建级 `reason` 为空。多个位置各自驳回时不会相互覆盖：

```json
{
  "endType": "FAIL_REVIEW",
  "endCategory": "FAIL",
  "reason": null,
  "endTypeDesc": "人工审核驳回",
  "endTime": 1718694000000,
  "positions": [
    {
      "position": "3-1-1",
      "componentPath": "stage-3/无编译环境/人工审核",
      "statusAtEnd": "REVIEW_ABORT",
      "statusAtEndDesc": "审核驳回",
      "endType": "FAIL_REVIEW",
      "endTypeDesc": "人工审核驳回",
      "reason": "变更改动风险偏高，需补充充分验证后再提审",
      "stageId": "stage-3",
      "containerId": "5",
      "taskId": "e-review-1",
      "operator": "zhangsan",
      "reviewSuggest": "变更改动风险偏高，需补充充分验证后再提审"
    },
    {
      "position": "3-3-1",
      "componentPath": "stage-3/无编译环境/人工审核",
      "statusAtEnd": "REVIEW_ABORT",
      "statusAtEndDesc": "审核驳回",
      "endType": "FAIL_REVIEW",
      "endTypeDesc": "人工审核驳回",
      "reason": "等待上游修复后再提单",
      "stageId": "stage-3",
      "containerId": "7",
      "taskId": "e-review-2",
      "operator": "lisi",
      "reviewSuggest": "等待上游修复后再提单"
    }
  ],
  "positionCount": 2
}
```

### 5.8 失败 - 多类失败

同一次构建中出现多种失败子类型时，构建级为 `FAIL_MULTIPLE`，每条位置各自携带自己的子类型与原因。
其中 FastKill 连带终止的位置不参与构建级归类，`statusAtEnd` 视被终止的插件而定
（构建机任务是 `CANCELED`，引擎侧插件是 `FAILED`），前端不必据此区分：

```json
{
  "endType": "FAIL_MULTIPLE",
  "endCategory": "FAIL",
  "reason": null,
  "endTypeDesc": "多类失败",
  "positions": [
    {
      "position": "3-1-1",
      "componentPath": "质量检查/单元测试/执行单测",
      "statusAtEnd": "FAILED",
      "endType": "FAIL_EXEC",
      "endTypeDesc": "执行失败",
      "stageId": "stage-3", "containerId": "5", "taskId": "e-ut-1",
      "errorType": 1, "errorCode": 2123020, "errorMsg": "unit test failed"
    },
    {
      "position": "3-2-1",
      "componentPath": "质量检查/代码扫描/质量红线检查",
      "statusAtEnd": "QUALITY_CHECK_FAIL",
      "endType": "FAIL_QUALITY",
      "endTypeDesc": "质量红线未达标",
      "reason": "代码坏味道 (code smells) 超过阈值 50",
      "reasonCode": "bkBuildEndFailQualityIndicatorExceed",
      "reasonParams": ["代码坏味道 (code smells)", "50"],
      "stageId": "stage-3", "containerId": "6", "taskId": "e-quality-1",
      "errorType": 1, "errorCode": 2199007
    },
    {
      "position": "3-2-2",
      "componentPath": "质量检查/通知卡片/AI代码检查",
      "statusAtEnd": "CANCELED",
      "endType": "FAIL_FAST_KILL",
      "endTypeDesc": "因失败即停被终止",
      "reason": "已启用 Fastkill，因「单元测试」失败被终止",
      "reasonCode": "bkBuildEndFailFastKill",
      "reasonParams": ["单元测试"],
      "stageId": "stage-3", "containerId": "6", "taskId": "e-ai-1",
      "errorType": 1, "errorCode": 2199010
    },
    {
      "position": "3-3-1",
      "componentPath": "质量检查/发布/调用发布子流水线",
      "statusAtEnd": "FAILED",
      "endType": "FAIL_SUB_PIPELINE",
      "endTypeDesc": "子流水线失败",
      "reason": "Android 发布子流水线",
      "stageId": "stage-3", "containerId": "7", "taskId": "e-sub-1",
      "subPipelineInfo": {
        "projectId": "demo-sub-project",
        "pipelineId": "p-sub-xxx",
        "pipelineName": "Android 发布子流水线",
        "buildId": "b-sub-xxx",
        "buildNum": 88
      }
    }
  ],
  "positionCount": 4
}
```

### 5.9 失败 - 步骤超时

构建以 `FAILED` 收尾，构建级降级为 `FAIL_EXEC` 且不带构建级原因；
「步骤超时」与时限落在位置级，位置自身的状态就是 `EXEC_TIMEOUT`。

```json
{
  "endType": "FAIL_EXEC",
  "endCategory": "FAIL",
  "endTypeDesc": "执行失败",
  "positions": [
    {
      "position": "3-2-1",
      "componentPath": "质量检查/代码扫描/质量红线检查",
      "statusAtEnd": "EXEC_TIMEOUT",
      "statusAtEndDesc": "执行超时",
      "endType": "TIMEOUT_STEP",
      "endTypeDesc": "步骤超时",
      "reason": "本步骤超过执行时限 30m",
      "reasonCode": "bkBuildEndTimeoutStep",
      "reasonParams": ["30"],
      "stageId": "stage-3", "containerId": "6", "taskId": "e-scan-1",
      "errorType": 1, "errorCode": 2199006,
      "containerHashId": "c-8a9b", "stepId": "code_scan"
    }
  ],
  "positionCount": 1
}
```

### 5.10 成功 - 普通执行成功

读取时合成，不落库：

```json
{
  "endType": "SUCCESS",
  "endCategory": "SUCCESS",
  "endTypeDesc": "执行成功",
  "totalCostTime": 242000,
  "positionCount": 0
}
```

### 5.11 成功 - 阶段准入审核中（构建挂起）

读取时按阶段审核状态合成，不落库。此时构建并未结束，展示已等待时长而非总耗时：

```json
{
  "endType": "SUCCESS_STAGE_REVIEWING",
  "endCategory": "SUCCESS",
  "endTypeDesc": "审核中",
  "waitCostTime": 218000,
  "positions": [
    {
      "position": "4",
      "componentPath": "部署",
      "statusAtEnd": "REVIEWING",
      "statusAtEndDesc": "审核中",
      "endType": "SUCCESS_STAGE_REVIEWING",
      "endTypeDesc": "审核中",
      "stageId": "stage-4",
      "containerId": "",
      "reviewGroupSeq": 2,
      "reviewGroupName": "运维审核组",
      "reviewers": ["fayewang", "zhangsan"]
    }
  ],
  "positionCount": 1
}
```

### 5.12 成功 - 阶段准入驳回

```json
{
  "endType": "SUCCESS_STAGE_ABORT",
  "endCategory": "SUCCESS",
  "reason": "环境变量未同步，驳回本次上线",
  "endTypeDesc": "阶段准入驳回",
  "endTime": 1718694000000,
  "totalCostTime": 200000,
  "positions": [
    {
      "position": "4",
      "componentPath": "部署",
      "statusAtEnd": "REVIEW_ABORT",
      "statusAtEndDesc": "审核驳回",
      "endType": "SUCCESS_STAGE_ABORT",
      "endTypeDesc": "阶段准入驳回",
      "stageId": "stage-4",
      "containerId": "",
      "operator": "zhangsan",
      "reviewSuggest": "环境变量未同步，驳回本次上线",
      "reviewGroupSeq": 2,
      "reviewGroupName": "运维审核组"
    }
  ],
  "positionCount": 1
}
```

## 六、代码质量审查

### 6.1 时序安全性
- **非TERMINATE路径**：`saveBuildEndInfo` 在 `sendBuildFinishEvent`（MQ dispatch）之前同步完成，`buildEnd` 收到MQ消息后读取 `recordModel` 时，`buildEndInfo` 已经持久化到 `modelVar` 中
- **`buildEnd` 的 `modelVar.plus()` 不会覆盖 `buildEndInfo`**：因为 `buildEnd` 只新增 `timeCost` key 到 modelVar

### 6.2 序列化兼容性
- Jackson Kotlin Module 已全局注册，data class 无需 `@JsonCreator` 注解
- `PipelineBuildCancelEvent.buildEndInfo` 默认 `null`，旧版本 MQ 消息反序列化时自动忽略缺失字段
- `modelVar` 中不包含 `buildEndInfo` 时，`getBuildRecord` 按构建最终状态从模型合成（运行中且非阶段审核挂起仍为 `null`）

### 6.3 完整原因码清单

| 常量 | reasonCode | 适用场景 | 参数 |
|------|------------|----------|------|
| `BK_BUILD_CANCEL_USER_MANUAL` | `bkBuildCancelUserManual` | 用户手动取消（无在途位置时） | 无 |
| `BK_BUILD_CANCEL_USER_IN_FLIGHT_STOPPED` | `bkBuildCancelUserInFlightStopped` | 用户手动取消且有在途位置被停止 | {0}=在途位置数 |
| `BK_BUILD_CANCEL_USER_FORCE_TERMINATE` | `bkBuildCancelUserForceTerminate` | 用户强制终止 | 无 |
| `BK_BUILD_CANCEL_USER_RESTART` | `bkBuildCancelUserRestart` | 用户重启构建 | 无 |
| `BK_BUILD_CANCEL_SYSTEM_SERVICE_SHUTDOWN` | `bkBuildCancelSystemServiceShutdown` | 系统服务端关闭 | 无 |
| `BK_BUILD_CANCEL_SYSTEM_QUEUE_FULL` | `bkBuildCancelSystemQueueFull` | 排队已满 | 无 |
| `BK_BUILD_CANCEL_SYSTEM_CONCURRENCY_GROUP` | `bkBuildCancelSystemConcurrencyGroup` | 并发组互斥 | {0}=组名 |
| `BK_BUILD_CANCEL_SYSTEM_CONCURRENCY_PRIORITY` | `bkBuildCancelSystemConcurrencyPriority` | 并发组优先级 | {0}=组名 |
| `BK_BUILD_CANCEL_SYSTEM_MODEL_NOT_FOUND` | `bkBuildCancelSystemModelNotFound` | 编排模型不存在 | 无 |
| `BK_BUILD_CANCEL_SYSTEM_PIPELINE_DELETED` | `bkBuildCancelSystemPipelineDeleted` | 流水线被删除 | 无 |
| `BK_BUILD_CANCEL_SYSTEM_HEARTBEAT_TIMEOUT` | `bkBuildCancelSystemHeartbeatTimeout` | 构建机Agent失联 | 无 |
| `BK_BUILD_CANCEL_SYSTEM_JOB_EXEC_TIMEOUT` | `bkBuildCancelSystemJobExecTimeout` | Job执行超时 | {0}=超时分钟数 |
| `BK_BUILD_CANCEL_SYSTEM_JOB_QUEUE_TIMEOUT` | `bkBuildCancelSystemJobQueueTimeout` | Job排队超时 | 无 |
| `BK_BUILD_CANCEL_PARENT_PIPELINE` | `bkBuildCancelParentPipeline` | 父流水线级联取消 | 无 |
| `BK_BUILD_END_FAIL_SUB_PIPELINE` | `bkBuildEndFailSubPipeline` | 子流水线失败（存量构建无子流水线名时兜底，位置级） | 无 |
| `BK_BUILD_END_FAIL_FAST_KILL` | `bkBuildEndFailFastKill` | FastKill 提前终止（位置级） | {0}=引发终止的Job名 |
| `BK_BUILD_END_FAIL_QUALITY` | `bkBuildEndFailQuality` | 质量红线未达标（无指标详情时兜底，位置级） | 无 |
| `BK_BUILD_END_FAIL_QUALITY_INDICATOR_EXCEED` | `bkBuildEndFailQualityIndicatorExceed` | 单个指标超过阈值上限（位置级） | {0}=指标名 {1}=阈值 |
| `BK_BUILD_END_FAIL_QUALITY_INDICATOR_BELOW` | `bkBuildEndFailQualityIndicatorBelow` | 单个指标未达阈值下限（位置级） | {0}=指标名 {1}=阈值 |
| `BK_BUILD_END_FAIL_QUALITY_INDICATORS` | `bkBuildEndFailQualityIndicators` | 多个指标未达标（位置级） | {0}=首个指标名 {1}=项数 |
| `BK_BUILD_END_TIMEOUT_STEP` | `bkBuildEndTimeoutStep` | 步骤执行超时 | {0}=超时分钟数 |
| `BK_BUILD_END_STAGE_REVIEW_ABORT` | `bkBuildEndStageReviewAbort` | 阶段准入驳回（无驳回意见时兜底） | 无 |

**`reason` 与 `reasonCode` 互斥**：读取时若 `reasonCode` 有值，会用其国际化文案覆盖 `reason`，
`reason` 只作为词条缺失时的兜底默认值。构建级与位置级同此规则。因此写入方二者必须择一：

- 文案可枚举（取消、超时、质量红线指标、FastKill 等）→ 填 `reasonCode` + `reasonParams`，翻译发生在读取时，按读者语言渲染
- 文案是运行时产生的具体内容（插件错误信息、审核驳回意见、子流水线名）→ 只填 `reason`

**失败类的原因只出现在位置级**：同一次构建可能有多个失败位置，各自的原因（驳回意见、红线指标、
子流水线名）只能逐位置表达。构建级取其中一条会与位置列表相互矛盾——
「多个人工审核驳回时只展示一条驳回意见」正是此问题。因此 `FAIL_*` 的构建级 `reason` 恒为 `null`，
`reasonCode` 也不填；取消类与超时类的原因对整次构建唯一，仍保留在构建级。

### 6.4 数据规模防护

`MODEL_VAR` 是 JSONB 字段，超大流水线全量失败时位置数可能很多，因此设置了两道上限：

- 单条原因文案截断至 512 字符（防止异常长的插件堆栈信息）
- 位置列表最多保留 50 条

## 七、扩展性设计

### 7.1 新增终态类型

只需：
1. 在 `BuildEndType` 枚举中添加新值并指定 `category`（如 `FAIL_XXX(BuildEndCategory.FAIL)`）
2. 直接复用 `BuildEndInfo.of(endType, ...)`；仅当需要携带专属字段（类似取消类的 `parentPipelineInfo`）时才新增工厂方法
3. 在 `BuildEndInfoResolver.classifyError()` 中补充判定条件（失败/超时类），或在对应入口处直接构造写入
4. 添加 i18n 翻译（`buildEndType.failXxx`）

前端无需改动：新子类型会按 `category` 自动归入已有的卡片样式。

### 7.2 新增终态信息字段

`BuildEndInfo` 使用 data class + JSON 存储，新增字段不影响历史数据反序列化（Jackson 自动忽略缺失字段）。

### 7.3 前端适配

- 前端通过 `buildEndInfo.endCategory` 判断终态大类（取消/失败/超时/成功），选择卡片样式；
  该字段与 `endType` 恒定同类，前端不需要再自行推断
- `endTypeDesc` 为已翻译的子类型标签（如「子流水线失败」），展示在卡片内的「失败类型/取消类型」一栏
- `endCategory` 与 `ModelRecord.status` 的状态标签同类（后端按最终状态选择子类型，见 4.3.11），
  超时、Agent 失联这类成因体现在 `reason` 与位置级 `endType` 上，不会单独形成超时卡片
  （唯一的超时卡片是排队超时）。构建是失败时，即便取消链路先写过 `CANCEL_USER`，返回的也是失败卡片
- 位置 `statusAtEnd` / `statusAtEndDesc` 与详情页该插件当前状态一致（如失败、审核驳回），
  不要按取消瞬间的 `PAUSE` 理解
- `positions` + 锚点字段（`stageId`/`containerId`/`taskId`）支持前端跳转到具体组件
- 位置级 `endType`/`endTypeDesc` 用于「多类失败」场景下按位置分别展示标签
- 位置级 `reason` 用于失败类的原因展示（失败类构建级 `reason` 恒为 `null`）；
  该字段已含审核驳回意见，无需再单独渲染 `reviewSuggest`，否则同一句话会重复出现
- `containerHashId` + `stepId` + `taskId` 配合 `ModelRecord.executeCount` 用于「定位日志」
- `errorCode` 有值时展示「错误码 XXX」，`subPipelineInfo` 有值时展示子构建跳转入口
- `parentPipelineInfo` 支持前端展示父流水线名称、构建号和取消操作人，并跳转到父流水线详情；
  跳转链接需使用 `parentPipelineInfo.projectId`（父子构建可能跨项目），该字段为空时不展示跳转入口
- `positionCount` 支持前端展示被影响位置的总数
- `totalCostTime` 支持前端展示"已运行 N 分钟"，为毫秒值，由前端按需格式化
- `SUCCESS_STAGE_REVIEWING` 展示 `waitCostTime`（已等待时长）而非总耗时，
  位置上的 `reviewGroupSeq`/`reviewGroupName`/`reviewers` 用于渲染「N 级 · 组名 · 待审核人」

### 7.4 尚未覆盖：运行态场景

截图中的「排队中」「执行中」两类卡片（排队位置、前方排队列表、占用中的构建、等待中的 Job、待人工处理项）
不属于终态，数据随时间实时变化，无法在构建结束时落库。这部分需要在 `getBuildRecordByExecuteCount`
读取时按当前状态实时计算，是与 `BuildEndInfo` 并列的另一套机制，建议作为独立需求推进，避免混入终态结构。
