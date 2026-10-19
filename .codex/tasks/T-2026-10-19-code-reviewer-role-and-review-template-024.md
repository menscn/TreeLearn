# Codex Harness Task: 把「写完自检」与「独立复核代码」拆成两个角色（ROADMAP B1）

id: T-2026-10-19-024
title: code-reviewer 角色、REVIEW.md 骨架、归档目录与成对守门
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 92
shared_code_touched: false
depends_on: [T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，并把规则、任务、文档与流程资产归 `harness` lane。
  B1 是这条要求在「复核」环节的落点：写完之后的自检不能同时是「代码好不好」的结论来源。
- Problem/background: 此前仓库里只有一个复核角色 `verifier`，它判的是「这片任务验收了吗」（任务契约、路径、
  契约、测试、证据）。代码本身的可读性、边界与错误语义、测试是否真会转红、有没有第二份同形逻辑——
  这些没有一个入口，也没有一份可以归档的产物。把它们塞进 verifier 会让「验收结论」和「质量意见」混成一个 PASS。
- Users/scenarios: 使用者是写码的 AI 与用户本人。场景有两个：① 一片实现完成后、进验收前，让 code-reviewer
  读一遍 diff 并产出一份报告；② 用户点名「审一下这次改动」时，有一条明确的落点与格式，而不是在聊天里散着说。
- Expected outcome: 一个只读角色（`.codex/agents/code-reviewer.md` + 同名 `.toml`）、一份报告骨架
  （`Docs/REVIEW.md`）、一个归档目录（`Docs/reviews/`）、一支成对守门，以及守门的注入用例与 core 档登记。
- Core flow and reversibility: 新增角色/骨架/守门各一份，并在两处流程入口各加一行（`HARNESS.md` 第 6 步、
  `.codex/README.md` 权威边界）。删除这四个新路径、还原那两处各一行、并从 `run_all_guards.py` 的注册表里
  删掉一行，即完全回到本片之前——被新增的角色此刻没有任何生产者，所以没有反向依赖。
- Inputs/outputs: 输入 = 角色正文、报告骨架、`verifier.md` 现文（用来派生「要覆盖哪些小节」）、
  `.codex/tasks/_TEMPLATE.md` 的 Verification report（用来派生「哪些字段名不许重复」）；
  输出 = 14 行 `OK/FAIL` 判据 + `<name>: N check(s) failed` + `EXITCODE=0/1`。
- State and exception handling: 三处 fail-closed 早退：五份输入缺任一即停（写错路径也要报）；
  `verifier.md` 小节集低于裸下限时停（等于判据失去比对对象）；两份字段清单任一侧低于下限时停。
  `.toml` 解析失败单独一条 FAIL，不与「正文不相同」混为一条——那是两个病因。
- Constraints and trade-offs: 只用标准库（`tomllib`）；不引入「从 .md 生成 .toml」的生成器（dev/ 里没有，
  本片不新增构建步骤），代价是同一份正文有两个人类可读拷贝，收益是这条重复被一条判据钉住而不是靠自觉。
- AI recommendation and rationale: 「两个角色不互相冒充」最便宜的可机检形式不是查措辞，而是查**字段名集合不相交**——
  一旦报告里出现 `Gaps`/`Risks`/`Verdict`，读者就会把它当验收结论。所以两边都现场派生再比对，并各配一条裸下限。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 角色正文写成 `.md`（权威）+ `.toml`（运行时包装）两份，沿用本仓库既有约定
  （`verifier.md`/`verifier.toml`、`intent-capturer.md`…），没有脚本或 `HARNESS.md` 引用 `.toml` 之外的路径；
  ② 归档目录放一份 9 行 `README.md`（git 不存空目录，且角色唯一可写落点若不在场就是死指针），并把「目录在场」
  做成判据；③ `Docs/REVIEW.md` 只定字段与归档写法，不复述「怎么审」，避免与角色正文形成第二份权威；
  ④ 不动 `.codex/rules/communication.md` 的阶段→角色表（见 Deferred）。
- User-confirmation decisions: 无新增（沿用 001 已确认的流程范围与 B1 条目本身的验收方式）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-19-code-reviewer-role-and-review-template-024.txt` 块 01–06。
- Facts: `python -X utf8 .codex/scripts/check_code_reviewer_agent.py` → 14 条 `OK`、`0 check(s) failed`、EXITCODE=0；
  `python -X utf8 .codex/scripts/dev/checkers_selftest.py` → `Mode: full (64 injected-violation cases + 5 baselines)`、
  `cases=64 ok=64 mismatch=0 harness_errors=0`、`OVERALL: PASS`、EXITCODE=0，其中 `review:*` 14 例；
  `python -X utf8 .codex/scripts/run_all_guards.py --list` → `ROSTER entries=32 core=26 precommit=6`，
  新行判据为 `check(s) failed`。下限承重实测：把 `MIN_TASK_FIELDS` 由 4 改成 0（其余字节不动）后同一条自检命令
  `CASE 60 MISMATCH review:task-fields-drained - expected FAIL label, rc=0, seen=[]`、`ok=63 mismatch=1`、
  EXITCODE=1；按裸字节复原后 sha256-12 前后同为 `f663ca23506e`，再跑 `cases=64 ok=64`、EXITCODE=0。
  行数口径：`check_code_reviewer_agent.py` 177 行文件 / 144 非空 / 去掉模块 docstring 与纯注释 90 行（计入）；
  `run_all_guards.py` 注册表 +2 行（计入）；`checkers_selftest.py` 的 14 例与 `delete_dir` 操作属**测试**，
  `.codex/agents/code-reviewer.md`（90 行）、`Docs/REVIEW.md`（67 行）、`Docs/reviews/README.md`（9 行）属**文档**，
  `.toml` 是 `.md` 的逐字包装（生成物），按 `.codex/rules/harness-tasks.md:47` 均不计入 → 合计 92，低于 200 目标线。
- Assumptions: `verifier.md` 的小节名（`identity/source/contract/rules/职责清单`）继续作为角色形状的对齐目标；
  它若改名，本片第二条判据会转红而不是悄悄放行。
- Unknowns: 该角色在真实 diff 上跑起来的报告是否够用，本片没有任何一份真实报告可证（`Docs/reviews/` 现在只有 README）。
- Risks: `.md`/`.toml` 两份拷贝的同步靠一条判据；若将来加入生成器，这条判据应从「逐字相同」改为「由 .md 生成」。
- Deferred: `.codex/rules/communication.md` 的阶段→角色表（第 18–23 行）与第 54 行「角色决定结论的可信范围」
  尚未列 `code-reviewer`——那是在规则正文里新增一个阶段角色，属实质修订，要连带回归说明与沟通措辞；
  留给下一次动该规则片的时机，本片的入口登记靠 `HARNESS.md` 第 6 步。
- Non-goals: 不改 `verifier.md` 与验收规则正文；不替 code-reviewer 写第一份真实报告；不引入 CI、不新增依赖、不自动提交。
- Skipped non-critical questions and reason: 未追问「复核报告要不要也进 `run_all_guards`」——报告是散文产物、
  没有可判的判据行，硬塞进汇总面只会造一条永远绿的空判据。

## Goal

`code-reviewer` 作为只读的「代码本身」复核角色与 `verifier` 并存，报告骨架与归档位置定死，
并且这两份产物与角色正文的成对关系由一支有注入用例的守门判住（新角色不再只是散文）。

## Scope

### In scope
- `.codex/agents/code-reviewer.md`：角色正文（identity/source/contract/rules + 触发时机/职责清单/输出格式/交接/禁区），
  只写 `Docs/reviews/**`，四类 finding 各要 `文件:行`，阻塞/非阻塞二分，不宣布验收结论。
- `.codex/agents/code-reviewer.toml`：Codex 运行时包装，`developer_instructions` 与 `.md` 逐字相同（3957 字符）。
- `Docs/REVIEW.md`：报告骨架（12 个 `- **字段**：`、9 个小节、含归档位置与「处置」写法）。
- `Docs/reviews/README.md` + 目录在场：归档落点，git 不存空目录所以留一份 9 行说明。
- `.codex/scripts/check_code_reviewer_agent.py`：14 条判据的成对检查，`Checks` 纪律、退出码 0/1。
- `.codex/scripts/dev/checkers_selftest.py`：`review:*` 14 例注入用例、`CHECK_REVIEW` 进基线、新增 `delete_dir` 操作。
- `.codex/scripts/run_all_guards.py`：新守门登记进 core 档（判据行 `check(s) failed`）。
- `.codex/HARNESS.md` 第 6 步、`.codex/README.md` 权威边界行：让新角色在流程入口可被发现。
- ROADMAP B1 条目的范围/验收方式修正与交付回写；同处把 B3 的注册表条数改成「随片变化 + 现场取读数」。

### Out of scope
- `verifier`、任务契约与验收规则的正文；B4/B5/B2/B6 的其余角色；`Docs/spec/**` 的规格内容。
- `.codex/rules/communication.md` 的阶段→角色表（见 Deferred）。
- 用该角色真的复核一片代码并产出第一份报告（要有真实 diff 才有意义）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 无运行时产品行为变化（改的是流程角色与门禁面）。
  要保留的旧行为是「`verifier` 仍是唯一的任务验收入口」——本片只新增一个并列的、不宣布验收的复核角色。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者=`.codex/agents/code-reviewer.md`
  与 `Docs/REVIEW.md`（人/agent 读）；`.toml` 的生产者是 Codex 运行时加载面；守门
  `.codex/scripts/check_code_reviewer_agent.py:85-166` 的消费者是
  `.codex/scripts/run_all_guards.py:87-88`（core 档）与 `checkers_selftest.py` 的基线+用例；
  兄弟入口 `.codex/agents/verifier.md`（形状对齐对象，本片未改）与 `Docs/reviews/README.md`（只写归档规矩、不复述字段）。
  平台分支：无产品平台分支；仪器面 Windows 上 `os.path.isdir(repo_path("Docs"+os.sep+"reviews"))` 与
  `tomllib.load` 的二进制读取各一处（后者在非 UTF-8 代码页控制台下仍要 `-X utf8` 才能把中文 FAIL 详情打全）。
  根 `AGENTS.md` 路由表未新增触发词（按需规则第 62 行的 `.codex/agents/*.toml` glob 已覆盖新角色），
  因此 `check_roadmap_and_routing` 的当前态路由判据不受影响；该片实测见证据块 05。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片确实新增了一个会被 Harness 流程引用的角色
  与一条 core 档门禁。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；新增流程角色、报告骨架、归档目录与一条 core 档守门。
- Authoritative code/config/migration evidence: `.codex/scripts/check_code_reviewer_agent.py:1-21`（判据的权威说明写在
  docstring）、`:45-50`（三条裸下限 `MIN_VERIFIER_SECTIONS` / `MIN_REVIEW_FIELDS` / `MIN_TASK_FIELDS`）、
  `:79-166`（14 条判定与 6 处 fail-closed 早退；片 026 把小节名派生上收进 `harness_check_lib.py` 后本文件短了 6 行）；`.codex/agents/code-reviewer.md:19-41`（只读契约与 Java-only 属阻塞项）、
  `Docs/REVIEW.md:7-11`（归档位置）。登记面：`.codex/scripts/run_all_guards.py:87-88`（core 档新行仍是这两行；整表 `:81-168`，026/027/028/029 各在其后新增了 3/3/2/2 行）、
  `.codex/scripts/dev/checkers_selftest.py:44-71`（常量与基线元组，本片加的是第 5 条）、`:728-747`（目录型注入的
  `run_dir_case`，在 `:755-756` 分派）。行号是 2026-10-19 片 025、片 026、片 027、片 028、片 029 之后各重测一次：这五片都往同一个文件里
  各加了用例与常量，把这些区间整体下推；原引 `:44-51` / `:389-408`、再到 `:44-57` / `:486-505` 在被引用当时都是对的，
  到后一片末态已成活指针——这正是 D9 登记的同一类缺陷，发现即校正；`artifacts/tmp/b027-pointers.py` 把这三张卡的指针
  全部当成断言逐条复算（条数由该驱动现场打印），不再靠人记。
- Documentation surfaces checked: `.codex/ROADMAP.md`（B1/B3 条目）、`.codex/HARNESS.md`、`.codex/README.md`、
  `.codex/rules/communication.md`、`.codex/rules/harness-verification.md`、`.codex/rules/contracts.md`（owner→guard 路由表
  不按脚本名枚举，故不需登记）、`AGENTS.md`（第 62 行 glob 已覆盖）。
- Documentation files updated: `.codex/ROADMAP.md`（B1 范围/验收方式改写 + 交付任务 + 实现要点；B3 条数措辞）、
  `.codex/HARNESS.md`（第 6 步）、`.codex/README.md`（权威边界加 `Docs/reviews/**`）、
  `Docs/reviews/README.md`（新建）、`Docs/REVIEW.md`（新建）、`.codex/agents/code-reviewer.md|.toml`（新建）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact 的 code evidence 行；
  `grep -rn "check_spec_author_agent\|check_intent_capturer_agent\|run_all_guards" --include=*.md` 确认规则正文不按脚本名
  枚举守门，因此新守门只需进注册表与 ROADMAP，不需新增规则条目。
- Superseded phrases/examples reverse-searched: `grep -rn "agents/code-reviewer\.md" .codex Docs` → 13 行命中，其中 6 行来自本卡自身，
  其余 7 行是 `ROADMAP.md` 三行（B1 范围、B1 交付任务、B5 依赖）、`HARNESS.md` 第 6 步、守门常量、自检常量、`Docs/REVIEW.md`，
  全部指向已存在的文件；
  `grep -rnI "Docs/reviews/" .codex Docs` → 29 行命中（本卡 14 行，其余 15 行按文件分布为角色 `.md` 4 / `.toml` 2 /
  `Docs/REVIEW.md` 2 / 守门 2 / 自检 1 / ROADMAP 2 / `README.md` 1 / `HARNESS.md` 1）。两个串都不含被废弃的旧写法，因此这一节记的是「活指针清点」，
  不是被替换短语的反查；真正的替换是 ROADMAP B1 的验收方式措辞与 B3 那句过期条数（块 06 给原样读数）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_code_reviewer_agent.py` 退出码 0 且逐条打印 14 行 `OK`，末行
  `check_code_reviewer_agent: 0 check(s) failed`。证据 = 块 01。
- [x] 角色小节覆盖判的是 `verifier.md` **现文**：读数 `names=['identity', 'source', 'contract', 'rules', '职责清单']`；
  把角色正文的 `<rules>` 换成 `<scope>` 后该用例转红（`CASE 53 … missing=['rules']`）。证据 = 块 01、块 03。
- [x] 四个复核维度缺一即红，且注入必须覆盖全部出现处：`CASE 54` 用 `replace_all`（单点替换曾因正文两处出现而放行，
  见 Implementation notes 第 3 条与块 02）。证据 = 块 03。
- [x] 报告字段与任务卡 Verification report 字段不重叠，两侧各配裸下限并各有一条缩表用例：
  `CASE 59`（骨架 12→0，`measured=0 floor=6`）、`CASE 60`（任务卡 5→3，`measured=3 floor=4`）。证据 = 块 03。
- [x] 归档路径与归档目录在场各有用例：`CASE 55`（`Docs/reviews/`→`Docs/notes/`，dir=False）、
  `CASE 56`（删掉整个目录 → `the declared archive directory exists on disk` FAIL）。证据 = 块 03。
- [x] `.toml` 与 `.md` 的成对关系有两条用例：`CASE 63`（只改 `.md` → 逐字相同判定 FAIL）、
  `CASE 64`（喂进坏 TOML → 解析判定 FAIL，不与「不相同」混条）。证据 = 块 03。
- [x] 占位符判据落实执行提示词的要求：`CASE 62` 注入 `TODO` 后打出
  `FAIL role text and report skeleton carry no placeholder tokens — hits=['TODO']`。证据 = 块 03。
- [x] 裸下限承重被现场证伪一次：同一条命令 `python -X utf8 .codex/scripts/dev/checkers_selftest.py`，
  把 `MIN_TASK_FIELDS` 由 4 改 0 → `ok=63 mismatch=1` / `OVERALL: FAIL` / EXITCODE=1；按裸字节复原（sha256-12
  `f663ca23506e` 前后相同）→ `cases=64 ok=64` / EXITCODE=0。RED 与 GREEN 的 Command 逐字相同。证据 = 块 04。
- [x] 新守门进 core 档并整跑绿：`ROSTER entries=32 core=26 precommit=6`，`python -X utf8 .codex/scripts/run_all_guards.py --tier core`
  26 条全部判绿（含本片的 `check_code_reviewer_agent` 与更新后的 `checkers_selftest`）。证据 = 块 05。
- [x] `python -X utf8 .codex/scripts/validate_tasks.py` 对本片与父需求 errors=0。证据 = 块 05。

## TDD execution record

### RED — failing test first
- Test/scenario: 「两边一起缩表就能让重叠判定自动成立」是本片最容易被悄悄满足的判据。把
  `check_code_reviewer_agent.py` 的裸下限 `MIN_TASK_FIELDS = 4` 改成 `= 0`（其余字节不动），整跑文档类守门自检，
  看那条盯下限的注入用例是否还响。
- Command: `python -X utf8 .codex/scripts/dev/checkers_selftest.py`
- Exit code: 1
- Observed failure: `CASE 60 MISMATCH  review:task-fields-drained - expected FAIL label, rc=0, seen=[]`、
  `SELFTEST cases=64 ok=63 mismatch=1 harness_errors=0 skipped=0`、`OVERALL: FAIL`、`EXITCODE=1`。
  （另一次真实红灯更早：`review:dimension-dropped` 最初用单点替换，跑出
  `MISMATCH … seen=['FAIL agent .toml body equals the agent markdown verbatim — toml_chars=3957 md_chars=3957 equal=False']`，
  即子串判据被正文第二处同名词放行；见 REFACTOR。）
- Failure reason validated: 不是命令没跑起来——同一条命令在按裸字节复原文件后（sha256-12 前后同为 `f663ca23506e`）
  立刻 `cases=64 ok=64 mismatch=0` / EXITCODE=0；且 `seen=[]` 说明该轮守门对「任务卡字段被删到 3 个」一个字都没判，
  正是下限该挡的东西。驱动 `artifacts/tmp/b1-redgreen.py`（gitignore 内）用 raw bytes 存取，不用 `git checkout --`
  撤注入：本工作树 `core.autocrlf=true`，checkout 会改写行尾而使「字节相同」无从证明。

### GREEN — minimum implementation
- Implementation: `check_code_reviewer_agent.py` 的 14 条判定（五输入 → verifier 小节集与角色覆盖 → 四个维度 →
  归档路径/目录在场 → 阻塞二分 → `文件:行` → 两侧字段集与裸下限 → 重叠 → 占位符 → `.toml` 可解析与逐字相同），
  三处早退保证「比对对象不在场」不会被读成绿；配套 `review:*` 14 例进 `checkers_selftest.py` 并登记 core 档。
- Command: `python -X utf8 .codex/scripts/dev/checkers_selftest.py`
- Exit code: 0
- Passing evidence: `SELFTEST cases=64 ok=64 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS` / `EXITCODE=0`；
  守门本体 `python -X utf8 .codex/scripts/check_code_reviewer_agent.py` → 14 行 `OK`、
  `check_code_reviewer_agent: 0 check(s) failed`、`EXITCODE=0`。
- Regression evidence: `--tier core` 26 条全绿（块 05，含未改判据的其余 25 条）；
  `validate_tasks.py`（块 05）；`check_roadmap_and_routing.py` 对改写后的 ROADMAP B1/B3 条目仍判绿（块 05）。

### REFACTOR — optional
- Cleanup: 两处改判不是整理而是错因修正。① `review:dimension-dropped` 从 `replace` 改成 `replace_all`：
  「测试充分性」在角色正文出现两处（职责清单、输出格式的小节清单），单点替换后子串判据仍放行，
  用例形成为「改了一处却什么也没判住」——这条判据本就是子串式，注入面必须覆盖全部出现处。
  ② `TASK_FIELD_RE` 由要求冒号后必须有空白改为 `(?:\s|$)`：任务模板里 `- Gaps:` / `- Risks:` 是空着让人填的，
  旧写法只认到 2 个字段，会让重叠判定在两个方向上都失真；同时新增 `MIN_TASK_FIELDS` 裸下限，
  避免「模板只剩 1 个字段 ⇒ 两边不重名自动成立」。③ `checkers_selftest.py` 的目录型注入走新增的 `delete_dir`
  分支（`read_text` 对目录抛 `IsADirectoryError` 会被记成仪器故障），按 REPO_ROOT 现文原样还原。
  ④ 收口时 `--tier core` 报 `roadmap:prompt-dead-path` MISMATCH（`rc=0, seen=[]`）：本片给 B1 的 `范围` 行新增了
  `.codex/HARNESS.md`，该行在文件里早于所有 `执行提示词`，于是"只替换第一处"的注入落到了不被判的字段上，
  用例静默失去目标物。改成 `replace_all` 后注入落回 `执行提示词`（`measured_dead=[('B1', …), ('B3', …)]`），
  用例恢复承重。这不是新写的一条，而是同一课的第二遍：**被判字段之外的文本也会挪动注入的落点**，
  所以要么全替，要么把 old 串锚死在被判字段上——电池把这次漂移测了出来，说明它按设计在工作。
- Regression evidence: 与 GREEN 同一条命令（`cases=64 ok=64` 必须同时含那三条改用例形状的条目，另加④那条）。

## Implementation notes

- 小节覆盖不抄清单：`verifier_section_names()` 从 `verifier.md` 现文取尖括号块名与 `## ` 标题，
  因此 `verifier` 演化时这条判据跟着动；抄一份固定清单的话，两边一起改就谁也不响。
- 两个字段集合都现场派生且写法不同：骨架侧认 `- **字段名**：`，任务卡侧认 `- Label:`（只取
  `## Verification report` 小节体）。归一化比 `strip().lower()`，所以「Changed/deleted … coverage」这种长标签
  与中文粗体字段不会互相误判，而 `Gaps` 一旦出现在骨架里就立刻被 `CASE 61`（`field-collision-injected`）抓住。
- 裸下限写在与两份清单都无关的字面量上（`MIN_VERIFIER_SECTIONS = 4` / `MIN_REVIEW_FIELDS = 6` / `MIN_TASK_FIELDS = 4`），
  并由块 04 证明其中一条真的承重。
- `.md` 是权威、`.toml` 是包装：本仓库 `.codex/agents/` 下没有任何生成器，`.toml` 由 `json.dumps` 一次性转义内嵌正文。
  因此「逐字相同」判成 FAIL 就是有人在单侧改了角色正文——不消除重复，而是让重复不能长期不一致。
- 归档目录靠一条独立判据（`os.path.isdir`）而不是靠模板措辞：声明了落点却没这个目录 = 角色第一次写报告时才发现的
  运行时事故。git 不存空目录，所以 `Docs/reviews/README.md` 那份 9 行说明同时是「目录在场」的载体，
  它不复述 `Docs/REVIEW.md` 的任何字段，避免第二份权威。

## Verification report

- Verdict: PASS（守门本体 + 14 例注入 + 两次承重证伪（下限、注入算子）+ core 档整跑 26/26 + 全局门禁；
  角色未在真实 diff 上跑过，见 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-code-reviewer-role-and-review-template-024.txt` 块 01（守门 14 行逐字读数）、
  块 02（单点替换那次 MISMATCH 的原样输出）、块 03（注入落点漂移的 `replace` / `replace_all` 对照与 sha256-12）、
  块 04（64 例全量末态与基线 5 支，含 CASE 54 与 CASE 27 复算后的读数）、
  块 05（下限注入 RED / 复原 GREEN 的对照与 sha256-12）、块 06（`--list`、`--tier core` 全量逐条、`validate_tasks`、
  `check_roadmap_and_routing`）、块 07（新角色/骨架/目录的活指针清点）、块 08（本片没覆盖的东西）。环境：Windows 10.0.26200 / x64 /
  Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。捕获文件留在 gitignore 的 `artifacts/tmp/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片没有删除或改宽任何既有判据。
  对既有文件的改动只有：`checkers_selftest.py` 加常量/基线条目/`delete_dir` 分支/14 例用例，并把 `roadmap:prompt-dead-path`
  的注入算子由 `replace` 改成 `replace_all`（这是把一条已静默失效的用例接回负载，不是放宽判据）；
  `run_all_guards.py` 加一条注册行；`.codex/HARNESS.md` 加第 6 步；`.codex/README.md` 权威边界加一个短语；
  `ROADMAP.md` 的 B1 范围与验收方式改写（判据内容写全、命令补 `-X utf8`）、B1 实现要点回写这条注入漂移，
  以及 B3 那句过期条数改成现场取读数。
  逐处见 diff。B1 验收方式**没有**保留原句的「复核报告含模板与归档路径」这种含糊措辞，而是列成可判的六件事——
  原句不指定判据，任何子集都能自称满足。
- Gaps: ① 角色从未在真实 diff 上产出过报告：本片只证明「产物成对且判据会响」，不证明「按这份骨架真能审出一片代码」；
  `Docs/reviews/` 目前只有 README。② 未做独立 verifier 复核轮次（用户要求一站到底；本片属 harness 仪器面，
  每条判据都有对应注入用例可现场复算）。③ 两块一次性对照（块 03 的算子漂移、块 05 的下限承重）都靠 gitignore 内的驱动，
  别的克隆跑不出那两块；长期复算靠已入库的 64 例自检本身——算子被改回 `replace` 由 CASE 27 抓住，
  下限被改坏由 CASE 60 抓住，两处失效都不需要驱动就能在任何克隆上响。④ `.codex/rules/communication.md` 的阶段→角色表仍不含 `code-reviewer`
  （Deferred，理由见 brief）；新角色的可发现性目前靠 `HARNESS.md` 第 6 步与 `AGENTS.md` 的 agents glob。
  ⑤ `.toml` 是否真被 Codex 运行时加载，本片没有运行时证据——只判了它与 `.md` 逐字相同。
- Risks: 判据「小节名覆盖 verifier.md」把两个角色的形状绑在一起：`verifier.md` 改名或删节会让本片判据转红（方向是响，
  不是漏），但也会让人误以为是 B1 坏了；子串式判据（四个维度、`文件:行`、归档串）对同义改写不设防，
  这一类只能靠注入用例覆盖出现处来兜。

## Handoff notes

- Completed: code-reviewer 角色（`.md` + `.toml`）、`Docs/REVIEW.md` 骨架、`Docs/reviews/` 归档目录、
  14 条判据的成对守门、14 例 `review:*` 注入用例并进 core 档、两处流程入口登记、ROADMAP B1 回写与 B3 条数措辞修正。
- In progress: 无（本片已收口）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 按 ROADMAP 顺序取 B4（`codebase-researcher`）——B0 的技术规格节在等它；本片留下的「角色未跑过真实 diff」
  缺口不需要单独排片，下一片真正的代码改动就该用这个角色出一份 `Docs/reviews/REVIEW-*.md`，那便是它的第一个生产者。
- Watch-outs: 不要为了「让新角色更像验收」而把 `Verdict/Evidence/Gaps/Risks` 加进 `Docs/REVIEW.md`——
  `review:field-collision-injected` 会当场判红，那不是仪器过敏，而是两个角色又开始互相冒充的信号。
  改 `verifier.md` 的小节名之前先看这条判据（块 01 的 `names=` 读数），两边要一起动。
