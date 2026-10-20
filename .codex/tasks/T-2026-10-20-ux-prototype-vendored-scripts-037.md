# Filename: T-2026-10-20-ux-prototype-vendored-scripts-037.md
# Codex Harness Task: 把原型的 React/Babel 从公网 CDN 收编进仓库，让「本地优先」的渲染判据断网也给得出判决（ROADMAP D14）

id: T-2026-10-20-037
title: 原型第三方脚本收编进 vendor/ 并配常驻完整性守门（D14 闭合）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-treelearn-process-migration-001.md
requirements_confirmed: true
generated_by: agent
owner: harness-dev
owner_lane: harness
stack: harness
estimated_production_lines: 190
shared_code_touched: false
depends_on: [T-2026-10-20-guard-diagnostics-timeout-lane-036.md, T-2026-10-20-pointer-liveness-standing-guard-035.md, T-2026-10-19-guard-runner-one-command-roster-023.md]
tdd_mode: required

## Requirement collaboration brief

Parent summary：`T-2026-10-18-treelearn-process-migration-001` 确认 `.codex/` 是 Treelearn 唯一 AI 研发流程入口，
并按 ROADMAP 逐片补齐流程资产。本片吃的是 ROADMAP 里唯一一条**需要用户裁定且已裁定**的欠账 D14：
原型的渲染依赖走公网 CDN，导致「本地优先」的 Web 仪器在一台代理挂掉的机器上必红或必故障。

- Problem/background: `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `<head>` 原先有三个
  `<script src>` 指向 `unpkg.com`（react / react-dom / @babel/standalone，各带 `integrity`），界面的 React+Babel
  是**在页面里现场编译**的，脚本没到就没有界面。片 036 用常驻 `.codex/scripts/dev/net_probe.py` 复算出
  本机系统代理 `127.0.0.1:7897` 一关，走注册表代理的三面探针一律 `WinError 10061`，于是
  `cdp_shot_selftest` 两条正控判红、`verify_1to1_selftest` 串行单跑 `rc=3` 且 5 条
  `INSTRUMENT FAULT: #root 是空的或不存在`。片 036 只是把 cdp 移出 core（止损），依赖本体还在。
- Users/scenarios: 要在断网或代理不通的机器上跑 Web 渲染判据的实现者；以及下一位改原型 `<head>` 的人
  （他需要一条仪器告诉他「这次改动把结构渲染重新钉到外网上了」）。
- Expected outcome: 三个第三方脚本按 sha384 校验后落到 `Docs/04-ux-design/vendor/`，原型改本地引用；
  新增常驻守门 `.codex/scripts/check_ux_vendor.py` 判「manifest ↔ 磁盘字节 ↔ 原型引用」三方一致，
  注册进 core；判定面不再需要外网，且「谁还在外网」由 manifest 记录而不是靠人记住。
- Core flow and reversibility: 只改 `<head>` 的三行引用、新增 vendor 目录与 manifest、新增一支守门并注册；
  不动原型的界面代码、token 与 `:root`。全部可逐文件回退（删 vendor/ 与守门、`<head>` 改回三行 CDN 即回到原状）。
- Inputs/outputs: 输入是 vendor 目录里的字节、manifest 声明与原型 HTML；输出是
  `check_ux_vendor: N check(s) failed` 判决行与 `--selftest` 的注入用例读数。
- State and exception handling: manifest 缺失/不可解析/条目为空 → 判红并指出文件；vendor 文件缺失或字节与
  manifest 不符 → 判红（这是被篡改或半个文件被覆盖的形状）；`<head>` 里出现任何 `http(s)://` 的
  `<script src>` → 判红（结构渲染重新依赖外网）；HTML 引用的 vendor 文件不在 manifest 里、或 manifest 声明
  的文件没被引用 → 判红（两侧各自漂移）。守门**不联网**：所有比对读本地字节。
- Constraints and trade-offs: 代价是仓库里多出约 4.3 MB 第三方字节（用户裁定时已明确接受这个量级）；
  边界是 **JS 收编、字体仍走公网**——两份 `cdn.jsdelivr.net` 与一份 `fonts.googleapis.com` 是装饰性依赖，
  缺了只影响字形不影响结构，本片不把它们搬进仓库（那是几百个 woff2 分片，量级与收益都不对），
  而是把它们**记进 manifest 并让守门盯着**：新增公网面不声明就红。
- AI recommendation and rationale: 按 D14 修法 ① 执行；SRI 校验放在收录当时（下载后算 sha384 与 HTML 里
  声明的 `integrity` 逐一比对，三个全等才落盘）。落盘后 `<script>` 标签**不带** `integrity`/`crossorigin`：
  实测 `Edg/155.0.4283.45` 在 `file://` 下对带这两个属性的本地脚本按 CORS 模式取子资源，直接拒绝执行，
  `#root` 停在 0（`artifacts/tmp/run362-verify1to1-after-vendor.txt`、`run363-cdpshot-after-vendor.txt`），
  去掉后同一仪器即能断网渲染（`artifacts/tmp/run364-cdpshot-no-sri.txt`）。字节身份因此从 HTML 移到
  manifest，由守门比对——校验位置变了，校验本身没少。
- User choice and custom additions: **用户 2026-10-20 裁定 D14 走 ①「收编进仓库」**（在「收编」与
  「保持 CDN + 钉 `direct://`」两案中选前者）。除此之外用户不在场，沿用「一站到底 + 常规技术决策直接执行」的授权。
- AI-autonomous decisions: ① 期望 sha384 的权威落点定为 vendor manifest（不由守门硬编码一份、也不抄回 HTML）；
  ② 字体留在公网但记入 manifest 并由守门做「未声明即红」的反向检查；③ 去掉 `integrity`/`crossorigin` 是
  实测驱动的必要改动，属 ① 的执行细节，不额外请示。
- User-confirmation decisions: D14 修法选型（①收编进仓库）。
- Acceptance evidence: 见 Acceptance criteria 每条后面的命令；末态两条整跑必须**串行且单独**取读数（D12③）。
- Facts: 三个文件的实测字节数 109,931 / 1,080,227 / 3,137,752，合计 4,327,910；`git check-ignore` 对
  `vendor/` 返回未忽略（退出码 1），所以它们会随本片进版本库。
- Assumptions: 无。
- Unknowns: D11③ 把 `cdp_shot_selftest` 移出 core 的理由当时写的就是「因为它要外网 CDN」；D14 闭合后
  这条理由是否还成立、要不要把它搬回 core，由本片末尾的实测读数决定，并在 Handoff notes 交代。
- Risks: 移除 `integrity` 后 HTML 侧不再自带完整性声明，完整性判断整体转移到 manifest+守门；若有人绕过
  守门直接改 vendor 文件，manifest 与字节会不一致 → 该形状正是注入用例之一。
- Deferred: 片 035 Gaps ①③④⑥、片 036 Gaps ①–⑥、C4 release-gate 与 D7①（需实机与用户在场）、
  Web 任务 #16–#21。字体自托管不在本片。
- Non-goals: 不给 `parity_cdp.mjs` 加 `--proxy-server`（那是被否决的 D14 修法 ②）；不改原型界面；
  不改任何一条既有守门的判据内容。
- Skipped non-critical questions and reason: 没问「vendor 用哪个子目录名」「manifest 用 JSON 还是 MD」，
  因为两者都不改变判定面形状，按仓库既有习惯（`Docs/04-ux-design/design-system/tokens.json` 同族）定即可。

## Goal

`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的结构渲染不再依赖任何公网脚本，且这件事由一条
注册在 core 档的只读守门判定：manifest、磁盘字节、HTML 引用三方不一致即红。

## Scope

### In scope
- 三个第三方脚本落 `Docs/04-ux-design/vendor/`（下载后按 sha384 与 HTML 原声明逐一比对才落盘）。
- `<head>` 改成本地引用；去掉在 `file://` 下挡住执行的 `integrity`/`crossorigin`，并写明为什么去掉。
- 新增 `Docs/04-ux-design/vendor/manifest.json`：脚本的 upstream/版本/integrity/字节 + 仍在外网的字体面 + 边界一句话。
- 新增 `.codex/scripts/check_ux_vendor.py`（判据 + `--selftest` 注入用例 + 裸下限常量），注册进 core 档。
- 过期叙述清理：`verify_1to1.mjs` 的成因注释与故障文案、`net_probe.py` 的默认目标与 docstring、
  `run_all_guards.py` 的 `external` 档说明与 cdp 条目注释。
- `context/source-of-truth.md` 增一条「原型渲染依赖」条目并同步覆盖面计数；`rules/ux-design.md` 记录这条边界。
- ROADMAP：D14 判据闭合、台账回写、`external` 档那句「因为 CDN」的归因改成本片实测的归因。

### Out of scope
- 字体自托管；给浏览器实例钉代理；原型界面与 token 的任何改动；`frontend/` 构建管线。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变的是原型 `<head>` 的三行脚本来源与标签属性；
  保留的是界面本身、`:root` token 块与全部 DOM 结构——`check_design_tokens.py` 判的正是后者，它必须仍绿。
  判定语义上，`page-not-rendered` 与 `#root 是空的或不存在` 这两条分支不删：它们仍要处理浏览器启动竞态，
  只是「外部脚本没到」这个成因在本机不再可达。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者＝原型 HTML 与 vendor 文件；
  消费者＝`.codex/scripts/dev/parity/verify_1to1.mjs`、`cdp_shot.mjs`、`cdp_shot_selftest.mjs`、
  `parity_cdp.mjs`（都按 `--screen` 档案加载同一个 HTML），以及 `dev/check_design_tokens.py`（读同一个 HTML 的
  `:root`）。新增守门是 manifest 与 vendor 目录的新消费者。Android 与 backend 无消费路径（原型只被 Web 仪器读）。
- Native bridge or independent-client compatibility/update requirements: 不适用——不改原生桥、不改数据、不改 API。

## Documentation impact record

- Changed runtime facts: 原型的渲染依赖从「三个 unpkg 脚本 + 两份公网字体」变为「三个仓库内本地脚本 +
  三份公网字体样式表面（装饰性）」；`external` 档的成立理由随之变化。
- Authoritative code/config/migration evidence: `Docs/04-ux-design/vendor/manifest.json`（字节身份与外网面）、
  `.codex/scripts/check_ux_vendor.py`（判定）、`run_all_guards.py` 注册表（档位）。
- Documentation surfaces checked: `AGENTS.md`、`.codex/rules/ux-design.md`、`.codex/rules/contracts.md`、
  `.codex/context/source-of-truth.md`、`.codex/context/project-brief.md`、`.codex/ROADMAP.md`、
  `.codex/skills/guard-runner/SKILL.md`、`Docs/04-ux-design/design-system/README.md`、`README.md`。
- Documentation files updated: ux-design 规则（边界一条）、source-of-truth（条目＋覆盖面）、ROADMAP（D14 闭合、
  台账、档位归因）、guard-runner SKILL（注册表条数只由 `--list` 打印，故只在散文命中的地方改）。
- Producer/consumer/sibling/compatibility paths checked: 上面 Behavior impact 列出的四个 Web 仪器与一支 token 守门，
  全部按改后 HTML 重跑读数。
- Superseded phrases/examples reverse-searched: 「走公网 unpkg」「脚本来自 unpkg CDN」「三个 `<script src>` 指向
  `unpkg.com`」「React/Babel 走 unpkg CDN」——逐条改或标注为登记时点历史读数。
- Historical/PRD handling: `.codex/tasks/**` 与 `artifacts/tmp/**` 里的旧读数（`rc=3`、`11m16.258s`、
  `failures=2`）保持原样，它们是登记时点证据；ROADMAP 里只在 D14 的处置节说明「此后失效」。

## Acceptance criteria

- [x] 原型 `<head>` 里没有任何 `http(s)://` 的 `<script src>`：`python -X utf8 .codex/scripts/check_ux_vendor.py` 判绿，
      且该条判据在 `--selftest` 里有「重新注入一个 unpkg `<script src>` 即红」的注入用例背书。
- [x] vendor 三字节的 sha384 与 manifest 声明全等、manifest 声明与 HTML 引用一一对应：同一支守门判绿；
      `--selftest` 里「删文件 / 翻一个字节 / 改 manifest 一个字符」三种注入各自判红。
- [x] 断网（本机代理 7897 不通）条件下渲染判据仍给得出判决：`node .codex/scripts/dev/parity/cdp_shot_selftest.mjs`
      与 `node .codex/scripts/dev/parity/verify_1to1_selftest.mjs` 各自单跑，读数里不出现
      `#root 是空的或不存在`；同场 `python -X utf8 .codex/scripts/dev/net_probe.py` 仍打印走代理 `10061`。
- [x] 新守门在 core 档：`python -X utf8 .codex/scripts/run_all_guards.py --list` 的条数与档位读数含 `check_ux_vendor`，
      且 `--only check_ux_vendor` 走绿。
- [x] 既有判据未被本片改坏：`--tier core` 与 `--tier precommit` 各**串行单独**整跑一轮并全绿
      （`fail=0 inconclusive=0 timeout=0`）；`check_design_tokens`、`check_source_of_truth`、
      `check_pointer_liveness` 三条在同一轮里判绿。
- [x] `check_pointer_liveness` 的活指针声称条数在注册表行号漂移后仍与实测相符：必要时跑
      `python -X utf8 .codex/scripts/dev/remap_pointers.py` 并复跑该守门。
- [x] 中文无乱码、UTF-8 读写：证据文件里 `?`/U+FFFD 计数为 0（本片证据脚本内同场检查）。

## TDD execution record

### RED — failing test first
- Test/scenario: 先让被测对象存在、判据的权威不在场——三个 vendor 文件已按 sha384 落盘、原型 `<head>` 已改成
  `./vendor/…` 本地引用，而 `Docs/04-ux-design/vendor/manifest.json` **尚未创建**。这时跑新写的
  `.codex/scripts/check_ux_vendor.py`：它必须先给不出绿，因为它判的是「manifest ↔ 磁盘字节 ↔ HTML 引用」
  三方一致，其中一方不在场时应当出声而不是默认通过。
- Command: `python -X utf8 .codex/scripts/check_ux_vendor.py`
- Exit code: 1
- Observed failure: 见证据块 01 —— `FAIL vendor-manifest-missing — path=Docs/04-ux-design/vendor/manifest.json`
  紧跟一行 `NOTE checks-skipped — manifest 读不出来，后面的比对没有对象（这一条不是绿）`，末行
  `check_ux_vendor: 1 check(s) failed`。
- Failure reason validated: 红的是「权威缺失」这条真实缺陷，不是判据拼错或路径写歪——同一轮里
  `OK prototype-missing` 说明原型路径解析正确、被测对象找得到；`--check` 里其余九条比对是**被跳过**而不是
  被判绿（那句 `这一条不是绿` 就是为区分这两种形状写的）。manifest 落盘后同一命令即 `0 check(s) failed`，
  排除「判据永远红」这种凑不出绿的写法。

### GREEN — minimum implementation
- Implementation: `Docs/04-ux-design/vendor/manifest.json`（schema `treelearn-ux-vendor/1`：三个脚本的
  upstream/版本/sha384/字节 + 四条仍在外网的公网面 + 边界一句话 + 一句为什么标签上不写 SRI）。
- Command: `python -X utf8 .codex/scripts/check_ux_vendor.py`
- Exit code: 0
- Passing evidence: 同一条命令从 `1 check(s) failed` 转为末行 `check_ux_vendor: 0 check(s) failed`，逐条 OK 里包含
  `no external script src — 外网脚本引用=[]`、`local script free of integrity/crossorigin — 带 SRI 属性的本地标签=[]`、
  `vendor dir has only declared files — 磁盘上有但没登记=[]`（证据块 02 前半）。
  自检电池 `python -X utf8 .codex/scripts/check_ux_vendor.py --selftest`（同一脚本的第二入口，不构成本片的
  第二条 RED 命令）读数 `BASELINE fixture checks_failed=0` 起头、末两行
  `SELFTEST cases=21 mismatch=0 expected=21 floor=21` + `OVERALL: PASS`（证据块 02 后半）。
- Regression evidence: 判据不是只有正例——21 条注入用例逐条按 `label in c.failures` 断言（不是按 stdout 子串，
  `Checks` 在 OK 与 FAIL 行打同一个标签，子串匹配会假通过）。证据块 02 里 CASE 01/02 把 `http(s)://` 与
  协议相对两种外部 `<script src>` 各注入一次，CASE 03/04 把 `integrity`/`crossorigin` 各注入一次，
  CASE 18 往 vendor 目录塞一个没登记的文件，CASE 19/20/21 分别改 schema、指错原型、删掉原型。
  既有仪器一字未改判据：`verify_1to1_selftest`、`cdp_shot_selftest` 两支在本机代理仍不通的条件下各自单跑给绿
  （证据块 03/04），读同一个 HTML `:root` 的 `check_design_tokens` 与上面两支同在 core 档整跑里判绿（证据块 06）。

### REFACTOR — optional
- Cleanup: 首版脚本里遗留的三处废码删掉（`…findall(html) if False else …` 的表达式残骸、
  `check_dir_extras` 收而不用的两个参数、一个 `_read_manifest` 占位别名与没用上的 `import urllib.parse`）；
  `judge()` 改成返回 `Checks` 对象、`run()` 只做 `judge(root).finish()`，注入用例因此能直接断言失败标签。
  两处用例形状问题在复跑中修掉：`MIN_SCRIPTS`/`MIN_REMOTE_FACES` 这类下限若先于成员判断返回，会让本应
  暴露的分支饿死（第 13 例 MISMATCH 的成因就是注入把 `remote` 删到低于下限，于是红的是下限而不是
  `remote face declared`）——改为保留四条声明面、只把其中一条的 URL 换成没登记的地址。
- Regression evidence: `--selftest` 的用例数下限 `MIN_SELFTEST_CASES = 21` 与末行 `expected=21 floor=21` 同源；
  注册表侧由 `check_run_all_guards` 电池作证（新守门两条入册后 `SELFTEST cases=33 … broken=0`）。

### LOOP — optional
- 一次真正的回路：`<head>` 加上 `integrity`/`crossorigin`（照抄原声明）之后，**同一条**渲染仪器反而连续三轮
  判不出界面——`run362`/`run363` 两次都是 `INSTRUMENT FAULT: #root 是空的或不存在（root=0 nodes=16
  ready=complete …，重载 1 次后仍空）`。第一反应是「vendoring 做坏了」，但 `nodes=16` 与正确的 `title`
  说明 HTML 载入了、脚本没执行，这与「脚本没到」是同一条故障路径的两种成因，读数分不开。
  把它分开的那一步是摘掉两个属性（`run364`，同一仪器即起来），而不是抬高等待上界或改判据。
  第二轮（`run367`/`run368`）在同一棵树上判绿，回路收敛；结论按「校验换位置、不删校验」写进 D14 处置栏 ②。
- 另有两处非绿各一次改动即消解，不进回路：`FileNotFoundError`（selftest 恢复循环在「删 vendor 文件」那一例
  之后无条件按原内容重写）与上面第 13 例的下限饿死。

## Implementation notes

- 收录过程（先校验后落盘）：`curl` 走注册表代理取不到 unpkg（退出码 35），改用显式禁用代理的
  `urllib` 直取三个包，逐个算 `sha384` 与 HTML 当时声明的 `integrity` base64 比对，三个全等才写入 vendor/。
- `integrity`/`crossorigin` 的取舍是实测推翻了「照抄原声明」这个默认：见 brief 的 AI recommendation。
  manifest 保留这两个值，是为了让「哪天浏览器修好了 `file://` 的 SRI 语义」这件事仍有一条可回读的记录。
- 守门放在 `.codex/scripts/` 而不是 `dev/`：它判的是设计事实源与仓库字节，不是开发期临时量测。
- 注入用例走 `harness_check_lib` 的 `root=` 逃生口，在临时复制的树里判，绝不改真树来凑红。

## Verification report

- Verdict: PASS（验收七条全部有当场读数；两条渲染仪器在「本机系统代理仍不通」的真实条件下给绿，
  不是靠网络状态恰好好。三轮 core 整跑连续全绿把 D11 原修法里那句「连续 ≥3 次」也一并兑现了。）
- Evidence: `artifacts/evidence/T-2026-10-20-ux-prototype-vendored-scripts-037.txt`
  （driver=`artifacts/tmp/b037-evidence.py`，只做拼接与现场补跑只读命令。块 00 注册表现场 `--list` 与 git 面；
  块 01 RED；块 02 GREEN 与注入电池；块 03/04 断网单跑的两支渲染仪器；块 05 三面探针复算；
  块 06 SRI 现象 A/B 三份（run362/363/364）；块 07 core 三轮 streak（run371–373）；块 08 precommit（run374）；
  块 09 末态确认轮（run375）；块 10 末态门禁批（run376：metrics 重生成、validate_tasks、四支文档守门、
  run_evals 与乱码扫描）。原始捕获 run362–run376 在 gitignore 的
  `artifacts/tmp/`，装配驱动逐条核对在场，缺任何一份就在表头点名。）
- 逐条验收覆盖：
  ① 无外网 `<script src>` — `OK no external script src — 外网脚本引用=[]`（run366）；
     注入 CASE 01（`https://unpkg.com/...`）与 CASE 02（协议相对 `//unpkg.com/...`）各自判红、
     标签都落在 `no external script src`。
  ② 字节三方一致 — run366 逐条 `OK vendor script digest/size:…` 三对六条全等（含
     `bytes=109931 / 1080227 / 3137752`）；「删文件」「翻一个字节」「改 manifest 一个字符」三例
     （CASE 06/07/08 族）各自判红且标签落点正确；CASE 18（vendor 目录塞未登记文件）判红。
  ③ 断网可判 — `run367`：`SELFTEST PASS cases=17 failures=0`、40s；`run368`：
     `SELFTEST PASS cases=40 cli=20 muts=18 failures=0`、214s，两份读数里都没有
     `#root 是空的或不存在`（那一条只出现在故意注入的 `title=桩` 反控里）；同场 `run369`：
     走注册表代理四个面一律 `URLError 10061`、显式禁用代理一律 `200`——机器确实出不去，仪器照样绿。
  ④ 注册在 core — `--list` 两条目在场（run371 同表）；`--only check_ux_vendor` 给
     `pass=2 fail=0` + `VERDICT: PASS (subset only — 2/45)` + 退出码 0。
  ⑤ 既有判据未坏 — core streak 三轮（run371/372/373，墙钟 334s/331s/329s）同为
     `pass=39 fail=0 inconclusive=0 fault=0 timeout=0`；`check_design_tokens`、`check_source_of_truth`、
     `check_pointer_liveness`（含 `citations` 复算）三条同轮 PASS；precommit 一轮
     `pass=6 fail=0`（run374，`line-budget` 对 vendor 三字节按 `Docs/` 排除规则不计行）。
     卡片散文与 status 翻转之后按片 036 同一姿势补了一轮**末态确认**（run375，core，
     `pass=39 fail=0`），prose-only 增量由它兜底。
  ⑥ 活指针 — 本片改 `run_all_guards.py` 使两条声称漂移，`remap_pointers.py` dry 跑
     `MOVED 声称=7 SAME 声称=0` 后 `--apply`（`EDITS applied=8`，含
     `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md:79` 的 `:147→:154`），
     复跑 `check_pointer_liveness: 0 check(s) failed`（streak 三轮里每轮都再复算一次）。
     复算器报的 `alias_unresolved=7` 沿用片 035 Gaps ⑥，本片未扩大。
  ⑦ 无乱码 — 证据装配脚本对全部捕获跑 U+FFFD 与 `？` 替换字符扫描，计数 0 才落盘。
- Metrics 教训（一次真实红，不是假设）：streak 之前那轮（run370）`metrics_report FAIL rc=1` 且
  `checkers_selftest` 连带 `OVERALL: INCONCLUSIVE rc=3`——新卡片让 `cards-by-file-date` 与
  `tasks-status-counts` 两个派生读数过期。处置是重跑 `metrics_report.py --write` 后整轮转绿；
  这条顺序（卡片改动 → 先重生成 metrics → 再取末态轮）写进 Handoff，status 翻 `verified` 后又补写了一次。
- Gaps: ① `Docs/04-ux-design/` 同目录另有三份 HTML（`…高保真原型-v1.1.html`、`…高亮批注交互Demo-V1.1.html`、
  `…设置页重设计-交互稿-V1.0.html`）仍走 unpkg CDN——没有任何常驻仪器读它们，本片不收；收编它们需要
  另立「哪些 HTML 算设计事实源」的判据面，与 D14 的仪器面是两个问题。② `dev/checkers_selftest.py` 的
  注入家族没有收录新守门：它按 checker 历史家族组织用例，而新守门自带 21 条注入且已注册进 core
  （`check_ux_vendor` + `check_ux_vendor --selftest` 两条目），再抄一份进去等于同一判据两个权威。
  ③ 字体四条公网面守的是「登记 ↔ HTML 引用」一致，不校验字体字节内容（守门不联网）；内容漂移要靠
  `dev/net_probe.py` 人工复算或未来另立判据。④ `verify_1to1.mjs` 的 boot-retry 语义（本地脚本没及时执行的
  启动竞态）只在 Windows + `Edg/155.0.4283.45` 一台机器上实测过。
- Risks: manifest 现在是字节身份唯一权威——绕过守门直接改 vendor 文件会红（这是想要的形状），但
  「先改字节后忘改 manifest」的路径要靠这条红引导，没有别处兜底。字体仍走公网：断网时结构渲染不受影响，
  字形回退为系统字体，属已声明的装饰性依赖。`integrity`/`crossorigin` 属性被移除的原因写在 manifest 的
  `integrity_note`，若未来浏览器修正 `file://` 的 SRI 语义，恢复与否以那份记录为起点再议。

## Handoff notes

- Blocker: 无。
- Watch-outs:
  - `external` 档现在是**空的，这是设计结果**：`--tier external` 选中零条 → `NO-SELECTION` + 退出码 3，
    是正常信号不是故障。别为了「看着有东西」往里塞条目，也别删档——「同一条目不许同时挂 external 与 core」
    这条结构规则还要它在场才有判的对象。
  - `cdp_shot_selftest` 已回 core：core 整跑墙钟从 ~2.5min 涨到 ~5.5min，收尾姿势照旧（串行、单独跑）。
    它 40s 出绿是健康读数；再见到它红，先看渲染仪器三件套的共性，别先怀疑注册表。
  - 改 vendor 的顺序固定：改字节 → 跑 `check_ux_vendor.py` 看哪条红 → 同步 manifest 的
    `digest` 与 `bytes` 两个键。反过来先改 manifest 会让「磁盘与声明谁先动」分不清。
  - 新增公网面（字体之外的任何 `<link>`/`<script>`）先登记进 manifest 的 `remote`，再写进 HTML；
    反过来守门即红。`<script src>` 的公网形式没有豁免通道，一条都不许。
  - 卡片增删或 status 翻转后先 `metrics_report.py --write` 再取整跑读数（run370 的教训，
    已存入项目 memory「任务卡改动后先重生成 metrics」）。
- Next steps: ① 片 035 Gaps ①③④⑥、片 036 Gaps ①–⑥ 仍无人认领；② C4 release-gate 与 D7① 建表缺陷
  需要实机与用户在场（真机验证按 `device-verify-install-r-only` 的规矩）；③ Web 任务 #16–#21；
  ④ Gap ① 那三份仍走 CDN 的 HTML 要不要收编，属设计事实源范围问题，等一次明确裁定再动。
