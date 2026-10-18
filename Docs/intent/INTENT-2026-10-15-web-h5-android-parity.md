# Intent: Web（H5）按 Android 现状 1:1 还原，PC 端由本地 JVM 服务承载

id: INTENT-2026-10-15-web-h5-android-parity
title: Web（H5）按 Android 现状 1:1 还原，PC 端由本地 JVM 服务承载
date: 2026-10-15
author: 用户（拍板）与技术架构师（记录）
origin: 用户确认的架构方向
status: accepted

> **时效说明（2026-10-16 追加，阅读前必读）**
> 本文件是 2026-10-15 的采集记录，正文**保持原样不改写**——用后来的实测值覆盖当时写下的估计，读者会把治理时序读错。
> 但其中「16 屏 + 6 浮层」是盘点仪器建立**之前**的人写估计；仪器建立后的实测读数取代了它——读数不抄进本文件（抄进来就会过期），现场回算用 `python -X utf8 .codex/scripts/dev/gen_screen_inventory.py --no-write | grep "^MEASURED"`，用户 2026-10-16 裁定**以实测清单为准**。因此本文件中每一处复述该组数字的地方（现场回读：`grep -nE "16 ?屏|16 ?个屏幕|6 ?浮层|4 ?个.*自绘" Docs/intent/INTENT-2026-10-15-web-h5-android-parity.md`）都按此口径读取，不要当成当前范围。
> 当前范围权威是 `Docs/04-ux-design/parity/screen-inventory.json` 的 `counts`（由 `python .codex/scripts/dev/gen_screen_inventory.py` 生成，Android 代码一改即重算）；两处差额各自的成因与逐处回写见 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` 末节「修订记录（追加，不回改上文原判定）」，屏一侧的差额**尚未逐条对应**，该文件与 `T-2026-10-15-screen-inventory-parity-ledger-005.md` 的 Gaps 都如实记为未决，不写成已解释。

## Problem（问题与背景）

- 问题/背景：**观察到的现象**——Web 线曾被完整建立又整体移除（`35bb6ce` 一次删除 842 个文件、92912 行），旧线形态是 `web/`（React 前端 + 本地后端 + 桌面 SQLite）；用户对该线的判定是「交付了一套完全和 Android 不一致的、类似重新开发了一套，重新调整回我想要的那样太麻烦，不如重开」。同时 Android 侧界面是当前唯一可用形态：单 Activity 承载 16 屏 + 6 浮层，声明式 XML 加自绘 View，无 Fragment、无 Navigation 组件、无 RecyclerView。**推测的原因**（与现象分开）——同一视觉规则与同一业务规则同时存在多份实现（高保真原型、旧 web、Android 各一份），且没有任何机械判据约束「web 必须等于 Android」，于是逐片漂移，直到无法回推。
- 用户/场景：单人开发者，手机为主终端；希望在不重新设计界面的前提下，让同一套页面也能在 PC 浏览器上用。

## Users and scenarios（用户与场景）

- 当前用户继续在 Android APK 上学习，Android 的数据与密钥边界不变。
- 同一用户在 PC 上打开浏览器，看到与手机上逐屏同形的界面，用同一份数据副本继续学习（副本，不回传）。
- 未迁移或回退场景不存在「两套界面」的问题：本意图下 Android 原生界面保持权威，不被替换。

## Expected outcome（预期结果）

- 预期结果：`frontend/` 里存在一套可构建的 Web 界面，**逐屏与 Android 当前实现结构、视觉、行为等价**，等价与否由机械台账（含截图量测证据）判定，而不是由人眼看判定；同一套组件在 PC 上只更换布局外壳即可用，不出现第二次界面开发；`backend/` 本地服务为浏览器端供数据与 AI 代理，且不持有业务规则。
- 可观察的变化：新增一条当前态事实「仓库存在 `frontend/` 与 `backend/`」，且旧线代码不作为任何新文件的来源。

## Core flow（核心流程）

- 核心流程（含中断、撤销、重试）：用户在 Android 导出当前数据副本 → 在 PC 启动本地服务并导入该副本 → 浏览器访问仅监听本机的服务 → 界面按手机基线逐屏渲染，与 Android 截图判据比对；用户在 PC 上的追问由服务代理 Provider 并以流式事件回传。中断（服务未启动、流断开、页面重建）时：先查询服务侧权威状态再决定继续或重试，不盲目重发非幂等命令。撤销：导出不修改 Android 数据；导入即覆盖 PC 副本，明确「不回传、不合并」。
- 输入/输出：输入是一份导出归档（数据库副本 + 附件 + 清单）；输出是浏览器内的同形界面与流式回答。
- 状态与异常：服务不可用、导入版本不兼容、Provider 无 Key、流中断、生成终态等都必须有可区分的用户可见状态；任何一项不得静默降级成另一状态。

## Constraints and non-goals（约束与非目标）

- 约束：Android 与后端保持 Java-only（不引入 Kotlin 源码或 Kotlin Gradle 插件）；继续本地优先，不建云端服务、账号、同步或远程遥测；**Android 是数据与界面事实的唯一权威**；设计数值只经 token 链流转（原型 `:root` → `tokens.json` → `colors.xml` → 生成的 Web CSS 变量），禁止在 Web 侧手抄设计值；时间统一 epoch 毫秒；触契约走 `contract-guardian`、触 schema/migration 走 `db-migration-reviewer`、触 Android 走 Java-only 守门。
- 权衡偏好：优先「提取既有高保真原型并逐屏校正到 Android 现状」，而不是重新画界面；优先「服务薄壳」——领域判定留在 `:core`、树几何留在 `:tree-layout`、DDL/SQL 文本单一来源，服务只做 HTTP/持久化/代理；优先可维护性与扩展性，宁可前期多花一片做单一来源与契约生成，也不留第二份实现。
- 非目标（non-goals）：不做双活与冲突合并；不做 iOS 或其它平台包；不做远程部署或多用户；界面语言只做简中（前端守门的「双语齐备」口径已于 2026-10-15 由用户裁定删除，见决策记录）；不删除或替换 Android 原生界面；不复用被移除的旧线代码。

## Open questions（开放问题）

- 已确认事实：用户于 2026-10-15 拍板四项——阶段一以快照夹具驱动界面还原；PC 端数据与密钥宿主采用本地 JVM 服务；首批范围为全部 16 屏与 6 个浮层；执行采用 Harness 任务流程。旧线废弃原因已确认为「界面与 Android 不一致、等于重新开发」。
- 假设：服务的薄壳形态（不持有业务规则）足以支撑全部界面所需读写；快照夹具能覆盖逐屏判据。两者都要在阶段一与阶段七用证据证伪或证实。
- 未知项：`:ai` 模块提纯为纯 JVM 库后 Android 侧的回归范围；SQLite 在线备份语法在 minSdk 28 设备上的可用性；后端 Gradle 构建引用 Android 目录下纯 JVM 模块的具体写法；高保真原型仍经外部 CDN 加载脚本与字体，离线可用性与安全边界如何处置。

## Convergence verdict（收敛判定）

### 有价值吗

结论：是。
证据：旧线已投入并整体废弃，说明「不做界面权威约束」的代价已被真实支付过一次；Android 界面资产可机械盘点（16 屏 + 6 浮层 + 46 份布局 + 80 行尺寸阶），且高保真原型本身已是可运行的 React 实现（`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 内含 `:root` token 与 `.screen`/`.appbar`/`.icon-btn` 等样式），还原成本低于重新设计。

### 值得做吗

结论：是，按「界面长坡先行、数据宿主后置到阶段七」的顺序做。
证据：界面还原（阶段一到六）不依赖数据宿主决策即可推进并被验证，把最昂贵、最容易反复的宿主问题推到已有真实证据时再定；这正对应旧线的病因——它在界面与后端同时铺开、两者互相迁就。

### 可以做吗

结论：是，但有三个必须先做的验证项（`:ai` 提纯、schema 单一来源、夹具采集），未通过前不得进入阶段七的服务实现。
证据：`:core`（29 文件）与 `:tree-layout`（12 文件）已是 `java-library` 且零 `android.*` 引用，并被 `android/build.gradle` 的 `pureJvmModules` 机械守门；`:ai` 21 个文件里仅 2 个引用 Android 类型；桌面 SQLite 复现同一份 DDL 已有旁证（`artifacts/tmp/desktop_sqlite_v5_probe.py`）。

## Decision record（决策记录）

- 决策权归属：架构形态由技术架构师提出并附代价，用户拍板；界面顺序与切片由工程实现者在本意图边界内细化。
- 决策人：用户
- 决策日期：2026-10-15
- 终态：accepted
- 否决理由（status 为 rejected 时必填）：不适用。
- 验收标准与证据：`frontend/` 全部 16 屏与 6 浮层的结构台账、视觉量测台账、行为回放台账逐项达绿，或已登记为被接受的偏差；PC 端由本地服务真实启动并跑通一次导入；Android 侧数据、密钥与 Java-only 边界经守门证明未变；schema/SQL 与契约的单一来源由跨引擎差分测试与共享夹具证明。
- 风险与后续范围：PC 副本与手机数据的分叉（已用「不回传」限定）；桌面密钥托管强度低于 Android Keystore（需威胁模型正文）；量测工具当前位于 `artifacts/tmp/`（会被清理）；原型的 CDN 依赖。2026-10-15 追加三项裁定：① 允许为「单一来源」改动 Android 生产代码（`:ai` 提纯 + DDL/SQL 文本外提，代价是生成链路与数据层各跑一轮回归，且两片触碰共享代码区须串行加双守门）；② `Docs/spec/SPEC-2026-10-01-progressive-web-ui.md` 整份删除，WebView 承载路线在仓库内不留规格文本；③ 删除前端「中英文双语齐备」守门口径，只保留「文案按 key 从资源表读取、生产代码不得硬编码」，本期不做英文。后续范围为阶段七之后的服务实现与阶段八的 PC 外壳适配。

## Next step（下一步）

- accepted：移交 requirement-decomposer 建立父 requirement，并按下列移交清单拆实现片。本意图限定覆盖 `Docs/intent/INTENT-2026-10-01-progressive-web-ui.md` 中「不交付浏览器正式客户端」与「不新增 Spring Boot/后端」两条非目标；该意图对应的 WebView 承载规格 `Docs/spec/SPEC-2026-10-01-progressive-web-ui.md` 已于 2026-10-15 由用户裁定整份删除，WebView 路线不启用。技术规格见 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md`（status 已置 accepted）。

### 移交清单

- 切片 1：权威链与文档奠基——本意图与规格入库、回写全部「不存在前端/后端目录」的当前态主张、收编三支 1:1 量测工具离开发物目录、建立 Web CSS token 生成链与裸色值守门。
- 切片 2：基线采集仪器——用仪器测试（不进生产代码）为 16 屏与 6 浮层成对产出「真机截图 + 状态与几何快照」，并生成结构台账；台账必须覆盖 4 个没有布局文件的自绘屏。
- 切片 3：前端工程骨架——`frontend/` 可构建工程、运行时端口与 Mock 适配器、简中文案资源表、依赖方向守门与用户文案守门。
- 切片 4：原型工程化与逐屏校正——把原型各屏提取进真实工程，按台账校正与 Android 现状的差异（含底部标签数量这类已确认差异）。
- 切片 5：独立技术方案屏——树画布按快照渲染、热力图与月历、正文富文本与公式、划词浮条。
- 切片 6：数据与宿主——`:ai` 提纯、schema 与 SQL 文本单一来源、导出/导入归档、`backend/` 本地服务与桌面密钥托管、契约生成与跨语言差分。
- 依赖顺序：1 → 2 → 3 → 4 → 5；6 只能在 4 的台账达绿之后开始，阶段八（PC 外壳）只能在 5 之后开始。
- 不可并行：切片 6 内的「schema 与 SQL 文本单一来源」触碰共享代码区，必须单独成任务、不与任何其它任务并行，且需 `db-migration-reviewer` 与 `android-no-kotlin-guard` 双门；契约正文（切片 3 与 6 的接口定义）未定稿前，前端适配器与服务实现不得并行开工。

<!-- FIELDS: id | title | date | author | origin | status | Problem（问题与背景） | Users and scenarios（用户与场景） | Expected outcome（预期结果） | Core flow（核心流程） | Constraints and non-goals（约束与非目标） | Open questions（开放问题） | Convergence verdict（收敛判定） | Decision record（决策记录） | Next step（下一步） -->
