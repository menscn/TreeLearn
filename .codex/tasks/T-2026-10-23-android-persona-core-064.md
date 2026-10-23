# Filename: T-2026-10-23-android-persona-core-064.md
# Codex Harness Task: Android 人设核心：枚举、提示词附加段、V5→V6 迁移与生成身份

id: T-2026-10-23-064
title: Android 人设核心与持久化
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-21-ai-persona-and-forest-requirement-038.md
requirements_confirmed: true
generated_by: agent
owner: android-dev
owner_lane: android
stack: android
estimated_production_lines: 460
shared_code_touched: true
depends_on: [T-2026-10-21-persona-spec-decisions-039.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary：父卡 038 已登记用户 2026-10-23「按 harness 开发，web 和 android」的实现授权；人设规格 `Docs/spec/SPEC-2026-09-21-ai-persona-preference.md` 已把未知值回落、窄写路径、七档 wireId 与行为矩阵写成已拍板。
- Problem/background: 人设目前只存在于规格。要让「每条成功生成的回答记录带当时的人设身份、旧回答可复现」成立，人设必须进入命令、请求、操作行、稳定回答行与主题行五处，并在读取边界对未知值回落默认。
- Users/scenarios: 学习者在主题内切换回答风格；下一次发送、重试或重置按当下主题的档位生成；旧回答永远保留生成时的档位身份。
- Expected outcome: 七档人设可解析；附加段只追加在 `LEARNING_ANSWER`；V5→V6 在全新安装与升级安装得到同一列定义；切换人设只写 `persona` 一列；未知 wire 值回落默认且不拖垮读取。
- Core flow and reversibility: 枚举与附加段 → 命令/请求身份 → 操作行与稳定行 → 主题列与窄写。每个提交点都可单独回退；回退迁移需保留已写入的列，不能降级删列。
- Inputs/outputs: 输入是主题行的 `persona`、生成命令与请求；输出是带人设身份的 `generation_operations`/`answer_revisions` 行与聊天页可读的主题档位。
- State and exception handling: 未知值只在读取边界回落并要求 UI 提示一次；写入侧只接受枚举；派生用途索要人设段按既有拒绝抛出；版本与人设不一致时协调器拒绝提交。
- Constraints and trade-offs: Java-only；`:core` 不碰 Android；不新增依赖；不改写 `PromptBodiesV1.LEARNING_ANSWER_TUTOR`；提示词正文不进数据库/日志；`ALTER TABLE ADD COLUMN` 带 NOT NULL DEFAULT；列定义只允许一份文本。
- AI recommendation and rationale: 人设与思考模式同构——叠加在基础正文上的第二维度，只追加、不改变基础版本字面量 `tl-learning-answer/1.1.0`。发送身份以主题行为权威，聊天页 chip 只显示与窄写，避免第二个事实源。
- User choice and custom additions: 未知档位回落默认并提示；切换人设不刷新活动时间；回答上不标注人设；七档固定。
- AI-autonomous decisions: ① 旧 `create/restore/assemble/systemContent` 重载保留并委托默认档，避免几十处既有测试为一个新维度改签名；生产链路只走带人设的重载。② `AnswerVersions` 在重试/重置的事务内读主题档位，不把 chip 状态传下来。③ 人设回落信号放在 `TopicDao.personaOf` 的小结果里，不污染 `Topic`。
- User-confirmation decisions: 2026-10-21 两项拍板与 2026-10-23 实现授权；本片不新增产品决策。
- Acceptance evidence: `PersonaTest`、`PersonaPromptsTest`、`PersonaIdentityTest`、`TopicPersonaTest` 的 RED/GREEN；`./gradlew :core:test :ai:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest`；`./gradlew :app:assembleDebug`。
- Facts: `DATABASE_VERSION = 5`；topics 4 列；answer_revisions 9 列；generation_operations 14 列；`PromptBodiesV1.LEARNING_ANSWER_TUTOR` 是主回答基线；`GenerationCommand` 只有一个私有构造器和一个工厂；`TopicDao.update()` 整行写回且刷新 `updated_at`。
- Assumptions: 现有测试的双重载保持源码兼容；设备已连接但本片不跑全量仪器测试（会卸载应用、清数据）。
- Unknowns: V6 迁移的仪器测试是否在真机执行；本片只保证编译与 JVM 测试，迁移的设备证据留给片 071。
- Risks: 迁移列定义写两份会在新装/升级之间漂移；把回落放进 `Topic` 构造器会拖垮整屏；复用 `TopicDao.update()` 会把主题挪到首页顶部。
- Deferred: 聊天页 picker 与窄写（片 066）、介绍页与设置入口（片 067）；Web 人设与森林（片 069/070）。
- Non-goals: 用户自建人设；提示词正文编辑；改写基线正文；新增供应商；遥测记录正文。
- Skipped non-critical questions and reason: 没有追问附加段的具体措辞是否逐字入评测；它属于片 071 的证据资产，不改变本片边界。

## Goal

让人设成为 Android 生成链路的一等身份：七档枚举稳定可解析、附加段穷尽且只用于主回答、V5→V6 三列迁移在新装与升级同构、主题行可窄写且未知值回落不拖垮读取，并让每条成功回答记录生成时的人设。

## Scope

### In scope
- `:core` 新增 `Persona`（7 档、稳定 wireId、未知回落、显示名资源标识语义）。
- `:ai` 新增 `PersonaPrompts` 穷尽附加段；`DeepSeekPromptRegistry` 增加带人设的 `systemContent` 重载；派生用途带非默认人设时拒绝。
- `GenerationCommand`、`DeepSeekRequest`、`DeepSeekRequestAssembler` 携带人设；`GenerationCoordinator` 的版本校验升级为「版本 ∧ 人设」。
- `GenerationOperation`、`GenerationOperationRow`、`GenerationOperationDao`、`AnswerRevision`、`AnswerRevisionDao`、`GenerationStabilizer` 贯穿人设。
- `TreeLearnSchema` V5→V6：`topics.persona`、`generation_operations.persona`、`answer_revisions.persona`；列定义共用一份常量；同步 androidTest 列期望。
- `Topic` 增加人设；`TopicDao.read/values/insert` 映射；新增 `updatePersona` 窄写与 `personaOf` 带回落的读取。
- `ChatStore` 增加主题人设读取；`Conversation` 携带人设与回落标记；`SqliteChatStore` 实现；`AnswerVersions` 重试/重置读当下主题档位；`ChatWiring.pin` 读主题档位。
- JVM 测试覆盖上述核心判定。

### Out of scope
- 聊天页 picker、窄写与 toast（片 066）；介绍页与设置入口（片 067）。
- Web 人设（片 069）。
- 行为评测运行与提示词效果调优。
- 全量仪器测试执行（设备证据在片 071 用安装截图与定向验证补）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 新增人设身份；默认档与未知值的行为必须与「没有人设」完全一致；旧回答不被改写；system 仍恰好一条且在第一位；派生用途不带人设段；主题排序仍只看 `updated_at`。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `ChatWiring.pin`/`replace` → `DeepSeekRequestAssembler` → `GenerationCoordinator.admit` → `GenerationStabilizer.freeze`；消费者 `AnswerRevisionDao.find*`、`SqliteChatStore.read`、聊天页。迁移路径 `TreeLearnSchema.applyForwardMigrations`，新装路径 `CREATE_TABLE_STATEMENTS`。
- Native bridge or independent-client compatibility/update requirements: 不适用——不穿越原生桥；Web 只读 fixture，不消费该列。
- Behavior-neutral non-applicability reason, if applicable: 不适用。

## Documentation impact record

- Changed runtime facts: `DATABASE_VERSION` 5→6；三张表各多一列 `persona`；主题切换不再只有整行写回一条写路径。
- Authoritative code/config/migration evidence: `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`、`android/core/src/main/java/com/treelearn/core/domain/Persona.java`、`android/ai/src/main/java/com/treelearn/ai/prompt/PersonaPrompts.java`。
- Documentation surfaces checked: 人设规格、`android/README.md`、PRD V3.1、`.codex/rules/sql.md`、`.codex/rules/android.md`、`.codex/rules/regression.md`。
- Documentation files updated: 本卡；`android/README.md` 与规格现状行号在片 071 回写。
- Producer/consumer/sibling/compatibility paths checked: 命令、请求、操作行、稳定行、主题行、重试/重置、聊天读取。
- Superseded phrases/examples reverse-searched: 「人设层在生产代码里还不存在」「未知档位待拍板」「复用整行写回」。
- `documentation impact: none` reason and evidence, 若适用: 不适用。
- Historical/PRD handling: 规格的历史拍板保留；实现口径写进代码注释与片 070 的规格回写。

## Acceptance criteria

- [ ] 七档 wireId 可往返解析；未知 wireId 回落 `default` 且 `isKnown` 为 false。
- [ ] 附加段对 7 档穷尽；默认档不追加任何内容；派生用途带非默认人设被拒绝；主回答版本字面量不变。
- [ ] `GenerationCommand`/`DeepSeekRequest`/`GenerationOperation`/`AnswerRevision` 均携带同一人设；协调器在版本与人设任一不符时拒绝。
- [ ] V5→V6 在 `CREATE_TABLE_STATEMENTS` 与迁移语句中使用同一份列声明；`DATABASE_VERSION = 6`；新装表列与升级后列一致。
- [ ] `TopicDao.updatePersona` 只写 `persona`，不写 `updated_at`；影响行数不是 1 时拒绝。
- [ ] 未知主题人设读取返回默认档 + 回落标记，不抛、不影响其他行。
- [ ] 既有 JVM 单元测试全部通过；`assembleDebug` 通过。

## TDD execution record

### RED — failing test first
- Test/scenario: `PersonaTest`（枚举往返与未知回落）、`PersonaIdentityTest`（命令/操作/回答身份一致）、`TopicPersonaTest`（主题承载与窄写）、`PersonaPromptsTest`（七档穷尽与派生拒绝）、`TreeLearnSchemaPersonaTest`（V5→V6 三列与声明复用）
- Command: `./gradlew :core:test :ai:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest`
- Exit code: 1
- Observed failure: `:core:compileTestJava` 失败，28 处「找不到符号」，例如
  `PersonaIdentityTest.java:26` 的 `GenerationCommand.create` 只接受
  `String,String,PromptPurpose,String,GenerationModel,boolean,long`，测试传入的 8 参（含
  `Persona`）无对应重载；`PersonaIdentityTest:46/64/73` 找不到 `command.persona` /
  `operation.persona` / `revision.persona`；`TopicPersonaTest:22/34` 找不到
  `Topic.persona` 与 `Topic.withPersona(Persona)`。编译在 `:core` 先失败，`:ai/:data/:app`
  的 `PersonaPrompts`、V6 schema 断言尚未轮到执行。
- Failure reason validated: 失败原因就是被测能力缺失（人设身份链与 V6 列尚不存在），不是测试写错或环境问题；新增测试只依赖规格已定的语义。

### GREEN — minimum implementation
- Implementation: 新增 `Persona`、`PersonaPrompts`，扩展现有身份对象与 DAO/schema；保留旧重载委托默认档。同步 `SqliteSchemaInstrumentedTest` 的三表列期望与 V1 fixture 的 V6 列剥离。
- Command: `./gradlew :core:test :ai:testDebugUnitTest :data:testDebugUnitTest :app:testDebugUnitTest`
- Exit code: 0
- Passing evidence: 四个模块共 1315 例，0 失败、0 错误、0 跳过（core 240 / ai 100 / data 73 / app 902），2026-10-23 实测 BUILD SUCCESSFUL in 6s。人设相关新套件：`PersonaTest` 4、`PersonaIdentityTest` 7、`TopicPersonaTest` 6、`PersonaPromptsTest` 9、`TreeLearnSchemaPersonaTest` 4、`TopicPersonaDaoStatementTest` 4、`GenerationCoordinatorIdentityTest` 4。
- Regression evidence: 既有 `GenerationCommandTest`、`TopicTest`、`DeepSeekPromptRegistryTest`、`DeepSeekRequestAssemblerTest` 全部随同一条命令通过；`./gradlew :app:assembleDebug :app:compileDebugAndroidTestJavaWithJavac` 退出 0，产物 `app/build/outputs/apk/debug/app-debug.apk`（22,182,465 字节）。

### REFACTOR — optional
- Cleanup: 人设附加段独立为 `PersonaPrompts`，不塞进 `PromptBodiesV1`，避免基线正文被误改。
- Regression evidence: 同一条命令。

### TDD or sizing exception — only with prior user approval
- Exception type: not-applicable
- Reason: 本片是标准 TDD 实现片；460 行是迁移、身份链与测试的总估计，超过 200 行软阈值但低于 500 行失败阈值，按一个身份不可分开落地评估。
- User approval: 用户 2026-10-23 授权按 harness 开发；无需额外的超限批准。
- Alternative verification: 不适用。

## Implementation notes

- 人设与思考模式同构：附加段只追加，版本字面量不变。
- `Persona` 只放稳定 wireId 与显示名 key 语义，不放 Android 资源 id，保持 `:core` 纯 JVM。
- 旧重载是兼容层而不是第二套规则：它们全部委托默认档，生产链路不调用。
- 发送/重试/重置的身份由主题行决定；聊天页 chip 状态不进入请求。
- 迁移列声明必须复用同一常量；`ALTER TABLE ADD COLUMN` 不能带 CHECK，合法性由枚举映射负责。

## Verification report

- Verdict: PASS（JVM 与编译层；设备上的 V5→V6 执行留给片 070）
- Evidence: GREEN 命令退出 0，1315 例全绿；`assembleDebug` 与 androidTest 编译退出 0。验收条目逐条落点：七档往返与未知回落在 `PersonaTest`；附加段穷尽、默认档不追加、派生用途拒绝在 `PersonaPromptsTest`；身份链与「版本 ∧ 人设」拒绝在 `PersonaIdentityTest` 与 `GenerationCoordinatorIdentityTest`；两安装路径同一声明在 `TreeLearnSchemaPersonaTest`；窄写不碰 `updated_at` 与未知值回落标记在 `TopicPersonaDaoStatementTest`。
- Changed/deleted assertion rationale and equivalent behavior coverage: `ProviderModelSchemaTest`、`TreeLearnSchemaAnnotationsTest`、`TreeLearnSchemaQuoteInvariantTest` 的当前版本字面量 5→6——三个套件各自的注释都声明「当前版本是它唯一钉住的字面量」，随版本 bump 更新是设计内行为，其余断言未动。`TreeLearnSchemaPersonaTest.theColumnIsWrittenOnce…` 初版要求源码出现 `String Topics.PERSONA_DECLARATION =`，而常量声明在各表内部类中，该文本不可能存在；已改为计数三处 `String PERSONA_DECLARATION =` 并保留「默认 wireId 只在枚举里写一次」的零计数断言，意图不变且不再要求一个无法成立的写法。
- Gaps: 真机 SQLite 上的 V5→V6 迁移执行、未知值读取与窄写只由 JVM 文本/契约测试覆盖；设备证据按既有拍板并入片 070（全量仪器测试会卸载应用并清本地数据）。本片无 UI，界面验收在片 066。
- Risks: 迁移与新装列定义若漂移，JVM 只能比对常量文本；仪器列期望已同步，真机执行仍是最终判据。

## Handoff notes

- Completed: 七档枚举、附加段、身份链（命令/请求/操作行/稳定行）、V6 迁移与主题窄写/回落读取；JVM 全绿、APK 构建通过；仪器列期望已同步。
- In progress: 无（本片收口）。
- Blocker: 无。
- Next steps: 片 066 选择器——ChatViewState/Presenter/Actions 暴露主题人设、PersonaPicker、窄写与回落 toast；介绍页与设置入口在片 067；设备证据随片 071。
- Watch-outs: 不改基线正文；切换人设不刷新 `updated_at`；不把回落塞进 `Topic` 构造器；生产链路只走带人设的重载。
