# 构建运行态详情（buildRunningInfo）前端对接文档

> 阅读对象：前端开发
> 配套文档：[BUILD_END_INFO_FRONTEND_API.md](./BUILD_END_INFO_FRONTEND_API.md)（构建**结束后**的详情）
> 目标：说明流水线**排队中 / 执行中**这两类未结束状态的详情如何从接口获取、各字段含义、以及前端如何渲染与跳转。

---

## 一、一句话理解

构建**尚未结束**时（排队中 / 执行中），后端在构建记录里附带一个 **`buildRunningInfo`（构建运行态详情）**，回答三件事：

- **现在处于什么状态**：排队中 / 执行中 → `runningType`、`runningTypeDesc`（有 Job 等待、待人工处理是执行中卡片下的区块，不改标题）
- **在等什么**：排队时是「排在第几位、被谁占着」→ `queueDetail`；执行时是「哪些 Job 卡在排队」→ `waitingJobs`
- **要我做什么**：哪些环节在等人工介入（暂停 / 审核 / 红线）→ `pendingItems`

它与 `buildEndInfo` 是**互斥**的一对：排队中/执行中看 `buildRunningInfo`，已结束或阶段准入挂起（`STAGE_SUCCESS`，卡片标题「审核中」）看 `buildEndInfo`，同一时刻只有一个有值。

运行态和终态**不要共用同一套位置/关联构建结构**。终态的 `EndPosition` / `SubPipelineInfo` 描述的是「为什么结束、跳到哪」；运行态描述的是「此刻卡在哪、在等谁」，字段语义不同，前端按各自文档对接。

---

## 二、接口与字段来源

| 项 | 说明 |
|----|------|
| 接口 | `getBuildRecordByExecuteCount`（复用既有接口，**不新增接口**） |
| 返回体 | `ModelRecord` |
| 新增字段 | `buildRunningInfo: BuildRunningInfo?`（**可选**，构建已结束时为 `null`） |
| 配套字段 | `ModelRecord.currentTimestamp`（服务端计算时刻，用于前端做时长自增） |

**什么时候 `buildRunningInfo` 为 `null`？**

- 构建**已结束**（成功/失败/取消/超时）——此时改看 `buildEndInfo`
- 构建处于**阶段准入挂起**（`STAGE_SUCCESS`）——此时改看 `buildEndInfo` 的「审核中」
- 构建处于**触发待审核**、**尚未入队**等既不排队也不执行的状态
- 解析过程发生异常——运行态属于展示增强，后端做了兜底，宁可返回 `null` 也不让详情页打不开

> 前端渲染前务必判空。

**这是实时计算的，不落库。** 排队位置、前方排队列表、已等待时长每秒都在变，落库读到的必然是过期快照，还会造成严重写放大。所以只在读取详情且构建未结束时现算——对已结束的构建零额外开销。

---

## 三、核心字段速查

### 3.1 `BuildRunningInfo`

| 字段 | 类型 | 含义 | 前端用法 |
|------|------|------|----------|
| `runningCategory` | String | **运行态大类**：`QUEUE` / `RUNNING` | 决定卡片样式（排队 / 执行两种配色） |
| `runningType` | String | 运行态子类型：`QUEUE_WAITING` / `QUEUE_CONCURRENCY` / `RUNNING_NORMAL` / `RUNNING_JOB_WAITING` | 一般不直接展示 |
| `runningTypeDesc` | String | **已翻译的状态标签**，排队态一律为「排队中」，执行态为「执行中」 | 卡片标题。并发组名称不要拼进标题，放到副行「并发组：xxx」 |
| `currentPhase` | String? | **当前阶段**。执行态下为正在推进的 Stage 名（如「构建编译」）；有 Job 排队时改为「Job 正在排队」。待人工处理**不**改写该字段，用 `pendingItems` 单独展示 | 执行中卡片的「当前阶段 xxx」 |
| `queueTime` | Long? | 入队时间（毫秒） | 「2026-04-17 20:01:00 入队」 |
| `startTime` | Long? | 开始执行时间（毫秒） | 「2026-04-17 20:01:00 开始执行」 |
| `waitingTime` | Long? | 已等待时长（毫秒，**排队态**填充） | 「已等待 12 秒」 |
| `runningTime` | Long? | 已运行时长（毫秒，**执行态**填充） | 「已运行 5 分钟」 |
| `triggerUser` | String? | 触发人 | 「fayeqiang 手动触发」 |
| `triggerDesc` | String? | **已翻译的**触发方式，如「手动触发」「Git 事件触发」 | 同上 |
| `queueDetail` | BuildQueueDetail? | 排队详情（**排队态**填充） | 见 3.2 |
| `waitingJobs` | List&lt;WaitingJobInfo&gt;? | 等待中的 Job 列表（**执行态**填充） | 见 3.3 |
| `waitingJobCount` | Int | 等待中的 Job 总数 | 「等待中的 Job (2)」 |
| `pendingItems` | List&lt;PendingManualItem&gt;? | 待人工处理项列表 | 见 3.4 |
| `pendingItemCount` | Int | 待人工处理项总数 | 「待人工处理 3 项」 |

> **时长字段是服务端快照。** `waitingTime` / `runningTime` 是后端计算那一刻的值，前端应结合 `currentTimestamp` 做秒级自增展示，不要为了刷新秒数去高频轮询接口。

### 3.2 `BuildQueueDetail`（排队详情）

| 字段 | 类型 | 含义 |
|------|------|------|
| `queuePosition` | Int | 排队位置，**从 1 开始**。取不到时为 `0`（并发组配置刚变更等边界情况），此时**不要展示**该项 |
| `concurrencyGroup` | String? | 并发组名称。未配置并发组（流水线串行排队）时为空。只用于副行展示，不要拼进卡片标题 |
| `maxConcurrency` | Int? | **当前生效的最大可并发数**，与流水线执行设置一致：单锁/并发组锁为 1，可并发运行为设置值。展示「仅允许 N 条运行」时必须用该字段，禁止写死 1 |
| `occupyingBuilds` | List&lt;RelatedBuildBrief&gt;? | **占用中**：正占着并发额度的运行中构建 |
| `occupyingCount` | Int | 占用中总数（可能大于列表长度，见下方上限说明） |
| `aheadBuilds` | List&lt;RelatedBuildBrief&gt;? | **前方排队**：排在自己前面的构建 |
| `aheadCount` | Int | 前方排队总数 |

`RelatedBuildBrief` 字段：`projectId`、`pipelineId`、`pipelineName`、`buildId`、`buildNum`、`triggerUser`、`triggerDesc`、`buildMsg`、`status`、`costTime`（运行中为已运行时长，排队中为已等待时长，毫秒）。

> **跳转必须用 `brief.projectId`。** 并发组可以跨流水线甚至跨项目，用当前页面的项目 ID 拼链接会 404。

### 3.3 `WaitingJobInfo`（等待中的 Job）

| 字段 | 类型 | 含义 |
|------|------|------|
| `waitType` | String | `MUTEX`（互斥排队） / `RESOURCE`（资源排队） / `DEPENDENT`（依赖等待） |
| `waitTypeDesc` | String | **已翻译**的等待类型标签，直接展示为 Job 前的彩色标签 |
| `position` | String | 位置编码 `{stage}-{job}`，如 `2-1` |
| `componentPath` | String | 组件路径 `Stage名/Job名`，如 `构建编译/低端构建` |
| `status` / `statusDesc` | String | Job 当前状态及其翻译 |
| `stageId` / `containerId` | String | **锚点定位**用，点「定位」时滚动到对应 Job |
| `containerHashId` | String? | 日志跳转用 |
| `matrixFlag` | Boolean? | 是否矩阵子 Job |
| `mutexGroup` | String? | 互斥组名（Job 互斥组排队时填充） |
| `waitReason` | String? | **已翻译**的等待原因。构建机复用互斥等待被依赖节点调度时填充，直接展示在 Job 行下方 |
| `waitingTime` | Long? | 已等待时长（毫秒） |
| `dependOnJobs` | List&lt;DependOnJobInfo&gt;? | 依赖的 Job 导航信息（`DEPENDENT` 时填充），结构与终态文档一致 |

> `position` / `componentPath` 的**编码规则**与终态 `EndPosition` 一致（共用 Model 位置索引），但对象本身不要当成终态位置来渲染。注意 `stageIndex` 已扣除 UI 不展示的触发器 Stage。

### 3.4 `PendingManualItem`（待人工处理项）

| 字段 | 类型 | 含义 |
|------|------|------|
| `itemType` | String | `TASK_PAUSE`（执行前暂停） / `TASK_REVIEW`（人工审核） / `TASK_QUALITY_GATE`（质量红线审核） / `STAGE_REVIEW`（阶段审核） / `STAGE_QUALITY_GATE`（阶段质量红线把关） |
| `itemTypeDesc` | String | **已翻译**的类型标签 |
| `handlers` | List&lt;String&gt;? | 处理人名单（**有明确名单时**填充） |
| `handlerDesc` | String? | 处理人描述（**无固定名单时**填充，如执行前暂停 → 「有执行权限者」） |
| `handlerDescCode` | String? | 上一项的 messageCode，一般不用 |
| `position` | String | 位置编码：任务级 `3-1-1`，阶段级 `3` |
| `componentPath` | String | 组件路径，如 `质量检查/单元测试/执行单测` |
| `stageId` / `containerId` / `taskId` | String? | 锚点定位。阶段级待处理项只有 `stageId` |
| `containerHashId` / `stepId` | String? | 日志跳转用 |
| `matrixFlag` | Boolean? | 是否矩阵子 Job |
| `reviewDesc` | String? | 审核说明 |

> **处理人展示逻辑**：`handlers` 有值就展示名单（「处理人：zhangsan」）；否则展示 `handlerDesc`（「处理人：有执行权限者」）；两者都为空时不展示该行。

---

## 四、列表上限

超大流水线可能产生大量条目，为避免撑爆响应体，后端做了截断：

| 列表 | 上限 | 计数字段 |
|------|------|----------|
| `occupyingBuilds` / `aheadBuilds` | 各 10 条 | `occupyingCount` / `aheadCount` 是**真实总数** |
| `waitingJobs` | 50 条 | `waitingJobCount` |
| `pendingItems` | 50 条 | `pendingItemCount` |

**计数字段是真实总数，可能大于列表长度。** 展示「前方排队 (12)」时用计数字段，列表里只渲染返回的那几条即可。

---

## 五、渲染示例

### 5.1 排队中（并发组）

```json
{
  "runningCategory": "QUEUE",
  "runningType": "QUEUE_CONCURRENCY",
  "runningTypeDesc": "排队中",
  "queueTime": 1776427260000,
  "waitingTime": 12000,
  "triggerUser": "fayeqiang",
  "triggerDesc": "Git事件触发",
  "queueDetail": {
    "queuePosition": 3,
    "concurrencyGroup": "release-train",
    "maxConcurrency": 1,
    "occupyingBuilds": [
      { "projectId": "demo", "pipelineId": "p-1", "pipelineName": "yamlv3-trigger",
        "buildId": "b-41", "buildNum": 41, "triggerUser": "fayeqiang",
        "buildMsg": "推送 master: feat: enhance pipeline trigger ui",
        "status": "RUNNING", "costTime": 1860000 }
    ],
    "occupyingCount": 1,
    "aheadBuilds": [
      { "projectId": "demo", "pipelineId": "p-2", "pipelineName": "部署流水线",
        "buildId": "b-54", "buildNum": 54, "triggerUser": "lisi",
        "triggerDesc": "手动触发", "status": "QUEUE", "costTime": 360000 }
    ],
    "aheadCount": 2
  }
}
```

渲染：标题「排队中」，主行「排队位置 **第 3 位** · 已等待 12 秒」，副行「2026-04-17 20:01:00 入队 · 并发组：release-train · 仅允许 1 条运行」，下方两个折叠区「占用中」「前方排队 (2)」，每条右侧「查看」跳转到对应构建。卡片标题不要写成「开发组排队中」或「并发组排队中」。

### 5.2 执行中（有排队的 Job）

```json
{
  "runningCategory": "RUNNING",
  "runningType": "RUNNING_JOB_WAITING",
  "runningTypeDesc": "执行中",
  "currentPhase": "Job 正在排队",
  "startTime": 1776427260000,
  "runningTime": 300000,
  "waitingJobs": [
    { "waitType": "MUTEX", "waitTypeDesc": "互斥排队",
      "position": "2-1", "componentPath": "构建编译/低端构建",
      "status": "PREPARE_ENV", "statusDesc": "环境准备中",
      "stageId": "stage-2", "containerId": "1",
      "waitReason": "构建机复用互斥，等待被依赖的节点 job_c2q 调度到具体节点后再进行复用调度",
      "waitingTime": 120000 },
    { "waitType": "RESOURCE", "waitTypeDesc": "资源排队",
      "position": "2-2", "componentPath": "构建编译/后端构建",
      "status": "PREPARE_ENV", "statusDesc": "环境准备中",
      "stageId": "stage-2", "containerId": "2", "waitingTime": 60000 }
  ],
  "waitingJobCount": 2
}
```

渲染：主行「当前阶段 **Job 正在排队** · 已运行 5 分钟」，下方「等待中的 Job (2)」列表，每条 = 等待类型标签 + `position` + `componentPath` + 右侧「定位」按钮（用 `stageId`/`containerId` 做锚点）。

### 5.3 执行中（待人工处理）

```json
{
  "runningCategory": "RUNNING",
  "runningType": "RUNNING_NORMAL",
  "runningTypeDesc": "执行中",
  "currentPhase": "质量检查",
  "pendingItems": [
    { "itemType": "TASK_PAUSE", "itemTypeDesc": "执行前暂停",
      "handlerDesc": "有执行权限者",
      "position": "3-1-1", "componentPath": "质量检查/单元测试/执行单测",
      "stageId": "stage-3", "containerId": "1", "taskId": "e-1" },
    { "itemType": "TASK_QUALITY_GATE", "itemTypeDesc": "质量红线审核",
      "handlers": ["zhangsan"],
      "position": "3-1-2", "componentPath": "质量检查/代码扫描/质量红线检查",
      "stageId": "stage-3", "containerId": "1", "taskId": "e-2" }
  ],
  "pendingItemCount": 2
}
```

渲染：构建行上方挂「待人工处理 2 项」提示条，点开为列表，每条 = 类型图标 + `itemTypeDesc` + 右侧处理人 + 次行 `position` + `componentPath` + 「去处理」按钮。

---

## 六、前端适配 Checklist

- [ ] `ModelRecord.buildRunningInfo` 判空后再渲染（已结束构建一定为 `null`）
- [ ] 卡片样式**只**用 `runningCategory` 决定（QUEUE / RUNNING）
- [ ] 状态标签用 `runningTypeDesc`（已翻译），不要用 `runningType` 硬编中文；排队态标题一律展示「排队中」，不要把 `concurrencyGroup` 拼进标题
- [ ] 「仅允许 N 条运行」用 `queueDetail.maxConcurrency`，不要写死 1
- [ ] `queuePosition === 0` 时不展示「排队位置」这一项
- [ ] 列表长度用 `xxxCount` 字段，不要用 `list.length`（后端有截断）；`aheadBuilds` 返回几条就渲染几条，不要只画第一条
- [ ] 构建机复用互斥：`waitType=MUTEX` 且 `waitReason` 有值时，把原因展示在 Job 行下方
- [ ] 时长用 `waitingTime` / `runningTime` 结合 `currentTimestamp` 自增，不要为刷新秒数高频轮询
- [ ] 所有关联构建跳转一律用 `brief.projectId`（并发组可跨项目）
- [ ] 「定位」用 `stageId` + `containerId`（+ `taskId`）做锚点；日志跳转再补 `containerHashId` + `stepId` + `executeCount`
- [ ] 处理人：优先 `handlers`，为空退 `handlerDesc`，都空则不展示
- [ ] 新增运行态子类型后端会自动归入现有大类，**前端一般无需改动**

---

## 七、常见问题（FAQ）

**Q：`buildRunningInfo` 和 `buildEndInfo` 会同时有值吗？**
A：不会。排队中/执行中只填 `buildRunningInfo`；构建结束、以及阶段准入挂起（`STAGE_SUCCESS` → `SUCCESS_STAGE_REVIEWING`）只填 `buildEndInfo`。前端按「哪个非空渲染哪个」即可，不要两张卡片叠着画。

**Q：阶段准入审核中为什么走终态卡片，而插件暂停/审核走运行态？**
A：`pauseStage` 后构建不再占用运行名额，状态是 `STAGE_SUCCESS`，由终态「审核中」表达。构建仍是 `RUNNING` 时的执行前暂停、人工审核、质量红线走 `pendingItems`，标题仍是「执行中」。

**Q：排队中为什么有时是 `QUEUE_WAITING`、有时是 `QUEUE_CONCURRENCY`？**
A：只有**确实配置了并发组且组内有构建在占用**时才算并发组排队。流水线串行（单锁）导致的排队同样会有「占用中」的构建，但成因不同，归为 `QUEUE_WAITING`。

**Q：Job 还在依赖等待/排队/准备环境，`currentPhase` 会是空的吗？**
A：不会。有 Job 排队时该字段是「Job 正在排队」；否则取第一个尚未结束的 Stage 名。仍为空只可能出现在阶段没有名字这类异常数据上，前端照常判空即可。待人工处理时仍返回 Stage 名，处理项走 `pendingItems`。

**Q：Job 明明在准备环境，为什么没出现在 `waitingJobs` 里？**
A：正常拉起构建机的那几秒不算「资源排队」。有第三方资源排队时间戳时立即上报；否则准备环境或 Job 排队持续超过约 15 秒才报 `RESOURCE`，避免每个构建都被误标。构建机复用互斥（含被依赖 Job 已在跑、本 Job 因节点并行已满仍停在准备环境）以 `MUTEX` 出现，并在 `waitReason` 写明原因。是否在等以页面状态为准：引擎在 startVM 后会把容器写成 `RUNNING`，记录表仍是 `PREPARE_ENV`。

**Q：矩阵 Job 会重复出现吗？**
A：不会。矩阵分组容器只是子容器的占位，其下插件不会真正执行，后端已跳过父容器，只统计矩阵子容器里的真实待处理项。

**Q：AI 待决策为什么没有？**
A：后端当前没有对应的运行时状态实现，暂不在本期范围内。`PendingItemType` 是枚举，后续补充子类型时前端按 `itemTypeDesc` 渲染即可，无需改动。
