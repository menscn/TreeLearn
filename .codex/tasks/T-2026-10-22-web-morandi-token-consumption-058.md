# Filename: T-2026-10-22-web-morandi-token-consumption-058.md
# Codex Harness Task: Web 消费莫兰迪玻璃 token 与整线回归

id: T-2026-10-22-058
title: Web 消费莫兰迪玻璃 token 与整线回归
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-22-scheme-a-morandi-requirement-052.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-22-morandi-token-chain-053.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 052 已拍板方案 A 结构 1:1 只落在 Android；Web 本轮只消费同一批莫兰迪玻璃 token 并做整线回归，不追结构 1:1。053 已把 `--glow-rose`、`--glow-lilac`、`--tint-rose`、`--tint-lilac`、`--tint-sand`、`--tint-sage` 贯通到生成的 `tokens.css`，Web 消费被明确留给本片。
- Problem/background: Web 的 `glass.css` 目前只消费 2026-10-21 的玻璃与 mist token；053 新增的两处莫兰迪柔光与 tint 在 Web 侧没有任何消费者，生成的 token 处于「存在但未使用」状态，方案 A 的莫兰迪增量在 Web 上读不出来。
- Users/scenarios: 浏览器/窄窗下的单人学习者，桌面与手机宽度共用同一套页面；场景是首页、对话、分支面板、学习库与设置页的材质观感，不涉及新交互。
- Expected outcome: Web 共享场景新增玫红与丁香两处低饱和柔光（位置与 Android `PaperRuleBackdrop` 一致：92%/52% 与 6%/24%），推荐标签使用 `--tint-sage` 作为非文字表面色；所有数值仍来自 `tokens.css`，不出现裸色值，布局、断点、z-index、滚动容器与命中区域一律不变。
- Core flow and reversibility: 只改 `frontend/src/styles/glass.css` 并新增一支结构测试。回退为删除两处渐变与 `.rec-tag` 覆盖、删除测试文件。
- Inputs/outputs: 输入是 `tokens.css` 中 053 生成的六个新 token 与既有 glass token；输出是 Web 场景的两处柔光与一处 tint 表面消费。不新增数据源、接口或依赖。
- State and exception handling: 纯 CSS 材质层，不读写业务状态；`prefers-reduced-transparency` 与不支持 `backdrop-filter` 的回退分支保持原样，柔光与 tint 不改变任何文本对比语义。
- Constraints and trade-offs: 不改 Web 结构、组件、文案与断言语义；tint 只用于非文字表面（标签底色），正文与按钮文字仍用 ink/green 语义色；不手改生成的 `tokens.css`；不新增依赖。
- AI recommendation and rationale: 只消费两枚 glow 与一枚同族 tint（`--tint-sage` 落在既有 `.rec-tag` 上），其余 tint 保留给 Android 的推荐卡图标底与分支标签，避免在 Web 结构未对齐时为了「用满 token」而发明新表面。柔光位置直接镜像 Android，保证两端场景同源。
- User-confirmation decisions: ① A + 莫兰迪毛玻璃；② 允许内置中文衬线；③ 接不了真实数据就删；④ 保留真机状态栏；⑤ 按 Harness 实现。
- AI-autonomous decisions: ① Web 只加材质、不改结构；② 新增 `.rec-tag` 的 tint 覆盖放在 `glass.css`，维持「材质层最后加载」的单一职责；③ 不消费 `--tint-rose`/`--tint-lilac`/`--tint-sand`，留给 Android 已定义的用途，不新造 Web 表面。
- Acceptance evidence: 新 `frontend/test/styles/morandi-material.test.ts` RED→GREEN；`pnpm --dir frontend verify` 16/16；Vitest 全绿；既有 `glass.test.ts` 的结构与加载顺序断言不回归。
- Facts: `frontend/src/styles/glass.css` 是最后加载的材质层（`main.tsx` 第 15 个 import）；`tokens.css` 已含六个新 token（053）；`PaperRuleBackdrop` 的 rose/lilac 圆心与半径；`.rec-tag` 在 `app.css` 已有 padding/圆角/文字色，只需覆盖背景。
- Assumptions: 浏览器对 `radial-gradient` 叠加的渲染开销可忽略；`--tint-sage` 与 `--green-faint` 亮度接近，不会改变标签文字的对比等级。
- Unknowns: 玫红/丁香柔光在低色域显示器上的可见度；tint-sage 与既有 green-faint 的肉眼差异是否足以读作「莫兰迪增量」。
- Risks: 柔光叠加可能让 mist 更亮，需确认正文对比仍由不透明阅读面保证；新增 CSS 若写在 `glass.css` 之外会破坏「最后材质层」的加载契约。
- Deferred: Web 结构 1:1、深色模式、实时模糊、其余 tint 在 Web 的消费。
- Non-goals: 改组件、路由、状态、文案、测试语义、依赖；手改 `tokens.css`；把 tint 用到文字上。
- Skipped non-critical questions and reason: 未追问六枚 token 是否都要在 Web 有消费者；Web 结构未对齐时强行铺满会制造无来源的新表面，按「只消费已有表面」处理并在此登记。

## Goal

让 Web 的共享玻璃场景读得出 053 新增的莫兰迪柔光，并让既有推荐标签消费一枚 tint token，同时整条 Web 校验线保持全绿。

## Scope

### In scope
- `frontend/src/styles/glass.css`：`body` 新增 `--glow-rose` 与 `--glow-lilac` 两处柔光；`.rec-tag` 背景改用 `--tint-sage`。
- 新增 `frontend/test/styles/morandi-material.test.ts`。
- `frontend/README` 或设计系统说明中与 Web 材质消费相关的当前态（仅当既有正文需要同步）。

### Out of scope
- Web 结构 1:1、组件与路由改造、深色模式、实时模糊。
- `tokens.css` 生成逻辑与 token 值；Android 侧（055–057 已完成）。
- 其余 tint token 在 Web 的消费。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 只改共享场景的背景叠加与一枚标签底色。页面结构、断点、交互命中区、滚动、文案与状态全部不变；玻璃回退分支与减少透明度分支保持原样。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者是 `tokens.css`（由 `.codex/scripts/dev/gen_design_tokens_css.py` 生成）；消费者是 `glass.css`；加载顺序由 `frontend/src/main.tsx` 固定；`frontend/test/styles/glass.test.ts` 是既有结构判据。
- Native bridge or independent-client compatibility/update requirements: 不涉及。
- Behavior-neutral non-applicability reason, if applicable: CSS 材质覆盖不改变 DOM 层级与任何事件绑定。

## Documentation impact record

- Changed runtime facts: Web 共享场景新增玫红/丁香两处柔光；推荐标签底色改为 `--tint-sage`。
- Authoritative code/config/migration evidence: `frontend/src/styles/glass.css`、`frontend/src/styles/tokens.css`（生成物）、`frontend/src/main.tsx`。
- Documentation surfaces checked: `frontend/README.md`、`Docs/04-ux-design/design-system/README.md`、`.codex/context/source-of-truth.md`。
- Documentation files updated: `Docs/04-ux-design/design-system/README.md`（新增 058 Web 消费记录；修正 tint 消费面与衬线适用范围两处过时表述）。
- Producer/consumer/sibling/compatibility paths checked: `glass.test.ts`、`frontend/scripts/verify.mjs`、`check_design_tokens.py`、`main.tsx`。
- Superseded phrases/examples reverse-searched: 「Web glass.css 只消费 2026-10-21 玻璃 token」「Web 场景没有莫兰迪柔光」。
- `documentation impact: none` reason and evidence, if applicable: 若设计系统 README 已把六枚 token 记为 Web 可用，则本片只改消费点，不需要新增文档正文。
- Historical/PRD handling: 保留 2026-10-21 Web 玻璃工作区的结论；本片只追加 2026-10-22 的莫兰迪消费。

## Acceptance criteria

- [x] 新 `morandi-material.test.ts` 断言 `glass.css` 消费 `--glow-rose`、`--glow-lilac` 与 `--tint-sage`，且仍无裸色值；RED 非 0、GREEN 为 0，命令一致。
- [x] `pnpm --dir frontend verify` 16/16、`OVERALL_EXITCODE=0`，Vitest 全绿。
- [x] 既有 `glass.test.ts` 的加载顺序、回退分支与选择器覆盖断言不回归。
- [x] 未改结构、断点、z-index、滚动容器与命中区域；未手改 `tokens.css`。
- [x] 未自动提交或推送；未新增依赖。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/styles/morandi-material.test.ts`：共享场景必须消费 `--glow-rose` 与 `--glow-lilac`，推荐标签必须消费 `--tint-sage`，`glass.css` 不得出现裸色值。
- Command: `pnpm --dir frontend exec vitest run test/styles/morandi-material.test.ts`
- Exit code: 1
- Observed failure: `Test Files 1 failed (1)`、`Tests 2 failed | 1 passed (3)`（`artifacts/evidence/android-scheme-a-parity-2026-10-22/058-red.txt`）。失败一：`expected '...' to match /background-image:[^;]*var\(--glow-rose\)/s`（第 37 行）；失败二：`expected '...' to match /\.rec-tag[^{]*\{[^}]*var\(--tint-sage\)/s`（第 43 行）。token 存在性那条通过，说明缺的是消费而不是生成。
- Failure reason validated: 失败来自 `glass.css` 的真实文本：当时 `body` 的 `background-image` 只有 white/green/sand 三处柔光，`.rec-tag` 也没有任何材质层覆盖。断言读取的是生产 CSS 源文件，不依赖浏览器环境，因此 RED 是「消费未实现」而非测试自身错误。

### GREEN — minimum implementation
- Implementation: `frontend/src/styles/glass.css` 的 `body` 背景在纸纹之后、既有三处柔光之前新增两枚 `radial-gradient`，分别消费 `--glow-rose`（92%/52%）与 `--glow-lilac`（6%/24%）；新增 `.rec-tag { background: var(--tint-sage); }` 覆盖层，文字色仍由 `app.css` 的 `--green-deep` 决定。未改 `main.tsx` 的加载顺序、未改任何布局属性。
- Command: `pnpm --dir frontend exec vitest run test/styles/morandi-material.test.ts`
- Exit code: 0
- Passing evidence: `✓ test/styles/morandi-material.test.ts (3 tests)`、`Tests 3 passed (3)`、`EXITCODE=0`（`058-green.txt`）。
- Regression evidence: `pnpm --dir frontend verify` 16/16、`VERIFY PASS`、`OVERALL_EXITCODE=0`（`058-verify.txt`）；全量 Vitest 19 个文件 / 116 例全绿（`058-vitest-all.txt`）；既有 `glass.test.ts` 的加载顺序、回退分支与选择器断言在 116 例内原样通过。

### REFACTOR — optional
- Cleanup: 无返工；实现一次 write 即 GREEN。仅修正 `Docs/04-ux-design/design-system/README.md` 中两处过时表述（tint 消费面、衬线适用范围），不改样式。
- Regression evidence: 同 GREEN 的 `058-verify.txt` 与 `058-vitest-all.txt`（文档改动不进入 Web 构建产物）。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行，未申请例外。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 柔光位置镜像 Android `PaperRuleBackdrop`：rose 在 92%/52%，lilac 在 6%/24%；CSS 中排在既有 white/green/sand 之前、纸纹之后，保证叠放次序与 Android 的绘制次序一致。
- `.rec-tag` 只覆盖 `background`；文字仍为 `--green-deep`，不把 tint 用作文字色。
- 不在 `glass.css` 里写任何十六进制或 `rgb()` 字面量；所有颜色走 `var(--token)`。
- 不改 `frontend/src/main.tsx` 的样式加载顺序。

## Verification report

- Verdict: PASS
- Evidence: `058-red.txt`（定向 RED，3 例 2 败，rc=1）；`058-green.txt`（同一命令 3/3，rc=0）；`058-vitest-all.txt`（19 文件 116 例全绿）；`058-verify.txt`（`verify steps=16 ok=16 bad=0 fault=0`、`VERIFY PASS`、`OVERALL_EXITCODE=0`）。浏览器实拍未在本片重跑：本片只改 CSS 材质层且 059 会汇总证据；柔光的实际观感与 `--tint-sage` 的肉眼差异记为待 059/人工复核。
- Changed/deleted assertion rationale and equivalent behavior coverage: 未修改或删除任何既有断言；`glass.test.ts` 的四条结构判据原样通过。新增测试只加判据，不替换旧覆盖。
- Gaps: Web 只消费两枚 glow 与 `--tint-sage`；`--tint-rose`/`--tint-lilac`/`--tint-sand` 生成后仍无消费者，Android 侧也没有 tint 消费。这是「不为用满 token 而发明表面」的有意决定，已在设计系统 README 写明；若后续要读作完整的莫兰迪增量，需要一次独立的表面设计片。
- Risks: 柔光叠加可能让共享场景更亮，正文对比仍由 86% 不透明阅读面保证；低色域屏上 rose/lilac 可能几乎不可见；`--tint-sage` 与 `--green-faint` 亮度接近，标签变化偏克制。

## Handoff notes

- Completed: `glass.css` 消费两处莫兰迪柔光与 `--tint-sage`；新增 3 条结构判据；Web 全量 116 例与 `verify` 16/16 通过；设计系统 README 已同步当前消费面。
- In progress: 无（058 已 verified）。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 059 整跑 Android 全量 JVM + 三份 APK + `verifyNoKotlinDex`，`adb install -r` 后截四屏（首页/对话/分支导航/学习库），写证据 README，跑 `validate_tasks.py`、`run_all_guards.py --tier core` 与 metrics，最后关闭 052。
- Watch-outs: ① 不要为了「用满六枚 token」在 Web 新造图标底或标签表面，Web 结构 1:1 不在本轮范围；② `glass.css` 必须保持最后加载且只覆盖材质，任何布局属性都会破坏与 `pages.css` 的分层契约；③ 不要手改 `tokens.css`，它是生成物，漂移由 `check_design_tokens.py` 逐字节判红。
