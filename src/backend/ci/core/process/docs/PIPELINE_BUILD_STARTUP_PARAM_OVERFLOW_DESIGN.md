# 构建启动参数大值溢出存储设计

> **总览请先读**：[`PIPELINE_LARGE_VARIABLE_TECHNICAL_DESIGN.md`](./PIPELINE_LARGE_VARIABLE_TECHNICAL_DESIGN.md)。  
> 关联文档：`PIPELINE_BUILD_VAR_OVERFLOW_DESIGN.md`（运行期变量 T_PIPELINE_BUILD_VAR 的大值方案）。  
> 本文解决的是**启动参数**长期载体（构建历史 BUILD_PARAMETERS）的大值问题，二者协议一致但保留周期不同。

## 1. 背景与问题

`UserBuildResource#manualStartup` 等入口允许传入单值最大 **4M** 的启动参数。这些值有两个落点：

| 落点 | 表 / 列 | 周期 | 说明 |
|------|---------|------|------|
| 运行期变量 | `T_PIPELINE_BUILD_VAR`(+`_OVERFLOW`) | **短期**（按 `CREATE_TIME` 月分区，约 1 个月清理） | 供构建运行期变量求值；大值已有溢出方案 |
| 启动参数快照 | `T_PIPELINE_BUILD_HISTORY[_DEBUG].BUILD_PARAMETERS` (`mediumtext`) | **长期**（与构建历史同周期） | 供 `getBuildParameters` 展示、retry / replay 重放 |

由于 VAR 表会按月清理，**启动参数的长期事实来源是 `BUILD_PARAMETERS`**。但原实现把 `List<BuildParameters>` 的**完整 value** 直接 `JsonUtil.toJson` 写入该列，存在两类严重问题：

1. **写入可能失败 / 超限**：多个大值拼成的 JSON 可能突破 `mediumtext`（~16M）上限；
2. **读取 OOM（最严重）**：列表 / 历史接口会**逐行**反序列化 `BUILD_PARAMETERS`：
   - `listPipelineBuildHistory` / `getHistoryBuild` 单页最多 1000 行；
   - `PipelineBuildLightInfoJooqMapper`（`listLightPipelineBuildInfo`）`SELECT` 里带 `BUILD_PARAMETERS` 并解析为 `List<LightBuildParameter>`（仍含完整 value）；
   - 量级 ≈ `pageSize × Σ(单行各大值)`，N 个 4M 值即可击穿 16Gi/Pod。

原溢出方案（`__BK_OVF__:` 引用 + `T_PIPELINE_BUILD_VAR_OVERFLOW`）**只覆盖 VAR 表，完全没有覆盖 `BUILD_PARAMETERS`**。

## 2. 方案概述

沿用 `BuildVarOverflowUtils` 的"引用协议"，把 `BUILD_PARAMETERS` 也**引用化**，并为大值提供一个**长期、不分区**的载体表：

- **写入**：写 `BUILD_PARAMETERS` JSON 前，对 value 长度 `> 4000` 的启动参数，把完整值落 `T_PIPELINE_BUILD_HISTORY_PARAM_OVERFLOW`，JSON 里只留纯引用串 `__BK_OVF__:<len>`（恒 ≤ 25 字符）。
- **读取**：
  - **启动参数 Tab**（`getBuildParameters`）不解析，只返回引用串；
  - **单 key 按需**（`getBuildParameterValue`）从溢出表取真实值；
  - **retry / replay** 走 `resolveForRestart` 完整还原；
  - **列表 / 历史接口**不解析，只看到引用串 → 反序列化体积恒定，**天然 OOM 安全**。

与 VAR 溢出表的**关键区别**：本表保留周期必须与构建历史一致，故**不做时间分区**，而是随历史按 `buildId` 清理 / 归档迁移。因此主键只需 `(BUILD_ID, KEY)`，可安全使用 `INSERT ... ON DUPLICATE KEY UPDATE`。

## 3. 数据模型

见 `T_PIPELINE_BUILD_HISTORY_PARAM_OVERFLOW_MIGRATION.sql`。

```sql
CREATE TABLE IF NOT EXISTS `T_PIPELINE_BUILD_HISTORY_PARAM_OVERFLOW` (
    `BUILD_ID`     varchar(34)  NOT NULL,
    `KEY`          varchar(255) NOT NULL,
    `VALUE`        mediumtext   NOT NULL,             -- 单值最大 4M
    `VALUE_LENGTH` int          NOT NULL DEFAULT 0,
    `PROJECT_ID`   varchar(64)  NOT NULL,
    `PIPELINE_ID`  varchar(64)  NOT NULL,
    `DEBUG`        bit(1)       NOT NULL DEFAULT b'0',
    `CREATE_TIME`  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `UPDATE_TIME`  datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`BUILD_ID`,`KEY`),                   -- 不分区，逻辑唯一，长期保留
    KEY `idx_PROJECT_PIPELINE` (`PROJECT_ID`,`PIPELINE_ID`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='构建启动参数大值溢出存储表';
```

> 上线：先执行 DDL（含归档库 / 各分片库）→ jOOQ codegen 生成 `TPipelineBuildHistoryParamOverflow` → 升级 process / misc。

## 4. 模块与改动清单

### 4.1 新增

| 文件 | 作用 |
|------|------|
| `engine/dao/PipelineBuildHistoryParamOverflowDao.kt` | 溢出表 CRUD：`save`(upsert) / `getValue` / `deleteByBuildId` |
| `service/BuildStartupParamOverflowService.kt` | `persistAndStrip`（写引用）/ `resolve`（读解析，预算护栏） |
| `docs/T_PIPELINE_BUILD_HISTORY_PARAM_OVERFLOW_MIGRATION.sql` | DDL |

### 4.2 写入路径（落溢出表 + JSON 只留引用）

集中在 `PipelineRuntimeService`，覆盖所有 `BUILD_PARAMETERS` 写点：

| 写点 | 触发 | 处理 |
|------|------|------|
| `pipelineBuildDao.create` | 全新构建 | `persistAndStrip(context.buildParameters)` → 经新增入参 `overflowStrippedParams` 写入 |
| `updateBuildRetryInfo` | 重试（复用同 buildId） | 对 `retryInfo.buildParameters` 跑 `persistAndStrip`（引用串幂等，不重复落库） |
| `updateBuildParameters` | `BuildStartControl` 刷新 BUILD_NO / 推荐版本号 | 写回前 `persistAndStrip`，避免把已解析的真实值又灌回 mediumtext |

`PipelineBuildDao.create` 新增可选入参 `overflowStrippedParams`（默认 `startBuildContext.buildParameters`，**不传则行为不变**）。

> **关键安全点**：`StartBuildContext.buildParameters` 的元素与 `pipelineParamMap` **共享对象**，`persistAndStrip` 只生成"引用化副本"（`param.copy(value = ref)`），**绝不原地修改** value，因此 VAR 表 (`batchSetVariable` / `startBuildBatchSaveWithoutThreadSafety`) 仍拿到完整值，其自有溢出逻辑不受影响。

### 4.3 读取路径（按需解析引用）

仅"单构建消费侧"解析，**列表 / 历史不解析**：

| 读点 | 是否解析 | 说明 |
|------|----------|------|
| `getBuildParametersFromStartup`（启动参数 Tab `getBuildParameters` API） | ❌ 传 `resolveOverflow=false` | 大变量一律返回引用串；真实值走 `getBuildParameterValue` |
| `getBuildParameterValue`（按 key 按需） | ✅ 单 key `resolveSingleValue` | 敏感参数短路脱敏，不查溢出表 |
| `BuildStartControl` 调 `getBuildParametersFromStartup` | ❌ 传 `resolveOverflow=false` | 仅需系统参数刷新 BUILD_NO，避免无谓加载大值 |
| `PipelineBuildParamCombinationService`（参数组合回填） | ✅ 默认 `resolveOverflow=true`（`resolveForDisplay`） | 尽量还原，超预算降级为引用串 |
| `PipelineBuildRetryService`（重试组装启动参数） | ✅ `resolveStartupParamOverflow` | 真实值要写回 VAR 表 / 新历史 |
| `PipelineBuildFacadeService` 定时重放 / `buildRestartPipeline` | ✅ `resolveStartupParamOverflow` | 新构建需要真实值 |
| 列表：`listPipelineBuildHistory` / `getLightHistoryBuild` / 各 Jooq Mapper | ❌ | 只读引用串，OOM 安全 |
| 状态：`getBuildStatusWithVars` / `buildManualStartupInfo` 默认值回填 | ❌ | 与 `variables`（VAR 引用串）保持一致；大值仅显示引用串，不影响 ≤4K 历史值 |

`PipelineRuntimeService.resolveStartupParamOverflow(...)` 为重试 / 重放提供统一入口（无引用串时走快路径，不触库）。

### 4.4 清理与归档迁移（misc）

- `ProcessDataDeleteDao.deletePipelineBuildHistoryParamOverflow(buildIds)`，在 `ProcessDataClearService.clearOtherBuildData` 中**归档与正式库路径都调用**，与构建历史同生命周期；
- `ProcessDataMigrateDao` 新增 `get/migratePipelineBuildHistoryParamOverflowData`，在 `PipelineBuildLinkedDataMigrationStrategy.migrateOverflowPerBuild` 中**按单 buildId 流式迁移**（与 VAR 溢出表一致），避免多 build 的 mediumtext 同时入内存。

### 4.5 其它健壮性

- `PipelineBuildFacadeService.buildManualStartup` 不再 `vars: $values` 全量打印启动参数，改为打印 `key(len=…)`，避免 4M 值撑爆日志 / 内存。

## 5. 内存安全分析

- **写入**：`persistAndStrip` 逐条 upsert（单值 ≤4M），`BUILD_PARAMETERS` JSON 中每个大值恒为 ≤25 字符引用串 → 既不超 `mediumtext`，也不会让列表读取膨胀。
- **列表读取**：永不解析引用 → 单行体积与是否含大值无关，从根上消除 `pageSize × 大值` 的 OOM。
- **单构建读取分两路**（关键：高频路径与大上限解耦）：
  - **启动参数 Tab `getBuildParameters`**（高频、易并发）：**不解析**大值，响应里只含引用串 `__BK_OVF__:<len>`，单次内存与是否含大变量无关。
  - **单 key `getBuildParameterValue`**：按需查溢出表取一条真实值（≤4M）；敏感参数短路脱敏、不查库。
  - **批量展示解析 `resolveForDisplay`**（参数组合回填等）：单次上限固定为 `lazyLoadBudgetMax`（默认 32M），超出保留引用串。
  - **重启侧 `resolveForRestart`**（retry / replay，低频、用户触发）：必须完整还原真实值，上限 = `startupTotalMax`；越界**抛错快速失败**，绝不静默把引用串当真实值。
- **并发内存保证**：高频的 `getBuildParameters` 不再批量加载大值；单 key 接口峰值 ≤ 单值 4M；构建**运行期**变量走 VAR 表的 `BuildVarOverflowLoader`。retry/replay 为低频操作，其总并发由既有启动限流 / 排队约束。
- **写入期双闸门**（`PipelineBuildService.rejectIfVariableHardOversize`，一处覆盖所有启动通道）：
  - **单值硬上限 4M**（`PIPELINE_VARIABLES_STRING_LENGTH_HARD_MAX`）：避免单条值撑爆 mediumtext；
  - **总大小上限**（可配置 `pipeline.variables.startupTotalMax`，默认 32M，`@RefreshScope` 热调）：避免一次传入大量大变量导致启动期内存 / 溢出表写入量 / 后续按需读取（受 `budgetMax` 约束只能部分解析）失控。
  > 草稿侧（`savePipelineDraft`）的参数默认值总量已由 `DefaultModelCheckPlugin` 的 `maxModelSize`（默认 ~16M）兜底，无需重复限制。
- **读取预算 ≠ 启动拦截**：`resolve()` 的 `budgetMax`（32M）是**读取侧**护栏，超限时保留引用串、停止加载（优雅降级），**不**终止构建；启动期是否放行由上面的"总大小上限"决定。

## 6. 兼容性 / 对历史逻辑的影响

- **历史构建**：旧 `BUILD_PARAMETERS` 存的是完整值（历史上限 4K，无引用串）。`resolve` 检测不到引用串即走快路径**原样返回**，行为完全不变。
- **不含大启动参数的新构建**：`persistAndStrip` 不触发溢出（无 value >4K），`resolve` 快路径返回，**零额外 DB 开销**。
- **仅**"启用大启动参数后的新构建"才进入溢出读写路径。
- 重试复用同 `buildId`，引用串与溢出行天然对齐；重放 / 重启创建新 `buildId`，已先 `resolve` 成真实值再发起，新构建会在新 `buildId` 下重新落溢出行。

## 7. 流程 review：其它已确认安全 / 无需改动点

| 位置 | 结论 |
|------|------|
| `T_PIPELINE_WEBHOOK_BUILD_PARAMETER` | 已有保护：JSON UTF-8 > 65535 直接跳过保存，不会写入大值 |
| `RECOMMEND_VERSION` / `T_PIPELINE_TRIGGER_DETAIL` / `T_PIPELINE_BUILD_DETAIL` | 不存启动参数完整 value，无本问题 |
| `getAllVariable`（VAR 表） | 大值已是引用串（~25 字符），非完整值，安全 |
| `StartBuildContext` 三份引用（variables / pipelineParamMap / buildParameters） | 单构建启动期、受 4M 硬上限约束，非列表聚合，风险可接受；引用化不改变其语义 |
