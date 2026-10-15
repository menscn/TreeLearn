# Treelearn Harness Task: CSS token 生成链与前端裸色值守门

id: T-2026-10-15-004
title: CSS token 生成链与前端裸色值守门
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-15-web-h5-android-parity-requirement-001.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 466
shared_code_touched: false
depends_on: [T-2026-10-15-web-h5-android-parity-requirement-001.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 设计数值目前有三个消费者（原型 `:root`、`tokens.json`、`android/.../colors.xml`），由 `check_design_tokens.py` 做三方一致守门（实测 44 token，全绿）。Web 线是第 4 个消费者。若 CSS 靠人手抄，旧线病因（设计值出现第二份实现）会在第一天复活。
- Users/scenarios: 阶段 2 建 `frontend/` 骨架、阶段 3–6 逐屏还原的实现者；复核者需要能一眼看出某个色值是从 token 来的还是手搓的。
- Expected outcome: CSS 变量文件是生成物，可重复生成并做漂移校验；`frontend/` 出现裸 hex 即被守门判 FAIL，且该判据有正反双向自检托底。
- Core flow and reversibility: `tokens.json` → 生成 `frontend/src/styles/tokens.css` → 守门比对（含「重新生成后与已提交内容必须逐字节相同」）→ 裸色值守门扫 `frontend/src/**`。纯工具片，可整片回退。
- Inputs/outputs: 输入 `Docs/04-ux-design/design-system/tokens.json`；输出 CSS 文件、守门判据行、`--selftest` 结论。
- State and exception handling: `frontend/` 尚不存在时裸色守门打印 `SKIPPED` 并说明原因，从用例总数扣除，**不得计为 PASS**；解析出的 token 数低于阈值（沿用现有 `MIN_TOKENS`）判 FAIL（解析退化兜底）。
- Constraints and trade-offs: 不引入构建工具插件，生成与校验都用 Python 标准库；权威顺序固定为原型 `:root` → `tokens.json` → CSS，CSS 反向修改一律视为漂移。
- AI recommendation and rationale: 用「重新生成 + 逐字节比对」而不是「读一遍看像不像」来判漂移，因为手抄的错法无穷，而字节相同是唯一可机械裁决的形式。
- User choice and custom additions: 用户批准采用 Harness 并把该项列入阶段 0。
- AI-autonomous decisions: CSS 变量命名沿用 `--<token>` 原名不重命名；文件头写明来源与生成时间戳之外的确定性内容（不含时间戳，避免每次生成都算漂移）。
- User-confirmation decisions: 无新增待确认项。
- Acceptance evidence: `check_design_tokens.py --selftest` 通过的新增用例清单；对真实仓库运行四方一致退出码 0；把 CSS 中一个值改掉一个字符后守门必须转红。
- Facts: 现状实测 `python .trae/scripts/dev/check_design_tokens.py` 退出码 0、44 token、colors.xml 16 资源；`--selftest` 现有 10 例全绿；`tokens.json` 的 `$meta.source_of_truth` 指向原型 v1 `:root`；`paper-rule` 的 note 记录了 Android `PaperRules.RULE_ALPHA` 的同步史；`fontFamily.$note` 记录 Android 实际落到系统 sans-serif。
- Assumptions: CSS 需要全部 44 个 token（含字体族与非颜色值），而 `colors.xml` 只承载不透明 hex 子集——沿用现有子集规则，不新造第二套映射表。
- Unknowns: 字体族 token 在 Web 侧的处理（原型用 CDN 字体，规格禁止 CDN）——归阶段 2/3，不在本片解决，但守门不得因此放行裸 font-family 字面量以外的值。
- Risks: 守门口径与既有三方守门分叉成两条规则——因此本片只扩展同一个脚本，不新建平行判据。
- Deferred: 前端工程骨架（阶段 2）、逐屏 token 覆盖度台账（阶段 3）。
- Non-goals: 不做设计系统改版、不新增 token、不改 `colors.xml` 数值。
- Skipped non-critical questions and reason: 变量命名风格沿用原型原名，无需用户裁决。

## Goal

`frontend/src/styles/tokens.css` 成为只能生成、不能手抄的产物，且 `frontend/` 内出现裸 hex 会被带正反自检的守门判 FAIL。

## Scope

### In scope
- 新增生成器 `.trae/scripts/dev/gen_design_tokens_css.py`（确定性输出，含来源与内容哈希）。
- 扩展 `.trae/scripts/dev/check_design_tokens.py`：由三方一致改为四方一致（新增生成 CSS 消费者 + 「重新生成必须逐字节相同」的漂移校验），并新增 `frontend/src/**` 裸 hex 扫描。
- 为每条新检测分支补正反双向 `--selftest` 用例（合成临时目录，不动工作区）。

### Out of scope
- 建 `frontend/` 工程本体、Vite/pnpm 配置、逐屏还原。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 现有三方一致判据与 10 例自检结论必须保持不变（同一份真实文件仍判 0）；新增只是第 4 个消费者与 `frontend/` 扫描。
- Producer/consumer/sibling entrypoints and platform branches: 生产者 `tokens.json`；消费者原型、`colors.xml`、Figma 变量、新 CSS；`android/app/src/main/res/values/colors.xml` 与 `PaperRules` 的同步注记不受影响。姊妹入口 `.trae/scripts/dev/checkers_selftest.py` 在副本上注入违规，必须确认扩展后仍绿。
- Native bridge or independent-client compatibility/update requirements: 无桥接；独立客户端 = 未来的 `frontend/`，其约束正是本片建立的裸色值守门。
- Behavior-neutral non-applicability reason, if applicable: 不适用——本片新增可执行判据，属行为变更。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 设计 token 的消费者数量由 3 变为 4；新增一条前端守门。
- Authoritative code/config/migration evidence: 生成器与扩展后的守门脚本本身、其 `--selftest` 输出。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`（token 链说明）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §1 权威链、`.trae/rules/ux-design.md`、`AGENTS.md` 工具段（已列 `check_design_tokens.py`）。
- Documentation files updated: 实测 10 个面逐字回写（GREEN 块 25 的改动面捕获与块 28 的当前态/带日期分界判定为证）：`Docs/04-ux-design/design-system/README.md`（第 4 行生成物 + 四方一致性 + 2026-10-15 变更记录）、`Docs/04-ux-design/design-system/tokens.json`（`$meta.consumers`/`generator`/`note`）、`.trae/rules/ux-design.md`（设计事实源与固定顺序）、`.trae/rules/frontend.md`（新增「设计数值与 CSS 变量」节）、`README.md` 与 `README_EN.md` 的「设计协作 / Design workflow」守门命令行（两处不写行号：这两行会被后续编辑位移，按内容锚点定位）、`.trae/ROADMAP.md:36`、`AGENTS.md` 工具与参考段（新增一条生成链 bullet，纯新增不改写既有条目）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §1 权威链、`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.1.html`（纪律注释加 2026-10-15 指向，`:root` 区块未动）。带日期的 `Docs/04-ux-design/知芽-Figma新稿迁移方案-V1.0.md` 与 `Docs/spec/SPEC-2026-09-22-figma-ia-migration.md` 只追加指向，原结论逐字保留。
- Producer/consumer/sibling/compatibility paths checked: 反向搜索「三方一致」措辞与 `tokens.css` 既有引用，命中处必须与新口径一致。
- Superseded phrases/examples reverse-searched: 「三处各自手写必然漂移」「唯一消费者是 Figma 与 colors.xml」类表述。
- documentation impact: none reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 带日期的 Figma 迁移稿只加指向当前权威链接，不改写当年结论。

## Acceptance criteria

- [x] 期望行为：`python .trae/scripts/dev/check_design_tokens.py` 退出码 0 且判据行显式列出四个消费者与各自数量。
- [ ] 保持行为：既有 10 例 `--selftest` 全部仍符合预期；`checkers_selftest.py` 退出码 0；未新增第三方依赖。（前、后两半成立，**中间一半不成立**：GREEN 块 24 实测 `checkers_selftest.py rc=3`，3 条 FAIL 全在姊妹守门 `check_roadmap_and_routing.py` 对 `AGENTS.md` 的判定上，且「references to this slice=0」——本片从未被它引用。该缺口属切片 002 期遗留，见 Verification report 与 Handoff，不替它打勾。）
- [x] 相邻失败：把生成 CSS 的任一值改动一个字符必须判 FAIL；删除该文件必须判 FAIL 而不是 SKIP；`frontend/` 不存在时裸色扫描打印 `SKIPPED` 且不计入 PASS 用例数。
- [x] 受影响入口：`android/.../colors.xml` 子集规则未变（16 资源仍全绿）；文档中「三方一致」表述已更新。
- [x] 证据：`--selftest` 新增用例逐条命名，且给出「同一份内容以两种形态喂入必须等价」的断言（避免探针形状 ≠ 生产形状）。

## TDD execution record

### RED — failing test first
- Test/scenario: 在 `check_design_tokens.py --selftest` 里先加「第 4 消费者漂移必须 FAIL」「裸 hex 必须 FAIL」「文件缺失必须 FAIL」三例。
- Command: python .trae/scripts/dev/check_design_tokens.py --selftest
- Exit code: 1
- Observed failure: TypeError compare() takes 3 positional arguments but 4 were given（RED 证据块 07，`artifacts/evidence/T-2026-10-15-004-red.txt:53-71`）——测试先行的那份文件已按四方签名调用 `compare(root, tokens, colors, css)`，而切片前的 `compare` 只收三个参数。
- Failure reason validated: 成立且不止一条因：块 08 的隔离探针在 HEAD 源码上逐个查新符号，`missing=['render_css','scan_bare_hex','bare_hex_checks','regeneration_problems']`（MISSING=4）、`HEAD compare arity=3`，所以红的是「判据所依赖的能力当时不存在」，不是笔误或路径问题；块 06 同时证明这份 RED 文件 = HEAD 原文 + 新增判定块（`added=51 removed=0`，difflib 逐行比对），基线没有被手改；块 09 反过来证明旧守门在生成 CSS 已落盘时仍打 `EXITCODE=0` 且输出 0 次提到 `tokens.css`——即旧判据对新消费者完全失明，这正是本片要消除的缺陷。

### GREEN — minimum implementation
- Implementation: 加生成器 `.trae/scripts/dev/gen_design_tokens_css.py`（确定性输出 + `--check` 漂移判定 + 13 例正反自检），并把 `check_design_tokens.py` 由三方扩为四方比对、新增 `frontend/src/**` 裸 hex 原文扫描与 SKIPPED 计数分层。
- Command: python .trae/scripts/dev/check_design_tokens.py --selftest
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-2026-10-15-004-green.txt`（31 个判定块全部 want=got，`unexpected count = 0`，`OVERALL_EXITCODE=0`）；块 03 打印 `selftest judgement cases executed = 41`、`0 check(s) failed`，块 04 打印 `floor literal=41 executed=41 equal=True`，块 01/02 给生成器同一套结论（13 例）。
- Regression evidence: 块 09 `keep_behaviour.py` 在 `mktemp` 副本里跑 HEAD 版守门并与新版逐标签比对，打印 `pre-slice OK labels=10 new OK labels=42 not preserved=0`（基线 10 来自 RED 块 10 的 `pre-slice selftest: rc=0 ok=10 fail=0`）；这 42 与块 04 的 `floor literal=41 executed=41` 不是矛盾：41 是 `_case(...)` 计数，第 42 条标签正是那条计数用例本身，它在 `c.check` 之后打印而刻意不自计（`.trae/scripts/dev/check_design_tokens.py:436`）。块 23 单验 `colors.xml` 子集规则未变（`resources=16 tokens=44 dupes=[]`）；块 12 在真实仓库复跑四方判定回绿（`measured_prototype=44 measured_tokens_json=44 measured_resources=16 measured_css=44`）；块 27 `validate_tasks.py` 本片 `PASS count=1 WARNING count=1 (size band=1, other=0)`，仓库总数由该块**打印而不比对**（当前读数 `files=70 errors=16 warnings=11`）；回读 `grep -a "this task\|repo baseline\|repo-wide readings" artifacts/evidence/T-2026-10-15-004-green.txt`。

【块 27 判据改写补记 2026-10-15】首版 GREEN 记录写的是「仓库基线 `files=69 errors=16 warnings=10`，且 `warnings` 由 9 变 10 是 E1 回写的直接后果」，那是当时的时点读数，块 27 曾把 `(errors, warnings) == (16, 10)` 当成对 004 的判据。该判据随后被自己抓住：把台账生成器从 005 拆成独立片 007 时，007 在 `estimated_production_lines` 里申报了自己的判定值 277，仓库 `warnings` 因此 10 → 11、`files` 69 → 70，而 004 一行代码未动——块 27 却对 004 判 FAIL。这与 E1 同根（把别人的 paperwork 读成自己的结论），只是方向相反：E1 是申报值藏起来，这里是总数被别片加上去。现块 27 只判 004 拥有的三条逐任务行（恰好一条 PASS、恰好一条规模带 WARNING、其它 WARNING 为 0），总数改为打印，SUMMARY 行解析不出仍判 FAIL（「读不到」与「读到了且没问题」是两种结论）。块 29 对全部被判文件与全部回写文件做判定前后指纹一致比对。

【块 31 补记 2026-10-15】「把申报值退回去，那条规模带警告会不会跟着消失」这一问原先只是口头推导，现在由 GREEN 块 31 实测：在 `.trae/tasks` 的**副本**上跑 `validate_tasks.py`（真实工作树按字节只读，判定前后各取一次 sha256 比对，因此不适用注入型夹具那套还原约束），把本片申报值 466 分别退成 150 与顶成 601，实测读数 `MUT pristine declared=466 -> pass=1 size=1 other=0 err=0 repo errors=16 warnings=11`、`MUT undeclared declared=150 -> pass=1 size=0 ... warnings=10`、`MUT over-cap declared=601 -> pass=0 size=1 ... err=1 repo errors=17`。即：把申报值藏回 150，规模带警告当场消失（块 27 的 `size_warn != 1` 分支会因此转红，这条锁承重）；顶过 500 则在警告之上再叠一条 ERROR（需用户批准的规模例外）。该块同时把 `MIN_BLOCKS` 由 30 提到 31，判定数由产物自身核对（回读 `grep -a "blocks=\|MUT " artifacts/evidence/T-2026-10-15-004-green.txt`）。

### REFACTOR — optional
- Cleanup: 生成器与守门共用 `.trae/scripts/harness_check_lib.py` 的 `Check/ok/fail/finish`，退出码分层（0 判定通过 / 1 判据 FAIL / 2 用法错 / 3 仪器故障）由该库单点实现；SKIPPED 走独立计数而不是「打一条 OK」。
- Regression evidence: GREEN 块 22 断言 `judgement tally: OK=9 FAIL=0 SKIPPED=1` 与逐行打印的 `OK `/`FAIL ` 行数闭合（`OK+FAIL+SKIPPED=10 (match=True)`），并断言「一个 SKIPPED 分支同时产出 OK 行」即判 FAIL。

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 判据必须 fail-closed：解析器终态不干净即 FAIL；关键判据要有一条不依赖解析器的原文兜底扫描（裸 hex 用正则在原文上扫，不用 CSS AST）。
- 豁免只能来自被判对象自身的属性，不能来自它与允许名的距离（旧坑：±160 字符窗口豁免被打穿）。本片把这条落成可执行判据：豁免**只认路径** `frontend/src/styles/tokens.css`。GREEN 块 19 把已落盘生成物**逐字节复制**成 `frontend/src/styles/copy.css`，守门对该副本报出 16 条裸 hex FAIL——同一份内容、换个路径就判红，证明判决不来自内容。
- 生成文件不带时间戳，否则「重新生成必须逐字节相同」这条漂移校验永远红。头部改为携带**内容 sha256**，块 06 断言同一输入两次生成字节相同、且输出中不含任何日期形状。
- 「改任一值都必须判 FAIL」不许用抽一条代表全量：GREEN 块 13 在单字符变异之后，**逐条变异 `tokens.css` 的全部 44 条声明**，每条都要求 ① 守门落到 `rc=1` 这一层（不是 2/3 的仪器层）② 报告里点名被改的那个 token。实测 `declarations=44 faults=0 all judged and all attributed=True`（证据第 196 行）。
- 两种「第 4 消费者不可用」的形态给出两句不同判据文案，各自实测：文件不存在 → `tokens.css 没有可判内容（文件不存在，或存在但解析不出 :root 区块）`（块 14）；文件存在但解析出 0 条 → 先打 `OK tokens.css 存在且解析出 :root 区块 — measured=0`，再由下限分支判 `解析疑似退化` 并叠加逐字节漂移（块 15）。块 15 的期望文本按实测改为这两句，**没有**为凑绿而改判据。
- SKIPPED 与 PASS 必须能在计数上分开：`judgement tally: OK=9 FAIL=0 SKIPPED=1`，块 22 断言 `OK+FAIL+SKIPPED` 与逐行打印数闭合、且「一个 SKIPPED 分支同时产出 OK 行」即判 FAIL。
- 依赖：只有 Python 标准库（`io/os/re/hashlib/subprocess/tempfile/shutil/difflib/sys`），未新增第三方包；`--check` 与 `--selftest` 走同一个入口分发，未知参数一律 `rc=2`（生成器自检第 11 例）。
- 规模（规则要求判定值由仓库自带的 `count_production_lines.py` 给出；本片**显式偏离**该字面要求，因为该工具对 `.py` 会漏计，改取**较大的非空行读数**作判定值，偏离的登记见 Verification report 的 E2，工具读数与塌陷实测在同一条小节里继续并列。**测试/生成文件不计入**，这条排除是承重的，所以四种读数一并落盘，全部由块 26 的命令产出，不手抄）：
  - A 严格（新文件的生产部分 123 + 守门新增生产 184）= **307**
  - B 生成器整文件计入（282 + 184）= **466** ← **判定值取最大的合规读数 B**
  - C 仓库工具原样读数（277 + 91）= 368（**偏低**，见下条缺陷）
  - D 什么都不排除（282 + 290）= 572（唯一越过 500 的读数，而它把测试代码当生产）
  - A/B/C 都落在 200–500 的 verifier 警告带（`200..500 band: True`），结论对该选择不敏感；未申请规模例外。
- 工具缺陷登记（待用户裁决是否修）：`.trae/scripts/dev/count_production_lines.py` 对 `.py` 也跑 Java 注释剥离器，Python 字符串字面量里出现的 `/*` 与 `*/` 会把两者之间的**真实代码**整段吃掉——守门新增面实测 `tool=91` vs 非空生产行 `184`，方向是**漏计**。本任务因此改用「安全（较大）读数」并在证据里显式声明该塌陷（`COLLAPSE DETECTED=True`）。没有顺手改这个工具：它是所有历史任务判定值的来源，改它会让既有片子的数字全部失去可比性。
- 文档回写的口径与实测：当前态声明里的「三方」由 RED 实测的 **15 处**（8 个面）回写为四方，GREEN 块 28 实测**当前态命中 = 0**；带日期的记录按「不改写当年结论、只加指向」处理，实测**保留 9 处**，并要求变更记录小节之外每条带日期的命中都必须自带 `2026-10-15` 指向（实测 `= 0` 条未指向）。
- 回写过程中查出的既有过期（不属本片范围，已就地标注待拍板）：`design-system/README.md` 第 14 行那句「v1 与 V1.1 的 `:root` 区块 md5 同为 `87d3f40f…`」自 2026-09-28 起已不成立——实测 v1 该区块 `678dab85b8055b355a2e65137d60feee`（44 token，含 `--yellow-highlight`），V1.1 仍 `87d3f40f…`（44 token，含已退役的 `--green-bubble`、不含 `--yellow-highlight`）。两边都 44 只是巧合。已按「带日期只加指向」写入该行的 2026-10-15 补充，并把两个 md5 交给块 28 机械复算（`match=True` ×2）；**V1.1 要同步还是退役属设计侧决定**，本片不动它的数值。
- `.workbuddy/memory/MEMORY.md` 里也有一句「三方一致性」，实测该文件未被 git 跟踪（`tracked=False`），属外部工具自己的记忆，不属本仓库文档面——块 28 把它作为 OUT OF SCOPE **打印出来**而不是静默略过。

## Verification report

- Verdict: PARTIAL（round 1，`artifacts/tmp/_verify004/report.md` 末行 `VERDICT: PARTIAL`）：复核者独立复跑 10 项职责并**全部确证**——生成器 13 例与守门 41 例正反自检（它自己跑出的读数见 `artifacts/tmp/_verify004/report.md:34,41`：四方计数逐条复现为「原型 44 / tokens.json 44 / colors.xml 16 / tokens.css 44」，总判定行 `OK=9 FAIL=0 SKIPPED=1`）、19 例变异电池、44 条声明全量扫描、文档回写（当前态「三方」15→0、带日期保留 9 处、未指向 0 条）、`v1.1.html` 只在 `:root` 之外加 2 行且第 1–437 行逐字未变、纯标准库、`android/` + Kotlin + manifest + secrets 全干净、RED/GREEN 五键逐字相等、`validate_tasks.py` 的 004 行 PASS 且基线未变。它同意 PARTIAL 的判决，理由与本片自述同一条：验收第 2 条里「`checkers_selftest.py` 退出码 0」实测 `rc=3`，三条 FAIL 全在姊妹守门对 `AGENTS.md` 的形状判定上（属切片 002 的既有债务，见下条与本节 Blocker；本片回写时直跑 `python -X utf8 .trae/scripts/check_roadmap_and_routing.py` 复现为 `rc=1` + 3 条 FAIL：删除行判定 `measured_deletions=1`、`adds >= 4 routing rows — measured=0` 与其后续覆盖判定 `missing=` 四项）。此外 round 1 抓出两条 minor 的规模申报缺陷 E1/E2（登记在下一条，均未闭合）。【回写时补记 2026-10-15：该句是 round 1 时点的登记，不回改；现状是 E1 已按实测闭合、E2 仍待 round 2 确认。】
- Evidence: `artifacts/evidence/T-2026-10-15-004-red.txt`（13 块，`unexpected count = 0`，`OVERALL_EXITCODE=0`）与 `artifacts/evidence/T-2026-10-15-004-green.txt`（30 块，`blocks=30 expectation_entries=30 floor=30`，`unexpected count = 0`，`OVERALL_EXITCODE=0`，末行由 UTF-8 闸门打印 `DECODE ok file bytes=N`——按本仓库「指纹类值只写复算命令不写值」的做法，N 不抄进正文，回读 `tail -1 artifacts/evidence/T-2026-10-15-004-green.txt`；E1 回写后该文件重生成过一次，字节数与块 27 的行位置都随申报值变化，任何沿用旧读数的引用都会当场过期）；两份都由生成它们的运行自己过 UTF-8 闸门（块 13 / 块 30），退出码由 want/got 期望表派生而非硬写 0。
- PARTIAL 的唯一原因：验收第 2 条的中间子句「`checkers_selftest.py` 退出码 0」实测为 **rc=3（INCONCLUSIVE 仪器层）**。三条 FAIL 全部落在姊妹守门 `check_roadmap_and_routing.py` 对 `AGENTS.md` 的判定：① `AGENTS.md change contains no deletion line — measured_deletions=1`，而那一条「删除」实为**就地追加**（HEAD 530 字符 → 工作树 1022 字符，前 530 字符逐字相同；是失败案例库自己「并入本条、不另开条目」的要求与行级判据的冲突）；② ③ `adds >= 4 routing rows — measured=0` 与其后续覆盖判定——该断言量的是**未提交增量**的形状，路由行一旦提交，工作树 diff 里就不再有「新增行」，判据变成不可满足。两条都早于本片、与本守门无引用关系（`references to this slice=0`），因此不把它记成本片已达成。【回写时复跑 2026-10-15：直跑该守门确为上面 3 条（`rc=1`）；但同一条守门在 `checkers_selftest.py` 的暂存工作副本里只打印 2 条——第 ① 条删除判定不响，因为副本里 `AGENTS.md` 处于已 `git add` 的暂存态，而该判据读的是不带 `--cached` 的工作树 diff。也就是说第 ② 类「判据依赖未提交增量形状」的缺陷不止影响路由行断言，删除断言同样会随暂存态消失；round 2 要把这两处一起核对，本片不据此改动任何判据。】
- 独立复核 round 1（2026-10-15，`artifacts/tmp/_verify004/report.md`）抓出两条缺陷，均 **minor**、均不翻转任何验收勾，但都**尚未闭合**，因此不按「已处置」登记：
  - **E1（申报值把警告带状态从自动门禁里藏起来，round 1 登记为 OPEN → 2026-10-15 本轮回写后 CLOSED）**：front matter 第 14 行 `estimated_production_lines: 150` 低于本片自己声明的判定值 466（第 119 行）。`validate_tasks.py` 只读该字段，所以它对 004 行不打印规模 WARNING——而第 122 行明确写着 466 落在 200–500 警告带（`200..500 band: True`），于是这条状态对门禁不可见（复核者的复算：把该字段填成 466 后同一命令即新增 `WARNING ... exceeds soft limit 200`，仍属 PASS 级）。处置动作是**把该字段改为 466**，但它必须与基线回读同批做：仓库 `SUMMARY files=69 errors=16 warnings=9` 里的 `warnings=9` 正被本片的期望值、002/003 的复核报告逐字引用，改一处会让另几处当场过期，因此排在 003 第 5 轮复核结束之后，并与引用该基线的文本一起重读。本片不用「事后追认」把 E1 记成已修。
    - **处置（2026-10-15，与 GREEN 重生成同批完成）**：前置条件已成立——003 的第 5 轮（`artifacts/tmp/_verify003e/report.md`，判 `PARTIAL`，抓出 E1 那条自述计数）与第 6 轮（`artifacts/tmp/_verify003f/report.md`，判 `PASS`、Defects 段为空）都跑完了，登记在此不回改。做了四件事：① front matter 该字段改为 466；② `artifacts/tmp/gen-004-green.sh` 块 27 的判据从「比较 `mine[0]` 这一行」改成**按前缀分组计数**（`PASS count` / `WARNING count` / `size band` / `other`），因为申报变诚实之后，`validate_tasks.py` 对 004 输出的第一行变成了 WARNING 而不是 PASS——旧判据会红在「这一行不是 PASS」上，而那是判据自己过时，不是契约变坏；同一条块另加仓库基线必须恰为 `errors=16 warnings=10` 的判定（`errors` 不得新增，`warnings` 从 9 到 10 正是本片声明规模带所**新增的那一条**）；③ GREEN 证据重生成一次，`unexpected count = 0`、`OVERALL_EXITCODE=0`、`blocks=30 expectation_entries=30 floor=30`、块 27 `want=0 got=0`（因此上面的字节数改为命令回读）；【同一轮稍后再改，见下文「块 27 判据改写补记」与「块 31 补记」】②里那条**仓库总数恰为 (16, 10)** 的判定已删除——它把别片的 paperwork 算到本片头上：台账生成器拆成 007 并申报 277 后总数变 `files=70 errors=16 warnings=11`，本片一行未动却被判 FAIL；现块 27 只判本片拥有的三条逐任务行并打印总数。③的重生成读数现为 `blocks=31 expectation_entries=31 floor=31`（新增块 31 是对规模带锁的变异实测）。④ 引用旧基线的正文（第 96 行 Regression evidence 与本条）一起重读。**改动面无法用 git 证明**：该证据文件尚未入库（`git ls-files --error-unmatch` 报 error），改前那份字节已不可恢复，所以这里只报「本次编辑过的文件是 front matter、第 96 行、本条与 `artifacts/tmp/gen-004-green.sh` 的块 27 段」这一自述，不写「与改前相比只有 X 处不同」这类无法回读的差分结论——按本仓库既有做法记为方法论缺口，而不是当作「没看到差异所以不存在」。反查：`grep -rn --include=*.md 'warnings=9' .trae Docs README*.md` 在 `.trae/tasks/` 下现在只剩这一条登记引句，`artifacts/tmp/_verify*/` 里的同名串是**各轮当时的捕获**，按归档时序规则不回改。
  - **E2（把非工具读数说成工具语义，OPEN，措辞已按实况改但判定值不变）**：第 117 行原先写「判定值必须由 `count_production_lines.py` 的语义给出」，而判定值 B=466 是**按非空行手数的**，工具原样读数是 C=368（第 120 行）。复核者独立复算确认了塌陷的方向：它对守门**整文件**的读数是 `grep -cE '\S'` = 438 而 `count_production_lines.py` = 113（`artifacts/tmp/_verify004/report.md:158-159`）；本片块 26 对同一文件的**新增面**读数是 `tool(added-face)=91` 对 `added non-blank=290`，扣除该片内自检区后 `production=184`（`artifacts/evidence/T-2026-10-15-004-green.txt:386-394`）。四组判定值在该证据里逐条对得上：A=123+184=**307**、B=282+184=**466**、C=277+91=**368**、D=282+290=**572**，本片判定取 B，并判定本片的选择是**偏高的一侧、不会假通过**，属「已解释的偏离」而不是隐瞒。处置：第 117 行已改为如实表述——规则要求用仓库自带工具，本片因为该工具对 `.py` 的注释剥离会漏计而**取较大的非空行读数作判定值**，这是一条**对规则的显式偏离**，必须这样标注；工具读数 368 与塌陷实测继续并列留在同一小节，是否修工具属用户裁决（第 123 行）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除任何既有断言；块 09 用 `mktemp` 副本跑 HEAD 版并与新版逐标签比对，打印 `pre-slice OK labels=10 new OK labels=42 not preserved=0`，即「既有 10 例仍符合预期」是按标签原文与结论双重核对，不是靠人眼。唯一改名的判据是 `css-unparsable` 用例的**期望文本**（原写作 `没有可判内容`，实测该形态走 `解析疑似退化`），改的是探针不是判据；生产判据的 OK 标签「tokens.css 存在且解析出 :root 区块」保留原样，其 `measured=0` 由下限分支单独判红，一条规则仍只有一个判定点。
- Gaps: ① 字体族 token 在禁止 CDN 前提下的 Web 处置留给阶段 2/3；② `check_roadmap_and_routing.py` 的两处形状依赖（行级删除判定、未提交增量判定）需要有人重新定义它到底要守什么，属切片 002 治理面；③ V1.1 增量稿与 v1 的 `:root` 差额是否补齐属设计侧裁决；④ `count_production_lines.py` 的 `.py` 塌陷是否修（修了会让既有片子的判定值全部需要重算）。
- Risks: 与既有三方守门分叉为两条口径——已通过在**同一脚本**里扩展避免；新风险是「生成物只许由生成器改」这条纪律要靠人遵守，机器侧只有漂移检测（红），没有 pre-commit 强制，留待阶段 2 的工程骨架接钩子。

## Handoff notes

- Completed: 生成器 `gen_design_tokens_css.py`（确定性输出、`--check`、13 例正反自检）；`check_design_tokens.py` 由三方扩为四方并新增 `frontend/src/**` 裸 hex 原文扫描与 SKIPPED 分层（41 例正反自检，基线 10 例逐标签保留）；`frontend/src/styles/tokens.css` 已生成并入库；10 个文档面完成四方回写（当前态「三方」命中 15→0、带日期记录保留 9 处并逐条指向当前权威）；RED/GREEN 两份证据由本次运行自证（13 块与 30 块全 want=got）；44 条声明逐条变异全判全归因。
- In progress: 无——实现与自检已收口，独立复核 round 1 已跑完并判 `PARTIAL`（职责 10 项全部确证，判决理由与本片自述同一条）。状态**不得**进 `verified`，两个未闭合项：① 验收第 2 条的子句「`checkers_selftest.py` 退出码 0」实测 `rc=3`，需由用户或 002 责任片把 `check_roadmap_and_routing.py` 的两处形状依赖定下来，或由 verifier 认可「该子句属片外既有债务」；② E2 只改了措辞、判定值口径未变，需 round 2 确认表述与实测一致。E1 已于 2026-10-15 按其实测回写并闭合（见 Verification report 的 E1 处置子条）。
- Blocker: 姊妹守门 `checkers_selftest.py` 实测 `rc=3`，其 3 条 FAIL 全在 `AGENTS.md` 的行级删除判定与「未提交增量 ≥4 条路由行」判定上；本片无权改它的判据，也不得为了让它绿而回退别人的 `AGENTS.md` 记录。
- Unblock condition: `check_roadmap_and_routing.py` 的两条判据被重新定义为可提交后仍成立的形式（或由用户明确记为已接受的既有债务），使 `checkers_selftest.py` 不再落在 `rc=3`。
- Next steps: ① 已做完：独立 verifier round 1 只读复跑了四道门、19 例变异电池与 44 条声明全量扫描，全部确证（报告 `artifacts/tmp/_verify004/report.md`），并抓出 E1/E2 两条 minor 申报缺陷；E1 的回写（`estimated_production_lines` → 466、块 27 判据改为按前缀分组计数、GREEN 证据重生成、引用旧基线的正文同批重读）已于 2026-10-15 完成——round 2 的职责因此收窄为只读确认 E1 落地的**结果**（申报值与块 27 判据是否与实际读数一致；块 31 的三条变异读数是否成立——466 给出一条规模带警告、退成 150 时该警告当场消失、顶成 601 时在警告之上再叠一条 ERROR；并确认块 27 已不再比对仓库总数，那条总数不属于本片）以及 E2 的措辞已按实况落地，然后才谈 `verified`；② T-2026-10-15-006 接手两支 Node 工具，用 `screens.json.prototype` 段与同一套 0/1/2/3 分层；③ 阶段 2 骨架片依赖本片产物路径 `frontend/src/styles/tokens.css`，只能引用 `var(--token)`。
- Watch-outs: ① 不要为了「方便改一个色」手工编辑 `tokens.css`——漂移校验会红，这是设计意图；② 规模判定值走 B（466）而不是仓库工具的读数（368），因为该工具对 `.py` 的注释剥离**漏计**，看到 200/500 线附近先确认口径再看方向；③ 给仪器写负向块时只比退出码不够，必须另有一条输出见证（本片块 02/04 第一版就是被 `cases=` 前缀的正则写坏、红在「计数没被打印」而不是「计数不符」上）；④ 文档回写的「当前态 vs 带日期」分界要用 `## 变更记录` 小节和行内日期判定，整文件级判定会把 2026-09-28 那条 `三处一致性` 的历史记录误当成当前态承诺（本次实测命中过一次）。
