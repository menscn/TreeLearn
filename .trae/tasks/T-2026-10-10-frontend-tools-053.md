# Treelearn Harness Task: 前端工具包：朗读/语音输入/版本切换/reset/翻译（T-053）

id: T-2026-10-10-053
title: 前端工具包：朗读/语音输入/版本切换/reset/翻译（T-053）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 210
shared_code_touched: false
depends_on: [T-2026-10-10-derived-endpoints-052.md, T-2026-10-10-version-commands-051.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求第三波「翻译/朗读/语音/reset」前端半。
- Problem/background: T-051/052 端点就绪；前端无任何消费入口。
- Users/scenarios: 学习者朗读回答（Web Speech）、语音输入提问（Chromium 系）、多版本切换、重新生成、查看译文。
- Expected outcome: MessageView 工具条（朗读/版本下拉/翻译/重新生成）+ TranslationBlock（自包含 SSE 订阅）+ Composer 麦克风（能力探测降级）。
- Core flow and reversibility: 新 lib 模块（speech/voiceInput）+ 组件扩展，全部新 props 可选，既有使用方零改动。
- Inputs/outputs: 见 store 新函数（resetMessage/selectRevision/startTranslation/loadDerivedArtifact）。
- State and exception handling: 朗读中再点=停止；听写错误置灰；翻译失败可重试。
- Constraints and trade-offs: 不新增依赖；Web Speech API 能力探测。
- AI recommendation and rationale: 翻译块用项目 `subscribeOperationEvents`（项目 SSE 统一 fetch-event-source，jsdom 无原生 EventSource）。
- User choice and custom additions: 已批准。
- AI-autonomous decisions: 版本切换本地立即生效（conversation 刷新由父拉取兜底）；翻译块独立订阅不占工作区活跃操作。
- User-confirmation decisions: 无。
- Acceptance evidence: artifacts/evidence/T-2026-10-10-053-*。
- Facts: `Operation` 类型 14 字段；`subscribeOperationEvents` handlers.onTerminal。
- Assumptions: SpeechRecognition 仅 Chromium 系（探测隐藏）。
- Unknowns: 无。
- Risks: Web Speech 声音质量随浏览器。
- Deferred: 翻译解析失败态专用 UI。
- Non-goals: 不做离线 TTS/STT。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

五项工具能力真实接线：189/189 + lint/build 全过，既有 184 例零回归。

## Scope

### In scope
- `web/frontend/src/lib/speech.ts`、`voiceInput.ts`（新）。
- `web/frontend/src/components/TranslationBlock.tsx`（新）。
- `MessageView` 工具条、`Composer` 麦克风、`BranchWorkspace` onReset 接线。
- store：resetMessage/selectRevision/startTranslation/loadDerivedArtifact。
- i18n 双语 11 key。测试 `ToolsAndBlocks.test.tsx`（6 例）。

### Out of scope
离线语音；翻译缓存 UI；版本 diff。

## Acceptance criteria
- [x] 工具条四能力（朗读/版本/翻译/重新生成）真实接线，无能力时隐藏。
- [x] TranslationBlock 走 §6.5 切片端点 + 项目 SSE + 产物读取（stale 透传）。
- [x] 麦克风能力探测（不支持则隐藏按钮）。
- [x] i18n 双语成对。
- [x] 全量 189/189 + lint/build exit 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `ToolsAndBlocks.test.tsx` 6 例按新行为先行
- Command: `cd web/frontend && pnpm test`
- Exit code: 1
- Observed failure: 新组件不存在，编译失败即红（TDD 探针轮另证：MessageView 工具条缺位时用例找不到按钮）
- Failure reason validated: 会话内首轮 vitest 输出（组件缺位失败）；GREEN 后全绿见证据文件

### GREEN — minimum implementation
- Implementation: lib 模块 + TranslationBlock + 工具条 + 麦克风 + 接线
- Command: `cd web/frontend && pnpm test`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-053-test.txt`（189/189，TEST-EXIT=0）
- Regression evidence: 同文件（27 文件含全部既有例）

### REFACTOR — optional
- Cleanup: TranslationBlock 原生 EventSource → 项目 subscribeOperationEvents；TS fixture 对齐 Operation 14 字段。
- Regression evidence: `artifacts/evidence/T-2026-10-10-053-{lint,build}.txt`（exit 0）

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测 约 210，处于软上限边界（仅警告））
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- `Operation` 类型无 kind/ordinal 字段（fixture 曾误加致 TS2353）。
- i18n 守卫拦行内 /* 中文 */ 注释（`//` 独立行放行）。
- SpeechSynthesisUtterance stub 用 class（箭头函数 this 报 TS2683）。

## Verification report

- Verdict: PASS（实现者自检全绿；合并独立 verifier 待统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-053-{test,lint,build}.txt`（189/189 + lint/build exit 0）
- Changed/deleted assertion rationale and equivalent behavior coverage: TranslationBlock 由原生 EventSource 重写为项目 `subscribeOperationEvents`（项目 SSE 统一走 `@microsoft/fetch-event-source`，jsdom 无原生 EventSource）；工具条对既有 MessageView 使用方零破坏（新 props 全可选）。
- Gaps: 翻译产物解析失败态展示（parse_state=parsed 才显示译文块）按当前实现以读取产物兜底；SpeechRecognition 仅 Chromium 系可用（能力探测降级）。
- Risks: 语音输入依赖浏览器 Web Speech API，Firefox 不支持（已探测隐藏）。

## Handoff notes

- Completed: 朗读/语音输入/版本切换/reset/翻译块 + 6 例测试 + 全量守门。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-054 附件后端。
- Watch-outs: `Operation` 类型无 `kind`/`ordinal` 字段（测试 fixture 曾误加）；i18n 守卫拦行内中文注释。


## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话页新增朗读/版本切换/翻译/重新生成工具条与 Composer 语音输入（用户可见）。
- Authoritative code/config/migration evidence: `web/frontend/src/components/{MessageView,Composer,TranslationBlock}.tsx`、`src/lib/{speech,voiceInput}.ts`、`src/store/topicData.ts`。
- Documentation surfaces checked: 契约与 `web/README.md` 状态节。
- Documentation files updated: 已按本轮交付同步。
- Producer/consumer/sibling/compatibility paths checked: `HomePage`/`SideBySideView` 既有 Composer/MessageView 用法零改动（新 props 全可选）。
- Superseded phrases/examples reverse-searched: 无（组件此前不存在）。
