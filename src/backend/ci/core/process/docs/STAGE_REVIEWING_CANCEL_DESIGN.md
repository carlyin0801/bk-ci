# 阶段准入审核中主动撤回执行 技术方案（#13391）

> 需求来源：[feat：当流水线状态进入 stage_success 发现参数有问题希望撤回重新填 · Issue #13391](https://github.com/TencentBlueKing/bk-ci/issues/13391)
> 影响模块：`process`（api-process / biz-base / biz-engine / biz-process）+ `support-files/i18n/process`

---

## 一、需求描述

流水线进入 Stage 准入人工审核（构建状态 `STAGE_SUCCESS`、准入 `checkIn.status = REVIEWING`）后，
发起人若发现执行参数填错，只能等审核人驳回或等审核超时，无法自己撤回，体验很差。

期望：**审核通过 / 超时之前，发起方可以主动取消本次执行；取消后阶段准入由「审核中」扭转为「审核驳回」，
驳回原因展示为「xxx 取消了执行」。**

---

## 二、现状分析

### 2.1 `STAGE_SUCCESS` 承载了两种语义

| 语义 | 构建状态 | `endTime` | 是否真正结束 |
|------|----------|-----------|--------------|
| 准入等待人工审核（挂起） | `STAGE_SUCCESS` | 空 | 否，审核通过后继续运行 |
| 准入被驳回/超时驳回后结束 | `STAGE_SUCCESS` | 有值 | 是（终态，按成功大类归类） |

`BuildStatus.STAGE_SUCCESS.isFinish()` 恒为 `false`，因此**只看枚举无法区分**这两种语义；
`BuildInfo.isFinish()` 已经用 `endTime > startTime` 做了区分（历史代码，见 `BuildInfo.kt`），本方案直接复用该口径。

### 2.2 原取消链路为什么不满足需求

用户点「取消」走 `PipelineBuildFacadeService.buildManualShutdown` → `pipelineRuntimeService.cancelBuild`
→ MQ `PipelineBuildCancelEvent` → `BuildCancelControl`，其中：

1. 终态判断用 `buildInfo.status.isFinish()`，对 `STAGE_SUCCESS` 恒为 `false`，
   即**已经驳回结束的构建也允许再次「取消」**，属于隐性缺陷。
2. `BuildCancelControl` 发现 pending stage 处于 `PAUSE` 时会调用 `cancelStageBySystem`，
   驳回意见硬编码为字符串 `"CANCEL"`，且终态信息已先按 `CANCEL_USER` 落库，
   构建被记为「用户取消」，与产品要求的「审核驳回 + xxx 取消了执行」不一致。
3. 前端取消按钮只在 `RUNNING/QUEUE` 等运行态展示，`STAGE_SUCCESS` 时按钮不可见。

---

## 三、设计目标与取舍

| 目标 | 取舍 |
|------|------|
| 取消后准入状态必须是「审核驳回」，原因是「xxx 取消了执行」 | **复用审核人驳回的同一条链路**（`PipelineStageService.cancelStage`），不新增状态、不新增表、不新增 MQ 事件 |
| 不影响审核人驳回、超时驳回、系统取消、父流水线取消等既有路径 | 新逻辑只在「构建处于准入审核中 + 用户主动取消（非强制终止）」这一交叉条件下生效，其余路径代码零改动 |
| 不引入 MQ / DB 兼容风险 | 不改 `PipelineBuildCancelEvent` 结构、不改表结构，灰度期间新老实例可混跑 |
| 可扩展 | 驳回意见通过 `cancelStageBySystem(abortSuggest = ...)` 可选参数注入，后续「撤回并重填参数」等场景可复用同一入口 |

---

## 四、方案设计

### 4.1 总体思路

在**取消入口层**（Facade）识别「准入审核中」，把这一类取消**改道**到审核驳回链路，
从而完全不发取消事件、不进 `BuildCancelControl`，构建不会被记为 `CANCELED`。

```
用户点击取消
      │
      ▼
PipelineBuildFacadeService.buildManualShutdown
      │  ① buildInfo.isFinish() → 已结束，拒绝
      │  ② 校验 pipelineId / 渠道
      ├── ③ terminateFlag != true && buildInfo.isStageReviewing()
      │        └─► PipelineStageService.cancelStageReviewingByUser
      │                 └─► cancelStageBySystem(abortSuggest = "xxx 取消了执行")
      │                          └─► cancelStage(...)   ← 与审核人驳回同一实现
      │                                  · checkIn.reviewGroup(ABORT, operator=userId, suggest=意见)
      │                                  · checkIn.status = REVIEW_ABORT
      │                                  · stage = STAGE_SUCCESS，build = STAGE_SUCCESS → RUNNING
      │                                  · 派发 BS_STAGE_CANCELED_END_SOURCE 走 finally stage → 构建结束
      │                                  · 广播 REVIEW_ABORT + 通知触发人
      │        └─► 打取消标识（防重复取消）+ 记录取消人 + 打印构建日志 → return
      │
      ▼ ④ 其余情况（运行中、排队中等）走原有取消链路，逻辑完全不变
```

### 4.2 状态流转

| 环节 | 取消前 | 取消后 |
|------|--------|--------|
| 阶段准入 `checkIn.status` | `REVIEWING` | `REVIEW_ABORT`（前端展示「审核驳回」） |
| 审核组 `reviewGroups[i]` | 待审核 | `status=ABORT`，`operator=取消人`，`suggest=「xxx 取消了执行」` |
| Stage 状态 | `PAUSE` | `STAGE_SUCCESS` |
| 构建状态 | `STAGE_SUCCESS`（无 `endTime`） | `STAGE_SUCCESS`（有 `endTime`，终态） |
| 构建终态信息 `BuildEndInfo` | 读取时合成 `SUCCESS_STAGE_REVIEWING` | 落库 `SUCCESS_STAGE_ABORT`，`reason` = 「xxx 取消了执行」 |

终态由 `BuildEndInfoResolver.resolveSuccess` 在构建结束时按 `checkIn.status == REVIEW_ABORT` 推导，
`reason` 取驳回组的 `suggest`，位置级 `operator` 即取消人，**无需为本需求新增终态类型**，
前端沿用 `BUILD_END_INFO_FRONTEND_API.md` 中「阶段准入驳回」的既有渲染规则。

---

## 五、代码改动清单

| 文件 | 改动 |
|------|------|
| `api-process/.../engine/pojo/BuildInfo.kt` | 新增 `isStageReviewing() = isStageSuccess() && !isFinish()`，统一「准入审核中」判定口径 |
| `api-process/.../constant/ProcessMessageCode.kt` | 新增 `BK_STAGE_REVIEW_ABORT_BY_USER_CANCEL = "bkStageReviewAbortByUserCancel"` |
| `support-files/i18n/process/message_{zh_CN,en_US,ja_JP}.properties` | 新增文案：`{0} 取消了执行` / `{0} cancelled the execution` / `{0} が実行をキャンセルしました` |
| `biz-base/.../service/PipelineStageService.kt` | ① 新增 `cancelStageReviewingByUser(userId, buildInfo): Boolean`；② `cancelStageBySystem` 新增可选参数 `abortSuggest`，驳回意见优先级 `TIMEOUT` > `abortSuggest` > `"CANCEL"`（默认值不变，老调用方行为不变） |
| `biz-process/.../builds/PipelineBuildFacadeService.kt` | ① 终态守卫由 `buildInfo.status.isFinish()` 收紧为 `buildInfo.isFinish()`；② 渠道校验前移；③ 新增私有方法 `cancelStageReviewingIfNeed`，命中则改道并 `return` |
| `biz-engine/.../control/BuildCancelControl.kt` | 仅同步收紧终态守卫为 `buildInfo.isFinish()`，防止已驳回结束的构建被取消事件二次处理；其余逻辑保持原样 |

### 5.1 关键实现

`PipelineStageService`：

```kotlin
fun cancelStageReviewingByUser(userId: String, buildInfo: BuildInfo): Boolean {
    val pendingStage = getPendingStage(buildInfo.projectId, buildInfo.buildId) ?: return false
    if (!pendingStage.status.isPause() || pendingStage.checkIn?.groupToReview() == null) {
        return false          // 没有待审核的暂停 Stage，交回调用方走普通取消
    }
    val abortSuggest = I18nUtil.getCodeLanMessage(
        messageCode = BK_STAGE_REVIEW_ABORT_BY_USER_CANCEL,
        params = arrayOf(userId),
        defaultMessage = "$userId 取消了执行"
    )
    cancelStageBySystem(userId, buildInfo, pendingStage, timeout = false, abortSuggest = abortSuggest)
    return true
}
```

`PipelineBuildFacadeService`：

```kotlin
// #13391 阶段准入审核中取消：走审核驳回，不把构建记为 CANCELED。
// 强制终止（草稿删除、数据迁移等）要求构建立即终止，仍走原有终止链路
if (terminateFlag != true && cancelStageReviewingIfNeed(userId = userId, buildInfo = buildInfo)) {
    return
}
```

改道成功后额外做三件事，保持与原取消链路的一致性：

1. `redisOperation.set(BuildUtils.getCancelActionBuildKey(buildId), ...)`：与 `BuildCancelControl.setBuildCancelActionRedisFlag` 完全一致的取消标识；
2. `buildRecordService.updateBuildCancelUser(...)`：记录取消人（与「审核插件驳回」路径一致的既有做法），重复取消提示可显示「已被 xx 取消」；
3. `buildLogPrinter.addYellowLine(...)`：构建日志留痕，格式沿用原 `tasks.isEmpty()` 分支。

---

## 六、边界场景与兼容性

| 场景 | 处理 | 说明 |
|------|------|------|
| **强制终止**（`terminateFlag = true`） | 不改道，走原终止链路 | 调用方为草稿版本删除 `PipelineDraftVersionPostProcessor`、数据迁移 `PipelineMigrationTask`，语义是「必须立刻终止」，不能变成异步驳回 |
| **重复点击取消** | 第 1 次改道成功后写入取消标识；驳回会同步把构建状态 `STAGE_SUCCESS → RUNNING`，在结束事件走完前的窗口内第 2 次点击命中标识，抛 `CANCEL_BUILD_BY_OTHER_USER` | 若不写标识，窗口内的第 2 次取消会把构建改写成 `CANCELED`，破坏「审核驳回」终态 |
| **审核人驳回 / 审核超时** | 零改动 | `timeout = true` 时意见仍为 `"TIMEOUT"`，`system = timeout` 保持 `SYSTEM` 操作人语义 |
| **系统取消**（服务重启 `serviceShutdown`、并发组抢占、父流水线取消） | 零改动 | 这些路径 `endType` 非 `CANCEL_USER`，且不经过 Facade 的用户取消入口，仍走 `cancelStageBySystem("CANCEL")` |
| **质量红线卡住的 Stage** | 不改道 | 质量红线等待时 Stage 不是 `PAUSE` 且无待审核组，`cancelStageReviewingByUser` 返回 `false`，自动回落普通取消 |
| **已驳回/超时结束的 `STAGE_SUCCESS` 构建** | Facade 与 `BuildCancelControl` 均按 `isFinish()` 拒绝 | 修正了历史上「终态构建仍可被取消」的隐性缺陷 |
| **准出审核（checkOut）** | 不改道，回落普通取消 | 当前仅准入有审核逻辑（见代码注释 #5019），保持现状 |
| **权限** | 复用 `hasPermissionToCancelBuild` | `RESTRICTED` 策略下触发人或管理者可取消（发起方天然满足）；默认策略校验执行权限 |
| **MQ / DB / 接口** | 无结构变更 | 未新增事件字段、未改表、未改 OpenAPI 出入参，灰度期新老实例可混跑，无需停机 |
| **国际化** | 驳回意见按取消操作时的请求语言落库 | 与审核意见（自由文本）一致的既有口径：存文本不存 code，跨语言查看不二次翻译 |

---

## 七、测试情况

### 7.1 自动化

| 用例 | 位置 | 结论 |
|------|------|------|
| `STAGE_SUCCESS` 无 `endTime` 判为审核中；有 `endTime` 判为终态；其他状态不误判 | `api-process/src/test/.../BuildInfoStageReviewingTest.kt` | 通过 |
| 驳回待审核组时正确记录 `operator` / `suggest` / 组状态，且不再有待审核组 | `common-pipeline/src/test/.../StagePauseCheckTest.kt`（新增用例） | 通过 |

编译验证：`:core:process:biz-process`、`:core:process:biz-engine`、`:core:process:biz-base`、
`:core:process:api-process`、`:core:common:common-pipeline` 的 `compileKotlin` 与 `compileTestKotlin` 全部 BUILD SUCCESSFUL。

### 7.2 需覆盖的手工用例

1. 单审核组准入审核中 → 发起人取消 → 准入显示「审核驳回」，意见「xxx 取消了执行」，构建终态「阶段成功 / 已驳回」，finally stage 正常执行；
2. 多审核组，第 1 组已通过、第 2 组审核中 → 取消 → 驳回落在第 2 组，`reviewGroupSeq` 正确；
3. 审核人正常驳回、审核超时驳回 → 意见仍为原文案，无回归；
4. 审核中连点两次取消 → 第 2 次提示「已被 xx 取消」，构建终态仍为阶段成功而非已取消；
5. 运行中取消、排队中取消、post 任务场景二次取消（强制终止）→ 行为与上线前一致；
6. 草稿版本删除 / 数据迁移触发的强制终止 → 构建被终止为 `CANCELED`，不走驳回；
7. 已驳回结束的构建再点取消 → 提示构建已结束，不可操作；
8. 触发人收到 Stage 驳回通知，`rejectUserId`、驳回意见正确；
9. 中英日三语言下取消，意见文案正确。

---

## 八、上线评估

**结论：后端改动已达到上线要求（需配套前端改动才能在 UI 使用）。**

| 评估项 | 结论 |
|--------|------|
| 需求覆盖 | 满足：审核中可主动取消、准入扭转为「审核驳回」、原因「xxx 取消了执行」 |
| 兼容性 | 无表结构、MQ 事件结构、接口出入参变更，可平滑灰度 |
| 存量逻辑影响 | 新逻辑仅在「审核中 + 用户主动取消 + 非强制终止」交叉条件生效；对既有路径唯一的行为变化是终态 `STAGE_SUCCESS` 构建不再可被取消（缺陷修正） |
| 并发/重复操作 | 已复用 `BuildIdLock` + 取消标识，重复取消不会改写终态 |
| 可观测性 | Facade 与 Stage 服务各打一条 `STAGE_REVIEWING_CANCEL` 日志，并写构建日志，便于排障 |
| 回滚 | 单独 commit，回滚后仅回到「取消记为 CANCELED」的旧行为，已产生的数据（`REVIEW_ABORT` + suggest）与旧版本读取逻辑兼容 |

### 待办（前端，非本次后端范围）

UI 取消按钮当前只在运行态展示，需放开到「`status === 'STAGE_SUCCESS' && !endTime`」：

- `devops-pipeline/src/components/PipelineHeader/DetailHeader.vue`（构建详情头部）
- `devops-flow/src/utils/flowStatus.ts` + `hooks/useExecuteDetail.ts` + `views/Flow/Execute/DetailHeader/index.tsx`
- `devops-stream/src/views/pipeline/build-detail/detail.vue`、`build-list.vue`

建议统一封装 `canCancelExecute(status, endTime)` 供各端复用，避免判定口径分散。
在前端放开前，后端能力可先通过 `ServiceBuildResource.manualShutdown` / OpenAPI 验证。

### 上线检查项

- [ ] `support-files/i18n/process/*.properties` 三语言文案随包发布（i18n 配置需同步到配置中心/DB）
- [ ] `process` 服务（biz-process / biz-engine 同镜像）滚动发布
- [ ] 发布后按第 7.2 节用例 1、3、4、6 做冒烟
- [ ] 确认 `gradle.properties` 等本地开发配置未被误提交
