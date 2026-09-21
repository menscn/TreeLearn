# TreeLearn Harness 全量开发交付提示词

## 使用前准备

> **⚠️ 已被用户 2026-09-21 指令取代（本文件后续的「API 28」要求一律作废）**
>
> 用户明确指示：**「SDK 使用 android-36/36.1，不在考虑 28」**。
> 因此本文档中所有「补齐 API 28 环境」「在 API 28 手机竖屏验证」「API 28 system image」
> 的要求均**不再执行**，统一替换为 API 36 / 36.1 模拟器与代表性平板横屏环境。
> `minSdk 28` 仅作为声明的最低兼容版本保留，其兼容性由 `compileSdk 36 + minSdk 28`
> 的 lint `NewApi` 分析覆盖（T-002 已验证该安全网有效：向 Activity 注入 API 30 调用会直接
> 让构建以 `Error: Call requires API level 30 (current is 28) [NewApi]` 失败）。
> 下方正文按原文保留以维持可追溯性，实际执行以本横幅为准。

- 在启动执行 AI 的同一进程环境中设置 `TREELEARN_DEEPSEEK_API_KEY`，不要把真实 Key 写进提示词、聊天、仓库或任务文件。
- 本机 Android SDK 位于 `D:\Environment\Android\SDK`，当前已有 API 36 手机模拟器 `emulator-5554` 在线；~~执行者仍须补齐 API 28 与代表性平板横屏验证环境~~ → 只需补齐代表性平板横屏验证环境（API 36）。
- 将下面代码块整体作为一个新任务发送。使用支持长时间执行、终端、文件编辑和子 agent 的最高能力编码模型。

## 可直接发送的提示词

```text
你是 Treelearn 项目的 Harness 总执行者。请在 D:\Project\Android\Treelearn 中立即开始实现，不要只输出计划，不要重新询问已经确认的产品决策，也不要在某个 Task 实现后提前结束。你的目标是在本次长任务中持续工作，按依赖完整执行 T-002 到 T-020（T-019 仍是最后的独立验收任务），最终交付一个已签名、已安装验证、可在个人 Android 设备侧载使用的 APK。

一、权威输入
1. 先完整阅读根 AGENTS.md、.trae/HARNESS.md，以及当前阶段实际命中的 rules/agents/skills；严格遵守按需读取协议。
2. 读取并以这些当前态文档为产品和技术权威：
   - Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.0.md
   - Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md
   - Docs/03-prd-draft/V3.1-知芽（TreeLearn）产品需求文档（PRD）.md
   - Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html
   - Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md
   - Docs/05-technical-design/知芽（TreeLearn）DeepSeek自适应学习导师提示词设计_V1.0.md
3. 读取 .trae/tasks/T-*.md 全部 Task v2，并先运行 python .trae/scripts/validate_tasks.py。任务已确认，不要重新拆任务或改变产品范围。

二、必须保持的产品与架构约束
1. 纯原生 Android、Java-only、minSdk 28、XML/View、五模块 :app/:core/:data/:ai/:tree-layout；禁止 Kotlin 源码、插件、org.jetbrains.kotlin 和 -ktx。
2. 本地优先模块化单体，无 backend/frontend、账号、云同步、备份或远程遥测。
3. AnswerRevision 不可变；重试/重置创建新 operation/revision，既有子分支永远引用创建时 sourceRevisionId 与 ContextSnapshot。
4. reasoning_content 必须流式展示、部分落库、稳定版本持久化、可搜索、复制全文时包含，并通过 ContextPlan + DeepSeekContextEncoder 加入后续上下文。每条思考默认折叠，点击展开，使用可读的次级灰色。
5. 主回答必须使用 `tl-learning-answer/1.0.0` 自适应学习导师 system；system 恰好一条且第一，当前 user 最后，不进入 ContextPlan/摘要/裁剪。标题、摘要、回答推荐、首页推荐和上下文压缩使用独立 PromptPurpose，禁止复用教学提示词。operation/revision/artifact 只保存用途/版本，不保存 system 正文。
6. 每分支最多一个活动生成，全应用最多两路；切换分支不取消。进程死亡保留最近落盘的部分 reasoning/answer，标记 INTERRUPTED，只允许用户手动重试。
7. 数据库是 Android SQLite。严格按 .trae/rules/sql.md 的 Android SQLite 分支验证，不要搭建无生产消费者的 MySQL。
8. API Key 仅通过 Android Keystore + AES-GCM 保存；不得进入 SQLite、日志、Bundle、剪贴板、测试 fixture 或仓库。system 提示词和用户/历史/派生正文也不得进入日志、异常或交付报告。

三、环境准备
1. 自动设置 ANDROID_HOME/ANDROID_SDK_ROOT=D:\Environment\Android\SDK，并创建 gitignored 的 android/local.properties（sdk.dir 指向该路径）。
2. 检查 JDK/Gradle/SDK licenses/build-tools/platform-tools。缺少 cmdline-tools 或平板 AVD 时，使用官方 Android SDK 工具自动安装和创建，不要把环境准备交还给我。（~~API 28 system image~~ 已按用户指令取消）
3. 当前 emulator-5554/API 36 可用于开发冒烟；最终必须增加代表性平板横屏环境。（~~API 28 手机环境~~ 已按用户指令取消）
4. 真实 DeepSeek 验收只从进程环境变量 TREELEARN_DEEPSEEK_API_KEY 读取临时 Key，绝不打印其值。若变量缺失，仍完成所有离线工作，但 T-018/T-019 不得伪造 PASS；清楚记录唯一阻塞项。

四、Harness 执行纪律
1. 按 depends_on 拓扑执行：002 → 003 → 004/005/006/016 → 007 → 020 → 008/009 → 010/011 → 012/013/014 → 015 → 017 → 018 → 019。实际开始每个 Task 前重新确认它的全部依赖已 verified。
2. 你是总调度者。环境支持 subagent 时，按 .trae/agents 使用 android-dev 实现、android-no-kotlin-guard、db-migration-reviewer、contract-guardian 和 verifier 独立复核；不要让不同 implementer 同时修改同一 Task。verifier 必须串行。
3. 每个实现 Task：ready → in_progress；先写目标测试并运行得到非零退出码，将真实 RED 命令/退出码/失败原因写回 Task；再做最小实现，用同一目标命令得到 GREEN；补回归证据后进入 verifying。只有独立 verifier PASS 才改为 verified。
4. verifier FAIL/PARTIAL 时立刻回派原 Task 做最小修复并新增回归测试，然后重新验证；不要绕过 guard，也不要把已知失败报成完成。
5. 实现中发现任务估算不准时仍遵守单 Task 200 行软预算和 500 行硬上限；必要时先在 .trae/tasks 中做最小、可追踪拆分，并重新运行任务校验。
6. 不自动提交 Git，除非我另行明确要求。保留并避开你开始前已经存在的无关工作区改动。

五、强制验证
1. 每轮关键集成后运行相关 JVM/unit/instrumentation；最终运行 assemble、lint、全部测试、依赖方向、secret scan、内容日志扫描、Java-only guard 和 python .trae/scripts/validate_tasks.py。
2. T-004 必须在 API 36 模拟器或真机通过生产 SQLiteOpenHelper 路径验证新装、升级、关闭重开、事务回滚、唯一/外键/级联、PRAGMA foreign_keys、foreign_key_check、integrity_check 和 user_version。
3. 使用临时 Key 验证 DeepSeek 余额、两个当前官方模型、thinking on/off、SSE 分块与错误映射，并以脱敏测试钩子证明后续上下文实际包含历史 reasoning；证据不得包含正文或 Key。
4. 对 T-020 运行确定性 fixture：PromptPurpose/版本唯一、system 首位且唯一、当前 user 末位、system 不裁剪、thinking 静态附加段、恶意历史文本不改变角色/协议、operation 到 revision 版本复制。真实 DeepSeek 再用脱敏结构断言覆盖入门/专业问题、错误前提、歧义、时效信息、类比边界、reasoning 折叠和派生 JSON；不得把单张“看起来不错”的截图当作完整证据。
5. 在 API 36 手机竖屏和代表性平板横屏上真实操作首页、设置、生成、思考展开、分叉、重置、搜索、归档、画布、后台切换和进程死亡恢复。保留截图、日志与命令证据；Canvas 做非空像素检查，UI 不得重叠。
6. 未提供正式商店签名时，生成 Treelearn 专用个人侧载 keystore，保存在仓库外；口令只放环境变量或未跟踪本地配置且不得打印。用它签名 release APK，运行 apksigner verify --print-certs，并验证同签名覆盖安装。记录证书 SHA-256 和 APK SHA-256，不记录口令。

六、完成定义与交付
1. T-002 至 T-018 及 T-020 全部 verified，T-019 独立验收 PASS，父任务 T-001 才能标记 verified。
2. 将最终 APK 复制到仓库 artifacts/TreeLearn-v1-release.apk，并给出绝对路径、文件大小、APK SHA-256、签名证书 SHA-256、versionName/versionCode、min/target SDK。
3. 新建 Docs/06-delivery/TreeLearn_V1_交付报告.md，记录构建环境、任务状态表、测试/设备/DeepSeek/SQLite/guard 证据、已知限制、安装命令与 APK 校验值；不得包含敏感内容。
4. 最终回复必须先说明 APK 是否可安装可使用，再给 APK 路径和校验值，然后汇总测试、设备、Task/guard 状态及任何真实未决项。不要用“理论上”“应该可以”代替实际安装证据。
5. 长命令和模拟器启动要持续轮询直至结束。除非出现无法自行恢复的外部凭据/服务阻塞，否则不要停下来向我汇报中间计划；持续执行到交付完成。
```
