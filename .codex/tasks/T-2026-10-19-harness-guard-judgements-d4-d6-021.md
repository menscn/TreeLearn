# Codex Harness Task: 门禁判据 D4/D6——反向搜索兜底与正控样本现场派生

id: T-2026-10-19-021
title: 门禁判据 D4/D6——反向搜索兜底与正控样本现场派生
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 182
shared_code_touched: false
depends_on: [T-2026-10-18-treelearn-process-migration-001.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 把流程入口收进 `.codex/`，并留下「已知欠账 D1–D8」一节。D1、D8 已在 2026-10-18 闭合；本片取其中两条**判据本身有洞**的欠账：D6（`check_stack_facts` 只钉已知精确短语，拦不住清单外新长出的同族主张）与 D4（`checkers_selftest` 的抄袭正控样本是手抄文本，改一处 pm-discovery 正文即失效，且失效方向是 fail-open）。
- Problem/background: D6 的实际发病过程写在 ROADMAP：002 片第一轮只列了 9 处命中，第 10 处（`Docs/spec/SPEC-2026-09-23-providers-and-models.md:168`）是收尾时**第二轮反向搜索**才冒出来的——也就是说守门当时对「下一处新写的旧主张」完全无感，因为它只认清单里那几条精确串。D4 的病因同族但方向相反：正控（用来证明「40/60 字重叠阈值确实抓得到抄袭」的那段样本）是从 pm-discovery 抄进自检脚本的常量，被抄的文本一改，正控就和被检文本再无重叠，用例照样跑、照样打印 ok，只是**再也证不到任何东西**。
- Users/scenarios: 学习者看不到这两条；它们服务的是「文档/门禁还能不能自证」。下一位实现者（人或 AI）读到 README 里「本项目没有后端」这类句子，会据此决定要不要建目录——002 片已经因为这个付出过一次返工。
- Expected outcome: D6 变成「清单**无关**的兜底扫描」：扫描集内任何一行留着旧方向形状，就必须同一行带权威指针，不带即判红并给到行号。D4 变成「正控随被检文本走」：样本从 pm-discovery 现文现场切片，档位关系是断言出来的，取不到样本时以「没跑完」（rc=3）的身份出现而不是「用例通过」。
- Core flow and reversibility: 只改两支 dev 仪器与 README 的一段叙述，不改生产代码、不改 SQL、不改任何用户可见文案。删除新增判据即可回到本片之前（旧行为见 git 历史，不留注释）。
- Inputs/outputs: 输入 = 当前工作树文本；输出 = 六条判据（原四条 + 模式下限 + 同行指针）、25 例自检（原 22 例 + 模式三例）、50 例电池的形状闸门与派生样本。
- State and exception handling: `STALE_PATTERNS` 为空必须判红（下限 `MIN_PATTERNS = 6` 写成裸字面量，与 `MIN_INVENTORY` 同理由）；扫描集一个文件都没匹配到不得算绿（前置：自检用一份**清单外**文件 `PATTERN_SCAN_SAMPLE` 证明这条判据真的读到了清单之外）；`derive_sample` 返回 None 时 `run_case` 返回 HARNESS，聚合行 `harness_errors>0` → `OVERALL: INCONCLUSIVE` / rc=3。
- Constraints and trade-offs: 只用标准库；命中不直接判红——「同行带权威指针算引用历史、不带才算把旧方向当现状」。这条区分是刻意的：若凡提及即红，唯一变绿办法是删掉那些纠正性引用，等于让守门逼人销毁证据。代价是模式扫描有假阳性风险（改写后的「不做远程服务端」合法），所以豁免判定用「同一行含 `SPEC-2026-10-15-web-h5-android-parity`」而不是关键词表。
- AI recommendation and rationale: 两条都按 ROADMAP 建议的修法实施（D6 加兜底扫描；D4 改为现场切片并加「样本必须能在被判文本里找到」的断言），并在 D4 上多做一层：不在运行时检查样本内容（派生自现文，天然对得上，查了等于没查），而是检查**用例形状**——三条抄检用例必须走 `append_sample`，出现字面量即在建副本之前判 rc=3。
- User choice and custom additions: 用户要求一站到底、按片提交本地仓库、过程中不请示。
- AI-autonomous decisions: ① 模式集合放在 `check_stack_facts.py` 一处（`STALE_PATTERNS`），短语表与正则表混排，因为「同一约束只留一份权威正文」；② 扫描集按前缀取而不是复用 `STACK_FACTS`——复用就等于这条判据不存在；③ 权威规格自身所在行豁免（`AUTHORITY in rel` 跳过整个文件），因为它是这条事实的定义处，必然要引用被它取代的旧主张；④ 档位样本长度沿用历史实测的 89/60/44，不重新选数，免得把「阈值改宽」混进「样本改派生」。
- User-confirmation decisions: 无新增（沿用 001 已确认范围）。
- Acceptance evidence: `artifacts/evidence/T-2026-10-19-harness-guards-d4-d6.txt` 块 00–08。
- Facts: 块 00 的对照是同一份副本、两版脚本各跑一次——注入一行清单外的裸主张（`.codex/rules/sql.md`「本仓库无服务端，也不做后端。」），**改前 rc=0（看不见，缺陷复现）、改后 rc=1**；把 pm-discovery 正文逐字 +1 码点（长度 3710 不变）后，**改前电池 3 条 MISMATCH / rc=1，改后 50 例全绿 / rc=0**，且三条旧手抄样本在新文本上的 60/40 字档命中全为 0（用守门自己的窗口函数算出来的，不是推测）。生产树：`check_stack_facts` 六条判据 `0 failed / EXITCODE=0`，`--selftest` `25 case(s), 0 mismatch, expected=25`，`checkers_selftest` `cases=50 ok=50 mismatch=0 harness_errors=0 / OVERALL: PASS`。命中数 30/21/5 与 002 片历史手抄证据逐条相同。
- Assumptions: 无。所有读数由当场执行取得。
- Unknowns: 扫描集四个前缀之外的旧主张（如 `Docs/05-technical-design/**` 里新长出的同族句子）不在 D6 兜底覆盖面内，那类文件由盘点清单的精确短语那条管；这种分工是否够用，等下一次真实漂移再判。
- Risks: 同行指针这条豁免让「合法引用」与「把旧方向当现状」的区分依赖一行内是否写了规格名——写的人可以不提规格而改写句子来绕过（改写后不再是旧方向形状，属正当收窄而非绕过）。风险可接受，因为判据要防的是**残留原话被当成现状**，不是防人重写。
- Deferred: D2/D3/D5/D7 的判据裁定（本片只在块 06 留当前态读数并回写 ROADMAP）；`run_all_guards` 一键化（B3）。
- Non-goals: 不改 `check_context_pack`、`validate_tasks`、`check_roadmap_and_routing` 的判据；不动 Android/前端；不把 `.codex/references/regression-cases.md` 的案例指针改写（另记为 D9）。
- Skipped non-critical questions and reason: 未追问「要不要把 10 条精确短语并进模式表只留一条判据」——两条各挡一件事（精确串挡「原话残留」，模式挡「同族新写」），合并会把 002 片的第 10 处命中重新变成看不见。

## Goal

让「清单外新长出的旧方向主张」和「被检正文被改写后的抄袭正控」两件事，从「没人能发现」变成「当场判红或当场判仪器故障」，并把两件事各自的可复算判据长期留在已入库的仪器里。

## Scope

### In scope
- `.codex/scripts/dev/check_stack_facts.py`：新增 `STALE_PATTERNS` / `MIN_PATTERNS` / `PATTERN_SCAN_PREFIXES` / `PATTERN_SCAN_SAMPLE`、`scan_files()`、`pattern_scan()` 与两条判据（模式下限、同行必须带权威指针）；`--selftest` 加 `pattern-bare`（判红）、`pattern-attributed`（判绿）、`empty-patterns`（判红）三例，用例数断言由 `2N+2` 改为 `2N+5`，中止消息同步。
- `.codex/scripts/dev/checkers_selftest.py`：删除三段手抄常量，新增 `derive_sample()`（现文切片 + 三条约束：能在现文找到、不含路径与占位符、落在预期档位）、`append_sample` 操作、`PM_SKILL` 与 `SAMPLE_SPEC_*` 三份规格，以及 `main()` 开头的用例形状闸门（不是 `append_sample` 即 rc=3）。
- `README.md`：把「该目录不存在」这类叙述补上当前权威指向，并把机械判据从三条写成四条（补模式兜底那条）。
- `.codex/ROADMAP.md`：D4/D6 写处置与回读命令；D2/D3/D5 按块 06 读数回写现状；新增 D9（指向已迁移的失败案例库的 5 处陈旧措辞）。

### Out of scope
- D2/D3/D5/D7 的判据改动与真机仪器复跑（跑整包会卸载应用、清学习者本地数据）。
- B 阶段角色（code-reviewer、eval-keeper 等）与 `run_all_guards`。
- `.codex/` 内「AGENTS.md 失败案例库」措辞的批量改写（只登记，不在本片动其它文件）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 旧四条判据的行为与读数原样保留（块 01/02 里前四条的 measured 值与 002 片一致：条目 10、缺失 0、精确串命中 0、缺指针 0）。新增的是两条兜底判据与一处自检形状闸门。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `check_stack_facts.py` 无生产消费者，只被 `.codex/scripts/run_all_guards`（B3 尚未落地）与人手执行；`harness_check_lib.py` 是共享库，本片只**复用**其 `normalize` / `PATH_RE` / `overlap_hits` / `PLACEHOLDERS`，未改其签名，故 `check_intent_capturer_agent.py`、`check_spec_author_agent.py` 等消费者不受影响（块 04 四条基线全绿就是这条的直接证据）。README/ROADMAP 属文档面。
- Native bridge or independent-client compatibility/update requirements: 无桥接与客户端改动。
- Behavior-neutral non-applicability reason, if applicable: 无——本片确实改了两条判据的行为（D6 那条从「只认清单」改成「清单无关兜底」），所以正反两个方向都各配了实测（块 00 的注入判红、块 02 的带指针判绿）。

## Documentation impact record

- Changed runtime facts: 无运行时代码变化。变化的是「文档一致性守门」的覆盖面：旧方向主张现在会被扫描集内任意一行触发。
- Authoritative code/config/migration evidence: `.codex/scripts/dev/check_stack_facts.py:60-75`（模式表与扫描集）、`:91-108`（`pattern_scan`，同行指针豁免在此）、`:141-147`（两条新判据）；`.codex/scripts/dev/checkers_selftest.py:107-109`（三份样本规格）、`:116-147`（`derive_sample`）、`:963-973`（`append_sample`）、`:1028-1030`（形状闸门）。
  这四条行号是 2026-10-19 片 028 复算、片 029 再复算、片 030 第三次、片 031 第四次复算的：片 025/026/027/028/029/030/031 依次往同一个文件加常量与用例，把原引的
  `:60-63` / `:66-90` / `:347-356` / `:408-418` 整体下推——本卡当时写下它们是对的，之后没人重测就成了活指针（D9 同类）。
- Documentation surfaces checked: `README.md`、`README_EN.md`、`.codex/ROADMAP.md`、`.codex/context/project-brief.md`、`.codex/rules/**`、`.codex/skills/pm-discovery/SKILL.md`、`Docs/spec/**`、`Docs/05-technical-design/**`（块 02 的 20 例注入即逐文件反向搜索的机器版）。
- Documentation files updated: `README.md`（补当前权威指向 + 判据由三条改四条）、`.codex/ROADMAP.md`（D2/D3/D4/D5/D6 处置与 D9 登记）。
- Producer/consumer/sibling/compatibility paths checked: 见上条 surfaces；扫描集四个前缀与 `PATTERN_SCAN_PREFIXES` 逐一对应，`grep -n` 现场核过。
- Superseded phrases/examples reverse-searched: 是——这正是本片的内容。块 02 每个被盘点文件跑 `inject-stale` 与 `drop-pointer` 两例，块 00 额外证明「清单外」也能被抓住。
- `documentation impact: none` reason and evidence, if applicable: 不适用，确有文档更新。
- Historical/PRD handling: 002 片留在 ROADMAP 与各任务文件里的旧读数**不回改**（历史捕获不是现状）；本片只在 D 条目上追加「处置 + 回读命令」，并按 D1 的教训把「某时点的 rc」与「今天的门禁状态」分开写。

## Acceptance criteria

- [x] D6 兜底：在**盘点清单之外**的扫描集文件里注入一行裸的旧方向主张，守门判 FAIL 并给到 `文件:行号`；同一形状但同行带权威指针判 OK；清空 `STALE_PATTERNS` 判 FAIL。证据 = 块 00（改前 rc=0 / 改后 rc=1）+ 块 02 的 `pattern-bare` / `pattern-attributed` / `empty-patterns` 三例。
- [x] 旧四条判据行为不变：生产树仍 `0 check(s) failed`，条目 10、指针缺失 0、精确串命中 0。证据 = 块 01。
- [x] D4：正控样本来自被检文本现文（样本串在块 04 的 FAIL 行里可见，且逐字出现在 pm-discovery 现文中）；把 pm-discovery 正文改写后，旧电池 3 条 MISMATCH、新电池 50 例全绿。证据 = 块 00 B 段。
- [x] D4 复发防护：任一抄检用例的操作从 `append_sample` 改回字面量 `append`，自检在**建副本之前**返回 rc=3（`OVERALL: INCONCLUSIVE`），不是 rc=1。证据 = 块 05。
- [x] 仪器三档退出码互斥未被破坏：`check_stack_facts --selftest` 用例数断言 `expected=2*len(清单)+5`，对不上判 rc=3；`checkers_selftest` 的 `harness_errors>0` 仍走 INCONCLUSIVE。证据 = 块 02 末行、块 04 末三行。
- [x] 文档一致：README 的判据叙述与实际判据条数一致（四条），ROADMAP 的 D2/D3/D5 现状与块 06 读数一致。证据 = 块 06 + 本片的 README/ROADMAP diff。
- [x] 全部门禁现场复算：`python -X utf8 .codex/scripts/dev/check_stack_facts.py`（rc=0）、`--selftest`（rc=0，25 例）、`python -X utf8 .codex/scripts/dev/checkers_selftest.py`（rc=0，50 例 + 4 基线）、`python -X utf8 .codex/scripts/validate_tasks.py`（`errors=0`）、`python -X utf8 .codex/scripts/check_context_pack.py`（rc=0）。证据 = 块 01/02/04/06。

## TDD execution record

> 为什么这张卡的 RED 只押 D4：两条欠账的缺陷形状不同。D4 的缺陷会让**同一条命令**在改前非零、改后为零，
> 正好是 Task v2 要的 RED→GREEN。D6 的缺陷形状是「注入违规之后仍然 rc=0」——修好之前的那次运行按构造就是 0，
> 写成 RED 只能靠编一个非零退出码，那是伪造。所以 D6 用「同副本双版本对照 + 真实树注入/还原」两组实测证，
> 记在下面 Facts 与验收项，并把可长期复算的部分留在入库的仪器里（`--selftest` 的 `pattern-bare` /
> `pattern-attributed` / `empty-patterns` 三例）。

### RED — failing test first
- Test/scenario: 把 pm-discovery 的正文逐字符 +1 码点改写（长度 3710 不变、与三段手抄样本再无 40 字重叠），在临时副本里跑整块电池。电池必须证明「抄袭正控确实抓得到抄袭」；被检文本一改，手抄样本就与现文再无重叠，正控再也证不到任何东西。
- Command: `python -X utf8 .codex/scripts/dev/checkers_selftest.py`
- Exit code: 1
- Observed failure: `CASE 19/20/21 MISMATCH intent:copied-methodology-* - expected FAIL label, rc=0, seen=[]`，聚合为 `SELFTEST cases=50 ok=47 mismatch=3 harness_errors=0 / OVERALL: FAIL`（块 00 B 段「改前」）。三条抄检用例注入的是**写死的旧文本**，守门在新文本上量到 0 命中于是打 OK，用例等不到期望的 FAIL 行。
- Failure reason validated: 是「样本与被检文本脱钩」而不是「副本不健康 / 守门崩」——同一副本上，用守门自己的窗口函数直接量三段旧样本的重叠命中为 60 字档 0、40 字档 0（块 00 B 段头三行），而四条基线守门在该副本上仍全绿；坏的是正控，不是判据。

### GREEN — minimum implementation
- Implementation: 删除三段手抄常量，新增 `derive_sample()`（在被检现文上逐窗口取样：必须逐字存在于现文、不含路径片段与占位符词、且落在预期档位）与 `append_sample` 操作；`main()` 开头加用例形状闸门（三条抄检用例必须走 `append_sample`，否则建副本之前就 rc=3）。同片另加 D6 的清单无关兜底扫描（见验收项 1）。
- Command: `python -X utf8 .codex/scripts/dev/checkers_selftest.py`
- Exit code: 0
- Passing evidence: 同一份改写后的副本上 `SELFTEST cases=50 ok=50 mismatch=0 harness_errors=0 / OVERALL: PASS`（块 00 B 段「改后」），三条抄检用例的样本串现在逐字来自被改写的现文；真实工作树上再跑一次同样 50 例全绿、命中数 30/21/5（块 04），与 002 片历史手抄证据逐条相同 → 阈值与档位一位未动。
- Regression evidence: 块 01 生产树 `check_stack_facts` 六条判据 `0 failed`；块 02 `--selftest` `25 case(s), 0 mismatch, expected=25`；块 03 真实树注入→判红 rc=1→字节级还原→判绿 rc=0（`RESTORE sha … equal=True`）；块 05 形状闸门变异 → rc=3；块 06 `check_context_pack` 与 `validate_tasks` 未受本片影响（`errors=0` 除本卡修前的 1 条）。

### REFACTOR — optional
- Cleanup: 把 `pattern_scan` 的编译正则放到函数内（模式表可变，自检会清空它，模块级预编译会让 `empty-patterns` 例失真）；`derive_sample` 里跳过 front matter 的偏移改由 `len(normalize(...))` 求（原先按原始串下标切归一化串，会错位）。
- Regression evidence: 重构后重跑块 02（25 例）与块 04（50 例）均为 rc=0；块 05 的闸门仍返回 rc=3。

### TDD or sizing exception — only with prior user approval
- 不适用（182 行 < 500 行预算，`tdd_mode: required` 已按 RED→GREEN 走完）。

## Implementation notes

- 「被检集合不能给自己定下限」在本片出现两次：`MIN_INVENTORY = 10` 是既有写法，新增的 `MIN_PATTERNS = 6` 沿用同一理由（写成 `len(STALE_PATTERNS)` 就等于清空模式表也照样绿）。自检里 `empty-patterns` 例专门把它打穿一次。
- 反向的那条用例 `pattern-attributed`（同行带指针必须判 **0**）是判据的接受路径，不是装饰：没有它，「凡提及旧短语即红」这种更宽的守门也能通过 `pattern-bare`，而那种守门的唯一变绿方式是删掉纠正性引用——删证据换绿灯是本仓库明令禁止的形状。
- D6 扫描集与盘点清单**刻意不同源**：`PATTERN_SCAN_PREFIXES` 是四个路径前缀，`STACK_FACTS` 是十条精确串。自检夹具因此必须放一份清单外的文件（`PATTERN_SCAN_SAMPLE = .codex/rules/sql.md`），否则这条判据在自检里只被清单内文件证到，等于没证到它要防的那件事。
- D4 的闸门查形状不查内容：样本派生自现文，任何「样本是否等于现文」的断言都恒真。所以断言改成「三条抄检用例必须走 `append_sample`」——一旦有人把它改回写死文本，`main()` 在建副本之前就 rc=3。块 05 是这条闸门的变异实测。
- 撤注入**不得用 `git checkout --`**：本片第一次用 `git checkout -- .codex/rules/sql.md` 还原，sha 由 `b644079527ff` 变成 `d0e27a3c9c89`——`core.autocrlf=true` 会按 smudge 重写行尾，内容相同字节不同，钉在证据里的读数就复算不出来。改成同进程内「读原始字节 → 注入 → 跑 → 原字节写回 → 打印 sha 相等」（块 03 末行）。这与 019 片钉夹具字节、`.gitattributes` 锁 `eol=crlf` 是同一缺口的三面。
- 三个驱动脚本留在 gitignore 的 `artifacts/tmp/`，不入库；可长期复算的部分全部搬进了已入库的仪器（`--selftest` 三例 + 形状闸门）。块 00 的「旧版看不见」是一次性对照，旧版脚本已被本片替换，之后跑不出同一对照——这点写在块 08，不假装它还能重跑。

## Verification report

- Verdict: PASS（同副本双版本对照 + 真实树注入/还原字节证明 + 两支仪器各自正反双向 + 整块电池 50 例 + 全局门禁读数五层；跨克隆可复算性未单独证明，见 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-harness-guards-d4-d6.txt` 块 00–08。环境：Windows 10.0.26200 / x64 / Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。命令与退出码逐块原样粘贴，无事后编辑；文件 507 行、`\ufffd` 计数 0。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删掉的是三段手抄样本常量（`STOLEN_LONG` / `STOLEN_SHORT` / `STOLEN_TRIM`）与 `--selftest` 的用例数断言 `2N+2`。前者由 `derive_sample` + `SAMPLE_SPEC_*` 档位规格等价替代，且**更强的**：历史证据里的命中数 30/21/5 在块 04 现场复算相同；后者改为 `2N+5`，因为新增了三个模式用例，少跑一个就 rc=3。没删任何判据，没改阈值（60/40 字档与允许命中数 1/0 原样）。
- Gaps: ① 驱动脚本不入库，块 00 的一次性对照在别的克隆上跑不出来（长期复算靠已入库的两支仪器）；② D6 扫描集只覆盖四个前缀，`Docs/05-technical-design/**` 里**新写**的同族句子仍靠精确串那条；③ 块 00 B 段的「改写后文本」是逐字 +1 码点的结构替身，不是人写的新 prose；④ D2/D3/D5 只回写现状未做裁定，D7 完全未动（跑整包仪器会卸载应用、清学习者数据，须由用户在场时执行）；⑤ 独立 verifier 复核已于 2026-10-20 完成（本片属 harness 仪器面，判据全部可现场复算），结论见本节末尾 Independent verification 行。
- Risks: 同行指针豁免依赖「那一行写了规格名」——它挡的是原话残留，不挡改写；模式表若被后续「嫌吵」而随手删到 5 条以下会立刻判红（下限就是为此写的）。
- Independent verification（2026-10-20）：PASS。本期 verifier 按验收规则复核本卡的判据、证据与末态读数；片 037 闭合 D14 后 core 档已可稳定整跑，未发现与本卡结论冲突的读数。

## Handoff notes

- Completed: D6 判据（含下限、扫描集、同行指针豁免、三例自检）、D4 现场派生正控与形状闸门、README 判据叙述对齐、ROADMAP 的 D2/D3/D4/D5/D6 回写与 D9 登记、证据文件块 00–08、本地提交。
- In progress: 无（本片已收口）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 按 ROADMAP 顺序取 B3（`run_all_guards` 一键跑全部只读守门）——它落地后本片的 `check_stack_facts` 两条新判据会进入那条命令的判据行清单；随后 B1/B4。D9（5 处「AGENTS.md 失败案例库」陈旧措辞 → 现址 `.codex/references/regression-cases.md`）适合与 B3 同片处理，因为它只动注释与措辞。
- Watch-outs: ① 改 pm-discovery 正文不再需要重抄样本，但**阈值**若要动，必须同时改 `check_intent_capturer_agent.py` 的两个档位常量与 `SAMPLE_SPEC_*` 的期望命中数，否则 `derive_sample` 会取不到样本而判 rc=3（这是设计，不是 bug）；② 往扫描集前缀里加新目录前，先确认没有大量合法的「无服务端」类纠正性引用不带指针；③ 别在混合行尾的工作树里用 `git checkout --` 撤注入。
