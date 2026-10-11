# Treelearn Harness Task: 批注端点（§6.10）

id: T-2026-10-10-047
title: 批注端点（§6.10）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-10-treelearn-local-web-edition-001.md
requirements_confirmed: true
generated_by: leader
owner: backend-dev
owner_lane: backend
stack: backend
estimated_production_lines: 240
shared_code_touched: false
depends_on: [T-2026-10-10-tree-layout-endpoint-045.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-10-001` 第二波交付（用户指令「继续完成后续的任务」）。
- Problem/background: 共享域 `Annotation` 与桌面 `message_annotations` 表已在，但端点不存在（404）；契约 §6.10 已冻结。
- Users/scenarios: 学习者在答案上划选加亮（相交/相邻自动合并）或写批注；答案重生成后旧批注按 digest 自愈清理。
- Expected outcome: GET 按显示文本 SHA-256 清理失配行再查（同事务、删除不重定位）；POST HIGHLIGHT 走 `Annotation.mergeHighlights`（absorbed 删+并集插同事务）、NOTE 非空；DELETE 幂等；生成中 AI 消息拒绝写入 409 `OPERATION_NOT_STABLE`。
- Core flow and reversibility: 控制器薄 → `MessageStore.get`/`AnswerRevisionStore.findActiveForMessage` 取显示文本 → digest → `AnnotationStore`。纯新增，无 schema 变更。
- Inputs/outputs: 输入=`AnnotationRequest{commandId,kind,container,proseIndex?,tableIndex?,rowIndex?,colIndex?,start,end,body?}`；输出=`AnnotationList`/`AnnotationWritten`/`AnnotationDeleted`。错误：404 `NOT_FOUND`、400 `NOTE_BODY_REQUIRED`/`ANCHOR_INVALID`、409 `OPERATION_NOT_STABLE`。
- State and exception handling: 区间非法（start<0/end<=start/end>文本长）→ `ANCHOR_INVALID`；container 非 PROSE/CELL → `ANCHOR_INVALID`。
- Constraints and trade-offs: 镜像 Android 语义与共享域唯一判定；不改契约既有章节语义；不改 Android/schema。
- AI recommendation and rationale: 与 042/043/045 同构的「镜像 Android + 共享域判定」模式。
- User choice and custom additions: 已批准（继续后续任务指令）。
- AI-autonomous decisions: digest 算法对齐 Android `SqliteChatStore.digest`（显示文本 UTF-8 SHA-256 hex）；`AnnotationStore` 镜像 Android `AnnotationDao.listFor` 的同事务清理。
- User-confirmation decisions: 无。
- Acceptance evidence: `artifacts/evidence/T-2026-10-10-047-{red,green,check}.txt`。
- Facts: `Annotation.highlight/note/mergeHighlights`、DesktopSchema.Annotations（含两索引）、`MessageStore.get`、`AnswerRevisionStore.findActiveForMessage`。
- Assumptions: 批注只挂 AI 答案文本 digest；USER 消息 digest=user_content（端点语义对称）。
- Unknowns: 无。
- Risks: 合并事务里 absorbed 删除用 `AnnotationStore.delete`（单行 DELETE，处于同一 `runInTransaction` 连接内）。
- Deferred: 前端高亮渲染（T-049）；CELL 容器的前端划选。
- Non-goals: 不扩大范围到矩阵外功能。
- Skipped non-critical questions and reason: 常规实现选择自主处理。

## Goal

交付 §6.10 批注端点：真实 HTTP+SQLite 证据证明 digest 自愈清理、HIGHLIGHT 合并、NOTE 校验、DELETE 幂等、生成中拒绝。

## Scope

### In scope
- `web/backend/.../storage/AnnotationStore.java`（新）
- `web/backend/.../api/AnnotationController.java`（新，含 DTO）
- `GenerationConfiguration` 补 `annotationStore` bean
- `AnnotationEndpointsTest`（9 例）

### Out of scope
- 前端渲染；CELL 划选；契约改动。

## Acceptance criteria
- [x] 契约 §6.10 语义落地：digest 自愈/合并/幂等/生成中拒绝
- [x] RED→GREEN 9 例真实 HTTP+SQLite
- [x] 后端全量 `:check` exit 0（284 例+checkstyle）

## TDD execution record

### RED — failing test first
- Test/scenario: `AnnotationEndpointsTest` 9 例（404/空/digest 自愈/合并/NOTE 校验/区间非法/DELETE 幂等/生成中拒绝）
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.AnnotationEndpointsTest`
- Exit code: 1
- Observed failure: 8 failed / 9 completed（端点 404 或无 error.code 响应；`missingMessageIs404` 因全局 404 JSON 兜底先过）
- Failure reason validated: `artifacts/evidence/T-2026-10-10-047-red.txt`（`EXITCODE=1`）。

### GREEN — minimum implementation
- Implementation: `AnnotationStore` + `AnnotationController` + bean 注册
- Command: `cd web/backend && ./gradlew --init-script ../../artifacts/evidence/T-2026-10-10-042-isolation.gradle --project-cache-dir ... --no-build-cache :test --tests com.treelearn.web.api.AnnotationEndpointsTest`
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-10-047-green.txt`（EXITCODE=0）。
- Regression evidence: `artifacts/evidence/T-2026-10-10-047-check.txt` `:check` BUILD SUCCESSFUL EXITCODE=0（284 例 0 失败+checkstyle）

### REFACTOR — optional
- Cleanup: `Message` 为共享域类型（无 activeRevisionId）→ 显示文本经 `findActiveForMessage`；测试导入修剪（checkstyle）。
- Regression evidence: `artifacts/evidence/T-2026-10-10-047-check.txt` `:check` BUILD SUCCESSFUL EXITCODE=0（284 例 0 失败+checkstyle）

### TDD or sizing exception — only with prior user approval
- Exception type: 无（非空非注释生产行 ≈260，超软上限 200、未超 500 硬上限；仅软预算警告）
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- digest 失效清理与查询在同一事务且查询自带 digest 过滤（reviewer F2）。
- 生成中写入拒绝是调用方义务的显式化（reviewer F3）：挂在 transient digest 上的行下次读取必被清理。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: `GET/POST /api/messages/{id}/annotations`、`DELETE /api/annotations/{id}` 由 404 变为按 §6.10 可用（用户可见）
- Authoritative code/config/migration evidence: `web/backend/.../api/AnnotationController.java`、`.../storage/AnnotationStore.java`、`.../generation/GenerationConfiguration.java`
- Documentation surfaces checked: 契约与 `web/README.md`。
- Documentation files updated: README 状态节已按本任务交付同步。
- Producer/consumer/sibling/compatibility paths checked: 前端 `src/api/types.ts` 已有 Annotation/AnnotationList 类型（T-014 契约客户端），本任务未改前端
- Superseded phrases/examples reverse-searched: 无（新增端点/组件此前不存在）。

## Verification report

- Verdict: PASS（实现者自检全绿；独立 verifier 终验待统一复核）
- Independent verifier re-check (2026-10-11, READONLY subagent, evidence-free re-runs, merged pass covering T-045..049): PASS——后端 :check 复跑 exit 0（284 PASSED / 0 FAILED + checkstyle 0 error）、前端 test 180/180 + lint + 守卫自测/扫描 + build 全 exit 0、validate_tasks.py errors=0（warnings=33 软预算）；证据交叉核对一致（045 red 11 败→green 11/11、047 red 8/9→green 9/9、048-red 回退实录 exit 1）；代码抽查（树布局装配/批注判定/划选映射/Markdown 兼容）逐条符合；E2E 证据脱敏确认（无 Key 明文）。非阻塞发现：046-guard 过程证据含已修复的旧拦截；1 例 waitFor 偶发超时复跑过（Gradle 并行抢 CPU）；CELL 划选/手势缩放/浏览器视觉复核为遗留项。
- Evidence: `artifacts/evidence/T-2026-10-10-047-{red,green,check}.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除既有断言。
- Gaps: 前端渲染未做（T-049）
- Risks: 合并依赖 `delete` 在事务连接内执行（`runInTransaction` 保证）

## Handoff notes

- Completed: 实现与证据全部落盘，守门全绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: N/A。
- Next steps: T-048/049 前端消费；来源定位/选文引用
- Watch-outs: USER 消息也可挂批注（digest=user_content）；CELL 容器字段完整性由 locatorOf 把守
