# Treelearn Harness Task: Web 运行边界与共享构件可行性验证

id: T-2026-10-10-002
title: Web 运行边界与共享构件可行性验证
template_version: 2
task_type: verification
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: harness
stack: harness
estimated_production_lines: 0
shared_code_touched: false
depends_on: []
tdd_mode: not-applicable

## Requirement collaboration brief

- Parent summary: 知芽本地 Web 版实现（父需求 T-2026-10-10-001），本任务为「运行边界与共享构件」阶段的可行性验证，先验证再固定依赖。
- Problem/background: 旧方案明确「先验证现有纯 Java 领域、树布局和生成能力的复用边界，再固定依赖、存储和凭据适配」；不得把 Android 库当桌面 JAR 推断可用。
- Users/scenarios: 开发与验收人员；后续 T-003/T-005 消费本任务结论。
- Expected outcome: 桌面 JVM 能直接消费 `:core`/`:tree-layout`；`ai/prompt` 包纯度确认；前后端候选依赖的精确版本与许可实测记录；端口/数据目录/凭据/树图渲染/迁移方案的决策记录。
- Core flow and reversibility: 临时 spike（仓库外）编译运行共享模块测试 → 候选依赖解析实测 → 输出证据与决策；spike 不落入仓库，无生产改动，可重跑。
- Inputs/outputs: 输入为现有 Gradle 工程与本机工具链；输出为 `artifacts/evidence/T-2026-10-10-002-*` 证据与任务内决策记录。
- State and exception handling: 网络不可用/依赖不可解析时记录缺口并按失败处理；spike 失败即阻断 T-003/T-005 并报告。
- Constraints and trade-offs: 零生产代码改动；不做性能承诺；不凭记忆写版本。
- AI recommendation and rationale: 用仓库现有 Gradle wrapper 在 `%TEMP%` 建最小 settings 直接 include `android/core`、`android/tree-layout`；依赖以真实解析结果为准。
- User choice and custom additions: 用户确认方案中「先验证复用边界」的顺序。
- AI-autonomous decisions: spike 形态、候选依赖清单、端口（8733）与数据目录（`%LOCALAPPDATA%\TreeLearn-Web`）初值。
- User-confirmation decisions: 桌面永久表准入（Phase 2 呈报，不在本任务）。
- Acceptance evidence: 证据文件含命令行、退出码、输出摘要；决策写入 Implementation notes。
- Facts: JDK 21.0.10、Node 22.22.0、pnpm 11.5.2、Gradle wrapper 8.14.3、无 Maven；`core`/`tree-layout` 为 `java-library` Java 17。
- Assumptions: Maven Central 与 npm registry 可达（本任务实测判定）。
- Unknowns: Spring Boot/sqlite-jdbc/JNA/React/Vite/图标库精确版本与许可；prompt 包 javac 独立编译可行性。
- Risks: 各候选依赖网络解析失败 → 记录缺口并调整选型。
- Deferred: 真实 Provider 与语音能力验证（后续阶段）。
- Non-goals: 创建生产工程、写业务代码、创建数据库表。

## Goal

完成桌面 JVM 复用、prompt 包纯度、前后端候选依赖（版本 + 许可）三项实测，并输出端口/数据目录/凭据/树图/迁移的书面决策记录，供后续任务直接消费。

## Scope

### In scope
- `%TEMP%` spike：最小 Gradle settings include `android/core`、`android/tree-layout`，运行两者 `test` 任务。
- `android/ai/prompt` 包依赖导入清单核查 + 针对 `core` 产物的 javac 独立编译实测。
- 候选依赖实测解析与许可记录：Spring Boot（3.x 最新稳定）、sqlite-jdbc、JNA、React、Vite、TypeScript、Vitest、@microsoft/fetch-event-source、图标库候选（≥2 个，含许可）、i18n 无依赖方案确认。
- 决策记录：端口、数据目录、loopback 安全边界、凭据适配方向、树图渲染方案、迁移运行器方向。
- 证据落 `artifacts/evidence/T-2026-10-10-002-*`。

### Out of scope
- 任何仓库内生产代码/工程骨架创建（T-003/T-004 负责）。
- 依赖安装到仓库锁文件；数据库表与业务实现。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 无运行时行为变更；验证结论供后续任务。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `android/core/build.gradle`、`android/tree-layout/build.gradle`（`java-library` 声明）、`android/ai/src/main/java/com/treelearn/ai/prompt/*.java` 导入。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason: 纯验证任务，不修改生产代码。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（仅验证）。
- Authoritative code/config/migration evidence: 现有 Gradle 配置与源码导入为准。
- Documentation surfaces checked: 结论供 T-2026-10-10-007 文档同步引用。
- Documentation files updated: 无（本任务不更新当前态文档）。
- Producer/consumer/sibling/compatibility paths checked: `android/ai` 对 `prompt` 包的实际引用。
- Superseded phrases/examples reverse-searched: N/A。
- documentation impact: none reason and evidence: 纯验证任务，无生产/配置/契约变更；结论以任务记录与证据文件承载。
- Historical/PRD handling: N/A。

## Acceptance criteria

- [ ] `%TEMP%` spike 中 `:core:test` 与 `:tree-layout:test` 全绿（证据文件含命令行、退出码、测试计数）。
- [ ] `ai/prompt` 包导入清单无 `android.*`/`org.json` 等平台符号；javac 以 core 产物为 classpath 编译通过（证据含命令与退出码）。
- [ ] 候选依赖逐项解析成功并记录精确版本 + 许可（证据含 `gradle dependencies`/`pnpm view` 输出）；任一失败按缺口记录并给出替代。
- [ ] 决策记录（端口/数据目录/凭据/树图/迁移/图标候选）写入 Implementation notes，格式可直接被 T-003/T-004/T-005 引用。
- [ ] 无仓库内生产代码被创建或修改（`git status` 验证）。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — verification task（无行为变更；spike 测试即验证手段）。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: 验证完成即通过；证据见 artifacts/evidence/T-2026-10-10-002-*。
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: spike 目录删除。
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: 纯验证任务，无可执行行为变更。
- User approval: 用户在 2026-10-10 确认方案（含「先验证复用边界再固定依赖」的推进顺序）。
- Alternative verification: 证据文件（命令行 + 退出码 + 输出）即验收证据。

## Implementation notes

- 端口初值 8733（本机 loopback）；数据目录初值 `%LOCALAPPDATA%\TreeLearn-Web`；最终以前端/后端任务实测为准并在 T-007 同步。
- 决策结果（2026-10-10 实测）：
  1. **桌面 JVM 复用**：`artifacts/evidence/T-2026-10-10-002-jvm-reuse.txt` —— 仓库外 spike（`%TEMP%\tl-web-spike`）以 projectDir 引用 `android/core`、`android/tree-layout`，`:core:test` 与 `:tree-layout:test` 全绿，`BUILD SUCCESSFUL`，`EXITCODE=0`（6 tasks）。
  2. **平台独立性**：`...-prompt-purity.txt` —— javac/javac-run 仅以两模块 class 目录为 classpath 编译并运行 `SpikeConsumer`（打印 `core.PromptPurpose values=8`、`tree-layout class=com.treelearn.treelayout.TreeLayout`），`EXITCODE_JAVAC_CONSUMER=0`、`EXITCODE_RUN_CONSUMER=0`；`android/ai` 的 `prompt` 包仅以 core 为 classpath 编译通过，`EXITCODE_JAVAC_PROMPT=0` → 确认纯 Java、可抽出共享。
  3. **后端依赖（Maven Central 实测）**：Spring Boot **4.1.1**（Apache-2.0，POM 实读；使用非弃用的 `spring-boot-starter-webmvc:4.1.1`，`+` 会解析到 4.2.0-M2 里程碑，禁止使用动态版本）；`org.xerial:sqlite-jdbc:3.53.4.0`（LICENSE 实读 Apache-2.0）；`net.java.dev.jna:jna:5.19.1`（POM 双许可 LGPL-2.1-or-later / Apache-2.0，本项目选择 Apache-2.0）。证据 `...-deps.txt`、`...-deps-boot-lines.txt`、`...-deps-extra.txt`。
  4. **前端依赖（npm 实测）**：react/react-dom 19.3.0、@types/react(-dom) 19.3.0、**typescript 5.9.3**（typescript-eslint 8.71.1 peer 要求 `>=4.8.4 <6.1.0`；TS 6/7 过新不采用）、vite 8.3.4、@vitejs/plugin-react 6.1.2、vitest 5.0.3、@testing-library/react 16.3.3、@testing-library/jest-dom 7.0.1、jsdom 30.1.2、eslint 10.12.0 + typescript-eslint 8.71.1（peer 实测兼容 eslint ^10）、@microsoft/fetch-event-source 2.0.1、**lucide-react 1.55.0（ISC）**。证据 `...-npm-deps.txt`、`...-npm-peers.txt`。
  5. **图标检索（用户要求）**：在线检索 5 个来源、对比 Lucide/Tabler/Phosphor，选定 Lucide（描边轮廓与 v1.1 原型一致，可用 strokeWidth 1.8 对齐；ISC/MIT 宽松许可）。证据与来源 `...-icons.txt`。
  6. **树图渲染**：候选 React Flow（MIT）不采用——改为消费共享 `:tree-layout` 布局坐标 + 前端自渲染 SVG（`android/tree-layout/build.gradle` 注释声明其为 pure JVM tree geometry / label placement / hit testing；`...-jvm-reuse.txt` 已实测该模块测试可独立运行），保证与 Android 同一份布局判定，不产生第二套布局实现。
  7. **凭据方向**：Windows 凭据管理器/DPAPI 经 JNA（Apache-2.0）适配；未验证前不使用明文或浏览器存储（T-008 级任务实现并实测）。
  8. **迁移运行器方向**：集中式版本化 migration runner（desktop SQLite，递增版本 + 事务），DDL 只允许出现在 runner；Phase 2 完成表准入后实现。
- 环境注意：PATH 上的 `java`/`javac` 存根（Oracle javapath）在本机执行崩溃（exit `-1073740791`），所有 Java 命令必须使用 `$env:JAVA_HOME\bin\*`（JDK 21.0.10）；该事实已影响证据日志，已记录。
- 未决项：无（图标最终组件化与视觉落地在 T-006 消费）。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；只读核对 + 独立复现）
- Evidence: verifier 独立重跑 `gradlew.bat -p %TEMP%\tl-web-spike :core:test :tree-layout:test --rerun-tasks` → `BUILD SUCCESSFUL`、`EXITCODE=0`、257 个 PASSED（与 `artifacts/evidence/T-2026-10-10-002-jvm-reuse.txt` 一致）；独立复现 javac/java（`EXITCODE_JAVAC_CONSUMER=0`、`EXITCODE_RUN_CONSUMER=0`、`EXITCODE_JAVAC_PROMPT=0`，classpath 无 android）；`npm view` 抽查版本全部命中（react 19.3.0、vite 8.3.4、vitest 5.0.3、eslint 10.12.0、lucide-react 1.55.0、fetch-event-source 2.0.1、typescript 5.9.3）；Maven 元数据与 POM 许可实读确认；`git status` 无生产代码变更；`validate_tasks.py` PASS（errors=0）。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A（无断言变更）
- Gaps: 无阻断项。两条非阻断观察已处置：① `npm-deps.txt` 中 `typescript version=7.0.2`（latest）与选定 5.9.3 的区分已在本任务 notes 显式说明；② `npm-peers.txt` 已补记命令行。
- Risks: 依赖为当日实测快照；T-003/T-004 必须以锁定版本写入锁文件，禁止动态版本（`+` 会解析到里程碑 4.2.0-M2 的教训已记录）。

## Handoff notes

- Completed: spike 桌面构建测试全绿；平台独立性 javac 实测通过；prompt 包纯度编译通过；Maven/npm 依赖版本与许可实测记录；图标在线检索与选型；8 项决策写入 Implementation notes；独立 verifier 结论 PASS。
- In progress: 无（本任务完成）。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: T-005（共享模块迁移）执行；T-003/T-004 按锁定版本建骨架。
- Watch-outs: 后端使用 `spring-boot-starter-webmvc:4.1.1` 固定版本（禁止动态版本）；Java 命令一律用 `$env:JAVA_HOME\bin\*`；spike 目录在 %TEMP%，不落仓库。