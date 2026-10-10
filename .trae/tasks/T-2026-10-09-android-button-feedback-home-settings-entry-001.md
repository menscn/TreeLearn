# Treelearn Harness Task: Android 按钮反馈与首页设置入口调整

id: T-2026-10-09-001
title: Android 按钮反馈与首页设置入口调整
template_version: 2
task_type: implementation
status: verifying
parent_requirement: T-2026-10-09-android-button-feedback-settings-requirement-002.md
requirements_confirmed: true
generated_by: leader
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 180
shared_code_touched: false
depends_on: []
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 用户反馈应用内按钮点击时背景/动效不一致，并要求整体优化；设置页的数据边界信息无需展示，底部设置入口移到首页右上角。
- Users/scenarios: 用户在首页、设置页、聊天、弹层等页面点击明确按钮、图标按钮或导航控件；从首页和聊天上下文打开设置。
- Expected outcome: 明确按钮拥有一致、轻量的原生按压波纹，保留各按钮正常、禁用和危险操作的底色语义；设置页不显示“数据边界”标题和说明；底部只保留首页与学习库，首页右上角提供齿轮设置入口。
- Core flow and reversibility: 首页点右上角设置进入设置根页，返回回首页；聊天页原设置入口继续工作，返回回原聊天；缺少 Key 时的设置引导与回退行为不变。
- Inputs/outputs: 点击操作控件触发对应动作和可见按压反馈；首页设置按钮有可访问性描述；设置入口位置变化不改设置数据。
- State and exception handling: 保留按钮 enabled/disabled 语义、危险动作颜色和既有业务事件；设置导航按调用来源返回；移除展示文案不改存储、备份配置或 Provider 请求内容。
- Constraints and trade-offs: Java-only、原生 Android/XML、不引入依赖、不使用模拟器；复用现有颜色 token；内容卡片业务点击保持原样；只统一明确按钮、图标按钮及导航控件反馈。
- AI recommendation and rationale: 采用原生 ripple 叠加原背景，而非缩放/位移动画，避免布局抖动并保留按钮正常/禁用/危险语义；复用项目现有齿轮矢量图标。
- User choice and custom additions: 2026-10-09 用户确认方案 A，并要求修改后编译 APK 安装；目标真机为 ALP-AN00，用户选择纯文本沟通。
- AI-autonomous decisions: 保留聊天内设置按钮；底部保留首页/学习库两个标签；设置页返回按原调用上下文；不改内容卡片交互；不替用户执行完整功能走查。
- User-confirmation decisions: 全应用明确操作按钮使用一致原生 ripple；将底部设置迁到首页右上角齿轮；移除设置页数据边界展示；构建 debug APK 并安装到 ALP-AN00，不用模拟器。
- Acceptance evidence: 定向测试覆盖首页设置入口及返回；Android lint、相关 JVM/instrumented 测试、依赖方向、Java-only dex guard；APK 精确产物与版本；指定设备安装和 Activity 启动就绪证据。
- Facts: 手机/平板 screen_tab_bar.xml 原各有三项；MainActivity 持有 tab 导航及设置调用；screen_home_body.xml 首页栏原只有左侧抽屉入口；screen_settings_body.xml 原渲染数据边界区块；部分自定义按钮背景 selector 按下时切换整块颜色。
- Assumptions: 现有主题颜色 token 可提供 ripple 色，不新增设计 token；首页设置返回回首页；聊天来源继续由现有 MainActivity 路由负责。
- Unknowns: MagicOS 11 的实机按压观感由用户安装后确认；未执行完整人工页面走查。
- Risks: 移除说明降低设置页对本地保存/Provider 请求的显式提示，但这是用户明确要求；实际数据处理保持不变。
- Deferred: 用户对 ALP-AN00 的完整视觉/功能走查；其他机型和横竖屏人工回归。
- Non-goals: 不修改服务端、数据库、Provider 数据处理、聊天内容卡片点击、分支返回逻辑；不改 API Key 安全策略；不使用模拟器。
- Skipped non-critical questions and reason: 用户已确认方案、按钮范围、聊天设置入口、目标设备与安装要求，无实质未决项。

## Goal

统一 Android 明确按钮的按压反馈，并将设置入口从底部标签迁到首页右上角，同时保持调用上下文返回行为。

## Scope

### In scope
- 为明确按钮、图标按钮及导航控件提供一致原生 ripple；保留默认、disabled、danger 与主操作底色语义。
- 从手机与平板底部导航移除设置项，首页右上角添加有描述的齿轮按钮。
- 保持聊天内设置入口及缺少 Key 引导路径，并验证设置返回来源。
- 移除设置根页数据边界展示，更新 Android 当前态说明与 UX 设计记录。
- 编译 APK，执行 Java-only guard，定向安装并启动 ALP-AN00 上的应用。

### Out of scope
- 内容卡片点击反馈与业务行为；聊天页设置入口移除；本地/远程数据处理行为调整；模拟器验收；用户完整手工走查。

### Behavior impact — applicable surfaces only

Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 设置入口从底部第三个 tab 移到首页右上角；首页与设置返回、聊天设置旁路、缺 Key 引导、按钮动作及 disabled/danger 语义保持正确。
- Producer/consumer/sibling entrypoints and platform branches: HomeScreen 首页控件 → MainActivity.showSettings；ChatScreen/ChatPresenter 设置入口 → MainActivity.showSettings；MainActivity.onBackRequested 与 swap；手机/平板 screen_tab_bar.xml；共享 screen_home_body.xml；按钮 drawable selectors。
- Native bridge or independent-client compatibility/update requirements: N/A；Android 单机 UI，无跨端契约。
- Behavior-neutral non-applicability reason, if applicable: N/A；包含用户可见导航与反馈变化，需行为证据。

## Documentation impact record

Follow [code-document alignment](../rules/documentation.md).
- Changed runtime facts: 设置不再是底部一级 tab；首页右上角进入；设置根页不再展示数据边界说明；按钮状态反馈统一。
- Authoritative code/config/migration evidence: MainActivity.java、HomeScreen.java、screen_home_body.xml、手机/平板 screen_tab_bar.xml、screen_settings_body.xml 与按钮 drawable selectors。
- Documentation surfaces checked: android/README.md 设置/标签栏描述；Docs/04-ux-design/知芽-TreeLearn-设置页重设计方案-V1.0.md；对应历史交互稿。
- Documentation files updated: android/README.md；Docs/04-ux-design/知芽-TreeLearn-设置页重设计方案-V1.0.md（增加历史快照说明）；Docs/04-ux-design/知芽-TreeLearn-设置入口与按钮反馈更新-2026-10-09.md；Docs/04-ux-design/design-system/README.md。
- Producer/consumer/sibling/compatibility paths checked: MainActivity/HomeScreen 首页入口；ChatPresenter/ChatScreen 设置入口；onBackRequested/swap；手机/平板 tab 布局；应用启动与目标包身份；本地存储/备份/Provider 代码未改。
- Superseded phrases/examples reverse-searched: Android 当前代码与 README 已无 tab_settings、ic_tab_settings、settings_boundary 引用；旧设置稿与 2026-09-27 原型中三个 tab 的字样明确保留为历史快照并从当前文档链接说明。数据处理依据生产代码未变。
- documentation impact: none reason and evidence, if applicable: N/A；当前态行为文档需同步。
- Historical/PRD handling: 2026-09-28 交互稿保留为历史快照并显式标注；更新当前态 Android README 与设置设计记录，不改 PRD。

## Acceptance criteria

- [ ] 首页齿轮按钮在手机/平板布局可见、触控区域达项目标准、无障碍描述明确；点击打开设置，返回回首页。
- [ ] 底部仅显示首页和学习库；聊天内设置与缺 Key 引导仍能打开设置并按来源返回。
- [x] 设置页不显示“数据边界”标题或说明；数据存储、备份及发送行为不变。布局、strings 与生产存储/请求路径已核对；真实页面仍待用户触摸走查。
- [ ] 明确按钮、图标按钮、导航按钮有一致原生 ripple；正常、禁用、危险按钮的基础背景语义保留；内容卡片点击逻辑不变。静态资源核对通过；完整设备交互未执行，待用户走查。
- [ ] 定向 SettingsBackRouteTest 2 tests / 0 failures；Android lint、依赖方向、instrumentation 测试 APK 编译和 Java-only APK dex guard 通过。全量 JVM 831 tests 有 1 项聊天生成阶段断言失败，详见 Gaps。
- [x] :app:assembleDebug 成功；只向在线真机 ALP-AN00 安装；MainActivity 启动返回 Status=ok 且进程存在；不使用模拟器。
- [x] 文档影响记录完整；git diff --check、UTF-8/替换字符及被替换文案反查通过；见 doc-task-integrity 与 task-validator-final 证据。

## TDD execution record

### RED — failing test first
- Test/scenario: SettingsBackRouteTest 的首页/聊天返回目标断言；完整 RED 输出见 artifacts/evidence/T-2026-10-09-001-settings-back-route-red.txt。
- Command: .\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.SettingsBackRouteTest --rerun-tasks --no-daemon --console=plain
- Exit code: 1
- Observed failure: javac 无法解析尚未实现的 SettingsBackRoute。
- Failure reason validated: 本次新增的设置返回目标策略尚不存在，定向测试无法编译；RED 失败点与被测功能一致。

### GREEN — minimum implementation
- Implementation: 新增设置返回目标判定；将设置从一级 tab 迁至首页齿轮；移除设置页数据边界展示；主/危险/图标/芯片等操作背景改用保留底色的 ripple；更新手机/平板导航测试和当前态文档。
- Command: .\gradlew.bat :app:testDebugUnitTest --tests com.treelearn.app.SettingsBackRouteTest --rerun-tasks --no-daemon --console=plain
- Exit code: 0
- Passing evidence: 2 定向测试通过，最终证据 artifacts/evidence/T-2026-10-09-001-settings-back-route-final.txt。
- Regression evidence: 最终综合命令及定向测试、lint、instrumentation 源码编译、依赖方向、debug APK 构建均成功，见 artifacts/evidence/T-2026-10-09-001-final-build-checks.txt；最终 APK 完整 Java-only dex guard 见 T-2026-10-09-001-java-only-guard.txt。Instrumentation 源码已编译但未在设备上执行。全量 JVM 套件与独立复跑结果见 artifacts/evidence/T-2026-10-09-001-prebuild-checks.txt 与 T-2026-10-09-001-chat-stage-isolation.txt。

### REFACTOR — optional
- Cleanup: N/A
- Regression evidence: N/A

### TDD or sizing exception — only with prior user approval
- Exception type: None
- Reason: N/A
- User approval: N/A
- Alternative verification: N/A

## Implementation notes

- 任务日期 2026-10-09；目标真机当前 adb serial 为 AWSS013807000356，product/model 为 ALP-AN00。
- Debug 包供用户自行功能测试；自动验证只执行定向回归与启动检查，不替代用户手工走查。
- 遥测决定：不新增本地或远程行为事件；只改变呈现与导航入口，不引入用户行为采集。

## Verification report

- Verdict: PARTIAL — 独立 verifier 确认；设备按钮和导航的实际触摸反馈未走查，完整 JVM 套件存在一项非本次范围失败。
- Evidence: RED/GREEN 分别见 artifacts/evidence/T-2026-10-09-001-settings-back-route-red.txt 与 T-2026-10-09-001-settings-back-route-final.txt；最终 lint/依赖方向/instrumentation 编译/debug 构建见 T-2026-10-09-001-final-build-checks.txt；APK 版本见 T-2026-10-09-001-apk-build.txt；最终 Java-only guard 见 T-2026-10-09-001-java-only-guard.txt；最终 ALP-AN00 安装/启动及 APK SHA-256 见 T-2026-10-09-001-device-install-startup.txt。文档/任务/编码校验见 T-2026-10-09-001-doc-task-integrity.txt。设备 Android 13/API 33，未使用模拟器。
- Changed/deleted assertion rationale and equivalent behavior coverage: 原 BottomTabShellInstrumentedTest 三 tab 覆盖更新为两项底栏、首页齿轮打开设置、设置返回首页；HomeMotionInstrumentedTest 更新为齿轮入口。旧断言已被新需求取代；设备用例本轮仅编译，未在真机执行。
- Gaps: 全量 JVM 套件 831 项中 ChatGenerationStageTest.anAcceptedSendShowsTheSendingStageUntilTheFirstRowArrives 稳定失败，预期 SENDING、实际 IDLE，定向复跑证据 T-2026-10-09-001-chat-stage-isolation.txt；该用例涉及已有 ChatPresenter/聊天改动，与此任务 UI 导航改动无调用关系，未扩大范围修复。Instrumentation 用例未运行；ALP-AN00 仅验证安装及 Activity 启动，未触摸走查 ripple、首页设置入口、聊天设置及缺 Key 返回。独立 verifier 已只读确认 PARTIAL。
- Risks: 用户明确要求设置根页隐藏数据边界说明；应用本地保护/备份配置/外发行为未改变。MagicOS 11 特定 ripple 视觉效果待用户查看。

## Handoff notes

- Completed: 需求/方案已确认；改动、目标设备 APK 构建与安装启动、Java-only guard、lint 和定向测试已完成。
- In progress: 等待用户在 ALP-AN00 完成按钮与设置路径触摸走查。
- Blocker: None.
- Unblock condition: N/A.
- Next steps: 用户在 ALP-AN00 走查首页齿轮/返回、聊天内设置/返回、各按钮按压反馈及数据边界隐藏；如发现问题再按实际现象处理。
- Watch-outs: 工作区含大量用户未提交改动；T115 仍 in_progress；只记录本任务自身变更，不回滚或覆盖其它任务代码.
