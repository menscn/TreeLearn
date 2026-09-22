import pathlib, re, io, sys

TASKS = pathlib.Path(r"D:\Project\Android\Treelearn\.trae\tasks")

COMMON = """> 独立 verifier 复核（verifier #2，2026-09-21），只读：未修改任何生产代码、测试或他人证据。
> 本轮按用户 2026-09-21 指令**不使用模拟器**；凡严格需要真机的验收项一律记为
> `deferred — 本轮不使用模拟器（用户指令 2026-09-21）`，既不算 PASS 也不算 FAIL。
> verifier 自产证据见 `artifacts/evidence/VERIFY2-*.txt`（helper 脚本在 `artifacts/evidence/.verify2/`）。
"""

REPORTS = {}

REPORTS["T-2026-09-20-deepseek-stream-gateway-006.md"] = COMMON + """
- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/ai/src/main/java/com/treelearn/ai/gateway/**`（11 个生产类）；
  `:app` 三个设备用例；证据 `T-006-{red,green,tests,guard,sizing,device-isolated}.txt`；
  verifier 证据 `VERIFY2-{jvm-suites,sizing,assemble-androidtest,guard,lint,static-findings}.txt`。
- 代码量: **748** 非空非注释行（verifier 自带计数器复算，11 文件；任务自报 749，差 1 在
  `AiGateway.java` 的 263/264，物理行 1121 两边一致）/ 500 硬上限。
  `sizing-adjudication-2026-09-21.txt` 的已批准例外表**不含本任务** → **规模判据 OPEN，未获批**。
- 任务合规: PASS（template_version 2；`id: T-2026-09-20-006` 与文件名一致；`status: verifying`；
  RED/GREEN 五个键均为纯值、两条 `Command` 逐字节相同；`validate_tasks.py` 对本文件 PASS）。
- Lint: `:app:lintDebug` PASS（EXITCODE=0，`VERIFY2-lint.txt`）；`:ai` 为 java-library 无 lint 任务。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`，EXITCODE=0）。
- 验收点对照:
  - [x] 1 先 RED 后 GREEN — verifier 亲自复跑 `:ai:testDebugUnitTest --rerun-tasks`：gateway 4 个类
        **45 例全绿（12+16+9+8）**，见 `VERIFY2-jvm-suites.txt`。RED 的 155 可核：
        `T-006-red.txt` 第 373 行 `100 个错误`、第 620 行 `55 个错误`，100+55=155；九类符号缺失
        之和亦为 155（36+32+25+22+16+10+9+4+1）。
  - [x] 2 两通道按各自顺序回调、EOF 无 `[DONE]` 不报成功 — JVM `SseFrameParserTest`/`AiGatewayStreamTest`
        覆盖，verifier 复跑全绿。
  - [x] 3 401/402/422/429/503、超时、取消、解析失败的稳定分类 — 判定收敛在
        `AiGatewayError.forHttpStatus` 单点，`Kind` 与《技术方案》§9.5 表逐条一致（多出的
        `PARSE_FAILED` 由本任务验收条件第 3 条明确要求）。
  - [ ] 4 请求体 role/顺序/UTF-8 原样编码 — 其证据是**设备用例** `DeepSeekRequestBodyInstrumentedTest`。
        **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。可得的最强替代：verifier 先清空
        `app/build/intermediates/javac/debugAndroidTest` 再跑 `:app:assembleDebugAndroidTest`，
        `:app:compileDebugAndroidTestJavaWithJavac` **真实执行**、产出 27 个 class、EXITCODE=0
        （`VERIFY2-assemble-androidtest.txt`）。**只证明这套 instrumentation 源码可编译，未执行任何用例。**
  - [x] 5 真实 Key 冒烟 — **blocked**：未提供 `TREELEARN_DEEPSEEK_API_KEY`，未对真实端点发起过请求；
        不以 fixture 冒充 live。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`，3 个 APK dex 级确认
  无 Kotlin 运行期字节码，EXITCODE=0）。
- 文档影响记录: **PASS** — 本任务自报「README 未描述 gateway 类」是**自报缺口而非矛盾**：
  `android/README.md:46` 已把 `:ai` 描述为「DeepSeek HTTPS/SSE、上下文编码、版本化提示词注册表与请求组装」；
  《技术方案》§9.1（base URL、仅 HTTPS）、§9.3（SSE 分帧规则）、§9.5（错误映射表）逐条与实现一致。
  README 只为 `:core`/`:data` 建类级索引，`:ai` 本就没有类级索引，故不列类名不构成「与现行事实矛盾」。
- 启动证据: N/A（库模块，无进程/端口/环境变量变化）。
- 结论: **PARTIAL**
- 修复指令: ① 规模：由用户明确裁定「批准 748 行例外（若在实现完成之后批准，须如实记为事后追认）」或
  按「SSE 分帧 + 载荷解码」/「传输 + 错误分类 + 编排」拆成两个任务；
  ② 真实 Key 冒烟待授权提供临时 key 后补跑，证据不得含 key、问题或正文。
- 阻塞点与剩余风险: 规模例外未批准；真实 Key 冒烟未执行；设备行为未执行（本轮明确 deferred）；
  供应商契约（模型 ID / base URL）发布前需按官方文档复核。
- 新型错误（建议追加 AGENTS.md 失败案例库）: **把现行文档已经覆盖的事实自报成「未完成的文档缺口」**
  → 会让 verifier 去修一个不存在的缺口（或据此误判 FAIL）。规则：自报文档缺口时必须给出
  「哪一行、与哪一条实现事实矛盾」的对照；给不出对照的按假缺口计，只作提示不作判据。
"""

REPORTS["T-2026-09-20-immutable-context-snapshots-007.md"] = COMMON + """
- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/core` 的 `JsonText`/`ContextSnapshot`/`ContextSnapshotCodec`/
  `ContextPlan`/`BuildContextPlanUseCase`，`android/ai` 的 `DeepSeekContextEncoder`，`DomainErrorCode`；
  证据 `T-007-{red,green,tests,checks,mutation,guard,regression,validate-tasks}.txt`；
  verifier 证据 `VERIFY2-{jvm-suites,sizing,guard,static-findings}.txt`。
- 代码量: **662** 非空非注释行（verifier 复算：5 个 `:core` 新文件 575 + `DeepSeekContextEncoder` 87；
  任务自报 664，物理行 895 与我一致），另加 `DomainErrorCode.java` 改动 7 行 → 合计 **669–671** / 500 硬上限。
  无已批准例外 → **规模判据 OPEN，未获批**。
- 任务合规: PASS（template_version 2；id/文件名一致；`status: verifying`；RED/GREEN 键为纯值且两条
  `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: N/A（`:core` 为纯 java-library，无 lint 任务）；Java-only 守门见下。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`，EXITCODE=0）。
- 验收点对照:
  - [x] 快照不可变、digest 由构造路径派生、每分支唯一 — JVM 用例 + `ContextSnapshotDao` 唯一索引用例。
  - [x] 上下文顺序、四标签封装、转义与「伪造边界」防护 — verifier 复跑 `:core:test` 与 `:ai` 全绿：
        `ContextSnapshotTest` 12、`BuildContextPlanUseCaseTest` 10、`DeepSeekContextEncoderTest` 8。
  - [x] fail-closed（缺 revision / digest 不符 / 非法 JSON / 摘要缺失 / 超窗）—
        `ContextSnapshotCodec` 的拒绝顺序固定为「版本 → 结构 → digest」，六个 `SNAPSHOT_*`/`CONTEXT_*`
        码**每一个都有 main 源里的生产者**（verifier 逐码 rg 复核，见 `VERIFY2-static-findings.txt` 第 3 节）。
  - [x] `:core` 不出现供应商标签 — verifier 对 `android/core/src/main`、`android/data/src/main`、
        `android/tree-layout/src/main`、`android/app/src/main` 做机械检索：**0 命中**；标签只在 `:ai`。
  - [ ] TDD 证据完整性 — **部分成立**：任务已自述「`--continue` 未加，`:ai` 一侧的 RED 是静态事实而非
        观测到的失败」（任务文件第 105-107 行）。该缺口按原文保留，不按观测到的 RED 计。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS — verifier 在 `android/README.md` 复核到任务声称的 5 行（180 `ContextSnapshot`、
  181 `ContextSnapshotCodec`、185 `ContextPlan`、186 `BuildContextPlanUseCase`、192 `JsonText`），
  且 §5.3/§9.4 与实现形态一致。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: ① 规模：用户明确裁定例外或拆成「快照 codec/实体」与「上下文组装 + 编码器」两个任务；
  ② 若要把 TDD 证据补全，需在 `:ai` 侧单独制造一次可观测的 RED（例如在隔离副本上编译）并把命令与
  退出码落盘。
- 阻塞点与剩余风险: 规模例外未批准；`:ai` 侧 RED 未被观测；provider 对标签文本的解释与 token 膨胀
  只能由 T-018/T-019 的真实行为验收覆盖。
"""

REPORTS["T-2026-09-21-versioned-learning-prompt-policy-020.md"] = COMMON + """
- Verdict: **PASS**
- 审阅文件: 本任务文件；`android/ai/src/main/java/com/treelearn/ai/prompt/**`（8 个生产文件）；
  `SystemBodyAssertions.java` 与两个测试类；证据 `T-020-{red,green,tests,mutation,guard,regression,sizing,transcription}.txt`；
  verifier 证据 `VERIFY2-{jvm-suites,T020-leakscan,T020-transcription,sizing,static-findings}.txt`。
- 代码量: **310** 非空非注释行（verifier 复算 8 个生产文件，不含 `DeepSeekContextEncoder`——那是 T-007 的）；
  任务自报 344，差异全部落在 `PromptBodiesV1.java`（我 23 / 它 55），原因是计数器对 Java
  **文本块正文**是否计为「代码行」口径不同。两种口径都**低于 500 硬上限**，只触发 200 软上限警告，
  不改变判定。物理行 563 与我 563 一致。
- 任务合规: PASS（template_version 2；id/文件名一致；RED/GREEN 键为纯值、两条 `Command` 逐字节相同；
  `validate_tasks.py` PASS）。
- Lint: N/A（`:ai` 为 java-library）。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`）。
- 验收点对照:
  - [x] 先 RED 后 GREEN；每个 `PromptPurpose` 有唯一非空版本与非空 system；派生用途与主回答隔离 —
    verifier 复跑 `:ai:testDebugUnitTest --rerun-tasks`：`DeepSeekPromptRegistryTest` 8 +
    `DeepSeekRequestAssemblerTest` 11 = **19 例全绿**（`VERIFY2-jvm-suites.txt`）。
  - [x] system 恰好一条且首位、当前 user 末位、thinking 只追加静态段、无运行时插值 — 同上 19 例覆盖。
  - [x] 预算先预留 system/当前问题/最小回答，不足即拒绝构建且不裁剪 system — 变异证据 B 证明该断言可证伪。
  - [x] 历史内容含 `system:`、同名标签或「忽略此前规则」时 role/order/用途不变 — 变异证据 A/C 各自
    让对应用例转红，还原后 19 例重新全绿（SHA-256 自证还原）。
  - [x] **正文与设计文档逐字一致** — verifier **独立复算**：从设计文档的 9 个 ```text 围栏块按
    `"\n".join` 计算 SHA-256，**9/9 命中**任务声明的摘要；再独立地从
    `PromptBodiesV1.java` 的 9 个文本块（正确处理 Java 行尾 `\\` 续行转义）重算，**9/9 同样命中**
    （715/101/186/102/116/156/200/187/174 字符）。见 `VERIFY2-T020-transcription.txt`。
  - [x] 日志/异常不含正文 — **独立复核通过**：以设计文档的全部 130 段 CJK 连续串切成 1661 个
    14 字窗口，对 `artifacts/evidence/**` 全量扫描，**共享窗口数 = 0**；`SystemBodyAssertions`
    在断言转红时只打印长度与 SHA-256（`SystemBodyAssertions.java:28-34`）。
    （残留提示，非判据：`T-020-mutation.txt` 的变异 A 仍会经 JUnit `ComparisonFailure` 打印两处
    **合成夹具问题串**——它们是测试源码里公开的夹具数据，不是真实用户内容、也不是提示词正文。）
  - [x] token 预算按 UTF-16 字符而非模型 token — 已在 `PromptBudget` javadoc 与验收条件第 6 条声明，
    verifier 接受该偏离（单位不同、四项行为逐字一致）。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS — `documentation impact: none` 的支撑经 verifier 逐条核对成立：
  《技术方案》§9.2 第 202 行逐字命名 `DeepSeekPromptRegistry` 与 `DeepSeekRequestAssembler` 并给出
  system/history/user 顺序；`android/README.md:46` 已把 `:ai` 描述为「版本化提示词注册表与请求组装」
  且「不接受用户自定义 system 模板」。
- 启动证据: N/A。
- 结论: **PASS** → `status: verified`
- 修复指令: 无阻塞项。可选清理：把 `T-020-mutation.txt` 里两处夹具问题串也改为只打印长度/摘要，
  使证据文件对「任何正文形内容」都保持零回声。
- 阻塞点与剩余风险: 无。真实模型行为（自然语言风格的非确定性）按任务声明由 T-018/T-019 验收，
  不属本任务判据。
- 新型错误（建议追加 AGENTS.md 失败案例库）: **「计数口径不同」被误当成「数字对不上」**：
  同一个目录，文本块正文计不计入「非空非注释生产行」会让 `PromptBodiesV1.java` 出现 23 vs 55 的差，
  合计数 310 vs 344。规则：给出规模化数字时必须**同时写明计数器对文本块/续行的处理**，
  否则两方都会「按自己的口径复算并认为对方错了」。
"""

REPORTS["T-2026-09-20-pure-java-tree-layout-016.md"] = COMMON + """
- Verdict: PARTIAL（其中「文档一致性」一项 **FAIL**）
- 审阅文件: 本任务文件；`android/tree-layout/src/main/java/com/treelearn/treelayout/**`（12 个生产类型）；
  证据 `T-016-{red,green,checks}.txt`；verifier 证据
  `VERIFY2-{jvm-suites,sizing,guard,static-findings}.txt`；`android/README.md`；`android/build.gradle`；
  《技术方案》V1.0。
- 代码量: **862** 非空非注释行（verifier 独立复算，12 文件，与任务自报一致；物理行我 1259 / 它 1247，
  差 12 同样来自注释与空行口径）/ 500 硬上限 → **超出 72%，规模判据 OPEN，未获批**。
- 任务合规: PASS（template_version 2；id/文件名一致；`status: verifying`；RED/GREEN 键为纯值、两条
  `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: N/A（`:tree-layout` 为纯 JVM 模块）。
- 依赖方向: **PASS** — `verifyDependencyDirection: PASS (downward-only module graph + pure-JVM module purity)`，
  EXITCODE=0；另经 verifier 机械复核：`rg '^import (android|androidx|kotlin)' android/tree-layout/src` → 0 命中，
  `android/tree-layout/build.gradle` 中 `project(` → 0 行，`android/build.gradle:27` 为 `':tree-layout': []`。
- 验收点对照:
  - [x] 先 RED 后 GREEN；输出确定且所有坐标/bounds 有限 — verifier 亲自复跑
        `:tree-layout:test --rerun-tasks`：3 个类 **34 例全绿（15+11+8）**，`VERIFY2-jvm-suites.txt`。
        RED 的自洽性：34 completed / 25 failed，12+7+6=25。
  - [x] 叶/枝/芽数量、枝长轮数、折叠后代与未读聚合、归档子树不入默认布局 — 由 `T-016-red.txt` 的
        桩探针与还原哈希（`FA72FB2E…` 桩 / `141FD565…` 还原）证明失败确由缺失实现造成。
  - [x] 标签互不重叠、可省略但命中区仍在且不小于交互下限 — 34 例覆盖（叶片盒在测试里独立重算）。
  - [x] 手机竖屏与平板横屏 fixture 的 fit bounds、底部对齐与 stable anchor delta — 几何断言全绿；
        **真机渲染不在本任务判据内**（画布是 T-017）。
  - [ ] **文档一致性 — FAIL**：`android/README.md:36-37` 仍断言「`:tree-layout` 仍**没有** `src/main/java`
        源码，扫描在该模块上仍是空跑」、第 38-39 行断言「由 T-016 填入代码后自动转为真实强制」——
        本任务已在该模块落地 12 个生产文件，该**当前态陈述与活动的生产事实矛盾**。
        同时《技术方案》V1.0 第 54 行「`:tree-layout` 只依赖 `:core`」与
        `android/build.gradle:27`（零项目依赖）矛盾。
        任务把这些记入 Handoff 并另立后续项，但按 `.trae/rules/documentation.md`
        「延后关键文档更新需要获得明确的用户批准，并让工作保持 PARTIAL 或 blocked」——
        **未取得用户批准的延后使该判据不成立**；仅建后续 issue 不让当前变更完整。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: **FAIL**（记录本身诚实地报告了缺口，但缺口未按规则闭环：既未改文档，也无用户批准的延后）。
- 启动证据: N/A。
- 结论: **PARTIAL**（功能判据 PASS；文档判据 FAIL；规模判据未获批）
- 修复指令: ① 把 `android/README.md:34-39` 的「覆盖缺口」段改为现行事实（`:tree-layout` 已有 12 个
  生产文件、纯净性扫描已真实强制、WARNING 消失）；② 修正《技术方案》第 54 行关于 `:tree-layout`
  依赖方向的描述（或改为「`:tree-layout` 不依赖任何项目模块」）；两条都应由**文档收尾任务**完成，
  并在该任务里引用 `verifyDependencyDirection` 的输出作为校准证据；③ 规模由用户裁定例外或按
  「输入模型 + 校验」/「几何 + 标签栏 + 命中 + 拟合」拆解。
- 阻塞点与剩余风险: 目录里 README 的过时断言会继续误导（例如让人以为纯净性扫描仍是空跑）；
  规模例外未批准；几何参数若再调常量需同步改测试并重跑（golden 由测试独立重抄）。
"""

REPORTS["T-2026-09-21-sqlite-dao-revisions-operations-023.md"] = COMMON + """
- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/data/.../db/dao/{AnswerRevisionDao,GenerationOperationDao,
  GenerationOperationRow,GenerationStabilizer,ConstraintFailures}.java`；
  `:app` 的 `AnswerRevisionGenerationOperationDaoInstrumentedTest.java`；
  证据 `T-023-{red,green,tests,mutation,guard,guard-selftest,lint,core-javadoc,core-tests,
  prompt-body-surface,restore-location,validate-tasks}.txt`；verifier 证据
  `VERIFY2-{assemble-androidtest,guard,lint,sizing,static-findings}.txt`。
- 代码量: **454** 非空非注释行（verifier 独立复算 5 个文件：84+38+202+45+85；物理行 832）/ 500 硬上限
  → **在预算内**（超 200 软上限，警告）。
- 任务合规: PASS（template_version 2；`id: T-2026-09-21-023` 与改名后的文件名一致；`status: verifying`；
  RED/GREEN 键为纯值且两条 `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: `:app:lintDebug` PASS（`abortOnError true` 下 BUILD SUCCESSFUL，EXITCODE=0，`VERIFY2-lint.txt`）。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`）。
- 验收点对照:
  - [x] 先 RED 后 GREEN（编译级）— RED 自洽：`T-023-red.txt` 结论段「javac 自报 53 / 找不到符号 53 /
        程序包…不存在 0」→ 0+53=53，与任务正文一致。
  - [x] **版本随同复制**、**稳定化全败（回滚）** — 这是本任务两条最关键的断言，`T-023-mutation.txt`
        对两者各做了一次变异：把版本改成从用途常量重推（应捕获
        `stabilizeCopiesThePinnedVersionFromTheRowVerbatim`）、把拒绝改成从工作体返回而不抛异常
        （应捕获「revision 已写但 operation 非终态」被提交），改完用 SHA-256 证明还原。
        该证据结构成立（变异 → 对应用例转红 → 还原）。
  - [ ] **instrumentation 执行** — **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
        可得的最强替代：verifier 先清空 `app/build/intermediates/javac/debugAndroidTest` 再跑
        `:app:assembleDebugAndroidTest`，`:app:compileDebugAndroidTestJavaWithJavac` **真实执行**、
        产出 27 个 class（含 `AnswerRevisionGenerationOperationDaoInstrumentedTest.class`）、
        EXITCODE=0（`VERIFY2-assemble-androidtest.txt`）。**只证明可编译，未执行任何用例。**
  - [x] **所报「T-023 DAO 设备用例失败」已静态定性 —— 不是本任务的缺陷。**
        (a) 断言与生产代码**不矛盾**：`GenerationStabilizer.java:119-125` 对
        `row.partialAnswer.trim().isEmpty()` 返回 `DomainErrorCode.OPERATION_NOT_COMPLETABLE`，
        测试第 491-497 行断言同一码、并断言未写入 revision、状态仍为 `STREAMING_ANSWER`；
        (b) 那份 XML 快照（verifier 存档 `VERIFY2-device-xml-snapshot.xml`）里
        `tests="1" failures="1"`、`<failure></failure>` **为空**，`<system-err>` 写的是
        `Test run failed to complete. Instrumentation run failed due to Process crashed.`——
        即**进程被杀**，不是断言失败；verifier 后续直接 `adb shell am instrument` 复现时两次拿到
        `INSTRUMENTATION_RESULT: shortMsg=Process crashed.`，且同一时段另有 lane 在执行
        install/uninstall（`pm list instrumentation` 一度为空）。
        **一次真机运行本应显示**：该类 12 个用例逐个出现 `INSTRUMENTATION_STATUS_CODE: 0`，最后一行
        `OK (12 tests)`；若真有缺陷，失败会带堆栈与具体用例名，而不是空 `<failure/>`。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）。
- 文档影响记录: PASS（`android/README.md` 的 `:data` 持久化入口索引含本任务两表 DAO 行；
  verifier 在 README:228/235/236 复核到 `BranchDao`/`ContextSnapshotDao`/`AiArtifactDao` 行）。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: 无代码级修复指令。待设备可用时整包跑一次
  `:app:connectedDebugAndroidTest`，从 JUnit XML（`TEST-<device>-_app-.xml`）读回 64/52+ 例的
  `failures="0" errors="0"`，并确认 `AnswerRevisionGenerationOperationDaoInstrumentedTest` 的 12 例在列。
- 阻塞点与剩余风险: 设备执行 deferred；`generation_operations` 的活动上限依赖 T-004 的偏索引
  （本任务只做翻译，未经真机验证）；夹具「篡改过的 operation 行」形态已被变异测试覆盖，可信。
"""

REPORTS["T-2026-09-21-sqlite-dao-snapshot-artifact-024.md"] = COMMON + """
- Verdict: PARTIAL
- 审阅文件: 本任务文件；`android/data/.../db/dao/{ContextSnapshotDao,AiArtifactDao}.java`；
  `:app` 的 `ContextSnapshotAiArtifactDaoInstrumentedTest.java`；证据
  `T-024-{red,green,tests,guard,regression-interference}.txt`；verifier 证据
  `VERIFY2-{assemble-androidtest,guard,lint,sizing,static-findings}.txt`。
- 代码量: **205** 非空非注释行（verifier 独立复算：`AiArtifactDao` 138 + `ContextSnapshotDao` 67；
  物理行 338）/ 500 硬上限 → **在预算内**（超 200 软上限，警告）。
- 任务合规: PASS（template_version 2；id/文件名一致；`status: verifying`；RED/GREEN 键为纯值且两条
  `Command` 逐字节相同；`validate_tasks.py` PASS）。
- Lint: `:app:lintDebug` PASS（EXITCODE=0）；本任务无独立 lint 证据文件，由 verifier 补跑覆盖。
- 依赖方向: PASS（`verifyDependencyDirection: PASS`）。
- 验收点对照:
  - [x] 先 RED 后 GREEN（编译级）— RED 自述 61 个错误全部落在两个新 DAO 的符号缺失上，
        与「把两个文件暂时移出源码树」这一操作相符。
  - [x] 快照不可改写/不可单独删除、每分支唯一、CAS 写回与 stale 丢弃、scope 清理不误删 —
        由 12 个 instrumentation 用例设计覆盖（含 `artifactWithAClearedBaselineNeverAcceptsAWriteback`、
        `illegalJsonPayloadIsStoredVerbatimAndFailsOnlyWhenParsed`、`onlyPayloadColumnsCanCarryBodyText`）。
  - [x] 「本层不解析载荷」— 代码层成立：`payload` 只作不透明字符串传递，结构化解析只在 `:core`
        `ContextSnapshotCodec`（README:235 已把该边界写成当前态事实）。
  - [ ] **instrumentation 执行** — **deferred — 本轮不使用模拟器（用户指令 2026-09-21）**。
        最强替代：`:app:compileDebugAndroidTestJavaWithJavac` **真实执行**并产出
        `ContextSnapshotAiArtifactDaoInstrumentedTest.class`（含内部类），EXITCODE=0
        （`VERIFY2-assemble-androidtest.txt`）。**只证明可编译，未执行任何用例。**
  - [x] 仓库级 `gradlew check` 的缺口被如实留存 — `T-024-regression-interference.txt` 记录的是
        **并行 lane 在 `:ai` 写 RED 时** `:ai:compileDebugUnitTestJavaWithJavac` 失败，
        不是本任务的红；verifier 独立复跑 `:core:test :ai:testDebugUnitTest :tree-layout:test`
        （`--rerun-tasks`）在 lane 静默后全绿（`:core` 154、`:ai` 72、`:tree-layout` 34，0 失败），
        该干扰项已消解。
- 专项守门路由: android-no-kotlin-guard PASS（`VERDICT: PASS (13 OK line(s))`）；
  `db-migration-reviewer` 对本任务 DDL 约束/级联/CAS 的复核由 T-004/T-022 的守门轮次覆盖，
  本任务未改 DDL。
- 文档影响记录: PASS（README:235 `ContextSnapshotDao`、README:236 `AiArtifactDao` 两行经 verifier 复核存在，
  且已写明「多态 scope 无外键、删除 scope 不自动清理」这一边界）。
- 启动证据: N/A。
- 结论: **PARTIAL**
- 修复指令: 无代码级修复指令。设备可用时整包跑 `:app:connectedDebugAndroidTest`，确认本任务 12 例
  在列且 `failures="0" errors="0"`；注意该 XML 每次运行被最后一个 lane 覆盖，须在跑完后立刻存档。
- 阻塞点与剩余风险: 设备执行 deferred；`ai_artifacts.scope_id` 是多态键、无外键，清理是调用方义务，
  只能靠 `deleteByScope` 的用例保证，未经真机执行前该保证未闭环。
"""

def replace_section(path: pathlib.Path, body: str) -> str:
    text = path.read_text(encoding="utf-8")
    lines = text.split("\n")
    start = None
    for i, l in enumerate(lines):
        if l.strip() == "## Verification report":
            start = i
            break
    if start is None:
        return "NO SECTION"
    end = len(lines)
    for j in range(start + 1, len(lines)):
        if lines[j].startswith("## "):
            end = j
            break
    new = ["## Verification report", ""] + body.strip("\n").split("\n") + [""]
    out = lines[:start] + new + lines[end:]
    path.write_text("\n".join(out), encoding="utf-8", newline="\n")
    return "OK"

for name, body in REPORTS.items():
    p = TASKS / name
    print(f"{name}: {replace_section(p, body)}")

# T-020 passes -> set status: verified
p = TASKS / "T-2026-09-21-versioned-learning-prompt-policy-020.md"
t = p.read_text(encoding="utf-8")
t2 = t.replace("status: verifying", "status: verified", 1)
p.write_text(t2, encoding="utf-8", newline="\n")
print("T-020 status -> verified" if t2 != t else "T-020 status unchanged")