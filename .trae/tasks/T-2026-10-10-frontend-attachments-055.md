# Treelearn Harness Task: 前端附件消费 + 文件管理页（T-055）

id: T-2026-10-10-055
title: 前端附件消费 + 文件管理页（T-055）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 140
shared_code_touched: false
depends_on: [T-2026-10-10-attachment-endpoints-054.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求第三波「附件注册与解析、文件管理与清理」前端半。
- Problem/background: T-054 端点就绪；前端无上传/管理入口；发送请求 attachmentId 写死 null。
- Users/scenarios: 学习者在 Composer 加附件（Paperclip→文件选择→上传→chip 待发）、发送带附件；USER 消息显示附件下载行（缺失标注）；设置-文件管理页做列表/用量/删除/孤儿清理。
- Expected outcome: 全链路可用 + 文件管理页（settings 第四分区）。
- Core flow and reversibility: store 5 函数 + Composer/MessageView 扩展（可选 props）+ FilesPage + settings 分区；可回退。
- Inputs/outputs: 见契约 §6.9。
- State and exception handling: 上传中防重入；错误统一走 actionError；缺失附件灰态下载链接。
- Constraints and trade-offs: 不动 platform/router Route 类型（文件管理做 settings 分区而非顶级路由，与既有设置页模式一致、测试面最小）。
- AI recommendation and rationale: settings 分区复用 SettingsLayout 导航，零路由类型变更。
- User choice and custom additions: 已批准（第三波连续执行指令）。
- AI-autonomous decisions: 文件管理页文案「解析完成/解析失败/解析排队中」映射小写枚举；下载链接直连 content 端点。
- User-confirmation decisions: 无。
- Acceptance evidence: artifacts/evidence/T-2026-10-10-055-*。
- Facts: `sendBranchMessage` 请求含 attachmentId；`Message.attachments` AttachmentRef[]；SettingsSection 联合类型。
- Assumptions: 单文件待发（契约 attachmentId 单值）。
- Unknowns: 无。
- Risks: 无已知。
- Deferred: 附件进生成上下文展示。
- Non-goals: 多附件批量上传。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

附件全链路前端可用：上传→待发→发送→消息附件行→文件管理页管理，195/195 + 全守门全过。

## Scope

### In scope
- store：uploadAttachment/listAttachments/loadAttachmentContent/deleteAttachment/cleanupOrphanAttachments。
- `Composer`：附件按钮 + 待发 chip；`BranchWorkspace`：pendingAttachment + 发送透传。
- `MessageView`：USER 附件下载行。
- `FilesPage.tsx`（新）+ SettingsLayout/Router files 分区。
- i18n 双语 15 key。测试 `ComposerAttachment.test.tsx`（2 例）+ `FilesPage.test.tsx`（4 例）。

### Out of scope
多附件；附件进上下文展示。

## Acceptance criteria
- [x] Composer 附件上传与待发 chip（含未命名/清除）。
- [x] 发送 attachmentId 透传（缺省 null 向后兼容）。
- [x] USER 消息附件行（present/missing 灰态）。
- [x] 文件管理页：列表/用量/引用计数/解析态/删除（仅无引用）/孤儿清理/错误条。
- [x] 全量 195/195 + lint/guard/build exit 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `ComposerAttachment.test` + `FilesPage.test` 6 例按新行为先行
- Command: `cd web/frontend && pnpm test`
- Exit code: 1
- Observed failure: FilesPage/附件 chip 不存在，组件缺位失败
- Failure reason validated: 会话内首轮 vitest 输出；GREEN 后全绿见证据文件

### GREEN — minimum implementation
- Implementation: store 5 函数 + Composer/MessageView/FilesPage + settings 分区接线
- Command: `cd web/frontend && pnpm test`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-055-test.txt`（195/195，TEST-EXIT=0）
- Regression evidence: 同文件（27 文件含全部既有例）

### REFACTOR — optional
- Cleanup: FilesPage 测试从 components 拆至 pages 层（eslint 分层守卫命中：components 不得 import pages）。
- Regression evidence: `artifacts/evidence/T-2026-10-10-055-{lint,build}.txt`（exit 0）

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测 约 140，未超软上限）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 测试 mock store 返回纯 body 而非 Response（vi.mock 整模块替换）。
- 跨表达式文本断言用正则（getByText 精确匹配会因多表达式拆分失败）。
- SettingsSection 扩 'files'（lucide 无 Files 图标，用 Library）。

## Verification report

- Verdict: PASS（实现者自检全绿；合并独立 verifier 待统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-055-{test,guard,lint,build}.txt`（195/195 + 全部守门 exit 0）
- Changed/deleted assertion rationale and equivalent behavior coverage: FilesPage 测试从 components 层拆至 pages 层（eslint 分层守卫：components 不得 import pages）；发送请求 `attachmentId` 通道从写死 null 改为透传（向后兼容：缺省 null）。
- Gaps: 附件内容尚未进入生成上下文（后端 assembler Deferred）；文本抽取仅 .txt/.md 同步就绪。
- Risks: 无已知。

## Handoff notes

- Completed: Composer 附件上传/待发 chip、USER 消息附件下载行、文件管理页（列表/用量/删除/孤儿清理）、settings files 分区、8 例测试 + 全量守门。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: 第三波收尾文档 + Android fixtures 等价验证。
- Watch-outs: 测试文本断言对跨表达式文本须用正则；mock store 函数返回纯 body 而非 Response。


## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 对话页支持附件上传/发送/下载，设置页新增文件管理分区（用户可见）。
- Authoritative code/config/migration evidence: `web/frontend/src/components/{Composer,MessageView}.tsx`、`src/pages/{FilesPage,BranchWorkspace}.tsx`、`src/layouts/SettingsLayout.tsx`、`src/pages/Router.tsx`、`src/store/topicData.ts`。
- Documentation surfaces checked: 契约与 `web/README.md` 状态节。
- Documentation files updated: 已按本轮交付同步。
- Producer/consumer/sibling/compatibility paths checked: `HomePage` Composer 用法零改动（新 props 可选）；SettingsLayout 导航自动出现新分区。
- Superseded phrases/examples reverse-searched: 无（页面此前不存在）。
