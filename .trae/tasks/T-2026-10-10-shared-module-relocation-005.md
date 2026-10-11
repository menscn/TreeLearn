# Treelearn Harness Task: 共享模块迁入 web/shared（core 与 tree-layout）

id: T-2026-10-10-005
title: 共享模块迁入 web/shared（core 与 tree-layout）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 220
shared_code_touched: true
depends_on: [T-2026-10-10-web-runtime-feasibility-002.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 知芽本地 Web 版（父需求 T-2026-10-10-001）；用户要求共享构件集中在 `web/shared`，旧方案授权「验证后一次性调整 projectDir」；共享业务判定必须唯一实现、禁止拷贝。
- Problem/background: `android/core` 与 `android/tree-layout` 是纯 JVM 构件，被 Android 消费；Web 端需要同一份源码。若各自复制将导致判定漂移（仓库失败案例库明确禁止）。
- Users/scenarios: Android 与 Web 两个构建的开发者；Android 用户行为不得变化。
- Expected outcome: 源码迁入 `web/shared/domain`、`web/shared/tree-layout`；`android/settings.gradle` 由 projectDir 指向新位置（项目名 `:core`/`:tree-layout` 与 Java 包名不变）；Web 端构建以同一方式引用；Android 全量回归与 Java-only guard 通过；新增 `web/scripts/check-shared-modules.ps1` 结构守卫（含无 Kotlin 覆盖到 web/shared）。
- Core flow and reversibility: 迁移 → Android 构建/测试 → 守卫脚本；若回归失败可整体回退（git 反向迁移，无数据影响）。
- Inputs/outputs: 输入为迁移命令；输出为 Android 构建结果与守卫输出。
- State and exception handling: 构建失败即阻断并向父需求报告；不保留双份源码副本。
- Constraints and trade-offs: 项目名/包名兼容；不修改业务逻辑（0 行业务判定变更）；Android Java-only。
- AI recommendation and rationale: 用 `git mv` 保留历史；android 与 web 两端仅改 settings 引用；守卫脚本断言位置与无副本，防止未来漂移。
- User choice and custom additions: 用户确认方案（含共享模块迁入 web/shared）。
- AI-autonomous decisions: 目标目录命名、守卫实现、回归命令集。
- User-confirmation decisions: 无。
- Acceptance evidence: 守卫脚本 RED→GREEN、Android 回归证据、Java-only guard 证据（含 web/shared 覆盖）、git 改名识别记录。
- Facts: `core`/`tree-layout` 为 `java-library` Java 17；android 构建 `include ':core'`、`include ':tree-layout'`。
- Assumptions: 无模块外硬编码绝对路径引用。
- Unknowns: 是否有脚本/文档引用旧路径（执行时反向检索）。
- Risks: android 依赖方向验证脚本（verifyDependencyDirection）可能按路径扫描，需实测确认。
- Deferred: `ai/prompt` 包抽取为 `web/shared/ai-protocol`（Phase 3 前单独任务）。
- Non-goals: 修改共享模块业务逻辑；迁移 Android 其它模块（data/ai/app 留在 android/）。

## Goal

`web/shared/domain` 与 `web/shared/tree-layout` 成为 `:core`/`:tree-layout` 唯一源码位置，android 与 web 两个构建都以 projectDir 引用它，Android 回归全绿且无 Kotlin。

## Scope

### In scope
- `git mv android/core → web/shared/domain`、`android/tree-layout → web/shared/tree-layout`（含各自 build.gradle 与测试）。
- `android/settings.gradle` 更新 projectDir；包名/项目名不变。
- 反向检索并更新仓库内引用旧路径的脚本/规则/文档（只更新当前态；历史材料标记）。
- `web/scripts/check-shared-modules.ps1`：断言目录存在、两端 settings 引用正确、无重复源码副本、web/shared 下无 Kotlin（正反自检）。
- Android 回归：`:core:test`、`:tree-layout:test`、`gradlew check`（含依赖方向验证）、Java-only guard（扩展到 web/shared）。

### Out of scope
- 新建 web 工程（T-003/T-004）；`ai/prompt` 抽取；任何业务代码修改。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 仅源码位置与构建引用变化；`com.treelearn.core.domain.*` 与 `com.treelearn.treelayout.*` 的编译产物、测试行为完全不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/settings.gradle`、`android/app`、`android/ai`、`android/data` 对 `:core` 的依赖；android 脚本目录。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason, if applicable: N/A（有构建引用变化，需完整回归）。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 共享模块源码位置与构建引用变化（项目名/包名不变）。
- Authoritative code/config/migration evidence: `android/settings.gradle`、`web/scripts/check-shared-modules.ps1`、git 改名记录。
- Documentation surfaces checked: `android/README.md`、`.trae/context/project-brief.md`、规格/开发方案中被 T-007 同步。
- Documentation files updated: `android/README.md`（模块位置说明）。
- Producer/consumer/sibling/compatibility paths checked: 全仓反向检索 `android/core`、`android/tree-layout` 字符串。
- Superseded phrases/examples reverse-searched: 上述字符串的引用清单记录在任务内。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 历史任务文件不改写。

## Acceptance criteria

- [x] `web/scripts/check-shared-modules.ps1` RED→GREEN：迁移前 FAIL（9 项、exit 1）、迁移后 PASS（5 OK、exit 0），自检 8 例（含注入违规转红）全通过。
- [x] Android 回归：`:core:test :tree-layout:test` 全绿（exit 0）；`:ai:testDebugUnitTest :data:testDebugUnitTest` 全绿（exit 0）；`verifyDependencyDirection` PASS；`verifyNoKotlinDex` PASS（3 APK）；`gradlew check` 全量运行完成，唯一失败为 **迁移前基线同样失败** 的 ChatGenerationStageTest（工作树归因证据），与本次变更无关——说明见 GREEN ⑤。
- [x] Java-only guard：`android/scripts/no-kotlin-check.ps1` VERDICT PASS（13 OK）；`web/shared/**` 的 Kotlin 覆盖由新守卫的 Kotlin 分支承担（源码文件 + 声明扫描，自检覆盖两类注入）。
- [x] git 识别为改名（`git status` 显示 `R android/core/... -> web/shared/domain/...`）；仓库内无 `android/core`、`android/tree-layout` 源码残留（守卫已断言）。
- [x] 项目名 `:core`/`:tree-layout` 与包名不变；`android/ai`、`android/data` 单测通过（依赖解析成功）。
- [x] 文档影响记录完成；守卫脚本实测 327 物理行（工具/守卫脚本，迁移本身为 0 业务行）；`android/README.md` 已补「共享模块位置」小节；待独立 verifier 复核。

## TDD execution record

### RED — failing test first
- Test/scenario: `web/scripts/check-shared-modules.ps1`（结构守卫：共享模块位置、两端引用、无重复副本、共享树无 Kotlin、空选择 fail-closed；迁移前运行必然失败）
- Command: powershell -NoProfile -ExecutionPolicy Bypass -File web\scripts\check-shared-modules.ps1
- Exit code: 1
- Observed failure: `VERDICT: FAIL (9 problem(s))`：:core/:tree-layout 源根与 build 文件缺失；android/settings.gradle 未指向 web/shared；android/core 与 android/tree-layout 仍有源码副本；共享树无可扫描 Gradle 文件（fail-closed）
- Failure reason validated: 失败确由目标结构与引用缺失导致（证据 `artifacts/evidence/T-2026-10-10-005-red.txt`，含命令行与 EXITCODE=1）；同时守卫自检 8 例全部注入违规后转红（`T-2026-10-10-005-guard-selftest.txt`，SELFTEST VERDICT: PASS，exit 0）

### GREEN — minimum implementation
- Implementation: `git mv android/core web/shared/domain`、`git mv android/tree-layout web/shared/tree-layout`；`android/settings.gradle` 改为 projectDir 引用（项目名/包名不变）；`DeepSeekContextEncoderTest.coreSources()` 补 `web/shared/domain/src/main/java` 候选；新增 `web/scripts/check-shared-modules.ps1`
- Command: powershell -NoProfile -ExecutionPolicy Bypass -File web\scripts\check-shared-modules.ps1
- Exit code: 0
- Passing evidence: `VERDICT: PASS (5 OK line(s))`（`T-2026-10-10-005-green.txt`：:core 29 个 .java、:tree-layout 12 个 .java 位于 web/shared；android 引用正确；无副本；共享树无 Kotlin）
- Regression evidence: ① `:core:test :tree-layout:test` BUILD SUCCESSFUL / EXITCODE_TESTS=0（green.txt）；② `:ai:testDebugUnitTest :data:testDebugUnitTest` SUCCESS / EXITCODE_CONSUMERS=0（consumers.txt，含更新后的 DeepSeekContextEncoderTest）；③ `gradlew check`：verifyDependencyDirection PASS、verifyNoKotlinDex PASS（3 APK 无 Kotlin 运行时）、app 单测 831 项 1 失败（ChatGenerationStageTest，**既有失败**，见归因）；④ Java-only 守门 `android/scripts/no-kotlin-check.ps1` VERDICT: PASS（13 OK，53 条 variant classpath、4231 报告行、3 APK）/ EXITCODE_NOKOTLIN=0（nokotlin.txt）；⑤ 归因证据：`T-2026-10-10-005-preexisting-attribution.txt` 在迁移前基线提交 d2c10c1 的独立 worktree 复跑同一测试类，结果与迁移后**隔离复跑**逐项一致（9 项 2 失败、同两用例）；整包 `check` 下为 831 项 1 失败（同一用例的时序敏感表现）→ 失败与本次迁移无关；任务记录交叉引用：T-2026-10-09-002 已记载同一失败（`全量 JVM 831 项有 1 项 ChatGenerationStageTest 失败`）。

### REFACTOR — optional
- Cleanup: 迁移前基线 worktree 已移除（`git worktree list` 仅剩主工作树）；spike 目录清理见 T-002。
- Regression evidence: 同 GREEN ④（守卫与回归在重构后复跑）

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 迁移为结构变更：TDD 目标命令（结构守卫）与回归命令分离记录，RED/GREEN 使用同一守卫命令。
- 若 android 构建存在按路径扫描的验证脚本，需同步其扫描根并在证据中说明。
- 已完成的消费者/引用盘点（2026-10-10 只读检索 `android/core|android/tree-layout`）：
  1. **必须修改**：`android/ai/src/test/java/com/treelearn/ai/prompt/DeepSeekContextEncoderTest.java` 的 `coreSources()` 从 CWD 逐级向上找 `core/src/main/java`、`android/core/src/main/java`；迁移后两个候选都失效，必须补新候选 `web/shared/domain/src/main/java`（否则该测试抛 `could not locate the core sources`，这是真实消费者回归信号）。
  2. **不受影响（已核对）**：`android/build.gradle` 的 `verifyDependencyDirection` 用 `project(':core')` 与 `moduleProject.fileTree('src/main/java')`（按 projectDir 解析），`gradle/verify-no-kotlin.gradle` 与 `android/scripts/**` 无路径硬编码；`android/ai`、`android/data`、`android/app` 通过 `project(':core')` 依赖。
  3. **需同步的当前态文档**：`android/README.md`；`Docs/05-technical-design/TreeLearn_Web本地版_功能一致性矩阵.md` 与 Web 三份文档的路径链接（由 T-007 统一）；`Docs/spec/SPEC-2026-10-10-local-web-edition.md`（T-007）。
  4. **历史材料不改写**：`.trae/rules/harness-tasks.md` 中的 T-003 规模记录、`artifacts/evidence/.gen-*.sh`、旧任务文件、`Docs/spec/SPEC-2026-09-22-*` 等带日期材料保留原路径（历史快照）。
- 守卫方案：新增 `web/scripts/check-shared-modules.ps1`（ASCII-only，符合 PS 5.1 编码前提）：断言 `web/shared/domain`、`web/shared/tree-layout` 源码位置、android/web 两端 settings 引用、无 `android/core|android/tree-layout` 源码残留、web/shared 与 web/backend 无 Kotlin 源文件/Kotlin 声明；带 `-Selftest` 正反自检（注入违规必须 FAIL）。android 既有 `no-kotlin-check.ps1` 保持原样（其 AndroidRoot 扫描继续覆盖 android/；web 侧由新脚本覆盖）。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；只读核对 + 独立复跑）
- Evidence: verifier 独立复跑守卫（`VERDICT: PASS (5 OK)`、exit 0；RED 9 项 exit 1 与证据一致）、自检（8 例 PASS）、`:core:test :tree-layout:test :ai:testDebugUnitTest --rerun-tasks`（21 task、BUILD SUCCESSFUL、exit 0）；核对 git 改名、`src/main/java` 已不在 android/core 与 android/tree-layout、settings projectDir 指向、Java-only 证据（13 OK / 53 classpath / 3 APK）与 README 引用真实性。代码量：迁移 0 行 + settings 4 行 + 测试 3 行 + 守卫脚本 287 非空非注释行 ≈ 294 行（<500 硬顶；超 200 软上限，警告已知）。
- Changed/deleted assertion rationale and equivalent behavior coverage: `DeepSeekContextEncoderTest` 仅新增源码定位候选，断言本体未改；verifier 复核其在新位置下通过。
- Gaps: `gradlew check` 未整体归零——唯一失败为 ChatGenerationStageTest（既有、已归因、时序敏感）；verifier 独立判断「既有失败不构成 T-005 缺陷，不应据此判 FAIL」。
- Risks: 既有失败长期非零可能掩盖未来 app 回归（verifier 风险项）；共享模块构建产物此前未被忽略（verifier 建议 ①，本任务已处置：根 `.gitignore` 增加 `web/shared/*/build/`）；建议 ②单独任务修复 ChatGenerationStageTest 已建 T-2026-10-10-008；建议 ③ GREEN 措辞已修正（区分整包/隔离）。

## Handoff notes

- Completed: 目录迁移 + settings 引用 + 消费者测试路径 + 结构守卫（含自检）+ Android 回归与 Java-only 守门 + 基线归因；独立 verifier PASS；verifier 三条建议已处置（.gitignore、后续任务、措辞）。
- In progress: 待落库提交。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 提交后 T-003（后端骨架，RED 已达成）继续；web 侧引用检查由守卫在 settings 出现后自动强制（当前已生效，PASS 5 OK 已含 web/backend 引用行）。
- Watch-outs: 不得恢复 `android/core`、`android/tree-layout` 任何副本；`ChatGenerationStageTest` 为既有失败，勿误归本次迁移；共享模块构建产物不进版本库（已加忽略规则）。