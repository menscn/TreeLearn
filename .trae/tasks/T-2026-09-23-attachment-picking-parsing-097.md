# Treelearn Harness Task: 附件选择、解析与上下文注入

id: T-2026-09-23-097
title: 附件选择、解析与上下文注入
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 200
shared_code_touched: false
depends_on: [T-2026-09-23-attachment-storage-file-manager-096.md, T-2026-09-23-ai-gateway-provider-abstraction-085.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务打通输入栏附件键：SAF 选择 → 复制入私有目录 → 解析为文本 → 注入当次上下文 → 随消息持久化引用。
- Problem/background: SPEC-082 定了格式范围（第一批 txt/md/PDF 文本层，Docx/图像按拍板）与依赖约束（PDFBox 需用户批准才引入）。
- Users/scenarios: 学习者从输入栏选一个 PDF，发送后 AI 基于其内容回答。
- Expected outcome: 输入栏附件键（真实入口）、SAF 流、解析（工作线程）、注入上限、[PART] 降级、单测。
- Core flow and reversibility: 选文件→复制→解析成功则入草稿附件行（可移除）→ 发送时注入；解析失败保留文件+提示，可移除附件后重试发送。
- Inputs/outputs: 输入 096 存储层、085 网关（上下文构造）、SPEC-082；输出完整链路与测试。
- State and exception handling: [PERM] SAF 取消无副作用；[PART] 解析失败不阻断发送（移除附件即可）；超大文件拦截。
- Constraints and trade-offs: 无存储权限（SAF）；解析在后台线程；注入字符上限（保护 fork 快照）。
- AI recommendation and rationale: 附件行 UI 沿用 T-076 引用行模式（quote row 的兄弟形态），复用状态结构。
- User choice and custom additions: 无。
- AI-autonomous decisions: 附件行与引用行互斥（一次一种上下文附加）——列入规格符合性自查。
- User-confirmation decisions: 依赖放宽（PDFBox）已在 082 拍板，未批准则 PDF 走受限方案或降批。
- Acceptance evidence: RED/GREEN + T-076 输入栏回归全绿。
- Facts: `screen_chat_body.xml` 附件扩展入口约定（T-076 watch-outs：不得伪入口）。
- Assumptions: 096/085 verified。
- Unknowns: PDF 文本层抽取质量差异（真机项）。
- Risks: 注入超预算撑爆上下文；解析阻塞主线程。
- Deferred: 图像 OCR、多附件批量解析。
- Non-goals: 不做文件管理页（096）；不做云盘。
- Skipped non-critical questions and reason: 附件数量单次上限按 SPEC-082。

## Goal

输入栏可选附件、解析入上下文、失败降级不阻断发送，输入栏既有行为不回退。

## Scope

### In scope

- 附件键 + 附件行、SAF 流、解析器（按 082 拍板范围）、注入上限、降级、单测。

### Out of scope

- 存储与管理页（096）；孤儿清理（098）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: T-076 输入栏验收面（三态/草稿/引用）全部保持；无附件能力设备上不显示伪入口。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `ChatPresenter`、`ChatDraftStore`、`screen_chat_body.xml`、096 存储层、上下文构造链。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 输入栏支持附件。
- Authoritative code/config/migration evidence: 输入栏与解析代码。
- Documentation surfaces checked: SPEC-082、T-076 文档。
- Documentation files updated: PRD 输入能力当前态说明。
- Producer/consumer/sibling/compatibility paths checked: fork 快照构造（附件内容进快照）。
- Superseded phrases/examples reverse-searched: 「附件延后」表述更新。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：选择→登记→解析→注入断言；解析失败/超大降级断言。
- [x] 附件内容随消息持久化引用；fork 快照含附件内容（上限内）。
- [x] T-076 输入栏全部回归测试保持全绿；Java-only guard + `assembleDebug` 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `AttachmentParseInjectInstrumentedTest`（新增，真机）——①txt 登记后 `finalizeParse` 落 `parsed` 且 `parseOutcome` 缓存文本；②手工构造的最小 PDF 文本层被抽取进缓存；③垃圾字节落 `parse_failed` 且无文本；④`link` 后 `SqliteChatStore.history` 重建的 USER_MESSAGE 轮含问题正文 + `ATTACH_OPEN` 框 + 附件文本；⑤无附件消息重建逐字如旧。另有 JVM 侧 `AttachmentTextExtractorTest`（txt/md/PDF 未压缩/Flate 压缩/垃圾拒绝/不支持 mime 拒绝/20MB 源上限/注入上限拒绝不截断）。RED 阶段 `AttachmentTextExtractor`/`finalizeParse`/`parseOutcome`/`ATTACH_OPEN` 尚不存在，预期编译期失败。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.AttachmentParseInjectInstrumentedTest`
- Exit code: 1
- Observed failure: 编译失败 42 处「找不到符号」，首条为 `AttachmentParseInjectInstrumentedTest.java:13`（`AttachmentTextExtractor` 不存在），全部指向本片新契约。
- Failure reason validated: 无一条错误指向既有行为；失败符号均为本片待实现类型（解析器、解析落库端口、附件框常量）。

### GREEN — minimum implementation
- Implementation: ① `app/files/AttachmentTextExtractor.java`（新，199 行）：mime 白名单（text/plain、text/markdown、application/pdf）；PDF 走最小文本层提取——`stream…endstream` 截取、`/FlateDecode` 用 `java.util.zip.Inflater` 一次性解压、括号串与 `Tj/TJ` 文本操作符拼接、转义序列还原；20MB 源上限与 `INJECTABLE_CHARS=20000` 注入上限（超限返回 null，不截断）；垃圾字节/不支持 mime → null。零新依赖（未引入 PDFBox，符合 082 拍板 B 方案）。② `AttachmentDao.finalizeParse/parseOutcome`（解析态与文本回写/读取）；`AttachmentStore.finalizeParse/parseOutcome/PARSE_STATE_PARSED/PARSE_STATE_FAILED`（端口，内部跑 extractor，异常吞为 parse_failed）。③ `DeepSeekContextEncoder`：`ATTACH_OPEN/CLOSE` 标签、`escape` 表补两标签、`currentInput(question, quote, attachmentText)` 重载——附件框在引用框与问题正文之前。④ `SqliteChatStore.history`：重建 USER_MESSAGE 轮时按 `linked attachment ids` 读记录、文本可注入才拼附件块（缺失/不可注入静默降级为纯问题——重建不依赖文件存活）。⑤ 发送链：`ChatCommands.send` 加 `attachmentId` 参数；`ChatWiring.send` 读记录文本、超限/缺失 → `ATTACHMENT_TOO_LARGE` 拒绝；`ChatPresenter.onAttachmentPicked/onAttachmentCleared`（后台线程登记+解析、chip 三态 PARSING/READY/FAILED、与引用互斥（拍板 4：带附件发送时引用让位——`onSend` 携带附件即清引用）；READY 才随发送、PARSING 直接忽略）；`ChatScreen` 附件行渲染（`chat_attach_row`，解析中/失败/就绪三态文案）+ 布局 `screen_chat_body.xml` + `strings.xml` 六键 + `MainActivity` SAF 选择器（`ACTION_GET_CONTENT`，txt/md/pdf，读满字节交 presenter）+ `AppContainer`/`ChatWiring` 装配。⑥ 既有测试适配：`ChatWiringTest` 等四处 `send` 调用补 `null` attachmentId；`ChatViewState` 构造器加 `composerAttachment` 并逐调用点透传。
- Command: `./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.AttachmentParseInjectInstrumentedTest`
- Exit code: 0
- Passing evidence: 真机 ALP-AN00 5/5（`Starting 5 tests … Finished 5 tests`，T-097-green-2026-09-25.log）；JVM 侧 `AttachmentTextExtractorTest` 8 例随全量单测通过。汇总证据 `artifacts/evidence/T-097-attachment-parsing.txt`（CMD-1 至 CMD-10，Python 实时执行、UTF-8）。
- Regression evidence: 全量 JVM 单测 623/0/0（基线 615 + 本片 8）；`check` EXITCODE=0；Java-only PASS；真机回归 54/54（BottomTabShell+LibraryArchive+Navigation+TreeCanvas+AttachmentStorage+SqliteSchema，T-097-regression-instrumented-2026-09-25.log）；`git diff` 证明 `DATABASE_VERSION` 零改动（CMD-10，本片零迁移）。

### REFACTOR — optional
- Cleanup: 无（chip 渲染与互斥判定一次成型，未产生重复判定点）
- Regression evidence: 同上

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 若 082 拍板引入 PDFBox，须先在任务文件记录用户批准原文再动 build.gradle（无新依赖规则）。
- 实现者禁读 RikkaHub 源码。
- 与 092/095 同触输入栏区域，串行执行。

## Verification report

- Verdict: PASS
- Evidence: `artifacts/evidence/T-097-attachment-parsing.txt`（CMD-1 至 CMD-10）——RED 编译失败 42 错 EXITCODE=1 与 GREEN 同命令真机 5/5 EXITCODE=0；全量单测 623/0/0；check 0；Java-only PASS；回归真机 54/54；`DATABASE_VERSION` 零改动证明。文档联动：`android/README.md` 聊天小节新增「附件文本进请求（T-097）」条目（发送编码路径与重建降级语义各成一句）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片未删除既有断言；发送链 `send` 签名扩展使四处既有调用补 `null`（判定语义零改动——无附件即原行为）；`ChatViewState` 构造器加参与全部调用点透传（等价行为，全量单测证明）。新增断言覆盖：解析三态落库、PDF Flate 流提取、垃圾拒绝、注入上限拒绝不截断、history 重建含/不含附件块两态。
- Gaps: PDF 复杂排版（表格/多栏/CID 字体）超出最小提取器能力，能提多少提多少、提不出即 FAILED——观感由人 review；附件块是否计入出生快照哈希口径未在 AC 里点名，本片按「随 user_content 落库、随重建重放」实现，与 fork 快照构造（文档影响记录已核）一致。
- Risks: (1) 提取器对畸形 PDF 的鲁棒性——已用垃圾字节与压缩流损坏两条负例收口，异常路径一律落 FAILED 不崩溃；(2) 20000 字符上限是本片拍板的实现值（≈1/3 容量预算），规格未字面写死——已在 README 与本文件记录依据，改值只动一个常量；(3) 引用与附件互斥的「让位」方向（附件清引用）是拍板 4 的字面执行，若用户后续要求反向优先需回滚 presenter 一处判定。

## Handoff notes

- Completed: 解析器/端口/编码框/重建拼接/发送链/UI/SAF 全链落地并 verified；零新依赖；零迁移。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 附件链收口片 T-098（引用计数/删除链路/文件管理页引用数联动）——096 的「双确认判定」与 097 的引用落库是它的直接前提。
- Watch-outs: (1) `send` 签名已含 `attachmentId`，后续改发送链必须同时过四处既有测试调用；(2) 附件块写死在 `user_content` 里——**不要**改成引用表实时拼接，否则 098 删记录会让旧对话的重建结果漂移（有意降级 vs 无意漂移的分界）；(3) 与 092/095 同触输入栏，仍须串行。
