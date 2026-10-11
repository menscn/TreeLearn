# Treelearn Harness Task: 附件端点（§6.9）

id: T-2026-10-10-054
title: 附件端点（§6.9）
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 280
shared_code_touched: false
depends_on: [T-2026-10-10-frontend-tools-053.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求第三波「附件注册与解析」后端。
- Problem/background: 契约 §6.9 与桌面 schema（attachments/content_hash/message_attachments 表）已冻结；后端零实现。
- Users/scenarios: 学习者上传 .txt/.md 附件 → 内容寻址去重 → 引用进对话 → 文件管理页删除/清理。
- Expected outcome: `AttachmentStore`（存储=数据目录 attachments/ 下以 content_hash 命名、同哈希复用、孤儿判定、幂等清理）+ `AttachmentController`（POST/GET/GET content/DELETE/cleanup-orphans）。
- Core flow and reversibility: Store 单事务（唯一索引防同哈希并发双行）+ 文件系统写（覆盖原子）；纯新增。
- Inputs/outputs: 见契约 §6.9；错误码 `ATTACHMENT_TOO_LARGE`（params.maxBytes）、`ATTACHMENT_TYPE_UNSUPPORTED`、`ATTACHMENT_NOT_DELETABLE`。
- State and exception handling: 超限/类型拒绝在落盘前；DELETE 仅无引用（有引用 409）；解析 .txt/.md 同步 READY，其余 pending（Worker Deferred）。
- Constraints and trade-offs: 不改 schema（表已就绪）；max-bytes 可配置（默认 20 MiB）。
- AI recommendation and rationale: 镜像 Android `AttachmentStore` 语义（register 去重/list/orphans/cleanup 幂等/缺失标注）。
- User choice and custom additions: 已批准。
- AI-autonomous decisions: 内容哈希 SHA-256；存储目录 `{dataDir}/attachments/`；解析 Deferred 明示。
- User-confirmation decisions: 无。
- Acceptance evidence: artifacts/evidence/T-2026-10-10-054-*。
- Facts: Android `AttachmentStore.register/list/delete/orphans/cleanupOrphans` 语义；`ReferenceRowGuard` FK。
- Assumptions: 单实例本地存储（无分布式去重需求）。
- Unknowns: 无。
- Risks: 大文件内存缓冲（受 max-bytes 约束）。
- Deferred: 解析 Worker；PDF/DOCX 抽取；附件进生成上下文。
- Non-goals: 不做分块上传。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

附件五端点按 §6.9 与 Android 语义工作：7 例真实 HTTP+SQLite（去重/引用计数/缺失标注/孤儿清理幂等），全量回归零破坏。

## Scope

### In scope
- `web/backend/.../storage/AttachmentStore.java`（新）。
- `web/backend/.../api/AttachmentController.java`（新）。
- `GenerationConfiguration`：AttachmentStore bean。
- 测试 `AttachmentEndpointsTest`（7 例）。

### Out of scope
解析 Worker；PDF/DOCX；上下文注入。

## Acceptance criteria
- [x] RED→GREEN：7 例（404→全绿）。
- [x] 同内容去重（同 id 返回、不重复落盘）；引用计数经 message_attachments 真实 FK。
- [x] DELETE 无引用才允许、保留引用行（缺失标注语义）、幂等。
- [x] cleanup-orphans 幂等；超限/类型拒绝带参数。
- [x] 全量 `:check` 307 例 + checkstyle exit 0。

## TDD execution record

### RED — failing test first
- Test/scenario: `AttachmentEndpointsTest` 7 例
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.AttachmentEndpointsTest`
- Exit code: 1
- Observed failure: 7 tests completed, 7 failed（404）
- Failure reason validated: `artifacts/evidence/T-2026-10-10-054-red.txt`（EXITCODE=1）

### GREEN — minimum implementation
- Implementation: `AttachmentStore` + `AttachmentController` + bean
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.AttachmentEndpointsTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-054-green.txt`（7/7）
- Regression evidence: `artifacts/evidence/T-2026-10-10-054-check.txt`（307 PASSED / 0 FAILED / EXITCODE=0）

### REFACTOR — optional
- Cleanup: queryList 统一 NO_PARAMS binder；测试 FK 目标改真实消息 seed（047 同款）。
- Regression evidence: 同 check 文件。

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行实测 281（185+96），超软上限 200、未超 500 硬上限（仅警告））
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- `AttachmentParseState` 枚举值小写（parsed/pending/parse_failed）。
- 错误响应 params 在 `error.params` 下（断言曾写 `error.params` 顶层）。
- `StorageException(message, cause)` 构造存在。

## Verification report

- Verdict: PASS（实现者自检全绿；合并独立 verifier 待统一复核）
- Evidence: `artifacts/evidence/T-2026-10-10-054-{red,green,check}.txt`（check 307 例 0 失败 + checkstyle）
- Changed/deleted assertion rationale and equivalent behavior coverage: 附件引用行 FK 指向真实消息（测试用 047 同款 seed 构造稳定 AI 消息），而非伪 id 直插——保证「删除保留引用行（缺失标注）」断言在真实 FK 下成立。
- Gaps: 解析流水线（PARSE_STATE 排队/完成）未接 Worker（当前 .txt/.md 同步就绪，其余类型 pending）；附件尚未进发送上下文（assembler 侧 Deferred）。
- Risks: 大文件内存缓冲（base64 解码）受 max-bytes 上限约束（默认 20 MiB）。

## Handoff notes

- Completed: AttachmentStore/AttachmentController + bean + 7 例 + 全量 check。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-055 附件前端。
- Watch-outs: `AttachmentParseState` 枚举值小写（`parsed`/`pending`/`parse_failed`）；错误响应 params 在 `error.params` 下；`ReferenceRowGuard` 拒绝直插伪 message id。


## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 附件五端点由 404 变为按 §6.9 可用（用户可见）。
- Authoritative code/config/migration evidence: `web/backend/.../storage/AttachmentStore.java`、`.../api/AttachmentController.java`、`.../generation/GenerationConfiguration.java`。
- Documentation surfaces checked: 契约与 `web/README.md` 状态节。
- Documentation files updated: 已按本轮交付同步。
- Producer/consumer/sibling/compatibility paths checked: 前端 uploadAttachment/listAttachments/deleteAttachment/cleanupOrphanAttachments（T-055 消费）；DesktopSchema 表 T-009 已建。
- Superseded phrases/examples reverse-searched: 无（端点此前不存在）。
