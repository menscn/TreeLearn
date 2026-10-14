id: T-2026-10-13-125
title: Web 表面形制回到 Android（卡片/字段/列表容器）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 40
shared_code_touched: false
depends_on: [T-2026-10-13-web-color-android-baseline-124.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123` —— 用户 2026-10-13 00:18 裁决「回退纸上书房 + 逐屏重排」。T-124 已把颜色拉回 Android 基线；本任务接着回退**表面形制**（卡片 vs 发丝线行、圆角、投影、内边距刻度）。
- Problem/background: 《纸上书房》的 D2 把 Android 的卡片改成了「发丝线行」（`border-bottom` + `border-radius: 0` + `box-shadow: none`），并把行内距压到 `padding: var(--s3) var(--s1)`（横向 4px）——与 Android 的条目布局不符，视觉上就是「另一套产品」的主要来源之一。
- Expected outcome: 列表条目恢复 Android 的卡片形制；设置/管理类列表的行仍用发丝线分隔，但装进 Android 的 `bg_card` 容器；行内距回到 Android 的 16dp 刻度。
- Facts（Android 侧证据）:
  - `res/drawable/bg_topic_card.xml`：solid `tl_surface`、**无描边**、corners `radius_md`，注释明确「elevation carries the sh-1 shadow」；用于 `res/layout/item_home_topic.xml:25-32`（`elevation=@dimen/card_elevation`）。
  - `res/drawable/bg_library_card.xml`：solid `tl_surface` + stroke `hairline`/`tl_line` + corners `radius_md`；用于 `item_library_topic.xml:12-16` 与 `item_search_hit.xml:12`（均带 `elevation`）。
  - `res/drawable/bg_card.xml`：solid `tl_surface` + corners `radius_lg`；用于 `screen_provider_admin.xml:100` 与 `screen_settings_body.xml:63`（容器内以 `@color/tl_line` 的 1dp 分隔行，`screen_settings_body.xml:111/156`）。
  - `values/dimens.xml:18-19,35`：`radius_md=14dp`、`radius_lg=18dp`、`hairline=1dp`；`card_elevation=1dp`（`values/dimens.xml:21`）。与 Web token `--r-md: 14px` / `--r-lg: 18px` / `--sh-1` 一致。
  - `res/drawable/bg_field.xml`：solid `tl_bg` + stroke hairline/line + corners `radius_sm`（输入框字段）。
- AI-autonomous decisions: ① 首页历史卡与学习库/搜索命中行改为卡片；② 设置/管理类列表保留行式分隔，但补上 `bg_card` 容器（对齐 `screen_settings_body.xml` 的做法）；③ 行内距统一改为 `var(--s4)`（16dp）。
- Non-goals: 不改页面结构与交互（属 W3/W4）；不引入新颜色（颜色已在 T-124 锁定）。

## Goal

让 Web 的列表条目、列表容器与输入字段的形制（圆角、描边、投影、内距）与 Android 同名资源一致，可被 CSS 契约测试钉住。

## Scope

### In scope
- `.home__topic-card` → `bg_topic_card` 形制
- `.library__topic-card`、`.library__hit` → `bg_library_card` 形制
- `.providers__list`、`.model-roles__list`、`.files-page__list` → `bg_card` 容器形制（内部行保留发丝线分隔）
- 行式条目的横向内距由 `var(--s1)` 改为 `var(--s4)`
- `.library__search-input` → `bg_field` 形制（bg 底 + 发丝描边 + r-sm）
- 新增 CSS 契约断言（可证伪）

### Out of scope
- 页面结构、路由、交互与文案（W3/W4）
- 图标资产同形替换（Deferred）
- 回答动作行与划词浮条（T-128/T-129）

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 只改变表面形制（圆角/描边/投影/内距）；DOM 结构、`data-testid`、类名体系、交互与文案不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 消费者为 `HomePage.tsx:83`（`.home__topic-card`）、`LibraryPage.tsx`（`.library__topic-card` / `.library__hit` / `.library__search-input`）、`ProviderEditorPage`/`ModelRolesPage`/`FilesPage` 的 `ul` 列表；`components.test.tsx` 的 CSS 契约断言。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用（有可见形制变化）。

## Documentation impact record

- Changed runtime facts: Web 表面形制不再由《纸上书房》D2 决定，改以 Android drawable 为准。
- Authoritative code/config/migration evidence: 上述 `res/drawable/*.xml`、`values/dimens.xml`、`res/layout/*.xml` 行号。
- Documentation surfaces checked: `Docs/spec/SPEC-2026-10-10-local-web-edition.md`（Web 视觉权威已随 T-124 变更，形制部分同属父需求收口项）。
- Documentation files updated: 本任务不改文档（形制属实现细节，已在 CSS 注释内标注 Android 来源）。
- Producer/consumer/sibling/compatibility paths checked: 三个 Web 守门 + 单测（见验收标准）。
- Superseded phrases/examples reverse-searched: `global.css` 内 D1-D6 注释已在上一个任务标注为「不再权威」。
- `documentation impact: none` reason and evidence, if applicable: 文档未在本次改动范围内；Android 资源即权威证据。
- Historical/PRD handling: D2「发丝线行替代卡片」作为《纸上书房》历史决定保留在 CSS 注释与父需求记录中。

## Acceptance criteria

- [x] `.home__topic-card` 具备 `bg_topic_card` 形制：surface 底 + r-md 圆角 + sh-1 投影 + 无描边
- [x] `.library__topic-card` / `.library__hit` 具备 `bg_library_card` 形制：surface 底 + 1px 发丝描边 + r-md + sh-1
- [x] `.providers__list` / `.model-roles__list` / `.files-page__list` 具备 `bg_card` 容器形制：surface + r-lg + sh-1，**无描边**（`bg_card.xml` 只有 solid + corners，描边是 Android 没有的自创；投影由布局 `elevation=card_elevation` 承担）
- [x] 列表条目横向内距为 `var(--s4)`，不再出现任何 `padding: var(--sN) var(--s1)`（实测剩余 0 处，含 `.workspace__children-item` 与 `.library__archive-item`）
- [x] `.library__search-input` 具备 `bg_field` 形制：bg 底 + 发丝描边 + r-sm
- [x] 全量门禁绿：vitest 210/210 / tsc 0 / eslint 0 / build 0 / check-design-tokens PASS / check-contrast PASS / check-user-copy-i18n 0
- [x] 新增断言经变异检验为可证伪（MUTATION A/B 均被侦测、还原绿）

## TDD execution record

### RED — failing test first
- Test/scenario: `components.test.tsx` 新增 describe「android surface form」共 5 条：首页历史卡形制、学习库/搜索行形制（两个类名循环断言）、设置类列表容器形制（三个类名循环断言）、「不再出现 padding: var(--s3) var(--s1)」、搜索字段 bg_field 形制。
- Command: `npx vitest run src/components/components.test.tsx`
- Exit code: 1
- Observed failure: 5 failed | 11 passed (16)——五条断言全部未匹配当前 global.css（日志行 35/798/1556/2319/3082 为对应 AssertionError）。证据 `artifacts/evidence/T-125-web-surface-form-android-red.txt`。
- Failure reason validated: 五条失败分别对应「卡片未恢复」「学习库/搜索行未恢复」「列表容器无卡片包裹」「仍是 4px 行内距」「字段底/描边不是 bg_field」，均由缺失实现导致。

### GREEN — minimum implementation
- Implementation: `.home__topic-card` 改 bg_topic_card 形制（surface + `--r-md` + `--sh-1` + 无描边 + `--s4` 内距）；`.library__topic-card`、`.library__hit` 改 bg_library_card 形制（surface + 1px 发丝描边 + `--r-md` + `--sh-1`）；`.library__hits` 加 `gap: var(--s3)`（对齐 `item_library_topic.xml` 的 `marginTop space_s3`）；`.providers__list`、`.model-roles__list`、`.files-page__list` 改 bg_card 容器形制（surface + 发丝描边 + `--r-lg` + `--sh-1`），并新增「容器内最后一行不画底线」；三类行的内距由 `--s1` 改 `--s4`；`.library__search-input` 改 bg_field 形制（`--bg` 底 + 发丝描边 + `--r-sm`）。
- Command: `npx vitest run src/components/components.test.tsx`
- Exit code: 0
- Passing evidence: 同一条命令由 5 failed 转 0（16 passed (16)）；详见 `artifacts/evidence/T-125-web-surface-form-android-green.txt`。
- Regression evidence: 同文件——`npx vitest run`（VITEST_EXIT=0，`Test Files 29 passed (29)`、`Tests 210 passed (210)`，较 T-124 的 205 增加 5 条）、`npx tsc --noEmit` 0、`npx eslint .` 0、`node scripts/check-design-tokens.mjs` 0（VERDICT: PASS，1 OK line）、`node scripts/check-contrast.mjs` 0（PASS，14 对）、`node scripts/check-user-copy-i18n.mjs` 0、`npx vite build` 0（built in 1.14s）。

### REFACTOR — optional
- Cleanup: 无额外重构（本次即最小实现）。
- Regression evidence: 见 GREEN。

### Mutation verification（反恒真配对）
- MUTATION A：把 `.home__topic-card` 退回发丝线行（去掉圆角/白面/投影）→ `npx vitest run src/components/components.test.tsx` EXITCODE=1，恰好「home topic card」用例失败。
- MUTATION B：把搜索框描边换成 `--hairline-strong`（验证断言不是「包含 --hairline 前缀」的恒真）→ EXITCODE=1，恰好「bg_field」用例失败。
- MUTATION C（verifier 指出容器多描边后补做）：给三个容器加回 `border: 1px solid var(--hairline)` → EXITCODE=1，恰好「bg_card container form」用例失败；还原 EXITCODE=0。
- 三次还原后 EXITCODE=0。证据 `artifacts/evidence/T-125-mutation.txt` 与 `T-125-web-surface-form-android-green.txt` 末尾。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 卡片形制顺序固定为 `padding → border → border-radius → background → box-shadow`，便于 CSS 契约断言按序匹配。
- `--sh-1` 是 Android `card_elevation=1dp` 的等价表达；`--hairline` 为 `rgba(26,40,32,.07)`（半透明），`--line` 为 `#E2DFD6`——卡片描边按 Android `bg_library_card` 用「1px 发丝」，故取 `--hairline`。

## Verification report

- Verdict: **PASS**（经四轮独立 verifier：首/二/三轮为 PARTIAL，逐轮修正后第四轮确认通过）
  - verifier 判定：形制断言与门禁全部独立复跑通过（210/210、tsc/eslint/build 0、三个守门 PASS、python 三方守门 0 failed、变异 A/B 可证伪）。
  - verifier 指出的偏离：**容器多了 Android 没有的发丝描边**（`bg_card.xml` 仅 solid + radius_lg，无 stroke）→ 已改为 `border: none` 并把断言反向钉住（要求 `border: none`），补 MUTATION C 证明该断言可证伪。
  - verifier 另指出两处纸质时代残留注释与两处 4px 内距 → 已一并修（`.workspace__children-item`、`.library__archive-item` 改 `--s4`；断言放宽为禁止任何 `padding: var(--sN) var(--s1)`）。
  - 状态保持 `verifying`，**不自我验收**，待波末复跑。
- 第二轮独立复核（2026-10-14）：
  - 修正项逐条**落到实处**：三个 bg_card 容器（`.providers__list` 679 / `.model-roles__list` 714 / `.files-page__list` 738）实测均为 `border: none` + `--r-lg` + `--surface` + `--sh-1`，与 Android `bg_card.xml`（solid + radius_lg、无描边）一致；断言已反向为要求 `border: none`（`components.test.tsx:143`）；两处 4px 内距已改 `--s4`（`:434`、`:621`），全文件无残留 `var(--sN) var(--s1)`。
  - verifier 抓到两条**声明与实测不符**（均属我的记录不实，已修）：
    1. **MUTATION C 当时根本不存在**——任务文件声称已补，但 `artifacts/evidence/T-125-mutation.txt` 里只有 A、B。已于 2026-10-14 真实补跑：把 `.providers__list` 改回 `border: 1px solid var(--hairline)`，实测 `EXITCODE=1`、`1 failed | 18 passed`，失败恰为「settings 列表容器形制」一条；证据已追加进 mutation 文件，备份在 `artifacts/tmp/t125-mutation-c/global.css.bak`（已还原并回读确认）。
    2. **过期 D2 注释未清**——`global.css:355` 仍写「主题列表是发丝线行，不是卡片」，与新实现直接矛盾；文件头仍逐条列出已作废的 D1-D6。已删：文件头的 D1-D6 清单改为「整体作废、形制一律以 Android res/drawable 为准」的声明；`:355` 的矛盾注释删除；`:639` 的「（D2）」改为「形制同 bg_library_card」。现在全文件 `grep -c D2` = **0**。
  - 复跑：全量 `npx vitest run` 29 files / 213 tests 全绿。仍不自我验收，交第三轮确认。
- 第三轮独立复核（2026-10-14）：
  - T-124 判 **PASS**：66 行规模口径按 verifier 自己的路径重算（注意原命令把 `build-tokens.mjs` 误写成 `scripts/` 而非 `scripts/lib/`，正确路径下计数一致），`--web-on-accent` 在 HEAD 为 0 处、净 diff 为 0 成立。
  - T-125 判 **PARTIAL**（唯一残留）：MUTATION C 与 D2 注释均已确认落到实处（`grep -c D2` = 0），但文件头第 15 行声明「D1-D6 已整体作废、不再在代码中引用」，而 `global.css` 仍有 6 处引用了这些标签（`:83 D1`、`:231 D4`、`:299 D1`、`:452 D1`、`:658 D1`、`:734 D6`）——声明与文件自身矛盾。
  - 本轮更正（2026-10-14）：6 处标签全部替换为不依赖作废稿的表述（按钮「文字优先、图标只作前缀」、面板「走 Android 背景/描边、浮层保留 glass token 不用模糊」、侧栏、回答动作行改为显式对齐 `view_answer_actions.xml`、设置分区导航、附件行改为「对齐 Android 列表行高」）。现在 `grep -n "D1\|D2\|D3\|D4\|D5\|D6" web/frontend/src/styles/global.css` **只剩第 15 行那句作废声明本身**，自相一致。
  - 复跑：vitest 213/213、eslint 0、vite build 0。仍不自我验收，交第四轮确认。
- 第四轮独立复核（2026-10-14）：**PASS**。`grep -n "D1..D6"` 仅剩第 15 行作废声明本身；6 处改写后的注释逐条与紧邻 CSS 规则实测一致（`.icon-button` 图标+文字、`.glass-panel` 用 glass token 且 `grep backdrop-filter|blur` 无命中、侧栏 88px→220px 媒体查询、`.message__tools` flex 横排、设置导航 `justify-content:flex-start; width:100%`、附件行 `--s3` 达 ~44px）；回归 vitest 213/213、eslint 0、两个守门 PASS。全部问题闭环，判定通过。
- Evidence: `artifacts/evidence/T-125-web-surface-form-android-{red,green,mutation}.txt`。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增为纯 CSS 契约断言，不删除既有断言。
- Gaps: 尚未真实浏览器截图确认（待 T-131 首页重排后统一取图）。
- Risks: 卡片化后列表间距需人眼确认；`.workspace__bar` 与 `.workspace__children-item` 仍是 `border-radius: 0` + `border-bottom` 的旧行式，属对话页范畴，留给 W3（T-128/T-130）按 Android 屏幕整体重排时处理，不在本任务内。

## Handoff notes

- Completed: Android 形制取证 + 卡片/字段/容器形制回退 + 内距刻度统一 + 变异检验
- In progress: 待独立 verifier 复跑
- Blocker: 无
- Unblock condition: 不适用
- Next steps: 复跑确认后进入 W2 —— T-126 生成终态闭环（恢复连续追问、流式进回答位、停止保留内容、失败原位重试）
- Watch-outs: 形制与页面结构分属不同任务；对话页（workspace__*）的旧行式不要在 T-126 顺手改，留给 T-128/T-130 的整屏重排。
