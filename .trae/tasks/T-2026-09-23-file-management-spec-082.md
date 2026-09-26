# Treelearn Harness Task: 规格｜附件与文件管理

id: T-2026-09-23-082
title: 规格｜附件与文件管理
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-23-rikkahub-settings-capabilities-078.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务产出附件体系与文件管理页的规格。
- Problem/background: 知芽无附件能力；RikkaHub 支持图片/PDF/Docx 上传解析并管理聊天文件。矩阵 6.1 曾 defer，本次被用户指令推翻。
- Users/scenarios: 学习者上传教材 PDF/截图提问；在文件管理页查看、删除聊天产生的文件。
- Expected outcome: `Docs/spec/SPEC-2026-09-23-file-management.md`，过自检脚本。
- Core flow and reversibility: 输入栏附件键 → 系统文件选择器（无存储权限，走 SAF）→ 选中复制入应用私有目录 → 解析为文本/图像描述入当次上下文 → 发送；文件管理页列表/删除/占用统计；删除被消息引用的文件保留消息文本、标注附件缺失。
- Inputs/outputs: 输入 RikkaHub 文件页与解析实现事实（`data/files/`、解析 provider，路径由规格作者核对）；输出规格。
- State and exception handling: [PERM] SAF 拒绝直接取消无副作用；[PART] 解析失败保留原文件并提示，不阻断发送（可去掉附件重试）；超大文件拦截阈值明确。
- Constraints and trade-offs: Java-only + 无新依赖原则：PDF/Docx 文本抽取是最大风险点——须评估「Android 自带 `PdfRenderer`（仅渲染）+ 纯 Java 轻量抽取」 vs 「放宽依赖引入 Apache PDFBox 类库」；图片附件在 DeepSeek 等纯文本模型下的降级（仅记录不送模型 or OCR 另立）。
- AI recommendation and rationale: 第一批支持「纯文本类文件（txt/md）+ PDF 文本层」；Docx 与图像 OCR 列拍板区；依赖放宽须用户明示批准。
- User choice and custom additions: 用户确认文件管理页 + 附件能力都要；格式范围待拍板。
- AI-autonomous decisions: 附件与消息强引用关系沿用消息表外键式引用（具体 schema 由 096 规格细化落地时定）。
- User-confirmation decisions: ①格式范围（txt/md/PDF 第一批，Docx/图像是否入）；②是否允许引入 PDFBox 类依赖（违反无新依赖原则须例外）；③附件大小上限。
- Acceptance evidence: SPEC 存在、自检退出码 0、拍板区齐。
- Facts: 知芽输入栏（T-076）有明确扩展入口禁伪实现约定；`android/data` 模块为 SQLiteOpenHelper。
- Assumptions: SAF（Storage Access Framework）可满足选择需求，无需 READ_EXTERNAL_STORAGE。
- Unknowns: PDFBox 在 minSdk 28 的可用性与体积影响。
- Risks: 大文件解析阻塞主线程；附件进上下文撑爆 token 预算——须设注入上限。
- Deferred: 语音文件、OCR、云盘导入。
- Non-goals: 不做文件编辑/预览器（仅管理）；不引文件服务器。
- Skipped non-critical questions and reason: 文件列表排序默认按时间倒序。

## Goal

产出附件选择、解析、存储与文件管理页的完整规格，供 096/097/098 实现任务直接引用。

## Scope

### In scope

- 输入栏附件入口与选择流（SAF）。
- 文件存储布局、消息-附件引用关系、解析入上下文的上限与降级。
- 文件管理页（列表、大小、删除、占用统计）。

### Out of scope

- 代码实现；OCR；其他三块能力。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯文档。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无代码改动。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 文档任务。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无。
- Authoritative code/config/migration evidence: RikkaHub `data/files/` 相关源（规格作者核对）；Android SAF 文档。
- Documentation surfaces checked: `Docs/spec/`。
- Documentation files updated: 新建 SPEC。
- Producer/consumer/sibling/compatibility paths checked: 不适用。
- Superseded phrases/examples reverse-searched: T-076 中「附件入口延后」表述由本需求推进，落地时更新。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [ ] SPEC 存在且自检脚本退出码 0。
- [ ] 依赖放宽与格式范围拍板完成；解析失败/超大文件降级路径齐备。
- [ ] 用户 review 完成。

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable（文档任务）
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: 由 spec-author 子代理（agent-a7c38f5e）产出 `Docs/spec/SPEC-2026-09-23-file-management.md` 四节规格；主会话独立复跑自检
- Command: `python .trae/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-23-file-management.md`
- Exit code: 0
- Passing evidence: 四节齐全（sections=4, missing=[]）、intent 指向真实文件、无占位符、无裸 hex/px 全 OK（主会话 2026-09-23 复跑 EXITCODE=0）；attachments+message_attachments 双表草案与孤儿双确认判定写实
- Regression evidence: 不适用（纯文档）

### REFACTOR — optional
- Cleanup: 无
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（纯文档任务，沿先例）
- Reason: 无可先行失败的测试
- User approval: 用户 2026-09-23 指令按 Harness 拆解并执行
- Alternative verification: 自检脚本 + 用户 review

## Implementation notes

- 解析必须在工作线程；附件注入上下文要有字符上限（保护 fork 快照体积）。
- 界面数值只引用 token 名。

## Verification report

- Verdict: PASS（用户 2026-09-23 08:05 回复「按照建议采纳」＝四项拍板点全部按建议采纳，规格翻 accepted）
- Evidence: 规格四节齐备且自检脚本 EXITCODE=0（主会话复跑）；拍板记录已写入规格 Doubts 节末尾（第一批 txt/md/PDF 文本层 / 不引 PDFBox 走自实现轻量抽取、真机不达标再单独拍板 / 附件上限 20MB 级常量+注入上限实现任务反推 / 附件行与引用行互斥成立）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 不适用（无代码断言）。
- Gaps: 注入字符上限具体数值由 T-097 按所选模型上下文预算反推后回填；PDF 抽取成功率待真机验证。
- Risks: 自实现 PDF 抽取对压缩流/字体编码覆盖有限，扫描版 PDF 会 [PART] 降级；用户若不满意需回到疑虑 2 拍板引库。

## Handoff notes

- Completed: 任务创建。
- In progress: 待开工。
- Blocker: 无。
- Unblock condition: 用户确认拆解。
- Next steps: 产出规格 → review → 驱动 096/097/098。
- Watch-outs: 依赖放宽必须用户明示批准，不得先引后报；附件入口在 097 落地前不得提前放伪入口。
