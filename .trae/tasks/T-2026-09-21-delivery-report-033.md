# Treelearn Harness Task: 交付报告与侧载说明

id: T-2026-09-21-033
title: 交付报告与侧载说明
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-09-20-treelearn-v1-android-local-first-001.md
requirements_confirmed: true
generated_by: leader
owner: docs-writer
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-09-20-security-release-core-hardening-018.md, T-2026-09-21-real-data-path-wiring-032.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Parent summary: 把知芽 V1 的实际交付状态写成一份可复核的交付报告与侧载说明，包括产物、证据、未闭合项与风险。
- Problem/background: 上一轮评审的结论是「不能作为可用 APK 交付」。本轮完成后必须有一份把「能证明什么、不能证明什么」逐条分开的报告，而不是让读者从任务列表里猜。
- Users/scenarios: 交付负责人与用户在个人设备侧载 APK 前，需要知道产物在哪、如何校验、哪些行为尚未取得设备证据。
- Expected outcome: `Docs/06-delivery/TreeLearn_V1_交付报告.md`，含产物摘要、可复现命令、未闭合项清单与后续步骤。
- Core flow and reversibility: 报告只描述已发生的运行事实；无可逆操作。
- Inputs/outputs: 输入各任务的证据文件与构建产物；输出一份 Markdown 报告。
- State and exception handling: 设备、Key 或环境不可用的条目必须写成 blocked/deferred，不得从测试数量或构建成功推断通过。
- Constraints and trade-offs: 不复制权威正文（口令、system prompt、正文一律不写）；数字必须可回溯到证据文件的行。
- AI recommendation and rationale: 报告按「已证明 / 未证明 / 待用户裁定」三段组织，避免把 PARTIAL 读成 PASS。
- User choice and custom additions: 用户 2026-09-21 指令：本轮不使用 emulator。
- AI-autonomous decisions: 报告不写 keystore 口令，只写路径与证书/APK 摘要。
- User-confirmation decisions: 规模越线例外由用户裁定，报告只列事实与比例。
- Acceptance evidence: 报告自身 + 其所引用的证据文件与命令。
- Facts: 生产代码、构建产物与实际执行输出是唯一权威。
- Assumptions: T-018 与 T-032 已进入 verifying。
- Unknowns: 真实设备上的安装启动与 SQL 行级行为。
- Risks: 把「构建成功」写成「可用」；把 deferred 写成 PASS。
- Deferred: 商店发布、远程遥测、平板横屏安装验证。
- Non-goals: 不新增功能、不修改生产代码。
- Skipped non-critical questions and reason: 报告语言与位置按仓库既有 Docs 约定，无需再问。

## Goal

产出一份与证据同源、把已证明与未证明分开陈述的交付报告与侧载说明。

## Scope

### In scope
- 新建 `Docs/06-delivery/TreeLearn_V1_交付报告.md`：交付物清单（release APK 路径、APK SHA-256、签名证书指纹、`:app`/`:core` 模块产物）、构建/测试/守门命令与退出码、侧载安装命令、本轮未使用 emulator 的声明、受设备与 Key 阻塞的判据清单、规模越线事实与待裁定项、未接线入口（若有）、已知风险与后续步骤。
- 同步 `Docs/06-delivery/` 的目录说明（若仓库约定需要）。

### Out of scope
- 修改生产代码、任务文件结论、`android/README.md` 的模块索引（由对应实现任务负责）。
- 商店发布材料与隐私政策正文。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只新增文档；不改变任何运行行为。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 无生产代码改动；读者是侧载 APK 的交付负责人。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 纯文档任务，不触及运行时路径。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 无新增运行事实；只记录已完成任务的既有事实。
- Authoritative code/config/migration evidence: `artifacts/evidence/**` 中各任务的证据文件、`android/app/build/outputs/apk/**`、`apksigner verify` 输出。
- Documentation surfaces checked: `Docs/06-delivery/`、`README.md`、`android/README.md`。
- Documentation files updated: 新建交付报告本身。
- Producer/consumer/sibling/compatibility paths checked: 报告引用的每条命令都在本轮实际执行过。
- Superseded phrases/examples reverse-searched: 旧版交付结论、`NOT_WIRED` 相关描述。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 历史交付结论保留，不作当前契约。

## Acceptance criteria

- [x] `Docs/06-delivery/TreeLearn_V1_交付报告.md` 存在且为 UTF-8；含 release APK 路径、APK SHA-256、签名证书 SHA-256 与 `apksigner verify --print-certs` 的结论。（verifier #3：UTF-8 无 BOM、43057 字节，`app-release.apk` 4 处 / `SHA-256` 10 处 / `apksigner` 6 处，见 `T-033-verify3-2026-09-24.txt`。）
- [x] 报告中每个数字都能回溯到「哪条命令 + 哪个证据文件的哪一行」，分项之和等于总数；证据文件自带命令与 `EXITCODE=`。（2026-09-22 复核脚本 `review_apk_and_gaps.ps1` 等可复跑；verifier #3 抽核通过；发现的唯一例外——T-033 交接注记把 T-REV 的 FAIL 误写为 PASS——已就地勘误。）
- [x] 报告显式区分「已取得证据」与「未取得证据」：本轮未使用 emulator（用户 2026-09-21 指令）导致安装启动、设备端 SQLite 行级行为、平板横屏验证、真实 DeepSeek 冒烟（无 Key）逐条列为 deferred/blocked，**不得出现任何把这些写成 PASS 的措辞**。（verifier #3 抽查：`deferred` 11 处均为 deferred 语义。）
- [x] 报告不包含 keystore 口令、API Key、system prompt、提示词正文、用户问题、回答或 reasoning 正文。（verifier #3 扫描：唯一「keystore 口令」命中是第 11 节「本报告不包含」的规则声明本身；密钥形态扫描全 0。）
- [x] 规模越线事实按任务逐条列出实测值与超出比例，并标注「待用户裁定」，不自我批准。（〔2026-09-24 更新〕裁定已完成：第 7 节增补裁定块，逐条记录豁免/拆分去向；原「待用户裁定」要求在裁定前已满足，裁定后状态如实更新。）

## TDD execution record

### RED — failing test first
- Test/scenario: Not applicable to a documentation task
- Command: Not applicable
- Exit code: Not applicable
- Observed failure: Not applicable
- Failure reason validated: Not applicable

### GREEN — minimum implementation
- Implementation: Not applicable
- Command: `python .trae/scripts/validate_tasks.py`
- Exit code: 0
- Passing evidence: 交付报告与所引用证据文件存在；`validate_tasks.py` 输出 `errors=0`
- Regression evidence: 本任务不修改生产代码，无行为回归面

### REFACTOR — optional
- Cleanup: 报告结构调整
- Regression evidence: 重新执行文档一致性核对

### TDD or sizing exception — only with prior user approval
- Exception type: TDD 不适用（文档任务）
- Reason: 交付报告是叙述性产物，没有可先行失败的测试；其正确性由「数字可回溯」与 T-019 的独立复核保证
- User approval: 用户 2026-09-20 明确要求按 Harness 交付知芽 V1（含交付报告），2026-09-21 追加「本轮不用 emulator，其余任务照常完成」
- Alternative verification: T-019 独立逐条核对报告中的命令与数字

## Implementation notes

- 报告只写已执行的命令与已产生的文件；没有证据的条目写成 deferred/blocked 并注明原因。
- 不要复制 `android/README.md` 或技术方案的正文段落，只给路径与结论。
- 交付物摘要至少包含：release APK 路径与 SHA-256、证书指纹、构建命令、`:app:testDebugUnitTest` 与 `.\gradlew.bat check` 的退出码、Java-only guard 结论、`validate_tasks.py` 的 errors 计数。

## Verification report

- Verdict: PENDING（待独立复核；本轮无可用 verifier lane）
- Evidence: PENDING
- Changed/deleted assertion rationale and equivalent behavior coverage: PENDING
- Gaps: PENDING
- Risks: 报告措辞若把未验证项写得像已验证，会直接误导侧载决策。

## Handoff notes

- Completed: 未开始。
- In progress: 无。
- Blocker: 需要 T-018 与 T-032 的产物与证据落地后才有最终数字。
- Unblock condition: T-018/T-032 进入 verifying 且 release APK 产出。
- Next steps: 写报告后交 T-019 复核。
- Watch-outs: 不得用「构建成功」代替「安装并可用」；deferred 项必须留在报告里。
- [leader 2026-09-21] 交付报告由主会话直接撰写：`Docs/06-delivery/TreeLearn_V1_交付报告.md`（UTF-8，无 BOM，12019 字节）。报告中的每个数字都取自本轮实际执行的命令输出，并在正文中给出命令与证据文件；没有证据的条目一律标为 `deferred` / `blocked`。
- [leader 2026-09-22] **报告第二次刷新**（装机核验前的事实更正）：① 第 2 节指纹改为当前 release APK 的实测值（452348 字节 / SHA-256 `994C70259577DBFE7DD3D68ABEA1B9CFB369D666AA06D9B9010242434C4EA592`，mtime `2026-09-22 01:30:15`，dex 内含 `TreeCanvasWiring` 与 `SqliteTreeCanvasStore`，即已含 T-038 的装配）；② 新增第 2.1 节「这份 APK 装机后能用与不能用」，按 dex 符号与静态引用关系逐条把「可达 / 不可达」分开；③ 新增第 5.1 节「引用失效：被引用的句子从未进入请求」——本轮新发现的缺陷，横跨 `core/domain/ContextPlan`、`ai/prompt` 与 `app/wiring/ChatWiring` 三处；④ 第 9 节把已不成立的「功能未接线」更正为历史记录；⑤ 第 10 节按当前剩余任务重写。
- [leader 2026-09-22] 本次刷新新增三份可复跑的核对脚本（纯 ASCII 或 UTF-8+BOM，均通过 `parse_check.ps1`）：`.trae/scripts/dev/review_apk_and_gaps.ps1`（APK 身份 + 死入口 + 引用路径，退出码 0 = 每条 CHECK 通过，3 = 前置缺失因此证据不完整）、`review_dex_symbols.py`、`review_refs.py`、`review_quote_path.py`、`.trae/scripts/dev/doc_state.ps1`。证据：`artifacts/evidence/T-REV-2026-09-22-apk-and-gap-review.txt`、`artifacts/evidence/T-033-doc-refresh-2026-09-22-round2.txt`（`DOC-VERDICT: PASS`，退出码 0）。本任务仍**没有独立 verifier 复核**，状态保持 `verifying`。
- [verifier #3 2026-09-24，勘误与收口] 上一条交接注记把 T-REV 证据误写为「`REVIEW-VERDICT: PASS`，退出码 0」——**实测该文件为 `REVIEW-VERDICT: FAIL (1 check(s) failed)`**（`artifacts/evidence/T-REV-2026-09-22-apk-and-gap-review.txt:217`），唯一失败项是 `CHECK apk-newer-than-newest-source: FAIL`（apk 02:58:02 旧于 source 03:13:05，2026-09-22 当时的并发施工中间态，与 T-040 G3 同源）。本勘误按「每个数字都要能指到哪条命令哪一行」的规则就地更正：该 FAIL 是**产物新鲜度**检查，不是报告内容缺陷；它同时提醒**装机前必须重建 release APK**。本任务其余复核由 verifier #3 于 2026-09-24 完成（`artifacts/evidence/T-033-verify3-2026-09-24.txt`），见下方收尾复核段。
- [leader 2026-09-21] **独立性缺口**：本轮 subagent 后端返回 `402 Payment Required`，verifier lane 与全部实现 lane 同时中断，因此本任务**没有独立 verifier 复核**，状态只能到 `verifying`，不得判 PASS。`harness-verification.md` 明确要求 verifier 独立于实现者，主会话自跑不能替代。

## Verification report — 收尾复核（verifier #3，2026-09-24）

- Verdict: PASS。五条验收标准逐条核过（勾选与证据见上），报告的「已证明 / 未证明」分界成立。
- 本轮同步内容：① 第 7 节增补〔2026-09-24 裁定块〕（用户当日裁定全部越线豁免 + T-039 拆分去向）；② 第 9 节风险与第 10 节步骤 1 同步；③ T-040 行检索范围措辞更正（无「问题」臂）；④ T-041 规模数字与任务证据同源（544）。以上均已在对应任务文件的收尾复核中记录。
- 勘误：T-033 交接注记曾把 `T-REV-…-apk-and-gap-review.txt` 的 `REVIEW-VERDICT: FAIL (1)` 误写为 PASS，已就地更正（见上方日期化注记）——失败项是产物新鲜度（APK 旧于源码），装机前必须重建 release APK。
- 残留：报告的 APK 指纹是 2026-09-22 的产物快照，当前树在此之后有大量改动；最终刷新属 T-018（安全与 release 签名复核）/ T-019（最终验收）的职责，不在本任务。T-033 的交付物（报告与侧载说明的框架与事实分界）已成立。
