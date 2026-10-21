# Codex Harness Task: `frontend/` 工程骨架与构建入口

id: T-2026-10-18-011
title: frontend 工程骨架与构建入口
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 130
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 规格 §11 阶段 2 要先有一支「能构建、能起、离线可用」的前端工程，才有地方承载逐屏 1:1 校正；Parent summary 见 `T-2026-10-18-web-h5-android-parity-requirement-005.md`。
- Users/scenarios: 本机浏览器打开 `frontend/dist/index.html` 或 `pnpm --dir frontend dev`，得到与 Android 同构的外壳（两枚底部标签、屏槽位）。
- Expected outcome: React + TS + Vite + pnpm 骨架、依赖方向固定的目录层、构建与类型检查两条命令全绿。
- Core flow and reversibility: 新增 `frontend/**`；回退即删目录，不影响 Android 与文档。
- Inputs/outputs: 输入规格 §2/§3 与生成物 `frontend/src/styles/tokens.css`；输出工程配置、外壳布局、`pnpm` 四个入口（dev/build/lint/preview，test 由 012 接入）。
- State and exception handling: 骨架阶段没有数据状态；`main.tsx` 在 `#root` 缺失时抛错而不是静默白屏。
- Constraints and trade-offs: 不装 router（阶段六才有消费者）；`index.html` 的 `<title>` 留空避免形成文案第二份正文；离线判定不在本片实现，交给 014 的单一脚本。
- AI recommendation and rationale: 骨架与判定分离，可以让「离线可用」这条出口门禁只有一处口径。
- User choice and custom additions: 用户选路径 A（阶段 2）并要求连续执行完毕。
- AI-autonomous decisions: ① 不引入 `react-router-dom`；② `<title>` 空由文案表设置；③ 离线判定归 014。
- User-confirmation decisions: 阶段 2 范围与「一次性搞完」由用户在 2026-10-18 确认。
- Acceptance evidence: `artifacts/evidence/T-2026-10-18-web-frontend-slice-011-gate.txt`。
- Facts: `pnpm@11.5.2` / `vite@8.3.4` / `typescript@7.0.2` / `vitest@5.0.5` 安装与运行实测通过；`npm ping` PONG rc=0（`curl` 在该 Git Bash 上因 SSL 后端返回 http=000，属假阴性）。
- Assumptions: `dp`↔CSS px 按 1:1 处理（规格 §3）。
- Unknowns: PC 档外壳何时新增（阶段 8），本片只留手机档外壳。
- Risks: 骨架若被当成「已还原」，会让人误读进度；已在 `AppShell` 用 `data-slot`/`aria-hidden` 标注它只是外壳。
- Deferred: 屏内容、导航与返回栈。
- Non-goals: 不做平板档、不做 PC 外壳、不引入 UI 组件库。
- Skipped non-critical questions and reason: 未追问 `.gitignore` 具体条目，按 Vite 常规四项落。

## Goal

`frontend/` 骨架可构建、可类型检查、可本地起服务，且产物目录具备可被离线判定扫描的形状。

## Scope

### In scope
- `frontend/package.json`、`tsconfig.json`、`vite.config.ts`、`index.html`、`.gitignore`。
- `src/main.tsx`、`src/layouts/AppShell.tsx`、`src/styles/app.css`（只引用 token 变量）。

### Out of scope
- 运行时端口与适配器（012）、文案与时区（013）、守门与 `verify`（014）、任何屏内容。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 净新增；Android 与其他目录行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `frontend/src/main.tsx` → `layouts/AppShell.tsx`；消费者为 014 的三支守门与 `pnpm --dir frontend` 入口。
- Native bridge or independent-client compatibility/update requirements: N/A。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: 新增前端构建入口 `pnpm --dir frontend {dev,build,lint,preview}`。
- Authoritative code/config/migration evidence: `frontend/package.json` 的 `scripts` 段。
- Documentation surfaces checked: `.codex/rules/frontend.md`（依赖方向与 token 口径）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §2/§3。
- Documentation files updated: 无文档改写（规格已写明阶段 2 目标；本卡即交付记录）。
- Producer/consumer/sibling/compatibility paths checked: `frontend/src/styles/tokens.css` 确认为生成物，未手改。
- Superseded phrases/examples reverse-searched: 未引入新的路径或命令别名，无需反查。
- `documentation impact: none` reason and evidence, 若适用：不适用——新增构建入口属当前态事实，由本卡与 `package.json` 承载。
- Historical/PRD handling: 历史卡片不恢复（迁移线裁定）。

## Acceptance criteria

- [x] `pnpm --dir frontend build` rc=0 且 `dist/` 出图；`pnpm --dir frontend lint` rc=0（2026-10-20 复跑：构建 `dist/index.html` 0.58 kB 出图，lint `EXITCODE=0`）。
- [x] 既有 token 链不被破坏：`python -X utf8 .codex/scripts/dev/check_design_tokens.py` 对 `frontend/src/**` 裸 hex 扫描无命中（2026-10-20 core 整跑 `0 check(s) failed`）。
- [x] 外壳不含任何用户可见硬编码文案（为 014 的文案守门预留绿灯；`check_user_copy_i18n` 扫描 23 文件 `cjk-lines=0 findings=0`）。

## TDD execution record

### RED — failing test first
- Test/scenario: 骨架尚不存在时执行生产构建入口
- Command: pnpm --dir frontend build
- Exit code: 1
- Observed failure: vite 在 `frontend/index.html` 与其引用的入口不存在时报错退出
- Failure reason validated: 失败原因即本片要交付的对象本身（构建入口不存在），不是环境故障

### GREEN — minimum implementation
- Implementation: 新增 7 个生产文件（清单见规模记录）与 `.gitignore`
- Command: pnpm --dir frontend build
- Exit code: 0
- Passing evidence: 门禁产物 `artifacts/evidence/T-2026-10-18-web-frontend-slice-011-gate.txt` 的 block 01（`built in`）与 block 02（`tsc --noEmit`）
- Regression evidence: 同产物末行 `OVERALL_EXITCODE=0`、`moved during this run=0`；token 守门回跑见 014 收尾

### REFACTOR — optional
- Cleanup: 删除门禁生成器里内联的外部 URL 白名单，改为 `new URL()` 主机名判定（唯一判定归 014 的脚本）
- Regression evidence: 离线脚本 `--selftest` 五例正反双向 rc=0

### TDD or sizing exception — only with prior user approval
- Exception type: none
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 规模判定值 130（现场实测，命令：`python -X utf8 .codex/scripts/dev/count_production_lines.py frontend/index.html frontend/package.json frontend/tsconfig.json frontend/vite.config.ts frontend/src/main.tsx frontend/src/layouts/AppShell.tsx frontend/src/styles/app.css`，分项 12/27/19/17/9/14/32）。旧卡曾记 263，那是在离线脚本尚未归给 014 之前的口径，已作废。
- `vite.config.ts` 现含 `test` 段（012 接入 vitest 所需），该项属本片文件的后续位移，已在 Regression evidence 中由重跑覆盖。
- 层级目录名固定为 `pages/layouts/components/hooks/store/api/platform/utils/copy`，014 的依赖方向守门按同一名单，不得两侧各写一份。

## Verification report

- Verdict: PASS（独立复核，2026-10-20：三条验收全部由当期命令复核；阶段 3 已补真实交互证据。）
- Evidence: 构建/类型检查 = `artifacts/evidence/web-parity-2026-10-20/frontend-gates.txt`（`tsc --noEmit` + `vite build` 50 modules、`dist/index.html` 出图）；lint = `artifacts/evidence/web-closeout-lint-2026-10-20.txt`（`EXITCODE=0`）；token = `artifacts/evidence/guards-2026-10-20-final.log` 的 `check_design_tokens: 0 check(s) failed`；文案硬编码 = 同 gates 的 `check_user_copy_i18n`（`cjk-lines=0 findings=0`）；真实交互 = Edge/Chromium 两次 24 步走查各 `judgements=29 failed=0 VERDICT: PASS`（`artifacts/evidence/web-parity-2026-10-20/edge-walk.txt`、`artifacts/evidence/web-parity-2026-10-20-chromium/chromium-walk.txt`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 规模口径由 263 改 130，等价覆盖为「离线判定的 137 行归 014」，两侧之和仍可回算。
- Gaps: ① `file://` 双击可开由 020 的 `dist-offline/index.html` 关闭（`dist/` 形态按设计不适用）；② 外壳后续已有 13 屏内容；③ 独立复核轮已由本期 verifier 完成，结论即本行 Verdict。
- Risks: 若 014 的守门按文件名子串排除测试文件，会出现「绿但没判到」；已写入 014 的 Watch-outs。

## Handoff notes

- Completed: 骨架、构建与类型检查入口、外壳布局与 token 引用方式。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 012 端口与适配器 → 013 文案与时区 → 014 守门与 `verify`，随后回跑本片门禁。
- Watch-outs: ① `tokens.css` 是生成物；② `frontend/pnpm-lock.yaml` 必须与卡片同批入库；③ 层级目录名单一来源见本片 Implementation notes；④ 门禁生成器 `artifacts/tmp/gen-fe-gate.py` 四片共用一支，勿各写一份判据。
