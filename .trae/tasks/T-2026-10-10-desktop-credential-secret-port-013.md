# Treelearn Harness Task: 桌面凭据端口——Windows 凭据管理器（JNA）

id: T-2026-10-10-013
title: 桌面凭据端口——Windows 凭据管理器（JNA，禁明文）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 260
shared_code_touched: false
depends_on: [T-2026-10-10-web-runtime-feasibility-002.md, T-2026-10-10-desktop-config-stores-012.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: API Key 必须存 Windows 系统凭据（T-002 决策：Windows 凭据管理器/DPAPI 经 JNA Apache-2.0；未验证前不使用明文或浏览器存储）；Provider 表不得有 Key 列（T-009 已保证）。
- Users/scenarios: 设置页保存/替换/删除 Key；服务启动装配读取；查询只返回脱敏状态（`hasKey`）。
- Expected outcome: `SecretPort` 接口 + `WindowsCredentialSecretPort`（CredWriteW/CredReadW/CredDeleteW，generic credential，`CRED_PERSIST_LOCAL_MACHINE`，Blob=UTF-8）；缺失→空；损坏/权限失败→类型化错误（`SecretAccessException{code}`），**绝不回退明文**；日志/返回对象不含 Key。
- Core flow and reversibility: 保存 → 系统凭据写入 → 重读/跨进程重读 → 删除；可回退（删除凭据）。
- Inputs/outputs: 输入=target（`TreeLearn-Web/provider/<uuid>` 前缀元数据）+ `char[]` 秘密；输出=Optional<char[]>（仅显式 read）/布尔状态。
- State and exception handling: 不存在→empty；拒绝访问→`ACCESS_DENIED`；损坏→`MALFORMED`（或按实测可读则记录）；失败不静默。
- Constraints and trade-offs: 仅 `jna`（5.19.1，T-002 实测许可）优先，手写 Advapi32 接口；若确需 `jna-platform`，须实测解析并记录版本+许可后使用（同 T-002 方式）。不得引入明文兜底、不得写用户目录明文文件。
- AI recommendation and rationale: 单接口 + 单实现 + 进程外重读证据；测试用唯一 target 前缀并在 `@AfterEach` 清理，避免污染真实凭据。
- User choice and custom additions: 已批准方向。
- AI-autonomous decisions: 包名（建议 `com.treelearn.web.credential`）、错误码词汇、target 命名。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-013-*`：RED/GREEN、真实凭据往返、跨进程重读、删除、缺失、覆盖、日志无 Key（grep）、checkstyle。
- Facts: T-002 实测 jna 5.19.1；Windows 凭据管理器可用（本机验证）；Android 对应 `KeystoreSecretStore`（别名 `tl.provider.<uuid>`，桌面 target 前缀不同但语义对应）。
- Assumptions: 本机允许当前用户写 generic credentials（测试会实测）。
- Unknowns: 损坏凭据的可构造性（以实测为准记录）。
- Risks: JNA 调用签名错误 → 以真实往返证明；测试污染真实凭据 → 唯一 target + 清理 + 前缀约束。
- Deferred: 模型/搜索服务的 Key 目标命名（同端口复用）；DPAPI 文件方案（不采用）。
- Non-goals: Provider 表存 Key；浏览器存储；明文文件。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 Windows 凭据管理器适配的 SecretPort：保存/重读/删除/跨进程重读/缺失/覆盖语义与日志无 Key 均以真实验证取证。

## Scope

### In scope
- `com.treelearn.web.credential`：`SecretPort`（save/has/read/delete + target 校验）、`WindowsCredentialSecretPort`（JNA 手写 Advapi32）、`SecretAccessException{code, target}`；target 命名常量与校验（仅允许 `Treelearn-Web/` 前缀，禁止任意字符串）。
- `web/backend/build.gradle` 增加 `implementation 'net.java.dev.jna:jna:5.19.1'`（注释注明 T-002 来源与许可）。
- 测试（真实 Windows 凭据，唯一前缀 target，@AfterEach 清理）：往返（含中文/emoji 键值）、覆盖、删除、缺失→empty、has 不含 Key、`toString` 不泄漏、非法 target 拒绝；跨进程重读证据（第二个 JVM 读取同一 target）。
- 日志证据：save/read 全程日志 grep 无 Key 值。

### Out of scope
- HTTP 端点（Phase 5）；Provider 装配（Phase 4）；DPAPI 文件方案。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 新增平台能力；无既有行为变化。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 对照 `android/data/.../KeystoreSecretStore`（语义注释）；后续 T-013 消费者=Provider 设置用例（Phase 5）。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 桌面 Key 存 Windows 凭据管理器（target 前缀 `Treelearn-Web/`），数据目录不含 Key。
- Authoritative code/config/migration evidence: `web/backend/src/main/java/com/treelearn/web/credential/**`、`build.gradle`。
- Documentation surfaces checked: `web/README.md`（数据目录/凭据段）、准入文档 §2.8（providers 无 Key 列）。
- Documentation files updated: `web/README.md` 凭据一句。
- Producer/consumer/sibling/compatibility paths checked: Android Keystore 语义对照注释。
- Superseded phrases/examples reverse-searched: 「凭据适配方向（Windows 凭据管理器……）未实现」类表述。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同命令 `:test`，记录退出码与计数）；业务行 ≤260。
- [ ] 真实 Windows 凭据：往返（含非 ASCII）、覆盖、删除、缺失→empty、非法 target 拒绝；测试目标全清理（`cmdkey /list` 或 API 复核无残留证据）。
- [ ] 跨进程重读：第二个 JVM 进程读取同一 target 成功（命令与输出留证）。
- [ ] 日志与返回对象无 Key：save/read 日志 grep 无秘密原文；`SecretAccessException`/状态对象不含秘密。
- [ ] 无明文兜底路径（grep 证明无文件写入秘密；失败抛类型化错误）。
- [ ] checkstyle 通过；独立 verifier PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `WindowsCredentialSecretPortTest`。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 1
- Observed failure: 缺类编译失败（`SecretPort`/`WindowsCredentialSecretPort` 不存在）
- Failure reason validated: 缺失类

### GREEN — minimum implementation
- Implementation: SecretPort + Windows 实现 + JNA 依赖。
- Command: `.\gradlew.bat :test --rerun-tasks`（cwd=web/backend）
- Exit code: 0
- Passing evidence: 87 passed / 0 failed（新增 7 用例）
- Regression evidence: 既有测试全绿（80→87）

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- CredReadW 返回的 blob 指针须 `CredFree`；本地 byte[] 用后清零（尽力而为，Java 限制如实注释）。
- target 允许字符与长度按 Credential Manager 限制校验并测试。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）
- Evidence: verifier 自跑 `:test --rerun-tasks` 87/0、`check` EXIT=0；**自行编写临时探针**完成写→读→删一轮（`has=true match=true length=12`，含中文/emoji），`cmdkey /list` 写入期命中 1、删除后 0 残留；第二 JVM 跨进程读取 `present=true length=12`（不打印秘密）。证据 `artifacts/evidence/T-2026-10-10-013-{red,green,checkstyle,cross-process,cleanup,no-key-in-logs}.txt`。
- JNA 审查（verifier）：`CredFree` 释放无泄漏；blob 空/非法大小 → MALFORMED；UTF-8 正确；`CRED_PERSIST_LOCAL_MACHINE=2`；错误映射 `ERROR_NOT_FOUND(1168)→empty`、`ERROR_ACCESS_DENIED(5)→ACCESS_DENIED`、其它→UNEXPECTED。
- 验收逐条：往返（非 ASCII）/覆盖/删除/缺失→empty/非法 target 拒绝全部通过；`has` 不返回秘密、异常信息不含秘密；日志 grep 秘密 canary 0 命中；无 `java.io`/文件写入/日志 API（无明文兜底）；仅用 `jna:5.19.1`（未用 jna-platform）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；7 新用例。
- 规模：verifier 独立计数 238（实现自述 250）≤260。
- Gaps: 损坏凭据（非法 UTF-8）当前以 REPLACE 解码不抛 MALFORMED——verifier 判为非阻塞（损坏不可构造、任务已列为 unknown），如实记录；如需严格化可在后续任务加字节校验。
- Risks: 低——`has` 仅表存在性（语义正确）。

## Handoff notes

- Completed: 实施 + 独立 verifier PASS（2026-10-10）。产物：`com.treelearn.web.credential` 四类 + jna 5.19.1；87 项测试全绿；跨进程与无泄漏证据齐备；无明文兜底。
- 验收后卫生修复（leader 应用，2026-10-10，事后记录）：跨进程用例原先把证据写回仓库 `artifacts/evidence/`，导致每次跑测试都把工作树改脏（并发任务据此报「越界改动」）。已改为写入 `web/backend/build/evidence/`（构建目录），仓库内那份保留为已提交快照；测试断言未变，改后 `:test --tests com.treelearn.web.credential.*` BUILD SUCCESSFUL、工作树不再产生该文件变更。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-021（桌面 AI 网关适配）；Phase 4 生成协调器装配 Provider + 本端口取 Key。
- Watch-outs: 绝不引入明文兜底；测试凭据目标必须清理（前缀 `Treelearn-Web/provider/`）；证据文件只进构建目录。