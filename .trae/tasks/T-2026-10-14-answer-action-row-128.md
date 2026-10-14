id: T-2026-10-14-128
title: 回答动作行改为 Android 的四纯图标 + 尾部时间
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-13-web-android-alignment-123.md
requirements_confirmed: true
generated_by: leader
owner: leader
owner_lane: frontend
stack: frontend
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-14-reading-restore-and-unread-127.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 父需求 `T-2026-10-13-web-android-alignment-123`。W3 对话页重排第一步。
- Problem/background（源码核对）:
  - Android 权威：`android/app/src/main/res/layout/view_answer_actions.xml` 与
    `android/app/src/main/java/com/treelearn/app/screen/AnswerActionBar.java`。2026-09-28 用户裁决
    （写在该 XML 头部注释里）：动作行**只保留四个纯图标**——复制(`ic_action_copy`)、重新生成
    (`ic_nav_restore`)、新建分支(`ic_action_fork`)、分支列表(`ic_nav_tree` + 右上角绿色计数徽标)，
    行尾是生成时间；`AnswerActionBar` 类注释：「The app's own extras (quote, translation, speech)
    and the hidden version switcher are removed from this row」。
  - 每个按钮固定 `@dimen/touch_min`（44dp）宽高、`paddingStart/End=0dp`、`text=""`——**不带文字**；
    用户原话「分支列表 对应的也是图标，不要是文字！」。分支列表在无子分支时 `visibility=gone`
    （`AnswerActionBar.render`：`hasChildren ? VISIBLE : GONE`），不是显示成禁用。
  - 时间：`AnswerActionBar.renderTime(createdAtMs)` 用 `DateUtils.getRelativeTimeSpanString(..., MINUTE_IN_MILLIS)`，
    即相对时间；注释明确「The row is hidden while the answer streams, so the time appears exactly when
    the prototype shows it: on a stable answer」。
  - Web 现状：`components/MessageView.tsx` 的 `.message__tools` 有 **7 项且全部 `showLabel` 带文字**——
    朗读(`Volume2`)、版本下拉(`versions-<id>`)、翻译(`Languages`)、复制、分叉、子分支列表（文字
    「子分支列表（0）」）、重新生成；没有时间。与 Android 的「四纯图标 + 时间」完全相反。
- Expected outcome: 回答动作行只有四个纯图标 + 行尾相对时间；朗读/翻译/版本不在这一行；分支列表无子分支时不出现。
- Facts: `Network`（树形图标）已在 `components/icons.ts` 导出；`platform/datetime.ts` 是时间展示唯一入口，
  但没有相对时间函数（Android 用的正是相对时间），需新增。
- AI-autonomous decisions（有意为之的偏差，已记录）:
  1. **版本切换保留但移出动作行**：Android 把版本下拉从这一行移除后，`ChatActions.onVersionPressed`
     在 Android 当前**没有调用方**（实测 `grep -rn onVersionPressed` 只命中接口声明），即 Android 自己也
     还没安置新入口。Web 的版本切换是**可用功能且有测试**（`ToolsAndBlocks.test.tsx:53`），删掉是能力倒退。
     故：保留，从四图标行移出，单独成行、仅在 `versions.length > 1` 时出现——动作行本身与 Android 一致。
     这是记录在案的偏差（参照 T-043 热力格偏差先例）。
  2. **翻译入口移入翻译卡**：Android 注记「the translation card keeps its own header entry」。
     `TranslationBlock` 自带 `translation-start-<id>` 按钮，故 Web 直接常驻渲染 `TranslationBlock`，
     翻译入口由卡片自己承载，动作行不再放翻译图标。
  3. **朗读移除**：Android 因用户裁决「删除不需要的按钮…还有语音播放」已无学习者可见入口
     （`ChatScreen.render` 注释：「speech has no learner-visible signal left」），Web 同步移除。
- Non-goals: 划词浮条（T-129）、推荐即发送（T-130）、首页与侧栏（T-131/T-132）。

## Goal

一条回答下面只有四个纯图标（复制 / 重新生成 / 新建分支 / 分支列表带计数）和行尾的生成时间。

## Scope

### In scope
- `.message__tools` → 四纯图标行 + 尾部相对时间
- 分支列表图标带绿色计数徽标；无子分支时不渲染
- 朗读图标移除；翻译入口迁到翻译卡；版本切换移出该行（保留能力）
- `platform/datetime.ts` 新增相对时间格式化

### Out of scope
- 划词浮条、推荐 chip、首页、侧栏
- 后端改动

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 改变动作行的构成与呈现；保留复制/重新生成/分叉/子分支列表的
  端点与回调语义（含 `fork-<id>`、`children-<id>` 两个 testid）、复制的二态反馈。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `components/MessageView.tsx`
  （被 `BranchWorkspace` 与 `SideBySideView` 共用）、`platform/datetime.ts`（新增纯函数）、
  `styles/global.css`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: 回答动作行的构成。
- Authoritative code/config/migration evidence: `view_answer_actions.xml`、`AnswerActionBar.java`、`MessageView.tsx`。
- Documentation surfaces checked: `web/contracts/api.md`（端点未变）。
- Documentation files updated: 无。
- Producer/consumer/sibling/compatibility paths checked: `SideBySideView` 以无回调方式复用 `MessageView`，
  不会渲染动作行内的多数按钮。
- Superseded phrases/examples reverse-searched: 未涉及文档措辞。
- `documentation impact: none` reason and evidence, if applicable: 无端点、契约、文档语句变化。
- Historical/PRD handling: 不适用。

## Acceptance criteria

- [x] 动作行只有四个按钮：复制、重新生成、新建分支、分支列表；且都是**纯图标无文字**
- [x] 行尾显示相对时间（生成中隐藏整行，稳定后才出现）
- [x] 分支列表带绿色计数徽标；无子分支时整项不渲染（不是禁用）
- [x] 朗读不在动作行；翻译不在动作行（改由翻译卡承载）
- [x] 版本下拉不在动作行，但多版本时仍可切换（保留能力）
- [x] 全量门禁绿：vitest / tsc / eslint / build / 三守门
- [x] 新增断言经变异检验为可证伪

## TDD execution record

### RED — failing test first
- Test/scenario: `web/frontend/src/components/ToolsAndBlocks.test.tsx` 新增 `describe('MessageView answer action row (T-128)')` 四条用例：动作行恰好四个纯图标按钮 + 行尾相对时间且不含朗读/翻译；无子分支时分支列表整项不出现；版本下拉不在动作行内但仍可取到；生成中整行隐藏。同时删除「朗读回答」用例（该能力随 Android 用户裁决移除）。
- Command: npx vitest run src/components/ToolsAndBlocks.test.tsx
- Exit code: 1
- Observed failure: 三条失败——`Unable to find an element by: [data-testid="actions-m-ai"]`（旧动作行叫 `message__tools` 且带 7 项文字按钮）、`expected <button> to be null`（无子分支也渲染分支列表）、同上 `actions-m-ai` 缺失。
- Failure reason validated: 证据 `artifacts/evidence/T-128-answer-action-row-red.txt`（EXITCODE=1，3 FAIL），以 **HEAD（T-128 实现之前）的 `MessageView.tsx`** 跑最终版测试，确保 RED 与最终断言同版本。第四条（生成中隐藏）在旧实现下天然成立，其可证伪性由 MUTATION D 承担。

### GREEN — minimum implementation
- Implementation: `components/MessageView.tsx`——`.message__tools` 换成 `.message__actions`：四个纯图标 `IconButton`（`Copy` / `RotateCcw` / `GitFork` / `Network`+计数徽标）全部去掉 `showLabel`；分支列表改为 `(childBranchCount ?? 0) > 0` 才渲染（不是禁用）；行尾新增 `formatRelativeTime(message.createdAtMillis, locale)`；朗读（`Volume2` + `lib/speech`）整体移除；翻译按钮删除，`TranslationBlock` 改为常驻渲染（入口由卡片自己承载）；版本下拉移出四图标行、保留在多版本时可用。`platform/datetime.ts` 新增 `formatRelativeTime`（`Intl.RelativeTimeFormat`，对齐 Android `DateUtils.getRelativeTimeSpanString(..., MINUTE_IN_MILLIS)`）。`global.css` 新增 `.message__actions` / `.message__actions-spacer` / `.message__time` / `.message__action-badge` / `.message__action-count`，动作按钮 44px 触达零内距。
- Command: npx vitest run src/components/ToolsAndBlocks.test.tsx
- Exit code: 0
- Passing evidence: `artifacts/evidence/T-128-answer-action-row-green.txt`——目标文件 9/9；全量 `npx vitest run` 29 files / 219 tests（前值 216：新增 4 条、删除 1 条朗读用例）。
- Regression evidence: 同证据文件——tsc 0、eslint 0、`check-design-tokens` PASS、`check-contrast` PASS（14 对）、`check-user-copy-i18n` PASS、`vite build` 0。分叉/子分支列表的两个 testid（`fork-<id>`、`children-<id>`）保留，既有 fork 测试未改。

### REFACTOR — optional
- Cleanup: JSX 里的中文注释被 `check-user-copy-i18n` 判为硬编码文案（4 violations），已把说明移到 JSX 外的 `//` 注释块。
- Regression evidence: 守门复跑 PASS；全量 219/219。

### Mutation verification（反恒真配对）
- 四个变异各改一处生产代码，重跑同一条命令；备份 `artifacts/tmp/t128/MessageView.green.bak`，已还原并复跑确认。证据：`artifacts/evidence/T-128-answer-action-row-mutation.txt`。
- A（加回 `showLabel`）→ 失败恰为「四纯图标 + 时间」；B（去掉计数门槛）→ 失败恰为「无子分支不出现」；C（版本下拉移回行内）→ 失败恰为「版本不在动作行」；D（生成中不隐藏）→ 失败恰为「生成中整行隐藏」。均为 `1 failed | 8 passed (9)`。
- 反恒真配对：四条都是「应该是 X」配「不应是 Y」（有文字 ✗ / 有朗读 ✗ / 有翻译 ✗ / 无限渲染分支列表 ✗ / 版本在行内 ✗ / 生成中显示 ✗），恒空或恒满实现都无法全绿。

### TDD or sizing exception — only with prior user approval
- 无。

## Implementation notes

- 「纯图标」的判定写成「四个按钮自身 `textContent` 为空」，而不是「整行文本为空」——分支列表的计数徽标是有意保留的文字角标（原型 `.ai-act-n`），它在按钮之外。
- 版本切换是有意保留的偏差：Android 把版本下拉移出动作行后，`ChatActions.onVersionPressed` 至今**没有调用方**（`grep -rn onVersionPressed` 只命中接口声明），即 Android 自己也还没安置新入口；Web 的版本切换可用且有 T-053 测试，删掉是能力倒退。故保留、移出动作行，并在任务文件与 commit message 里显式记录。
- 朗读是真正的能力删除，不是移动：Android 用户裁决「删除不需要的按钮…还有语音播放」，`ChatScreen.render` 注释写明 "speech has no learner-visible signal left"。
- `formatRelativeTime` 放在 `platform/datetime.ts`（时间展示唯一入口），不在组件里现算——`rules/frontend.md` 要求设备时区与时间展示走同一入口。

## Verification report

- Verdict: PASS（独立 verifier 复核，2026-10-14）
- Verifier evidence: 复核确认 `MessageView.tsx:235-273` 确为四纯图标 + 行尾 `formatRelativeTime`，Android 权威 `AnswerActionBar.java:110` 引文属实；重跑 `npx vitest run`（cwd=web/frontend）29 files / 234 tests 全绿、EXITCODE=0，与本任务证据序列一致。
- Evidence: （待独立 verifier 复核）
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除「动作行带文字标签」类断言，改为
  「四纯图标 + 时间」断言；版本切换的等价覆盖由 `ToolsAndBlocks.test.tsx` 的 T-053 用例继续承担（该用例
  改为直接断言版本行仍存在）。
- Gaps: Android 目前也没有版本切换入口（`onVersionPressed` 无调用方），Web 保留该能力属记录在案的偏差。
- Risks: ① 版本切换是记录在案的偏差（Android 暂无入口而 Web 保留），Android 落地新入口时需回来看齐。② 相对时间用 `Intl.RelativeTimeFormat`，与 Android `DateUtils` 的措辞不完全逐字一致（如 0 秒读作「现在」而非「0 分钟前」）。③ 翻译卡常驻渲染后，每条 AI 回答下方都会出现「翻译」按钮——Android 的翻译卡可见性由状态驱动，这里按「卡片自带入口」的折中实现，后续若 Android 明确只在翻译后显示再对齐。

## Handoff notes

- Completed: Android 动作行取证（XML + AnswerActionBar）；RED（3 红，用 HEAD 版实现重采）/ GREEN（9/9、全量 219/219、六项门禁 0）/ 四变异证伪并还原
- In progress: 交独立 verifier 复核（与 T-129 合并一轮）
- Blocker: 无
- Unblock condition: 不适用
- Next steps: verifier 复核 → 提交 → T-129 划词浮条（6 格：问AI/分叉/高亮/批注/复制/全选）→ T-130 推荐即发送

- Watch-outs: 不要为了「对齐」而删掉 Web 独有且被测的能力（版本切换）——按偏差记录处理。
