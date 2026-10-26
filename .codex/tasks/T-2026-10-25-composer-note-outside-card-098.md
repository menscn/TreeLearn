# Filename: T-2026-10-25-composer-note-outside-card-098.md
# Codex Harness Task: 输入卡下的「尚未接通」移到卡外，且只有宿主明确要才画（WH2 + WC3）

id: T-2026-10-25-098
title: 输入卡的说明句出卡、去默认（WH2 + WC3）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: web-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 60
shared_code_touched: false
depends_on: [T-2026-10-25-ui-readability-parity-requirement-086.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 WH2（重要）——主输入卡下沿那句「尚未接通」写在动作容器里；WC3（优化）——同一句「未接通」在第二屏又出现。
- Problem/background: `Composer` 把说明句画在 `<form class="composer">` 内部、紧贴动作按钮，读起来像按钮的副标题；同时组件还带一句硬编码的 `chat_composer_note` 兜底，于是首页与对话页各说一遍同一件事。一屏只需要一次能力边界说明。
- Users/scenarios: 首次进入首页、准备输入根问题的人；以及正在对话页追问的人。
- Expected outcome: 说明句移到卡片之外、8px 之下，且只有宿主传 `note` 才画——首页传，对话页不传；`chat_composer_note` 兜底文案删除。出现条件仍是空草稿（`value.trim() === ""`）。
- Core flow and reversibility: 只改 `Composer.tsx` 的说明句位置与条件、`workspace.css` 的 `.composer-help` 注释与行高、`zh-CN.ts` 的文案。回退＝把说明句放回 form 内并恢复兜底键。
- Inputs/outputs: 输入是评审板 WH2／WC3；输出是 `note?: string` 的显式契约、卡外说明句、首页一句新文案。
- State and exception handling: 有草稿时不画说明句，但两个动作仍禁用且可访问名里写明「尚未接通」——说明句退场不带走能力边界。
- Constraints and trade-offs: 说明句在卡外会改变坞高；对话页不传 note 后该行的显隐变化消失，`--dock-h` 的 ResizeObserver 读数更稳定。
- AI recommendation and rationale: 把「这张卡现在能做什么」交给宿主决定，比在组件里写死一句更接近事实——首页需要首次解释，对话页不需要重复。
- User-confirmation decisions: 22 条全做。
- AI-autonomous decisions: ① 保留空草稿才显示的规则（打字后问题不再存在）；② 首页文案调整为「发送与上传尚未接通 · 可从下方推荐问题或历史主题开始」；③ 行高 1.7 与 `--s2` 上边距，让卡外一行与卡片保持 8px 的呼吸。
- Acceptance evidence: `Composer.test.tsx` 的四条（卡外／纯空白／无 note 不画／退场后边界仍在），先红后绿。
- Facts: `Composer.tsx:94-99`（说明句在 `</form>` 之后）；`workspace.css:789`（`.composer-help`）；`zh-CN.ts:85`（首页新文案）、删除 `chat_composer_note`；测试 `Composer.test.tsx:53`、`:74`。
- Assumptions: 对话页此前依赖兜底文案；删除后由 `note` 未传自然消失（已用测试钉住）。
- Unknowns: 无。
- Risks: 若将来别的页面需要同一句，必须显式传 `note`，不会再「免费」得到——这是有意为之。
- Deferred: 不改输入卡内部的模型 chip、风格选择与引用条。
- Non-goals: 不做发送／上传接通；不改两个禁用动作的语义。
- Skipped non-critical questions and reason: 未问「说明句要不要可关闭」——它只在空草稿出现，打字即退场，不需要第二个关闭手势。

## Goal

让输入卡的说明句只由宿主声明、只在空草稿时出现在卡片之外，并且同一屏不重复同一句。

## Scope

### In scope
- `frontend/src/components/Composer.tsx` 的说明句位置与渲染条件。
- `frontend/src/styles/workspace.css` 的 `.composer-help`。
- `frontend/src/copy/zh-CN.ts` 的 `home_composer_note` 与 `chat_composer_note`。
- `frontend/test/components/Composer.test.tsx` 的四条判据。

### Out of scope
- 发送／上传能力；对话页输入坞的其它层（引用条、chips、模型 chip）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 说明句从卡内移到卡外，组件不再自带兜底文案。必须保留：空草稿才显示、纯空白算空、有草稿时不占一行、两个动作始终禁用且 `aria-label` 含「尚未接通」、`quoteSlot`／`personaSlot`／`statusSlot` 的位置。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `Composer.tsx:97`；消费者 `HomePage.tsx:52`（传 `note={t("home_composer_note")}`）与对话页（不传）；样式 `.composer-help`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 输入卡说明句成为宿主的显式声明；首页与对话页不再各说一遍同一句。
- Authoritative code/config/migration evidence: `Composer.tsx:97-99`；`workspace.css:789-795`；`zh-CN.ts:85`。
- Documentation surfaces checked: `.codex/rules/frontend.md`（文案与错误语义）；`frontend/README.md`（不述组件 props）。
- Documentation files updated: 无需改文档；契约写在 `Composer.tsx` 的头注释里。
- Producer/consumer/sibling/compatibility paths checked: `grep -rn "chat_composer_note" frontend/src` 无命中；`grep -rn "note=" frontend/src/pages` 只有 HomePage 一处。
- Superseded phrases/examples reverse-searched: 旧注释「说明句贴在输入下方左对齐」已替换为「卡外 8px」。
- `documentation impact: none` reason and evidence: 组件内部契约变化，外部页面已同步；没有对外 API 或文档正文引用旧写法。
- Historical/PRD handling: 不改写 087「输入坞焦点说明」的历史；本片只决定说明句画在哪与谁来决定。

## Acceptance criteria

- [x] 说明句是 `.composer` 的兄弟节点而不是子节点；无 `note` 时 DOM 里没有 `.composer-help`。
- [x] 有 `note` 且草稿为空（含纯空白）时才出现，文案逐字等于宿主传入值。
- [x] 输入后说明句退场，两个动作仍 `disabled` 且可访问名含「尚未接通」。
- [x] `chat_composer_note` 从文案表删除；首页使用新的 `home_composer_note`。

## TDD execution record

### RED — failing test first
- Test/scenario: 四条——空草稿且有 note 时说明句在卡外、纯空白仍算空态、宿主不给 note 就一句都不画、说明退场后能力边界仍在。
- Command: `cd frontend && npx vitest run test/components/Composer.test.tsx`
- Exit code: 1
- Observed failure: `Tests 2 failed | 3 passed (5)`——`说明句不再占卡片内部的地面: expected <form class="composer">…(3)</form> to be null`、`expected <p class="composer-help"></p> to be null`（`098-red.txt`）。
- Failure reason validated: 两条红分别钉住「位置」与「默认渲染」；另三条（纯空白、退场、边界）在红态已绿，说明本片的失败只来自说明句的新契约，不牵动输入与动作。

### GREEN — minimum implementation
- Implementation: 说明句移出 `<form>`，条件改为 `note !== undefined && value.trim() === ""`；删除 `chat_composer_note`；首页文案改为「发送与上传尚未接通 · 可从下方推荐问题或历史主题开始」；`.composer-help` 行高 1.7。
- Command: `cd frontend && npx vitest run test/components/Composer.test.tsx`
- Exit code: 0
- Passing evidence: `Tests 5 passed (5)`、`EXITCODE=0`（`098-green.txt`）。
- Regression evidence: 前端全量套件绿（当次整跑，见 `frontend-full-suite.txt`）；首页与对话页的渲染测试未新增红。

### REFACTOR — optional
- Cleanup: `Composer` 不再 import 兜底文案键；`zh-CN.ts` 删除死键。
- Regression evidence: 重跑同一条命令，5 条保持绿。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 两条各红一次。
- Stop condition hit: 5 条全绿。
- Handoff: 105 的整跑包含本片。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 「宿主不给就不画」是契约，不是技巧：说明句说的是这张卡当前能做什么，只有知道这一屏是否首次解释的宿主才答得准。
- 说明句放在 `</form>` 之后、`.composer` 容器的直接子级，使它对表单提交与焦点顺序都无影响。
- 保留「空草稿才显示」：一旦开始打字，「这里能不能发」这个问题已经被动作按钮的禁用态回答。

## Verification report

- Verdict: PASS
- Evidence: `098-red.txt`（2 failed / 5）、`098-green.txt`（5 passed / EXITCODE=0）；前端全量整跑绿。
- Changed/deleted assertion rationale and equivalent behavior coverage: 删除的 `chat_composer_note` 断言由「无 note 不画」的新判据等价覆盖。
- Gaps: 未截图核验对话页输入坞的高度变化；由 105 的统一截图承担。
- Risks: 新页面若忘记传 `note` 会「没有说明句」——这正是契约，不应在组件里恢复兜底。

## Handoff notes

- Completed: 说明句出卡、去默认、首页文案更新。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 无。
- Watch-outs: 不要把兜底文案加回 `Composer`——那会让对话页再次重复同一句。
