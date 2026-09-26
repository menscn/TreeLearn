# Treelearn Harness Task: 完成安全、Release 与核心流加固

id: T-2026-09-20-018
title: 完成安全、Release 与核心流加固
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: true
depends_on: [T-2026-09-20-home-settings-ui-012.md, T-2026-09-20-chat-reasoning-answer-actions-013.md, T-2026-09-20-navigation-archive-delete-014.md, T-2026-09-20-search-unread-reading-position-015.md, T-2026-09-20-tree-canvas-tablet-ui-017.md, T-2026-09-21-real-data-path-wiring-032.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 将所有切片收敛为可侧载 release APK，并完成隐私、日志、网络、恢复和核心用户流加固。
- Problem/background: 单任务测试通过不代表 release 配置、模块连接和真实生命周期正确。
- Users/scenarios: 用户在个人设备安装 APK，配置 Key，创建/分叉/搜索/归档主题，后台切换并从中断恢复。
- Expected outcome: release 配置、Telemetry no-op、脱敏日志、HTTPS/备份守门、提示词行为集成测试和可安装 APK。
- Core flow and reversibility: 安装后从空状态走完整流程；卸载清除本机数据；应用内删除按产品规则执行。
- Inputs/outputs: 输入各模块实现、提示词行为样例和临时测试 Key；输出使用仓库外专用侧载证书签名的可安装 release APK、校验摘要、脱敏行为验证报告和启动命令。
- State and exception handling: 离线、错误码、旋转、后台、进程死亡、低存储和长内容均纳入核心回归。
- Constraints and trade-offs: Java-only、API 28+、release 无远程遥测、无正文日志、无备份、无明文流量。
- AI recommendation and rationale: 用端到端测试连接状态边界，再做最小发布配置，不在此任务扩功能。
- User choice and custom additions: 个人设备直接安装 APK；后台仅尽力继续。
- AI-autonomous decisions: release TelemetrySink 使用 no-op；debug 也只允许脱敏元数据。
- User-confirmation decisions: 技术方案全部 P0/P1 与本地安全边界。
- Acceptance evidence: lint/unit/instrumentation/release build、安装启动、secret scan、依赖图和真实核心流。
- Facts: 技术方案 §14～§16 是权威。
- Assumptions: 验收设备/模拟器与临时 DeepSeek Key 可用。
- Unknowns: 应用商店正式签名证书仍由用户未来发布时管理；本期只建立仓库外的个人侧载签名。
- Risks: release-only 混淆、网络安全配置和生命周期回归。
- Deferred: 商店发布、崩溃上报和云端遥测。
- Non-goals: 不增加新产品功能或后端。
- Skipped non-critical questions and reason: 商店渠道未指定，当前只要求个人侧载。

## Goal

产出在目标设备可安装、核心流可复现且满足隐私/Java-only 守门的 release APK。

## Scope

### In scope
- release build、网络/备份/log/telemetry 配置、核心端到端测试、安装启动、性能与泄密检查。

### Out of scope
- 商店签名托管、云服务、远程遥测和新功能。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 集成现有切片并强化 release；不改变已确认业务语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): app container 连接 core/data/ai/tree-layout；debug/release、API 36、手机/平板均执行。
- Native bridge or independent-client compatibility/update requirements: DeepSeek HTTPS 与 Android 系统生命周期为外部边界。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 构建变体、APK、启动、网络/备份/日志/遥测和已知限制。
- Authoritative code/config/migration evidence: Gradle/Manifest/network/backup/proguard、测试与安装输出。
- Documentation surfaces checked: Android README、Android 技术方案、提示词设计 V1.0、PRD F21/隐私/错误处理。
- Documentation files updated: 写明构建、测试、安装、临时 Key 注入和已知限制。
- Producer/consumer/sibling/compatibility paths checked: 所有模块入口、release/debug、手机/平板和进程恢复。
- Superseded phrases/examples reverse-searched: 云备份、远程遥测、明文流量和后台保证续传。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 只同步当前运行事实。

## Acceptance criteria

- [x] 同一端到端目标测试命令先 RED 后 GREEN；完整创建、reasoning 展开、分叉、重置、搜索、归档、画布流程通过。
  - `CoreFlowE2eTest` 8 步全链路（`artifacts/evidence/T-018-e2e-2026-09-24.txt`）。验证性夹具无自然 RED，按 T-041 先例以变异验证补实：突变 `ChatWiring.java:266` AI 轮序号 → e2e FAILED（ordinal 断言）→ 还原 PASS（`T-018-e2e-mutation-2026-09-24.log`）。
- [x] lint、所有 unit/instrumentation、release assemble、依赖方向、Java-only guard 和 secret/content log scan 返回 0。
  - lint 0 error、`check` EXITCODE=0（P3-B4-gates）、全量 :app 单测 EXITCODE=0（本日两次）、Java-only guard PASS、secret/content scan 报告（`T-018-scan-2026-09-24.txt`）、release assemble EXITCODE=0（`T-018-release-apk-2026-09-24.txt`）。
  - 〔deferred — emulator not used this round (user instruction 2026-09-21)〕instrumentation 在真机/模拟器上的运行。
- [x] Manifest/Network Security/backup 配置证明仅 HTTPS、禁备份/迁移；release TelemetrySink 为 no-op。
  - Manifest :16-:25 配置 grep 原文在案（`T-018-scan`）。release no-op 本日落地：`QuietTelemetry` + `TelemetrySink.forVariant` 单一判定点，`TelemetrySinkVariantTest` 3 例 RED→GREEN——这是执行期发现并修复的**真实缺口**（原实现两变体均装配 LogcatTelemetry）。
- [x] release APK 使用仓库外的 Treelearn 专用个人侧载 keystore 签名，`apksigner verify --print-certs` 通过；在 API 36 手机竖屏和代表性平板横屏安装启动，无崩溃、重叠或空白 Canvas。（用户 2026-09-21：验收环境为 API 36/36.1）
  - 新鲜 APK（2026-09-24，917 135 B，SHA-256 0ed7813a…76819b7）+ apksigner EXITCODE=0、证书指纹与首验一致（`T-018-release-apk-2026-09-24.txt`）。
  - 〔deferred — emulator not used this round (user instruction 2026-09-21)；用户自行侧载验证〕设备安装启动与平板横屏。
- [ ] 真实 DeepSeek 冒烟验证两个发布模型、thinking on/off、版本化 system 被接受、历史 reasoning 进入后续请求和派生 JSON 可解析；证据不含 system、Key、问题、reasoning 或 answer 正文。
  - 〔deferred — key not provided, user decision 2026-09-24〕用户裁定无 Key、按 deferred 收口。
- [ ] 受控行为集覆盖入门/专业问题、错误前提、可假设与必须澄清的歧义、时效信息、类比边界和 reasoning 折叠；按结构性 rubric 判定，不逐字匹配，任一明显编造、内部协议泄露或最终回答依赖展开 reasoning 均为 FAIL。
  - 〔deferred — key not provided, user decision 2026-09-24〕同上，依赖真实模型调用。
- [x] secret/content log scan 同时证明 SQLite、SharedPreferences、日志、异常和报告不含 system content；只允许 PromptPurpose/promptPolicyVersion 等脱敏元数据。
  - 静态扫描全过（`T-018-scan`）：日志 2 处皆元数据、异常零内容拼接、SQLite `role CHECK IN ('USER','AI')` 使 SYSTEM 行不可表示、SharedPreferences 唯一写方为草稿存储（用户自有草稿，本地优先设计内）。〔运行时扫描随设备项 deferred〕

## TDD execution record

### RED — failing test first
- Test/scenario: `TelemetrySinkVariantTest`（release 装配 QuietTelemetry no-op / debug 保留 LogcatTelemetry / QuietTelemetry 对全部 Event 可静默调用）。自治决策「release TelemetrySink 使用 no-op」此前未落位——AppContainer 两变体都装配 LogcatTelemetry，这是本任务执行期发现的**真实缺口**，测试先于实现书写。
- Command: `./gradlew :app:testDebugUnitTest --tests '*TelemetrySinkVariantTest*' --console=plain`
- Exit code: 1
- Observed failure: 6 个编译错误——`TelemetrySink`/`QuietTelemetry` 不存在（含 `QuietTelemetry.INSTANCE` 两处符号缺失）。该命令在实现落地前运行时，于其依赖阶段 compileDebugUnitTestJavaWithJavac 失败（即测试编译不过）；日志以同一编译阶段的更快入口 `:app:compileDebugUnitTestJavaWithJavac` 捕获，错误集相同（`artifacts/evidence/T-018-telemetry-red-2026-09-24.log`）。
- Failure reason validated: 缺失的正是被测的变体选择工厂与 no-op sink，非测试笔误。

### GREEN — minimum implementation
- Implementation: `wiring/QuietTelemetry.java`（release 空实现，唯一 INSTANCE）、`wiring/TelemetrySink.java`（`forVariant(boolean)` 单一判定点）、`AppContainer.java:163-170` 以 `FLAG_DEBUGGABLE` 判变体并经 `forVariant` 注入；删除 AppContainer 无用 import。
- Command: `./gradlew :app:testDebugUnitTest --tests '*TelemetrySinkVariantTest*' --console=plain`
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL；日志 /tmp/t018-green.log 摘录与汇总见 `artifacts/evidence/T-018-scan-2026-09-24.txt` [SCAN] release TelemetrySink 节。
- Regression evidence: 全量 `:app:testDebugUnitTest` BUILD SUCCESSFUL（EXITCODE=0），debug 变体行为不变。

### REFACTOR — optional
- Cleanup: AppContainer 内联旗标计算带注释说明 BuildConfig 未启用、以 ApplicationInfo.FLAG_DEBUGGABLE 为运行时判据。
- Regression evidence: 同上全量单测。

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 例外（端到端目标测试，2026-09-24 登记，**用户已追认**）
- Reason: 端到端测试是对既有行为的验证性夹具，无自然 RED（首跑即 GREEN；更早的一次编译失败是测试自身 import 笔误，非有效 RED）。按 T-041 先例以**变异验证**补实：突变 `ChatWiring.java:266` AI 轮序号 `+1→+2`，e2e 立即 FAILED（ordinal 断言），还原后 PASS——测试对被钉行为有杀伤力。
- User approval: 2026-09-24 用户裁定「追认例外」。
- Alternative verification: `artifacts/evidence/T-018-e2e-2026-09-24.txt`（8 步全链路 + 变异杀伤 + 全量回归）与 `T-018-e2e-mutation-2026-09-24.log`

## Implementation notes

- 组合根所有权：`AppContainer.java` 与 `MainActivity.java` 由 T-2026-09-21-032（真实数据路径接线）独占。本任务只做安全/release/守门/端到端测试，不并行编辑这两个文件；确需改动时先经 leader 交接。

- 未提供正式证书时，生成 Treelearn 专用个人侧载 keystore 并保存在仓库外；口令只进入环境变量或未跟踪的本地配置，绝不打印或提交。记录 keystore 路径、证书 SHA-256 和 APK SHA-256，不记录口令。
- 正式商店签名材料、个人侧载 keystore、测试 Key 和 local.properties 均不入库。
- 进程死亡测试必须用系统终止方式，不以 Activity recreate 冒充。
- release 验证发现功能缺陷时回到所属任务修复并补 RED/GREEN，不在本任务堆叠大改。
- 真实模型行为有非确定性；至少对失败样例复跑并保留脱敏结构结论。不能用单个“看起来不错”的截图替代 T-020 确定性契约测试。

## Verification report

- Verdict: PASS（2026-09-24 收口；带三项已登记 deferred：真实 Key 冒烟〔key not provided, user decision 2026-09-24〕、设备安装启动/平板横屏〔emulator not used this round, user instruction 2026-09-21〕、运行时内容扫描〔需设备〕）
- Evidence: ①`T-018-e2e-2026-09-24.txt`（端到端 8 步 + 变异杀伤 + 全量回归）；②`T-018-scan-2026-09-24.txt`（secret/content scan + 配置证明 + release no-op RED→GREEN）；③`T-018-release-apk-2026-09-24.txt`（新鲜签名 APK + apksigner）；④`T-018-telemetry-red-2026-09-24.log`；⑤leader 2026-09-21 的 lint/check/guard/首版 APK 证据。全部证据文件自带命令与退出码。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无既有断言删除。新增 `TelemetrySinkVariantTest`（3 例）与 `CoreFlowE2eTest`（1 例 8 步）。release 遥测从「脱敏 logcat」收紧为「no-op」是任务自治决策的落实，debug 行为不变。
- Gaps: 真实模型行为（验收项 5/6）无 Key 未验证——release ready 的「模型行为」维度留空，由 deferred 条目显式记录，不虚报。
- Risks: 设备/OEM 差异与供应商在线可用性；QuietTelemetry 使 release 无任何生命周期日志，线上排障只能靠 SQLite 内的 ai_generation 行（设计内的取舍）。

## Handoff notes

- Completed: 非设备项全部完成（2026-09-24，见 Verification report 与 Handoff [执行 2026-09-24]）；Key 冒烟与设备项按用户裁定 deferred。
- In progress: 无。
- Blocker: 无（原「等 Key」阻塞已由用户 2026-09-24 裁定转为 deferred 收口）。
- Unblock condition: 无。若未来提供 Key 或安排设备轮次，验收项 4 后半/5/6 可单独补验。
- Next steps: 交独立 verifier 复核（如走 verifier 轮次）；APK 已就绪供用户自行侧载。
- Watch-outs: 未覆盖真实分支不得声称 release ready——本任务的 release ready 声明仅限「配置、隐私、构建、JVM 行为」维度；QuietTelemetry 使 release 无生命周期日志，排障靠 ai_generation 表行。
- [leader 2026-09-21] 本轮由主会话完成了本任务中**不依赖设备**的部分：
  - 新增 `android/app/src/main/res/xml/network_security_config.xml`（base-config 禁明文、只信任系统锚点、无 debug 放宽），并在 `AndroidManifest.xml` 声明 `android:networkSecurityConfig="@xml/network_security_config"`；同时改写了 manifest 里「网络配置尚无、属于 T-018」的旧注释，避免留下文档漂移。
  - `android/app/build.gradle` 的 `signingConfigs` 顺序缺陷已修（原写法使**每个** Gradle 任务在配置期失败）；`apksigner verify --print-certs` 退出码 0，证书 SHA-256 `d8670c863d692a90e21b7a895cf0f717bc8a1cc4ba82a48ff771f09a8780c561`。
  - 产出 `android/app/build/outputs/apk/release/app-release.apk`（429964 字节，SHA-256 `05CDD77999DAC8388400DC84902425A52B8CA3E8609F5E450306CA67B9FB892B`），证据 `artifacts/evidence/T-018-release-apk-2026-09-21.txt`。
  - 构建期守门：`\gradlew.bat check` 退出码 0；Java-only guard `VERDICT: PASS (13 OK line(s))`；lint 0 error。
- [leader 2026-09-21] **未完成**（不得读作通过）：release `TelemetrySink` no-op 的落位与验证、端到端测试的**执行**（`androidTest-results/connected/debug` 为 0 文件）、secret/content log scan 的正式报告、安装启动与手机/平板实机验收、真实 DeepSeek 冒烟（无 Key）。设备相关一律 `deferred — emulator not used this round (user instruction 2026-09-21)`。
- [leader 2026-09-21] 注意：release APK 虽然签名有效，但**应用仍装配 `PendingHomeWiring`/`PendingChatWiring`**，首页与对话数据路径未接线（见 T-032），装到设备上核心流程走不通。〔2026-09-24 注：T-032 已 verified，接线早已落地，此条为历史记录〕
- [执行 2026-09-24] 非设备项推进（证据：`T-018-scan-2026-09-24.txt`、`T-018-e2e-2026-09-24.txt`）：
  - **发现并修复真实缺口**：release TelemetrySink no-op 未落位（两变体都装配 LogcatTelemetry）→ `QuietTelemetry` + `TelemetrySink.forVariant` 单一判定点 + `AppContainer` 按 `FLAG_DEBUGGABLE` 注入，RED→GREEN + 全量回归。
  - **端到端目标测试落地**：`CoreFlowE2eTest` 8 步（创建/追问/思考展开/分叉/重置/搜索/归档/画布），前步 id 后步消费；变异验证补 RED 缺席（T-041 先例）。
  - **secret/content log scan**：日志 2 处皆脱敏元数据；SharedPreferences 唯一写方为草稿存储；异常消息零内容拼接；SQLite `role CHECK IN ('USER','AI')` 使 SYSTEM 行不可表示、schema javadoc 明令禁止 system 正文落列；Manifest 备份/TLS 配置 grep 原文在案。
- **剩余（不构成 PASS）**：① 真实 DeepSeek 冒烟 + 受控行为集（验收项 5/6）——等用户临时 Key（环境变量，不入库不入日志）；② 设备安装启动与手机/平板实机（验收项 4 后半）——deferred（user 2026-09-21）；③ 运行时 SQLite/SharedPreferences 内容扫描（需设备）；④ release APK 为 T-032 接线前产物，如需可复验 freshness。
