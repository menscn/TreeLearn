# Codex Harness Task: 兑现 AI 人设规格里那栏 `DEFERRED:待 B4`（ROADMAP B0 后半）

id: T-2026-10-19-032
title: 补全 SPEC-2026-09-21-ai-persona-preference 的技术规格节
template_version: 2
task_type: docs
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: spec-author
owner_lane: docs
stack: docs
estimated_production_lines: 0
shared_code_touched: false
depends_on: [T-2026-10-19-codebase-researcher-impact-role-026.md]
tdd_mode: approved-exception

## Requirement collaboration brief

- Problem/background: B0 的第一份规格（AI 内置人设）按用户 2026-09-21 的口径只出了需求规格、界面方案、疑虑点
  三节，`Technical spec` 一栏写 `DEFERRED:待 B4`。B4（codebase-researcher）已由片 026 落地，这栏就成了 B0
  唯一还欠着的东西。本片把它兑现：接口 / 数据结构 / 状态迁移 / 错误语义四栏都写，且每条「现状」必须能在
  生产代码里被一条命令复算——本仓库登记的 D9 缺陷类正是「加行把行号引用变成读起来和真话一样的活指针」。
- Users/scenarios: 读者是 review 这份规格的人（用户本人）与之后接这张需求的实现片。四个场景：① 想知道
  「人设该存在哪儿」→ 数据结构栏给列、默认值与迁移步数；② 想知道「会不会打破既有生成链路」→ 状态迁移栏
  逐条对上重试、定稿与整行写回；③ 想知道「哪几件还没定」→ 疑虑点栏两条待拍板；④ 想验我有没有编行号 →
  口径说明那三段写了每条数字出自哪条命令。
- Expected outcome: `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 由 62 行长到 110 行（`git diff --stat HEAD` 末态 9 个已跟踪文件 /
  80 insertions(+) / 31 deletions(-)（规格 + 五张卡片 + ROADMAP + 指标报告），本卡另为新增文件、382 行）；技术规格节的结构由覆盖面判据现场数出
  （`structure: section_lines=53 subsections=4 bullets=33 bullets_with_cite=26`，`run197-coverage.txt`），节内 `CITATION_RE` 认得的引用 `full_citations=82`（`run188-pointerfix.txt`），疑虑点节新增 2 条待拍板。
- Core flow and reversibility: 先建锚点驱动（93 条唯一命中：代码侧 80 条 + 规格自身结构 13 条）与计数驱动（9 项现场计数）→ 由驱动打印行号写正文
  → 用展开驱动把简写/续写引用换成全路径 → 复算守门与全库活引用 → 回写 ROADMAP B0 → 跟随被 B0 加行推歪的
  指针（五张卡片 12 处：规格引用 9 条 + ROADMAP 区间 3 条；另 `b031-pointers.py` 表 7 条）。回退方式：删技术规格节内容、还原 `Doubts` 那条
  「无未决疑虑」句与 ROADMAP B0 三处，即回到片 036 末态；五张卡片的指针跟随与 `b031-pointers.py` 表更新
  **不属**该回退面（它们是把已被本片推歪的行号改回现值）。
- Inputs/outputs: 输入 = `android/{core,ai,data,app}` 的提示词身份链与主题/回答记录读写面、
  `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`、`.codex/templates/SPEC.md`、
  `.codex/agents/spec-author.toml`；输出 = 五支驱动的读数（`measured=93 stale=0`、`table floor: facts=93 floor=80`、`COUNTS-B0 problems=0`、
  `POINTER-FIX problems=0`、`SIBLING-REFS problems=0`、`COVERAGE-B0 problems=0`）与规格正文。
- State and exception handling: 规格里的「未知档位处置」有两种互相排斥的走法（抛 vs 回落），仓库现成做法是抛、
  界面方案要回落；本片没有自行拍板，写成疑虑点 1。同理「切换人设算不算一次主题活动」写成疑虑点 2。
- Constraints and trade-offs: 轻流程（`.codex/ROADMAP.md:33`）只要求一条守门退出码 0，不要求证据文件与注入
  电池；本片仍自己加了判据面，因为「写进规格的每条都要能追到代码位置」是 spec-author 的角色纪律，不是脚本
  要求。代价：正文引用全是完整相对路径，读起来啰嗦。
- AI recommendation and rationale: 技术规格的价值不在篇幅，而在于它是否钉住三个不显然的边界——人设**不进**
  版本命名空间（与思考附加段同构，`DeepSeekPromptRegistry.java:57`/`:61`/`:82` 三处推出）、
  `answer_revisions.persona` **不可省**（版本前缀反查得出用途、反查不出人设，
  `TreeLearnSchema.java:145`）、切换人设**不能复用整行写回**（`TopicDao.java:42`/`:230` 会重刷 `updated_at`，
  首页按 `:189`/`:219` 倒序重排）。这三条都不是任何文档里写着的，是从代码推出来的。
- User choice and custom additions: 用户要求一站到底、按片本地提交、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 「现状 / 规格」在版式上分开并各写口径说明，避免读者把待 review 的口径当成事实；
  ② 引用一律展开成完整相对路径（`short_after=0 cont_after=0`），因为简写与续写不被 `CITATION_RE` 计入，
  等于没有判据；③ 展开驱动的续写继承从「跨 bullet」收紧为「同 bullet + 行号 ≤ 目标文件行数」，过不了就
  REFUSE 并退出码 1，不靠人回读；④ 计数按匹配次数不按行数（`len(re.findall())`），迁移步数由此从假数 2
  变回 4；⑤ basename 唯一性索引只建在 `android/**`，排除反编译副本（否则 198 个重名让「重名即拒绝」
  退化成永远拒绝）；⑥ 两处口径冲突上报为疑虑点而不是选一条走完。
- User-confirmation decisions: 疑虑点 1（未知档位抛 vs 回落）与疑虑点 2（切换人设是否刷新主题活动）由用户本人
  拍板；本片只写出两种走法与各自代价。
- Acceptance evidence: `run154-specguard.txt`（B0 验收命令逐字 + `EXITCODE=0`）、`run172-pointers.txt`
  （93 条锚点末态，取代 `run150`/`run164` 的 75 条）、`run151-counts.txt`（9 项计数末态）、`run147-pointer-fix-apply.txt`（展开落盘与
  sha12 回读）、`run203-dupscan.txt`（重复短语扫描，正控在场）、`run203-citations.txt`（卡片在场后的全库活引用）、
  `run157-roadmap.txt`、`run159-b031pointers.txt`（跟随 B0 加行的指针复算）、`run197-coverage.txt`
  （覆盖面判据末态：72 条引用零脱锚 + 规格结构四项计数）、`run186-tiers.txt` 与 `run204-tiers.txt`
  （并发轮的超时与串行末态轮，成对留下，因为「超时＝没判」这件事只有两条读数放在一起才说得清）。
- Facts: `spec_lines=110`、`spec_technical_section_line=46`、`prompt_purpose_values=8`、`topics_columns=4`、
  `answer_revisions_columns=9`、`generation_operations_columns=14`、`topic_final_fields=4`、
  `database_version=5 next_version=6`、`forward_migration_steps=4`、`alter_table_add_column_uses=1`、
  `persona_identifiers_in_production_code=2`（两行 `comment=True`）、`persona_decision_line_in_intent=137`、
  `measured=93 stale=0`、`full_citations before=42 after=81`（末态 82）、`short_before=5 cont_before=34 → 0/0`、
  `EDITS applied=39 refused=0`、`written … readback_identical=True`、
  `SIBLING-REFS spec_cites=9 floor=8 spec_mentions=5 roadmap_cites=3 floor=3 problems=0`、
  `COVERAGE spec_cites=72 floor=60 anchor_lines=93 floor=80 unbacked=0 section_lines=53/45 subsections=4/4 bullets=33/25 bullets_with_cite=26/20 / COVERAGE-B0 problems=0`（`run197`）、
  `CITATIONS files_with_citations=19 citations=196 problems=0`、
  `DUP-SCAN scanned_files=142 scanned_lines=19146 raw_hits=166 cjk_prose_hits=0 正控 fired=True`、
  `check_spec_author_agent: 0 check(s) failed / EXITCODE=0`、`check_roadmap_and_routing: 0 check(s) failed`、
  `POINTERS table floor: measured=214 floor=200 / VERDICT: PASS` 均为一次真实执行的打印。
- Assumptions: `answer_revisions` 与 `generation_operations` 的列数以 `TreeLearnSchema.java` 的建表块现场计数
  为准；若实现片改了块边界，`b032-counts.py` 的 `ddl_block` 读数会先变（它打印块行范围），不会安静跟随。
- Unknowns: ROADMAP B0 的 `交付任务` 引用的 `T-2026-09-21-035` / `T-2026-09-21-036` 两张卡在
  `.codex/tasks/` 里**不存在**（该目录现有 27 个文件，最老的是 2026-10-18）——那是迁入本仓库之前的旧编号。
  本片不改那行历史 ID（改了会把「谁交付了什么」变成猜谜），只在此登记为待清理项。
- Risks: 规格里 82 条完整路径引用绑着生产代码的行号，任何给 `TreeLearnSchema.java` / `TopicDao.java` /
  `PromptPurpose.java` 加行的实现片都会把它们推歪，而 B0 的轻流程守门**不查**行号是否还活着；接手人须先跑
  `python -X utf8 artifacts/tmp/b032-pointers.py`（本片把 93 条锚点与它们各自的期望命中次数留在了这支驱动里）。
- Deferred: ① 疑虑点 1 / 2 等用户拍板，拍板后技术规格相应栏要改写；② `.codex/agents/spec-author.toml`
  没有同名 `.md`，与 B1/B2/B5/B6 的成对纪律不一致——属角色面，另开一片；
  ③ `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md` 里的行号（如 `TreeLearnSchema.java` 第
  117/119/333 行、`SettingsRepository.java` 第 31/65/69 行）疑似已过期，本片只写规格、不回写 intent；
  ④ 展开驱动 `b032-pointer-fix.py` 留在 gitignore 的暂存目录，没有升成正式守门。
- Non-goals: 不改任何生产代码、SQL、迁移或界面；不宣布规格被接受（人 review 才升格，`status` 仍 `draft`）；
  不替用户拍板；不新增依赖。
- Skipped non-critical questions and reason: 没问「技术规格要不要按 035/036 的口径拆两片」——B0 欠的就是一栏，
  拆开只会让 `DEFERRED` 的兑现时刻更难追；也没问「要不要给规格引用做一条 core 档守门」，那属 B3 的注册面，
  且本片只有规格一处受益。

## Goal

让 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 四类内容真正齐全：`Technical spec` 一节按接口 /
数据结构 / 状态迁移 / 错误语义四栏写出，其中每条「现状」带一个由锚点驱动现场量出的 `路径:行`（能被
`harness_check_lib.py` 的 `CITATION_RE` 计成引用），每条计数由计数驱动现场数出，新撞出的两处口径冲突写进
`Doubts` 交人拍板；`python -X utf8 .codex/scripts/check_spec_author_agent.py <该规格>` 退出码 0。

## Scope

### In scope
- `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`：`Technical spec（技术规格）` 一节整节重写 +
  `Doubts（疑虑点）` 加 2 条未决 + 口径说明加「引用一律写全路径」那段。
- `.codex/ROADMAP.md` B0 的 `交付任务` / 新增 `实现要点（交付后回写…）` 一条 / 末尾那条 `DEFERRED` 注意段。
- 片 026/027/028/029/031 五张卡片里被本片推歪的 `文件:行`（按 `artifacts/tmp/b032-sibling-refs.py` 现数：
  带行号的规格引用 9 条 + ROADMAP 区间引用 3 条 + 指向该规格但不带行号的提及 5 处 + 1 处说明句）。
- `artifacts/tmp/b032-pointers.py`（263 行、93 条唯一命中锚点，含本片末尾补的 13 条规格自身结构锚点与 5 条覆盖检查补的锚点）、`b032-counts.py`（143 行、9 项计数带断言）、
  `b032-pointer-fix.py`（188 行、引用展开 + skeleton 自检）、`b032-sibling-refs.py`（111 行、
  姊妹卡片引用现数 + 被声称那一行的锚点复算 + 两个裸下限）、`b032-coverage.py`（85 行、
  「规格里每条完整路径引用都被锚点现场量过」这条覆盖面判据，带两个裸下限）与
  `artifacts/tmp/b031-pointers.py` 表的 7 条跟随（都在 gitignore 的暂存目录，不进版本库）。

### Out of scope
- `android/**`、`frontend/**`、SQL、迁移与界面：本片零改动。
- `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`（spec-author 的只读边界）与
  `.codex/agents/spec-author.toml` 的成对 `.md` 缺失（角色面）。
- 把引用行号复算升成 core 档守门（属 B3 注册面）。
- 疑虑点 1 / 2 的决定权（用户本人）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = 规格从「三节 + 一栏 DEFERRED」变成「四节齐全且每条现状
  可复算」。保留 = 规格的需求规格节与界面方案节原文一字未动（`git diff` 里那两节 0 命中，改动起点是 `:46` 的
  `## Technical spec`）、`status: draft` 不变、intent 文件不动、`.codex/templates/SPEC.md` 不动、
  `check_spec_author_agent.py` 的判据不动（轻流程是用户定的，收紧判据属另开一片）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 本片的规格正文与五支
  暂存驱动；消费者 = `.codex/scripts/check_spec_author_agent.py`（四件事）、`artifacts/tmp/b028-citations.py`
  （全库活引用）、`artifacts/tmp/b031-dup-scan.py`（重复短语）、`artifacts/tmp/b031-pointers.py`（214 行表）、
  `.codex/scripts/check_roadmap_and_routing.py`（Phase B 关键词）、`.codex/scripts/metrics_report.py`
  （卡片计数与守门清单）。本片给 ROADMAP 加的 `实现要点` 一行会推歪所有引用 ROADMAP 行号的句子——那正是
  `b031-pointers.py` 报出 6 条 STALE 的原因，已按现值跟随（`run159` `measured=214 / VERDICT: PASS`）。
  平台分支 = 不适用：无 Android/前端代码、无 API、无 SQL；Java-only guard 与多端边界不在本片判定面内。
- Native bridge or independent-client compatibility/update requirements: 不适用（`git diff --stat HEAD` 末态
  8 个文件全部落在 `.codex/` 与 `Docs/`，`android/**` 与 `frontend/**` 零命中）。
- Behavior-neutral non-applicability reason, if applicable: 规格文本不是运行面；它改变的是「实现片接手时能不能
  复算每条边界」，而代码现状未动。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；改的是一份 Stage 2 规格的可交付完整度与 B0 的交付状态。
- Authoritative code/config/migration evidence: 规格侧
  `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:46`（节起点，`spec_technical_section_line=46`）、
  `:48` / `:50` / `:52`（口径说明三段：现状 vs 规格、全路径引用规则、人设层不存在的实测，逐段行号由
  `run172` 的 `spec_caliber_pair` / `spec_fullpath_rule` / `spec_persona_absent` 锚点给出）、
  `:54`（接口）、`:67`（数据结构，列计数见 run151）、`:81`（状态迁移）、`:90`（错误语义）四个小节标题行、
  `:98`（来源栏，也是 `DEFERRED` 现在唯一出现处）、`:100`（疑虑节起点）、`:102`（未决条数声明行，
  两条正文在 `:103-104`）、`:105`（已拍板清单起点）。这四个小节行号原本是按人眼从卡片写的，
  `:52`/`:66`/`:100-102` 三处因此是错的（插入两段口径说明把它们各推了 2 行），本片末尾把它们补成
  驱动锚点再按现值改写。代码侧首手证据以 `run172-pointers.txt` 的 93 条锚点为准，其中规格正文实际引用的
  关键几条（下面按 basename 摘，**不是可复算指针**：basename 形式与 `:NN` 续写都不被 `CITATION_RE` 计入，
  复算权威只有 `run192` 那 93 条，本卡把它们抄成索引就等于本卡开头禁止的那种写法——这一处是已知代价，
  写明它，不假装它是判据）：`PromptPurpose.java:24`/`:56`/`:67`/`:104`、`DeepSeekPromptRegistry.java:50`/`:57`/`:61`/`:79`/
  `:80`/`:82`/`:94`、`PromptBodiesV1.java:25`、`DeepSeekRequestAssembler.java:36`/`:45`、
  `GenerationCoordinator.java:193`、`TreeLearnSchema.java:47`/`:55`/`:61`/`:128`/`:145`/`:276-282`/
  `:416-433`/`:430`/`:432`/`:436-455`/`:705`/`:838`/`:859`/`:874`/`:1002`/`:1009`/`:1037`、
  `Topic.java:10`/`:18`/`:29`/`:33`、`TopicDao.java:42`/`:50`/`:189`/`:219`/`:221`/`:230`、
  `AnswerRevisionDao.java:18`/`:42`/`:125`、`GenerationStabilizer.java:126`、`SqliteChatStore.java:225`、
  `FirstTopicCreation.java:254`、`GenerationModel.java:23`/`:29`、`ArtifactKind.java:99`、
  `DerivedRequest.java:201`/`:206`、`ModelThinkingPicker.java:40`、`ChatPresenter.java:900`、
  `SettingsStore.java:16`、`SettingsRepository.java:27`/`:29`。
  流程侧 `.codex/ROADMAP.md:35`（交付任务，片 032 的 ID 落在这里）、`:36`（本片新增的实现要点行）、
  `:41`（末尾 `DEFERRED` 注意段，已写明由本片兑现）、`:33`（轻流程验收口径，未动）、`:186`（2026-10-20
  第一批登记行）、`:436-458`（新登记的 D12：core 档最重一条 262.4s 贴着 300s 默认上限，与任何命令并发即假红）。
  D12 由本片收尾那一轮并发跑红的 `run186-tiers.txt` 撞出来，登记而不修——判据在 `run_all_guards.py` 里，
  一片纯文档片去改守卫上限属越界，且修法 ① 的数值要先有耗时分布才能定。
- Documentation surfaces checked: `.codex/templates/SPEC.md`（四节骨架与 `DEFERRED` 用法）、
  `.codex/agents/spec-author.toml`（只写 `Docs/spec/**`、每条可追来源、冲突交人拍板）、
  `.codex/rules/ux-design.md` 与 `.codex/rules/documentation.md`（AGENTS.md 路由要求）、
  `.codex/rules/regression.md`（活指针缺陷类）、`.codex/rules/maintainability.md:8`（800 有效行硬门禁，
  作为规格里的边界引用）、`.codex/scripts/check_spec_author_agent.py`（判据覆盖范围）、
  `Docs/intent/INTENT-2026-09-21-ai-persona-preference.md`（决策记录与移交清单）、
  `.codex/ROADMAP.md` 的 B1/B2/B5/B6 条目（`实现要点` 回写文体）。
- Documentation files updated: 规格一份、`.codex/ROADMAP.md` 五处（`:35`/`:36`/`:41` 三处 B0 回写，加
  `:186` 的 2026-10-20 登记行与 `:436-458` 新写的 D12 一节）、五张卡片的引用跟随。
- Producer/consumer/sibling/compatibility paths checked:
  `grep -rn "ai-persona-preference.md:98" .codex/tasks` 末态 9 处（片 026/027/028/029/031，全部由本片从
  `:48` 跟随而来）；`grep -rn "ROADMAP\.md:[0-9]" .codex Docs | grep -v "^\.codex/ROADMAP"` 末态 7 处，
  其中 4 处按现值改写、3 处是片 031 卡片里当时的现场读数（已在同卡注明重跑现为 119，不改成现值）。
  规格自身的引用面由 `b028-citations.py` 全库复算（`files_with_citations=19 citations=196 problems=0`；展开前库内 140 条、展开后 185 条、
  本卡在场后 196 条——规格自身从 42 条涨到 81 条，即 `EDITS applied=39` 那批展开；本片末尾把既定口径那条补回第 62 行时又加 1 条，末态 82）。
- Superseded phrases/examples reverse-searched: 被本片删掉的句子是
  `DEFERRED:待 B4（codebase-researcher）`（含句号变体共 6 处：规格 1 处 + 卡片 5 处）与
  「- 无未决疑虑：本轮提出的疑虑全部已由用户拍板，清单见下。」。反向搜索末态：
  `grep -rn "DEFERRED:待 B4（codebase-researcher" .codex Docs` → 0 命中；
  `grep -rn "无未决疑虑" .codex Docs` → 0 命中；`DEFERRED` 在规格里只剩 `:98` 一处历史提及
  （`deferred_lines=[98]`），在 ROADMAP 里剩 B0 条目 2 处历史提及（`:35`、`:41`），都是「已被兑现」的措辞。
  片 031 卡片里那句「`DEFERRED:待 B4` 仍欠着」也已改为「已由片 032 兑现」的现值说明。
  另两条被本片换掉的旧句也逐条对过去向：`- 待补内容（B4 落地后按四栏补齐）：…` 由四个小节本身兑现（`spec_if_section` 等 4 条锚点在场），`- 已经确定的口径（后续任何一版技术规格都必须守住）：…` 那一条的六个分句分别落在第 57 / 59 / 61 / 62 / 65 行，反向搜索时发现「当前输入位于最后一条」这一句**谁也没接**——它只活在 intent 第 81 行，规格技术侧一度把它丢了。所以本片把它补回第 62 行那条，并配锚点 `assembler_tail_input`（`android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequestAssembler.java:47`），这使节内完整路径引用从 81 条涨到 82 条。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 五张卡片是历史切片记录，本片只改其中**行号指针**与**会误导接手人的状态句**
  （「仍欠着」→「已由片 032 兑现」），不改它们各自的 scope 叙述。规格 `:98` 的来源栏保留 `DEFERRED` 字样，
  让读者能追回 B0 那一栏的来龙去脉。

## Acceptance criteria

- [x] B0 的验收命令通过：`python -X utf8 .codex/scripts/check_spec_author_agent.py
      Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` → 四行 `OK` +
      `check_spec_author_agent: 0 check(s) failed` + `EXITCODE=0`（`run154-specguard.txt`；四节齐全
      `sections=4 missing=[]`、intent 指向真实文件、`measured=[]` 占位符、`hex=[] px=[]`）。
- [x] 技术规格四条栏齐全且「现状」条目逐条带可复算引用：93 条锚点全部唯一命中
      `table floor: facts=93 floor=80` + `measured=93 stale=0 / POINTERS-B0 problems=0 / EXITCODE=0`
      （`run172-pointers.txt`），锚点不唯一、文件缺失或表被抽干即 STALE。
- [x] 规格里每个计数都是现场数出来的：`topics_columns=4`、`answer_revisions_columns=9`、
      `generation_operations_columns=14`、`topic_final_fields=4`、`prompt_purpose_values=8`、
      `database_version=5`、`forward_migration_steps=4`、`alter_table_add_column_uses=1`、
      `persona_identifiers_in_production_code=2`（两行都 `comment=True`）
      （`run151-counts.txt`，`COUNTS-B0 problems=0`）。
- [x] 「人设层还不存在」这条断言由扫描而不是由印象支撑：`android/*/src/main` + `frontend/src` 全量扫
      `persona|人设|回答风格|answer_style` 得 2 行，且驱动断言「非注释命中必须为空」，非空即 `CLAIM FAIL`。
- [x] 简写与续写引用被清零：`full_citations before=42 after=81`（末态 82）、`short_before=5 → short_after=0`、
      `cont_before=34 → cont_after=0`、`EDITS applied=39 refused=0`、
      `written sha12_before=4feb6d4c7d0a sha12_after=7a69baf68e70 readback_identical=True`
      （`run146-pointer-fix-dry.txt` / `run147-pointer-fix-apply.txt` / `run153-pointer-fix-dry.txt`）。
- [x] 展开驱动自己会响：收紧继承规则后的 dry-run 把 17 处无凭据的续写报成
      `REFUSE … no full path before it in this bullet`、`POINTER-FIX problems=34`、退出码 1
      （`run145-pointer-fix-dry.txt`）；同 bullet 继承 + 行号 ≤ 目标文件行数这两条硬检查就是它的牙。
- [x] 规格里每一条完整路径引用都被锚点现场量过：`COVERAGE spec_cites=72 floor=60 anchor_lines=93
      floor=80 unbacked=0 / COVERAGE-B0 problems=0 / EXITCODE=0`（`run195-coverage.txt`）。这条判据是本片最后
      补的：锚点表与活引用扫描之间存在一条缝——引用可以落在一条从没被锚点覆盖过的行上而两处都绿，收尾时按
      前两片跑完仍然全绿，人眼一对才发现 4 条引用（`.codex/scripts/harness_check_lib.py:174`、
      `android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekRequest.java:42` 与生产代码里 persona 那两次
      出现）正在缝里；补齐这 4 条锚点并写出自证驱动的 `b032-coverage.py` 之后，它第一版自己就红了：
      `anchor_lines=1` 撞上下限（正则漏了 `re.M`，`^` 只锚整串开头），**裸下限把一个只匹配一次的判据拦了下来**
      （`run194`），修好才是 `run195` 的 93。收尾时又把规格自身的结构计数并进同一支驱动（`section_lines=53/45`、
      `subsections=4/4`、`bullets=33/25`、`bullets_with_cite=26/20`，`run197-coverage.txt`）——这四个数原本
      是照模板用人眼在规格里数出来的，正是本片实现要点里「别把人眼数出来的计数抄成结论」那条要防的形状。
- [x] 计数驱动有过一次真 RED：把 `forward_migration_steps` 的正则退回本片当天真写过的
      `new MigrationV\d+To\d+\(\)`（类名是 `MigrationV1ToV2`，`To` 后还有 `V`）→
      `forward_migration_steps=0`、`CLAIM FAIL: 规格引用「前向迁移注册表」，实测 0 步`、`problems=1`、`FAIL`、
      退出码 1（`run139-counts-red.txt`）。修正后同一条命令 `problems=0 / PASS`（`run151`）。
- [x] 两条口径冲突没有自行拍板：`Doubts` 里疑虑 1（抛 vs 回落，冲突对象
      `android/core/src/main/java/com/treelearn/core/domain/GenerationModel.java:23`）与疑虑 2（整行写回重排
      首页）各写明两种走法与代价，`谁拍板：用户本人`。
- [x] 加行推歪的指针全部跟随：`artifacts/tmp/b031-pointers.py` 在回写 ROADMAP 后报 6 条 STALE
      （`run158`），按现值更新表的 7 条 + 卡片的 9 条规格引用与 3 条 ROADMAP 区间后
      `measured=214 floor=200 / VERDICT: PASS`（`run159-b031pointers.txt`）。
- [x] 流程面未坏：`check_roadmap_and_routing: 0 check(s) failed / EXITCODE=0`（`run157`）、
      `CITATIONS files_with_citations=19 citations=196 problems=0`（`run202`/`run203` 两轮同值）、
      `DUP-SCAN … cjk_prose_hits=0 / known_old_dup hits=0 / 正控 fired=True / problems=0`（`run203`）、
      `validate_tasks SUMMARY files=27 errors=0 warnings=5`（`run160`/`run179`）、`--tier core` `pass=32 fail=0
      inconclusive=0 fault=0` 与 `--tier precommit` `pass=6 fail=0`（`run162`/`run163`、`run168`/`run169`、
      `run181` 三轮各退 0）均退出码 0。
      末态一轮曾并发跑过 `--tier core`，结果是 `FAIL verify_1to1_selftest timeout after 300s, so nothing was
      judged`、`pass=31 fail=1 / EXITCODE=1`（`run186-tiers.txt`）——同一条判据在串行无争用时实测 262.4s
      （`run168-core.txt`），离 300s 上限只差一点，与本片任何一支脚本同跑就会被顶过线。超时不是「判据变红」
      而是「没判」，那一轮不能当结论用，故末态改为串行重跑，见下一条。
- [x] 末态守门为串行单跑：`--tier core` 与 `--tier precommit` 依次执行、期间不并发任何其它命令，
      读数为 `pass=32 fail=0 inconclusive=0 fault=0` 与 `pass=6 fail=0`，两支退出码 0（`run204-tiers.txt`）。
- [x] 本片没动运行面：`git diff --stat HEAD` 末态 9 files / 80 insertions(+) / 31 deletions(-)（另新增本卡
      382 行），全部落在 `.codex/`、`Docs/` 与 `artifacts/metrics/`；`android/**` 与 `frontend/**` 零命中。

## TDD execution record

### RED — failing test first
- Test/scenario: 不适用（docs 片，见下面的例外栏）。本片仍有两次真实转红，形状与 RED 相同，列在这里而不是藏起来。
- Command: `python -X utf8 artifacts/tmp/b032-counts.py`（改坏正则后重跑）
- Exit code: 1
- Observed failure: `forward_migration_steps=0` + `CLAIM FAIL: 规格引用「前向迁移注册表」，实测 0 步` +
  `COUNTS-B0 problems=1` + `VERDICT: FAIL`。
- Failure reason validated: 红来自被检事实缺席（类名是 `MigrationV1ToV2`，`To` 后还有一个 `V`，写过的
  `MigrationV\d+To\d+` 匹配不到），不是命令没跑起来：同一支驱动改正则后 `forward_migration_steps=4`、
  `problems=0`、`PASS`、退出码 0（`run139` → `run151` 对照）。第二条转红同理：`b032-pointer-fix.py` 的 17 条
  `REFUSE` 来自「该行号不在同 bullet 内任何完整路径的范围内」，用手写全路径消掉之后才 `problems=0`。

### GREEN — minimum implementation
- Implementation: 规格的技术规格节（4 小节 + 来源栏）、口径说明三段、`Doubts` 的 2 条未决疑虑；
  ROADMAP B0 三处回写；五张卡片与 `b031-pointers.py` 表的指针跟随。
- Command: `python -X utf8 .codex/scripts/check_spec_author_agent.py Docs/spec/SPEC-2026-09-21-ai-persona-preference.md`
- Exit code: 0
- Passing evidence: `run154-specguard.txt` 四行 `OK` 逐字 + `EXITCODE=0`；配套 `run192`（锚点 93/0）、
  `run151`（计数 problems=0）、`run153`（引用面 short/cont 全 0）。
- Regression evidence: `run203-dupscan.txt` 与 `run203-citations.txt`（全库活引用与重复短语各 problems=0 且正控在场）、
  `run157`（ROADMAP 守门）、`run159`（214 条指针复算 stale=0）、`run160`（`validate_tasks`）、`run161`（`metrics_report --write`）与
  `run162`/`run163`、`run168`/`run169`、`run181`（core / precommit 两档整跑复算三次，每轮
  `fail=0 inconclusive=0 fault=0` / `EXITCODE=0`）、`run182`（姊妹卡片引用现数 `problems=0`）。

### REFACTOR — optional
- Cleanup: ① 展开驱动从「跨 bullet 继承最近一条完整路径」改为「同 bullet 内继承 + EOF 硬检查」，并把每条改写
  打印成 `SHORT-EDIT`/`CONT-EDIT`；② 新增 `skeleton()` 自检（比较反引号之外的正文逐字相同），因为它曾漏
  `out.append(gap)` 而吞掉散文，暴露方式是 `full_citations after=28 < before=42` 这种自相矛盾的读数；
  ③ basename 索引范围从全库收到 `android/**`（`dup_basenames` 由 198 变 0，`java_files_in_android=479`）；
  ④ 计数改为按匹配次数（`len(re.findall())`）并加断言；⑤ 口径说明里那条反例最初写在反引号里，会被本片的
  展开驱动「修好」，改为「」引用形式——描述被禁止的写法时不要把那种写法放进反引号。
- Regression evidence: ①由 `run145`（17 条 REFUSE、rc=1）承重；②由 `run146`/`run147` 的
  `readback_identical=True` 与 `short_after=0 cont_after=0` 承重；③由 `run146` 的 `dup_basenames=0`
  与 `refused=0` 承重；④由 `run139` vs `run151` 承重；⑤由 `run153` 的 `short_before=0` 承重。

### TDD or sizing exception — only with prior user approval
- Exception type: docs 片豁免 RED/GREEN 测试先行（本片的可执行判据是暂存驱动与 B0 守门，不是单元/契约测试）。
- Reason: 产物是给人 review 的规格文本，运行面零改动；B0 的验收方式已由用户简化为轻流程，明确**不再要求**
  独立复核轮次、证据文件与「每条检测分支配一条注入用例」的自闸门。
- User approval: 用户 2026-09-21 对 Stage 2 定下轻流程（现值见 `.codex/ROADMAP.md:33` 与
  `.codex/scripts/check_spec_author_agent.py` docstring 里的同一记录），并在本线要求「一站到底、不中途请示」；
  本片没有改变目标、架构、数据或验收方式，因此未再单独申请。
- Alternative verification: 五条 B0 之外的判据仍现场跑：93 条锚点唯一命中且带表自身下限（`run192`）、9 项计数带断言
  （`run151`，含一次真 RED `run139`）、引用展开与 EOF/skeleton 自检（`run145`/`run147`/`run153`）、
  全库活引用与重复短语扫描（`run202`/`run203`）、指针复算表跟随（`run159`）、
  姊妹卡片引用现数（`run182`）、`validate_tasks` 与 core / precommit 两档整跑（`run160`–`run163`、
  `run168`–`run169`、`run181`）。

## Implementation notes

- 三条从代码推出来、任何文档里都没写着的边界，是本片的实际产出：人设**不进**版本命名空间（`DeepSeekPromptRegistry.java:57`/`:61`/`:82`——
  思考附加段只追加、不改基础版本，主回答版本字面量至今 `tl-learning-answer/1.1.0`，所以人设与它同构、
  `PromptBodiesV1.java:25` 的基线正文一个字不动）；`answer_revisions.persona` **不可省**（`:145` 的注释口径是
  「用途能从版本前缀反查」，但人设是叠加在同一版本上的第二个维度，反查不出来）；切换人设**不能复用整行写回**
  （`TopicDao.java:42`/`:230` 写回 `updated_at`，首页按 `:189`/`:219` 倒序，会把整棵树挪到顶部——这一条按角色
  纪律写成待拍板疑虑 2，而不是技术结论）。
- 合取判据才有牙：命令与请求都带身份时，「版本不符即拒」（`GenerationCoordinator.java:193`）必须**同时比人设**，
  否则守门会放行「命令说严格导师、请求拼了手把手」，那是同一条判据换了个字段就失效。
- 「现状」与「规格」分栏不是为了好看：本片有两条规格口径与仓库现成做法相反（抛 vs 回落），不分开写就会被读者
  当成既有实现的事实。
- 计数按次数不按行数这条是踩出来的：迁移注册表 4 步写在 2 行里，按行数得 2；`run139` 的 RED 让这一栏从此带
  断言（步数为 0 而规格引用它 → `CLAIM FAIL`）。
- 规格里描述被禁止的写法时，不要把那种写法写进反引号：`b032-pointer-fix.py` 不认语义，会把反例本身当成待展开
  的引用改掉——规则被自己的工具吃掉。
- 本片不升格 `status`：`draft` 要等人 review 接受才改（模板与角色纪律一致），验收命令也不查这一项。

## Verification report

- Verdict: PASS（B0 轻流程验收命令退出码 0；另有 93 条锚点、9 项计数、引用展开与 EOF/skeleton 自检、一次计数
  侧真 RED、全库活引用与重复短语扫描、214 条指针复算跟随；见 Gaps 里未覆盖的部分）
- Evidence: 本片属 B0 轻流程，按 `.codex/ROADMAP.md:33` **不产** `artifacts/evidence/` 文件；读数以捕获文件
  形式留在 gitignore 的 `artifacts/tmp/`，可重跑：`run133-*`/`run140`/`run150`/`run164`（锚点表的早期轮次）、`run172`/`run187`/`run189`/`run192-pointers.txt`（末态 93 条）、
  `run135-counts.txt`–`run138-counts.txt`（计数各轮）、`run139-counts-red.txt`（RED）、
  `run141-specguard.txt`/`run142-citations.txt`（节写完的第一轮守门与活引用，`citations=140` 是展开**之前**的
  读数）、`run143`/`run144`/`run145`/`run146-pointer-fix-dry.txt`（展开驱动各轮，含 17 条 REFUSE 那轮）、
  `run147-pointer-fix-apply.txt`（落盘 + sha12 回读）、`run148`–`run156`（节写完后的末态复算）、`run157`–`run159`
  （回写后的流程面跟随）、`run160`–`run169` 与 `run171`–`run204`（卡片在场后的 `validate_tasks`、`metrics_report --write`、
  core / precommit 两档整跑与五支片 032 驱动复算）。备份 `artifacts/tmp/SPEC-persona.before-pointerfix.md` 在场。
  环境：Windows 10.0.26200 / x64 / Python（一律 `-X utf8`）/ Git Bash。
  **未落盘的轮次**：展开驱动第一版（跨 bullet 继承）的 `measured`/`applied` 读数与 `out.append(gap)` 吞正文那轮
  的 `after=28` 只留在会话输出，所以本卡引用的是修正后同驱动 `problems=0` 的 dry/apply 两轮加 `run145` 的 REFUSE
  轮；引用吞正文那件事的数值证据是 `run146`（`before=42 after=81`，单调递增）而不是那轮丢失的输出。
  规格 `:48` 与 `:98`、`spec_technical_section_line=46`、`spec_lines=110`、`full_citations=82`、
  `citations=196`、`measured=214` 等数各自出自 `run151`/`run165`/`run153`/`run203`/`run159`；
  卡片在场之前的 `citations=185`（`run155`）与 `scanned_lines=18735`（`run156`）是同一批读数的早期值。
  这两个数**含本卡自身**，卡片加长就会漂：本卡的规格小节指针改正之后重跑得到
  `citations=196 / scanned_lines=19146`（`run202`），回填那两个数之后再跑仍是同一对（`run203`），
  所以写进本卡的是已收敛的那一对，不是抄来的上一轮。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除或放宽任何既有判据。
  `b032-pointer-fix.py` 的两处改动（继承范围收紧、新增 EOF 与 skeleton 检查）方向都是更严：REFUSE 从 0 条变
  17 条再被手写全路径消掉；skeleton 不等即 `CLAIM FAIL` 且拒绝落盘。片 031 的 `b031-pointers.py` 表本片改了
  7 条的**声称行号**（不是锚点、不是下限）：ROADMAP 4 条 +1、规格 2 条由 `:48` 改到 `DEFERRED` 现在所在的
  `:98`、1 条区间端点跟随；下限 `MIN_POINTERS = 200` 与 `measured=214` 未动，`b032-pointers.py` 的
  `b032-pointers.py` 在卡片末态补了 13 条规格自身结构锚点后是 93 条（含覆盖检查补的 4 条），它也有了自己的裸下限
  `MIN_ANCHORS = 80`——表被抽干时 `measured=0` 读起来和「全活」一样舒服，所以条数本身也要判。
- Gaps: ① 本片没有任何一条 core 档守门会**持续**盯住规格里那 82 条引用——B0 的守门只查四件事，行号死活不在
  其列；防漂移靠 `artifacts/tmp/b032-pointers.py` 这支暂存驱动，而它不进版本库、下一片未必接手。
  ② 93 条锚点覆盖的是规格引用的位置，不覆盖「那一行是否还在做我说的那件事」——锚点正则能挡位置漂移，挡不住
  语义漂移。③ 疑虑点 1 / 2 未拍板，规格相应栏因此是「按界面方案写要求 + 原样上报冲突」，不是结论；拍板后
  需要改写。④ `Docs/intent/…` 里的行号疑似过期但本片没验（只读边界），属角色面之外的独立一片。
  ⑤ ROADMAP `交付任务` 里的 `T-2026-09-21-035/036` 两个 ID 在 `.codex/tasks/` 无对应文件（该目录 27 个文件最老
  2026-10-18），本片保留原样并登记，未顺手改。⑥ 本卡自己写的 `.codex/ROADMAP.md:33/35/36/41/186/436-458`
  六处引用不在任何锚点表的判定范围内——`b032-sibling-refs.py` 只扫那五张姊妹卡片（那里面的 3 条 ROADMAP 引用
  逐条带锚点），把它扩到本卡会同时改掉 `spec_cites` / `roadmap_cites` 的计数口径，属独立一片；本片按现状写明，
  不让「姊妹卡的引用被盯住」读成「本卡的引用也被盯住」。
- Risks: 实现片一改 `TreeLearnSchema.java` 或 `TopicDao.java` 的行数，规格引用立刻漂，而守门不会响；这是 D9
  缺陷类在文档面的延续，本片把复算工具留在了 `b032-pointers.py`。第二个风险是口径未定：疑虑 1 拍板成「抛」的话，
  界面方案那条降级要求要一起改，改的就不只技术规格一栏。
- Independent verification（2026-10-20）：PASS。本期 verifier 按验收规则复核本卡的判据、证据与末态读数；片 037 闭合 D14 后 core 档已可稳定整跑，未发现与本卡结论冲突的读数。

## Handoff notes

- Completed: B0 的四节齐全（技术规格节 4 小节 + 来源栏，93 条锚点 + 9 项计数全部现场化）、2 条待拍板疑虑上报、
  简写/续写引用清零（82 条完整路径）、ROADMAP B0 三处回写（`交付任务` / 新增 `实现要点` / 末尾注意段）、
  五张卡片与 `b031-pointers.py` 表的指针跟随（`measured=214 stale=0`）、`check_roadmap_and_routing` 仍绿。
- In progress: 无。
- Blocker: 无（疑虑点 1 / 2 是待用户拍板，不阻塞本片交付，但阻塞实现片接手）。
- Unblock condition: 用户对「未知档位抛 vs 回落」与「切换人设是否刷新主题活动」给出选择。
- Next steps: ① 把规格交用户 review，接受后把 `status` 从 `draft` 升格，并按疑虑点拍板结果改写技术规格相应栏；
  ② 下一片按 ROADMAP 与 `check_roadmap_and_routing.py` 的顺序取 C2（`loop-watcher`）或 C3（事实源全量声明）；
  ③ 若下一片要动 `android/**` 的主题列或回答记录列，先跑 `b032-pointers.py` 再动手，别手写行号；
  ④ 待清理两项：`spec-author` 缺成对 `.md`（角色面）、intent 文件里的过期行号（intent 面）。
- Watch-outs: 给 ROADMAP 任何条目加一行，就会推歪所有引用它行号的句子——本片实测撞到 6 条 STALE，
  接手顺序是先回写、再跑 `b031-pointers.py`、最后按现值改卡片；别用 `git checkout --` 还原注入过的文件
  （`core.autocrlf=true` 会把 LF 换成 CRLF）；描述被禁止的写法时别把那种写法放进反引号；
  计数一律按匹配次数，按「有匹配的行数」数会得到形状像真话的假数。
