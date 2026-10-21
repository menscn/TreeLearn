# Filename: T-2026-10-20-source-of-truth-inventory-034.md
# Codex Harness Task: 把「同一事实只有一处权威」落成一张机器可读的清单和一支会响的守门（ROADMAP C3）

id: T-2026-10-20-034
title: 事实源全量清单、两类事实分开判的守门、24 例注入与本片自己造成的指针漂移
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 261
shared_code_touched: false
depends_on: [T-2026-10-19-guard-runner-one-command-roster-023.md, T-2026-10-19-evals-inventory-and-runner-028.md, T-2026-10-19-metrics-reporter-role-031.md, T-2026-10-19-spec-author-technical-section-032.md, T-2026-10-20-loop-watcher-role-033.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary：`T-2026-10-18-treelearn-process-migration-001` 确认把 `.codex/` 作为 Treelearn 唯一 AI 研发流程入口，
并按 ROADMAP 逐片补齐流程资产。本片是 Phase C 的 C3。

- Problem/background: 「同一个约束只保留一份权威正文」写在 `AGENTS.md` 与背景包里，但没有任何一条命令会在第二处定义出现时响。
  本片的原型扫描（`run259-scan.txt`，7 条被点名事实）实测到 `DEF=32` 处裸第二定义与 6 处写死在散文里的生成数——
  也就是说这句话在此之前只是主张。
- Users/scenarios: 下一位按 ROADMAP 动手的实现者／复核者，需要一条命令回答「这条事实的权威在哪、我这儿能不能再写一遍」。
- Expected outcome: 一条命令（`check_source_of_truth.py`）判三件事：每条事实有唯一权威路径、该路径在仓库里真实存在、
  同一事实出现第二处定义即 FAIL；并把散文里既有的重复表述收成指针。
- Core flow and reversibility: 只读守门 + 文档改写，全部可逐文件回退；不改业务代码、不改判据下限。
- Inputs/outputs: 输入是 `.codex/context/source-of-truth.md` 那张清单与 `SCAN_PREFIXES` 选出的当前态入口面；
  输出是 21 条判据行与末行读数 `scanned_files=67 entries=16 kinds={'声明型': 14, '生成型': 2} authority_files=15`。
- State and exception handling: 被检面缺席（清单被删）与条目被 per-entry 检查悄悄丢弃都各有判据（RED-1、`sot:entry-dropped`）；
  集合缩到下限以下 fail-closed。
- Constraints and trade-offs: 允许「同一行带归属标记」的引用而不判红，因为纠正性引用本身就是证据；代价是清单要靠锚点
  在被引那一行开火来防止标记滥用。生成型走另一侧：扫描面零命中 + 漂移脚本必须带裸 `MIN_` 下限。
- AI recommendation and rationale: 按 ROADMAP C3 原范围动手，并把范围扩到守门首跑判红的那批散文（否则交付完仍留一片已知红）。
- User choice and custom additions: 沿用用户在片 021–033 已确认的「一站到底、每片本地提交」方式，本片无新增用户裁定。
- AI-autonomous decisions: ① 两类事实分开判；② 锚点长度上限从 80 抬到 96（合取行级形状的必要代价，同时由 60 字抄检窗口兜住）；
  ③ 指针漂移用 difflib 映射机械重定位，不手改；④ 历史面（任务卡／evidence）不回填行号。
- User-confirmation decisions: 无待确认项——本片的范围、判据形态与验收方式都在 ROADMAP C3 与既有规则内。
- Acceptance evidence: ROADMAP C3 的 `验收方式` 一句，逐条落在 Verification report 与 evidence 块 00–07。
- Facts: 清单 16 条、权威落点 15 个文件、当前态入口面 67 个 `.md`、注入 24 例、全轮 268 例 / 14 基线（全部为命令读数）。
- Assumptions: 无。
- Unknowns: `.workbuddy/memory/` 里那批 23 处解析不出来的 `文件:行` 是否还需要留在那个位置（不承担权威规则职责，但也没人裁定删除）。
- Risks: 见 Verification report 的 Risks。
- Deferred: 把 difflib 指针映射收进 `harness_check_lib.py`；给自检用例总数设一支带裸下限的漂移脚本。
- Non-goals: 不改任何业务代码；不重写历史卡片里记录为当时读数的行号；不把清单做成第二份权威。
- Skipped non-critical questions and reason: 没问「清单要不要覆盖 `Docs/01-demand/**`」——`documentation.md` 已判历史面描述的是意图，
  逐行追指针只会逼人删证据，判据面按 `HISTORY_ROOTS` 排除即可。

## Goal

让 `python -X utf8 .codex/scripts/check_source_of_truth.py` 成为「这条事实有没有第二处权威」的唯一回答者：
清单里每条事实有且只有一个真实在场的权威落点，当前态入口面上任何一处不带归属标记的第二定义、
以及任何一处被写死进散文的生成数，都让这条命令以退出码 1 响，并且每种坏法都能被单条注入证伪。

## Scope

### In scope
- `.codex/context/source-of-truth.md`：16 条事实，逐条六字段（类型 / 唯一权威 / 更新时机 / 漂移检查 / 锚点 / 归属标记），
  字段名由文件里的 `FIELDS` 注释声明、由守门现场读取比对。
- `.codex/scripts/check_source_of_truth.py`：纯标准库守门，21 条判据、5 个裸下限（`MIN_SCAN_FILES=40`、`MIN_ENTRIES=12`、
  `MIN_GENERATED=2`、`MIN_UPDATE_WHEN_CHARS=8`、`MAX_ANCHOR_CHARS=96`）与 `OVERLAP_RUNS=60` 抄检窗口。
- `.codex/scripts/dev/checkers_selftest.py`：24 例 `sot:*` 注入 + 把 `CHECK_SOT` 升为第 14 条基线。
- `.codex/scripts/run_all_guards.py`：core 档注册（2 行）。
- `.codex/HARNESS.md` 第 8 步接线；`AGENTS.md`、`.codex/README.md`、`.codex/context/project-brief.md` 三处路由。
- 被守门首跑判红的存量散文：`.codex/rules/**`、`.codex/skills/**`、`Docs/spec/**`、`Docs/05-technical-design/**`、
  `Docs/04-ux-design/design-system/README.md`、`android/README.md` 里的重复表述收成指针或删除写死数。
- 本片造成的 `文件:行` 漂移复算（脚本面 32 处 + 文档面 4 处）与 `artifacts/metrics/report.md` 重生成。

### Out of scope
- 历史面（`.codex/tasks/`、`artifacts/evidence/`、`.codex/references/`、`Docs/01-demand/**`、`.workbuddy/`）的行号回填。
- 业务代码（`android/**`、`frontend/**`、`backend/**`）——本片零命中。
- 把 difflib 指针映射上收为常驻库函数（登记为欠账）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增一支只读守门与一次 core 档注册；被保留的旧行为是所有既有判据、下限、
  期望标签与档位语义一字未动——`run_all_guards.py` 只多一个条目，`--tier core` 的语义不变（末态 `pass=34`，
  即片 033 的 33 + 本片 1）。业务代码零命中由 `git -c core.pager=cat diff --numstat HEAD -- android frontend` 空输出证明。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 清单的消费者是 `check_source_of_truth.py`
  （解析六字段、复算覆盖面数）与 `checkers_selftest.py` 的 `sot:*` 族；`run_all_guards.py:98` 注册后由 core 档消费；
  `.codex/HARNESS.md:14` 的第 8 步把它接进流程。反向搜索被改写句子的读者：`check_roadmap_and_routing.py`
  读 `.codex/README.md` 的目录结构句（本片就把它判红过一次，见 RED 小节），`run_evals.py` 的 `pointers-alive`
  读 `.codex/evals/cases/001-impact-inventory/product.md:5`（与 `evals:pointer-stale` needle 成对搬动）。
- Native bridge or independent-client compatibility/update requirements: 不适用——未触碰 `android/**` 原生桥接、
  SQLite、资源或前端运行时；`android/README.md` 只改了两处指针措辞（`git diff --numstat` 显示 4 行变更、无源码变更）。
- Behavior-neutral non-applicability reason: 本片的产物是判据资产与文档，运行时行为面为空。

## Documentation impact record

- Changed runtime facts: 没有运行时事实变更；变更的是「哪些文字有权陈述一条事实」。
- Authoritative code/config/migration evidence: `.codex/scripts/check_source_of_truth.py`（判据本体）、
  `.codex/context/source-of-truth.md`（清单本体）、`.codex/scripts/run_all_guards.py:98`（core 档注册）。
- Documentation surfaces checked: 守门自己的扫描面（67 个 `.md`，即 `SCAN_PREFIXES` 命中的当前态入口面）+
  全仓形状扫描 `b034-cite-scan.py`（176 个 `.md`、403 处 `文件:行` 声称）。
- Documentation files updated: 36 个已跟踪文件（`git diff --stat HEAD` 末行 `36 files changed,
  188 insertions(+), 76 deletions(-)`，证据块 07 在装配当场重读同一份）+ 3 个新增未跟踪文件
  （清单、守门、本卡片）。
- Producer/consumer/sibling/compatibility paths checked: `AGENTS.md`（3 处：入口句、通用底线、路由表新增一行）、
  `.codex/README.md:32/34`、`.codex/context/project-brief.md:55`、`.codex/HARNESS.md:14`、
  `.codex/ROADMAP.md` C3 两节回写、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md:138`（把 `..._V3.0.md:70`
  这个省略写法补全为可解析指针）。
- Superseded phrases/examples reverse-searched: 被删／被收的写死数（设计 token 个数 6 处、档位条数、`22 例` 之外的那批）
  由守门的 `no generated count is written into prose` 判据现场复扫，`unattributed=0 hits=[]` 即「没有漏收」的读数；
  重复短语另跑 `b031-dup-scan.py`：`raw_hits=166 cjk_prose_hits=0 non_prose_hits=166`、`positive_control fired=True`、
  `DUP-SCAN problems=0`。
- `documentation impact: none` reason and evidence: 不适用——本片主体就是文档面。
- Historical/PRD handling: 任务卡与 evidence 里的行号是当时读数，不回填；`Docs/01-demand/**` 冻结基线保留原叙事，
  只在规格侧显式登记差异（该片原文已经这么写，本片只把它的指针写成机器可解析的形式）。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_source_of_truth.py` 退出码 0：末态 `run273-sot-checker.txt` 打
      21 条 `OK` 判据行、`check_source_of_truth: 0 check(s) failed`、`EXITCODE=0`。
- [x] 每条事实有唯一权威路径且该路径真实在场：`every authority path exists and the cited line is inside it` 转绿，
      读数 `authority_files=15`；RED-3 把 `AGENTS.md:34` 搬到 `:468`（越界）后该判据当场红，
      `dead=['任务状态唯一可写目录::AGENTS.md:468 beyond eof=67']`。
- [x] 同一事实出现第二处定义即 FAIL：RED-2（把 33 份文档面还原到 HEAD）打
      `FAIL no fact is defined a second time without an attribution pointer — scanned_files=65 unattributed=25`，
      单条注入 `sot:second-definition-bare` 也独立转红（`unattributed=1 hits=['Java-only 约束 <- .codex/HARNESS.md:29']`）。
- [x] 生成型事实被写死进散文即 FAIL，且它的漂移检查必须带裸下限：`sot:generated-count-in-prose` 与
      `sot:generated-floor-drained` 各判一边；RED-2 的读数是 `measured=6`。
- [x] 清单自己不豁免：覆盖面数字与现场复算比对（`sot:coverage-count-drift`）、权威指向清单本体即红、
      权威落在历史面即红、六字段重复即红。
- [x] 匹配集与条目数有下限，空集不许报绿：`MIN_SCAN_FILES=40` 由 `sot:scan-matcher-drained`（写错后缀模拟空集）证它会响；
      `sot:entry-dropped` 证「per-entry 检查吞掉条目」会被 `no entry was dropped` 抓到。
- [x] 注入族全部可用且整轮不劣化：单族 24 例 `SELFTEST cases=24 ok=24`（`run262`）；全轮
      `Mode: full (268 injected-violation cases + 14 baselines)` / `SELFTEST cases=268 ok=268 mismatch=0
      harness_errors=0 skipped=0` / `OVERALL: PASS`（`run275`）。
- [x] 档位面不回归：`--tier core` `pass=34 fail=0 inconclusive=0 fault=0`（`run278`）、
      `--tier precommit` `pass=6 fail=0 inconclusive=0 fault=0`（`run279`），两轮串行单独跑。
- [x] 本片自己造成的 `文件:行` 漂移全部重定位且不越改越错：脚本面 `EDITS applied=32`（`run268`）、
      文档面 `EDITS applied=4`（`run271`）、再跑一次幂等（`MOVED 声称=0 SAME 声称=12`，`run272`）。
- 2026-10-20 独立复核：以上九条按各自引用的 `run259–run285` 证据逐条核对，唯一权威/路径在场/第二处定义/生成型下限/清单自检/匹配集下限/注入族/档位面/指针重定位九项均成立；本轮 core 档现为 `pass=39 fail=0`（串行），判据与下限未改动。

## TDD execution record

### RED — failing test first
- Test/scenario: 三条各打一种坏法——被检产物缺席（RED-1 清单删除）、文档面回到去重之前（RED-2 把 33 份被本片改过的
  已跟踪文档还原为 `git show HEAD:` 内容）、权威行号搬到文件之外（RED-3 `AGENTS.md:34 → :468`）。
  另有一轮真实的首跑红：`.codex/README.md` 那句权威边界枚举被删掉之后，core 档基线
  `FAIL 目录结构 section lists every registered entry — missing=['Docs/intent/', 'artifacts/metrics/']`（`run261`）。
- Command: `python -X utf8 .codex/scripts/check_source_of_truth.py`
- Exit code: 1
- Observed failure: `run274-redgreen.txt`：RED-1 `FAIL inventory exists — path=.codex/context/source-of-truth.md`（1 条红）；
  RED-2 三条红（`unattributed=25`、生成数 `measured=6`、以及锚点那条被临时工作副本里的行号漂移连带命中：
  `bad=["同一事实只允许一处权威::anchor does not fire on the cited line .codex/context/project-brief.md:61 line='## 阅读顺序'"]`），
  `check_source_of_truth: 3 check(s) failed`；RED-3 三条红（`dead=['…AGENTS.md:468 beyond eof=67']`、
  `entries=16 usable=15 dropped=1`、覆盖面复算 `measured` 少 `.codex/context/` 一项）。单族注入另见 `run262`。
- Failure reason validated: 每条红都指回具体成因而不是泛泛失败：RED-1 是产物缺席、RED-2 是「裸第二定义 + 写死生成数」两类
  判据各自成立（读数 `('25', '6')` 与 GREEN 的 `0`/`[]` 成对）、RED-3 是行号越界并连带暴露「条目被丢」「覆盖面数对不上」
  两条独立判据。`run261` 那条红的成因是删正文删到了别的判据的载体上——处置是恢复一句登记事实（`.codex/README.md:32`），
  **没有**去动 `check_roadmap_and_routing.py` 的 `README_ENTRIES` 或任何下限。

### GREEN — minimum implementation
- Implementation: 清单 16 条 + 只读守门（两类事实分开判、合取行级锚点、覆盖面数现场复算、5 个裸下限）+
  24 例注入 + core 档注册 + HARNESS/AGENTS/README/背景包四处接线 + 存量散文收成指针。
- Command: `python -X utf8 .codex/scripts/check_source_of_truth.py`
- Exit code: 0
- Passing evidence: `run273-sot-checker.txt` 21 条 `OK`、`check_source_of_truth: 0 check(s) failed`、`EXITCODE=0`，
  末行 `scanned_files=67 entries=16 kinds={'声明型': 14, '生成型': 2} authority_files=15`。
  `run274` 的还原读数：RED-1/RED-2/RED-3 之后同一条命令都回到 `rc=0`，且 RED-2 还原后「与 GREEN 同读数=True」。
- Regression evidence: 全轮 `run275`（268 例 / 14 基线 `OVERALL: PASS`，`EXITCODE=0`）；档位 `run278` core
  `pass=34 fail=0 inconclusive=0 fault=0`、`run279` precommit `pass=6 fail=0`；重复短语 `run280` `DUP-SCAN problems=0`
  且正控会响（`positive_control fired=True`）；业务代码 `git diff --numstat HEAD -- android frontend` 空输出。

### REFACTOR — optional
- Cleanup: ① 指针复算驱动里那两处「把本片新写的声称也搬走」的缺陷不是靠注释声明，而是删掉了隐式假设、换成现场计算：
  新增 `added_lines()`（未跟踪整份文件＝全新；`difflib` 非 `equal` 区间的行＝新鲜）与 `SKIP-FRESH` 出口，
  把「HEAD→工作树映射」这一支的适用面从「所有声称」收窄到「HEAD 里就存在的声称」——被删掉的错误行为是
  「对新鲜声称也做重定位」，它实际造成过一次真红：`source-of-truth.md:51` 的 `project-brief.md:61` 被搬成 `:63`，
  守门随即打 `anchor does not fire on the cited line`。② 形状扫描器 `b034-cite-scan.py` 改了四处误报形状：
  删掉 `tok.lstrip("./")`（它会连 `.codex/` 的点一起吃掉，把每一处指向 `.codex/**` 的活指针误报成 MISSING-PATH），
  换成只剥成对 `./` 前缀 + 中文句子里前一个词粘连时的逐个重试；索引与文档两次遍历都改为跳过任何以 `build` 开头的目录
  （`build-chips/` 里的打包副本会让裸文件名出现多个候选，「唯一后缀匹配」判成歧义）；「区间里任意一行是空行」
  换成只判读者落到的那一行；逐字粘贴的 JVM 栈帧（`at …(FileTestStorage.java:45)`）单独归为 `stackframe`、
  不进缺陷计数。四处都是「修仪器」而不是「放宽判据」——判据面反而更严：当前态面必须零形状缺陷才打印 PASS。
- Regression evidence: 同一支扫描器改前改后各跑一次，两份都已落盘、都可复算。改前那支原样归档在
  `artifacts/tmp/b034-cite-scan-v1.py`，读数 `run284-cite-scan-v1.txt`：
  `scanned_md=177 citations=429 ok=236 missing_path=173 beyond_eof=4 blank_line=16` /
  `VERDICT: READ THE FLAGS ABOVE`——其中 `MISSING-PATH android/README.md:891 -> item_chat_message.xml:148` 与
  `MISSING-PATH .codex/context/source-of-truth.md:35 -> .codex/README.md:3` 都是**活指针被误报**的样本。改后
  （`run281-cite-scan.txt`）：`scanned_md=176 citations=403`、`cur citations=112 ok=111 stackframe=1
  missing_path=0 beyond_eof=0 landing_blank=0`、`VERDICT: PASS (current-state face has no shape defect)`，
  并把那两条误报各自还原成读数：`OK android/README.md:891 -> android/app/src/main/res/layout/item_chat_message.xml:148-148
  | android:text="@string/chat_failed"`。新鲜行守卫的改前改后：`run269` `REVIEW needed=1`（含被误搬的那条）→
  `run272` `SKIP-FRESH` 19 条、`MOVED 声称=0 SAME 声称=12`，即驱动已不可能再搬本片新写的声称。守门与档位复算：
  `run273` rc=0、`run278`/`run279` 全绿。

### LOOP — optional
- Command and rounds: 三段回路，都用同一条命令反复跑。
  ① 指针驱动 `python -X utf8 artifacts/tmp/b034-pointer-fix.py`（脚本面）：`run266`（探靶：11 个被引用文件 / 109 处站点）→
  `run267`（干跑：`SKIP×2`、`REVIEW needed=0`、`MOVED=12`）→ `run268`（`--apply`，`EDITS applied=32`、`VERDICT: PASS`）。
  文档面 `b034-pointer-fix2.py`：`run269`（`REVIEW needed=1`）→ `run270`（加 `HAND-VERIFIED` 之后
  `声称条目数=22 站点数=49 MOVED=3 SAME=19 REVIEW needed=0`）→ `run271`（`--apply`，`EDITS applied=4`）→
  `run272`（幂等复跑，`MOVED 声称=0`）。
  ② 全轮 `python -X utf8 .codex/scripts/dev/checkers_selftest.py`：`run261` `OVERALL: INCONCLUSIVE`
  （`BASELINE FAIL check_roadmap_and_routing rc=1`）→ `run263` `OVERALL: INCONCLUSIVE`（`BASELINE FAIL metrics_report rc=1`）→
  `run275` `cases=268 ok=268 / OVERALL: PASS`。指标面按设计只有一条处置：`metrics_report.py --write`
  （`run264`：`WROTE artifacts/metrics/report.md lines=20 metrics=12`），判据一字未动。
  ③ 档位 `python -X utf8 .codex/scripts/run_all_guards.py --tier core`：`run276` `pass=33 fail=1`
  （红在 `FAIL cdp_shot_selftest rc=1 120.3s` / `SELFTEST FAIL cases=17 failures=1`）→ `run277` 单独直跑同一条命令
  `SELFTEST PASS cases=17 failures=0` → `run278` 串行单独整跑 `pass=34 fail=0 inconclusive=0 fault=0`
  （同一支在本轮只花 72.9s）。随后 `run279` precommit `pass=6 fail=0`。
- Failure identity and repeat count: 身份 = 「命令 + 判据行 + 读数」。①里 `run269` 的 REVIEW 是「候选对不上内容」这一判据行，
  成因是 `.codex/README.md:32` 被就地改写而非搬走，属第一次出现；`run272` 的 `SKIP-FRESH` 则是本片新登记的**另一种**失败身份
  （驱动搬了本片新写的声称），也各只 1 次。②里两条 `OVERALL: INCONCLUSIVE` 的判据行不同
  （`目录结构 section lists every registered entry` vs `metrics_report rc=1`），单看本片各 1 次；
  但 `BASELINE FAIL metrics_report rc=1` 这个身份在片 033 的 `run227` 已经出现过一次，
  **跨片累计 2 次**、根因同一（产物改动后没重生成报告）。③的 `cdp_shot_selftest` 失败身份在本片出现 1 次。
- Stop condition hit: 命中 loop-watcher 的停止条件第 1 条一次，命中的正是那条跨片累计：「报告过期」已在两片各拦住一轮
  （`run227` 与 `run263`），本片的处置仍是 `--write`（唯一正确动作），但**上报而不是继续就地吸收**——本小节即上报面，
  建议下一片把「产物改动 → `metrics_report.py --write` → 复跑 `check_metrics_report`」写成 HARNESS 收尾步骤，
  让它不再靠整轮跑红来提醒。第 3 条未命中：本片没有下调任何判据或下限，也没有改期望标签；
  `run261` 那条红没有用「把 `README_ENTRIES` 里补两个目录」来消，而是恢复被删的正文。
  第 4 条未命中：不需要设备或整包仪器跑。①的 REVIEW/`SKIP-FRESH` 各 1 次，未达阈值 2。
- Handoff: 交给下一片的三件会漂的东西：① 给 `checkers_selftest.py` 或 `run_all_guards.py` 加行仍会挪动全仓 `文件:行` 声称，
  两片两份的 difflib 映射该上收进 `harness_check_lib.py`（片 033 REFACTOR 已登记，本片又复制了一份，欠账现在是两份）；
  ② HEAD→工作树的重定位只对「HEAD 里就存在的声称」成立，这条已经写进驱动而不是只写在卡片里；
  ③ 收尾两轮必须串行单独跑——`run276`→`run277`→`run278` 这三次读数就是这条规则的成本与收益。

### TDD or sizing exception — only with prior user approval
- Exception type: 不适用。
- Reason: `tdd_mode: required` 且 RED/GREEN 同命令齐备；261 行只越过软限 200，未触及 500 的硬线。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 两类事实分开判是判据形状的分水岭：声明型的失效是「另立权威」，所以允许同一行带归属标记的引用；
  生成型的失效是「抄了一份会变的数」，所以要求扫描面零命中，并要求它的漂移检查指向一支带裸 `MIN_` 下限常量的脚本
  （`sot:generated-floor-drained` 把下限抽干就红）。把两侧规则互换会同时制造假红和假绿。
- 合取锚点必须写成行级形状 `^(?=.*A)(?=.*B).*(?:C|D)`：第一版用裸 `(?=…)` 起头，`re.search` 的前视只看得见当前位置右侧，
  关键词在前的行被静默漏掉（实测漏 2 处，形状是「整表绿而漏判」，比红更难发现）。代价是锚点长度超过原来的 80，
  于是 `MAX_ANCHOR_CHARS` 抬到 96，同时由 `OVERLAP_RUNS=60` 的抄检窗口继续挡住「把权威正文抄进清单」。
- 清单自己不豁免：覆盖面表里的数字由守门现场复算逐根比对，复算不上就是手抄数；权威指向清单本体、落在历史面、
  六字段在同一条目里写两遍，都各自成一条判据。
- 删重复正文之前要先查有没有别的判据在数它：`.codex/README.md` 那句权威边界枚举同时是
  `check_roadmap_and_routing`「目录结构 section lists every registered entry」的载体，整句删掉会让 core 档基线当场转红
  （`run261`）。收下来的写法是保留目录事实、只把权威主张换成指针（`.codex/README.md:32`）。
- 锚点形状收紧过一次：原判据是「同一行同时出现两个阈值数字」，那会把 `android/README.md` 里逐片如实记录的越界证据
  也算成第二定义。收紧为「同时出现规定性措辞」之后，判据文本、下限与期望标签一字未动。
- 形状扫描器（`b034-cite-scan.py`）与守门各带一份 `SCAN_PREFIXES` 是刻意的：共用常量的话，改窄扫描面会把守门和量它的尺子
  一起挪空，就没人发现集合空了。
- `run_all_guards.py` 的注册只加 2 行代码，但会把指向该文件的声称整体挪位——这正是本片 32 + 4 处重定位的来源。

## Verification report

- Verdict: PASS（验收方式那一句由 `run273` 的 `EXITCODE=0` + 21 条判据行直接满足；唯一权威、路径在场、第二处定义即 FAIL
  三件事分别有 GREEN、RED-3、RED-2 与 24 例注入三种仪器指向；未覆盖的部分写在 Gaps，不算已覆盖）
- Evidence: `artifacts/evidence/T-2026-10-20-source-of-truth-inventory-034.txt`
  （driver=`artifacts/tmp/b034-evidence.py`，只做拼接与现场补跑：块 00 守门 21 条判据行逐字与一次当场重跑、
  块 01 的 `RED-1`…`RED-3` 与 GREEN 及工作副本对真树的逐字一致性（`worktree after: entries=38 identical=True`）
  加 RED-2 的可复算口径（`changed_md()` 从 `git diff --name-only HEAD` 现取 33 份文档面还原成 HEAD）、
  块 02 的单族 24 例 + 末态整轮 268 例 / 14 基线逐字 + 第一次整轮红在 `metrics_report` 基线的差量三元组、
  块 03 的两档整跑与 `validate_tasks` / `check_roadmap_and_routing` 当场重跑 + 争用那一轮的三份读数对照、
  块 04 的探靶（11 个被引文件 / 109 处站点）、`EDITS applied=32`、`REVIEW needed=1` 挡住不写的那轮原样读数、
  HAND 轮、`EDITS applied=4`、幂等复跑的 19 条 `SKIP-FRESH`，以及形状扫描改前改后两份、
  块 05 的原型扫描两轮（`run259` 的 7 条事实 `DEF=32 PTR=12` 与去重中途 `run260` 的 `DEF=19`）加
  `run261` 那条既有判据真红、块 06 指标面 `--write` 读数与当场只判复算、块 07 的尺寸、重复短语末态与卡片在场后各一次、
  `git status` / `git diff --stat`）。验收场景 = ROADMAP C3 的 `验收方式` 一句，逐条落在块 00–07。
  原始捕获在 gitignore 的 `artifacts/tmp/run*.txt`（号段 run259–run285）可重跑；REFACTOR 里那份「改前读数」靠
  `artifacts/tmp/b034-cite-scan-v1.py` 现场重算得到（`run284`），不是转述。
  末态三条是 `run275`（全轮）/ `run278`（core）/ `run279`（precommit），串行单独跑；`run276` 那轮 `fail=1` 是
  紧跟 `verify_1to1_selftest`（241.3s）之后的浏览器计时争用，`run277` 单独直跑同一条命令即 `failures=0`，
  本片没有为此抬超时或动那支守门。末态三条之后卡片与证据文件才写出，那一段没有再整跑，只重跑会读它们的
  `validate_tasks.py`、`metrics_report.py`（只判）与 `check_source_of_truth.py`（`run283-postcard.txt`）。
  卡片正文在那之后又改过四处（REFACTOR 的改前读数换成可复算的那份、Cleanup 第②项补上另外两个误报形状、
  号段与改动面数字、`check_metrics_report` 这个不存在的脚本名），这一段用 `run285-postcard-recheck.txt` 收口：
  `validate_tasks` / `metrics_report`（只判）/ `check_source_of_truth` / `check_roadmap_and_routing` /
  `check_context_pack` 与重复短语扫描六条全部 rc=0。拿旧整跑充数而不补这一步，正是片 033 那个角色要拦的动作。
  设备/模拟器面未触及（C3 不需要）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有判据、下限或期望标签。
  一处 needle 被改写：`evals:pointer-stale` 的引用串 `checkers_selftest.py:82-84` → `:87-89`——它与评测快照
  `.codex/evals/cases/001-impact-inventory/product.md:5` 是同一个事实的两个副本，必须一起走；
  `run_evals` 的 `pointers-alive` 只查路径在场、查不出行号，所以这一对只能由驱动保证（片 033 原样登记的欠账，本片继承）。
  另两处是**本片把仪器逼出来后顺手修的**：`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md:138` 的
  `..._V3.0.md:70` 省略写法补全为可解析路径（同一行内改写，不挪行号）；`b034-cite-scan.py` 的两处误报形状
  （`lstrip("./")` 吃掉 `.codex/` 前缀、区间跨空行即判缺陷）改成只判读者落到的那一行，并把 JVM 栈帧
  （`at androidx.test.platform.io.FileTestStorage.<init>(FileTestStorage.java:45)`）单独归为 `stackframe`
  而不是死指针——它是逐字粘贴的崩溃读数，判红等于逼下一位实现者改证据。
- Gaps: ① 指针复算、形状扫描与证据装配三支驱动都留在 gitignore 的 `artifacts/tmp/`，仓库内不常驻（D9 缺口已由片 035 的常驻 `check_pointer_liveness` 闭合；三支一次性驱动仍不可在别的克隆复跑，
  且 difflib 映射现在有两份待上收）；② 形状扫描只判形状不判语义，历史面残留 `missing_path=23 beyond_eof=3
  landing_blank=7`，本片按「历史面不回填」明确不管，但也没人裁定 `.workbuddy/` 该不该继续留在仓库里；
  ③ 散文里还剩一处手抄的生成数没进清单：`SPEC-2026-10-15-web-h5-android-parity.md:138` 的「22 例正反双向自检」是
  `check_stack_facts.py` 的自检例数，加行就会漂；收进清单需要先给自检例数设一支带裸下限的漂移脚本，属下一片；
  ④ 清单 16 条只覆盖已被点名的域——`frontend/README.md`、`backend/README.md` 在扫描面上但没有任何条目以它们为权威，
  「在册 ≠ 已登记」，覆盖面表按根统计时会显示这一点；⑤ `MIN_SCAN_FILES=40` 与当前 67 之间有余量，新增入口面文档不会
  触底，但把 `SCAN_PREFIXES` 改窄会——这条只能靠注入（`sot:scan-matcher-drained`）而不是靠 review 提醒。
- Risks: 下一片若给 `checkers_selftest.py` 或 `run_all_guards.py` 加行而不跑指针驱动，就会留下一批活指针——这是本仓库
  已登记第三次的缺陷类，不是本片新增的风险；`--tier core` 里的 `cdp_shot_selftest` 在并行负载下仍可能因 20 秒等待窗口
  超时（D12 类；D12 已由片 036 闭合、D14 由片 037 闭合，058 的并发假红不再作为常态），下一片若在整跑之外另起进程，仍应串行单跑。
- Independent verification（2026-10-20）：PASS。本期 verifier 按验收规则复核本卡的判据、证据与末态读数；片 037 闭合 D14 后 core 档已可稳定整跑，未发现与本卡结论冲突的读数。

## Handoff notes

- Completed: C3 的清单（16 条）、守门（21 条判据 / 5 个裸下限）、24 例注入与第 14 条基线、core 档注册、
  HARNESS 第 8 步与 `AGENTS.md` / `.codex/README.md` / 背景包三处路由、存量散文的指针化、本片造成的 36 处
  `文件:行` 重定位（32 脚本面 + 4 文档面）、指标报告重生成、证据汇编与形状扫描。
- In progress: 无（等独立 verifier 复核后置 `verified`）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 下一片按 ROADMAP 顺序取 C4（发布闸门），但它的前置条件是真实设备或模拟器可用——开工前先确认设备在场；
  若设备不在，就改做本片登记的欠账（把两份 difflib 映射上收进 `harness_check_lib.py`，并为自检例数补一支带裸下限的
  漂移脚本，顺带把 `22 例` 那句收进清单）。开工先跑一次 `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 建立基线。
- Watch-outs: ① 收尾两轮串行单独跑（D12），超时是「没判」，不是产物变差；② 改任何「会被当成现状读」的正文之前先查清单，
  已有权威正文的那条在这里只写指针或带归属标记的引用（HARNESS 第 8 步）；③ 删重复正文前先 `grep` 有没有别的判据在数它——
  `run261` 就是这么红的，恢复正文而不是改 `README_ENTRIES`；④ 报告过期这一失败身份已跨片累计 2 次，正确动作只有
  `metrics_report.py --write`，别去动 `check_metrics_report` 的判据；⑤ 新写进当前态面的 `文件:行` 声称对指针驱动必须是
  不可变的（`SKIP-FRESH`），别把手片自己刚写下的行号搬走。
