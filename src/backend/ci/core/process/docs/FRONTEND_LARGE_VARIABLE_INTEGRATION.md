# 大变量（>4K）前端对接说明

> 对应 Issue：[#12636](https://github.com/TencentBlueKing/bk-ci/issues/12636)  
> 适用对象：流水线前端（`devops-pipeline` / `devops-flow`）  
> 完整技术方案：[`PIPELINE_LARGE_VARIABLE_TECHNICAL_DESIGN.md`](./PIPELINE_LARGE_VARIABLE_TECHNICAL_DESIGN.md)  
> 文档版本：2026-10-16（修订：大变量文件改存 `variable` 仓库，路径 `/{pipelineId}/{buildId}/{executeCount}/{sha256}`，无后缀。前端接口不变）

后端约定：**构建运行期产生的变量**（插件输出、脚本 / API 设置的变量）超过 4000 字符时，真实值以文件形式存在制品库，变量表中只存引用串，真实值通过接口按需查询。**启动参数不支持大变量**，默认值和启动时填写的值都不能超过 4000 字符。

> **相对上一版（2026-07-15）的变化**
> 1. 引用串多了执行次数段和摘要段：`__BK_OVF__:<长度>:<执行次数>:<sha256>`，**长度解析方式需调整**（见 §1）
> 2. 单值上限由 4M 改为 **256KB**
> 3. **启动参数仍限制 4000 字符**：启动参数 Tab 无需改造，`parameters/value` 接口已取消；前端 4K 校验**全部保留**（原 §2.1、§2.5 作废）
> 4. 「变量值超长时失败」开关**继续生效**，不再下线，只改文案（原 §2.4 作废）
> 5. **运行期变量点查接口**为 `.../projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/variables/value?key={varName}`（见 §2.2）
> 6. 点查新增 `executeCount` 参数，用于回看**指定那一次执行**的内容（见 §2.2）。日志行格式不变

---

## 1. 引用串协议

| 项 | 说明 |
|----|------|
| 格式 | `__BK_OVF__:{length}:{executeCount}:{sha256}`，例：`__BK_OVF__:123456:1:9f86d081884c7d65…`（64 位十六进制） |
| 识别 | `String(value).startsWith('__BK_OVF__:')` |
| 展示长度 | `Number(String(value).slice('__BK_OVF__:'.length).split(':')[0])` → 「约 N 字符」 |
| 含义 | **不是真实内容**，不可当普通业务值展示 / 复制 / 回填到启动参数 |

**接口返回给前端的引用串就是上表这一整段**，与变量表里存的一致，三段都在：

```text
__BK_OVF__:123456:1:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08
```

| 段 | 含义 |
|----|------|
| `123456` | 真实内容的字符长度 |
| `1` | 写入时的执行次数 |
| 64 位小写十六进制 | 内容 sha256，前端不用它取文件 |

点查成功时 `data` 是真实内容，**不是**这串。只有列表 / 详情里未展开的变量值，以及点查时文件已清理或下载失败，前端才会拿到它。拿到后按前缀识别，用第一段展示「约 N 字符」，不要当正文展示或复制。

构建日志里印的是**退化形态** `__BK_OVF__:{length}`，只有长度一段，不要拿它去点查——点查只需要变量名和执行次数（见 §2.2）。上面的 `split(':')[0]` 对两种形态都能取出长度。

常量对齐后端：`PIPELINE_VARIABLES_OVERFLOW_PREFIX = "__BK_OVF__:"`  
阈值：**4000 字符**（运行期变量超过即进入大变量流程；启动参数的上限）；大变量单值硬上限默认 **256KB（UTF-8 字节）**，可由 `pipeline.variables.largeValueMaxBytes` 调整。

---

## 2. 需要前端改动的项

### 2.1 插件输出超长日志（必做）

构建机对长度 **>4000** 的非敏感输出不打全文，行格式：

```text
output(overflow): {key}=__BK_OVF__:{length}
```

示例：`output(overflow): MY_JSON=__BK_OVF__:204800`

**前端日志渲染：**

1. 匹配：`^output\(overflow\):\s*([^=]+)=__BK_OVF__:(\d+)\s*$`  
2. 展示：`output(overflow): MY_JSON` +「查看完整值」（可附「约 N 字符」，取捕获组 2）  
3. 点击后调**运行期变量**接口（见 2.2）：`key` 取捕获组 1，`executeCount` 传**当前正在查看的这一次执行的次数**，`taskId` 传**这条日志所属的插件**。同名变量在不同插件上内容不同，不带 `taskId` 会取到变量表里最后写入的那一份，而不是这条日志对应的那一份

其它日志行：

| 行格式 | 行为 |
|--------|------|
| `output(normal): key=value` | 照旧 |
| `output(sensitive): key=******` | 照旧，不可点查明文（敏感值从不上传，也不会出现引用串） |
| `output(except): key=__BK_OVF__:N` | 超长异常输出，与 overflow 同款点查 |
| `input(normal): (label)key=__BK_OVF__:N` | 插件入参中的大值占位，**固定只有长度**，仅展示、无需点查 |

超过 256KB 或上传失败的输出，变量会被丢弃并另有一条告警日志，点查会取不到内容（见 §2.2 的过期分支）。日志行本身看不出区别。

---

### 2.2 运行期变量按需查询（通用）

这个接口不只给日志点击用：变量 Tab、插件输出面板等任何拿到「值是引用串」的地方，都用它取真实内容。

```http
GET /ms/process/api/user/builds/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/variables/value?key={varName}&executeCount={N}&taskId={taskId}&containerId={containerId}&stageId={stageId}&archiveFlag=
Header: X-DEVOPS-UID: {userId}
```

| 参数 | 必填 | 说明 |
|------|------|------|
| `key` | 是 | 变量名 |
| `executeCount` | 否 | 执行次数。和下面的组件 id 一起，取回该组件这一次执行当时的内容。记录里没有时，退回取变量表当前值 |
| `taskId` | 否 | 插件 id。日志「查看完整值」必须带上这条日志所属的插件。同名变量在不同插件上各留各的 |
| `containerId` | 否 | Job id。查看某个 Job 上的变量时传入。同时传了 `taskId` 时以插件为准 |
| `stageId` | 否 | Stage id。查看某个 Stage 上的变量时传入。同时传了 `taskId` 或 `containerId` 时以更细的组件为准 |
| `archiveFlag` | 否 | 查归档构建 |

不带 `executeCount` 时取到的是该变量**最后一次写入**的值，这是旧版前端的行为，仍然可用。

**什么时候必须带 `executeCount` 和 `taskId`**：变量表按 `(projectId, buildId, key)` 唯一，重试会覆盖上一次，不同插件上的同名变量也会互相覆盖。展示「某一次执行里某个插件」的界面（日志、插件输出面板），要把执行次数和**这条日志的插件 id** 一起带上。引用串在该插件的 `T_PIPELINE_BUILD_RECORD_TASK` 上。

三个组件 id 只影响先查哪一行，传不准不会直接查不到：按组件查不到时接口会自动回落到流水线级记录，那里有这一次执行的全量引用串。只有构建还在进行、流水线级快照尚未写入时，才会退回变量表里的当前值（不区分执行次数）。

**构建详情接口不返回引用串**：引用串只存在于执行记录里，合成构建详情模型时会被摘掉，`Model` / `Element` 上没有这个字段。要拿内容只能调上面这个接口。

**矩阵**：每一组有自己的插件 id 和容器 id，`stepId` 各组相同。点查必须用这一组日志上的 `taskId`（分裂后的 id），不要用编排里的原插件 id，也不要去查矩阵父 Job。

**插件之间传递**：后一个插件要用 `${{ }}` 引用前一个插件的输出（例如 `${{ jobs.<jobId>.steps.<stepId>.outputs.name }}`）。`$name` / `${name}` 不会把大变量展开，插件拿到的是引用串。入参行 `input(normal): ...=__BK_OVF__:{length}` 只是日志不打印正文。矩阵多组并行时，短变量名在变量表里会被后完成的组覆盖；某一组的日志点查仍用该组的 `taskId`。

响应示例：

```json
{
  "status": 0,
  "data": "...真实内容，最长 256KB..."
}
```

- 找不到 key：`data = null`
- 敏感变量：返回脱敏符 `******`，不是引用串，也不是明文
- 下载成功：`data` 为真实内容
- 制品库文件已过期（大变量文件在 `variable` 仓库，删除流水线或构建时由平台清理接口删除）或下载失败：`data` 为完整引用串 `__BK_OVF__:{length}:{executeCount}:{sha256}`（见 §1），**不是**日志里的 `__BK_OVF__:{length}`。前端提示「内容已过期或暂不可用」，不要把这串当正文展示
- `archiveFlag=true`：查归档构建
- 弹窗 / 下载要考虑 256KB 文本的体验（截断预览、复制、下载为文件均可）

---

### 2.3 设置页「流水线变量值超长时」开关（改文案）

- `failIfVariableInvalid` **继续生效，保留开关**（`RunningLock.vue`、`BaseConfig.vue`、模板约束等）  
- 语义：
  - 启动时任意启动变量超过 4000 字符 → 启动失败（与原来一致）
  - 运行中脚本设置的变量超过系统上限（256KB；非 bkrepo 制品库环境为 4000 字符）→ 插件失败
- 未开启时：运行期超限的值被丢弃并在日志告警，构建继续
- 文案建议见 §4

---

### 2.4 前端长度校验（保留）

| 场景 | 上限 | 前端处理 |
|------|------|----------|
| 编辑流水线 / 模板时的变量**默认值** | 4000 字符 | **保留** 4K 校验（后端保存时以 `2101504` 拒绝） |
| 启动 / 重放时填写的启动参数值 | 4000 字符 | **保留** 4K 校验（后端启动时以 `2101274` 拒绝） |

- `store/constants.js` 中 `VAR_MAX_LENGTH = 4096` 建议改为 `4000`，与后端口径一致  
- `param-value-option.vue` / `atom/getters.js` 中的超长拦截：不再只在开启 `failIfVariableInvalid` 时生效，启动参数一律按 4000 拦截

### 2.5 启动参数 Tab（无需改动）

启动参数不会出现引用串，`GET .../parameters` 行为与原来一致，`StartParams.vue` 无需改造。上一版文档中的 `parameters/value` 接口**已取消**，请勿对接。

---

## 3. API 一览

| 场景 | Method | Path | 返回 |
|------|--------|------|------|
| 运行期变量真实值 | GET | `/user/builds/projects/{projectId}/pipelines/{pipelineId}/builds/{buildId}/variables/value?key={varName}&executeCount={N}&taskId={taskId}&containerId={containerId}&stageId={stageId}&archiveFlag=` | `String?` |
| 启动参数列表（未改动） | GET | `/user/builds/{projectId}/{pipelineId}/{buildId}/parameters` | `List<BuildParameters>` |

鉴权：流水线 **VIEW**（`archiveFlag=true` 时走归档权限校验）。  
前缀：经网关一般为 `/ms/process/api`。

相关错误码：

| 码 | 场景 | 建议提示 |
|----|------|----------|
| `2101274` | 启动参数值超过 4000 字符 | 直接展示后端 message |
| `2101504` | 保存时默认值超过 4000 字符 | 直接展示后端 message |

---

## 4. 文案建议

| 场景 | 文案 |
|------|------|
| 日志 overflow | 输出过大未展示全文，点击查看 |
| 点查拿到引用串 | 内容已过期或暂不可用 |
| `$xxx` / `${xxx}`（若产品要提示） | 大变量请使用 `${{ xxx }}` |
| 设置开关 | 变量值超过系统上限时构建失败（启动参数 4000 字符，运行中输出 256KB） |
| 默认值 / 启动值超长 | 变量值不能超过 4000 字符 |

---

## 5. 联调检查清单

- [ ] 日志出现 `output(overflow):` 时可点击查询到全文；长度解析正确  
- [ ] 重试构建后，分别带 `executeCount=1`、`executeCount=2`，并带上这条日志的 `taskId`，点「查看完整值」，各自取回当次、该插件的内容  
- [ ] 两个插件都输出同名变量时，各自带自己的 `taskId`，取回的内容不同  
- [ ] 矩阵多组都输出同名变量时，每组用自己日志上的 `taskId` 点查，内容对应该组；不要用父 Job 或编排里的原插件 id  
- [ ] 后一个插件用 `${{ }}` 引用前一个插件的大变量时，入参是真实内容；用 `$name` / `${name}` 时入参是引用串  
- [ ] 不带 `executeCount` 时取到的是最后一次执行的内容  
- [ ] 敏感变量点查返回脱敏符  
- [ ] `archiveFlag=true` 时运行期变量单值仍可用  
- [ ] 设置页「变量值超长」开关保留，文案已更新  
- [ ] 编辑默认值、启动时填写参数值 >4000 均被拦截，错误提示可读  
- [ ] 无权限 / key 不存在 / 内容过期时提示可读  

---

## 6. 前端无需关心（后端已保证）

- 写入：运行期 >4K 的值由构建机或后端上传到项目 `variable` 仓库 `/{pipelineId}/{buildId}/{executeCount}/{sha256}`（无后缀），变量表只留引用串  
- 构建详情「制品列表」不会出现大变量文件（上传时不带构建元数据），产物列表无需过滤  
- `${{ xxx }}`：引擎 / 构建机 / 条件 / 矩阵 / Job 调度与互斥超时等按需下载；`$xxx` / `${xxx}` 只见引用串  
- 列表 / 详情接口不还原大值，变量值字段原样是完整引用串 `__BK_OVF__:{length}:{executeCount}:{sha256}`，不是日志占位 `__BK_OVF__:{length}`  
- 按执行次数、按组件回看所需的引用串由后端存在对应组件的执行记录里，前端传 `key` + `executeCount` + 组件 id（日志场景是 `taskId`），不用自己保存或回传引用串  
- 引擎侧单会话加载有预算上限（默认 2M 字符）  

有接口字段或路径问题，对齐本文件第 3 节或技术方案文档即可。
