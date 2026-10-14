id: T-2026-10-14-131
title: 首页结构对齐 Android（brand lockup / hero 条件显示 / 最近学习计数与三条上限 / 整卡可点）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 80
shared_code_touched: false
depends_on: [T-2026-10-14-recommendation-send-130.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123`。W4 首页与外壳第一步。
- Problem/background（源码核对）:
  - Android 权威一：`android/app/src/main/res/layout/screen_home_body.xml` —— 自上而下是
    appbar → **brand lockup（芽 + 知芽，`home_brand_lockup`，恒显示）** → hero 主标语 →
    composer → 推荐区 → 最近学习；文件头注释写明 *"the slogan is shown only while the home has
    no history topics"*。
  - Android 权威二：`HomeScreen.java:174` `heroTitle.setVisibility(state.hasTopics() ? GONE : VISIBLE)`；
    `:232-236` 计数 `recentCount.setVisibility(hasTopics ? VISIBLE : GONE)` 且文案
    `home_recent_count = "%1$d 个"`——**没有可数之物时整条隐藏，不是显示 0**。
  - Android 权威三：`HomeScreen.java:217` `MAX_RECENT = 3` + 类注释 *"the shortcut surface, not
    the full list"*；`:239` `Math.min(MAX_RECENT, size)`。
  - Android 权威四：`item_home_topic.xml` —— 卡片自身 `android:clickable="true"`，**没有独立的
    chevron 按钮**；meta 行是「分支数 · 相对时间」。
  - Web 现状偏差：无 brand lockup；主标语恒显示；最近区无计数、无上限；卡片里 chevron 是唯一
    点击目标（点标题文字没反应）；无 meta 行。
- Expected outcome: 首页结构与 Android 同序同口径——品牌恒在、标语只在「还没有主题」时出现、
  最近学习带计数且最多三条、整张卡可点。
- Facts: 契约 `GET /api/topics` 的 `TopicSummary` 只有 `lastUsedAtMillis`，**没有 branchCount /
  summary / unreadCount**（Android `TopicCard` 有）——因此 meta 行只画相对时间，卡片不画摘要与未读点。
- AI-autonomous decisions:
  1. meta 行用 T-128 已落地的 `formatRelativeTime`（对齐 Android `DateUtils.getRelativeTimeSpanString`），
     缺 branchCount 那一半记为 Gap，等契约补字段（T-133 一并结合）。
  2. **首页推荐条不在本任务内**：它的数据源是后端派生能力（`HOME_RECOMMENDATIONS`，契约 §5.x
     标注为可选），正是 T-133 的范围；在没有数据源之前画一个空壳属于假实现，故留到 T-133 接线。
  3. 整卡改为 `<button>`，`aria-label` 用标题（对齐 Android 的 `contentDescription`），
     这样既有点卡任意处可点的语义，又不破坏既有用例按 name 查询。
- Non-goals: 推荐条（T-133）、侧栏（T-132）、树/设置/学习库（T-134）、契约变更。

## Goal

首页按 Android 那一套排：品牌一直在，标语只在还没主题时出现，最近学习带计数、最多三条，整张卡都能点。

## Scope

### In scope
- brand lockup（芽 + 品牌名）恒显示
- 主标语只在无历史主题时显示
- 最近学习：标题 + 计数（无则隐藏）、最多 3 条、meta 相对时间、整卡可点
- i18n 补 `home.brand` / `home.recent.count`；`icons.ts` 用既有 `Sprout` 白名单
- CSS：brand / recent-head / count / 卡片改 button 形制 / meta

### Out of scope
- 首页推荐条（数据源待 T-133）
- 卡片的摘要、未读点、分支数（契约字段缺失）
- 侧栏、外壳

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变首页 hero 的显示条件、最近区的构成与卡片的点击
  目标；保留首问提交、草稿、模型不可用引导、错误文案、i18n 切换。
- Producer/consumer/sibling entrypoints and platform branches (code evidence):
  `pages/HomePage.tsx`、`i18n/{zh-CN,en-US}.ts`、`styles/global.css`；
  `platform/datetime.ts` 的 `formatRelativeTime`（T-128 已落地，只读）。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 首页 hero 的显示条件、最近区的计数与上界、卡片的点击目标。
- Authoritative code/config/migration evidence: `screen_home_body.xml`、`HomeScreen.java:174/217/232-236`、
  `item_home_topic.xml`、`pages/HomePage.tsx`。
- Documentation surfaces checked: `web/contracts/api.md` §5.1b（`TopicSummary` 字段未变）。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: `loadRecentTopics` / `submitFirstQuestion`
  未改；`HomePage.test.tsx` 既有用例（按 name 查询卡片按钮）零改动且全绿。
- Superseded phrases/examples reverse-searched: 无文档语句描述旧结构。
- `documentation impact: none` reason and evidence, if applicable: 无端点、契约、文档语句变化。
- Historical/PRD handling: 不适用。

## Acceptance criteria

- [x] brand lockup 恒显示（有主题、无主题都在）
- [x] 主标语只在没有历史主题时出现
- [x] 最近学习带计数（有主题时「n 个」），无主题时计数整条消失而不是显示 0
- [x] 最近学习最多画 3 条（读回 5 条也只画 3 条）
- [x] 点卡片任意处都能进主题，不再只有尾部按钮可点
- [x] 全量门禁绿：vitest / tsc / eslint / build / 三守门
- [x] 新增断言经变异检验为可证伪

## TDD execution record

### RED — failing test first
- Test/scenario: `pages/HomePage.test.tsx` 新增 `describe('HomePage structure (T-131)')` 五条用例：有主题时标语消失而 brand 仍在；无主题时标语出现且计数消失；有主题时计数为「2 个」；读回 5 条只画 3 条；点卡片文字即进主题。
- Command: npx vitest run src/pages/HomePage.test.tsx
- Exit code: 1
- Observed failure: 五条全红——`Unable to find an element by: [data-testid="home-brand" / "home-recent-count" / "home-topic-card"]`，点标题文字不跳转。
- Failure reason validated: 证据 `artifacts/evidence/T-131-home-structure-red.txt`（EXITCODE=1，5 FAIL / 11 passed），以 `git show HEAD:` 取回的 T-131 之前版本（HomePage.tsx / i18n / global.css）跑**最终版**测试。

### GREEN — minimum implementation
- Implementation: `HomePage.tsx`——新增 `MAX_RECENT = 3` 常量与 `hasTopics` / `recentTopics` / `recentCount` 派生；hero 区加 `home__brand`（`Icon icon={Sprout}` + `t('home.brand')`）并给 `h2.home__title` 套 `{hasTopics ? null : ...}`；最近区加 `home__recent-head`（标题 + 条件计数）与 `home__recent-count`；卡片从 `GlassPanel + IconButton(chevron)` 改为单个 `<button className="glass-panel home__topic-card" aria-label={title} onClick=...>`，内含 label 与 meta（`formatRelativeTime(lastUsedAtMillis, locale)`）。i18n 补 `home.brand` / `home.recent.count`（zh「知芽」「{n} 个」、en「TreeLearn」「{n}」）；CSS 补 brand / recent-head / count / 卡片 button 形制 / meta。
- Command: npx vitest run src/pages/HomePage.test.tsx
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-131-home-structure-green.txt`——目标文件 16/16；全量 `npx vitest run` 29 files / 233 tests（前值 228，新增 5 条）。
- Regression evidence: 同证据文件——tsc 0、eslint 0、三守门 PASS、`vite build` 0；首页既有的 composer 状态机、草稿、i18n 用例未改且全绿。

### REFACTOR — optional
- Cleanup: 删除卡片里已无用的 `GlassPanel` / `IconButton` / `ChevronRight` 引入（改为 button 自带 `glass-panel` 类）。
- Regression evidence: 见 GREEN 证据文件的同一批门禁。

### Mutation verification（反恒真配对）
- 五个变异各改一处生产代码，重跑同一条命令；备份 `artifacts/tmp/t131/HomePage.green.bak`，
  已 `diff` 还原确认 RESTORED_CLEAN，并再跑一次全量 233/233。
  证据：`artifacts/evidence/T-131-home-structure-mutation.txt`。
- A（标语改回恒显示）→ 失败恰为「有主题时标语消失」；B（`MAX_RECENT` 3→999）→ 失败恰为「最多 3 条」；
  C（卡片去掉 onClick）→ 失败恰为「点卡片任意处进主题」；D（计数改回恒显示）→ 失败恰为「无主题时计数消失」；
  E（brand 也改成只在无主题时显示）→ 失败恰为「有主题时 brand 仍在」。均为 `1 failed | 15 passed (16)`。
- **恒真事故与修补**：变异 D 第一次跑是 16/16 全绿——「计数」只写了正向断言，没写「无主题时计数消失」，
  于是「恒显示计数」能全绿过关。补上负向断言后用 HEAD 版生产代码**重采 RED**（5 failed | 11 passed），
  再跑变异 D 才红。教训已写进证据文件：断言「有 X 时出现」必须配「没有 X 时消失」。
- 反恒真配对：正向（标语消失 / 画满 3 条 / 点卡片可进 / 计数出现）配负向（标语出现 / 第 4 条不出现 / 计数消失 / brand 仍在）。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 「没有可数之物时隐藏计数」不是排版细节：Android 明确写 `recentCount.setVisibility(... GONE)`
  并且文本置空。写成「0 个」会把「还没有主题」和「有 0 个主题」混成一件事。
- 卡片改成 `<button>` 而不是给 `<li>` 挂 onClick，是为了键盘可达与可访问名；`aria-label` 用标题，
  使既有用例 `getByRole('button', { name: <标题> })` 不需要改。
- meta 只画相对时间：契约 `TopicSummary` 没有 `branchCount`，凭空补一个数字比少画一半更糟。

## Verification report

- Verdict: PASS with nits（独立 verifier 复核，2026-10-14）
- Verifier evidence: 复核确认 brand 恒显、标语条件显示、`MAX_RECENT=3`、整卡 `<button>` 均已落地；Android 权威 `HomeScreen.java:174/217/232-236` 与 `item_home_topic.xml` 引文属实；重跑 `npx vitest run` 234/234 全绿。
- Verifier nit: 变异 D 曾恒真（本任务已自行发现并修补），verifier 复查确认负向断言 `queryByTestId('home-recent-count')` 为 null 确实在位且能证伪。
- Evidence: `artifacts/evidence/T-131-home-structure-{red,green,mutation}.txt`
- Changed/deleted assertion rationale and equivalent behavior coverage: 未删除既有断言；新增五条覆盖首页结构；首问提交与草稿的等价覆盖由既有 `HomePage composer state machine` / `draft and i18n` 用例继续承担。
- Gaps: ① 首页推荐条（数据源 = 后端 `HOME_RECOMMENDATIONS`，待 T-133）；② 卡片的摘要、未读点、分支数（`TopicSummary` 缺字段，待契约扩展）；③ 卡片入场动画（Android `HomeMotion` 的错峰入场）未做。
- Risks: ① 卡片 button 少了 `GlassPanel` 的 DOM 层，视觉需浏览器复核一次；② `home.recent.count` 中文是「{n} 个」、英文是「{n}」，英文侧缺少量词语感，可能要按 locale 给复数形式。

## Handoff notes

- Completed: Android 四处权威取证（screen_home_body.xml / HomeScreen.java:174,217,232-236 / item_home_topic.xml）；RED（5 红，用 HEAD 版实现重采）/ GREEN（16/16、全量 233/233、六项门禁 0）/ 五变异证伪并还原
- In progress: 交独立 verifier 复核
- Blocker: 无
- Unblock condition: 不适用
- Next steps: verifier 复核 → 提交 → T-132 侧栏内容与布局切换收敛 → T-133 后端派生能力补齐（含首页推荐条接线）
- Watch-outs: 计数「没有就隐藏」不要改回 0；`MAX_RECENT` 是快捷面的上界，完整列表在学习库，别在首页放开。
