# Spec: Web（H5）按 Android 现状 1:1 还原与本地 JVM 服务承载

id: SPEC-2026-10-15-web-h5-android-parity
title: Web（H5）按 Android 现状 1:1 还原与本地 JVM 服务承载
date: 2026-10-15
intent: Docs/intent/INTENT-2026-10-15-web-h5-android-parity.md
author: 技术架构师（主会话）
status: accepted

## Requirement spec（需求规格）

- 范围：新建 `frontend/`（可构建的 Web 界面工程）与 `backend/`（仅本机可达的 Java 服务），把 Android 当前界面逐屏还原为 H5 形态；还原以 Android 现状为唯一界面事实权威，高保真原型作为 token 与视觉意图来源并作为一次性提取源。**首批范围 = 屏幕清单的每一条目**，条目数不在本文复述：唯一权威是生成的 `Docs/04-ux-design/parity/screen-inventory.json` 的 `counts`（现值 14 屏 / 16 个浮层 / 其中 5 屏自绘），Android 代码一改、重跑 `python .trae/scripts/dev/gen_screen_inventory.py` 即随之更新，本文不需要跟着改。2026-10-15 拍板时写的是「全部 16 屏 + 6 浮层」，那是盘点仪器建立之前的人写估计；用户 2026-10-16 裁定「以实测清单为准」。两处差额成因不同，均已登记在 `T-2026-10-15-screen-inventory-parity-ledger-005.md`：浮层一侧是仪器的模式词表手抄导致**少计**（已修，因此 6→16 是把漏的补回来，不是范围扩张）；屏一侧的 16→14 **尚未逐条对应**——规格里没有屏清单可核对，而实测 `MainActivity.openOverview` 只是 `showTree(branchId)` 的委托（`android/app/src/main/java/com/treelearn/app/MainActivity.java:545`），说明「总览」并不构成第 15 个屏，这类「一个入口两个名字」最可能是拍板数字的来源，但要等阶段 1 逐屏对照后才能登记为结论。阶段一到阶段六不依赖任何宿主：界面由「快照夹具」驱动，夹具是从 Android 侧成对采集的真机截图与状态/几何快照。阶段七才接入 `backend/`。
- 验收方向：① 逐屏有三层判据台账（结构 / 视觉 / 行为）且每项结论可重跑；② 达绿或已被显式登记为接受的偏差，二者必居其一，不允许沉默；③ PC 端由本地服务真实启动并跑通一次导出导入；④ Android 侧数据、密钥、Java-only 与既有业务规则保持不变，并被守门证明；⑤ 同一规则在 Web 或服务侧不存在第二份实现（设计值、SQL/DDL、领域判定、树几何、枚举与错误语义）。
- 非目标：不做双活编辑与冲突合并；不做 iOS 或其它平台包；不做远程部署、多租户、云端账号或远程遥测；本期不新增英文文案；不替换或删除 Android 原生界面；不复用被移除的旧线（`35bb6ce` 之前的 `web/**`）任何代码；不把 SQLite、密钥保管或生成/流式的权威实现搬进 JavaScript。
- 来源：intent 的 Problem、Expected outcome、Core flow、Constraints and non-goals、Decision record（终态 accepted，2026-10-15 四项拍板）；界面资产盘点取自 `android/app/src/main/**`；旧线病因取自 `35bb6ce` 的提交说明与用户 2026-10-15 陈述。

## Interface plan（界面方案）

- 信息架构：照抄 Android 现况，不做信息架构重设计。单 Activity 换屏结构映射为前端路由；底部标签为**两枚**（主页 / 学习库），设置不在标签上而是从主页与对话页的齿轮进入（此处是原型与 Android 已确认不一致的一项，以 Android 为准）；抽屉是主页上的覆盖层，不是独立导航层级。
- 入口位置：全部入口按 Android 现有导航图 1:1 落位——主页（抽屉键 → 统计详情、齿轮 → 设置根、提交问题、最近学习卡、推荐项）、学习库（分段与顶部搜索框、命中结果定位跳转）、对话页（总览、导航、划词浮条、子分支浮层、版本菜单、联网检索选择、模型与思考选择、全屏编辑、语音与附件）、画布（节点弹卡、导航、打开）、设置根的五条子入口（KEY 房 → 供应商 / 模型 / 搜索服务 / 语音 / 文件管理）。
- 状态与异常：每屏必须实现并可视化以下状态类别，且彼此不可混同——加载中、可用、夹具/数据不可用、服务不可达、Provider 无 Key、生成空闲/提交/推理流/回答流/稳定/失败/中断/取消。异常文案由前端资源表提供稳定 key，业务状态只由数据侧给出，前端不得自行猜测原因。
- 数量与呈现方式：「范围」条所指清单的每一条目必须各有台账条目——屏数、浮层数、自绘屏数一律从 `counts` 读取，本文与任务文件都不复述这些数字（复述即第二份正文，必然过期）；视觉数值一律引用 token 名（`bg`/`surface`/`ink`/`ink2`/`ink3`/`line`/`hairline`/`green`/`green-deep`/`green-soft`/`green-faint`/`blue`/`amber`/`amber-soft`/`red`/`red-soft`/`yellow-highlight`、间距 `s1`–`s8` 与 `gutter`、圆角 `r-sm`/`r-md`/`r-lg`/`r-full`、字号 `fs-display`/`fs-brand`/`fs-h1`/`fs-h2`/`fs-body`/`fs-sm`/`fs-cap`、阴影 `sh-1`/`sh-2`、纸感 `paper-rule`/`paper-write` 与阅读行高 `lh-read`），触控热区下限引用 `touch_min` 这一 Android 尺寸定义；本文不出现新设计数值。形状档位只允许三档：手机基线档、平板档（复刻 `layout-sw600dp` 与 `values-sw600dp` 已存在的差异）、PC 档（阶段八新增，且只改外壳）。
- 来源：`Docs/04-ux-design/design-system/tokens.json`（其权威为高保真原型 `:root` 区块）、`Docs/04-ux-design/parity/screen-inventory.json`（范围条目数的唯一权威，由 `.trae/scripts/dev/gen_screen_inventory.py` 生成）、`android/app/src/main/res/values/dimens.xml` 与 `colors.xml`、`Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md` 的逐项对照表与偏差判定。

## Technical spec（技术规格）

### 1. 权威链（本方案的地基）

```
Android 当前实现           ← 界面事实唯一权威（1:1 的「原物」）
高保真原型 v1/v1.1         ← 设计 token 与视觉意图权威；结构与此冲突时 Android 胜；工程化后即冻结
Docs/.../tokens.json       ← 原型 :root 的机器镜像
frontend CSS 变量          ← 生成物，由 `.trae/scripts/dev/gen_design_tokens_css.py` 从 tokens.json 产出，禁止手抄（token 链的第 4 个消费者，落点 `frontend/src/styles/tokens.css`）
contracts/ 一份正文         ← DTO / 枚举 / 事件 / 错误语义唯一正文 → 代码生成 TypeScript 类型
`:core` / `:tree-layout`    ← 领域判定与树几何唯一实现
共享 schema 与 SQL 文本模块  ← DDL 与语句文本唯一实现，Android 与服务共用、驱动各一份
```

原型在 `frontend/` 建立后**不得继续被当第二份实现维护**；任何界面改动只发生在 `frontend/`，原型仅作为 token 与视觉意图的参照。这条是旧线病因的直接对症项。

### 2. 目录与分层

- `frontend/src/` 依赖方向固定为 `pages → layouts → components → hooks → store → api → platform → utils`（沿用 `.trae/rules/frontend.md` 既有约束，反向 import 由 lint 判 FAIL）。
- `backend/` 依赖方向固定为 `controller → service → repository → entity`（沿用 `.trae/HARNESS.md` 三栈水流方向）。
- 顶层目录名用 `frontend/` 与 `backend/`，**不用 `web/`**：治理体系已把 `frontend/**` 与 `backend/**` 分别授权给 `frontend-dev` 与 `backend-dev`（`.trae/rules/contracts.md` 路由表），`validate_tasks.py` 的 lane 与 stack 取值也已内置；自定义目录会落入无 owner 可写的真空。

### 3. 前端工程

React + TypeScript + Vite + pnpm；构建产物可离线打开；**不得经外部 CDN 加载脚本或字体**（原型现状如此，工程化时必须内联或改走系统字体栈）。单位约定：Android 逻辑 `dp` 与 CSS 逻辑像素按 1:1 处理，因此 `touch_min` 与既有尺寸阶可直接落到 CSS 变量。文案从资源表按 key 读取，生产代码不出现硬编码文案；本期只做简中，前端守门已删除「双语齐备」口径（见裁定 4）。

### 4. 运行时边界与适配器矩阵

`platform/` 暴露单一 `RuntimePort`（查询 / 命令 / 流式事件三类方法），实现矩阵：

| 适配器 | 阶段 | 数据来源 | 用途 |
|---|---|---|---|
| `FixtureRuntimeAdapter` | 一到六 | 快照夹具（Android 成对采集） | 还原与判据，不等宿主 |
| `JvmRuntimeAdapter` | 七起 | `backend/` 本地服务（REST + 流式事件） | PC 浏览器真实可用 |
| `MockRuntimeAdapter` | 开发 | 手工最小样例 | 组件单测，不用于任何 1:1 判定 |

组件层禁止感知自己跑在哪个适配器上；换宿主只允许改 `platform/` 与注入点。

### 5. 契约与代码生成

`contracts/` 下唯一正文定义：DTO 字段与单位、枚举取值、流式事件名与序号语义、错误码与是否可重试。DTO 只允许无平台类型的 JSON 值（字符串 ID、有限枚举、布尔、epoch 毫秒、列表、对象）；时间遵守 `.trae/rules/contracts.md` 的时间字段契约。Java 与 TypeScript 各自实现解析与类型，但**类型由契约生成**，并**共用同一组 JSON fixture 做双向差分测试**——同一条 fixture 分别喂给 Java 与 TypeScript 的实现，逐字段比对解析结果、错误码与是否可重试；只允许存在一份 fixture 正文，任何一侧自造的样例数据都不算这条判据的证据。该原则是本规格自带的判据，不引用任何已删除的旧规格。生成产物不进入手工维护；README 只指向该正文，不复制字段表。

### 6. 后端形态（架构师决策，依据「高可维护性与扩展性」）

**采用零框架方案：JDK 17 `com.sun.net.httpserver` + 自建窄路由 + 桌面 SQLite，不引入 Spring Boot。** 第三方依赖只允许 SQLite JDBC 驱动一项，且在实现任务里先核实版本、许可证、原生库打包与启动开销，再固定。

理由：本地单用户服务的可维护性不来自框架，而来自分层方向与「业务规则只有一处」；框架在此处只会带入依赖树审计成本、启动内存与方言适配（Flyway 的历史表语义与 Android 的 `user_version` 前向迁移不同）。扩展性由端口与适配器分层保证——将来新增终端只加 `RuntimePort` 实现，不改服务。

服务约束：

1. **薄壳**：服务不得实现分叉、归档、稳定回答、未读口径、阅读位置等领域判定；这些只存在于 `:core`。服务侧出现同名规则即视为缺陷。
2. **引擎裁定**：本服务的生产数据库引擎是**桌面 SQLite**，不是 MySQL，不使用 Flyway；`.trae/rules/sql.md` 的「按实际生产引擎验证」判据必须显式记下这一裁定，否则会把桌面 SQLite 的迁移错误地送去 MySQL 验证（此坑已被记录）。
3. **网络边界**：仅绑定环回地址、随机化会话令牌、严格同源与 CORS 策略；不监听公网接口，不做远程部署。
4. **可观测**：只记录操作 ID、状态、错误类别与耗时，不记录问题、回答、推理、提示词或密钥。

### 7. 单一来源三项（防止第二份实现）

- **DDL 与 SQL 文本**：新建一个纯 JVM 模块承载建表语句、迁移步骤文本与约束名，Android 的 `:data` 与 `backend/` **都依赖它**；两侧只有驱动不同（`android.database` 与 JDBC）。配套**跨引擎差分测试**：同一份 fixture 数据在 Android SQLite 与桌面 SQLite 上跑同一语句，断言行数与关键列一致，并共用同一套约束扩展错误码判据。此项触碰 Android 生产数据层与共享代码区，必须单独成任务、叠加 `db-migration-reviewer` 与 `android-no-kotlin-guard`，并遵守防回归规则。
- **树几何**：`:tree-layout` 是唯一实现（已是 `java-library`、零 Android 引用、并被 `android/build.gradle` 的 `pureJvmModules` 守门）。阶段一到六的画屏**不重算**，直接渲染 Android 采集好的坐标快照；阶段七起服务直接依赖同一模块，浏览器侧不自算，从而几何分叉风险归零。**禁止把布局算法手抄成 TypeScript**。
- **AI 网关与提示词组装**：`:ai` 目前 21 个文件里仅 2 个引用 Android 类型（日志与 JSON 写出）。把它们换成可移植实现后把 `:ai` 升为 `java-library`，Android 与服务共用同一网关代码；同时把 `:ai` 加入 `pureJvmModules` 名单，让既有守门机械覆盖。这是「Provider 调用与 SSE 语义不分叉」的最低代价解，代价是 Android 侧要跑一轮回归。

### 8. 密钥保管

Android 侧不变（Keystore，别名不可导出，密钥不进数据库、日志或备份）。服务侧新增 `SecretPort` 抽象：实现为本地密钥库文件（JDK 原生 PKCS12 + AES-GCM）加首次启动口令派生，接口化以便将来接系统钥匙串。**必须单独写一份威胁模型正文**，明确桌面方案无硬件根、强度低于 Keystore，并声明这是 PC 端可用性的前提代价。密钥永不下发到浏览器，只由服务在代理请求时使用。

### 9. 数据通道（导出与导入）

Android 侧当前**没有任何导出实现**，这是本次净新增交付物。方案：导出为单一归档（数据库副本 + 附件目录 + 清单 JSON），清单含 schema 版本、应用版本、条目校验和与生成时间；优先用数据库自带的在线备份语义产出一致性副本，其在 minSdk 28 上的可用性作为实现任务的前置核实项，不可用时退化为受控复制并记录风险。服务侧导入即**覆盖** PC 副本，UI 必须显式说明「不回传、不合并」。归档格式一经发布即为长期契约，版本演进规则与 `db-migration-reviewer` 对齐。

### 10. 1:1 的度量方法

沿用仓库内已验证过一轮的方法（`知芽-TreeLearn-对话页1比1复刻方案-V1.0.md` 的实测方法与闭环验证小节），并把它从「单页手工对照」升级为「逐屏机械台账」：

- **仪器收编**：像素量测与 PNG 解码两支已于 2026-10-15 迁入 `.trae/scripts/dev/parity/`（`measure_screen.py`、`png_probe.py`，加同目录屏档案 `screens.json`），可按屏名运行并自带正反双向自检；DOM 闭环量测与无头渲染截图两支仍是 `artifacts/tmp/` 下的位置参数形态，由 T-2026-10-15-006 收编。仪器留在会被清理的目录里，后续所有绿色结论都不成立——这条对已迁的两支已经解除，对剩下两支仍然成立。
- **偏移校正内置**：已记录的两处量测偏移（无头渲染滚动条占位、截图裁剪原点取整）必须作为脚本常量并每次打印扣除量，否则每屏重踩。Python 侧已按此落地：三个常量从屏档案 `defaults` 读取，每次运行在头注打印本次实际扣除量，任一常量缺失、非数或被置成非正数即判 FAIL（不是默默当 0）；两支 Node 工具待 006。
- **判据分工**：结构层比对 DOM 树与 Android 视图树（元素集合、层级、顺序、文案 key）；视觉层比对真机截图与 Web 渲染截图，字体类项目按「字体族 + 字重」验收而不做逐像素判定（既有裁定理由：渲染侧字形不可能逐像素相同）；行为层用夹具回放导航、返回栈、流式事件、未读口径与阅读位置。
- **台账由构造派生**：台账条目与结论全部从捕获输出机械生成，不手写数字；条目覆盖以代码可达性为源，确保无根布局充气的自绘屏不被漏掉（成员由清单的 `self_drawn` 标记给出，不由本文点名）。
- **双环境**：Chrome 与移动 WebView/WebKit 各跑一轮；基线截图必须来自真机而非模拟器。
- **防腐烂**：台账记录来源提交号与屏幕清单哈希；Android 界面改动时该守门转红，强制 Web 侧同步——这是「等 Android 侧继续修改后仍保持 1:1」的唯一可执行形式。

### 11. 阶段、门禁与规模

| 阶段 | 内容 | 出口门禁 | 预估片数 |
|---|---|---|---|
| 0 | 权威链入库、当前态文档回写、量测仪器收编、CSS token 生成链与裸色值守门 | 守门可跑且含正反自检 | 3–4 |
| 1 | 夹具采集与台账生成器（覆盖清单全部条目） | 台账覆盖全部屏且数字可回读 | 4–5 |
| 2 | `frontend/` 工程骨架、`RuntimePort` 与夹具适配器、文案资源、依赖方向与文案守门 | 构建、lint、文案守门全绿且离线可用 | 4–5 |
| 3 | 原型工程化并逐屏校正到 Android | 清单内每一屏的结构层与视觉层达绿 | 10–14 |
| 4 | 自绘屏与难移植组件（清单标记的全部自绘屏、表格、吸底跟随、边缘渐隐、跑马灯、浮层定位） | 结构层达绿并留交互记录 | 6–8 |
| 5 | 独立技术方案（画布、热力图、月历、富文本与公式、划词浮条） | 与快照逐帧对齐，公式与字体入偏差台账 | 6–9 |
| 6 | 行为接线（导航、返回栈、五态回放、未读、阅读位置） | 行为层全清单 | 4–6 |
| 7 | `:ai` 提纯、schema 与 SQL 单一来源、导出导入、`backend/`、桌面密钥、契约差分 | 服务真实启动、跨引擎差分通过、双环境跑通 | 10–15 |
| 8 | PC 外壳适配 | 改动面不得触碰组件层（机械检查） | 4–6 |
| 9 | 收口回归与文档 | 无未登记的红色条目 | 3–4 |

合计 54–79 片。每片按 `.trae/rules/harness-tasks.md` 的规模目标（不超两百生产行，超五百需用户逐项批准规模例外并如实记录批准时点）；执行采用 Harness 任务流程（用户 2026-10-15 拍板），任务统一落在 `.trae/tasks/`。

### 12. 风险登记

| 风险 | 缓解 |
|---|---|
| 界面与 Android 再度分叉（旧线病因） | 台账先行 + 屏幕清单哈希守门 + 原型工程化后冻结 |
| schema/SQL 单一来源触碰 Android 数据层 | 单独成片、双守门、跨引擎差分测试、防回归影响面先行 |
| 量测工具位于会被清理的目录 | 阶段 0 第一件事即收编入库 |
| 桌面密钥强度低于 Keystore | 环回绑定 + 会话令牌 + 独立威胁模型正文 |
| PC 副本与手机数据分叉 | 单向搬迁、导入即覆盖、UI 显式声明不回传 |
| 新增依赖未核实 | 实现任务先核实许可证/版本/字节码再固定；Java-only 判定以构件字节码为准 |
| 后端 Gradle 引用 Android 目录下模块的写法未验证 | 阶段 7 前置核实项，失败则改为把共享模块提升为顶层独立模块并记录影响 |

- 来源：`android/app/src/main/**` 屏幕与资源盘点、`android/build.gradle` 的模块方向与纯 JVM 守门、`android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java` 与 `:data`/`:ai` 的 Android 依赖计数、`Docs/04-ux-design/design-system/tokens.json` 及其权威原型 `:root` 区块、`Docs/04-ux-design/知芽-TreeLearn-对话页1比1复刻方案-V1.0.md` 的实测方法与闭环判据、`.trae/rules/frontend.md`、`.trae/rules/backend.md`、`.trae/rules/sql.md`、`.trae/rules/contracts.md`、`.trae/rules/harness-tasks.md`。

## Doubts（疑虑点）

- 疑虑 1（已裁定 2026-10-15 并已于 `T-2026-10-15-002` 回写完成）：当前态权威文档曾主张「不建设 Spring Boot、React」与「无前后端目录」，与本规格直接冲突。冲突点第一轮盘点 9 个文件，GREEN 收尾的第二轮反向搜索又补入第 10 个：根 `README.md` 与 `README_EN.md`、`.trae/context/project-brief.md`、`.trae/ROADMAP.md`、`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md`、`Docs/05-technical-design/TreeLearn_Harness全量开发交付提示词.md`、`.trae/rules/shared-state.md`、`.trae/rules/observability.md`、`.trae/skills/pm-discovery/SKILL.md`、`Docs/spec/SPEC-2026-09-23-providers-and-models.md`（把 project-brief 的旧约束当理由引用）。落法：每处改写为「不做**远程**服务端」并指向本规格，历史技术方案保留其原叙事且显式标注「与规格冲突处以本规格为准」，不改写历史；复活旧断言、盘点清单塌陷或删除权威指针由 `.trae/scripts/dev/check_stack_facts.py` 三条判据长期守住（该脚本含条目下限、行号级命中输出与 22 例正反双向自检）。PRD 基准 `Docs/01-demand/结构化需求基准说明书_知芽（TreeLearn）_V3.3增量.md:26` 与 `..._V3.0.md:70` 仍保留「不做后端/前端」类主张，属冻结基线、不随本规格回写，改它要走基线变更流程；本条把那两处显式登记为与本规格的已知差异，不留静默冲突。原「维持旧边界并否决本规格」的走法已由用户裁定否决。
- 疑虑 2：Android 侧新增导出能力会触碰生产数据层与「本地优先、首版不建同步」的既有裁定，且归档格式是长期契约（冲突对象：`Docs/05-technical-design/知芽（TreeLearn）Android本地优先技术架构方案_V1.0.md` 的未来扩展表述与 `.trae/rules/sql.md`；两种走法：把导出限定为一次性单向搬迁并冻结格式版本，或改为服务侧只读接入而放弃 PC 可写；谁拍板：用户）。
- 疑虑 3（已裁定 2026-10-15，用户批准「为单一来源改动 Android 生产代码」）：`:ai` 升为 `java-library` 并加入 `pureJvmModules`、DDL 与 SQL 文本外提到共享 JVM 模块，两者都要改 Android 生产代码，回归面覆盖生成链路与数据层。落法：这两片各自独立成任务、串行执行、叠加 `db-migration-reviewer` 与 `android-no-kotlin-guard`，并按 `.trae/rules/regression.md` 先写影响面；服务侧出现第二份网关或第二份建表语句即视为缺陷（旧线病因）。原「保留 Android 现状、服务侧另写一份」的走法已否决。
- 疑虑 4（已裁定 2026-10-15，用户选择「删除」而非改写口径）：`.trae/rules/frontend.md` 的 user-copy guard 不再要求任何第二语言齐备，判定口径只存在于该 guard 一处；`.trae/agents/frontend-dev.md` 原先复述「`zh-CN`/`en-US` 双键」的那条已改为指向该规则，不做复述。同一段里「该命令也是 Android 构建入口的一部分」描述的是已删除旧线的接线方式，已按新线事实改写为前端 dev/lint/build 入口、不进 Android 构建。保留不变的判据：文案必须按 key 从资源表读取、生产代码不得硬编码用户可见文案、`i18n-ignore` 例外机制与 decrement-only 基线。
- 疑虑 5（已裁定 2026-10-15，用户选择「全部删除」）：`Docs/spec/SPEC-2026-10-01-progressive-web-ui.md` 已整份删除，APK 内 WebView 承载路线在仓库内不再存在任何现存规格文本，读者不会把它误读成当前方向。配对意图 `Docs/intent/INTENT-2026-10-01-progressive-web-ui.md` 按 `Docs/intent/README.md` 的约定保留原判定不改写历史，并在页首加「归档时序说明（阅读前必读）」指向本规格与被删事实。原建议的「标 superseded 后保留」方案已按用户裁定作废；fixture 双测与桥安全原则已内联进 §5，全文不留对该文件的悬空引用。
- 疑虑 6：桌面 SQLite 作为后端生产引擎与 `backend/` 规则正文里默认的 MySQL/Flyway 假设不一致，而守门要求执行证据来自真实生产引擎（冲突对象：`.trae/rules/sql.md`、`.trae/rules/backend.md`；两种走法：在规则正文里为本地服务显式裁定桌面 SQLite，或改用 MySQL——后者与本地优先和单机安装冲突；谁拍板：架构师提出、用户确认，确认后只在一处正文落判据）。
- 疑虑 7：量测台账以真机截图为基线，而仓库内既有截图覆盖不全（主页、学习库、设置、对话、抽屉有，画布、导航、统计、供应商、KEY 房子页缺），且原型渲染图与真机截图存在已记录的信息架构差异（冲突对象：`artifacts/` 既有截图与高保真原型 v1.1 的底部标签叙事；两种走法：阶段 1 重新完整采集一套真机基线并以此为准，或复用既有截图快速起步；谁拍板：架构师建议前者，代价是首轮采集耗时）。**架构师已按前者决定，2026-10-15 写入 §10 与阶段 1 门禁。**
- 疑虑 8（新增，阶段 3 开工前必须裁定）：`.trae/rules/frontend.md` 的 frontend-dev 职责写着「平板横屏优先，不假设手机竖屏」，而本规格的 1:1 校正基线是**手机档**——Android 侧真机基线截图取自手机，且平板差异只存在于少量 `layout-sw600dp`/`values-sw600dp` 文件。两种走法：把该条解释为设计评审的注意力顺序、1:1 校正以手机档为唯一基线并在阶段 3 之后另出平板档差异片（建议，代价是「平板优先」的字面读感需要读者接受解释），或要求阶段 3 以平板档为 1:1 基线（代价是平板基线截图当前不存在，必须先补一轮采集与采集脚本适配）。谁拍板：用户。不裁定的后果是同一屏可能出现两个互斥基线，台账绿色结论不可比。

## 修订记录（追加，不回改上文原判定）

- 2026-10-16｜范围条目数改为指向生成的屏幕清单，不再由本文书写。改动处为「范围」「数量与呈现方式」「§10 台账由构造派生」「§11 阶段 1/3/4 行」与「界面方案·来源」，理由：盘点仪器建立后实测 `14 屏 / 16 个浮层 / 5 屏自绘`，与本文 2026-10-15 写的 `16 屏 / 6 浮层 / 4 自绘屏` 不符；用户同日裁定「以实测清单为准」。2026-10-15 那次拍板的原文不改写——它是当时的真实状态，只在「范围」条里就地标注它是仪器建立前的人写估计。
- 2026-10-16｜两处差额的成因**不同且各只有一半已解释**，本节不做统一归因：浮层 6→16 是**修正**（守门的模式词表靠手抄，漏了 `new PopupMenu(` 2 处与 `new AlertDialog.Builder(` 4 处，实测计数由 `T-2026-10-15-screen-inventory-parity-ledger-005.md` 的 GREEN 证据托底）；屏 16→14 **未解释**，规格里没有屏清单可核对，因此不写成结论、只在「范围」条登记为待阶段 1 逐屏对照的事项。反向搜索命中并同步改写的复述位置：本文 5 处、`Docs/intent/INTENT-2026-10-15-web-h5-android-parity.md` 与父需求 `T-2026-10-15-web-h5-android-parity-requirement-001.md`（这两处为按日归档的采集记录，处理方式见该两条的下一行）。
- 2026-10-16｜intent 与父需求**不改写历史正文**，只在页首加指向本节与清单的时效说明：按 AGENTS.md 归档条目的规则，「原始发现与处置结果必须分时可辨」，用现在的实测值去覆盖当时写下的估计，读者会把治理时序读错。
