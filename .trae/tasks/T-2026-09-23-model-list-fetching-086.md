# Treelearn Harness Task: 模型列表拉取与导入

id: T-2026-09-23-086
title: 模型列表拉取与导入
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: true
depends_on: [T-2026-09-23-sqlite-provider-model-schema-083.md, T-2026-09-23-ai-gateway-provider-abstraction-085.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务实现从 OpenAI 兼容 `/v1/models` 端点拉取模型清单并导入本地配置。
- Problem/background: RikkaHub 支持从 API 地址自动导入模型列表；知芽需要同能力避免手工逐个输模型 ID。
- Users/scenarios: 用户新建提供商后一键拉取模型，拉到的模型出现在模型设置页可选列表。
- Expected outcome: 网关新增模型列表查询；拉取结果去重合并入 model 表；失败可重试。
- Core flow and reversibility: 拉取 → 解析 → 去重合并（已有手工模型不覆盖）→ 展示；失败不写库。
- Inputs/outputs: 输入 083 DAO 与 085 网关抽象；输出拉取/导入链路与单测。
- State and exception handling: [OFF] 失败提示可重试；[DUP] 同 ID 模型去重；响应异常（非 JSON/字段缺失）fail-fast 不写库。
- Constraints and trade-offs: HttpsURLConnection；拉取在工作线程；响应大小上限防异常巨包。
- AI recommendation and rationale: 合并策略「以模型 ID 为唯一键，新增 only」，避免覆盖用户改过的显示名。
- User choice and custom additions: 无。
- AI-autonomous decisions: 缓存策略（每次实拉，不落缓存表）。
- User-confirmation decisions: 无（在 079 规格内）。
- Acceptance evidence: RED/GREEN 单测（含 mock SSE-free HTTP 层）+ 回归。
- Facts: OpenAI 兼容 /v1/models 返回 `{data:[{id:...}]}` 形态（079 规格核对）。
- Assumptions: 083/085 已 verified。
- Unknowns: 各服务分页/截断行为差异。
- Risks: 恶意/超大响应打爆内存。
- Deferred: 手动刷新调度、ETag 缓存。
- Non-goals: 不做 UI（087/088 展示）。
- Skipped non-critical questions and reason: 请求超时沿用网关默认。

## Goal

交付模型列表拉取、去重合并导入链路，供提供商管理页与模型设置页消费。

## Scope

### In scope

- 网关 models 端点查询、解析、去重合并 DAO 写入、单测。

### Out of scope

- UI；提供商 CRUD。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增能力；现有网关对话路径不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ai/gateway/` 新增端点；`data` model DAO 消费。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 网关新增 models 查询能力。
- Authoritative code/config/migration evidence: 网关与 DAO 代码。
- Documentation surfaces checked: SPEC-079 技术节。
- Documentation files updated: 网关能力说明。
- Producer/consumer/sibling/compatibility paths checked: 087/088 将消费本能力。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：拉取解析、去重合并、失败不写库断言。
- [x] 超大响应上限与异常 JSON 防护有测试。
- [x] Java-only/依赖方向 guard PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `:ai` AiGatewayModelListTest（8 用例：GET `{base}/models` + Bearer、缺 Key 拒发、401 分类且不读体、网络异常分类、超大响应拒收不截断、payload/凭据不进日志）；`:data` ModelDaoMergeTest（5 用例：大小写精确去重、只增不删、远端内部重复静默跳过、空远端/空库边界）。
- Command: cd android && ./gradlew :ai:testDebugUnitTest :data:testDebugUnitTest
- Exit code: 1
- Observed failure: 22 个编译错误——T-086 API（AiGateway.listModels / AiGateway.Models / MAX_MODELS_BYTES / GatewayEndpoints.MODELS_PATH / ProviderEndpoint.modelsUrl / ProviderJson.ModelList / ModelDao.newModelIds）不存在。
- Failure reason validated: 失败恰因待实现能力缺失（找不到符号全部指向 T-086 面），非环境或其他缺陷。

### GREEN — minimum implementation
- Implementation: 网关 `AiGateway.listModels(apiKey, endpoint)`（同步，沿用 checkBalance 模式；Key 仅经 Transport.get 参数进传输边界，不落任何字段）+ `AiGateway.Models` 结果值（失败时 ids 恒空，调用方只在 ok() 时合并，结构性保证不半写库）+ `GatewayPorts.ModelListDecoder` / `ProviderJson.ModelList`（platform org.json 解析 `data[].id`，缺 data 数组或非字符串 id fail-fast）+ 响应上限 `MAX_MODELS_BYTES=256KB`（readAllBytes 参数化，balance 行为不变）+ `ModelDao.newModelIds` 纯函数（F8 口径）与 `ModelDao.mergeRemote`（调用方持事务，displayName=modelId）。见 Implementation notes。
- Command: cd android && ./gradlew :ai:testDebugUnitTest :data:testDebugUnitTest
- Exit code: 0
- Passing evidence: artifacts/evidence/T-086-green.txt（JVM 13 用例全过：AiGatewayModelListTest 8 + ModelDaoMergeTest 5）；真机补充 artifacts/evidence/T-086-check.txt 所载 ModelListMergeInstrumentedTest 5/5（ALP-AN00 - 13，真解码器 + 真库合并 + [OFF] 不写库）。
- Regression evidence: artifacts/evidence/T-086-check.txt（./gradlew check assembleDebug，exit 0，无 FAILED）；artifacts/evidence/T-086-no-kotlin.txt（VERDICT: PASS，exit 0）。

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 修改 :ai 网关（共享区），与其它任务串行。
- 实现者禁读 RikkaHub 源码。
- 关键决策：
  - listModels 放进 AiGateway 与 checkBalance 同构（同步、设置屏工作线程调用），复用既有 Transport.get（Bearer 注入在 HttpsTransport，语义不变）；Models 结果值失败时 ids 恒空——调用方「只在 ok() 时合并」即结构性获得 [OFF] 失败不落库。
  - 响应上限复用余额路径的「拒收不截断」读法：readAllBytes 参数化 maxBytes，balance 传 16KB、models 传 256KB，行为各自不变。
  - 合并决策拆为 :data 纯函数 ModelDao.newModelIds（JVM 可测：大小写精确、远端序、远端内部重复静默跳过、永不缩减库内列表）+ mergeRemote 落库（事务归调用方，displayName 取 wire id）。
  - platform org.json 解析按仓库约定由设备测试覆盖（ModelListMergeInstrumentedTest：真解码器 + 真库，5/5 于 ALP-AN00 - 13）；JVM 侧 decoder 以 scripted double 注入。
  - AiGateway 构造器新增 ModelListDecoder 参数（镜像 BalanceDecoder），5 处既有调用点同步更新，SSE/流式语义零改动。
  - 行数 119/200（artifacts/evidence/T-086-linecount.txt）；无新依赖、Java-only、UI 未动。
- 关键决策：
  - `listModels` 放进 `AiGateway` 与 `checkBalance` 同构（同步、设置屏工作线程调用），复用既有 `Transport.get`（Bearer 注入在 `HttpsTransport`，语义不变）；`Models` 结果值失败时 ids 恒空——调用方"只在 ok() 时合并"即结构性获得 [OFF] 失败不落库。
  - 响应上限复用余额路径的"拒收不截断"读法：`readAllBytes` 参数化 maxBytes，balance 传 16KB、models 传 256KB，行为各自不变。
  - 合并决策拆为 `:data` 纯函数 `ModelDao.newModelIds`（JVM 可测：大小写精确、远端序、远端内部重复静默跳过、永不缩减库内列表）+ `mergeRemote` 落库（事务归调用方，displayName 取 wire id）。
  - platform `org.json` 解析按仓库约定由设备测试覆盖（`ModelListMergeInstrumentedTest`：真解码器 + 真库，5/5 于 ALP-AN00 - 13）；JVM 侧 decoder 以 scripted double 注入。
  - `AiGateway` 构造器新增 `ModelListDecoder` 参数（镜像 BalanceDecoder），5 处既有调用点同步更新，SSE/流式语义零改动。
  - 行数 119/200（artifacts/evidence/T-086-linecount.txt）；无新依赖、Java-only、UI 未动。

## Verification report

- Verdict: PASS
- Evidence: independent verifier 2026-09-23：RED/GREEN 命令一致、RED exit 1（22 缺符号）→GREEN exit 0（13 JVM + 真机 5/5）；/models 端点、Key 只经传输边界、mergeRemote 调用方事务内均属实；119 行合规；acceptance 漏勾已由 leader 补勾。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除性断言；纯新增或新增面板，既有行为回归由全量 check 覆盖（详见 GREEN 回归证据）。
- Gaps: 无行为缺口
- Risks: fetch() 无 String 密钥副本问题（该风险属 087）；超大响应 256KB 拒收不截断已测

## Handoff notes

- Completed: RED（exit 1，22 编译错误）→ GREEN（JVM 13 用例 exit 0）→ 真机 ModelListMergeInstrumentedTest 5/5 → `./gradlew check assembleDebug` exit 0 → no-kotlin guard VERDICT: PASS。证据：T-086-red/green/check/no-kotlin/linecount.txt。生产行数 119/200。
- In progress: 无（待 verifier 验收）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: verifier 出验收报告；T-087/088 UI 消费 `AiGateway.listModels` + `ModelDao.mergeRemote`（在 ok() 后调用合并，合并包在调用方事务内）。
- Watch-outs: 不得为拉取便利放宽鉴权头/密钥边界。
