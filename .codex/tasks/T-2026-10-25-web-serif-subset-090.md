# Filename: T-2026-10-25-web-serif-subset-090.md
# Codex Harness Task: 让 Web／桌面的衬线字面随包（X1：子集内嵌 + Windows 兜底栈）

id: T-2026-10-25-090
title: Web／桌面衬线字面随包（X1）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 120
shared_code_touched: true
depends_on: [T-2026-10-25-ui-asset-parity-judgment-088.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 X1——Web 与 Windows 便携 exe 没有随包字体，`--serif` 首选族在目标机不存在时逐字降级，评审机装了 Noto Serif SC 所以截图看不出问题。
- Problem/background: `tokens.css` 的 `--serif` 首选 `'Noto Serif SC'`，但 `frontend/src` 里没有任何 `@font-face`；`frontend/dist-offline/index.html` 519,350 字节里 `@font-face` 出现 0 次。Windows 目标机大概率没有该族，品牌与主标题落到 generic serif（实际是宋体），而宋体并未被任何人声明为兜底——这是随机降级。
- Users/scenarios: 在没装 Noto Serif SC 的 Windows 上双击便携 exe 阅读的学习者；以及任何在 Web 面新增衬线标题的后续改动。
- Expected outcome: 两端首选族名的字节各自随包（Android 已有 `res/font/noto_serif_sc.otf`，Web 侧补子集），兜底栈里出现一个 Windows 真实存在的宋体族名，离线包内以 `data:` 内联、不需要联网，体积有机械上限。
- Core flow and reversibility: 加 `frontend/src/styles/fonts.css` 的一条 `@font-face`、子集资产与 OFL、`main.tsx` 的 import、`inline_offline.mjs` 的字体内联分支，并改 `tokens.json` 的 `serif` 后重生成 `tokens.css`。回退＝删这几个文件并还原 token；无数据迁移。
- Inputs/outputs: 输入是 `android/app/src/main/res/font/noto_serif_sc.otf`（24,427,224 B）与仓库可见文案的取字表；输出是 `frontend/src/fonts/noto-serif-sc-subset.woff`、`frontend/src/fonts/OFL.txt`、`frontend/src/styles/fonts.css`、内联后的 `dist-offline/index.html`。
- State and exception handling: 子集工具缺字时打 NOTE 并继续（控制符本来不入子集）；离线包内不许出现需要联网的 src——`check_offline_bundle.mjs` 只放行 `data:`，与 X1 的修法同向。
- Constraints and trade-offs: 不新增依赖（子集脚本只用已装工具链与 stdlib）；不手改 `tokens.css`（生成物）；兜底栈只补 Windows 真实存在的族名；体积有判据上限 6 MiB，不许把 23 MiB 全量塞进来充数。
- AI recommendation and rationale: 用户已拍板「子集内嵌 + 兜底栈」。内联是离线包唯一可行的随包方式（`file://` 下相对字体路径在单文件里不可寻址）。
- User-confirmation decisions: X1 选择「子集内嵌 + 兜底栈（推荐）」。
- AI-autonomous decisions: ① 子集取 GB2312 两级 + ASCII + 中英标点 + 仓库可见文案，共 7,628 码位；② 兜底补 `'SimSun'`——它正是 generic serif 当前会落到的字面，补进栈是把随机降级变成明确决定；③ `--kai` 与本片同批退役（它是 `app.css` 的假消费者，删表在 093）。
- Acceptance evidence: `serif-face.test.ts` 先红后绿；`check_ui_asset_parity.py` 的 A1／A2／A4／A5 由红转绿；构建产物 5,864,300 字节、内联字体 4,018,044 字节。
- Facts: `090-subset.txt`（取字 7,628、源实际 7,564、缺 64 个控制符、子集 4,018,044 字节 / 13,716 字形 / 覆盖 7,566 码位）；`090-build.txt`（`single=dist-offline/index.html bytes=5864300 violations=0`）；`090-parity-after.txt`（A1/A2/A4/A5 OK，A3 仍红属 093）。
- Assumptions: GB2312 常用字覆盖全部可见文案；专有名词若越界会落回兜底栈而不是丢字（`font-display: swap` 与逐字回退都保留）。
- Unknowns: 无改变范围的未知项。
- Risks: 离线包从 0.50 MiB 涨到 5.59 MiB；exe 预计 +2.8%～+4.7%（`size-baseline.txt`），需在 105 重打包后复核。
- Deferred: 拉丁专面子集优化（当前直接沿用源字体的拉丁字形）。
- Non-goals: 不引入远程字体 CDN；不改 Android 字体；不改字号。
- Skipped non-critical questions and reason: 未问「子集要不要再瘦」——A4 的上限已把失控面钉住，等 105 重打包读数再决定是否值得做。

## Goal

让 Web／桌面的衬线首选族名持有随包字节，并把 Windows 上的降级从随机变成明确声明的宋体兜底。

## Scope

### In scope
- 新增 `frontend/src/styles/fonts.css`、`frontend/src/fonts/noto-serif-sc-subset.woff`、`frontend/src/fonts/OFL.txt`、`.codex/scripts/dev/gen_serif_subset.py`。
- `frontend/src/main.tsx` 引 `fonts.css`；`frontend/scripts/inline_offline.mjs` 把 CSS `url()` 字体引用转成 `data:` base64 并复查没有相对 `url()` 残留。
- `Docs/04-ux-design/design-system/tokens.json` 的 `fontFamily.serif` 与 `$note`，重生成 `frontend/src/styles/tokens.css`。

### Out of scope
- `app.css` 的删除与 `--kai` 退役的落地（→093）；字号（→092）；判据注册（→104）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 字面从「宿主兜底」变成「随包」；必须保留 `font-display: swap`、正文 sans 不受影响、Android 的 `res/font/noto_serif_sc.otf` 仍在（A6 反删除式达标）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `frontend/src/styles/fonts.css`（唯一 `@font-face`）与 `tokens.json:fontFamily.serif`；消费者 `workspace.css`／`pages.css`／`forest.css` 里所有 `var(--serif)`；同族消费者 Android `res/font/noto_serif_sc.otf`；产物面 `dist-offline/index.html` 的 `@font-face` 块。
- Native bridge or independent-client compatibility/update requirements: 桌面壳消费同一份 `dist-offline/index.html`，字面自动随产物进入 exe，无需版本协商；`desktop/app/index.html` 的代次差在 105 统一刷新。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 「Web／exe 与 Android 用同一套衬线身份」从假设变成有字节、有判据的事实；Windows 兜底从 generic serif 变成显式 `'SimSun'`。
- Authoritative code/config/migration evidence: `frontend/src/styles/fonts.css`、`frontend/src/fonts/*`、`frontend/scripts/inline_offline.mjs`（ASSET_MIME 与残留学检查）、`tokens.json` 的 `fontFamily.serif`、`tokens.css`（生成物）。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`、`tokens.json` 的 `$note`、`frontend/scripts/check_offline_bundle.mjs` 头注释、根 `README.md`、`desktop/README.md`。
- Documentation files updated: `tokens.json` 的 `fontFamily.$note` 与 `serif.note`（原文「Web 侧仍由 --serif 回退栈与原型 CDN 承担」已改为子集内联的事实，并写明 SimSun 是明确决定）。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact。
- Superseded phrases/examples reverse-searched: `grep -rn "原型 CDN" Docs frontend` 只剩历史卡片与原型声明，产物面零命中。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 不改写 054（Android 字体）与 053（token 链）的历史记录。

## Acceptance criteria

- [x] `@font-face` 的族名与 `--serif` 首选一致，src 只指向包内字节，离线包里以 `data:` 出现。
- [x] 兜底栈含 Windows 真实存在的中文衬线（`SimSun`），且 generic serif 不再是唯一的降级终点。
- [x] 随包字体字节 ≤ 6 MiB（实测 4,018,044）。
- [x] 离线包仍然零外链：`check_offline_bundle.mjs` violations=0；单文件产物 5,864,300 字节。
- [x] 四方 token 链仍绿：`check_design_tokens.py` 0 failed。
- [x] 既有行为不回退：前端 30 文件 / 210 用例全绿；Android JVM 108 套 / 949 用例全绿。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/styles/serif-face.test.ts` 七条：族名一致、src 只允许包内字节、子集资产存在且 ≤6 MiB、OFL 随包、兜底栈含 Windows 宋体族、`fonts.css` 被 `main.tsx` 引进、离线打包链会把字体转成 `data:`。
- Command: `cd frontend && npx vitest run test/styles/serif-face.test.ts`
- Exit code: 1
- Observed failure: 7 failed / 7；首条报 `frontend/src/styles/fonts.css 不存在：衬线身份没有随包声明，Windows 目标机落到宋体`。
- Failure reason validated: RED 时资产与声明都不存在，七条各自命中的是同一个缺失事实的不同面（声明、字节、许可、兜底、构建链），不是同一断言重复七次；测试用 `readRequired` 把缺文件抛在单条用例里，避免整个 suite 崩掉冒充判据。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/090-red.txt`。

### GREEN — minimum implementation
- Implementation: 子集生成（`.codex/scripts/dev/gen_serif_subset.py`，7,628 码位取字、7,564 可得、64 个控制符 NOTE 跳过）；`fonts.css` 一条 `@font-face`；`inline_offline.mjs` 增加字体 MIME 表与 `url()` → `data:` 转换；`tokens.json` 的 `serif` 补 `'SimSun'` 并重生成 `tokens.css`。
- Command: `cd frontend && npx vitest run test/styles/serif-face.test.ts`
- Exit code: 0
- Passing evidence: 7 passed / 7（`090-green.txt`）；`090-subset.txt` EXITCODE=0；`090-build.txt` `single=dist-offline/index.html bytes=5864300 violations=0`；`090-parity-after.txt` A1／A2／A4／A5 OK。
- Regression evidence: 前端全套 30 文件 / 210 用例绿；`check_design_tokens.py` 0 failed；Android JVM 949 用例绿（字体资源未动）。

### REFACTOR — optional
- Cleanup: `inline_offline.mjs` 的 MIME 表抽成一个常量；残留学检查从「有没有相对 url」扩成「允许列表之外的一切 url」。
- Regression evidence: 复跑构建，`violations=0` 维持。

### LOOP — optional
- Command and rounds: 构建三轮（首轮缺字体 MIME、次轮残留相对 url、末轮通过）。
- Failure identity and repeat count: 两次不同缺陷各一次：无 MIME 表时把 woff 当未知类型跳过；CSS 改写后仍留 `url("../fonts/…")` 的相对路径。
- Stop condition hit: `inline ok` + `violations=0` + `bytes=5864300`。
- Handoff: 093 接着删 `app.css`；105 重打包后复核 exe 体积。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 子集只覆盖 GB2312 常用面 + ASCII + 中英标点 + 仓库可见文案；越界字逐字落回 `'SimSun'`，不是丢字。
- 内联是 `file://` 单文件产物下唯一可行的随包方式：相对 `url()` 在 `dist-offline/index.html` 里没有可寻址的目录。
- **一次仪器故障已登记**：`090-cdp-probe.txt` / `090-cdp-probe-run.txt` 两次 CDP 探针都以 `EXITCODE=3` 收场（一次是探针源码的正则字面量炸掉，一次是返回 `undefined`）。它属于「仪器没判」，不是「产物判红」，因此本片的字体渲染结论由 `090-green.txt` 的静态判据与构建产物承担，探针未成为任何结论的依据。
- A6 是反删除式达标：不许为了让「两端一致」去删 Android 字体。

## Verification report

- Verdict: PASS（静态资产面 + 构建面）
- Evidence: `serif-face.test.ts` 7/7；`check_ui_asset_parity.py` A1／A2／A4／A5 全 OK（`093-green.txt` 为当前态整跑）；构建 5,864,300 字节、字体 4,018,044 字节、violations=0；token 四方链 0 failed。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增七条断言；未删改既有断言。
- Gaps: ① 没有「字体在真实 Windows 上渲染」的截图证据——CDP 探针两次 FAULT，本片不以目测冒充实测；② 子集覆盖率只按仓库可见文案 + GB2312 论证，未做全量用户输入采样。
- Risks: 离线包与 exe 体积上涨；若后续文案引入 GB2312 外的字，会静默落到宋体（视觉上是两种衬线混排）。

## Handoff notes

- Completed: 子集与许可随包、`@font-face` 声明、内联链、兜底栈、token 重生成，七条判据转绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 093 删 `app.css` 让 A3 转绿；105 重打包 exe 并复核体积。
- Watch-outs: ① 改 `serif` 值必须走生成器，不许手改 `tokens.css`；② 别为省体积把子集换成 CDN；③ 105 的 exe 读数要拿 `size-baseline.txt` 的 110,493,305 字节做对照。
