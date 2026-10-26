# Filename: T-2026-10-25-caption-tier-twelve-092.md
# Codex Harness Task: 把小字层抬到 12，并让 meta 与导航不再挤同一档（X3／WC2）

id: T-2026-10-25-092
title: 小字层 12px／12sp（X3／WC2）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 30
shared_code_touched: true
depends_on: [T-2026-10-25-ui-readability-render-judgment-089.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 X3（标签层 11px／11sp）与 WC2（字号档中段空缺，meta 与导航挤同一档）。089 的实测把 X3 更正为：Web 侧栏导航标签已是 13px，红的是 Android 底部标签栏（`screen_tab_bar.xml` 用 `@dimen/text_cap`）。
- Problem/background: `--fs-cap` 11px 与 `text_cap` 11sp 是中文小字地板以下的一档；在 1080p 手机与 Windows 桌面上中文横画先掉。`fs-cap` 有 38 处 Web 消费者、`text_cap` 有 84 处 Android 消费者，所以这是 token 级改动，必须四方同批。
- Users/scenarios: 读底部标签栏、meta、森林计数、侧栏摘要的人。
- Expected outcome: 最小可见字号 ≥12px／12sp；导航标签不落在最小档；`tokens.json → tokens.css → dimens.xml` 三处同批，`check_design_tokens.py` 绿。
- Core flow and reversibility: 改 `tokens.json` 的 `fs-cap`，重生成 `tokens.css`，并同步 `dimens.xml` 的 `text_cap`。回退＝三处一起回 11，不许只回一处。
- Inputs/outputs: 输入是 089 的 R1／R3 实测；输出是 token 三件套的新值。
- State and exception handling: 只回退一处会让 `check_design_tokens.py` 判红——这是设计上的保险，不是缺陷。
- Constraints and trade-offs: 38 + 84 处消费者里若有按像素对齐的几何会跟着漂；不为「好看」放宽地板。
- AI recommendation and rationale: 086 的 AI 自主决定 ②：走「把 cap 提到 12」而不是「把导航降到 13」，因为 meta 类才是多数，单点降导航会让 meta 仍留在 11。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① 12 是地板不是建议值；② Android 与 Web 同值（12sp／12px）；③ 图书馆页面的 13／14／12.5／12 由 102 负责，本片只动 `text_cap`。
- Acceptance evidence: `theCaptionTierIsTwelveSp` 先红后绿；`check_design_tokens.py` 0 failed；前端与 Android 全量套件绿。
- Facts: `092-red.txt`（`SchemeAHomeShellLayoutTest > theCaptionTierIsTwelveSp FAILED`，10 tests / 1 failed）；`092-green.txt`（BUILD SUCCESSFUL，EXITCODE=0）；`tokens.css` 头注释 token 数 71、内容 sha256 `06b0c22b…`。
- Assumptions: 12px 中文在 1080p 与 Windows 桌面上都可读（089 的 R1 地板即此值）。
- Unknowns: 无。
- Risks: 个别按 11px 对齐的几何会有一档位移；已由全量视觉测试（morandi-material、recent-row、chat-column）覆盖到类名级，未做像素级截图比对。
- Deferred: 无。
- Non-goals: 不改字号阶梯的其它档；不改行高。
- Skipped non-critical questions and reason: 未问「地板取 12 还是 13」——12 是中文小字的通行地板，13 会连带改导航与正文关系，超出 X3 的范围。

## Goal

把一个 token 级的小字地板从 11 抬到 12，并保证四方 token 链与两端消费者同时到位。

## Scope

### In scope
- `Docs/04-ux-design/design-system/tokens.json` 的 `fs-cap`；生成的 `frontend/src/styles/tokens.css`；`android/app/src/main/res/values/dimens.xml` 的 `text_cap`。
- `SchemeAHomeShellLayoutTest.theCaptionTierIsTwelveSp` 这条钉子。

### Out of scope
- 图书馆页面的 13／14／12.5／12（→102）；首页 22sp 标题与 22dp 段距（→100）；渲染判据本身（→089）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 最小档从 11 提到 12；必须保留导航档 13px 与它之上的阶梯、行高倍数、Android 的 sp 单位。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `tokens.json:fs-cap` → `tokens.css:--fs-cap` 与 `dimens.xml:text_cap`；消费者 38 处 `var(--fs-cap)`（含 `.recent-meta`、`.recent-summary`、`.composer-help`、`.side-heading`、`.suggestion-tag`、`.home-count`、`.chat-meta`）与 84 处 `@dimen/text_cap`（含底部标签栏、卡片 meta）。
- Native bridge or independent-client compatibility/update requirements: 无；桌面壳随产物面自动更新。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 中文小字地板 11 → 12（两端同值）。
- Authoritative code/config/migration evidence: `tokens.json` 的 `fs-cap.note` 记录了改动理由与两端对应关系。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md` 的字号表、`.codex/rules/ux-design.md`。
- Documentation files updated: `tokens.json` 的 `fs-cap.note`；`README.md` 的字号段未单列 cap 值，未改。
- Producer/consumer/sibling/compatibility paths checked: `check_design_tokens.py` 的四方逐字节比对。
- Superseded phrases/examples reverse-searched: `grep -rn "11px\|11sp" Docs/04-ux-design/design-system` 只剩历史说明。
- `documentation impact: none` reason and evidence: 不适用。
- Historical/PRD handling: 不改写 053 的 token 链历史。

## Acceptance criteria

- [x] `text_cap` 12sp 且 `--fs-cap` 12px，`tokens.json` 同值，`check_design_tokens.py` 0 failed。
- [x] Android 底部标签栏不再落在 11sp 档（改由 `text_cap` 12sp 承担）。
- [x] 导航标签仍高于最小档（Web 13px，实测见 089 的 R3）。
- [x] 两端全量套件绿，未出现按 11px 对齐的类名级回退。

## TDD execution record

### RED — failing test first
- Test/scenario: `theCaptionTierIsTwelveSp` 读 `dimens.xml`，要求 `text_cap` 恰为 12sp。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.SchemeAHomeShellLayoutTest.theCaptionTierIsTwelveSp"`
- Exit code: 1
- Observed failure: `SchemeAHomeShellLayoutTest > theCaptionTierIsTwelveSp FAILED`（`AssertionError`，历史转录里 10 tests / 1 failed）。
- Failure reason validated: 断言是精确等值（`<dimen name="text_cap">12sp</dimen>`），把 `text_cap` 临时改回 11sp 后立刻判红，说明它钉的就是这一档而不是「存在某个 dimen」。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/092-red.txt`。

### GREEN — minimum implementation
- Implementation: `tokens.json` 的 `fs-cap` 改 12px 并写理由；用生成器重出 `tokens.css`（token 数 71）；`dimens.xml` 的 `text_cap` 改 12sp。
- Command: `cd android && ./gradlew :app:testDebugUnitTest --offline --rerun-tasks --tests "com.treelearn.app.screen.SchemeAHomeShellLayoutTest.theCaptionTierIsTwelveSp"`
- Exit code: 0
- Passing evidence: BUILD SUCCESSFUL（`092-green.txt`）；`check_design_tokens.py` 0 failed（10 条 OK，含「四方一致，含逐字节重新生成比对」）。
- Regression evidence: 前端 30 文件 / 210 用例绿；Android 108 套 / 949 用例绿；089 的 R1 在重建产物后复跑（见 105）。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 单次单缺陷。
- Stop condition hit: 钉子绿 + 四方链 0 failed。
- Handoff: 102 继续把图书馆的 13／14／12.5／12 落到它自己的 token 上。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 只回退一处（例如只改 `tokens.css`）会让 `check_design_tokens.py` 判红——这是有意设计的护栏。
- WC2 的「中段空缺」不是新增一档，而是把最小档抬高后，meta（12）与导航（13）自然分开；图书馆页面的 12.5／13／14 由 102 补齐。
- Android 底部标签栏此前直接消费 `text_cap`，所以本片一处 token 改动同时修掉了它——这正是 089 更正 X3 落点后的实际形状。

## Verification report

- Verdict: PASS
- Evidence: `092-red.txt`／`092-green.txt`；`check_design_tokens.py` 10 条 OK / 0 failed（当次终端读数，命令 `python -X utf8 .codex/scripts/dev/check_design_tokens.py`）。
- Changed/deleted assertion rationale and equivalent behavior coverage: 新增一条等值钉子；无删除。
- Gaps: 未做 1080p 真机截图比对 12sp 的实际观感（地板值由 089 的合成渲染判据给出）。
- Risks: 11→12 会让个别按 11px 对齐的间距出现一档位移。

## Handoff notes

- Completed: cap 档两端同值 12，token 四方链绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 105 重建产物后由渲染判据复核 R1。
- Watch-outs: 改这一档必须四方同批，别只改生成物。
