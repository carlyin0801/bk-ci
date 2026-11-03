# 构建终态详情（buildEndInfo）前端对接文档

> 适用对象：前端开发
> 配套后端设计：[BUILD_CANCEL_INFO_DESIGN.md](./BUILD_CANCEL_INFO_DESIGN.md)
> 目标：说明流水线构建「取消 / 失败 / 超时 / 成功」终态详情如何从接口获取、各字段含义、以及前端如何渲染与跳转。

---

## 一、一句话概览

流水线构建结束后（取消、失败、超时、成功），后端会在构建记录里附上一份 **`buildEndInfo`（构建终态详情）**，告诉你：

- **属于哪一类结束成因**（取消 / 失败 / 超时 / 成功）—— 用 `endCategory` 决定卡片样式
- **为什么结束的**（用户手动取消、构建机失联、质量红线未达标、人工审核驳回……）—— 用 `endTypeDesc` 作为卡片内的成因标签，`reason` 作为原因详情
- **哪些组件被影响了**（哪个 Stage / Job / 插件）—— 用 `positions` 列表，每条自带成因与原因，支持跳转定位

前端**无需自己推断**终态归类，拿到字段直接渲染即可。

---

## 二、接口与字段来源

| 项 | 说明 |
|----|------|
| 接口 | `getBuildRecordByExecuteCount`（复用既有接口，**不新增接口**） |
| 返回体 | `ModelRecord`（`buildRecord` 相关返回对象） |
| 新增字段 | `buildEndInfo: BuildEndInfo?`（**可选**，历史数据 / 运行中构建为 `null`） |
| 构建次数 | `ModelRecord.executeCount`（构建级，定位日志时要用，见第六节） |

**重要：什么时候 `buildEndInfo` 为 `null`？**
- 构建仍在**排队中 / 执行中**：终态还没产生，返回 `null`。例外是**挂起在阶段准入审核**的构建，
  它虽未结束，但会返回 `SUCCESS_STAGE_REVIEWING` 详情（详见 5.x「阶段准入审核中」示例）
- 普通**成功**构建：**不落库**，读取时由后端合成（前端拿到的结构和其它终态完全一致，只是 `positions` 为空）
- 已结束的失败 / 取消 / 超时：即使写入侧漏记（执行前暂停点终止、Job 互斥组抢锁失败等），
  读取侧也会按模型合成，前端应能拿到卡片。只有模型里也还原不出任何终态线索、且构建尚未结束时才为 `null`。

> 前端渲染前务必判空，避免 `Cannot read properties of null` 这类报错。

---

## 三、核心字段速查表

### 3.1 `BuildEndInfo`（构建级终态详情）

| 字段 | 类型 | 含义 | 前端用法 |
|------|------|------|----------|
| `endCategory` | String | **结束成因大类**：`CANCEL` / `FAIL` / `TIMEOUT` / `SUCCESS`，恒等于 `endType` 所属大类 | 决定卡片整体样式/配色 |
| `endType` | String | 终态子类型（枚举值，见 3.5） | 一般不直接展示，用于区分逻辑；展示用 `endTypeDesc` |
| `endTypeDesc` | String | **已翻译的成因标签**，如「系统取消」「用户取消」「质量红线未达标」 | 卡片内的「原因标签」直接展示 |
| `reason` | String? | 构建级终态原因文案（已翻译，空白已压缩并截断至 200 字符）。**失败类恒为 `null`**，原因逐位置给出（见 4.4） | 卡片头部的「原因详情」，为空则不渲染该行 |
| `reasonCode` | String? | 国际化 messageCode（如 `bkBuildCancelUserManual`）。**有值时优先级高于 `reason`** | 一般不用，做兜底/调试参考 |
| `reasonParams` | List<String>? | 国际化占位符参数（如超时分钟数 `["15"]`） | 一般不用，文案已拼好 |
| `operator` | String? | 操作人。**仅用户主动取消时有值**（如 `CANCEL_USER`）。系统取消 / 父流水线级联取消时为 `null` | 展示「由 xxx 取消」 |
| `endTime` | Long? | 终态时间戳（毫秒） | 展示结束时间（按需格式化） |
| `totalCostTime` | Long? | 构建总耗时（毫秒）。**队列中从未启动的构建为 `null`** | 展示「已运行 N 分钟 / N 小时」 |
| `waitCostTime` | Long? | 已等待时长（毫秒）。**仅 `SUCCESS_STAGE_REVIEWING`（阶段准入审核中）有值** | 展示「已等待 3 分 38 秒」 |
| `positions` | List<EndPosition>? | 被影响的组件位置列表（Stage / Job / 插件） | 渲染「受影响位置」列表，支持跳转定位 |
| `positionCount` | Int | 被影响位置总数（与 `positions.length` 一致，空列表时为 0） | 展示「N 个位置受影响」 |
| `parentPipelineInfo` | ParentPipelineInfo? | 父流水线信息（**仅父流水线级联取消时有值**） | 展示父流水线名称/构建号/操作人，可跳转 |

> ⚠️ **`endCategory` 与构建最终状态同类**：后端按 `ModelRecord.status` 所属大类选择 `endType`，
> 不会出现「状态：失败 + 取消卡片」或「状态：已取消 + 失败卡片」这类矛盾组合。
> 取消链路可能在暂停插件上提前写入 `CANCEL_USER`，若构建最终收成失败（如暂停插件落成 `FAILED`、
> 后续人工审核 `REVIEW_ABORT`），读取/写入侧都会把卡片对齐为失败类，位置 `statusAtEnd` 取插件当前终态，
> 而不是取消瞬间的 `PAUSE`。像 Job 超时、Agent 失联这类走终止链路的成因，大类跟随最终状态（取消/失败），
> 具体成因放在 `reason` 与位置级 `endType` 里。
> 卡片标题（「执行失败」/「阶段成功」/「已取消」）沿用页面既有的状态标签逻辑，
> 卡片内的类型标签与原因用 `buildEndInfo`。
> 执行失败位置若没有插件错误信息，后端会补「暂停执行期间被终止」或「步骤执行失败」，
> 不要因为 `reason` 为空就隐藏整张卡片。

### 3.2 `EndPosition`（位置级详情）

每个被影响的组件一条记录，字段较多，按用途分组：

**① 展示信息**

| 字段 | 含义 | 示例 |
|------|------|------|
| `position` | 位置编码：`{stage}-{job}[-{task}]` | `3-1-1`（插件级）、`3-2`（Job级）、`3`（Stage级） |
| `componentPath` | 组件路径：`Stage名/Job名[/Task名]` | `质量检查/单元测试/执行单测` |
| `statusAtEnd` | 该组件**当前终态**枚举值（如 `FAILED`、`REVIEW_ABORT`），与详情页插件状态一致，不是取消瞬间的中间态 | 展示位置状态 |
| `statusAtEndDesc` | 上述状态的**已翻译描述**。执行前暂停被终止时固定为「暂停/终止」，与编排图对齐 | `失败`、`审核驳回`、`暂停/终止` |
| `endType` | 该位置自身的终态子类型（多类失败时各位置不同） | `FAIL_QUALITY` |
| `endTypeDesc` | 该位置的已翻译成因标签 | `质量红线未达标` |
| `reason` | 该位置的原因文案（已翻译，空白已压缩并截断至 200 字符），失败类位置的原因都在这里 | `代码坏味道 (code smells) 超过阈值 50` |
| `reasonCode` / `reasonParams` | 位置原因的国际化标识与参数 | 一般不用，`reason` 已拼好 |
| `reviewGroupSeq` / `reviewGroupName` | 审核组序号（从 1 开始）与名称，阶段准入审核位置有值 | `2` / `运维审核组` |
| `reviewers` | 待审核人列表，**仅阶段准入审核中**有值 | `["fayewang", "zhangsan"]` |

**② 锚点定位（点击跳转用）**

| 字段 | 含义 |
|------|------|
| `stageId` | 阶段 ID（必填） |
| `containerId` | 容器 ID（Job 级，必填；Stage 级质量红线失败时可能为空串 `""`） |
| `taskId` | 插件 ID（仅插件级位置有值） |
| `matrixFlag` | 是否构建矩阵 |
| `containerHashId` / `stepId` | 定位日志参数（见第六节） |

**③ 错误与跳转补充信息**

| 字段 | 含义 | 前端用法 |
|------|------|----------|
| `errorType` | 错误类型（Int，有值时展示错误图标） | 据值选图标 |
| `errorCode` | 错误码（Int，有值时展示「错误码 XXX」） | 展示错误码 |
| `errorMsg` | 错误信息文案（空白已压缩，截断至 512 字符） | **不要铺在卡片原因行**。仅用于 tooltip / 展开态；卡片原因行只用 `reason` |
| `operator` | 操作人（人工审核驳回等场景） | 展示「由 xxx 驳回」 |
| `reviewSuggest` | 审核意见原始值（驳回原因）。人工审核驳回位置的 `reason` 即由它填充，**不要与 `reason` 重复渲染** | 一般用 `reason` |
| `subPipelineInfo` | 子流水线失败时的跳转信息（有值时展示子构建入口），含 `pipelineName` / `buildNum` | 跳转子流水线详情 |
| `dependOnJobs` | 依赖 Job 导航信息（`DEPENDENT_WAITING` 状态时有值） | 展示「等待以下 Job」并跳转 |

### 3.3 `ParentPipelineInfo`（父流水线信息）

| 字段 | 含义 | 前端用法 |
|------|------|----------|
| `projectId` | 父流水线所属项目 ID（**可能跨项目！**） | 拼接父构建链接时**必须用它**，不能用当前页面项目 ID |
| `pipelineId` | 父流水线 ID | 跳转锚点 |
| `pipelineName` | 父流水线名称 | 展示「父流水线：xxx」 |
| `buildId` | 父构建 ID | 跳转锚点 |
| `buildNum` | 父构建号 | 展示「#130」 |
| `operator` | 父流水线的取消操作人 | 展示「由 yyy 取消」（父构建非用户取消时为 `null`） |

> ⚠️ `projectId` 可能为空：历史数据兼容需要，前端**拼接父构建链接前必须判空**，为空则不展示跳转入口。

### 3.4 `SubPipelineInfo` / `DependOnJobInfo`（嵌套跳转）

结构类似，均携带 `projectId` + `pipelineId` + `buildId` + 名称/构建号，用于跳转到对应构建详情页。**跳转链接同样必须使用各自的 `projectId`**。

> `SubPipelineInfo.pipelineName` / `buildNum` 自本次改动起随子流水线启动一并记录，
> 存量构建可能为空，此时位置的 `reason` 退化为「子流水线执行失败」。

### 3.5 `endType` 枚举全集

| 大类 | 枚举值 | 中文标签（`endTypeDesc`） |
|------|--------|--------------------------|
| CANCEL | `CANCEL_USER` | 用户取消 |
| CANCEL | `CANCEL_SYSTEM` | 系统取消（含服务重启、排队满、并发互斥、构建机 Agent 失联等，具体成因见 `reason`） |
| CANCEL | `CANCEL_PARENT_PIPELINE` | 父流水线取消 |
| FAIL | `FAIL_EXEC` | 执行失败 |
| FAIL | `FAIL_QUALITY` | 质量红线未达标 |
| FAIL | `FAIL_REVIEW` | 人工审核驳回 |
| FAIL | `FAIL_SUB_PIPELINE` | 子流水线失败 |
| FAIL | `FAIL_FAST_KILL` | 因失败即停被终止（**只作为位置级类型出现**，是同阶段其他 Job 失败的连带影响） |
| FAIL | `FAIL_MULTIPLE` | 多类失败 |
| TIMEOUT | `TIMEOUT_JOB` | Job 超时（**只作为位置级类型出现**，构建级降级为取消/失败类） |
| TIMEOUT | `TIMEOUT_STEP` | 步骤超时（**只作为位置级类型出现**，构建级降级为 `FAIL_EXEC`） |
| TIMEOUT | `TIMEOUT_QUEUE` | 排队超时（唯一的构建级超时类型，构建状态本身就是 `QUEUE_TIMEOUT`） |
| SUCCESS | `SUCCESS` | 执行成功 |
| SUCCESS | `SUCCESS_STAGE_ABORT` | 已驳回（阶段准入被驳回，构建按阶段成功结束） |
| SUCCESS | `SUCCESS_STAGE_REVIEWING` | 审核中（阶段准入等待人工审核，构建挂起） |

> 原 `TIMEOUT_HEARTBEAT` 已下线：构建机 Agent 失联对用户而言是构建被系统中断，
> 现统一归为 `CANCEL_SYSTEM`，成因由 `reason`（「构建机Agent异常退出，与节点失联」）表达。

---

## 四、前端渲染规则（重点）

### 4.1 卡片样式由 `endCategory` 决定

```
endCategory === "CANCEL"     → 取消卡片
endCategory === "FAIL"       → 失败卡片
endCategory === "TIMEOUT"    → 超时卡片（构建级仅排队超时会出现）
endCategory === "SUCCESS"    → 成功卡片
```

**不要**用 `endType` 去推断大类，`endCategory` 已是后端算好的、与 `endType` 恒定同类的结果，
且与 `ModelRecord.status` 所属大类一致。

具体配色以设计稿为准（取消/阶段成功为橙色系、失败/超时为红色系），后端只提供归类不约束样式。
注意设计稿中同为「阶段成功」的两种子类型强调色不同：`SUCCESS_STAGE_ABORT`（已驳回）为橙色，
`SUCCESS_STAGE_REVIEWING`（审核中）为蓝色，需按 `endType` 区分。

### 4.2 卡片内的「原因标签」用 `endTypeDesc`

直接展示 `endTypeDesc`（已是中文/英文/日文翻译好的），例如「系统取消」「用户取消」「质量红线未达标」「人工审核驳回」「子流水线失败」。

### 4.3 三种「成功」的区分

| `endType` | 卡片标题（沿用状态标签） | 卡片内 | 位置区块 |
|-----------|------------------------|--------|----------|
| `SUCCESS` | 执行成功 | 只有总耗时 | 无 |
| `SUCCESS_STAGE_ABORT` | 阶段成功 | 「阶段准入」+ `endTypeDesc`（已驳回）+ `reason`（驳回意见）+ 结束时间/总耗时 | 1 条 Stage 级「驳回位置」，带 `reviewGroupSeq`/`reviewGroupName`/`operator` |
| `SUCCESS_STAGE_REVIEWING` | 阶段成功 | 「阶段准入」+ `endTypeDesc`（审核中）+ 开始时间 + `waitCostTime`（已等待） | 1 条 Stage 级「审核位置」，带 `reviewGroupSeq`/`reviewGroupName`/`reviewers` |

> `SUCCESS_STAGE_REVIEWING` 表示构建**并未真正结束**，只是挂起在审核环节，
> 因此不要展示「总耗时」，而是展示「已等待」（`waitCostTime`，每次拉取实时计算）。

### 4.4 失败类的原因逐位置展示

失败类（`endCategory === "FAIL"`）构建级 `reason` 恒为 `null`——同一次构建可能有多个失败位置，
各自的原因（驳回意见、红线指标、子流水线名）不可能用一句话概括。前端应：

1. 卡片内只展示「失败类型」= 构建级 `endTypeDesc`
2. 遍历 `positions`，每条位置下方补一行原因（字段对应见下表）
3. `FAIL_MULTIPLE` 时每条位置的 `endTypeDesc` 各不相同，逐条展示，不要用构建级那一个

多个人工审核驳回时，每条驳回位置展示自己的 `reason`（该位置的驳回意见），
不会再出现「构建级只显示其中一条意见」的冲突。

**位置原因行的字段对应**（设计稿中红色部分为成因标签、其后为原因内容）：

| 位置 `endType` | 红色部分 | 「：」之后的部分 | 效果 |
|---------------|---------|----------------|------|
| `FAIL_QUALITY` | `endTypeDesc` | `reason` | 质量红线未达标：代码坏味道 (code smells) 超过阈值 50 |
| `FAIL_REVIEW` | `endTypeDesc` | `reason`（= 驳回意见） | 人工审核驳回：鉴权改动风险偏高，需补充验证 |
| `FAIL_SUB_PIPELINE` | `endTypeDesc` | `reason`（= 子流水线名） | 子流水线失败：子流水线名 |
| `FAIL_FAST_KILL` | `endTypeDesc` | `reason` | 因失败即停被终止：已启用 Fastkill，因「job 名称」失败被终止 |
| `FAIL_EXEC` | `错误码 {errorCode}` 整行 | 无（`reason` 为 `null`） | 错误码 2123020 |

即：**红色 = `position.endTypeDesc`，冒号后 = `position.reason`**；`FAIL_EXEC` 且 `reason` 为空时
退化为整行红色的「错误码 `position.errorCode`」。没有 `errorCode` 时不要渲染空的错误码框，改展示 `reason`。
`errorMsg` 是插件原始报错，通常很长，建议只在 tooltip 或展开态里用，不放在原因行。

FastKill 与其它失败并存时，构建级 `endType` 为 `FAIL_MULTIPLE`（失败类型「多类失败」），
必须按位置各自的 `endTypeDesc` 渲染，**不要**把 FastKill 位置画成「错误码 2199010」。
定位不到引发终止的 Job 时 `reason` 为「已启用 Fastkill，因同阶段其他 Job 失败被终止」，
**不要退化去展示 `errorMsg`**——被强制终止的插件给不出真实原因，那里只有「Force Terminate!」这类引擎占位文案。

子流水线的构建号不在 `reason` 里（与设计稿一致），需要展示 `#88` 时从
`position.subPipelineInfo.buildNum` 取，跳转仍用 `subPipelineInfo` 的 `projectId`/`pipelineId`/`buildId`。

### 4.5 位置列表渲染

- 遍历 `positions`（最多 50 条，已被后端截断）
- 每个位置展示：`componentPath`（路径）+ `statusAtEndDesc`（状态）+ `endTypeDesc`（成因）+ `reason`（原因详情）
- 点击位置 → 用锚点字段跳转到对应 Stage/Job/插件（见第六节）
- `positionCount` 可单独用于展示「共 N 个位置受影响」

### 4.6 兜底与空值

- `buildEndInfo` 为 `null` → 不渲染终态详情卡片（走原有逻辑或展示「运行中」）
- `positions` 为 `null` 或空 → 不渲染位置区块（后端用 `withPositions` 统一保证空时不渲染）
- `operator` / `parentPipelineInfo` / `subPipelineInfo` 为 `null` → 对应区块不展示
- 构建级 / 位置级 `reason` 为 `null` → 不渲染对应的原因行
- `totalCostTime` 为 `null` → 不展示总耗时（构建从未启动）

---

## 五、字段取值语义速记

| 场景 | `operator` | 构建级 `reason` | 位置级 `endType` / `reason` | 备注 |
|------|-----------|----------------|----------------------------|------|
| 用户手动取消 | 有值（取消人） | 「N 处在途均已停止」 | 无 / 无 | N = `positionCount`，与卡片位置数一致（读取时会补上取消落库之后才置为取消的插件） |
| 系统取消（含 Agent 失联） | `null` | 有值 | 视情况 / 无 | 系统无真实操作人 |
| 父流水线级联取消 | `null` | 有值 | 无 / 无 | 操作人在 `parentPipelineInfo.operator` |
| Job / 步骤超时 | `null` | 有值（含时限） | 视情况 / 无 | 位置展示错误码 |
| 质量红线未达标 | `null` | **`null`** | `FAIL_QUALITY` / 指标详情 | 指标按控制点插件匹配到位置 |
| 人工审核驳回 | 位置级 `operator` 有值 | **`null`** | `FAIL_REVIEW` / 该位置的驳回意见 | 多个驳回各自独立 |
| 子流水线失败 | `null` | **`null`** | `FAIL_SUB_PIPELINE` / 子流水线名 | 构建号与跳转信息在 `subPipelineInfo` |
| FastKill 连带终止 | `null` | **`null`** | `FAIL_FAST_KILL` / 已启用 Fastkill，因「JobX」失败被终止 | 与其它失败并存时构建级为 `FAIL_MULTIPLE`；Job 名定位不到时 `reason` 仍有兜底文案 |
| 普通执行失败 | `null` | **`null`** | `FAIL_EXEC` / 无 | 错误码 `errorCode`、详情 `errorMsg` |
| 阶段准入驳回 | `null` | 驳回意见 | `SUCCESS_STAGE_ABORT` / 无 | 审核组信息在位置上 |
| 阶段准入审核中 | `null` | `null` | `SUCCESS_STAGE_REVIEWING` / 无 | 待审核人在位置 `reviewers` |

---

## 六、跳转链接拼接规则（避坑重点）

### 6.1 定位日志（插件级位置）

需要 5 个参数：`buildId` + `taskId` + `containerHashId` + `executeCount` + `stepId`

| 参数 | 来源 |
|------|------|
| `buildId` | 当前构建 ID |
| `executeCount` | `ModelRecord.executeCount`（构建级，只有一个） |
| `taskId` | `EndPosition.taskId` |
| `containerHashId` | `EndPosition.containerHashId`（优先运行时值） |
| `stepId` | `EndPosition.stepId` |

> 只有插件级位置（`taskId` 有值）才支持定位日志。`containerHashId` / `stepId` 为空时不展示日志入口。

### 6.2 跳转到具体组件（Stage / Job / 插件）

用 `stageId` + `containerId` + `taskId` 锚点定位到流水线编排对应位置（同项目内）。

### 6.3 跳转到父流水线 / 子流水线 / 依赖 Job

- **父流水线**：必须用 `parentPipelineInfo.projectId`（可能跨项目！）+ `pipelineId` + `buildId`
- **子流水线**：用 `subPipelineInfo.projectId` + `pipelineId` + `buildId`
- **依赖 Job**：用 `dependOnJobs[].projectId` + `pipelineId` + `buildId` + `executeCount`

> ⚠️ 所有跨流水线跳转，**项目 ID 一律取对应对象自己的 `projectId`，不能用当前页面项目 ID**，否则跨项目时链接会 404。

---

## 七、完整示例（直接对照渲染）

### 示例 A：用户手动取消（带 3 个受影响位置，含依赖等待）

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
      "statusAtEnd": "RUNNING", "statusAtEndDesc": "运行中",
      "stageId": "stage-3", "containerId": "5", "taskId": "e-abc123"
    },
    {
      "position": "3-2",
      "componentPath": "质量检查/代码扫描",
      "statusAtEnd": "PREPARE_ENV", "statusAtEndDesc": "准备环境",
      "stageId": "stage-3", "containerId": "6"
    },
    {
      "position": "3-3",
      "componentPath": "质量检查/通知卡片",
      "statusAtEnd": "DEPENDENT_WAITING", "statusAtEndDesc": "等待依赖Job",
      "stageId": "stage-3", "containerId": "7",
      "dependOnJobs": [
        { "jobName": "单元测试", "projectId": "proj-xxx", "pipelineId": "p-xxx",
          "buildId": "b-xxx", "executeCount": 1, "stageId": "stage-3", "containerId": "5" }
      ]
    }
  ],
  "positionCount": 3,
  "totalCostTime": 300000
}
```

渲染要点：取消卡片 + 原因标签「用户取消」+ 原因「3 处在途均已停止」+ 「由 username 取消」+ 3 个位置列表（第 3 个带「等待依赖 Job：单元测试」可跳转）。

### 示例 B：父流水线级联取消

```json
{
  "endType": "CANCEL_PARENT_PIPELINE",
  "endCategory": "CANCEL",
  "reason": "本次构建随父流水线取消而终止",
  "endTypeDesc": "父流水线取消",
  "reasonCode": "bkBuildCancelParentPipeline",
  "endTime": 1718694000000,
  "positions": [
    { "position": "2-1-3", "componentPath": "构建/编译Job/执行编译",
      "statusAtEnd": "RUNNING", "statusAtEndDesc": "运行中",
      "stageId": "stage-2", "containerId": "3", "taskId": "e-def456" }
  ],
  "positionCount": 1,
  "totalCostTime": 300000,
  "parentPipelineInfo": {
    "projectId": "demo-parent-project", "pipelineId": "p-xxx",
    "pipelineName": "Android 发布主流水线", "buildId": "b-xxx",
    "buildNum": 130, "operator": "fayewang"
  }
}
```

渲染要点：取消卡片 + 「父流水线取消」+ 父流水线区块「Android 发布主流水线 #130 由 fayewang 取消」（点击跳转到 `demo-parent-project` 项目下的父构建）。

### 示例 C：多类失败

```json
{
  "endType": "FAIL_MULTIPLE",
  "endCategory": "FAIL",
  "reason": null,
  "endTypeDesc": "多类失败",
  "positions": [
    { "position": "3-1-1", "componentPath": "质量检查/单元测试/执行单测",
      "statusAtEnd": "FAILED", "endType": "FAIL_EXEC", "endTypeDesc": "执行失败",
      "stageId": "stage-3", "containerId": "5", "taskId": "e-ut-1",
      "errorType": 1, "errorCode": 2123020, "errorMsg": "unit test failed" },
    { "position": "3-2-1", "componentPath": "质量检查/代码扫描/质量红线检查",
      "statusAtEnd": "QUALITY_CHECK_FAIL", "endType": "FAIL_QUALITY", "endTypeDesc": "质量红线未达标",
      "reason": "代码坏味道 (code smells) 超过阈值 50",
      "stageId": "stage-3", "containerId": "6", "taskId": "e-quality-1",
      "errorType": 1, "errorCode": 2199007 },
    { "position": "3-2-2", "componentPath": "质量检查/通知卡片/AI代码检查",
      "statusAtEnd": "CANCELED", "endType": "FAIL_FAST_KILL", "endTypeDesc": "因失败即停被终止",
      "reason": "已启用 Fastkill，因「单元测试」失败被终止",
      "stageId": "stage-3", "containerId": "6", "taskId": "e-ai-1",
      "errorType": 1, "errorCode": 2199010 },
    { "position": "3-3-1", "componentPath": "质量检查/发布/调用发布子流水线",
      "statusAtEnd": "FAILED", "endType": "FAIL_SUB_PIPELINE", "endTypeDesc": "子流水线失败",
      "reason": "Android 发布子流水线",
      "stageId": "stage-3", "containerId": "7", "taskId": "e-sub-1",
      "subPipelineInfo": { "projectId": "demo-sub-project", "pipelineId": "p-sub-xxx",
        "pipelineName": "Android 发布子流水线", "buildId": "b-sub-xxx", "buildNum": 88 } }
  ],
  "positionCount": 4
}
```

渲染要点：失败卡片 + 「多类失败」标题（构建级无原因行）+ **遍历 4 个位置，每个位置展示各自的成因标签与原因行**；第 4 个位置带子构建跳转入口。

### 示例 D：构建机 Agent 失联（系统取消）

```json
{
  "endType": "CANCEL_SYSTEM",
  "endCategory": "CANCEL",
  "reason": "构建机Agent异常退出，与节点失联",
  "endTypeDesc": "系统取消",
  "reasonCode": "bkBuildCancelSystemHeartbeatTimeout",
  "endTime": 1718694000000,
  "positions": [
    { "position": "2-1", "componentPath": "编译/构建Job",
      "statusAtEnd": "RUNNING", "statusAtEndDesc": "运行中",
      "stageId": "stage-2", "containerId": "3" }
  ],
  "positionCount": 1
}
```

渲染要点：取消卡片 + 类型标签「系统取消」+ 原因「构建机 Agent 异常退出，与节点失联」+ 1 个被终止位置。

### 示例 E：阶段准入审核中（构建挂起，非真正结束）

```json
{
  "endType": "SUCCESS_STAGE_REVIEWING",
  "endCategory": "SUCCESS",
  "endTypeDesc": "审核中",
  "reason": null,
  "waitCostTime": 218000,
  "positions": [
    { "position": "4", "componentPath": "部署",
      "statusAtEnd": "REVIEWING", "statusAtEndDesc": "审核中",
      "endType": "SUCCESS_STAGE_REVIEWING", "endTypeDesc": "审核中",
      "stageId": "stage-4", "containerId": "",
      "reviewGroupSeq": 2, "reviewGroupName": "运维审核组",
      "reviewers": ["fayewang", "zhangsan"] }
  ],
  "positionCount": 1
}
```

渲染要点：标题沿用状态标签「阶段成功」+ 卡片内「阶段准入 审核中」+ 「已等待 3 分 38 秒」（`waitCostTime`）+ 1 条审核位置「2 级：运维审核组 / fayewang, zhangsan 审核」。**不展示总耗时**。

### 更多示例

质量红线未达标（`FAIL_QUALITY`）、人工审核驳回（`FAIL_REVIEW`）、步骤超时（位置级 `TIMEOUT_STEP`）、普通成功（`SUCCESS`）、阶段准入驳回（`SUCCESS_STAGE_ABORT`）等完整 JSON 见配套设计文档「第五节 终态信息示例」。

---

## 八、前端适配 Checklist

- [ ] `ModelRecord.buildEndInfo` 判空后再渲染终态详情
- [ ] 卡片样式**只**用 `endCategory` 决定（CANCEL/FAIL/TIMEOUT/SUCCESS）
- [ ] 卡片大类与页面构建状态标签同类：构建是失败就渲染失败卡片，不要因为历史 JSON 里见过 `CANCEL_USER` 就画取消卡
- [ ] 位置状态用 `statusAtEndDesc`（已翻译），与插件详情状态一致；不要把取消瞬间的 `PAUSE` 当成终态
- [ ] 成因标签用 `endTypeDesc`（已翻译），不要用 `endType` 硬编中文
- [ ] `reason` 与 `reasonCode` 二选一展示：`reasonCode` 有值时以国际化文案（`reason`）为准
- [ ] 失败类不读构建级 `reason`（恒为 `null`），改为逐位置读 `position.reason`
- [ ] 位置级驳回意见读 `position.reason`，不要再单独渲染 `reviewSuggest`（否则会重复）
- [ ] `SUCCESS_STAGE_REVIEWING` 展示 `waitCostTime`（已等待），不展示总耗时
- [ ] 遍历 `positions` 渲染位置列表；多类失败时每个位置用各自的 `position.endTypeDesc`
- [ ] 定位日志参数凑齐 `buildId + taskId + containerHashId + executeCount + stepId`
- [ ] 所有跨流水线跳转（父/子/依赖 Job）一律用对应对象的 `projectId`
- [ ] `parentPipelineInfo.projectId` 为空时不展示父构建跳转入口
- [ ] `totalCostTime` 为毫秒，按需格式化为「N 分钟」；为 `null` 时不展示
- [ ] `operator` / `subPipelineInfo` / `errorCode` 等按需判空展示
- [ ] 新增终态子类型后端会自动归入现有卡片样式，**前端一般无需改动**

---

## 九、常见问题（FAQ）

**Q：`endCategory` 和 `ModelRecord.status` 的关系？**
A：同类。后端按构建最终状态所属大类选择 `endType`，所以卡片大类与标题状态标签始终一致。像 Job 执行超时走终止链路、构建以「已取消」收尾的情况，卡片是取消卡片，「超时」这个成因体现在 `reason`（「所属 Job 超过执行时限 15m，步骤被终止」）和位置级 `endType`（`TIMEOUT_JOB`）上。若构建状态是失败，即使取消链路先写过 `CANCEL_USER`，返回的也是失败卡片，不会再出「已取消」。

**Q：暂停插件上点了取消，为什么卡片是失败不是取消？**
A：取消发生时插件可能还是 `PAUSE`，但构建最终状态以收尾结果为准。常见路径是暂停插件落成 `FAILED`、同 Job 里的人工审核落成 `REVIEW_ABORT`（失败态），构建因此是失败。卡片跟构建状态走：`endCategory = FAIL`，位置分别展示 `FAILED` / `REVIEW_ABORT`，而不是取消时的 `PAUSE`。两类失败并存时构建级 `endType` 为 `FAIL_MULTIPLE`。

**Q：为什么有的 `buildEndInfo` 是 `null`？**
A：排队中/执行中（且未挂起在阶段准入审核）没有终态详情。已结束的失败/取消/超时会落库或按模型合成；普通成功虽不落库，读取时也会合成完整结构（仅 `positions` 为空）。

**Q：执行前暂停插件点了终止，流水线变成「已取消」，为什么以前没有卡片？**
A：这条路径不走用户取消入口，构建以 `CANCELED` 收尾。现在写入侧会落下 `CANCEL_USER`，读取侧也会按模型里仍停在取消/暂停的插件补齐 `positions`。位置 `statusAtEnd` 取插件当前终态（通常是 `CANCELED`），不要再展示取消瞬间的 `PAUSE`。

**Q：Job 互斥组抢锁失败为什么以前没有 `buildEndInfo`？**
A：互斥失败不写插件 `errorType`，任务从未启动。现在失败卡片会给出 Job 级位置，原因是「互斥组未开启排队 / 排队失败」。若容器落成取消态，也会按互斥组归因，而不会整张卡片缺失。

**Q：构建刚结束时 WebSocket 推送里为什么没有 `buildEndInfo`？**
A：已修复，三个成因：一是终态详情在构建结束的记录更新之后才落库，此前只推送过一次不含详情的记录，现在落库成功后会再推一次记录变更；二是普通成功与阶段准入审核中这两种读取时合成的终态，原本按滞后的构建历史表状态判定，推送时合成不出来，现已改为与状态标签同源；三是阶段挂起时构建状态的改写晚于推送，现在只要构建未结束就直接按阶段的审核态合成，不再以构建状态为前提。前端按最后一次推送渲染即可，无需手动刷新。

**Q：人工审核「同意」之后卡片怎么变？**
A：分两种。审核插件（Job 内的人工审核步骤）同意后构建继续运行，此时没有终态，`buildEndInfo` 为 `null`，卡片应隐藏；阶段准入审核同意后若还有下一个审核组，仍是 `SUCCESS_STAGE_REVIEWING`，卡片内容切到下一组的组名与审核人。驳回则分别是构建失败（`FAIL_REVIEW`）与阶段成功（`SUCCESS_STAGE_ABORT`），都是落库的终态。

**Q：开启 FastKill 的阶段里，人工审核步骤为什么标成「因失败即停被终止」而不是「人工审核驳回」？**
A：那条位置是在等待审核时被 FastKill 连带终止的，不是被人驳回的。产品口径是因 FastKill 结束的位置统一标记「因失败即停被终止」，所以卡在 Set up job 的开机任务也不再显示「执行失败」。真正被人驳回的审核步骤仍是 `FAIL_REVIEW`，且带驳回人 `operator` 与驳回意见 `reason`。

**Q：`reason` 和 `reasonCode` 该展示哪个？**
A：展示 `reason`。读取时若 `reasonCode` 有值，后端已用国际化文案覆盖 `reason`，所以 `reason` 始终是最新的、按读者语言翻译好的文本。`reasonCode` 仅作兜底/调试。

**Q：位置列表会不会很长？失败原因有没有长度限制？**
A：后端最多保留 50 条。卡片原因行 `reason` 会压缩空白并截断至 **200** 字符（超长脚本堆栈只在插件日志里看完整内容）。`errorMsg` 截断至 512 字符，只给 tooltip / 展开态用，**不要**把 `errorMsg` 铺在卡片上，否则脚本失败仍会把卡片撑开。

**Q：一个 Job 执行超时，构建还在跑，为什么不能出「已取消」卡片？**
A：Job 超时只终止这一个 Job，构建状态仍是运行中。超时分钟数会先记在记录里，但运行中 `buildEndInfo` 返回 `null`，终态卡片不展示。等构建真正结束后，卡片大类跟随最终状态；若最终是失败，这条 Job 超时原因可以留在失败卡片上。

**Q：执行前暂停的插件被终止，卡片状态为什么写成「取消」而编排图是「暂停/终止」？**
A：插件落库状态可能是 `CANCELED` / `PAUSE` / `TERMINATE`。读取时若该步骤配置了执行前暂停，位置 `statusAtEndDesc` 会改成「暂停/终止」，与编排图对齐；普通被取消的步骤仍是「取消」。

**Q：父流水线跳转链接用哪个项目 ID？**
A：必须用 `parentPipelineInfo.projectId`。子流水线插件支持跨项目调用，父子可能不在同一项目，用当前页面项目 ID 会 404。
