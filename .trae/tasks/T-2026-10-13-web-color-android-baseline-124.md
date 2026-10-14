id: T-2026-10-13-124
title: Web 颜色回到 Android 基线（作废纸上书房色板）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 129
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123` 记录用户 2026-10-13 00:18 裁决「回退纸上书房 + 逐屏重排，按 harness+goal 方式执行」。本任务是 W1 视觉基座的第一步。
- Problem/background: Web 持有独立色板（2026-10-12《纸上书房》），主色为鼠尾草绿 `#3E5C41`，与 Android 的 `#2B6E45` / `#164027` 肉眼可辨。此外该次改动把 HEAD 原本已定义的线色变量丢掉了：**HEAD 的 `tokens.css:27` 有 `--hairline: rgba(26,40,32,.07)`**（源自基础 token），纸质版既没有它、也从没定义它新引入的 `--hairline-strong`，于是改动前工作区里 global.css 的 26 处 `--hairline` + 15 处 `--hairline-strong` 引用全部落空。核对证据：`git show HEAD:web/frontend/src/styles/tokens.css | grep -c -- "--hairline:"` = 1，`grep -c "hairline-strong"` = 0，而 `git show HEAD:web/frontend/src/styles/global.css | grep -c "hairline-strong"` = 0（该引用是纸质版才引入的）。
- Expected outcome: Web 不再有任何自有色值；每个不透明颜色都能在 Android 基础 token 文件里按名查到；被使用却未定义的线色变量得到定义。
- AI-autonomous decisions: 保留玻璃浮层概念但改为「基础 token 的同色 rgba」（不含任何新色相）；`hairline-strong` 收敛到基础 `line`（Android 没有对应 token）；`color` 组留空作为「无独立色板」的机械证据。
- Facts: `Docs/04-ux-design/design-system/tokens.json` 颜色键 17 个（bg/surface/ink/ink2/ink3/line/hairline/green/green-deep/green-soft/green-faint/blue/amber/amber-soft/red/red-soft/yellow-highlight），`hairline` 的值是 rgba(26,40,32,.07) 而不是 hex。
- Non-goals: 不改 Android 源码与 design-system/tokens.json；不改动页面结构（属 T-125 及之后任务）。

## Goal

让 Web 的唯一颜色来源变为 `Docs/04-ux-design/design-system/tokens.json`，`tokens.css` 中不再出现任何 `--web-*` 自有色板变量，且 Android 三方守门保持 0 failed。

## Scope

### In scope
- `Docs/04-ux-design/web-local-design-tokens.json` 改为「只引用不定义」
- `web/frontend/scripts/lib/build-tokens.mjs` 改为全量下发基础色 + 别名只做 var() 引用 + 支持 `ref:` 与 rgba 派生
- `web/frontend/scripts/check-design-tokens.mjs` 守门改为校验别名与基础色变更可被侦测
- `web/frontend/scripts/check-contrast.mjs` 对比度对改读 Android 基线色
- `web/frontend/src/styles/global.css` 移除 `var(--web-on-accent)`（改为 `var(--surface)`，对齐 `view_answer_actions.xml:104-112` 绿色底上用近白的写法）、更新文件头说明

### Out of scope
- global.css 的页面形制（发丝线行 vs 卡片、圆角层次）——属 T-125
- 页面结构与交互重排——属 W2/W3/W4
- 后端派生能力与RoHS/推荐数据——属 W5

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 只改变色值与两枚线色变量的有无；组件结构、`data-testid`、交互与路由一律不动。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者只有 `global.css`（共引用 `--hairline` 26 次、`--hairline-strong` 15 次、`--glass-*` 7 次）与 `src/components/components.test.tsx` 的不变量断言；`TreeCanvasView` 经 `:root` 的 `--tree-*` 读取 `--hairline-strong`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（本次有真实色值变化）。

## Documentation impact record

- Changed runtime facts: Web 颜色不再由 `web-local-design-tokens.json` 提供；该文件转为「映射 + 派生透明度」。
- Authoritative code/config/migration evidence: `web/frontend/src/styles/tokens.css`（由 `node scripts/generate-design-tokens.mjs` 生成，证据文件 `artifacts/evidence/T-124-*.txt`）。
- Documentation surfaces checked: `Docs/04-ux-design/web-local-assets/brand-spec.md:14`、`Docs/04-ux-design/知芽-Web本地版-原型说明-V0.1.md:10`、`Docs/05-technical-design/TreeLearn_Web本地版_详细开发方案_V1.0.md:160`、`Docs/spec/SPEC-2026-10-10-local-web-edition.md:27`。**这几处仍写着「Web 颜色由 web-local-design-tokens.json 独立定义」，已过时，由父需求收口时统一改。**（本任务已在该 JSON 的 `$meta`/`$note` 内写明作废来源与日期。）
- Documentation files updated: `web/frontend/src/styles/global.css` 文件头（改为 Android 基线说明并记录 D1-D6 不再权威）。
- Producer/consumer/sibling/compatibility paths checked: token 三方守门 `python .trae/scripts/dev/check_design_tokens.py` → `0 check(s) failed`（未触碰 Android，故必然通过）。
- Superseded phrases/examples reverse-searched: 见上一项（历史文档待收口）。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 《纸上书房》色板写入本 JSON 的 `supersedes` 字段，保留其历史脉络。

## Acceptance criteria

- [x] `tokens.css` 不含任何 `--web-*` 变量（证据：`artifacts/evidence/T-124-web-color-android-baseline-green.txt`）
- [x] `tokens.css` 内每个 `design-system/tokens.json` 的颜色键都按其值逐字节出现（同上）
- [x] global.css 使用的线色变量均有定义（同上；`--hairline-strong` → `base.line`）
- [x] Web 守门全绿：`check-design-tokens`（47 条属性一致）、`check-contrast`（14 对全部达标，最低 ink3 on bg 4.62）、`check-user-copy-i18n`（44 文件无硬编码文案）
- [x] 两个守门的 selftest 仍能侦测注入变更：`SELFTEST VERDICT: PASS (4 case(s))` / `PASS (3 case(s))`
- [x] `npx vitest run` 205/205、`npx tsc --noEmit` 0、`npx eslint .` 0、`npx vite build` 成功
- [x] `python .trae/scripts/dev/check_design_tokens.py` → `0 check(s) failed`（Android 未受影响）
- [x] 新断言经变异检验为可证伪（见 TDD 记录下方 MUTATION）

## TDD execution record

### RED — failing test first
- Test/scenario: `src/components/components.test.tsx` 新增用例「derives every colour from the Android base token file (no local palette)」与「composites overlays only from Android surface and ink tokens」；并把 D1 用例里的绿色按钮文字色由 `--web-on-accent` 改为 `--surface`。
- Command: `npx vitest run src/components/components.test.tsx`
- Exit code: 1
- Observed failure: 3 failed | 11 passed (14)——① tokens.css 仍匹配 /--web-[a-z]/；② 未匹配 /--glass-panel: rgba\(252, ?251, ?248,/；③ 未匹配 /\.btn \{[^}]*background: var\(--green\)[^}]*color: var\(--surface\)/（证据行分别见 artifacts/evidence/T-124-web-color-android-baseline-red.txt 第 29/132/235 行）。
- Failure reason validated: 三条失败分别对应「仍有 Web 独立色板」「浮层仍是纸系色相」「按钮文字色引用将被删除的 web 变量」，均由缺失行为导致。

### GREEN — minimum implementation
- Implementation: 重写 `web-local-design-tokens.json`（`color: {}`、`aliases.hairline-strong = base.line`、`glass` 用 surface/ink 的同色 rgba + `ref:line`）；`build-tokens.mjs` 改为全量下发基础色、别名只允许 `var()` 引用、支持 `ref:`；`check-design-tokens.mjs` 守门改为校验别名与基础色变更；`check-contrast.mjs` 的 14 对改读 Android 基线色；`global.css` 4 处 `--web-on-accent` → `--surface` 并更新文件头。
- Command: `npx vitest run src/components/components.test.tsx`
- Exit code: 0
- Passing evidence: 同一条命令由 3 failed 转 0（14 passed (14)）；详见 artifacts/evidence/T-124-web-color-android-baseline-green.txt。
- Regression evidence: 同文件另含全量回归——`npx vitest run`（EXITCODE=0，`Test Files 29 passed (29)`、`Tests 205 passed (205)`）、`node scripts/check-design-tokens.mjs`（EXITCODE=0，47 条属性一致）、`node scripts/check-contrast.mjs`（EXITCODE=0，14 对全部达标，最低 ink3 on bg 4.62:1）、`node scripts/check-user-copy-i18n.mjs`（EXITCODE=0，44 文件无硬编码文案）、`npx tsc --noEmit`（EXITCODE=0）、`npx eslint .`（EXITCODE=0）、`npx vite build`（EXITCODE=0，built in 905ms）、`python .trae/scripts/dev/check_design_tokens.py`（EXITCODE=0，0 check(s) failed）。另跑两个守门 selftest：EXITCODE=0，`PASS (4 case(s), every tamper branch turned red)` / `PASS (3 case(s))`。

### REFACTOR — optional
- Cleanup: 无额外重构（本次即最小实现）。
- Regression evidence: 见 GREEN。

### Mutation verification（反恒真配对）
- MUTATION 1 往 `tokens.css` 注入 `--web-paper: #F7F4EE` → `npx vitest run src/components/components.test.tsx` EXITCODE=1，恰好目标用例失败；还原后 EXITCODE=0。
- MUTATION 2 删除 `--ink3` 基础色 → EXITCODE=1，同一用例失败；还原后 EXITCODE=0。
- 证据：`artifacts/evidence/T-124-mutation.txt`。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 规模口径（2026-10-14 重算，替换首轮凭印象写的 129）：**非空非注释生产新增 66 行**，全部在三个构建/守门脚本里——
  `build-tokens.mjs` 18 + `check-design-tokens.mjs` 32 + `check-contrast.mjs` 16。
  计数命令（可复现）：`git diff --cached -U0 -- <文件> | grep '^+' | grep -v '^+++' | grep -vcE '^\+\s*(//|\*|/\*|\s*$')`。
  `global.css` **不计入**：它相对 HEAD 的 436/130 行几乎全部来自 2026-10-12 未提交的《纸上书房》批次；T-124 在它上面的自身改动只有文件头注释改写（注释行，非生产行）与 4 处 `--web-on-accent` → `--surface`——而 `git grep -c web-on-accent HEAD -- web/frontend/src/styles/global.css` **无输出（0 处）**，说明该变量是纸上书房批次引入的，改回 `--surface` 后与 HEAD 一致，**净 diff 为 0**。
  `tokens.css` **不计入**：由 `build-tokens.mjs` 生成（18 行非空新增）。
  - 关于 global.css：`git diff --cached --stat` 显示它与 HEAD 相差 **436 insertions / 130 deletions（合计 566 行）**，其中非空非注释新增 319 行——这些几乎全部来自 2026-10-12 **未提交**的《纸上书房》改动（该批次今日早些时候产出、尚在暂存区），**不计入本任务**；本任务在 global.css 上的自身改动只有文件头 14 行 + 4 处 `--web-on-accent` → `--surface` 替换。剩余形制回退属 T-125。
  - verifier 已就此提出质疑（「规模数字不可复现」），上述口径即该题的答复，并以本段的命令计数为依据。
- `--hairline` 在 Android 基础 token 里是 `rgba(26,40,32,.07)`（半透明），不进 `colors.xml`；Web 直接继承该值，因此它与 `--hairline-strong`（→ `line`）是两个不同语义，二者都被 global.css 使用。
- ~~原稿曾写「两个变量从未被定义」——**已被 verifier 指出不准确并更正**：`--hairline` 在 HEAD 就有定义（`git show HEAD:...tokens.css` 第 27 行），真正的缺陷是《纸上书房》把它删了、且从未定义它自己新引用的 `--hairline-strong`。本任务一并修复。

## Verification report

- Verdict: PARTIAL（2026-10-13 首轮独立 verifier）
  - 功能/验收：verifier 逐条独立复跑后**全部 PASS**（命令、退出码与关键输出见其回执并入本节）：tokens.css 无 `--web-*`；守门 47 属性一致；contrast 14 对最低 4.62:1；vitest 205/205；tsc/eslint 0；vite build 成功；`check_design_tokens.py` 0 failed；变异检验两组均被侦测。
  - verifier 指出的两类非功能问题：**① 背景描述把「`--hairline` 从未定义」写成既成事实**（实为纸质版删除了 HEAD 已有的定义）→ 已在 Problem/background 与 Implementation notes 更正并附 git 验证命令；**② 规模数字与 `git diff` 不可复现**→ 已在规模口径段给出精确计数（436/130，非空新增 319）并说明绝大部分属今日早些时候未提交的批次。
  - verifier 另提示：暂存区混有非本任务文件（`MessageView.tsx`、`BranchWorkspace.tsx`、i18n、`web-fork-wiring-2026-10-12.txt` 等今日早些时候的作业）→ **提交时按 `git commit -- <精确 pathspec>` 只带本任务路径**，已在 Handoff watch-outs 记录。
  - 状态保持 `verifying`：更正项同轮完成，待波末再跑一次独立复核确认，**不自我验收**。
- 第二轮独立复核（2026-10-14）：
  - verifier 确认问题①（hairline 表述）**已真实修正**：`git grep` 复核 HEAD `tokens.css:27` 确有 `--hairline: rgba(26,40,32,.07)`，且 HEAD 不存在 `--hairline-strong`；任务文件现有表述准确。
  - verifier 确认问题②**部分修正、仍留一条不符**：`git diff --cached --numstat` 的 436/130 与任务文件一致，原「不可复现」已解决；但「`global.css` 约 45 行」**不可复现**——HEAD 的 `global.css` 里 `--web-on-accent` 为 **0 处**，故那 4 处替换相对 HEAD 净 diff 为 0，且任务文件内部自相矛盾（求和写 45，正文又说只有 14+4）。
  - 本轮更正（2026-10-14）：按可复现命令重算，规模口径由「129（含 global.css 约 45）」改为「**66 行，全部在三个脚本**」，并在 Implementation notes 写出计数命令与 global.css 净 0 的 git 依据；同时删除旧的自相矛盾表述。仍不自我验收，交第三轮确认。
- 第三轮独立复核（2026-10-14）：**PASS**。verifier 按其修正后的路径（`build-tokens.mjs` 实际在 `scripts/lib/` 下）重算得 18 + 32 + 16 = **66**，与任务文件一致；`git grep -c web-on-accent HEAD -- global.css` = 0、`git diff HEAD -- global.css | grep web-on-accent` 无输出，净 diff 为 0 成立。首轮遗留的两类非功能问题全部闭环。
- Evidence: 上述命令、退出码与 `artifacts/evidence/T-124-*.txt`；缺真实浏览器截图（属 T-125 之后的 UI 验收）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除 4 条钉住纸感色值的断言（paper namespace/hexx inj），改为「颜色必须源自 Android 基础 token」的更强断言，并新增浮层派生约束；等价覆盖由 `check-contrast.mjs` 的 14 对 + 三方守门承担。
- Gaps: 未跑真实浏览器视觉确认；历史文案（brand-spec / 原型说明 / 详细开发方案）仍写着「Web 颜色独立定义」，由父需求收口时统一修正。
- Risks: 页面 CSS 中部分为纸上书房新增的行被称为 T-125 处理；在此之前页面上仍有纸时代遗留的形制（不等同于色值回退未完成）。

## Handoff notes

- Completed: 颜色基座回退（token 源、生成器、两个守门、global.css 变量引用）
- In progress: 待独立 verifier 复核
- Blocker: 无
- Unblock condition: 不适用
- Next steps: T-125 全局样式形制回退（发丝线行 vs 卡片、圆角与层次、控制形制）
- Watch-outs: 后续任何新增不透明色值都必须先过 design-system 与 Android colors.xml 的三方守门；不要再把颜色写进 `web-local-design-tokens.json` 的 `color` 组（留空是「无独立色板」的机械证据）。
- Watch-outs（提交纪律）：暂存区里混有今日早些时候其它批次的作业（`MessageView.tsx`、`BranchWorkspace.tsx`、i18n、`web-fork-wiring-2026-10-12.txt` 等）。提交本任务时用 `git commit -m "..." -- <本任务精确路径>`，不要把别人的暂存改动带进同一个提交。
