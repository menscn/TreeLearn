# Treelearn Harness Task: Keystore 多提供商密钥存取

id: T-2026-09-23-084
title: Keystore 多提供商密钥存取
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: false
depends_on: [T-2026-09-23-providers-models-spec-079.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务把单 DeepSeek Key 扩展为按提供商命名空间的多 Key 存取。
- Problem/background: 现 `KeyVault.java` 只管理一个 DeepSeek Key（Android Keystore AES-GCM）；多供应商需要每提供商独立密钥条目。
- Users/scenarios: 用户为不同提供商各存一个 Key；旧 Key 自动迁移到默认 DeepSeek 提供商条目。
- Expected outcome: KeyVault 支持按提供商 ID 写/读/删密钥；首次启动将存量单 Key 迁移为 DeepSeek 默认条目；明文仍不入 SQLite/日志/备份。
- Core flow and reversibility: 迁移单向（旧条目读出→写入新命名空间→删旧条目），失败保留旧条目不删。
- Inputs/outputs: 输入 SPEC-079 的密钥边界条款；输出 KeyVault 扩展与单测。
- State and exception handling: Key 不存在返回空不抛异常（沿用 Key 守卫语义）；Keystore 不可用时沿用现有降级路径。
- Constraints and trade-offs: 密钥边界（sql/安全规则）不可降低；Keystore 条目数无上限假设要验证。
- AI recommendation and rationale: 迁移逻辑放首次读新接口时惰性执行，避免额外启动钩子。
- User choice and custom additions: 无。
- AI-autonomous decisions: 命名空间格式（如 `provider/<id>/apikey`）按 079 规格。
- User-confirmation decisions: 无（密钥边界已在规格冻结）。
- Acceptance evidence: RED/GREEN 单测 + 密钥不落盘扫描证据。
- Facts: T-005 keystore-secret-settings 已交付单 Key 体系。
- Assumptions: 079 拍板完成。
- Unknowns: 部分 OEM Keystore 条目数限制（真机项）。
- Risks: 迁移失败导致用户 Key 丢失。
- Deferred: 无。
- Non-goals: 不做 Key 云同步/导出。
- Skipped non-critical questions and reason: 条目前缀格式属实现细节。

## Goal

交付按提供商命名空间的多 Key 存取与存量单 Key 迁移，密钥边界不降低。

## Scope

### In scope

- KeyVault 多命名空间读写删、旧 Key 惰性迁移、单测。

### Out of scope

- UI（087）；schema（083）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 旧接口调用（单 Key 读）保持兼容或统一改造并回归 ChatPresenter/Key 守卫。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `app/ui/KeyVault.java`、`KeySnapshot.java`、ChatPresenter/SettingsPresenter 消费点。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: Key 存储结构（单条目→命名空间多键）。
- Authoritative code/config/migration evidence: KeyVault 实现与迁移代码。
- Documentation surfaces checked: T-005 交付文档、安全边界规则。
- Documentation files updated: T-005 相关文档备注新命名空间。
- Producer/consumer/sibling/compatibility paths checked: Key 守卫、Settings 页 Key 入口。
- Superseded phrases/examples reverse-searched: 「单 DeepSeek Key」相关表述由 079 基线回写统一。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：多 Key 读写删与旧 Key 迁移断言。（RED exit 1 六处缺符号→GREEN JVM+真机 5 例 exit 0，verifier 复核）
- [x] 明文不出现于 SQLite/日志/备份（沿用 T-005 验证方式）。（T-084-source-checks.txt 全 no-match 含正控，覆盖新代码路径）
- [x] 现有 Key 守卫与设置页 Key 流程回归全绿。（check-full exit 0）
- [ ] 明文不出现于 SQLite/日志/备份（沿用 T-005 验证方式）。
- [ ] 现有 Key 守卫与设置页 Key 流程回归全绿。

## TDD execution record

### RED — failing test first
- Test/scenario: JVM `ProviderKeyNamespaceTest`（别名规则 `tl.provider.<uuid>`、不同提供商别名不同、空 id 拒绝）+ 真机 `ProviderSecretStoreInstrumentedTest`（多别名读写删互相独立、按提供商存在性布尔、旧 Key 迁移恰一次且幂等、失败迁移保留旧条目、明文不落盘扫描含正控）
- Command: `cd android && ./gradlew.bat :data:testDebugUnitTest --tests "*ProviderKeyNamespaceTest*" :app:compileDebugAndroidTestJavaWithJavac --console=plain`
- Exit code: 1
- Observed failure: `:data` 编译失败，6 个「找不到符号: 变量 ProviderKeyNamespace」错误；`:app:compileDebugAndroidTestJavaWithJavac` 因同一缺失 API 未实现（证据 `artifacts/evidence/T-084-red.txt`）
- Failure reason validated: 全部错误均指向本任务尚未实现的多提供商 API（新类与新方法不存在），无一处为测试自身缺陷

### GREEN — minimum implementation
- Implementation: 新增 `ProviderKeyNamespace`（纯别名派生，无 Android 依赖）；`KeystoreSecretStore` 内部参数化为 `(preferenceKey, alias)` 并新增 per-provider `hasSecret/save/read/delete` 与 `migrateLegacySecret`（读旧→写新→确认成功后删旧，任何失败保留旧条目，幂等）；`AndroidSettingsStore` 旧单 Key 接口全部映射到内置 DeepSeek 固定 UUID 命名空间并实现新 `KeyVault.isConfigured(String)`；`AiGatewayStreams` 构造器增加提供商 id，按命名空间取 Key；`AppContainer` 注入 `BUILTIN_DEEPSEEK_PROVIDER_ID`
- Command: `cd android && ./gradlew.bat :data:testDebugUnitTest --tests "*ProviderKeyNamespaceTest*" :app:compileDebugAndroidTestJavaWithJavac --console=plain`
- Exit code: 0
- Passing evidence: 同命令 GREEN 通过（`T-084-green-jvm.txt`）；另有真机 `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.ProviderSecretStoreInstrumentedTest` exit 0（`T-084-green-instrumented.txt`，归回归证据）。JVM 别名测试 3 例通过；真机 ALP-AN00（Android 13）5 例通过：多提供商独立读写删、存在性布尔、迁移恰一次（旧信封与旧别名移除、新别名在位）、损坏信封迁移失败保留旧条目、明文扫描空且正控命中
- Regression evidence: `./gradlew.bat check assembleDebug` EXITCODE=0（`T-084-check-full.txt`）；no-kotlin guard PASS 13 OK（`T-084-no-kotlin-guard.txt`）；T-005 式源码扫描（secret 包无日志/SQLite/Bundle，触碰文件无日志）全 no-match（`T-084-source-checks.txt`）；行数证据 `artifacts/evidence/.t084-linecount.txt`

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 实现者禁读 RikkaHub 源码。
- 密钥边界（不入 SQLite/日志/备份）是硬约束，不可为迁移便利降低。

## Verification report

- Verdict: PASS（verifier-084 初判 PARTIAL，两项缺口均为文档级且 verifier 明示可回填：①acceptance 未勾选——已由 leader 依 verifier 复核结论勾选；②证据文件未回显命令行——已在 T-084-red.txt / T-084-green-jvm.txt 追加诚实补注，命令行以本文件 RED/GREEN 记录为权威）
- Evidence: RED `gradlew :data:testDebugUnitTest --tests "*ProviderKeyNamespaceTest*" :app:compileDebugAndroidTestJavaWithJavac` exit 1（六处缺符号 ProviderKeyNamespace）；同命令 GREEN exit 0（`T-084-green-jvm.txt`）+ 真机 instrumented 5 例 exit 0（`T-084-green-instrumented.txt`，迁移恰一次幂等/损坏信封保留旧条目/明文扫描含正控）；`check assembleDebug` exit 0（`T-084-check-full.txt`）；no-kotlin PASS 13 OK；行数 94（`.t084-linecount.txt`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言删除；KeyVault 保留布尔存在性契约（`isConfigured(String)`），不返回密文。
- Gaps: OEM Keystore 条目数上限未知（真机项）；API 36 模拟器轮次未跑（沿既有口径）；证据命令行回显流程缺口已补注，后续任务须带回显。
- Risks: 别名前缀 `tl.provider.` 与规格一致；同名 DeepSeek 边角经论证天然隔离（固定合成 UUID）。

## Handoff notes

- Completed: 2026-09-23 android-dev 完成 RED→GREEN（JVM + 真机）→全量回归→guard→回填，status=verifying。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: verifier 判 Verdict（核对 RED/GREEN 五键、真机证据与行数口径）；后续 T-085 网关参数化时将 `AiGatewayStreams.keyProviderId` 从固定内置 UUID 改为随请求所选提供商。
- Watch-outs: ① 旧单 Key 首次读新接口时惰性迁移，迁移失败保留旧条目（宁重复不丢失），设置页可能出现「已配置但不可读」的 UNREADABLE 态，属既有守卫语义；② 部分 OEM Keystore 条目数上限未知（每提供商一个 AES key，条目数随提供商增长），真机项遗留；③ 真实引擎证据在单一真机（ALP-AN00 / Android 13）取得，API 36 模拟器轮次未跑（与既有任务 device-rounds 口径一致）；④ 工作区含其他 lane 未提交改动，本任务 diff 仅限上文清单文件。
