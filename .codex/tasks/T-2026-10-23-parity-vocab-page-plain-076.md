# Filename: T-2026-10-23-parity-vocab-page-plain-076.md
# Codex Harness Task: 073 的判据后账——1:1 词表里那条还在断言整页横格的检查

id: T-2026-10-23-076
title: 1:1 词表把 grid-period 换成 page-plain，断言 073 之后的现行制式
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 45
shared_code_touched: false
depends_on: [T-2026-10-23-background-rules-073.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 073 按用户判定把整页横格从三处宿主同批撤掉（原型 `.screen`、Web `glass.css` 的 `body`、Android `PaperRuleBackdrop`），但删的是**绘制**，漏删了一条**断言那条绘制**的判据：`.codex/scripts/dev/parity/verify_1to1.mjs` 词表里的 `grid-period` 仍要求 `.screen` 背景里有「周期 = `--s8`、墨色 = `--paper-rule` 的 `repeating-linear-gradient`」。它在健康页面上从此判红（实测 `background-image="none"`），`verify_1to1_selftest` 的正控断言当场抓住，整支 1:1 仪器的负向比对因此是空见证。
- Users/scenarios: 跑 1:1 parity 守门的人（`run_all_guards` core 档）与读对话页复刻结论的人。这片不改任何用户可见界面。
- Expected outcome: 正控重新成立、整支仪器恢复效力，且词表**仍然断言一件可证伪的事**——不是把这条删掉，而是把它换成 073 之后的现行制式。
- Core flow and reversibility: 改三支仪器文件（检查正文、名单、变异用例）。撤销 = 放回 HEAD 版本；本片自己就用这条 reversible 路径取了一次活体 RED。
- Inputs/outputs: 输入是 073 的设计事实与既有探针读数（`gridTexture` = `.screen` 的 `background-image`）；输出是一条三断言的新检查 + 四条各自隔离的注入用例。不新增文件、不新增依赖、不动生产代码。
- State and exception handling: 无运行时状态。量不到 `.screen` 背景（`typeof tex !== "string"`）判红并说明「属探针或页面结构变化，这条无从判」，与「页面确实没有横格」分成两种读数。
- Constraints and trade-offs: 用户 073 的原话是「所有界面的背景中的横线都去掉」，所以「背景有周期格线」不再是可主张的设计事实；而「判据先行、不改脚本凑数」要求这条改动**净断言数不减**。取舍：新检查从「量周期与墨色一致性」换成「横线不许回来 + token 不许消失」，条数 6→6，正控与负控都仍在。
- AI recommendation and rationale: 保留 `--paper-rule` 的断言而不是只删掉 repeating 检查——073 有意让 token 活着（唯一消费者是快照 `.snap-screen`），「页面没横线」与「token 被人删了」是两件事，判据要能分开这两件事。
- User-confirmation decisions: 承接 073 的拍板（2026-10-23「背景中的横线去掉吧，现在不好看」）；本片无新增用户决策点，属该拍板的判据后账。
- AI-autonomous decisions: ① 改名 `grid-period` → `page-plain` 而不是复用旧名（旧名的字面意思是「格线周期」，留着它会让读者以为判据仍要求格线存在）；② 依赖 token 从 `[--paper-rule, --s8, --lh-read]` 收到 `[--paper-rule]`（前两项不再由这条引用，二者另有消费者，未退役）；③ 随 5 条旧注入下线 `inkText()` 派生器，把「别只数停止点的一端」的教训移进新检查注释，并用「把 `--paper-rule` 追加到背景末尾」这条注入继续守着自己。
- Acceptance evidence: `node .codex/scripts/dev/parity/verify_1to1_selftest.mjs` rc 1→0；`SELFTEST PASS cases=39 cli=20 muts=17 failures=0`；正控 `6/6 项在真实读数上判绿`；四条注入逐条「判红且其它 5 项不受影响」。
- Facts: 名单只有一处正文（`.codex/scripts/dev/parity/screens.json` 的 chat `prototype.dom_checks`）；档案点名而词表不认的名字判仪器故障（3），这条分支由 `unknown-check.json` 用例覆盖，改名后仍绿；历史证据文件里的 `grid-period` 字样不改写。
- Assumptions: 无——被核对象（工作树里的原型 `.screen`）与判据（词表）都在同一棵树里，RED 由真实页面读数给出。
- Unknowns: 无。
- Risks: 「背景没横线」被后来者当成回归而去恢复格线——新检查的 ① 正是为这一刻准备的：任何写法把 repeating 画回 `.screen` 都会红。次要风险是把这片读成「放宽判据」。
- Deferred: 无。
- Non-goals: 不动生产代码与界面样式、不动 token 值、不改 `--s8`/`--lh-read` 的其它判据、不复算 `verify_1to1_selftest` 自己的 failures 聚合口径、不自动提交或推送。
- Skipped non-critical questions and reason: 未追问「要不要顺手给 `.snap-screen` 也加一条 DOM 检查」——快照屏不在 1:1 词表的被核面内，加它等于给仪器造一个新面，不属本片。

## Goal

让 1:1 仪器的正控重新成立，同时把那条判据换成 073 之后仍然可证伪的现行制式，词条数与负向覆盖不缩水。

## Scope

### In scope
- `verify_1to1.mjs`：`grid-period` 检查替换为 `page-plain`（三断言）与其 tokens 声明。
- `screens.json`：chat `dom_checks` 名单同步改名。
- `verify_1to1_selftest.mjs`：5 条 `grid-period` 注入换成 4 条各自隔离的 `page-plain` 注入；下线 `inkText()`。
- 设计与 parity 文档的当前态表述（设计系统 README 变更记录 + 073 条目补后账指针）。

### Out of scope
- 生产代码、任何界面样式、token 值与存在性、`--s8` / `--lh-read` 的其它判据、历史证据文件正文。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 判定面改变（不再要求背景有格线，改为要求背景没有格线且 token 仍在）；页面渲染零改变，其余 5 项检查逐字不变。
- Producer/consumer/sibling entrypoints and platform branches（代码证据）：词表正文在 `verify_1to1.mjs` 的 `DOM_CHECKS`，名单消费者是 `verify_1to1.mjs` 主流程与 `gen_parity_ledger.py`（台账按名单打印「6 项 DOM 检查」，条数不变）；变异用例正文在 `verify_1to1_selftest.mjs`。无平台分支（仪器只跑本机 node）。
- Native bridge or independent-client compatibility：无。
- Behavior-neutral non-applicability reason：不适用——本片确实改变了验收面（1:1 parity 的 DOM 判定项）。

## Documentation impact record

- Changed runtime facts: 无运行时事实变化；变化在**判据**：1:1 词表不再主张「原型背景有周期格线」。
- Authoritative code/config/migration evidence: `verify_1to1.mjs` 的 `DOM_CHECKS["page-plain"]`（含三条断言的正文与理由注释）与 `screens.json` 的 `dom_checks` 名单——判据唯一正文在词表，档案只点名。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`（`other` token 行 + 变更记录）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`（只泛引 `dom_checks`，未点词条名，无需改）、`Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`（2026-09-27 的历史逐项表）。
- Documentation files updated: 设计系统 README 新增 076 变更记录，并在 073 条目尾部补「后账」指针；076 条目里点名复刻方案 §逐项闭环那两行「纸感横格线 一致」属当时记录、不代表现行制式。
- Producer/consumer/sibling/compatibility paths checked: 全仓反向搜索 `grid-period`——命中 `screens.json`、`verify_1to1.mjs`、`verify_1to1_selftest.mjs` 三处活性正文（已改）与 `artifacts/evidence/` 下 009/051 的历史转录（不改写）。
- Superseded phrases/examples reverse-searched: 历史证据里的 `grid-period` 读数保留；只在当前态文档表述新制式。
- Historical/PRD handling: 历史卡片与历史转录保留。

## Acceptance criteria

- [x] `verify_1to1_selftest` 正控成立（6/6 在真实读数上判绿），整支仪器恢复效力。
- [x] 净断言数不减：词条 6→6，`page-plain` 自身可证伪的 clause 有 3 条，各有至少一条注入打红它。
- [x] 四条注入各自只打红本条（`其它 5 项不受影响`），含一条专测「只换写法不改墨色」的注入。
- [x] `--paper-rule` token 的存在性有判据守着（073 的「不退役」约定不再只写在文档里）。
- [x] 历史证据与文档中「当时的格线读数」未被改写。

## TDD execution record

### RED — failing test first
- Test/scenario: 1:1 词表自检的正控 + 两次真实运行读数（`pos-real-tree`、`pos-written-artifact-selfdescribes`）。
- Command: `node .codex/scripts/dev/parity/verify_1to1_selftest.mjs`
- Exit code: 1
- Observed failure: `FAIL grid-period 量不到纸感渐变（实测 background-image="none"）` ×2、`FAIL dom-checks = 5/6 成立（名单来自档案 body-bg, grid-period, …）` ×2、`FAIL positive-control 健康样本上这些检查本就判红: grid-period——正控不成立，负向比对因此是空见证`，汇总行 `SELFTEST FAIL cases=40 cli=20 muts=18 failures=4`。转录见 `artifacts/evidence/parity-vocab-page-plain-2026-10-23/076-red-selftest-full.txt`（另有 073 落地当时由 `run_all_guards` 打出的同一条红：`076-red-from-run-all-guards.txt`）。
- Failure reason validated: 红来自被核页面已经没有那条横格（073 删的），不是页面缺陷也不是仪器损坏——同一支探针在其它 5 项上照常判绿，且 `background-image="none"` 与 073 的 Web 运行时回读（`body` 剩 6 层、`repeating` 计数 0）互相印证。RED 是把 HEAD 的三支仪器文件放回工作树现场跑出来的，跑完逐一 cp 回备份，三个 md5 与 `artifacts/tmp/076-red-backup/` 逐位一致。汇总口径 `failures=4` 是脚本自己的聚合方式，逐行红点 5 处，本片不重算它。

### GREEN — minimum implementation
- Implementation: `page-plain` 三断言 + 名单改名 + 4 条隔离注入 + `inkText()` 下线。
- Command: `node .codex/scripts/dev/parity/verify_1to1_selftest.mjs`
- Exit code: 0
- Transcript: `artifacts/evidence/parity-vocab-page-plain-2026-10-23/076-green-selftest.txt` → `OK page-plain .screen 背景 0 层渐变、无 repeating、不含 --paper-rule 墨色（token 仍在 rgba(30,43,35,.018)，唯一消费者是快照 .snap-screen）` / `ok positive-control 6/6 项在真实读数上判绿` / `SELFTEST PASS cases=39 cli=20 muts=17 failures=0`。（与 RED 同一条命令、同一份工作树页面，差的只有三支仪器文件。）
- Passing evidence: 39 用例（20 CLI + 17 变异 + 正控）全过；档案点名未知词条仍判仪器故障（3）。
- Regression evidence: `gen_parity_ledger.py` 重跑 EXITCODE=0（ChatScreen 行仍为「6 项 DOM 检查」）；`parity_ledger_selftest` 20/20 PASS；`run_all_guards` core 档 **pass=38 fail=1**，唯一残留红是 `check_pointer_liveness`（`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 的锚点，与本片无关，见 Handoff）。本片之前该档是 pass=36 fail=3。

### REFACTOR — optional
- Cleanup: 顺带消解了 009 注释里悬置的那条「`--lh-read`=28px ≠ 周期 32px，与 tokens.json 注释不符，属设计侧待裁定」——整页格线不复存在后，没有任何断言再依赖那个等式。
- Regression evidence: `--lh-read` 仍由 `body-pitch` 消费（其注入「正文行高脱离 --lh-read」仍判红），未因本片变成无消费者 token。

### LOOP — optional
- 不适用。

## Implementation notes

- 改名而非复用旧名：`grid-period` 的字面意思是「格线周期」，保留它等于让下一个读者以为判据仍要求格线存在——那正是本片要避免的误读。
- 断言 ② 扫**全部** rgba 停止点而不是首/尾：浏览器把 `transparent` 序列化成 `rgba(0, 0, 0, 0)` 排在最前，只数一端会让落在中间的注入判绿。这条教训原属 `grid-period` 的注释与 `inkText()`，现在由注入用例「把 `--paper-rule` 追加到背景末尾」继续守着。
- 断言 ③（token 必须仍在）不是凑数：073 明确保留 `--paper-rule` 给快照 `.snap-screen`，若将来有人「顺手清理没用的 token」把它删了，页面照样判绿而快照会失去底色依据——这条把两种「没了」分开。

## Verification report

- Verdict: PASS
- Evidence: 仪器——`verify_1to1_selftest` 39 例 rc=0（RED 同命令 rc=1，转录与三支文件的还原 md5 都在证据目录）。台账——`gen_parity_ledger.py` rc=0、`parity_ledger_selftest` 20/20 rc=0。守门整表——改前 `073-before-run-all-guards-3red.txt`（pass=36 fail=3）与改后 `076-run-all-guards-final.txt`（pass=38 fail=1）两份原样转录都在同一证据目录。文档——设计系统 README 076 条目 + 073「后账」指针。生产面——本片零改动，故不重跑 JVM 与真机（075 的 946 例全绿与 `adb install -r` 装机件仍是当前构建的事实）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除的是 `grid-period` 整条（5 个 clause：有 repeating、周期可解析、单一墨色、墨色 = `--paper-rule`、周期 = `--s8`）——它们断言的正是 073 已撤销的行为，保留即永久红。等效覆盖：①「横线不许回来」+ ②「墨色不许留在背景」+ ③「token 不许消失」，加上原样保留的 `body-bg`（整页底色仍 = `--bg`）。被撤掉的「周期 = `--s8`」不再有任何生产者，Android 侧对应的 `space_s8` 画线常量与三个测试 seam 已在 073 清掉，所以这里不存在「判据还在但两端都量不到」的悬空。
- Gaps: ① 新检查是**负向断言**为主，它守不住「背景该有的质感还在」那一半——那一半由 073 的 `glass-depth.test.ts`（Web 侧点名 6 层）与 `GlassSurfaceLayoutTest`（Android 侧点名 mist + glow）守着，1:1 词表这次不重复主张；② `page-plain` 只看 `.screen`，没看快照 `.snap-screen`，所以 ③ 只保证 token 声明还在，不保证快照仍在用它画底；③ 未复跑 075 之后可能被这三支文件影响的其它面（不存在已知消费者，反向搜索只命中上述三处活性正文）；④ **core 档整表仍有一条红不在本片内**：`check_pointer_liveness` 报 `judged_blank=3`（persona 规格里三条引用落在空行上），本片未修，成因与处置见 Handoff。
- Risks: 把这片读成「为了让守门变绿而改脚本」。缓解：词条数不减、正控与 17 条注入全在、RED 用可逆路径重跑过、文档变更记录明确写出「这不是放宽判据」的理由。

## Handoff notes

- Completed: 词表改名与三断言实现、名单同步、注入电池重写、`inkText()` 下线、RED/GREEN 转录与还原校验归档、设计系统 README 两处更新、台账与守门复跑。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: ① 若将来给快照屏做 1:1 面，`--paper-rule` 的正向消费者断言应加在那条新检查里，而不是加回 `page-plain`；② **`check_pointer_liveness` 的三条假红要单独立片**——`Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 落在当前态判定面上，却有 76 条锚点（其中 72 条指向 `android/**`），而这些文件在 064–070 被整片重写。现在被点名的是三条空行（`DeepSeekRequest.java:42`、`TreeLearnSchema.java:874`、`TopicDao.java:42`——三条都因行号漂移落到空行，对象本身仍在位：`toString()` 在 :44、`FORWARD_MIGRATIONS` 在 :905、`TopicDao.update()` 在 :44）；同一条 bullet 里的「共 4 步」「`DATABASE_VERSION = 5`」「最新一步 V4→V5」是 2026-09-21 定稿时的 before 态，实测已是 5 步 / `DATABASE_VERSION = 6`（:67）。**只把三条空行挪绿等于制造更多看不见的错锚**（守门判形状不判语义），所以修复必须整表 76 条逐条核对，属 persona 线的文档一致性片，不由本片顺带做。
- Watch-outs: 谁把整页横格加回来都会先红这条；红的时候先读 073 与 076 两条变更记录再动手，别把它当渲染缺陷查。
