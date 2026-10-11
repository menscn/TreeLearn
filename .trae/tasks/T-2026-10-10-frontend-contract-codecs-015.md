# Treelearn Harness Task: 前端契约编解码——TS 类型、API/SSE 客户端与 fixtures 往返

id: T-2026-10-10-015
title: 前端契约编解码——TS 类型、API/SSE 客户端与 fixtures 往返
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 420
shared_code_touched: false
depends_on: [T-2026-10-10-web-contract-design-014.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 契约已定稿（`web/contracts/api.md` + 100 fixtures）；前端尚无契约类型与 API/SSE 客户端，Phase 5 页面与 Phase 4 联调都依赖它。
- Users/scenarios: 后续页面/状态层消费统一 HTTP 与 SSE；错误按契约码本地化；SSE 断线/缺口/晚到事件按契约处理。
- Expected outcome: `web/frontend/src/api/` 内 TS 类型（与 api.md 逐字段一致）、统一 HTTP 客户端（错误封装→类型化 `ApiError`）、SSE 客户端（基于 @microsoft/fetch-event-source：Last-Event-ID、seq 去重、operationId 过滤、缺口补 snapshot、终态关流、心跳容忍）、错误码 i18n 双语文案；测试用 fixtures 全量往返。
- Core flow and reversibility: fixtures 作为唯一共享样例：TS 侧解析/构造并断言与 api.md 一致；可回退（纯新增文件）。
- Inputs/outputs: 输入=api.md + fixtures；输出=类型/客户端/测试/i18n 词条。
- State and exception handling: 网络错误、非 2xx 封装、SSE 断流、重复/晚到事件、未知事件类型（按契约忽略/容错）。
- Constraints and trade-offs: 遵循既有依赖方向（pages→layouts→components→hooks→store→api→platform→utils）；用户可见文案必须走 i18n（`zh-CN`/`en-US` 双语，`pnpm check:user-copy-i18n` 守卫）；不新增依赖（fetch-event-source 已在 T-002 选定并在 T-004 登记，须确认 package.json 已含；若无则按已批准依赖加入）。
- AI recommendation and rationale: 类型手写（与 api.md 一一对应）+ 运行时校验器/type guard（fixtures 驱动），不引入 zod。
- User choice and custom additions: 按已批准方案。
- AI-autonomous decisions: 文件划分、校验器形态、错误映射实现。
- User-confirmation decisions: 无新增。
- Acceptance evidence: RED/GREEN（`pnpm test`，cwd=web/frontend）、fixtures 往返测试输出、`pnpm lint`、`pnpm check:user-copy-i18n`、`pnpm build`。
- Facts: T-004 骨架含 i18n 渲染时解析与守卫、ESLint 依赖方向规则、Vitest+Testing Library；jsdom 30 无 dialog API 已有 polyfill。
- Assumptions: fixtures 路径可从测试读取（相对仓库路径）。
- Unknowns: 无。
- Risks: api.md 与 TS 类型漂移 → fixtures 往返 + contract-guardian；SSE 客户端行为需后续真实联调（Phase 4/5）。
- Deferred: 真实网络联调（Phase 4/5）。
- Non-goals: 页面/状态层；后端 Java 编解码（T-016）。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付与契约逐字段一致的 TS 类型、统一 HTTP/SSE 客户端与错误码双语 i18n，并以 fixtures 全量往返测试约束漂移。

## Scope

### In scope
- `src/api/types.ts`（全部 DTO）、`src/api/errors.ts`（42 码 + ApiError + i18n key 映射）、`src/api/client.ts`（统一 HTTP）、`src/api/sse.ts`（SSE 客户端）。
- i18n 资源新增 `errors.<code>` 条目（zh-CN + en-US 全量）。
- 测试：`fixtures/*` 全量解析往返（dtos 24 / errors 42 / sse 6 / commands 28 至少用于类型/字段校验）；SSE 状态机（序列应用、晚到忽略、缺口补快照、终态停止）；错误码↔i18n 完整性。
- 质量：`pnpm test`、`pnpm lint`、`pnpm check:user-copy-i18n`、`pnpm build` 全过。

### Out of scope
- 页面/状态层实现；后端 Java DTO/SSE 编码（T-016）；真实联调。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增 api 层；不改 AppShell/i18n 既有行为（仅新增词条）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 契约（producer=api.md）→ 本层 consumer；后续页面为 consumer。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 前端新增统一 API/SSE 客户端与错误码双语文案（用户可见：错误提示文案）。
- Authoritative code/config/migration evidence: `web/frontend/src/api/**`、i18n 资源、`package.json`。
- Documentation surfaces checked: `web/contracts/api.md`（消费约定）；`web/README.md` 前端状态节。
- Documentation files updated: `web/README.md` 前端状态节补一行（契约客户端就绪）。
- Producer/consumer/sibling/compatibility paths checked: fixtures 消费路径写入 `web/contracts/README.md` 的消费约定（若已含 TS 约定则核对一致，无需改则记录 `documentation impact: none` 于该面）。
- Superseded phrases/examples reverse-searched: N/A。
- `documentation impact: none` reason and evidence, if applicable: N/A。
- Historical/PRD handling: 不改写历史。

## Acceptance criteria

- [ ] RED→GREEN（同一 `pnpm test` 命令；RED 记录退出码与失败原因）。
- [ ] fixtures 往返：100 个 fixtures 全部被消费（dtos 字段/类型校验、errors 码与 i18n、sse 6 场景状态机、commands 作为请求样例的类型校验）；与 api.md 无字段漂移。
- [ ] SSE 客户端：`seq` 去重、按 `operationId` 过滤晚到、缺口/缺失 `Last-Event-ID` 时先应用 snapshot、终态关流、未知事件类型按契约忽略——均有测试。
- [ ] 错误码 i18n：42 码在 `zh-CN`/`en-US` 均有文案；映射完整性由测试断言。
- [ ] `pnpm lint`、`pnpm check:user-copy-i18n`、`pnpm build` 全过（含 token 守卫既有链）。
- [ ] 依赖方向规则未被破坏（api 层不反向依赖页面/store）。
- [ ] 独立 verifier PASS；contract-guardian 复核 TS↔契约一致 PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: `src/api/__tests__/`（fixtures 往返、错误码 i18n、SSE 状态机）。
- Command: `pnpm test`
- Exit code: 1
- Observed failure: 见证据
- Failure reason validated: 缺失类型/客户端

### GREEN — minimum implementation
- Implementation: types/errors/client/sse + i18n 词条。
- Command: `pnpm test`
- Exit code: 0
- Passing evidence: 见证据
- Regression evidence: 既有测试全绿 + build/lint 守卫

### REFACTOR — optional
- Cleanup: 按 lint/评审整理。
- Regression evidence: 同 GREEN。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- fixtures 从测试读取：用 `fs` + 相对 `web/contracts/fixtures` 的路径（以 `import.meta.url` 解析），断言失败信息要指明 fixture 文件名。
- 文案语言：错误文案用中文为主、英文为译，措辞可行动（如「未配置 API Key，请到设置填写」），不含内部码/堆栈。
- SSE 客户端错误不直接暴露网络库报错文案（规则要求边界转换）。

## Verification report

- Verdict: PASS（独立 verifier 两轮：首轮功能 PASS；修复回归后 PASS。contract-guardian 首轮 FAIL→修复后回归 PASS）
- Evidence: 独立 verifier 自跑 `pnpm test`（cwd=web/frontend）32 passed / 0 failed、`pnpm lint`/`check:user-copy-i18n`/`build` 全 EXIT=0（回归复核与实现自证一致）；contract-guardian 对 24 DTO + 42 码 + 6 SSE 场景 + 契约四处 `kind` 大小写逐一核对后 PASS（含 §6.5 请求词汇表与 §5.4 versions 措辞裁定）。证据 `artifacts/evidence/T-2026-10-10-015-{red,green,lint,i18n,build,gitstatus}.txt` 与 `...-015-fix-{test,lint,i18n,build,json}.txt`。
- 修复记录：① `kind` 硬漂移（guardian 裁定：Android `ArtifactKind.wireId` 落库小写，契约表格正确）→ api.md 示例、fixture、TS 枚举、测试四处统一为小写；② §6.5 显式声明请求词汇表（大写 7 项、`MESSAGE_TRANSLATION` 不落库）；TS 拆 `DerivedRequestKind`/`DerivedArtifactKind`；③ §5.4 `versions` 明确为完整 `AnswerRevision` 对象；④ `sse.ts` 新增 `onError`/`SseStreamError`（不泄漏原始报错、终态后不误报）；未知事件可解析 `seq` 时推进 cursor（消除假缺口）；⑤ fixtures 测试改为编译期键完备断言（`Exclude<keyof T, K[number]> extends never`）+ 运行时键集比对，替代与 types.ts 不联动的 Shape 副本（verifier 用临时探针实测可拦截漂移）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无削弱；新增 5 项 SSE 用例与 1 项 fixtures 用例。
- Gaps: 生产行 499（≤500 硬上限、>200 软线警告），后续编辑须重算；`sse.ts` 传输级 onError 端到端行为属 Phase 4 真实联调范畴（当前单测覆盖到 I/O 边界）。
- Risks: `process.cwd()` 依赖测试从 `web/frontend` 运行（换 cwd 失效）；契约变更须同任务更新 TS 类型与 fixtures 并回归 contract-guardian。

## Handoff notes

- Completed: 实施 + 两轮修复 + 双守卫 PASS（2026-10-10）。产物：`src/api/{types,errors,client,sse}.ts`、`src/api/__tests__/*`、i18n 各 +44 词、`@microsoft/fetch-event-source@2.0.1`（MIT 登记）。
- In progress: commit 由主会话执行。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: Phase 5 页面/状态层消费本层；T-016（Java DTO/SSE codec）对齐同一 fixtures。
- Watch-outs: fixtures 是唯一共享样例来源；`kind` 落库词汇为小写 wireId；生产行已接近 500，新增须同步核算。