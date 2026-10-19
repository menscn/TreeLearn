# Codex Harness Task: 背景包 L0 的目录现状主张改成当前态，并给它加一条双向守门

id: T-2026-10-19-025
title: project-brief 目录现状修正与 check_context_pack 的双向判据
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 22
shared_code_touched: false
depends_on: [T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 001 要求「完成结论必须匹配证据覆盖」，并把规则、任务、文档与流程资产归 `harness` lane。
  本片是同一要求在**常驻背景包**上的落点：L0 文件说错的现状，比一份过期报告为害更大，因为每个角色都读它。
- Problem/background: 2026-10-19 为 B4 读 `.codex/context/project-brief.md` 时发现两处与实测冲突：
  ①「形态：纯 Android 原生」——`frontend/` 早已按 Web 线落地（React + TypeScript + Vite，阶段 2 骨架 011–015、
  夹具 019、逐屏渲染 020）；②「规划新建 `frontend/` 与……`backend/`；两目录尚未创建」——实测
  `ls -d frontend` 存在、`ls -d backend` 不存在。按 `.codex/rules/documentation.md` 的双向一致性，
  当前态文档必须用生产来源（目录在场性）证明，而不是从另一份文档复制。
- Users/scenarios: 读背景包的所有角色与用户本人。场景是「照 L0 决定去哪里找代码」——它说前端还没落地，
  下游就会去 `android/` 找一份并不存在的「规划」，或反过来把已落地的实现当成未开工的意图。
- Expected outcome: 背景包两处写成当前态；`check_context_pack.py` 增加一条能挡住这个形状的判据，
  并且这条判据在注入用例里两个方向都会响（把已存在的目录说成「尚未创建」/ 把不存在的目录当现状引用）。
- Core flow and reversibility: 新增三行常量与两段判定（约 22 行），改背景包两行正文，
  给 `checkers_selftest.py` 加 3 例 `context:*` 用例并把 `check_context_pack.py` 升为第 6 条基线；
  另把基线 tag 从手写字典改成文件名派生、把工作副本补齐顶层目录名（新判据在沙箱里复算的前提）。
  全部可逆：删掉判定块与用例、还原两行正文即回到片前。
- Inputs/outputs: 输入 = 背景包正文 + 仓库顶层目录的在场性；输出 = 三条新 `OK/FAIL` 判据行、
  67 例注入自检的末态、一次 RED/GREEN 对照（同一条命令、字节级还原、sha256-12 相同）。
- State and exception handling: 裸下限 `MIN_CITED_DIRS = 2` 挡「把引用删光让判定空集通过」；
  下限不满足时早退（`return c.finish()`），因为后面两条判定没有可比对的引用。
- Constraints and trade-offs: 只用标准库；判定形状取「同一行内出现该说法 + 该行引用了 `` `name/` ``」，
  不做小句切分。代价是正文里「已落地」与「尚未创建」不能写在同一行的两个目录上（今天这两行天然分开），
  收益是判据简单、不引入语法解析。另一条取舍：沙箱里只镜像顶层**目录名**、不复制内容，
  所以「目录在场」在副本里为真而「目录里的某个文件在场」仍为假——本片的判据只需要前者。
- AI recommendation and rationale: 只改正文不加判据是不够的——这条错误正文存在了四天没人发现，
  因为它没有任何门禁读；判据要落在「说法 ⇔ 磁盘事实」这种可判形状上，才会在下一次目录变化时响。
- User choice and custom additions: 用户要求一站到底、按片提交本地、不中途请示；未对本片提出额外定制。
- AI-autonomous decisions: ① 双向都判（存在 ⇔ 未被声称「尚未创建」），单向只覆盖今天这一半；
  ② 基线 tag 改成文件名派生（那张手写 `{路径: 名字}` 字典在加第 6 条基线时直接 KeyError，
  是仪器故障不是「用例通过」，顺手消除这个会漂移的地方）；③ 工作副本镜像顶层目录名而不是给沙箱硬编码 `frontend/`。
- User-confirmation decisions: 无新增需拍板项（不改产品口径，只把已有拍板写实）。
- Acceptance evidence: 块 01（守门末态逐字）、块 02（RED/GREEN 同命令对照 + sha）、块 03（67 例注入与 6 条基线）、
  块 04（`--tier core` 与新判据在 core 档里的读数）、块 05（两条被本片位移的行号指针校正）、
  块 06（改后「纯 Android」反查复算，用来纠正 Assumptions 里那句写早了的结论）。
- Facts: `frontend/` 与 `backend/` 的在场性由 `ls -d` 实测；背景包旧正文两条原样留在块 02 的 RED 里。
- Assumptions: 起初假设「纯 Android 原生」这句除背景包外没有别处重复——这个假设是错的，改正后的反查读数
  见 Documentation impact：`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md:12` 也有一句，但它由同文件
  `:29` 的〔2026-10-15 更新〕范围声明兜住（该文件只描述 Android 单机架构），在它的口径内不算过期。
- Unknowns: 无。
- Risks: 行内形状判定对「换一种说法」不设防（例如写成「还没建」），这类只能靠注入用例覆盖不到而承认；
  工作副本镜像目录名后，任何**新增**的顶层目录都会进入沙箱，若将来有守门按目录名分支，需要复查这条。
- Deferred: 不处理 `.codex/rules/**` 里同类「按目录现状写的句子」（没有一条是错的，实测见块 06 的反查）。
- Non-goals: 不把背景包改成穷尽清单（一页预算 60 行仍在守门里）；不动 `Docs/spec/**` 的历史口径。
- Skipped non-critical questions and reason: 未问用户「要不要连 44dp/Java-only 那条一起重写」——它们与实测一致。

## Goal

`.codex/context/project-brief.md` 关于 `frontend/` 与 `backend/` 的两句话与磁盘事实一致，
且 `python -X utf8 .codex/scripts/check_context_pack.py` 能在「把已存在的目录说成尚未创建」时转红、
在正文与磁盘一致时转绿。

## Scope

### In scope
- `.codex/context/project-brief.md`「当前阶段与技术栈」的形态行、「关键约束」的无远程服务端行。
- `.codex/scripts/check_context_pack.py`：`ABSENT_CLAIM` / `DIR_CITE_RE` / `MIN_CITED_DIRS` 三条常量与两段判定。
- `.codex/scripts/dev/checkers_selftest.py`：3 例 `context:*` 注入用例、`CHECK_CONTEXT` 升为第 6 条基线、
  基线 tag 改为文件名派生、`build_workspace()` 镜像顶层目录名。
- 被本片位移的两处行号指针校正（`ROADMAP.md` D5 处置行、片 024 的自检文件区间）。

### Out of scope
- 背景包的一页预算、术语表、Docs 索引子集三条既有判据的口径（一条未动）。
- `check_context_pack.py` 的 80 字抄袭判定阈值与屏蔽规则。
- Web 线自身的进度与 gap（另片）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 意图 = L0 现状陈述与磁盘一致，并让下一次目录变化时门禁先响。
  保留 = 一页预算（`measured=46/60`）、六节齐全、Docs 索引合法子集、术语表 ≥12 且每条指得到真实路径、
  抄袭判定与占位符判定，逐条在块 01 仍为 `OK`。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 = 背景包正文；
  消费者 = `check_context_pack.py:75-95`（新判定）、`checkers_selftest.py` 的 6 条基线（`CHECK_CONTEXT` 在列）、
  以及任何读 L0 的角色（`.codex/context/project-brief.md` 被 `AGENTS.md` 路由表第 46 行、
  `intent-capturer.md`、`spec-author.md`、`ROADMAP.md` 引用，反查读数见块 06）。
  守门本身早已在 `run_all_guards.py` 的 core 档注册，无需新增注册行。
- Native bridge or independent-client compatibility/update requirements: 不适用（无生产代码、无 API、无桥接字段）。

## Documentation impact record

- Changed runtime facts: 未改产品运行事实；改的是一份 L0 常驻文档里的**目录现状主张**，并给该主张加了门禁。
- Authoritative code/config/migration evidence: `ls -d frontend backend`（frontend 存在、backend 不存在）、
  `.codex/scripts/check_context_pack.py:38-41`（三条新常量 `ABSENT_CLAIM` / `DIR_CITE_RE` / `MIN_CITED_DIRS`）、
  `:75-95`（两条判定 + 一处早退）、
  `.codex/scripts/dev/checkers_selftest.py:49-71`（`CHECK_CONTEXT` 常量与基线元组；026 之后该元组已含第 7 条、027 含第 8 条、028 含第 9 条）、
  `:375-386`（3 例 `context:*`）、`:682-690`（顶层目录名镜像）、`:859-861`（tag 派生）。
  区间在片 026、片 027、片 028、片 029 之后各重测过一次：026 往同一个文件加了 19 例与 4 个常量、027 又加了 24 例与 4 个常量、
  028 加了 5 个常量与 14 例 `evals:*`、029 加了 4 个常量与 29 例 `keeper:*`，
  原引 `:328-339` / `:365-374` / `:537-541`、再到 `:342-351` / `:440-448` / `:615-618` 都在被引用当时是对的，之后成了活指针。
- Documentation surfaces checked: `AGENTS.md`（Treelearn 当前事实一节已写「`backend/` 目录尚未落地」，与新正文同向）、
  `.codex/ROADMAP.md`（B0 注记已写「`frontend/` 已落地」）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`、
  `.codex/agents/intent-capturer.md`、`.codex/agents/spec-author.md`、`.codex/context/domain-glossary.md`。
- Documentation files updated: `.codex/context/project-brief.md`（两行）、`.codex/ROADMAP.md`（D5 处置行的指针）、
  `.codex/tasks/T-2026-10-19-code-reviewer-role-and-review-template-024.md`（被本片位移的两处行号区间）。
- Producer/consumer/sibling/compatibility paths checked: 反查「纯 Android」在**改前**只命中背景包自己，
  改后按同一命令复算（`grep -rnI "纯 Android" .codex Docs AGENTS.md`，块 06）命中 7 行：片 025 卡片自己
  引用被删句的 6 行，加 `Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md:12`
  「终端 | 纯 Android 原生应用」——该文件 `:29` 已声明自己只覆盖 Android 单机架构、与 Web 规格冲突处以规格为准，
  所以那句在它的范围内成立，本片不连带改它（改它要动整份历史技术方案的口径，属 Out of scope 第 3 条）。
  结论：这一句改正不需要连带改规则，`AGENTS.md` 与 `.codex/rules/**` 零命中。
  反查 `frontend/`+`尚未创建` 同行写法：仅背景包第 23 行一处，已改。
- `documentation impact: none` reason and evidence, if applicable: 不适用。

## Acceptance criteria

- [x] `python -X utf8 .codex/scripts/check_context_pack.py` 退出码 0，且新增三条判据都打出 `OK` 行（块 01）。
- [x] 背景包「形态」行不再说「纯 Android 原生」，并写明 `frontend/` 是并行的 Web/H5 复刻线。
- [x] 背景包「无远程服务端」行只在 `backend/` 那一侧保留「尚未创建」。
- [x] 同一条命令在把旧现状主张摆回去时退出码 1，FAIL 标签是 `no dir is claimed \`尚未创建\` while present on disk`
      且 `stale=['frontend']`（块 02，字节级还原后 sha 与还原前相同）。
- [x] `checkers_selftest.py` 有 3 例 `context:*` 注入用例，两个方向 + 下限各一例，全部 `OK`（块 03）。
- [x] `check_context_pack.py` 进入 `BASELINES`（第 6 条），未注入副本上 `BASELINE OK rc=0`（块 03）。
- [x] 沙箱镜像顶层目录名后，67 例 + 6 基线整轮 `OVERALL: PASS`，且其余 5 条基线未因新判定转红（块 03）。
- [x] `python -X utf8 .codex/scripts/run_all_guards.py --tier core` 全绿（块 04）。
- [x] 被本片位移的两处行号指针（ROADMAP D5 处置行、片 024 自检区间）已按实测行号校正（块 05）。
- [x] `validate_tasks.py` `errors=0`（块 04）。

## TDD execution record

### RED — failing test first
- Test/scenario: 判据写完、背景包正文未改——即把 2026-10-15 那句「两目录尚未创建」留在 L0 上，
  跑 `check_context_pack.py`，看新判据是否认出「`frontend/` 已存在却被声称尚未创建」。
- Command: `python -X utf8 .codex/scripts/check_context_pack.py`
- Exit code: 1
- Observed failure: `FAIL no dir is claimed \`尚未创建\` while present on disk — claimed_absent=['backend', 'frontend'] stale=['frontend'] cited=['backend', 'frontend']`、
  `check_context_pack: 1 check(s) failed`。同轮另外两条新判据是绿的（`brief cites >= 2 top-level dirs — measured=2`、
  `every other cited dir exists on disk — dead=[]`），说明红来自这一条而不是来自其它判定被顺带改坏。
- Failure reason validated: 红的原因是被检正文过期，不是命令没跑起来——同一条命令在字节级还原回当前正文后
  立刻 `0 check(s) failed` / `rc=0`；驱动 `artifacts/tmp/b025-redgreen.py`（gitignore 内）用 raw bytes 存取，
  还原前后 `sha256-12=1112cb30d956` 相同。不用 `git checkout --` 撤注入：本工作树 `core.autocrlf=true`，
  checkout 会改写行尾而使「字节相同」无从证明（片 021 记的同一课）。

### GREEN — minimum implementation
- Implementation: 背景包两行改成当前态（形态行去掉「纯 Android 原生」、写明 `frontend/` 是并行的 Web/H5 复刻线；
  无远程服务端行只让 `backend/` 保留「尚未创建」）；`check_context_pack.py` 加三条常量与
  「说法 ⇔ 磁盘事实」的双向判定和一条裸下限；`checkers_selftest.py` 加 3 例 `context:*`、
  把该守门升为第 6 条基线、tag 改文件名派生、`build_workspace()` 镜像顶层目录名。
- Command: `python -X utf8 .codex/scripts/check_context_pack.py`
- Exit code: 0
- Passing evidence: `check_context_pack: 0 check(s) failed` / `EXITCODE=0`，其中三条新判据逐字为
  `OK brief cites >= 2 top-level dirs — measured=2 floor=2 cited=['backend', 'frontend']`、
  `OK no dir is claimed \`尚未创建\` while present on disk — claimed_absent=['backend'] stale=[]`、
  `OK every other cited dir exists on disk — dead=[] cited=['backend', 'frontend']`（块 01）。
- Regression evidence: `python -X utf8 .codex/scripts/dev/checkers_selftest.py` →
  `SELFTEST cases=67 ok=67 mismatch=0 harness_errors=0 skipped=0` / `OVERALL: PASS` / `EXITCODE=0`，
  6 条基线全 `BASELINE OK rc=0`（含新升的 `check_context_pack`），其余 64 例判据一字未动（块 03）；
  `--tier core` 整跑与 `validate_tasks` errors=0 见块 04。

### REFACTOR — optional
- Cleanup: ① 基线 tag 从手写 `{路径: 名字}` 字典改成 `os.path.splitext(basename)` 派生——加第 6 条基线时
  它 KeyError，整轮以 `OVERALL: INCONCLUSIVE` / `rc=3` 收场（fail-closed 是对的，但这张表本身就是会漏补的地方）。
  六个名字逐一核对：派生结果与原字典六项逐字相同，没有改变任何打印。
  ② `build_workspace()` 原本只复制 `.codex/`、`Docs/` 与 HEAD `AGENTS.md`，因此新判据在沙箱里把
  「`frontend/` 已落地」读成假——第一次跑就 `BASELINE FAIL check_context_pack rc=1`。
  改成镜像顶层目录名（跳过 `.` 开头，避免撞上 `.git`），只建空壳不复制内容。
  ③ 撤掉为「已落地」方向临时写的 `PRESENT_CLAIM` 判定：它与「每个被引用的目录都存在（除非该行说它尚未创建）」
  是同一条充要条件的两半，留一半就是留第二份实现。
- Regression evidence: 块 03 的 67 例必须包含 65/66/67 三条 `context:*`，且 6 条基线全 `OK`；
  块 04 的 core 档整跑覆盖同一命令的注册面。

## Implementation notes

- 判据的形状是「说法 ⇔ 磁盘事实」的双向条件，而不是关键词黑名单：
  `` `name/` `` 引用与 `尚未创建` 同行时，那个目录必须**不**存在；不同行时必须存在。
  单写一条只能挡住今天这一半。
- 裸下限 `MIN_CITED_DIRS = 2` 写在与两条集合推导都无关的字面量上，并由 `context:dir-cites-drained`
  （删掉两条含「逐屏对齐」的行，引用集合成空）证明它承重：抽干引用后上面两条自动成立。
- 沙箱镜像目录名这一条是新判据「能在注入自检里被复算」的前提。它改变的是仪器的世界观，不是被检正文，
  所以如实写在这里而不是验收方式里。
- 注入用例锚点全部按片 024 的教训取 `replace_all`：`context:stale-absent-claim` 的锚
  "`frontend/` 是并行的" 当前只有一处，用全替是为了让这个锚将来出现在第二处时用例仍然响，
  而不是静默落到不被判的那一行。

## Verification report

- Verdict: PASS（守门末态 + 一次同命令 RED/GREEN + 67 例注入含 6 基线 + core 档整跑 + 全局门禁；
  见块 01–05 与 Gaps）
- Evidence: `artifacts/evidence/T-2026-10-19-project-brief-dir-state-guard-025.txt`
  块 01（`check_context_pack.py` 末态逐字）、块 02（RED/GREEN 对照与 sha256-12）、
  块 03（67 例注入 + 6 条基线 + 三条 `context:*` 逐条）、块 04（`--tier core` 与 `validate_tasks`）、
  块 05（两处行号指针校正）与块 06（「纯 Android」反查在改前/改后的两次复算读数）。环境：Windows 10.0.26200 / x64 /
  Python（`sys.executable`，一律 `-X utf8`）/ Git Bash。捕获文件与驱动留在 gitignore 的 `artifacts/tmp/`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 本片**新增**判据，未删除或改宽任何一条。
  对既有判据唯一的行为改动是背景包正文（被检对象），不是断言本身。删掉的东西只有一处：
  `checkers_selftest.py` 里那张手写 `{路径: 名字}` tag 字典，等价的六个名字由文件名派生逐字给出（块 03 打印行可核）。
  `PRESENT_CLAIM` 这条中途写过又删的实现见 REFACTOR ③——它没有对应被删判据，充要条件的两半都还在。
- Gaps: ① 只认反引号里的 `` `name/` `` 一层目录写法；正文若用「前端目录还没建」这类无标记措辞，判据看不见。
  ② `backend/` 落地那天需要人改背景包，判据不会主动提醒（它此刻只判「说尚未创建 ⇒ 真不在场」这个方向）。
  ③ 沙箱只镜像目录名，任何按**目录内容**分支的将来判据都不能靠这套副本复算。
  ④ 无独立 verifier 轮次（用户要求一站到底；本片属 harness 仪器面，每条新判据都有对应注入用例）。
  ⑤ 卡片初稿把「已反查、无别处重复」写在改动之前，改后复算才发现 `Docs/05-technical-design` 还有一处命中：
  反查这类结论只有在被检文本进入末态后复算才算证据，写在计划里只是预测（块 06 是复算轮）。
- Risks: 镜像顶层目录名让沙箱更接近真仓库——如果某条判据其实是在依赖「副本里没有 android/」而转绿，
  它现在会第一次真判。今天 6 条基线全绿，说明没有这种耦合；但这条改动本身要在下一次加判据时被想起。

## Handoff notes

- Completed: 背景包两行改成当前态；`check_context_pack.py` 加双向判据与一条裸下限；
  `checkers_selftest.py` 加 3 例注入、把该守门升为第 6 条基线、tag 改派生、沙箱镜像顶层目录名；
  两处被位移的行号指针校正。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 回到 B4（`codebase-researcher`）——本片的起因就是为它读 L0 时发现正文过期；
  B4 的角色正文可以直接引用「背景包与磁盘一致」这条新事实。
- Watch-outs: 不要在别的文件里复制「`backend/` 尚未创建」这句话当作现状——`check_context_pack.py` 只判背景包，
  复制到别处就成了一条无人看守的门禁失效面。写别处时改成引用背景包。
