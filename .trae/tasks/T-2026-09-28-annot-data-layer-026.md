# Filename: T-2026-09-28-annot-data-layer-026.md
# Treelearn Harness Task: 高亮批注数据层（T-A）

id: T-2026-09-28-026
title: 高亮批注数据层 + 纯逻辑（T-A）
template_version: 2
task_type: implementation
status: ready
parent_requirement: T-2026-09-28-chat-highlight-annotation-025.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 460
shared_code_touched: true
depends_on: []
tdd_mode: required

## Requirement collaboration brief

Parent summary: 见父需求 T-2026-09-28-chat-highlight-annotation-025.md——对话页正文高亮与批注，方案 V1.1 已全部拍板。

- Problem/background: 标记数据无处存：需新增 `message_annotations` 表 + DAO + 纯逻辑定位/判重/合并。
- Expected outcome: DB V4→V5（fresh 与升级两路都建表）；`AnnotationDao`（含 digest 失配清除）；`app/ui/Annotation.java` 纯 JVM 可测。
- Core flow and reversibility: 写入高亮/批注行；读路径 `listFor(messageId, digest)` 先 `DELETE WHERE digest <> ?` 再 SELECT（自愈）。
- State and exception handling: 流式期间调用方不传 stable digest 时不触发清除（清 除只在 `isStable()` 语义下调用，接口设计体现）。
- Constraints and trade-offs: 单表 + `container` 哨兵列（PROSE: prose_index>=0 其余 -1；CELL: table/row/col>=0 prose_index=-1）；`kind` 入唯一索引；`anchor_text` 列已删（拍板 B）。
- AI recommendation and rationale: 方案 §3/§5。
- User choice and custom additions: 方案 §3.1 DDL 随 8 项拍板获用户确认（2026-09-28）。
- AI-autonomous decisions: DDL 字符串常量 fresh/upgrade 复用（防两路漂移）。
- User-confirmation decisions: 建表确认见上；Harness 开工方式。
- Acceptance evidence: 本文件 TDD 记录 + 真机 SQLite 仪器测试证据。
- Facts: `TreeLearnSchema.java` 现 `DATABASE_VERSION = 4`、11 表、`FORWARD_MIGRATIONS` 至 V3→V4（方案 §1 核实）。
- Assumptions: minSdk 28，无 API 障碍。
- Unknowns: 无。
- Risks: 迁移失败留半迁移库（须证明回滚）；仪器测试与别线抢真机。
- Deferred: UI 一切（T-B/C/D）。
- Non-goals: 不改既有 11 表。
- Skipped non-critical questions and reason: 无。

## Goal

`message_annotations` 表（含索引/CHECK）+ V4→V5 迁移 + `AnnotationDao`（digest 清除/增删改/mergeRange）+ `Annotation` 纯逻辑（严格重合判重、并区间合并、定位器），全部有 RED→GREEN 证据。

## Scope

### In scope
- `android/data/.../db/TreeLearnSchema.java`：`Annotations` 常量类、`TABLE_NAMES`、`CREATE_TABLE_STATEMENTS`、`CREATE_INDEX_STATEMENTS`、`MIGRATION_V4_TO_V5_STATEMENTS`、`MigrationV4ToV5` 注册、版本 5。
- `android/data/.../db/dao/AnnotationDao.java`（新）：`listFor(messageId, digest)`（内含清除）、`insert`、`delete`、`updateBody`、`mergeRange`。
- `SqliteChatStore` 接线（最小暴露面）。
- `android/app/.../ui/Annotation.java`（新）：定位器、严格重合判重（`isExactOverlap`）、并区间合并（`mergeInto`）+ JVM 测试。
- 仪器测试：真实 Android SQLite 上 onCreate/onUpgrade/PRAGMA/约束/级联/重开。

### Out of scope
- span 渲染、菜单、便签 UI（T-B/C/D）；既有表改动。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增表与 DAO；既有 11 表 schema、既有 store 行为不变（V4 升级后旧数据逐行保留）。
- Producer/consumer: producer=ChatScreen 标记动作（T-B/D 接入）；consumer=标记读路径。本任务内 consumer=DAO 测试。
- Platform branches: 手机/平板同一 DB，无分支。
- Behavior-neutral non-applicability reason: N/A。

## Documentation impact record

- Changed runtime facts: `DATABASE_VERSION` 4→5；表清单 11→12。
- Authoritative evidence: schema 常量类 + 迁移语句常量。
- Documentation files updated: `android/README.md` 表清单/版本行（完成后）；表清单与架构文档同变更更新（sql.md 要求）。
- Superseded phrases reverse-searched: 「DATABASE_VERSION = 4」「11 张表」出现处完成后反查更新。

## Acceptance criteria

- [ ] 真实 Android SQLite（API 28+ 设备/模拟器）上：fresh install 经生产 `onCreate` 建出表+2 索引，`PRAGMA user_version=5`；`PRAGMA foreign_keys=ON`、`foreign_key_check` 空、`integrity_check=ok`。
- [ ] `onUpgrade` V4→V5 后既有行逐表保留（升级前后行数相等断言）。
- [ ] CHECK/唯一/FK 约束生效：非法 container 哨兵组合被拒；同 (message,container,定位,kind,start,end) 重复插入被拒；`message_id` 外键级联删除生效；NOTE 空 body 被拒、HIGHLIGHT 带 body 被拒。
- [ ] `AnnotationDao.listFor` 先删 digest 失配行再返回；事务回滚路径不留半迁移库。
- [ ] `Annotation` 纯逻辑 JVM 测试：严格重合判重（部分重叠≠重复）、相交合并为并区间、定位器 equals/边界。
- [ ] 关闭重开后数据一致；JVM 全量单测绿；Java-only guard PASS；`validate_tasks.py` PASS。

## TDD execution record

### RED — failing test first
- Test/scenario: PENDING（实现时记录：先写 DAO/迁移仪器测试与 Annotation JVM 测试，跑出非零退出码）。
- Command: PENDING
- Exit code: PENDING
- Observed failure: PENDING
- Failure reason validated: PENDING

### GREEN — minimum implementation
- Implementation: PENDING
- Command: PENDING
- Exit code: PENDING
- Passing evidence: PENDING
- Regression evidence: PENDING

### TDD or sizing exception — only with prior user approval
- Exception type: 无（估 460 行 >200 软上限，verifier 警告级；<500 红线内，先例 T-004/T-022）。

## Implementation notes

- **建表入场记录（table admission guard，sql.md 强制）**：
  - 为何不用既有存储：标记是按 (消息,容器,区间,kind) 可枚举、需唯一约束与级联删除的一等业务事实；塞进 messages 有界 JSON 会失去唯一索引判重与行级删除，且与既有 11 张规范化表的设计不一致。
  - 领域/权威来源：学习标记（对话域）；producer=用户标记动作；consumer=对话页渲染与读路径。
  - 行标识/基数/增长：`id`(UUID)；每消息 0~数百行，随使用线性增长。
  - 保留与删除：随消息级联删除；digest 失配自愈清除；无归档需求。
  - 敏感字段：body 为用户手写批注文本（本机 SQLite，无出域）。
  - 索引/约束：唯一锚索引（含 kind）+ message_id 索引 + 5 条 CHECK + FK ON DELETE CASCADE。
  - 跨实例：无（单机）。发布/升级：V4→V5 前向迁移，历史迁移不可改写。
  - 可观测性：DAO 操作随既有 store 日志纪律（无新增噪声）。
  - 废弃触发：若标记功能下线，随该特性删除表（前向迁移）。
  - 用户确认证据：方案 §3.1 完整 DDL 于 2026-09-28 随 8 项拍板获用户确认（方案变更记录 V1.1/15:23 行）；本表为第 12 张，超出「已确认七张」清单，按 sql.md 建表审批边界由父需求记录确认链。
- DDL 一律复用字符串常量；时间字段用应用显式写入的 UTC epoch millis `INTEGER`（sql.md）。
- `db-migration-reviewer` 在 schema 设计定稿后、实现收口前各过一遍。
- 仪器测试真机窗口与别线协调；AGP 测试后重装应用（工作区记忆）。

## Verification report

- Verdict: PENDING

## Handoff notes

- Completed: 任务文件已建。
- In progress: —
- Blocker: 无。
- Unblock condition: —
- Next steps: RED（DAO 仪器测试 + Annotation JVM 测试）→ GREEN（schema/DAO/Annotation）→ 真机 SQLite 证据 → db-migration-reviewer。
- Watch-outs: 与 T-B/C/D 的接口一旦定稿不再改签名；跨线并发期构建带 `--no-build-cache`。
