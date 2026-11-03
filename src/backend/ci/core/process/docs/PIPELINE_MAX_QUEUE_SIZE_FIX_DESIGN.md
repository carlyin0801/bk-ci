# 流水线最大排队数量（maxQueueSize）不生效修复技术方案

- 关联 Issue：[#13499](https://github.com/TencentBlueKing/bk-ci/issues/13499)
- 影响模块：`process` / `biz-base`、`biz-engine`
- 改动文件：
  - `biz-base/.../engine/interceptor/QueueInterceptor.kt`（统计口径 + 淘汰收敛）
  - `biz-base/.../engine/service/PipelineConcurrencyQueueTrimService.kt`（**新增**，对账式收敛）
  - `biz-base/.../engine/dao/PipelineBuildDao.kt`（新增按排队时间列出候选构建的查询）
  - `biz-base/.../engine/service/PipelineRuntimeExtService.kt`（DAO 透传）
  - `biz-engine/.../engine/control/BuildStartControl.kt`（在现有并发组锁内接入收敛）

## 一、问题现象

流水线并发配置中「最大排队数量」配置为 `20`，但实际排队构建数量堆积到几百个，配置形同失效。

复现配置（GROUP_LOCK 场景）：

| 配置项 | 值 |
|--------|-----|
| 运行方式 | GROUP_LOCK（并发组串行） |
| 分组名称 | `${{ci.pipeline_id}}` |
| 取消正在运行的构建（`concurrencyCancelInProgress`） | 否 |
| 最大排队数量（`maxQueueSize`） | 20 |
| 最大排队时长 | 1440 分钟 |

## 二、对 Issue 原始结论的复核

Issue #13499 原始描述认为：`queueCount` 只统计 `QUEUE` 漏掉了 `QUEUE_CACHE`，
而「大量构建在领取-回退循环里，相当一部分会落在 QUEUE_CACHE 状态」，因此放大到几百个。

**结论：漏统计的事实成立，但「放大到几百个」的归因不成立。**

`QUEUE_CACHE` 的数量存在硬性上界，不可能达到几百：

```kotlin
// PipelineBuildDao.getOneConcurrencyQueueBuild
select.orderBy(QUEUE_TIME.asc(), PIPELINE_ID, BUILD_NUM.asc()).limit(1)
```

`popNextConcurrencyGroupQueueCanPend2Start` 每次只 `limit(1)` 领取一个构建，
且在 `BuildStartControl.checkGroupType` 中是持 `ConcurrencyGroupLock` 串行执行的。因此：

| 调用方 | 领取范围 | QUEUE_CACHE 上界 |
|--------|----------|------------------|
| `BuildStartControl.checkGroupType` | 并发组级（`pipelineId = null`） | 每并发组 1 个 |
| `BuildMonitorControl.monitorQueueBuild` | 流水线级（传 `pipelineId`） | 每流水线 1 个 |

即同一流水线在同一并发组内处于 `QUEUE_CACHE` 的构建约为 1 个，漏统计量 ≈ 1，
只会让淘汰时机推迟一个名额，不可能造成几百的堆积。

## 三、真正的根因

几百个堆积是**两个缺陷叠加**的结果，其中主因不在 `QueueInterceptor` 的统计口径上。

### 3.0 量化：为什么恰好是"几百个"

请求在"读取排队数量"到"插入排队记录"之间的窗口内，同一流水线的并发量被
`SimpleRateLimiter` 的令牌桶兜住，桶大小即 `maxConRunningQueueSize`：

```kotlin
// SimpleRateLimiter.acquire —— 本质就是 Redis INCR + TTL 的信号量
return if ((redisOperation.increment(lockKey, 1) ?: 1) <= bucketSize) { ... }
```

`maxConRunningQueueSize` 经 YAML/复制路径创建时默认取
`PIPELINE_SETTING_MAX_CON_QUEUE_SIZE_MAX = 200`（手工配置默认 50、上限 200）。
因此单波突发最多灌进约 200 个，与现场观测的"好几百个"数量级吻合：
**队列并非无界增长，而是被顶在了 `maxConRunningQueueSize` 上，然后因为淘汰不收敛而长期驻留。**

### 3.1 主因一：满员判定与入队之间没有互斥（产生超限）

`PipelineBuildService.startPipeline` 中，读取排队数量的拦截器与真正写入排队记录的
`startBuild` 之间没有任何流水线级互斥锁：

```kotlin
val interceptResult = pipelineInterceptorChain.filter(InterceptData(...))  // 读 queueCount
if (interceptResult.isNotOk()) { throw ErrorCodeException(...) }
return pipelineRuntimeService.startBuild(fullModel = resource.model, context = context)  // 才插入 QUEUE 记录
```

唯一的前置保护 `simpleRateLimiter.acquire(bucketSize)` 是一个容量等于
`maxConRunningQueueSize` 的**信号量**，不是互斥锁；且 `frequencyLimit = false`
或渠道命中 `NO_LIMIT_CHANNEL` 时会被整体跳过。

对比参照：同文件中 stage 审核触发那条路径反而加了 `PipelineBuildRunLock`，
说明这里缺锁是遗漏而非有意设计。

后果：webhook 风暴时几百个并发触发读到同一个偏小的 `queueCount`，全部通过满员判定并入队。

### 3.2 主因二：淘汰逻辑不收敛（超限后永不回落）

这是「只进不出」的直接原因。原实现在满员时**固定只淘汰 1 个、同时放行 1 个**，净变化为 0：

```kotlin
queueCount >= task.maxQueueSize -> {
    outQueueCancelByGroup(...)            // 淘汰组内最早排队的 1 个
    Response(data = BuildStatus.RUNNING)  // 照样放行当前新构建
}
```

队列一旦被 3.1 的竞态冲高到 500，就会永久停在 500 附近，`maxQueueSize = 20` 再也无法把它拉回来。

> 需要说明：`maxQueueSize` 的历史语义是「排队队列的容量」而非「新构建的准入门槛」，
> 满员时挤掉最早排队者并放行新构建是**有意设计**（保证最新代码优先被构建），本方案保留该语义。

### 3.3 次因：排队数量统计漏掉 QUEUE_CACHE

即 Issue 描述的问题，量级 ≈ 1。它还带来一个口径不一致的副作用：
统计口径（仅 `QUEUE`）小于淘汰口径（`QUEUE + QUEUE_CACHE`），
当被淘汰的正好是 `QUEUE_CACHE` 构建时，`queueCount` 不会下降，下一次检查会重复触发淘汰。

## 四、方案设计

改动集中在 `QueueInterceptor.kt`，不涉及 DB 变更、不涉及接口变更、无需数据迁移。

### 4.1 统计口径对齐（对应 Issue 要求的措施）

抽出 `countGroupBuild` 统一统计逻辑，排队数量改为同时统计 `QUEUE` 与 `QUEUE_CACHE`：

```kotlin
private val QUEUE_STATUS_LIST = listOf(BuildStatus.QUEUE, BuildStatus.QUEUE_CACHE)
```

- `QUEUE`：等待被领取
- `QUEUE_CACHE`：已被领取、等待启动的中间态

两者都尚未开始执行，都应计入排队数量，与 `PipelineBuildDao.getOneConcurrencyQueueBuild`
（淘汰时选取构建的查询条件）口径完全一致。

`runningCount` 侧仅做等价重构（复用同一个 helper），**行为与改动前完全一致**。

### 4.2 淘汰数量按超出量收敛

```kotlin
val outQueueSize = min(queueCount - task.maxQueueSize + 1, MAX_OUT_QUEUE_SIZE_PER_CHECK)
```

| 队列状态 | 淘汰数量 | 与历史行为对比 |
|----------|----------|----------------|
| 正好满员（`queueCount == maxQueueSize`） | 1 | **完全一致** |
| 已超限（如 500 > 20） | `min(超出量+1, 50)` | 新增收敛能力 |

放行当前构建后排队数量向 `maxQueueSize` 回落，存量积压会随后续触发自动收敛，
不再出现「判定满员却永不回落」。

### 4.3 三处风险防护

1. **单次淘汰上限 50**（`MAX_OUT_QUEUE_SIZE_PER_CHECK`）：避免一次产生几百个取消事件冲击 MQ。
2. **锁内只做出队**：`ConcurrencyGroupLock` TTL 为 30s，批量淘汰时锁内仅执行 DB 状态更新
   （50 次约 100~500ms），日志打印与取消事件派发移到锁外，避免长时间占锁阻塞构建启动。
3. **重复出队保护**：`popNextQueueBuildInfo` / `popNextConcurrencyGroupQueueCanPend2Start`
   都忽略了 `updateStatus` 的 CAS 返回值，状态更新失败时会重复取到同一个构建；
   循环中检测到重复 `buildId` 即终止，避免重复发送取消事件。

### 4.4 关键决策：为什么**不**叠加 #8143 空并发组兼容统计

方案迭代过程中曾尝试在 `countGroupBuild` 中叠加 `getBuildInfoListByConcurrencyGroupNull`
（兼容 `CONCURRENCY_GROUP IS NULL` 的历史构建），与
`RunLockInterceptor`、`BuildStartControl.checkGroupType` 及本方法另一分支保持一致。
**该做法已被否决并回退**，原因是它会重新引入无界增长：

- 淘汰依赖 `getOneConcurrencyQueueBuild`，其条件为 `CONCURRENCY_GROUP = 并发组`，
  **永远取不到并发组为空的构建**；
- 若把这类构建计入 `queueCount`，例如存在 30 个空并发组的历史排队构建、`maxQueueSize = 20`，
  则算出需淘汰 11 个，但 pop 立即返回 `null`、实际淘汰 0 个，新构建照样放行 → 队列继续无界增长。

**统计口径必须与淘汰口径严格一致**，这是本修复能够收敛的前提。
代价是空并发组的历史排队构建仍不计入排队数量（与改动前一致，属已知遗留），
如需覆盖，须同步改造淘汰侧查询，不在本次范围内。

顺带收益：回退后 GROUP_LOCK 热路径每次构建启动少了 2 次额外 DB 查询。

### 4.5 对账式收敛：不阻塞请求也能把队列限住（针对 3.1 主因一）

#### 为什么不在请求路径加互斥锁

延迟不是来自"串行化检查"本身，而是来自**把重活圈进了临界区**。
若照 stage 审核路径用 `PipelineBuildRunLock` 包住 `filter + startBuild`，
锁内要跑完模型解析、变量落库、大事务与事件派发，单次持锁上百毫秒；
而 `RedisLock.lock()` 是 `while(true) + Thread.sleep(10)` 的**无限阻塞**，
200 并发即数十秒串行等待，直接打成 HTTP 超时。故否决。

#### 为什么"补上并发在途数"也不行

曾考虑用 Redis 计数器把"在途未入库的构建"补进 `queueCount`。
但 `maxQueueSize` 是软限制、判定结果永远是放行，
补上在途数只会让淘汰更激进，**并不能阻止入队**——200 并发照样全进，
只是把存量队列清得更干净。此路不通。

#### 采用方案：把限额的强制执行移到串行的对账时机

不在快路径上跟竞态较劲，而是在**记录已经落库之后**、由天然串行的位置把队列裁剪回
`maxQueueSize`。此时读到的是真实数量，**并发多大都能收敛**。

落点选在 `BuildStartControl.checkGroupType`：它本来就持有 `ConcurrencyGroupLock`、
手上就有 `setting`（含 `maxQueueSize`），且只对队头构建执行、每轮一次。
排队构建由 `BuildMonitorControl.monitorQueueBuild` 周期性驱动
（结尾无条件 re-dispatch 监控事件），因此收敛会持续发生，
**突发后即使没有任何新触发也能回落**——这同时解决了 4.2 "依赖后续触发"的局限。

| | 阻塞式互斥锁 | 对账式收敛（采用） |
|---|---|---|
| 请求路径延迟 | 突发时数十秒 | **零** |
| 队列上界 | 严格 maxQueueSize | 短暂冲高后收敛 |
| 突发后静默期 | 无超限 | **也能收敛** |
| 改动位置 | 请求主链路 | 引擎热路径 |

需要明确：该方案**不"修复"竞态，而是让竞态变得无害**。
要在请求路径上做到严格不超限，只能改成硬拒绝（变更软限制语义）或接受阻塞。

#### 最关键的设计点：保护正在启动的构建

并发组内**队头构建既是"排队最久、按最新代码优先语义最该被淘汰"的，
也是"下一个要执行"的**。若不排除它，收敛逻辑会把即将启动的构建取消掉。
因此 `trimGroupQueue` 强制要求传入 `protectBuildId`，并且：

- **只淘汰 `QUEUE` 状态**：`QUEUE_CACHE` 已被领取、正在走启动流程，取消它会与启动逻辑相互打架；
- 统计仍按 `QUEUE + QUEUE_CACHE`（与拦截器一致），只是候选集更窄。

#### 安全护栏

| 护栏 | 作用 |
|------|------|
| `maxQueueSize <= 0` 直接返回 | 该语义是"不允许排队"，由启动拦截器把关；对账不介入，避免把整个队列清空 |
| `queueCount <= maxQueueSize` 直接返回 | 正常情况下完全 no-op，零额外写操作 |
| CAS 出队（`QUEUE → UNEXEC`） | 状态已被其他流程改变的构建自动跳过，不会误取消 |
| 单轮上限 50 | 避免一次产生过多取消事件、长时间占用并发组锁 |
| `runCatching` 包裹调用 | 对账属尽力而为，**任何异常都不能阻塞构建启动** |
| `tryLock` 失败即跳过 | 不阻塞任何线程，下一轮再来 |

收敛速度：单轮 50 个，按监控周期驱动，200 → 20 约需 4 轮。

#### 遗留的技术债

`QueueInterceptor` 的满员淘汰与本服务的对账淘汰各自实现了一份
"CAS 出队 + 打印红线日志 + 派发取消事件"。两者语义确有差异
（前者是"放行新构建前腾位置"，可淘汰 `QUEUE_CACHE`、无保护构建；
后者是"对既有记录强制收敛"，须保护 `QUEUE_CACHE` 与当前构建），
但重复代码有漂移风险。本次为控制回归面**未重构已上线的拦截器**，
建议后续统一到同一组件。

## 五、历史逻辑兼容性核对

| 历史行为 | 是否受影响 | 说明 |
|----------|------------|------|
| SINGLE / SINGLE_LOCK 的 `queueCount` 来源 | 否 | 仍取 `buildSummaryRecord.queueCount` |
| GROUP_LOCK 的 `runningCount` 统计 | 否 | helper 为等价重构，表达式语义不变 |
| `#7681` 排队数量按流水线过滤 | 否 | `count { it.first == pipelineId }` 保留 |
| 队列正好满员时淘汰 1 个 | 否 | `outQueueSize` 算出 1，行为一致 |
| 满员时放行新构建（软限制语义） | 否 | 仍返回 `Response(data = RUNNING)` |
| `cancelAllowed = false`（stage 审核）不执行淘汰 | 否 | 前置 `return` 保留 |
| `concurrencyCancelInProgress = true` 分支 | 否 | 未改动 |
| 拦截器链短路行为 | 否 | `Response(data = ...)` 的 `status` 为 `OK`，`isNotOk()` 为 false |
| 淘汰查询的索引访问路径 | 否 | 命中 `IDX_PROJECT_STATUS_GROUP(PROJECT_ID, STATUS, CONCURRENCY_GROUP)`，`STATUS IN` 增加一个值不改变访问路径 |

### 有意引入的行为变化（需知情）

1. **`maxQueueSize = 0` + GROUP_LOCK**：当头部构建处于 `QUEUE_CACHE` 时，
   新触发由「放行」变为返回 `ERROR_PIPELINE_QUEUE_FULL`。
   `maxQueueSize = 0` 语义是「不允许排队」，而 `QUEUE_CACHE` 构建确实占用了队列，
   因此新行为更正确。当前代码对 `maxQueueSize` 无最小值校验，该配置可达。
2. **队列已超限时单次淘汰数量** 由 1 变为最多 50。

## 六、验证与上线

### 6.1 编译验证

`:core:process:biz-base:compileKotlin` 与 `:core:process:biz-engine:compileKotlin`
**均已 BUILD SUCCESSFUL**，本方案全部改动（新增 DAO 查询、新增服务、
`BuildStartControl` 构造注入与调用）通过编译与类型检查。

过程中遇到两个与本改动无关的存量阻塞，记录备查：

1. `generateGenenrateJooq` 需要可用的 MySQL 连接生成 jOOQ 代码，
   `gradle.properties` 指向占位地址 `127.0.0.1:3306` 时该任务失败；
   配置为可用库后即通过。
2. 上游模块 `common-pipeline-yaml` 的 `StageTransfer.kt:293-295`
   存在存量 Kotlin smart cast 报错（`yaml2Stage(stage: StreamV3Stage, ...)` 内
   对 `stage.ifField` 智能转换失败），该文件未被本次改动修改，
   但会导致下游 `biz-base` / `biz-engine` 根本无法开始编译。
   验证时通过"引入局部变量"临时绕过以取得编译信号，**该临时改动已回退**，
   不包含在本方案交付内容中。建议单独修复。

**上线前仍需在 CI 执行一次完整构建确认。**

### 6.2 建议的功能验证场景

| 场景 | 预期 |
|------|------|
| GROUP_LOCK + `cancelCancelInProgress=false`，队列正好 20，触发第 21 个 | 淘汰最早 1 个，放行新构建，队列稳定 20 |
| 头部构建处于 QUEUE_CACHE 时触发新构建 | 排队数量统计包含该构建，淘汰时机不再被推迟 |
| 人工造出 100 个排队构建后连续触发 | 每次收敛最多 50 个，最终回落到 20 |
| `cancelAllowed=false`（stage 审核） | 不发生任何淘汰 |
| SINGLE_LOCK 常规排队 | 行为与改动前一致 |
| **人工造出 100 个排队构建后不再触发任何新构建** | 由监控周期驱动对账收敛，无需新触发即回落到 20 |
| **并发组内队头构建正在启动时触发收敛** | 队头构建不被取消，只淘汰其后的 `QUEUE` 构建 |
| **`maxQueueSize=0` 且存在排队构建** | 对账不介入、不清空队列，仍由启动拦截器拒绝新触发 |
| **对账逻辑抛异常（可注入模拟）** | 被 `runCatching` 捕获，构建正常启动不受影响 |

### 6.3 可观测性

淘汰与收敛均有日志，便于线上确认过程：

```
# QueueInterceptor 满员淘汰
[$pipelineId]|${task.buildId}|QUEUE_OUT_SIZE|$groupName|out=${outQueueBuilds.size}

# PipelineConcurrencyQueueTrimService 对账收敛（仅在实际淘汰时打印）
ENGINE|$pipelineId|QUEUE_TRIM|$concurrencyGroup|queue=$queueCount|max=$maxQueueSize|trimmed=$trimmed

# 对账异常（不影响构建启动）
ENGINE|$buildId|$source|$pipelineId|$concurrencyGroup|QUEUE_TRIM_FAIL
```

## 七、未覆盖的残留问题

以下问题**未在本次解决**：

1. **竞态本身仍然存在，只是被对账收敛变得无害**（见 4.5）。
   突发期间队列仍会短暂冲高到约 `maxConRunningQueueSize`，
   也仍会有一批构建「创建后立刻被取消」的抖动（DB 记录、MQ 事件、构建历史噪音）。
   要在请求路径上严格不超限，只能改成硬拒绝或接受阻塞，均为产品语义决策。

2. **空并发组历史排队构建仍不计入排队数量**（见 4.4），且对账收敛同样不覆盖
   （`listConcurrencyQueueBuilds` 条件为 `CONCURRENCY_GROUP = 并发组`）。

3. **淘汰可能选中已提交启动的 QUEUE_CACHE 构建**（存量竞态，仅存在于 `QueueInterceptor` 路径）。
   `checkGroupType` 在释放并发组锁之后才调用 `startLatestRunningBuild` 置为 RUNNING，
   该窗口内构建仍为 `QUEUE_CACHE`，可能被拦截器淘汰置为 `UNEXEC`。
   改动前后该风险等价。新增的对账收敛已通过"只淘汰 QUEUE + 保护当前构建"规避此问题。

4. **`QueueInterceptor` 与 `PipelineConcurrencyQueueTrimService` 存在重复的出队/取消代码**，
   建议后续统一（见 4.5 末）。

5. **存量积压的清理**：现在无需新触发也会由监控周期驱动收敛（单轮 50 个），
   但如需立即清零仍建议人工取消。

4. **淘汰可能选中已提交启动的 QUEUE_CACHE 构建**（存量竞态）。
   `checkGroupType` 在释放并发组锁之后才调用 `startLatestRunningBuild` 置为 RUNNING，
   该窗口内构建仍为 `QUEUE_CACHE`，可能被淘汰置为 `UNEXEC`。
   改动前后该风险等价（原实现淘汰的也正是最早的那个，即最可能处于 `QUEUE_CACHE` 的构建）。

## 八、配置侧建议排查

`queueCount` 只统计**当前流水线**的排队（`#7681` 原有语义），
而 GROUP_LOCK 串行化的是**整个并发组**。若并发组被多条流水线共享，
组内总排队量上界为 `流水线数 × maxQueueSize`，观感上同样会是几百个 —— 这属于配置量级，不是缺陷。

截图配置中分组名为 `${{ci.pipeline_id}}`，正常应由
`StartBuildContext.init` 的 `EnvUtils.parseEnv` 解析为各自的流水线 ID。
建议确认实际构建记录的 `CONCURRENCY_GROUP` 字段值未退化为未解析的字面量
`${{ci.pipeline_id}}`，否则全项目流水线会挤进同一个并发组。

## 附录：修正后的 Issue #13499 描述建议

> 原 Issue 的「措施」方向正确，但根因归纳有误（把量级 ≈1 的漏统计当成了几百堆积的主因），建议按下述内容修正。

**问题现象**

流水线「最大排队数量」配置为 20，实际排队构建堆积到几百个。

**根因（按影响程度排序）**

1. **满员判定与入队非原子（主因）。**
   `PipelineBuildService.startPipeline` 中 `pipelineInterceptorChain.filter` 读取排队数量，
   之后才由 `startBuild` 插入 QUEUE 记录，两步之间无流水线级互斥锁
   （`simpleRateLimiter` 是容量为 `maxConRunningQueueSize` 的信号量，非互斥锁，
   且 `frequencyLimit=false` 或免限渠道时被跳过）。
   webhook 风暴时大量并发触发读到同一个偏小的 `queueCount`，全部放行入队，造成超限。

2. **淘汰逻辑不收敛（「只进不出」的直接原因）。**
   满员时固定淘汰 1 个、同时放行 1 个，净变化为 0。
   队列一旦被竞态冲高到几百，就会永久停在高位，`maxQueueSize` 再也无法把它拉回。

3. **排队数量漏统计 QUEUE_CACHE（次因，量级 ≈ 1）。**
   `checkRunLockWithGroupType` 中 `queueCount` 只统计 `QUEUE`，
   而 `QUEUE_CACHE`（已被领取、等待启动的中间态）同样属于排队中。
   需注意 `getOneConcurrencyQueueBuild` 有 `limit(1)`，且领取在 `ConcurrencyGroupLock` 内串行，
   因此同一流水线处于 `QUEUE_CACHE` 的构建约为 1 个，
   **不存在「大量构建落在 QUEUE_CACHE」的情况**，该漏统计只会让淘汰时机推迟一个名额。
   它的另一个副作用是统计口径（仅 `QUEUE`）小于淘汰口径（`QUEUE + QUEUE_CACHE`），
   淘汰到 `QUEUE_CACHE` 构建时 `queueCount` 不下降，会重复触发淘汰。

**补充：maxQueueSize 是「软限制」而非硬上限**

满员时的行为是「挤掉一个最早排队的 + 放行当前新构建」，而不是拒绝新构建。
这是有意设计（保证最新代码优先被构建），应保留。

**措施**

1. 把 GROUP_LOCK 满员判定的统计口径改为 `QUEUE + QUEUE_CACHE`，
   与 `getOneConcurrencyQueueBuild` 的查询口径对齐。
2. 把固定淘汰 1 个改为按超出量淘汰 `min(queueCount - maxQueueSize + 1, 50)`，
   使已超限的队列能够收敛回 `maxQueueSize`，解决「只进不出」。
3. 统计口径必须与淘汰口径严格一致：
   **不可**在此叠加 `#8143` 的空并发组（`CONCURRENCY_GROUP IS NULL`）兼容统计，
   否则会出现「判定满员但一个都淘汰不掉」，重新导致无界增长。
4. 竞态（根因 1）**不在请求路径上加阻塞锁**（会让突发流量长时间排队甚至超时），
   改为在引擎侧已持有并发组锁的对账时机做强制收敛：
   在 `BuildStartControl.checkGroupType` 中把排队数量裁剪回 `maxQueueSize`。
   此时记录都已入库、读到的是真实数量，因此并发多大都能收敛，
   且由监控周期驱动，突发后无需新触发也能回落。请求路径零改动、零额外延迟。
   注意必须保护正在启动的构建、且只淘汰 `QUEUE` 状态，
   否则会把即将执行的队头构建取消掉。
