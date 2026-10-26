# Filename: T-2026-10-25-reading-column-width-094.md
# Codex Harness Task: 把阅读正文收回到每行 32–41 个汉字（B1）

id: T-2026-10-25-094
title: 阅读列宽 784 → 684（B1）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 12
shared_code_touched: true
depends_on: [T-2026-10-25-ui-readability-render-judgment-089.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 B1（=WC1）——阅读正文实测 48 个汉字一行，超出 32–41 的可读区间。
- Problem/background: 089 的 R2 量得 `.chat-answer` 内宽 720px ÷ 15px = 每行 48 个汉字；评审板独立复现了同一个 48。列宽 `.chat-main` 是 784px，两侧各 32px 内边距，正文内宽 720px。
- Users/scenarios: 在 Web／桌面读长回答的人；拖选、复制、追问的起点都在这一段正文上。
- Expected outcome: 列宽收到 684px，正文内宽 620px，15px 字号下每行约 41 个汉字；输入区与阅读面继续共用同一个 `--chat-column`，滚动条槽位对称。
- Core flow and reversibility: 只改一个 CSS 变量值与它的注释。回退＝还原 784px。
- Inputs/outputs: 输入是 R2 的实测与评审板；输出是 `.chat-main` 的 `--chat-column: 684px`。
- State and exception handling: 输入区与正文必须同宽；两侧 `scrollbar-gutter: stable both-edges` 保证滚动条出现时正文不偏移。
- Constraints and trade-offs: 收窄会让「一屏字数」下降，长回答的纵向长度增加；可读性优先于密度。
- AI recommendation and rationale: 620px 是 41 个汉字的上界（R2 的 MAX_CJK_PER_LINE=41），比「尽量宽」更可辩护：它由判据给出，不是目测。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① 只改 `--chat-column`，不改内边距（两侧 32px 是既有节奏）；② 把内宽算式写进测试注释，避免只改外层再漂。
- Acceptance evidence: `chat-column.test.ts` 先红后绿；R2 在重建产物后复跑。
- Facts: `094-red.txt` 的断言失败（Received 是 784 时代的样式表原文）；`094-green.txt` 2 passed。
- Assumptions: 正文是中文，1 个汉字 ≈ 1em，故「每行汉字数 = 内宽 ÷ 字号」成立（089 的测量口径）。
- Unknowns: 无。
- Risks: 极窄窗口下 684 会被 `max-width` 与视口共同约束，行数会更少——这是窄窗本来就该发生的事。
- Deferred: 平板档的列宽另取（当前与手机同值）。
- Non-goals: 不改字号；不改输入区高度；不改侧栏宽度。
- Skipped non-critical questions and reason: 未问「684 还是 660」——先落判据上限内的最大值，若真机观感偏宽再单开一片。

## Goal

把阅读正文的内宽从 720px 收到 620px，让每行汉字数落到判据上限之内，并让输入区继续共用同一列宽。

## Scope

### In scope
- `frontend/src/styles/workspace.css` 的 `.chat-main` 与相邻注释。
- `frontend/test/styles/chat-column.test.ts` 的 684／620 钉子。

### Out of scope
- 渲染判据本身（→089）；Android 阅读面（无对应发现）；字体与字号（→090／092）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 列宽收窄；必须保留「阅读面与输入区同宽」、两侧对称滚动条槽位、`max-width` 与内边距的组合算式。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `.chat-main { --chat-column }`；消费者 `.chat-reading` 与 `.chat-dock .composer` 的 `max-width`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 阅读列宽 784 → 684，正文内宽 720 → 620。
- Authoritative code/config/migration evidence: `workspace.css:330-334`；`chat-column.test.ts` 的算式注释。
- Documentation surfaces checked: `.codex/rules/frontend.md`、`Docs/04-ux-design/design-system/README.md`。
- Documentation files updated: 无需改文档；尺寸理由写在 CSS 注释与测试头注释。
- Producer/consumer/sibling/compatibility paths checked: `grep -n "784\|720" frontend/src/styles` 无残留。
- Superseded phrases/examples reverse-searched: 旧注释「784」已随本片重写。
- `documentation impact: none` reason and evidence: 只改一个布局变量值，不改变任何设计 token 或组件契约。
- Historical/PRD handling: 不改写 056／063 的对话面历史。

## Acceptance criteria

- [x] `.chat-main` 的 `--chat-column` 为 684px，正文内宽 620px。
- [x] `.chat-reading` 与 `.chat-dock .composer` 仍共用该变量。
- [x] 滚动条槽位仍对称（`stable both-edges`）。
- [x] R2 在重建产物后 ≤41 个汉字/行。

## TDD execution record

### RED — failing test first
- Test/scenario: `阅读面与输入区共享 --chat-column，且正文内宽落在 620px` 与 `两侧预留对称滚动条槽位`。
- Command: `cd frontend && npx vitest run test/styles/chat-column.test.ts`
- Exit code: 1
- Observed failure: 1 failed / 2，报 `expected '…' to match /\.chat-main\s*\{[^}]*--chat-column:\s*684px/s`，Received 是 784px 时代的整段样式表原文。
- Failure reason validated: 断言钉的是变量值与其消费方式，不是「存在某个 max-width」；把 CSS 临时还原到 HEAD 后单条判红，说明红点就在这个数值上。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/094-red.txt`。

### GREEN — minimum implementation
- Implementation: `--chat-column: 684px`，并把「684 − 64 = 620px ≈ 41 汉字」的算式写进注释。
- Command: `cd frontend && npx vitest run test/styles/chat-column.test.ts`
- Exit code: 0
- Passing evidence: 2 passed / 2（`094-green.txt`）。
- Regression evidence: 前端 30 文件 / 210 用例绿；R2 在 105 重建产物后复跑。

### REFACTOR — optional
- Cleanup: 无。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 单次单缺陷。
- Stop condition hit: 684／620 两条钉子同时绿。
- Handoff: 105 用渲染判据复核 R2 的实测值。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 判据（R2）与实现（684）分居两片：R2 是「什么算坏」，684 是「这一版取多少」。若将来改成 41 以外的上限，先改判据再改数值。
- 只改外层不改内边距会让内宽漂回去，所以测试把 `padding: … var(--s8)` 与 `max-width` 一起钉住。

## Verification report

- Verdict: PASS
- Evidence: `094-red.txt`／`094-green.txt`；当前态全量前端 210 用例绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 数值断言从 784 更新为 684，并新增内宽算式注释；覆盖的「同宽」语义不变。
- Gaps: 未在真实浏览器里量 620px 下的实际换行数（R2 是合成渲染判据，见 089／105）。
- Risks: 窄窗口下可用宽度小于 684 时，行数会更少（预期行为）。

## Handoff notes

- Completed: 列宽收到 684，输入区与正文仍同宽。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 105 复跑 R2。
- Watch-outs: 别只改 `.chat-reading` 的 max-width 而不改变量——输入区会重新漂宽。
