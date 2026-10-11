# Treelearn Harness Task: android/ai 纯协议抽取到 web/shared/ai-protocol

id: T-2026-10-10-017
title: android/ai 纯协议抽取到 web/shared/ai-protocol（两端唯一实现）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 120
shared_code_touched: true
depends_on: [T-2026-10-10-shared-module-relocation-005.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Problem/background: 开发方案 `web/shared/ai-protocol`「可移植的请求/提示词/SSE 纯 Java 能力，平台代码不进入（抽取任务待建）」；Phase 4 桌面生成核心必须复用同一份 prompt/SSE/错误分类判定，避免两套实现漂移（矩阵：提示词策略「同用途、同输入的判断收敛到共享规则」）。
- Users/scenarios: 桌面生成核心（T-019+）直接消费共享网关/提示词；Android 行为不变。
- Expected outcome: 新建 `web/shared/ai-protocol`（java-library Java 17，`api project(':core')`）；把 `android/ai` 中**纯 JDK/core 依赖**的类迁入（包名不变：`com.treelearn.ai.prompt`、`com.treelearn.ai.gateway`）；Android 保留平台耦合实现（`DeepSeekGatewayFactory`（android.util.Log）、`JsonChatRequestBody`（android.util.JsonWriter）、`ProviderJson`（org.json））并新增 `implementation project(':ai-protocol')`；两端 settings 以 projectDir 引用同一份源码；结构守卫与自检更新；Android 全回归 + Java-only guard PASS。
- Core flow and reversibility: 源码移动 + 构建引用；可回退（git revert 单提交）。
- Inputs/outputs: 输入=现有 android/ai；输出=web/shared/ai-protocol 新模块 + 两端构建引用 + 守卫更新 + 回归证据。
- State and exception handling: 构建失败/引用遗漏 → 以 Android 编译与测试证明；平台耦合判断以真实 import 为准（不凭文件名）。
- Constraints and trade-offs: 不改包名（Android 源码零修改，仅 build.gradle）；不重构行为；不新增第三方依赖；共享树禁止 Kotlin；`JsonChatRequestBody`/`ProviderJson` 的平台实现暂不抽取（桌面侧 Phase 4 用 Jackson 实现，以共享 fixtures 证明请求/流解析等价）。
- AI recommendation and rationale: 一次抽出全部纯类（含 `AiGateway`/`HttpsTransport`/`SseFrameParser`/`AiGatewayError`/`GatewayPorts`/`ProviderEndpoint`/`StreamOutcome` + `prompt/*` 9 个），因为它们只依赖 java.* 与 `:core`（已核 import 清单）；这是「唯一实现」的最大化收益点。
- User choice and custom additions: 已批准方案的共享策略。
- AI-autonomous decisions: 模块内目录/测试迁移、守卫增量。
- User-confirmation decisions: 无新增。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-017-*`：两端构建/测试输出、guard 自检、no-Kotlin、`git status` 范围。
- Facts: `android/ai/src/main/java/com/treelearn/ai/prompt/*`（9 文件）仅依赖 `:core`；`gateway/{AiGateway,AiGatewayError,GatewayPorts,ProviderEndpoint,HttpsTransport,SseFrameParser,StreamOutcome}` 仅依赖 java.*/`:core`/prompt；`ProviderJson` 依赖 org.json、`JsonChatRequestBody` 依赖 android.util.JsonWriter、`DeepSeekGatewayFactory` 依赖 android.util.Log（三者留 Android）。
- Assumptions: T-005 的 projectDir 引用模式可直接套用。
- Unknowns: `android/ai/src/test` 下测试的迁移范围——以真实文件为准。
- Risks: 抽取遗漏平台耦合 → 以 Android 编译全绿证明；守卫未更新 → 结构守卫自检必须过并把新模块纳入。
- Deferred: 桌面侧 JSON 请求体/流解码实现（Phase 4，以共享 fixtures 验证等价）；`JsonChatRequestBody`/`ProviderJson` 的后续抽取（有真实双端消费者时）。
- Non-goals: 修改 Android 行为/接口；抽取平台耦合类；引入新依赖。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

把 `android/ai` 中纯 Java 的提示词/SSE/网关内核抽取为两端共享模块 `web/shared/ai-protocol`，Android 行为与构建通过全回归证明，结构守卫覆盖新模块。

## Scope

### In scope
- 新建 `web/shared/ai-protocol/`（build.gradle + src/main/java/... + 迁移现有测试到 src/test）。
- 移动纯类（包名不变）：`prompt/`（DeepSeekContextEncoder、DeepSeekMessage、DeepSeekPromptRegistry、DeepSeekRequest、DeepSeekRequestAssembler、JsonObjectText、PromptBodiesV1、PromptBudget、PromptBuildException）；`gateway/`（AiGateway、AiGatewayError、GatewayPorts、ProviderEndpoint、HttpsTransport、SseFrameParser、StreamOutcome）。抽取前逐个用 import 复查纯度，发现耦合者留在 Android 并报告。
- `web/backend/settings.gradle` 与 `android/settings.gradle` 增加 `:ai-protocol` projectDir 引用；`android/ai/build.gradle` 加依赖。
- `web/scripts/check-shared-modules.ps1`：把新模块纳入（位置/两端引用/无副本/Kotlin-free），自检（`-Selftest`）保持且增补用例；`web/README.md` 共享模块段同步。
- Android 回归（复刻 T-005 的回归命令集，读 `.trae/tasks/T-2026-10-10-shared-module-relocation-005.md` 确认真实命令）：`:ai` 与受影响模块编译、单元测试、`:app:assembleDebug`；Java-only guard（按 T-005 记录的命令/脚本）。
- web 侧回归：`web/backend` `.\gradlew.bat test`（新模块测试在其中运行）。

### Out of scope
- `web/backend/src/**` 业务代码；桌面网关实现；行为/接口变更；新依赖。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: Android 运行行为与 API 完全不变（仅构建引用与源码位置）；Web 端新增消费者。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/ai`（消费者，build.gradle 变更）；`:app`（间接）；后续 T-019+。
- Native bridge or independent-client compatibility/update requirements: 不适用（无桥接）。
- Behavior-neutral non-applicability reason, if applicable: 纯移动 + 构建引用，无行为变更；等价性由 Android 全回归证明。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 共享模块集合新增 `ai-protocol`（构建事实，非运行时行为）。
- Authoritative code/config/migration evidence: `web/shared/ai-protocol/**`、`android/settings.gradle`、`web/backend/settings.gradle`、`android/ai/build.gradle`。
- Documentation surfaces checked: `web/README.md`（共享模块段）、`android/README.md`（若描述模块结构则同步）、开发方案 §3 目录结构（ai-protocol 行）。
- Documentation files updated: `web/README.md`、`android/README.md`（如涉及）、开发方案目录表。
- Producer/consumer/sibling/compatibility paths checked: T-005 守卫脚本与记录。
- Superseded phrases/examples reverse-searched: 「ai-protocol（抽取任务待建）」。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] `web/shared/ai-protocol` 存在且被两端 projectDir 引用；包名未变；无副本（原位置文件已删）。
- [ ] 抽取类纯度经 import 复查（报告逐文件）+ 两端编译通过；Android 源码除 build.gradle 外零修改（`git diff` 证明）。
- [ ] Android 回归：T-005 同款命令集全绿（构建 + 单元测试 + assembleDebug），记录命令/退出码。
- [ ] Java-only guard PASS（无 Kotlin 文件/插件/依赖）；结构守卫 `check-shared-modules.ps1` 含自检通过并把新模块纳入。
- [ ] web/backend `gradlew test` 全绿（含 ai-protocol 测试，若从 android 迁移了测试则一并运行）。
- [ ] 文档同步（README/方案目录）、`documentation impact` 记录完整。
- [ ] 规模：新增/改动业务行（非移动行）≤120，移动行数另记。
- [ ] 独立 verifier PASS；android-no-kotlin-guard PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 纯移动/重构任务（无行为变更）；替代验证=Android 全回归 + 守卫 + 两端构建。若迁移的测试存在，先确认其在原位置为绿、迁移后在新位置仍绿。
- Command: 见证据
- Exit code: 见证据
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 模块建立与移动完成，回归全绿。
- Command: 见证据
- Exit code: 见证据
- Passing evidence: 见证据
- Regression evidence: 见证据

### REFACTOR — optional
- Cleanup: 按检查整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: tdd-not-applicable（行为中性重构；任务契约允许）
- Reason: 纯移动 + 构建引用；等价性由 Android 全回归 + 守卫证明。
- User approval: N/A（非行为变更）
- Alternative verification: 见上。

## Implementation notes

- 新模块 build.gradle 参照 `web/shared/domain/build.gradle`（java-library、Java 17、UTF-8、release 17、JUnit 4 测试）。
- `:ai-protocol` 用 `api project(':core')`（对外签名暴露 core 类型）。
- 移动前逐文件核对 import（以真实内容为准，不凭文件名判断）。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10）+ PASS（android-no-kotlin-guard，2026-10-10）
- Evidence: verifier 以 `git diff -M --name-status HEAD` 独立确认 25 个文件全部 `R100`（内容零改动，抽查 blob 哈希一致）；纯度 grep 无 `android.*`/`org.json`/第三方；独立复跑 `--rerun-tasks`：Android（`:core:test :tree-layout:test :ai-protocol:test :ai:testDebugUnitTest :app:assembleDebug verifyDependencyDirection`）EXIT=0、web/backend 全量 `test` EXIT=0、守卫 `check-shared-modules.ps1` PASS 8 OK + `-Selftest` 12 例全过、`no-kotlin-check.ps1` PASS 13 OK（57 classpath/3 APK，已含 `:ai-protocol`）。证据 `artifacts/evidence/T-2026-10-10-017-*.txt`。
- 移动清单（18 类 + 7 测试；实测多移动 2 个纯类 `GatewayEndpoints`/`GenerationSink`，为其编译依赖，已说明）；留守 Android：`ProviderJson`（org.json）、`JsonChatRequestBody`（android.util.JsonWriter）、`DeepSeekGatewayFactory`（android.util.Log）。
- 守卫增量真实性：脚本真实纳入 `:ai-protocol`（位置/两端引用/无副本/Kotlin-free）并新增 4a-2 逐类断言 + 4 个自检用例；no-kotlin 脚本模块列表已补 `:ai-protocol`（leader 应用 guard 建议后复跑 PASS）。
- 偏差与处置：① `android/ai/build.gradle` 用 `api project(':ai-protocol')`（`:app` 经 `:ai` 传导引用，抽查 `ChatWiring`/`MessageTranslator` import 证明）；② `android/build.gradle` 的 `moduleDependencyRules`/`pureJvmModules` 同步纳入（方向约束未放松）；③ 文档同步（README/方案 §3）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言改动（纯移动）。
- Gaps: 桌面侧 JSON 请求体/流解码实现待 Phase 4（以共享 fixtures 验证等价）；`JsonChatRequestBody`/`ProviderJson` 的抽取待出现真实双端消费者。
- Risks: verifier 备注——证据 `017-guard.txt` 捕获早于脚本文案微调，结论不受影响；工作树混入并行任务改动，提交须按任务拆分（本次已拆）。

## Handoff notes

- Completed: 抽取完成并双守卫 PASS（2026-10-10）。`web/shared/ai-protocol` 现承载 18 个纯类（包名不变）；两端 projectDir 引用；Android 全回归绿；守卫把新模块纳入（自检 12 例）。
- In progress: commit 由主会话执行（T-017 与 T-011 的 `web/README.md` 改动按任务拆分提交）。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: Phase 4 桌面网关直接复用 `com.treelearn.ai.gateway`（`AiGateway`/`HttpsTransport`/`SseFrameParser`/`AiGatewayError`）与 `prompt`；桌面仅需 Jackson 版 JSON 请求体/模型列表解码（`JsonChatRequestBody`/`ProviderJson` 的平台实现）。
- Watch-outs: 共享树禁止 Kotlin；新模块新增文件须同步结构守卫用例；包名不得再改。