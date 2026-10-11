# Treelearn Harness Task: C 树图布局端点

id: T-2026-10-10-045
title: C 树图布局端点（GET /api/topics/{id}/tree-layout）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 260
shared_code_touched: false
depends_on: [T-2026-10-10-search-stats-endpoints-043.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001` 第二波「C 树图」的后端半——功能矩阵「树导航与折叠」行补全服务端几何。
- Problem/background: 前端 C 视图是占位文案；`web/shared/tree-layout` 模块已挂进后端依赖但无任何端点消费；契约缺树布局端点章节。
- Users/scenarios: 学习者在 C 视图查看整棵分支树（树干/叶/苞/名牌），点击分支跳转。
- Expected outcome: 契约 §6.11 + `TreeLayoutController`：数据装配镜像 Android `TreeCanvasWiring`/`snapshotOf`（行序、rounds=USER 消息数、summary=branch_summary artifact payload JSON 的 summary 字段、孤儿行跳过、无根=空布局、根 archived=fail-closed 500 `STORAGE_REFUSED`）；几何全由共享 `TreeLayout.compute` 计算，前端不推几何。
- Core flow and reversibility: 控制器薄 → `BranchStore.listForTopic` + `ArtifactStore.find` + messages count → `TreeSnapshot.of` → `TreeLayout.compute` → DTO。纯新增端点，无 schema 变更，可回退。
- Inputs/outputs: 输入=`GET /api/topics/{id}/tree-layout?scale=&currentBranchId=`；输出=`TreeLayoutResponse`（bounds/branches/labels+title+summary/hits+source）。错误：404 `NOT_FOUND`(resource=topic)、400 `SCALE_INVALID`（新增错误目录条目）。
- State and exception handling: 见上；`scale` 非有限正数 → `SCALE_INVALID`。
- Constraints and trade-offs: 不改契约既有章节语义（仅新增 §6.11 与错误目录两行）；不改 Android/共享域/schema；contract-guardian 复核通过（6 处实偏差已修正：kind=branch_summary、payload JSON、STORAGE_REFUSED、NOT_FOUND、SCALE_INVALID、hits.source）。
- AI recommendation and rationale: 与 042/043 同构的「镜像 Android 语义 + 共享域唯一判定」；共享 `:tree-layout` 模块即 Android 同一实现，几何零漂移。
- User choice and custom additions: 已批准（第二波开工指令）。
- AI-autonomous decisions: labels 附带 title/summary 文本（前端不自行拼装）；固定视口 `ViewportConstraints.of(800,1200)`；`GenerationConfiguration` 补 `ArtifactStore` bean。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-045-{red,green,check,probe}.txt`。
- Facts: `TreeLayout.compute(TreeSnapshot, currentBranchId, Density, ViewportConstraints)`、`Density.forScale`（0.72/1.05 分层）、`BranchGeometry/LabelGeometry/HitRegion/Bounds` 字段、`ArtifactStore.find(kind, scopeId)`、`PromptPurpose.BRANCH_SUMMARY`。
- Assumptions: summary artifact payload 为 JSON `{"summary":...}`（contract-guardian 核实 `ArtifactPayloadCodec`）。
- Unknowns: 无。
- Risks: labels 只给 LEAF 放置（共享模块语义），LIMB/BUD 无名牌——前端与测试均已按此对齐。
- Deferred: 前端 C 视图渲染（T-046）；缩放/平移手势。
- Non-goals: 不做布局缓存；不做 WebSocket 推送。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付契约 §6.11 树布局端点：真实 HTTP + 真 SQLite 证据证明 404/`SCALE_INVALID`/空布局/单根/子树深度/当前路径/密度层级/summary artifact/折叠 BUD/归档子树离场/根 archived fail-closed 全部按共享模块语义工作。

## Scope

### In scope
- `web/contracts/api.md` §6.11 + 错误目录 `SCALE_INVALID` 行。
- `web/backend/.../api/TreeLayoutController.java`（新，含 DTO records）。
- `web/backend/.../generation/GenerationConfiguration.java`：补 `ArtifactStore` bean。
- `web/backend/src/test/.../api/TreeLayoutEndpointsTest.java`（新，11 例）。

### Out of scope
- 前端渲染（T-046）；布局缓存；契约既有章节改动。

## Acceptance criteria
- [x] 契约 §6.11 落稿并经 contract-guardian 复核修正（6 处实偏差）。
- [x] RED→GREEN：11 例真实 HTTP+SQLite，先全红（端点 404）后全绿。
- [x] 装配镜像 Android `snapshotOf`（孤儿行跳过/无根空布局/根 archived fail-closed 500）。
- [x] `rounds`=已提交 USER 消息数；`summary`=branch_summary payload JSON 的 summary 字段（缺失/畸形/空=空串）。
- [x] 几何全部来自共享 `:tree-layout`，DTO 字段与几何类一一对应（含 hits.source）。
- [x] 后端全量 `:check`（含 checkstyle）exit 0，既有 264 例零回归。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `GET /api/topics/{id}/tree-layout` 由不存在变为按 §6.11 可用（用户可见）；错误目录新增 `SCALE_INVALID`。
- Authoritative code/config/migration evidence: `web/contracts/api.md` §6.11、`web/backend/.../api/TreeLayoutController.java`、`.../generation/GenerationConfiguration.java`（ArtifactStore bean）。
- Documentation surfaces checked: 契约（新增 §6.11；错误目录表新增 SCALE_INVALID 行）、`web/README.md` 状态节。
- Documentation files updated: 契约与 README 已按本轮交付同步（见下方收口说明）。
- Producer/consumer/sibling/compatibility paths checked: 前端 `src/api/types.ts`/`src/store/topicData.ts`（T-046 消费）；fixtures 无既有树布局样例（响应形状由 DTO 与组件测试钉住）。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。



## TDD execution record

### RED — failing test first
- Test/scenario: `TreeLayoutEndpointsTest` 11 例（404/scale/空布局/单根/子树/当前路径/密度/summary/BUD/归档/根 fail-closed）。
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.TreeLayoutEndpointsTest`
- Exit code: 1
- Observed failure: 11 tests completed, 11 failed（端点不存在，全部非 404 即错误响应；编译通过）。
- Failure reason validated: `artifacts/evidence/T-2026-10-10-045-red.txt`（`grep "tests completed"` 末行 + `EXITCODE=1`）。

### GREEN — minimum implementation
- Implementation: `TreeLayoutController`（装配+compute+DTO）+ `GenerationConfiguration.artifactStore` bean + 契约 §6.11。
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.TreeLayoutEndpointsTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-045-green.txt`（11/11，`EXITCODE=0`）；迭代细节（ordinal 冲突、Density 1.0=MID 非 NEAR、LIMB 无 label、current 回落根语义）见 probe 文件与正文。
- Regression evidence: `artifacts/evidence/T-2026-10-10-045-check.txt` `:check` BUILD SUCCESSFUL `EXITCODE=0`（275 例 0 失败 + checkstyle 0 error）。

### REFACTOR — optional
- Cleanup: labels 文本通道（title/summary）随装配一次收集（`LinkedHashMap`），避免重复查询；注释说明 fail-closed 理由。
- Regression evidence: 同 check 文件（重跑 exit 0）。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行 ≈230，超软上限 200、未超 500 硬上限；仅软预算警告）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- `Scale` 参数解析：非数字/非有限/≤0 一律 `SCALE_INVALID`（`Map.of("field","scale")`）。
- `currentBranchId` 不在树内时共享模块 `resolveCurrent` 回落根分支（契约已写明）；测试钉死 root onCurrentPath=true、child=false。
- `Density.forScale(1.0)=MID`（`NEAR_START_SCALE=1.05`），默认 scale 下标签为 MID 层语义；测试用 1.2 钉 NEAR。
- labels 只为 `LEAF` 放置（`placeLabels`），LIMB/BUD 无名牌——`summaryArtifactDrivesShowSummary` 用「labels 中不存在根条目」表达该语义。

## Verification report

- Verdict: PASS（实现者自检全绿；独立 verifier 终验待下一轮统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-045-{red,green,check,probe,probe2}.txt`；后端 `:check` 275 例 0 失败 + checkstyle 0 error。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除既有断言；「根 archived → 500」以 SQL 直改绕过域校验构造，证明 fail-closed 出口真实可达。
- Gaps: 缩放/平移手势、布局缓存未做（Deferred）。
- Risks: 契约新增章节未经独立 verifier 终验（随下一轮统一）。

## Handoff notes

- Completed: 契约 §6.11 + Controller + 装配 + 11 例 RED→GREEN + 全量 check。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-046 前端 C 视图消费本端点。
- Watch-outs: labels 只含 LEAF；`Density` 阈值 0.72/1.05；`branch_summary` payload 是 JSON 不是纯文本。
