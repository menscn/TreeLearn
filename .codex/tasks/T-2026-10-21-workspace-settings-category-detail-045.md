# Filename: T-2026-10-21-workspace-settings-category-detail-045.md
# Codex Harness Task: 设置分类＋详情（只读真实配置）

id: T-2026-10-21-045
title: 设置分类＋详情
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-web-learning-workspace-requirement-041.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 300
shared_code_touched: true
depends_on: [T-2026-10-21-workspace-shell-and-state-042.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 工作区外壳已由 042 交付。本片把旧设置快照入口替换成「分类＋详情」页，六类真实配置只读呈现，写入控件明确禁用。
- Problem/background: 现有设置只通过 `SnapshotScreen` 按采集坐标还原，没有分类导航、没有详情结构、没有只读说明；夹具里有六类真实配置（API KEY/提供商/模型/搜索服务/语音/文件管理），但 Web 端没有任何写入接口。
- Users/scenarios: 用户从侧栏进入设置，在左侧分类间切换查看 API Key 掩码、提供商、模型、搜索服务、语音与文件占用；离开去对话再回来，仍停在原分类与滚动位置；想修改时看到明确的只读说明与禁用控件，而不是一个看似能保存的按钮。
- Expected outcome: 设置页左侧分类列表（六类真实名称＋摘要），右侧结构化详情；每类只呈现夹具里真实存在的字段；所有输入、开关、保存、删除、试听禁用并标注「Web 端尚未接通」；分类与滚动位置进仓库并在返回时恢复；窄窗下分类变为可横向滚动的顶部列表或抽屉式呈现，不遮挡详情。
- Core flow and reversibility: 侧栏「设置」→ 默认 API KEY 分类 → 切换分类 → 滚动详情 → 离开 → 返回恢复分类与位置。回退为删除 `SettingsPage` 与相关测试/样式并恢复 `WorkspaceRouter` 的设置分支；仓库字段可保留（042 已建）。
- Inputs/outputs: 输入是 `WorkspaceData.settings`（六类真实夹具模型）与 `WorkspaceState.settingsCategory/settingsScroll`；输出是分类列表、详情块与禁用控件。不写配置、不读密钥明文、不发网络。
- State and exception handling: 某类夹具缺失时该类显示 `state_fixture_unavailable`，分类仍可切换；默认分类固定为 `api-key`（存在则）；未知分类回落第一类；密钥只显示夹具已掩码值，不尝试补全；所有写入动作 `disabled` 且 `aria-label`/`title` 含「尚未接通」，页面顶部有只读说明。
- Constraints and trade-offs: 只读呈现，不模拟保存成功；不引入表单库、不写 localStorage；不复制示意稿的 `demo save`/toast；分类名与必要状态文字保留（不能用纯图标替代 API KEY、提供商管理等名称）；开关用只读状态徽标呈现而不是可操作 switch，避免假交互。
- AI recommendation and rationale: 设置页的价值是把真实已采集配置摆清楚，而不是做一个不能落盘的假表单。所有字段来自夹具，写入控件统一禁用并解释，比「看起来能改、点了没反应」诚实。
- User choice and custom additions: 图标优先（保存、删除、试听、显示密钥等按钮图标化并禁用）；分类名保留文字；真实浏览器验证键盘与焦点；不新增依赖；不改 Android。
- AI-autonomous decisions: ① 六个分类顺序按设置夹具；② 默认分类 `api-key`；③ 详情字段按夹具行序线性读取，不做坐标还原；④ 开关渲染为 `role="img"`/状态徽标＋文字，不用 `role="switch"` 以免可操作暗示；⑤ 分类切换保留各类滚动位置（`settings:<category>`）；⑥ 只读说明固定在详情顶部，不重复在每字段。
- User-confirmation decisions: 见父卡；「设置只读」由父卡登记，本片不再询问。
- Acceptance evidence: 新增 `test/pages/SettingsPage.test.tsx` 的 RED/GREEN；六类分类与真实字段、禁用原因、分类/滚动恢复断言；真实浏览器证据在 046。
- Facts: `settings` 夹具 35 节点，六类：API KEY · DeepSeek、提供商管理、模型设置、搜索服务、语音、文件管理；`settingskey` 有掩码、可用状态、删除、替换、保存；`provideradmin` 有 DeepSeek、掩码地址、1 个模型、开关；`modelsettings` 有聊天/快速/翻译模型、思考预算、聊天建议开关；`searchservicesettings` 有联网搜索开关、智谱选中、Tavily、博查、未配置/未保存 Key、检索结果数；`voicesettings` 有系统 TTS、试听；`filemanager` 有已占用 0 B 与空文本。
- Assumptions: 六个设置子页夹具都属于当前配置快照；开关的真实状态以夹具描述为准（例如联网搜索默认关闭）。
- Unknowns: 设置写入接口、密钥安全存储、语音试听能力均未接通。
- Risks: 旧设置经由 `SnapshotScreen` 的 parity 测试与图标断言需要保留；分类切换后的滚动恢复可能因详情高度差异越界；把开关做成视觉 switch 容易被误读为可操作。
- Deferred: 真实保存/删除/替换 Key、开关写入、语音试听、配置校验、敏感信息二次确认。
- Non-goals: 不写配置、不请求后端、不展示明文密钥、不新增依赖、不改 Android/后端、不复制示意稿演示保存。
- Skipped non-critical questions and reason: 没有追问分类图标与详情卡间距；不影响验收，由 token 与 CSS 决定。

## Goal

用六类真实夹具交付可切换、可恢复的分类＋详情设置页，所有写入控件明确禁用并解释，敏感字段保持夹具掩码，不伪造保存成功。

## Scope

### In scope
- 新增 `frontend/src/pages/SettingsPage.tsx`（分类列表＋详情）。
- `frontend/src/store/workspaceData.ts` 的设置模型六类提取（由 042 建骨架，本片补齐字段）。
- `frontend/src/pages/WorkspaceRouter.tsx` 接入设置视图。
- `frontend/src/styles/pages.css` 的设置样式（分类行、详情字段、开关徽标、禁用态与 760 断点）。
- `frontend/src/copy/zh-CN.ts` 新增设置文案。
- 新增 `frontend/test/pages/SettingsPage.test.tsx`。

### Out of scope
- 真实配置写入、密钥管理、语音试听。
- 主页/学习库/对话页面（043/044）。
- 旧 `SnapshotScreen` 设置快照删除。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 旧设置入口仍可在旧快照路径查看；新主路径展示同样的六类真实配置文本与状态；夹具正文不改写；敏感值仍为夹具掩码。旧 `SnapshotScreen.test.tsx` 的设置/图标断言不受影响。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): `SettingsPage` 读 `WorkspaceData.settings`，写 `setSettingsCategory/rememberScroll`；`WorkspaceRouter` 增加 settings 分支；`AppShell` 侧栏设置入口已由 042 提供；旧 `SnapshotScreen` 仍消费同一批夹具。
- Native bridge or independent-client compatibility/update requirements: 不适用。
- Behavior-neutral non-applicability reason, if applicable: 夹具、`RuntimePort`、旧快照屏不改。

## Documentation impact record

- Changed runtime facts: Web 设置主路径变为分类＋详情；所有写入控件禁用；密钥保持掩码；设置页只读。
- Authoritative code/config/migration evidence: `frontend/src/platform/fixtures/settings-android.json` 及六个子页夹具；`frontend/src/store/workspaceData.ts`。
- Documentation surfaces checked: 父卡 041、`.codex/rules/frontend.md`、`.codex/rules/regression.md`、`.codex/rules/ux-design.md`、`.codex/rules/contracts.md`。
- Documentation files updated: 本卡与父卡；权威规格修订留给 046。
- Producer/consumer/sibling/compatibility paths checked: 旧设置快照与图标断言保留；新设置页不绕过 `RuntimePort`；不使用 Android 本地存储格式。
- Superseded phrases/examples reverse-searched: 「设置按坐标还原即唯一入口」「保存后 toast 成功」——本片改写主路径并禁止假保存。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 旧设置快照作为 parity 证据保留；只读限制写入父卡与规格修订。

## Acceptance criteria

- [x] 六类分类全部可见（API KEY · DeepSeek、提供商管理、模型设置、搜索服务、语音、文件管理）；切换分类详情随之变化且 `aria-current`/选中态正确。
- [x] 每类详情只显示夹具真实字段：掩码 Key 与可用状态、DeepSeek 与 1 个模型、聊天/快速/翻译模型与思考预算、搜索服务与检索结果数、系统 TTS、已占用 0 B。
- [x] 所有写入控件（保存、删除、替换、开关、试听）disabled，`aria-label`/`title` 含「尚未接通」，页面有只读说明；不存在可提交表单或假 toast。
- [x] 离开设置再返回恢复分类与滚动位置；未知分类回落第一类；缺失某类夹具时显示 `state_fixture_unavailable` 且不影响其他分类。
- [x] 图标按钮复用 `Icon`、有可访问名与 `title`、44×44；分类名保留文字；无 emoji、无新依赖。
- [x] `pnpm --dir frontend test -- test/pages/SettingsPage.test.tsx` 退出码 0（vitest 配置不按文件过滤，实际跑完整套件）；`pnpm --dir frontend lint` 退出码 0；旧 `SnapshotScreen.test.tsx` 仍绿。

## TDD execution record

### RED — failing test first
- Test/scenario: `frontend/test/pages/SettingsPage.test.tsx` 7 例：六类分类与默认 API KEY；提供商真实字段；模型/搜索/语音/文件详情；写入口禁用且开关非控件；分类与滚动恢复、窄窗、未知回落；缺失子夹具；缺失主夹具。
- Command: `pnpm --dir frontend test -- test/pages/SettingsPage.test.tsx`
- Exit code: 1
- Observed failure: `Tests 7 failed | 99 passed (106)`；7 例全部是新增设置页用例（`SettingsPage` 尚未存在、`SettingsCategory.available` 与新文案尚未定义），既有 99 例保持绿。该 vitest 配置不按文件过滤，命令实际跑完整套件。
- Failure reason validated: 失败原因正是本片要交付的行为缺失，而不是测试环境或选择器错误。

### GREEN — minimum implementation
- Implementation: 新增 `frontend/src/pages/SettingsPage.tsx`（六类分类＋只读详情、滚动恢复、缺失回落）；`WorkspaceRouter` 接入 settings 分支；`workspaceData` 增加 `SettingsCategory.available` 并把 api-key 拆成「当前 KEY / 替换 KEY」两组；新增六条设置文案与 `pages.css` 设置样式；保存/删除/试听映射到 `Icon` 字形。
- Command: `pnpm --dir frontend test -- test/pages/SettingsPage.test.tsx`
- Exit code: 0
- Passing evidence: 同一命令同样跑完整套件：`Test Files 16 passed (16)`、`Tests 106 passed (106)`；补一条设置图标断言后全量 `107 passed (107)`。
- Regression evidence: 旧 `SnapshotScreen.test.tsx`、042/043/044 的用例都在同一轮 107 例中保持绿；`pnpm --dir frontend lint` 退出码 0。

### REFACTOR — optional
- Cleanup: ① 用 `available` 区分「子夹具缺失」与「解析为空」，缺失类仍保留分类入口；② api-key 详情拆两组，字段顺序照夹具；③ `SettingsFieldRow` 只在 `disabled && value === undefined` 时渲染禁用输入，避免带 `value` 的模型字段被误渲染成输入框；④ 分类行标题与 chevron 收进 `.settings-category-head`、摘要独立成行，修复 chevron 落到第三行左侧的布局。
- Regression evidence: 全量 16 文件 / 107 例通过；lint 退出码 0（依赖方向 35 文件 0 发现、设备时间 0、文案 0、夹具同步 13/13）。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片按 TDD 执行。
- User approval: 不适用。
- Alternative verification: 不适用。

## Implementation notes

- 设置模型结构：`SettingsCategory { id, title, summary, available, groups: [{ title, fields }], actions }`；`SettingsField { label, value?, control?, state?, disabled? }`；全部字段逐字来自夹具节点，不硬编码配置值。
- 分类 id：`api-key`、`provider`、`model`、`search`、`voice`、`files`；标题与摘要用夹具原文。
- 只读说明放详情顶部一次；禁用控件用 `disabled`＋`aria-label`＋`title`（均含「尚未接通」），视觉上保留但降低对比度；开关渲染为 `role="img"` 状态徽标（`已开启`/`已关闭`/`采集未含开关状态`），不生成 `role="switch"`。
- 滚动位置按 `settings:<category>` 存；离开时在 effect cleanup 写入，切回该分类时恢复，首次为 0。
- 窄窗（≤760）分类列表改为横向滚动条，详情占满宽度；不把分类藏进不可发现的菜单。

## Verification report

- Verdict: PASS
- Evidence:
  - 单测：`pnpm --dir frontend test` → `Test Files 16 passed (16)`、`Tests 107 passed (107)`。
  - 守门：`pnpm --dir frontend lint` 退出码 0；tsc 0 错；依赖方向 35 文件 0 发现；设备时间 0；用户可见文案 0 条绕过 `src/copy`；夹具同步 13/13、bad=0、redacted=0。
  - 浏览器探针 1440×900：`.page` 宽 960、侧栏 240、页面无横向溢出；六分类可切换，`aria-current="page"` 与 `[data-settings-detail]` 同步；只读说明存在；全部 `[data-settings-action]` 为 disabled 且 `aria-label`/`title` 含「尚未接通」；`form`=0、`role="switch"`=0；开关徽标为 `role="img"`（provider=on、search=off、model=unknown）。
  - 短窗 1440×520：搜索详情 `scrollHeight 816 > clientHeight 444`，滚到 140；home→settings 返回后仍为 140，分类仍为 search。
  - 窄窗 390×844：分类条 `categoriesOverflow=true`（可横向滚动），页面 `overflowX=false`；短窗滚到 140 后切窄窗被浏览器夹到 105（885−780 的可滚上限），属于视口变化后的正常夹取，分类与详情未重置。
  - 截图（scratch，不入库）：`artifacts/tmp/probe-045/01`–`09`；正式截图归档在 046。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除断言；「检索结果数」是分组标题而非字段，断言改为 `[data-settings-group="检索结果数"]` 的正文包含「每次检索 10 条」，行为覆盖不变。
- 046 正式证据补记（2026-10-21）：`artifacts/evidence/web-workspace-implementation-2026-10-21/` 的读数确认搜索服务详情 816→444 可滚、滚到 140 后去主页再回仍为 140 且分类仍为 `search`；390×844 下侧栏收起、分类条可横向滚动、页面无横向溢出；截图 `desktop-settings.png`。
- Gaps: 真实保存/删除/替换 Key、开关写入、语音试听未接通；键盘、IME、跨视图焦点与返回恢复的正式取证在 046。
- Risks: 旧设置快照入口与新页并存需 046 文档说明；只读控件可能仍被误读为可操作，已用禁用态、`aria-label`/`title` 与顶部只读说明缓解。

## Handoff notes

- Completed: 设置分类＋详情页、`available` 模型、六类真实字段、禁用写入语义、分类/滚动恢复、7 例单测、图标断言、浏览器探针与卡片收尾。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 046 正式浏览器取证（键盘、中文 IME、焦点、返回恢复、桌面与窄窗）、规格修订、全量守门与 metrics、截图归档。
- Watch-outs: ① 不写配置；② 不展示明文 Key；③ 不用假 toast；④ 旧快照屏设置断言继续绿；⑤ 短窗→窄窗滚动值会被浏览器夹到可滚上限，记录时按实际值写。
