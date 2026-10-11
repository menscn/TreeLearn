# Treelearn Harness Task: Web 路径文档同步（规格/开发方案/启动说明）

id: T-2026-10-10-007
title: Web 路径文档同步（规格/开发方案/启动说明）
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-10-web-backend-skeleton-003.md, T-2026-10-10-web-frontend-skeleton-004.md, T-2026-10-10-shared-module-relocation-005.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 知芽本地 Web 版（父需求 T-2026-10-10-001）；用户要求「旧方案中仓库根级 frontend/、backend/、shared/、contracts/ 等目标路径，按本次要求调整到 web/ 下，并同步相关规格、开发方案、构建和启动说明」。
- Problem/background: 规格、详细开发方案与交接入口仍写根级目录目标；实际工程已落 `web/`；若不回写，后续执行者与文档读者会按错误路径操作（属于文档一致性规则中的「当前态主张与生产者事实矛盾」）。
- Users/scenarios: 后续开发/验收者、用户本人；按文档路径能直接找到真实工程与启动命令。
- Expected outcome: 三份文档的目标路径调整为 `web/`；根 README/README_EN 增加 Web 本地版启动说明；`.trae/context/project-brief.md` 同步；启动说明与实际脚本一致。
- Core flow and reversibility: 反向检索旧路径 → 逐处更新 → 链接/UTF-8/替换字符检查；纯文档可回退。
- Inputs/outputs: 输入为实际工程路径与启动脚本；输出为更新后的文档。
- State and exception handling: 凡与实际工程不一致的表述一律以工程事实为准修正；历史材料只加历史说明，不改写。
- Constraints and trade-offs: 只更新当前态文档；不复刻 token 数值清单；不复述规则正文。
- AI recommendation and rationale: 先反向检索后改；用 `git diff` 与编码闸门核验。
- User choice and custom additions: 用户明确要求同步。
- AI-autonomous decisions: 具体措辞与链接位置。
- User-confirmation decisions: 无。
- Acceptance evidence: 反向检索结果、链接检查、编码检查、git diff。
- Facts: `web/` 工程由 T-003/T-004/T-005 产出；端口 8733。
- Assumptions: T-003/T-004 的启动脚本命名稳定。
- Unknowns: 旧路径在历史任务文件中的出现是否需要保留（保留，不改历史）。
- Risks: 漏改导致文档与工程不一致；以反向检索兜底。
- Deferred: Phase 3 契约文档、表清单（后续任务）。
- Non-goals: 改写历史任务与已归档报告；修改产品范围。

## Goal

规格、开发方案、交接入口与根 README 中的目标路径、构建与启动说明与 `web/` 实际工程一致，且旧路径（根级 frontend/、backend/、shared/、contracts/ 作为目标）无残留。

## Scope

### In scope
- `Docs/spec/SPEC-2026-10-10-local-web-edition.md`、`Docs/05-technical-design/TreeLearn_Web本地版_详细开发方案_V1.0.md`、`TreeLearn_Web本地版_AI开发交接入口.md` 的路径与启动说明更新。
- 根 `README.md`/`README_EN.md` 增加 Web 本地版入口与启动/停止说明；`.trae/context/project-brief.md` 同步。
- `web/README.md` 成为启动说明权威（含 dev/release 两套入口），其余文档指向它。

### Out of scope
- `web/contracts/` 契约正文（Phase 3）；表清单文档（Phase 2）；历史任务文件。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯文档；不改任何运行时行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 文档中路径名应对齐 `web/` 实际目录与脚本。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason: 纯文档变更，无行为面。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无（记录性同步；运行时事实来自 T-003/T-004/T-005 证据）。
- Authoritative code/config/migration evidence: `web/**` 工程与脚本、`android/settings.gradle`。
- Documentation surfaces checked: 上述三份设计文档、根 README 双语、project-brief、`web/README.md`。
- Documentation files updated: 同上。
- Producer/consumer/sibling/compatibility paths checked: 全仓反向检索 `frontend/`、`backend/`（根级目标语境）、`android/core`、`android/tree-layout`。
- Superseded phrases/examples reverse-searched: 反向检索命令与结果落任务记录。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 历史材料加时间戳说明，不改写结论。

## Acceptance criteria

- [x] 交接入口、规格、开发方案中根级目录目标全部指向 `web/`；反向检索无「以根级路径为目标」的残留（证据 `T-2026-10-10-007-doc-checks.txt` Check4/评审结论：剩余命中均为 `web/` 树内子项或改写后的 `web/shared/...` 表述）。
- [x] 根 README 双语含 Web 本地版启动/停止说明，命令与真实脚本一致（命令在 T-003/T-004/T-006 中已真实执行并留证：`start-backend.ps1`/`stop-backend.ps1`/`pnpm dev`）；另修正两处已过期的项目状态描述（Android 模块类型数与 Web 进展），并同步目录结构树。
- [x] `git diff --check` exit 0；替换字符扫描 0 命中；矩阵 50 条相对链接全部可达（含迁移后的 `web/shared/...` 链接）。
- [x] 未改写历史材料：旧 WebView 草案（带 2026-10-10 历史说明）、历史任务文件、`artifacts/evidence/.gen-*.sh` 均未触碰（`git status` 仅列 8 个目标文档）。
- [x] `.trae/context/project-brief.md` 同步为 `web/` 现状；交接入口加同日「后续状态」说明（设计交付当时的目录描述保留并注明以仓库为准）。

## TDD execution record

### RED — failing test first
- Test/scenario: N/A — 纯文档任务，无可执行行为；以文档一致性检查替代（反向检索 + diff-check + 编码扫描 + 链接可达）。
- Command: N/A
- Exit code: N/A
- Observed failure: N/A
- Failure reason validated: N/A

### GREEN — minimum implementation
- Implementation: N/A — 与 RED 同因（纯文档）。
- Command: N/A
- Exit code: N/A
- Passing evidence: N/A
- Regression evidence: N/A

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: documented TDD exception（docs task）
- Reason: 纯文档变更，无生产行为可执行；TDD 目标命令不存在。
- User approval: 用户在 2026-10-10 确认整体方案，其中明确要求「同步相关规格、开发方案、构建和启动说明」由本流程承担。
- Alternative verification: 反向检索 + `git diff --check` + UTF-8/替换字符扫描 + 相对链接检查（50/50 可达）+ 命令与脚本逐条实测核对（引用 T-003/T-004/T-006 的真实执行证据）。

## Implementation notes

- 契约路由判断：本次改动的规格/矩阵是设计文档，不是 `web/contracts` 的唯一 API 契约（Phase 3 建立），故未路由 contract-guardian；若独立 verifier 认定需要，按规则补派。
- 更新面：`Docs/spec/SPEC-2026-10-10-local-web-edition.md`（来源清单）、`Docs/05-technical-design/TreeLearn_Web本地版_详细开发方案_V1.0.md`（§2 结构树/逐条说明、§6、§12）、`TreeLearn_Web本地版_功能一致性矩阵.md`（9 条链接 → `web/shared/...`）、`TreeLearn_Web本地版_AI开发交接入口.md`（同日后续状态说明）、根 `README.md`/`README_EN.md`、`.trae/context/project-brief.md`；`web/README.md` 保持为启动说明权威（其树图本身即 `web/` 内子项）。

## Verification report

- Verdict: PASS（独立 verifier，2026-10-10；只读核对 + 独立复跑）
- Evidence: verifier 独立复跑 `validate_tasks.py`（PASS）、反向检索 8 文档（无根级目标残留；SPEC 以「原 android/core」标注历史）、`git diff --check` exit 0、替换字符 0 命中、矩阵相对链接独立提取 53 条全部可达（含 9 条新 `web/shared/...`；与证据的 50 条为提取口径差异）、README 命令与脚本静态一致（loopback 8733/5173 与 vite.config/application.properties 吻合）、历史材料未触碰（`git diff --name-only` 仅列本批任务文件）。
- Changed/deleted assertion rationale and equivalent behavior coverage: N/A
- Gaps: verifier 观察项 ①：docs 任务的 TDD 例外「用户批准」来源为间接推断（无逐条例外原话）——低风险，如实保留；② 验收点「git status 仅列 8 个目标文档」在最终工作树不成立（存在其它在飞任务改动，按指派不计 T-007 问题，证据文件生成于更早时点）。
- Risks: 无阻断；后续路径表述统一以 `web/` 为根。

## Handoff notes

- Completed: 8 个当前态文档的路径与启动说明同步；机械检查全过；独立 verifier PASS。
- In progress: 待与 T-004/T-006 一并落库。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: Phase 2 存储任务继续在 `web/README.md` 补数据目录说明。
- Watch-outs: 历史材料不静默改写；新增路径表述统一以 `web/` 为根。