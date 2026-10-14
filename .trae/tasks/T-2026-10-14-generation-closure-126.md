id: T-2026-10-14-126
title: 生成终态闭环——终态后恢复追问、流式进回答位、停止保内容、失败原位重试
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 90
shared_code_touched: false
depends_on: [T-2026-10-13-web-surface-form-android-125.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123`（回退纸上书房 + 逐屏重排）。W1（颜色/形制）已完成；本任务是 W2 可用性闭环的第一步，对应用户最直接的痛点「生成完一次就再也问不下去」。
- Problem/background（源码核对）:
  - `web/frontend/src/pages/BranchWorkspace.tsx:127` 是 `activeOperationId` 的**唯一清除点**（只在 handleStop 里）；而 `:288` 的 `canSend` 恒要求 `activeOperationId === null`，于是**生成正常完成（stable/failed/interrupted）后输入框永久禁用**，必须刷新页面才能继续追问。
  - 流式内容渲染在页面底部的独立面板 `workspace__stream`（`:252-263`），不在对应 AI 消息位内；终态后既不读回权威消息，也不把面板收回，回答位与"正在生成区"长期并存。
- Expected outcome: 生成内容在对应的 AI 消息位内更新；终态到达后读回权威消息/版本并解除生成锁；停止保留已收到的内容；失败/中断在回答位给出原位重试入口。
- Facts: 契约 fixtures 已有 `stream-stable.json` / `stream-interrupted.json` / `stream-failed.json` / `stream-canceled.json`（`web/contracts/fixtures/sse/`），可直接回放终态。`loadConversation(branchId)` 是权威读回入口（`store/topicData.ts`）。
- AI-autonomous decisions: 流式状态行与停止/重试按钮迁移到回答位内部（`MessageView` 新增可选 `streamText` / `streamReasoning` / `footer` 三个 props，既有用法零改动）；底部 `workspace__stream` 面板整体移除；终态后统一 `loadConversation` 读回，不区分 stable/failed/interrupted/canceled（四种终态都要保住已收内容并解锁）。
- Non-goals: 未读/阅读位置口径（T-127）、回答动作行与划词浮条（T-128/T-129）、推荐即发送与返回语义（T-130）。

## Goal

一次生成结束后，用户能不刷新继续追问；流式内容出现在回答位里而不是页面底部；停止后已收内容仍在；失败/中断能在原位重试。

## Scope

### In scope
- 终态到达后读回权威消息并清除生成锁
- 流式正文/思考渲染进对应 AI 消息位（替代底部面板）
- 停止后保留已收内容（读回保证）
- 失败/中断的重试入口随回答位呈现

### Out of scope
- 未读口径与阅读位置（T-127）
- 回答动作行 4 图标、划词浮条、推荐即发送、返回语义（T-128~T-130）
- 后端任何改动

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变生成期间的渲染位置与终态后的解锁时机；保留用户输入草稿、分支切换、布局切换、取消/重试端点与 commandId 语义。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `BranchWorkspace.tsx`（A 布局消息列表 + composer + 停止/重试）、`components/MessageView.tsx`（被 `BranchWorkspace` 与 `SideBySideView` 共用，新增 props 均为可选）、`hooks/useOperationStream.ts`（只读）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 流式渲染位置与终态解锁时机。
- Authoritative code/config/migration evidence: `BranchWorkspace.tsx`、`MessageView.tsx`、`useOperationStream.ts`。
- Documentation surfaces checked: `web/contracts/api.md`（未改动端点，无需同步）。
- Documentation files updated: 无（行为对齐 Android `ChatPresenter`/`ChatScreen` 既有语义，非新契约）。
- Producer/consumer/sibling/compatibility paths checked: `SideBySideView` 仍以无流式 props 方式调用 `MessageView`。
- Superseded phrases/examples reverse-searched: 未涉及文档措辞。
- `documentation impact: none` reason and evidence, if applicable: 无端点、无契约、无文档语句变化。
- Historical/PRD handling: 不适用。

## Acceptance criteria

- [x] stable 终态后输入框可用，能继续发送追问（无需刷新）
- [x] 终态后读回权威消息，回答位显示服务端内容而非流式残留
- [x] 流式中的正文出现在对应 AI 消息位内部（`#msg-<id>`），页面底部不再有独立流面板
- [x] 停止后已收到的内容保留（取消走读回，不丢弃部分回答）
- [x] 失败/中断仍在原位提供重试，端点和 commandId 语义不变
- [x] 全量门禁绿：vitest / tsc / eslint / build / 三守门
- [x] 新增断言经变异检验为可证伪

## TDD execution record

### RED — failing test first
- Test/scenario: `web/frontend/src/pages/BranchWorkspace.test.tsx` 新增 `describe('BranchWorkspace generation closure (T-126)')` 三条用例：stable 终态后解锁追问并读回权威答案；流式正文落在回答位 `#msg-a1` 且底部无 `.workspace__stream`；canceled 终态保留部分回答并解锁。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx
- Exit code: 1
- Observed failure: 三条用例全红——`expected 1 to be greater than 1`（终态后未再读回对话）、`expected '思考过程先想清楚再回答。…' to contain '流式落在回答位里的正文。'`（流式仍在底部面板）、`… to contain '取消后保留的部分回答'`（取消后回答位为空）。
- Failure reason validated: 证据 `artifacts/evidence/T-126-generation-closure-red.txt`（EXITCODE=1，3 FAIL）。失败点与取证的根因一一对应：`activeOperationId` 只在 handleStop 清除（旧 `:127`）、流式渲染在底部面板（旧 `:252-263`）。

### GREEN — minimum implementation
- Implementation: `BranchWorkspace.tsx:112-133` 新增终态 effect——`stream.terminal` 非空即 `loadConversation(branchId)` 读回权威消息/推荐，随后无条件清除 `activeOperationId`/`activeMessageId`（四种终态都解锁），并把 failed/interrupted 的消息 id 钉进 `retryTarget`；`:155-160` `handleRetry` 改用 `retryTarget ?? activeMessageId`；`:171` `handleSend` 清 retryTarget；`:263-280` 消息位内渲染流式状态行并删除底部 `workspace__stream` 面板。`MessageView.tsx:50-57` 新增可选 props `streamText`/`streamReasoning`/`footer`（默认 null，既有用法零改动），`:181-183` 流式正文渲染进 `.message__answer`，`:192` 生成中隐藏动作行，`:291` 追加 footer。`global.css:462` 新增 `.message__stream-status`，并删除已无引用的 `.workspace__stream` 规则。
- Command: npx vitest run src/pages/BranchWorkspace.test.tsx
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-126-generation-closure-green.txt`——目标文件 1 passed / 19 tests；全量 `npx vitest run` 29 files / 213 tests（GREEN 前 210，新增 3 条全部转绿）。
- Regression evidence: 同证据文件——`npx tsc --noEmit`、`npx eslint .`、`node scripts/check-design-tokens.mjs`、`node scripts/check-contrast.mjs`（14 对全部达标）、`node scripts/check-user-copy-i18n.mjs`、`npx vite build` 均 EXITCODE=0。SideBySideView 与既有用例（分叉、批注、版本切换、错误文案、草稿）全绿，未改端点与 commandId 语义。

### REFACTOR — optional
- Cleanup: 移除底部面板遗留的死 CSS 规则 `.workspace__stream`（`global.css` 原 `:403`），避免"面板已删、样式还在"的错位；`BranchWorkspace.tsx` 移除底部面板带走的未使用 `Markdown` 导入。
- Regression evidence: 见 GREEN 证据文件的同一批门禁（tsc/eslint 捕获未使用导入，build 通过）。

### Mutation verification（反恒真配对）
- 三个变异各改一处生产代码，重跑同一条命令 `npx vitest run src/pages/BranchWorkspace.test.tsx`；备份 `artifacts/tmp/t126-mutation/{BranchWorkspace,MessageView}.tsx.bak`，已还原并 grep 回读确认无残留。证据：`artifacts/evidence/T-126-generation-closure-mutation.txt`。
- MUTATION A（读回）：`BranchWorkspace.tsx:127` 的 `setLoaded(...)` 换成 `void conversation` → 实测 `2 failed | 17 passed`，失败恰为「stable 读回」与「canceled 保留」两条，流式落位用例不受影响。
- MUTATION B（落位）：`MessageView.tsx:182` 的 `<Markdown text={streamText}>` 换成 `revision?.answerContent` → 实测 `1 failed | 18 passed`，失败恰为「流式落在回答位」，另两条不受影响。
- MUTATION C（终态覆盖面）：`BranchWorkspace.tsx:119` 的终态 effect 加 `if (stream.terminal.kind !== 'stable') return` → 实测 `1 failed | 18 passed`，失败恰为「canceled 保留并解锁」，证明取消/失败/中断路径确实被走到，不是只覆盖了 stable。
- 反恒真配对：三条断言均配正向断言（读回后要求**出现**服务端内容且**不出现**流式残留文本；落位同时断言底部面板为 null；解锁同时断言发送按钮 enabled），空实现无法让断言全绿。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 终态解锁与"权威读回"绑在同一个 effect 里：先 `loadConversation` 覆盖 `loaded.messages`，再 `finally` 解锁。顺序不能反——先解锁会让用户在新内容到位前就发出下一个请求，产生"追问挂在旧回答后面"的错序。
- `retryTarget` 与 `activeMessageId` 分工：`activeMessageId` 是"当前活跃"、解锁后必清；`retryTarget` 是"上次失败/中断的那条"、解锁后保留，所以原位重试在解锁之后仍然可用。
- 四种终态（stable/failed/interrupted/canceled）走同一条读回路径，不按终态分叉：Android `ChatPresenter` 也是终态即落库，停止同样保留已收内容。
- 流式期间隐藏 `message__tools`（朗读/版本/翻译/复制/分叉/子分支/重新生成）：这些动作依赖已落库的修订，生成中调用会打到旧版本。生成结束读回后自动恢复。
- 底部面板整体删除而非保留空壳：`.workspace__stream` 的 CSS 规则同步删除，测试以 `document.querySelector('.workspace__stream')` 为 null 做断言，防止面板以新类名回流。

## Verification report

- Verdict: PASS（独立 verifier 复核，2026-10-14）
- Evidence: `artifacts/evidence/T-126-generation-closure-red.txt` / `-green.txt` / `-mutation.txt`；verifier 独立核对 8 项声明全部成立，并指出两处非阻塞观察（见 Risks）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有断言；底部 `workspace__stream` 面板移除后，原「底部面板显示流式内容」的行为由「回答位内显示 + 面板为 null」覆盖（`BranchWorkspace.test.tsx:305-306`）。
- Gaps: 无阻塞缺口。生成中的停止按钮与终态后的重试按钮在 B（并排）布局下走 `SideBySideView`，该视图本轮未接入流式 props——并排视图下的生成态仍沿用旧行为，属 T-132（布局收敛）范围。
- Risks: ① `global.css` 的未暂存改动混有 T-125 的卡片形制改动，本任务在其中只占 3 行（删 `.workspace__stream`、加 `.message__stream-status`），提交时需按批次说明，混在一起不影响门禁但影响逐任务归因。② 测试 3（canceled）没有单独断言底部面板为 null，该断言由测试 2 覆盖；canceled 路径的独立性由 MUTATION C 证明。

## Handoff notes

- Completed: 取证、RED（3 红）、GREEN（19/19、全量 213/213、六项门禁 0）、三变异证伪并还原
- In progress: 交独立 verifier 复核
- Blocker: 无
- Unblock condition: 不适用
- Next steps: verifier 复核 → 提交本任务改动 → T-127 未读/阅读位置
- Watch-outs: `MessageView` 新增 props 必须可选，`SideBySideView` 的既有用法零改动；不要把未读口径混进来（属 T-127）。
