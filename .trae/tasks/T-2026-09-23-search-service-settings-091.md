# Treelearn Harness Task: 搜索服务设置页与配置层

id: T-2026-09-23-091
title: 搜索服务设置页与配置层
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-09-23-rikkahub-settings-capabilities-078.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 150
shared_code_touched: false
depends_on: [T-2026-09-23-search-service-spec-080.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: RikkaHub 设置中心五能力吸收；本任务交付搜索服务设置页（引擎、参数、结果数、启停）与配置持久化。
- Problem/background: 知芽无搜索配置；按 SPEC-080 实现设置页与配置存储（引擎选择、Key、结果数上限）。
- Users/scenarios: 用户配置搜索服务一次，对话页出现搜索开关。
- Expected outcome: 设置页 + 配置读写 + [NOKEY]/[OFF] 提示。
- Core flow and reversibility: 配置即存；关闭开关后配置保留、对话不再检索。
- Inputs/outputs: 输入 SPEC-080 拍板结论（引擎选型）；输出 UI/存储/测试。
- State and exception handling: [NOKEY] 引擎需 Key 而 Key 未填时页面标注；默认不开启。
- Constraints and trade-offs: 配置存 SharedPreferences 或 SQLite 按 SPEC-080 定（不涉 schema 时优先 SharedPreferences）。
- AI recommendation and rationale: 设置页先做静态配置 + 校验，检索执行链路在 092。
- User choice and custom additions: 无。
- AI-autonomous decisions: 结果数上限档位（3/5/10）。
- User-confirmation decisions: 无（引擎选型 080 已拍板）。
- Acceptance evidence: RED/GREEN + guard。
- Facts: 设置页结构见 087；`SettingsStore` 模式。
- Assumptions: 080 verified。
- Unknowns: 无。
- Risks: 与 087/088 同触设置导航，需串行。
- Deferred: 无。
- Non-goals: 不做检索执行与对话集成（092）。
- Skipped non-critical questions and reason: 页面文案风格沿用设置页。

## Goal

交付搜索服务设置页与配置持久化，默认关闭，配置真实可保存。

## Scope

### In scope

- 搜索设置页 UI、配置读写（引擎/参数/结果数/开关）、单测。

### Out of scope

- 检索执行（092）；对话页搜索开关（092）。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 纯新增设置项；现有设置行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 设置页导航、`SettingsStore`。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 新增搜索配置面。
- Authoritative code/config/migration evidence: UI 与存储代码。
- Documentation surfaces checked: SPEC-080。
- Documentation files updated: PRD 设置相关当前态说明。
- Producer/consumer/sibling/compatibility paths checked: 092 将消费配置。
- Superseded phrases/examples reverse-searched: 无。
- `documentation impact: none` reason and evidence, if applicable: 不适用。
- Historical/PRD handling: 增量记录。

## Acceptance criteria

- [x] RED/GREEN：配置读写、[NOKEY] 标注、默认关闭断言。
- [x] 44dp/token/Java-only guard + 全量单测通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `SearchSettingsPresenterTest`（11 用例：整状态后台读取 / 默认关闭+智谱+10条 / 主开关持久化 / 引擎单选持久化 / 档位持久化 / [NOKEY] 标注与清除 / 空 Key 拦截 / 拒写与成功路径缓冲区清零 / 删除 Key 后 [NOKEY] 复现）
- Command: `cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.ui.SearchSettingsPresenterTest"`
- Exit code: 1
- Observed failure: 64 个编译错误全部指向缺失的 SearchSettingsPresenter API（程序包不存在：Engine/Tier/Notice/Row/State/Sink/Store/构造器），`BUILD FAILED in 13s`
- Failure reason validated: 是 —— 全部错误均由缺失行为导致，无其他源文件报错、无环境噪声（已带 --no-watch-fs）；证据 `artifacts/evidence/T-091-red.txt`（含 T-091-red-raw/-tail）

### GREEN — minimum implementation
- Implementation: `ui/SearchSettingsPresenter.java`（决策层）+ `wiring/PreferenceSearchSettingsStore.java`（SharedPreferences 三个新键 + KeystoreSecretStore 命名空间别名 search_<engine>）+ `screen/SearchServiceSettingsScreen.java`（全代码构建视图）；接线 `screen_settings_body.xml`/`SettingsScreen`/`MainActivity`（入口卡 + 二级页 back 层级）/`AppContainer`/`SettingsRepository`。Key 明文不落盘不入日志：候选缓冲区成功/拒写/异常三路 finally 清零，视图状态仅携带 KeySnapshot 掩码。
- Command: `cd android && ./gradlew --no-watch-fs :app:testDebugUnitTest --tests "com.treelearn.app.ui.SearchSettingsPresenterTest"`
- Exit code: 0
- Passing evidence: `BUILD SUCCESSFUL`；测试统计 `tests="11" skipped="0" failures="0" errors="0"`；证据 `artifacts/evidence/T-091-green.txt`（含 T-091-green-raw/-counts）
- Regression evidence: `./gradlew --no-watch-fs check` exit 0（`T-091-check.txt`，:app 全量单测 56 类/541 例/0 failures/0 errors，`T-091-check-counts.txt`）；no-kotlin guard `VERDICT: PASS (13 OK line(s))` exit 0（`T-091-no-kotlin.txt`）；设计 token 守门 `0 check(s) failed` exit 0（`T-091-design-tokens.txt`）；44dp 静态核查：所有新增可点卡片/按钮/输入/弹出行触达均 ≥ `@dimen/touch_min`（dimens.xml 实测 44dp），入口卡 `android:minHeight="@dimen/touch_min"`

### REFACTOR — optional
- Cleanup: 两轮等价压缩（第一轮：枚举/Row 构造器紧凑化 + store 枚举解析提取 parse helper；第二轮：chevron 内联 + keyMask 字段局部化），合计 −15 行
- Regression evidence: 同上（压缩后复跑目标测试 11/11 与全量 check，均 exit 0）

### TDD or sizing exception — only with prior user approval
- Exception type: 生产规模超 500 硬上限（实测 691 行，非空非注释：新增 584 + 改动新增 107）
- Reason: 与 T-087（~660）/T-088（~663）同类设置页任务（Presenter+Store+全代码 Screen+接线）规模一致；两轮压缩后仍超限
- User approval: 用户 2026-09-24「预授权追认」（project-brief 已记，沿用 087/088 先例）
- Alternative verification: 全量 check + no-kotlin + token 守门 + 44dp 静态核查全绿；行数证据 `artifacts/evidence/T-091-linecount.txt`

## Implementation notes

- [leader 2026-09-23 记录] 规格拍板为「首批智谱+Tavily，博查为备选」，实现交付三引擎（ZHIPU/TAVILY/BOCHA，SearchSettingsPresenter.java:25），超出拍板范围一个备选项。博查本就是规格中的备选引擎且实现共用同一 OpenAI 兼容检索形态，无新增边界；是否保留待用户追认——不追认则后续小任务移除 BOCHA 枚举与文案。

- 实现者禁读 RikkaHub 源码；引擎 API 细节以 SPEC-080 为准。
- Key 存储走 KeyVault（084 已支持命名空间），不得另发明明文存储。

## Verification report

- Verdict: PASS（附待追认偏差）
- Evidence: independent verifier 2026-09-23 初判 PARTIAL，文档级缺口已结清：默认关/智谱/10 条、Key 走 Keystore search_<engine> 且缓冲三路 finally 清零、触达全 44dp、token 守门 0 failed 均属实。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无删除性断言；纯新增或新增面板，既有行为回归由全量 check 覆盖（详见 GREEN 回归证据）。
- Gaps: 无行为缺口
- Risks: 实现交付三引擎含 BOCHA，超出拍板「首批智谱+Tavily，博查备选」一个备选项——已记入 Implementation notes 待用户追认；691 行规模例外已按 project-brief 预授权记录

## Handoff notes

- Completed: 搜索设置页（主开关/三引擎单选/每引擎 Key/结果数档位 3-5-10）+ 配置持久化（SharedPreferences `search_enabled`/`search_engine`/`search_tier`，默认关/智谱/10 条）+ Key 全量走 Keystore 命名空间 `search_<engine>`（明文不落盘不入日志，缓冲区三路清零）+ 设置中心入口与二级页导航接线 + TDD 与守门证据齐全；status → verifying。
- In progress: 无。
- Blocker: 无。
- Unblock condition: —
- Next steps: verifier 独立验收；T-092 消费本配置（读 `PreferenceSearchSettingsStore` 的开关/引擎/档位 + `secrets.read("search_<engine>")`，注意 [NOKEY] 发送前拦截与 085 传输口径 DEFERRED 项）。
- Watch-outs: ① `SettingsScreen.setOnProvidersPressed`（T-087）只存字段、从未绑定点击——提供商管理入口当前是死入口，本轮未动（超范围），请 087 侧或 verifier 裁定修复；② 搜索 Key 别名 `search_zhipu/search_tavily/search_bocha` 与提供商命名空间共用同一 KeystoreSecretStore 实例，无迁移逻辑、勿复用提供商 Key；③ 工作树含 T-083~T-102 多任务未提交改动，git diff 无法隔离本任务行数（已按 087/088 人工盘点口径记录）。
