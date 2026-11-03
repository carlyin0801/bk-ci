# 归档库构建变量查询 & 单变量查询优化 技术方案

> 关联需求：[issue #12636](https://github.com/TencentBlueKing/bk-ci/issues/12636) 大变量（>4K，上限 4M）溢出存储与按需加载。
> 本文覆盖该需求收尾阶段的两块改动：**归档库支持构建变量查询** 与 **单变量查询效率优化**。
> 溢出存储/懒加载的整体设计见同目录 `PIPELINE_LARGE_VARIABLE_TECHNICAL_DESIGN.md`、
> `CLASSIC_DIALECT_OVERFLOW_SYNTHETIC_KEY_DESIGN.md`。

---

## 1. 背景与目标

流水线归档（把不再活跃的流水线整体迁移到 `devops_ci_archive_process` 归档库，并从线上库删除）落地后，
用户仍可在「归档流水线」入口查看历史构建。但本次大变量特性引入的三张表未纳入归档体系，导致：

1. **归档库缺表**：`T_PIPELINE_BUILD_VAR`、`T_PIPELINE_BUILD_VAR_OVERFLOW`、
   `T_PIPELINE_BUILD_HISTORY_PARAM_OVERFLOW` 在归档库不存在。
2. **迁移不搬变量**：归档迁移显式跳过了这三张表，归档后构建变量与大值全部丢失。
3. **读接口不路由归档库**：
   - `UserBuildResource#getBuildVariableValue`（运行期变量真实值）无 `archiveFlag`，永远查线上库；
   - `UserBuildResource#getBuildParameterValue`（启动参数真实值）虽已带 `archiveFlag`，
     但因归档库缺表 + 迁移不搬，实际查不到大启动参数真实值。
4. **单变量查询低效**：`BuildVariableService#getVariable` 查一个变量却全量拉出该次构建的所有变量，
   在大变量场景下会把主表所有引用串（甚至误触发大值）一次性载入。

### 目标

- 归档流水线的构建变量 / 大变量 / 大启动参数可正常查询真实值；
- 单变量查询按 key 精准查询，杜绝「查一个拉全部」；
- **对历史正常构建零行为影响**，满足上线标准。

---

## 2. 现状分析（改动前）

### 2.1 归档数据路由机制

- 运行期由 `archiveFlag` 决定 DSLContext：
  `CommonUtils.getJooqDslContext(archiveFlag, ARCHIVE_SHARDING_DSL_CONTEXT)`，
  `true` → 归档库 bean `archiveShardingDslContext`，否则默认库。
- Resource（`@QueryParam archiveFlag`）→ Facade（解析 queryDslContext）→ Service/DAO（透传 dslContext）。
- 归档后线上库无该流水线数据，读接口**不做自动探测**，完全依赖调用方传入 `archiveFlag=true`。

### 2.2 迁移机制

- `UserArchivePipelineResource#migrateArchivePipelineData` / `batchMigrateArchivePipelineData`
  → Facade → MQ/任务 → `ProcessArchivePipelineDataMigrateService` → `PipelineMigrationTask`
  → `MigrationStrategyFactory`（`archiveFlag=true` 只跑 `commonPipelineDataStrategies`）。
- 构建关联表由 `PipelineBuildLinkedDataMigrationStrategy` 迁移，其中：
  - `nonArchiveHandlers`（含 `T_PIPELINE_BUILD_VAR`、DETAIL、PAUSE_VALUE 等）与
    `migrateOverflowPerBuild`（两张溢出表）**被 `if (archiveFlag != true)` 整体跳过**。
  - 迁移 DAO 方法（`getPipelineBuildVarRecords` / `migratePipelineBuildVarData` /
    `get|migratePipelineBuildVarOverflowData` / `get|migratePipelineBuildHistoryParamOverflowData`）
    **早已实现**，只是被开关挡住。

### 2.3 变量读取链路

- `getVariable(name)` → `getAllVariable()`（`keys=null` 全量）→ 取 `vars[name]`。**低效**。
- `getVariableValue(name)` → 主表单 key 查询 + 溢出按需加载。**已高效**，但固定用 `commonDslContext`，无归档能力。
- Facade `getBuildVariableValue` → 先 `getVariable`（全量）再判断溢出、必要时 `getVariableValue`（再查一次）。**双重低效**。

---

## 3. 方案设计

### 3.1 单变量查询优化（`BuildVariableService`）

**原则**：单变量查询只按 key 精准查，落库统一新变量名，映射兼容旧名 / 预置上下文名（含前缀映射）。

- 新增 `PipelineVarUtil.resolveStoredVarKey(varName)`：依次尝试
  「旧→新精确映射 / 预置上下文→变量名映射 / 旧→新前缀映射」，都不命中原样返回。
  等价于 `mixOldVarAndNewVar` 的取值口径，但只解析单个 key，避免全量混合。
- `getVariable`：改为 `keys = {varName, storedKey}` 精准查询，返回 `dataMap[varName] ?: dataMap[storedKey]`。
- `getVariableValue`：新增可选 `queryDslContext`（默认 `commonDslContext`），
  同样做 key 映射；命中的 key 即溢出表 key，溢出按需加载走同一 DSLContext。

### 3.2 归档库读路由（`getBuildVariableValue`）

- `UserBuildResource#getBuildVariableValue` 接口 + `UserBuildResourceImpl` 新增
  `@QueryParam("archiveFlag") archiveFlag: Boolean? = false`。
- Facade `getBuildVariableValue` 新增 `archiveFlag`：
  - 权限校验改用 `createUserPipelinePermissionCheckStrategy(archiveFlag)`（与 `getBuildParameterValue` 一致，
    归档走 `MANAGE_ARCHIVED_PIPELINE`）；
  - `queryDslContext = CommonUtils.getJooqDslContext(archiveFlag, ARCHIVE_SHARDING_DSL_CONTEXT)`；
  - 直接调用 `getVariableValue(..., queryDslContext)`——**去掉「先 getVariable 再判溢出」的双查**。
- `getBuildParameterValue` 无需改代码（早已带 `archiveFlag` 且透传 queryDslContext），
  本次通过「补表 + 补迁移」使其归档链路端到端可用。

### 3.3 迁移补齐三张变量表（`PipelineBuildLinkedDataMigrationStrategy`）

- 从 `nonArchiveHandlers` 拆出 `varHandlers`（仅 `T_PIPELINE_BUILD_VAR`）；
- `migrateBuildLinkedData` 中：
  - `varHandlers` + `migrateOverflowPerBuild`（两张溢出表）**归档 / 非归档都执行**；
  - 其余运行期 / 执行明细表（DETAIL、PAUSE_VALUE、WEBHOOK_BUILD_PARAMETER、STAGE、CONTAINER、TASK）
    **仍仅非归档执行**（归档不保留运行期明细，符合归档语义）。
- 复用既有迁移 DAO（无新增 DAO），溢出表仍按 buildId 流式迁移，控制 mediumtext 内存峰值。

### 3.4 归档库 DDL 补三张表

- 基础脚本 `support-files/sql/1001_ci_archive_process_ddl_mysql.sql` 追加三张 `CREATE TABLE IF NOT EXISTS`；
- 增量脚本 `support-files/sql/2026_v4.x/2026_ci_archive_process-update_v4.1_mysql.sql` 追加同样建表（幂等，供存量归档库升级）。
- **归档库与线上库的差异（重要）**：
  - `T_PIPELINE_BUILD_VAR_OVERFLOW` 在**归档库不做按月 RANGE 分区**。
    线上库靠 `DROP PARTITION` 做 ~1 个月清理（运行期变量短期），而归档数据须与构建历史同周期长期保留，
    因此归档表主键仅 `(BUILD_ID, KEY)`，避免误挂分区清理任务导致归档数据被删。
  - `T_PIPELINE_BUILD_HISTORY_PARAM_OVERFLOW` 本就长期不分区，归档库结构与线上一致（PK `(BUILD_ID, KEY)`）。
  - jOOQ 生成的表对象基于线上库结构，插入只依赖列集合，与归档库 PK / 分区差异无关，迁移插入不受影响。

---

## 4. 改动清单

| # | 文件 | 改动 |
|---|------|------|
| 1 | `core/process/api-process/.../utils/PipelineVarUtil.kt` | 新增 `resolveStoredVarKey`（旧名/上下文名/前缀 → 落库新名） |
| 2 | `core/process/biz-base/.../service/BuildVariableService.kt` | `getVariable` 单 key 精准查；`getVariableValue` 加 `queryDslContext` + key 映射 |
| 3 | `core/process/biz-process/.../service/builds/PipelineBuildFacadeService.kt` | `getBuildVariableValue` 加 `archiveFlag`，归档路由 + 归档权限策略，直调 `getVariableValue`（去双查）；移除未用 import |
| 4 | `core/process/api-process/.../api/user/UserBuildResource.kt` | `getBuildVariableValue` 加 `@QueryParam archiveFlag` |
| 5 | `core/process/biz-process/.../api/UserBuildResourceImpl.kt` | `getBuildVariableValue` 加 `archiveFlag` 并透传 |
| 6 | `core/misc/biz-misc/.../strategy/impl/pipeline/PipelineBuildLinkedDataMigrationStrategy.kt` | 拆出 `varHandlers`；变量主表 + 两张溢出表归档/非归档都迁移 |
| 7 | `support-files/sql/1001_ci_archive_process_ddl_mysql.sql` | 归档库新增三张表 |
| 8 | `support-files/sql/2026_v4.x/2026_ci_archive_process-update_v4.1_mysql.sql` | 存量归档库增量建三张表 |

---

## 5. 兼容性与历史构建影响分析

### 5.1 单变量查询优化

- **落库始终新变量名**：写路径 `batchSetVariable → replaceOldByNewVar` 已把旧名转新名，旧名不入库。
  `resolveStoredVarKey` 精确覆盖旧→新（精确 + 前缀）与预置上下文→新名三类映射，读取能力与
  `mixOldVarAndNewVar` 一致。
- `getVariable` 现有调用方（`PIPELINE_BUILD_REMARK`、`PIPELINE_RETRY_COUNT`、
  失败任务名、父项目 ID、taskId 等）均为具体已知 key，精准查询结果与全量后取值等价，且更快。
- `getVariableValue` 新增参数带默认值，**所有历史调用方（worker SDK、`BuildVarResourceImpl` 等）零改动**；
  新增的 key 映射对「按落库真实 key 查询」的既有行为是超集（只多兼容旧名，不改变命中值）。

### 5.2 归档读路由

- 新增 `archiveFlag` 均为 `Boolean? = false` 默认值：非归档调用行为与改动前完全一致。
- `getBuildVariableValue` 由「`getVariable` 全量 + 可能二次 `getVariableValue`」改为「一次 `getVariableValue`」：
  非溢出变量返回主表值、溢出变量返回真实值，**结果语义不变**，且减少一次全量查询。

### 5.3 迁移改动

- 仅把三张变量表从「非归档才迁」放宽为「归档也迁」；**非归档（项目分库迁移）路径完全不变**
  （它本就迁这三张表）。
- 其余运行期 / 执行明细表的归档跳过逻辑保持不变。
- 复用既有迁移 DAO 与流式迁移策略，无新增读写模式，内存与失败语义（快速失败）不变。

### 5.4 DDL

- 全部 `CREATE TABLE IF NOT EXISTS`，对已存在对象无副作用；仅作用于归档库 `devops_ci_archive_process`，
  不触碰线上库与历史数据。

---

## 6. 性能分析

| 场景 | 改动前 | 改动后 |
|------|--------|--------|
| `getVariable(name)` | 全表扫该 build 所有变量行 | 主键点查 1~2 个 key |
| `getBuildVariableValue`（非溢出） | 全量查 + 内存过滤 | 单 key 主表查询 |
| `getBuildVariableValue`（溢出） | 全量查 + 二次单 key + 溢出加载 | 单 key 主表查询 + 溢出按需加载 |
| 归档迁移 | 变量表不迁 | 变量主表分页 batchInsert；溢出表按 buildId 流式迁移（内存峰值 = 单 build 大值之和） |

---

## 7. 上线步骤

1. **先执行 DDL**（非破坏性）：
   - 新归档库：随 `1001_ci_archive_process_ddl_mysql.sql` 建库即含三张表；
   - 存量归档库：执行 `2026_v4.x/2026_ci_archive_process-update_v4.1_mysql.sql`（幂等）。
   - 确保线上/分片库的三张表 DDL 也已按大变量特性文档就位（本特性前置）。
2. **升级 process / misc 服务**（读路由 + 迁移逻辑）。
3. 验证：归档一条含大变量 / 大启动参数的流水线，从归档入口查询变量真实值。

> 顺序要点：DDL 必须先于服务升级；迁移逻辑依赖归档库已建表，否则迁移插入会失败并快速失败回滚。

---

## 8. 风险与回滚

- **风险**：归档库未建表就升级服务 → 迁移插入报错。**规避**：严格「先 DDL 后服务」。
- **回滚**：三处代码改动均可独立回滚；`archiveFlag` 默认关，回滚后非归档链路不受影响；
  DDL 为增表，回滚服务无需删表（空表无副作用）。
- **既有 `common-pipeline-yaml` 编译问题**：本地检出中 `common-pipeline-yaml` 存在与本需求无关的
  预置编译错误（`ModelContainer.kt` / `ContainerTransfer.kt` / `PipelineTransferAspectLoader.kt` 的
  smart-cast 与 `envProjectId`/`poolName` 未解析），非本次改动引入，需由相应分支/模块单独修复后才能整体编译。

---

## 9. 上线标准评估

| 维度 | 结论 |
|------|------|
| 需求覆盖 | 归档库读（运行期变量 + 启动参数）、单变量查询效率、迁移补三表、归档 DDL —— **全部覆盖** |
| 历史构建影响 | 新增参数默认值兼容、落库新名映射为超集、非归档迁移不变 —— **零行为影响** |
| 编译校验 | `biz-misc`、`api-process` 独立编译通过；`biz-base`/`biz-process` 因**无关的预置** `common-pipeline-yaml` 报错未能整体编译；本次改动文件均 lint 通过、符号/依赖核对无误 |
| 性能 | 单变量查询由全量降为点查，读接口去双查 —— **正向优化** |

**结论**：本需求代码改动在功能、兼容性、性能上均满足上线标准。**唯一阻塞项是与本需求无关的
`common-pipeline-yaml` 预置编译错误**——需在该模块修复后进行一次完整编译 + 联调回归，再行发布。
