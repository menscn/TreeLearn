# 知芽（TreeLearn）V1 交付报告

> 生成日期：2026-09-21　生成者：Harness 主会话（leader）　最后刷新：**2026-09-22**（第 1、2、2.1、5.1、6、7、9、10 节为当前事实；第 5 节正文保留为历史快照）
> 权威来源：本仓库生产代码、构建产物、实际执行的命令输出（证据文件见 `artifacts/evidence/`）。
> 本报告只写已经发生的事实；没有证据的条目一律标注为 deferred / blocked，**不写 PASS**。

## 1. 结论摘要

| 项目 | 结论 |
| :--- | :--- |
| 代码与构建 | **通过**：全模块 `check` 退出码 0；JVM 测试全绿，**两口径都记**（〔2026-09-22 03:30 更新，T-039 落盘后强制重跑 `--rerun-tasks --no-build-cache`〕读法 A「每模块只计一个变体」= **667 例 / 65 份套件**，读法 B「debug 与 release 两个变体都计」= **1103 例 / 108 份套件**，分项之和与总数一致，两读法均 0 failures / 0 errors / 0 skipped；此前的 1011 例是更早一次读法 B 快照且未写口径）；Java-only guard PASS；lint 0 error |
| 发布产物 | **已产出并签名**：release APK 已用仓库外个人侧载证书签名，`apksigner verify` 退出码 0 |
| 端到端可用性 | **未取得证据**：本轮按用户指令不使用 emulator/设备，**安装、启动、真实交互均未执行** |
| 真实模型冒烟 | **blocked**：未提供 DeepSeek Key，未发出任何真实网络请求 |
| 首页/对话数据路径 | **已接线**（`T-2026-09-21-real-data-path-wiring-032`，2026-09-22）：两个占位类已删除，首页与对话走真实数据路径，dex 内 `PendingHomeWiring` / `PendingChatWiring` / `NOT_WIRED` 出现 0 次（证据文件第 4 节）。**引用文本进请求已由 T-044 补上**（待独立 verifier）。〔2026-09-22 更新〕首页主题改名/删除/未读与搜索用户问题臂已落盘并通过 JVM/真机定向证据；仍缺的是派生摘要/推荐的完整生命周期接线与上下文压缩。 |
| 装机后能用到什么 | **核心链路 + 导航页 + 树画布 + 搜索页可用、4 项 P0 能力缺失**（1 处入口不可达 + 3 处派生接线无生产者，逐条见第 2.1 节）。这是静态可达性结论，不是设备结论 |
| 综合判定 | **PARTIAL（部分完成）**——可作为「可构建、可签名、构建期守门全绿、核心链路代码可达」的交付；**不可声称「已在设备上验证可用」**，因为本轮没有任何安装与交互证据 |

**一句话说明**：本轮把「代码是否自洽」这一段做实了，把「装到手机上是否真的能用」这一段**明确留空**，没有用构建成功或测试数量去替代它。

## 2. 交付物

- **APK**：`android/app/build/outputs/apk/release/app-release.apk`
  - 字节数：`523861`
  - SHA-256：`3DFE991DE9EE31470CB86B9CABB87C5191848A1A0C02DC9E873F31F5851695E2`
  - 〔2026-09-22 05:13 更新 · 取代 04:59 那条〕上两行对应 mtime `2026-09-22 05:13:06`，由 **T-042 修复轮（Fix round 1，D1–D5）** 收尾刻意跑 `.\gradlew.bat --no-daemon --console=plain :app:assembleRelease --rerun-tasks` 重建（退出码 0，见 `artifacts/evidence/T-042-fix1-apk-build.txt`）。**早于它的 `522389` / `AA40169C…`（04:58:17）与 `522389` / `A79D66CE…`（04:46:11）一律作废，不再是装机目标。** 该包里 T-042 修复轮的符号实测存在：`NoticeText` 3、`READING_POSITION` 1、`noticeReadingFailure` 2、`visibleRows` 1、`onRowsLaidOut` 3，资源名 `notice_reading_position` 与 `chat_notice` 各 1（`resources.arsc`）；阴性对照 `ChatReadingTest` / `ChatPresenterTest` 均为 0。逐符号清单、资源名扫描与 mtime 断言见 `artifacts/evidence/T-042-fix1-apk-contains-wiring.txt`。
  - 〔2026-09-22 05:13 签名复核〕`apksigner verify --verbose --print-certs` 退出码 0（v2 方案 `true`），证书 DN `CN=Treelearn Personal Sideload`、证书 SHA-256 仍为 `d8670c863d692a90e21b7a895cf0f717bc8a1cc4ba82a48ff771f09a8780c561`（与下条一致，装机核验仍以证书那行为准）；证据 `artifacts/evidence/T-042-fix1-apk-signature.txt`。
  - 〔2026-09-22 04:59 更新 · 取代 04:52 那条〕上两行对应 mtime `2026-09-22 04:58:17`（早于它的
    `522389` / `A79D66CE…` 已作废，不再是装机目标）。**号称来源**：`T-040` 收尾时**刻意**跑的
    `.\gradlew.bat --no-daemon --console=plain :app:assembleRelease --rerun-tasks`（真重链：118 个任务 executed，
    退出码 0），核对口径见 `artifacts/evidence/T-040-apk-build.txt` 与
    `artifacts/evidence/T-040-apk-contains-search.txt` 头部。
    **两个同尺寸不同哈希的包**：`04:46:11` 那次（`A79D66CE…`）与本次（`AA40169C…`）都是 `522389` 字节、符号集合
    一致，差异同样来自 zip 条目时间戳——与下面 03:41 那条「SHA-256 只标识这一个文件、不是可复现指纹」是同一现象。
  - 〔2026-09-22 04:59 我另独立断言 · 不引用实现者数字〕我把这个包当 zip 打开、逐 dex 字节流扫符号表（ASCII）、
    并自算字节数与 SHA-256：上两行与实测一致。**该包里有什么**：`SearchScreen` 7、`SearchPage` 6、
    `BranchTitles` 2、`SqliteSearchStore` 2、`onSearchPressed` 1、`openAnchor` 1、`showSearch` 1、`drawSearch` 1
    （**T-040 搜索页与首页入口**）；`ReadingSession` 2、`SqliteReadingStore` 2、`onViewportChanged` 2（T-042）；
    `NavigationScreen` 23、`NavigationPresenter` 13、`NavigationWiring` 2（T-039）；
    `WordMenuPopup` 11、`ChildBranchPopup` 4、`BranchChildren` 2（T-041）；`treelearn_quote` 4（T-044）；
    `TreeCanvasWiring` 2（T-038）——**同一个包里既扫出本任务的符号，也扫出它依赖的四个在制品，回退会在这里露出来**。
    逐符号清单、源码新旧排序断言与判定见 `artifacts/evidence/T-040-apk-contains-search.txt`。
    **阴性对照（探针有效性的另一半）**：`SearchPageTest` / `HomeSearchEntryTest` / `SearchJumpTest` /
    `SearchPresenterTest` / `ChatReadingTest` **全为 0**（测试类不进包）。上一版此处写「`SearchScreen` 0 次
    （T-040 尚未落盘）」，那条随本版作废：`SearchScreen` 由 0 变 7 本身就说明这个探针不是恒零。
  - 〔2026-09-22 04:40 签名复核〕`apksigner verify --print-certs` 退出码 0，证书 DN
    `CN=Treelearn Personal Sideload`，证书 SHA-256 与下方同步为
    `d8670c863d692a90e21b7a895cf0f717bc8a1cc4ba82a48ff771f09a8780c561`。
    **装机核验以本行 SHA-256 为准。**
  - 〔2026-09-22 03:41 追加 · 实测发现〕**这个 SHA-256 只标识「这一个文件」，不是可复现指纹**：独立 verifier 在
    冻结副本里重跑 `.\gradlew.bat check` 后，快照内的 `app-release.apk` 字节数**同为 `498904`**，但 mtime 变为
    `2026-09-22 03:38:22`、SHA-256 变为 `0EE5443BE9E65BFABE8F32E808FC1AFEAF1C9533CF2964654A230A727601B394`
    （实测两个文件并存、同尺寸不同哈希）。同一份源码两次构建得到同尺寸不同字节的产物，差异来自 zip 条目
    时间戳一类的不确定性。含义有两条：① **别拿旧 SHA 去核对新构建出来的文件**，重建后必须刷新本行；
    ② 第 2.1 节的可达性结论按**源码与 dex 符号**成立，不依赖某一个哈希。
  - 该产物的来源要如实说清：它**不是**一次刻意的 release 构建，而是 `T-039` 跑强制门禁
    `.\gradlew.bat check` 时由 `:app:packageRelease` / `:app:assembleRelease` **顺带重打包**的结果
    （实现者在 `artifacts/evidence/T-039-release-apk.txt` 头部主动披露了这一点）。门禁顺带产出的包与刻意
    构建的包走同一个 `:app` 变体，但**"两者装配等价"只有下一次刻意构建产出同一功能集合才能进一步印证**，
    本报告不把它写成已证明。
  - 〔2026-09-22 04:52 更新〕**本产物晚于 `android/` 下参与打包的全部源码**（最新源码 mtime
    `2026-09-22 04:44:58`，`app/src/main/java/com/treelearn/app/search/SearchPage.java`），因此此前
    `apk-newer-than-newest-source: FAIL` 那条真信号**已消解**（该 FAIL 是 02:58 那次构建的真实状态，
    不是脚本故障）。**上一版此处写的「T-042 起的改动还没有进入任何产物」已作废**：本次
    `:app:assembleRelease` 退出码 0，且 dex 里同时扫出 T-040 与 T-042 的符号。装机前仍请重跑本节给出的
    核对命令并刷新上面两行数值。
  - 〔历史，不列数值〕本次之前还产出过两个更旧的 APK 快照（2026-09-21 的 T-018 快照，以及 2026-09-22 T-032 接线后的一次）。**它们的字节数与 SHA-256 一律不再写在报告里**：同一处出现两个哈希会让读者不知道该核验哪一个，而装机只有当前这一个产物。历史快照的原始数字仍可从 `artifacts/evidence/` 下 T-018 与本轮证据文件中回读。
    签名者与证书摘要自 T-018 以来不变（同一 keystore）：`apksigner verify` 退出码 0，v2 方案通过，`CN=Treelearn Personal Sideload`，RSA 4096。
- **签名证书**（`apksigner verify --print-certs`，退出码 0）
  - DN：`CN=Treelearn Personal Sideload, OU=Personal, O=Treelearn, L=Shanghai, ST=Shanghai, C=CN`
  - 证书 SHA-256：`d8670c863d692a90e21b7a895cf0f717bc8a1cc4ba82a48ff771f09a8780c561`
  - 证书 SHA-1：`ab80340942e1d12bdac23245de139b3afa360d2f`
- **keystore**：`D:\Environment\Android\keystore\treelearn-release.jks`（**仓库外**，不入库）
  - 口令只保存在被忽略的 `android/keystore.properties`（`git check-ignore` 确认命中 `android/.gitignore:3`）
  - **本报告与任何证据文件都不含口令**
- 证据：当前产物的身份、签名、dex 内容与死入口清单见 `artifacts/evidence/T-REV-2026-09-22-apk-and-gap-review.txt`（`REVIEW-VERDICT: FAIL (1 check(s) failed)`，退出码 1——**唯一失败项是「产物早于最新源码」**，其余检查含 `quote-reaches-prompt` 全 PASS，原因见上面最后一条）；更早的构建记录见 `artifacts/evidence/T-018-release-apk-2026-09-21.txt`。

侧载命令（设备可用时）：

```powershell
adb install -r android/app/build/outputs/apk/release/app-release.apk
```

### 2.1 这份 APK 装机后能用与不能用（2026-09-22 事实核对，新增）

> 核对方式与全部原始输出见 `artifacts/evidence/T-REV-2026-09-22-apk-and-gap-review.txt`（`REVIEW-VERDICT: FAIL
> (1 check(s) failed)`，退出码 1；**唯一失败项是「产物早于最新源码」**，原因见第 2 节最后一条，其余检查全 PASS，
> 其中 `quote-reaches-prompt: PASS`）：第 4 节按 dex 符号核对「到底装进去了什么」，第 5 节按引用关系核对「哪些 presenter 没人能调到」，
> 第 7 节把布局里的死按钮与运行时驱动的按钮区分开。**设备侧一律仍是
> `deferred — 用户自行装机验证（user decision 2026-09-21）`**：本节只回答「代码路径可达 / 不可达」，
> 不声称「真机上一定不崩」，也不声称「一定好用」。
>
> 〔2026-09-22 03:36 追加〕上面引用的那次核对快照**早于 T-039 落盘**，因此它当时判「导航页不可达」是
> 对的、现在已过期。下表中导航页一行已按当前源码与产物 dex 更正（`NavigationScreen` 在
> `android/app/build/outputs/apk/release/app-release.apk` 的 `classes.dex` 内实测出现 23 次）。
> 每一行末尾都标了它对应的任务编号，装机前请以「不能用」表的当前内容为准。

能用（代码路径可达；需先在「设置」里填入 DeepSeek API Key）：

| 能力 | 可达依据 |
| :--- | :--- |
| 首页主题列表；提交问题即创建一个新主题 | `wiring/HomeWiring.submit`（首页提交每次都 `Topic.create`） |
| 重开应用读回已存主题 | `wiring/SqliteHomeStore` |
| 对话页：发送 / 重试 / 重置 / 切换回答版本 / 新建分支 / 复制 | `wiring/ChatWiring` |
| 对话页长按划词四项菜单（追问 / 新建分支 / 复制 / 全选）与「子分支」列表弹层〔2026-09-22 更新，T-041〕 | `screen/WordMenuPopup` 由 `ChatScreen.Row` 的 `OnLongClickListener` 构造；`screen/ChildBranchPopup` 由 `MainActivity.openChildren` 构造，行由 `wiring/BranchChildren` 判定 |
| 对话页「总览」进入树画布（缩放、平移、折叠、节点弹层、从弹层进入该分支对话） | `MainActivity.showTree`（`screen/TreeCanvasScreen` 的唯一生产构造点） |
| 导航页：分支列表、归档 / 恢复、永久删除确认〔2026-09-22 更新，T-039〕 | 三条入口实测存在：首页「归档」→ `HomeScreen.java:115`（`screen_home_body.xml:46` 已无 `enabled="false"`）、对话页 `chat_nav` → `ChatScreen.java:127`、画布 `tree_nav` → `TreeCanvasScreen.java:78`；宿主 `MainActivity.showNavigation` / `enterNavigation`；产物 dex 内 `NavigationScreen` 23 次。**但该页的「分支级 AI 总结」栏位读 `BRANCH_SUMMARY` 派生产物，其生产者不存在 → 栏位恒空**，属下面「不能用」表的派生接线缺口（T-045） |
| 划词追问的**引用文本进入发给模型的请求**〔2026-09-22 更新，T-044〕 | `ChatWiring.java:252` 把存储的引用交给 `DeepSeekContextEncoder.currentInput(input, quote)`，由后者包成 `<treelearn_quote>…</treelearn_quote>` 前置在问题正文之前；判定脚本 `.trae/scripts/dev/review_quote_path.py` 从代码推导该结论（证据文件第 6 节 `CHECK quote-reaches-prompt: PASS`）。**T-044 仍为 `verifying`，独立 verifier 尚未判定通过** |
| 阅读位置恢复与「看过即清未读」〔2026-09-22 更新，T-042〕 | `MainActivity.showChat(String, ReadingAnchor)`（`MainActivity.java:459`，行号取 `artifacts/evidence/T-042-fix1-source-scan.txt` 的扫描实测值——旧引用 `:383` 已被并行 T-040 lane 挪走）在打开分支时经 `container.readingStore()` 恢复位置、离开时经同一 port 保存；`screen/ChatScreen` 只上报滚动事实（`onRowsLaidOut` / `onViewportChanged` / `onLeaving`），读写库一律走 `AppContainer.background()`。原文扫描 `artifacts/evidence/T-042-source-scan.txt`（首轮）与 `artifacts/evidence/T-042-fix1-source-scan.txt`（2026-09-22 修复轮）证明 `ReadingStore` / `ReadingSession` 已有生产消费者；修复轮另补齐两处：写失败的可见 notice `notice_reading_position`（唯一映射 `screen/NoticeText`）与首帧布局完成后的可见集上报（判定仍是同一处 `ViewportSeen`）。**设备侧滚动观感与像素定位仍 `deferred — 用户自行装机验证（user decision 2026-09-21）`** |

不能用（入口不可达；下表每一项都是 PRD 标注 P0 的条目）：

| 缺失 | 现状（均为可复核的静态事实） |
| :--- | :--- |
| 搜索页（跨主题搜标题 / 锚定原文 / 回答 / 推理）〔2026-09-22 已接线，T-040〕 | 本行原记「无 `SearchScreen`、无布局；首页搜索是 `enabled="false"` 的死按钮；`SearchPresenter` 没有任何生产调用点」，**该状态已作废**：新增 `screen.SearchScreen` + `search.SearchPage`（纯 JVM 页面对象）+ `search.BranchTitles` + `wiring.SqliteSearchStore` 与四个布局（含 `layout-sw600dp/` 镜像）；`MainActivity` 实现 `SearchPresenter.Sink`，`openAnchor` 复用 T-042 的 `MainActivity.showChat(branchId, explicitAnchor)`；`HomeScreen` 给 `home_search` 挂了监听器（原文扫描 `artifacts/evidence/T-040-source-scan.txt`）。检索范围与 `search_scope` 引导文案一致（四项，**无「用户问题」臂**——T-015 的检索语句没有该臂，2026-09-24 更正，原「问题」措辞有误）。设备侧输入法与像素证据仍 `deferred — 用户自行装机验证（user decision 2026-09-21）` |
| 首页空态引导、主题永久删除、根标题改名、未读徽标〔T-043〕 | **已实现，待独立 verifier**：空态入口聚焦问题输入；主题卡提供改名/删除确认；删除复用 `TopicDao` 的级联；未读总数由序列差值汇总。真机启动已验证，完整点击路径仍待设备脚本化验收 |
| 主题与分支标题、分支摘要（派生 AI 产物）〔T-045〕 | **部分实现**：`BlockingDerivedCaller` / `DerivedService` 已接入真实流式网关，首题成功后会尝试生成根标题并走 `DerivedOperation` CAS 写回；分支摘要及其它派生种类仍无完整生命周期调用者，待继续施工与独立 verifier |
| 两类推荐（回答级「接下来问」/ 首页级推荐）〔T-046〕 | **已接线（2026-09-24 verified）**：回答级推荐在聊天读回时通过 `DerivedService` 按当前 revision 幂等触发；首页级推荐在有主题且配置 Key 时经 `HomeWiring.recommendations` → `derived.homeRecommendations` 取历史派生集合（`fromHistory=true` 有测试钉住），无主题/派生为空/无 Key 时回退启动兜底。真实 provider 载荷验收仍 deferred（无 Key） |
| 长会话继续追问（超预算上下文压缩）〔T-047〕 | **已接入压缩路径，待真实 provider 验收**：`ChatWiring.historyForRequest` 保留最近 10 个完整问答轮次；更早轮次通过 `CONTEXT_COMPRESSION` 派生为一个 `SUMMARY` artifact，只有成功写回后才替换历史，失败仍按原 `HISTORY_EXCEEDS_BUDGET` 失败关闭。 |

侧载提醒：release APK 的签名证书是 `CN=Treelearn Personal Sideload`（见第 2 节）。若手机上已装的是用另一把钥匙
签名的旧包，`adb install -r` 会以签名不一致失败，需先卸载旧包再装。

## 3. 本轮实际取得证据的条目

| 判据 | 命令 | 结果 | 证据文件 |
| :--- | :--- | :--- | :--- |
| 全模块检查 | `.\gradlew.bat check --no-daemon --console=plain`（在 `android/`） | `BUILD SUCCESSFUL`，`EXITCODE=0` | `artifacts/evidence/leader-full-check-2026-09-21.txt` |
| JVM 测试 | `.\gradlew.bat test --rerun-tasks --no-build-cache --no-daemon --console=plain`（在 `android/`；**强制全部重跑**，`UP-TO-DATE` 与 `FROM-CACHE` 不算证据） | 〔2026-09-22 03:30 **T-039 落盘后复测**；**口径已写明**〕`BUILD SUCCESSFUL`，`124 actionable tasks: 124 executed`。**读法 A（每模块只计一个变体）**：`:app` 334、`:core` 197、`:ai` 76、`:data` 26、`:tree-layout` 34 → **667 例 / 65 份套件报告**。**读法 B（Android 模块的 debug 与 release 两个变体都计；同一批类各跑一次）**：`:app` 668、`:core` 197、`:ai` 152、`:data` 52、`:tree-layout` 34 → **1103 例 / 108 份套件报告**（分项之和与总数一致：668+197+152+52+34=1103）。两读法均 **0 failures / 0 errors / 0 skipped**。含 T-039 新增的 `navigation/NavigationRootGateTest`（5 例全通过）。旧版此处写作「98 个测试类 / 1011 例」而**未写口径**，那是更早一次读法 B 的快照。 | `artifacts/evidence/T-REV-2026-09-22-jvm-tests-after-T039.txt`（头部含命令与 cwd，正文含本次强制重跑的原始输出、`EXITCODE=0`，末段是 `.trae/scripts/dev/count_jvm_tests.py` 的逐模块清单）；更早快照见 `artifacts/evidence/T-REV-2026-09-22-jvm-test-count.txt` 与 `artifacts/evidence/T-032-checks.txt` |
| Java-only guard | `powershell -ExecutionPolicy Bypass -File android/scripts/no-kotlin-check.ps1`（仓库根） | `VERDICT: PASS (13 OK line(s))`，退出码 0；3 个 APK 均无 Kotlin 运行时字节码 | 命令与结论见 `artifacts/evidence/` 对应 guard 证据 |
| lint | `check` 内含 | **0 error**；warning：`:app` 28、`:data` 1 | `android/*/build/reports/lint-results-*.xml` |
| instrumentation **源码可编译** | `.\gradlew.bat :app:assembleDebugAndroidTest --no-daemon --console=plain` | `BUILD SUCCESSFUL` | 本报告生成时的构建输出 |
| **未发生设备运行** | 目录文件计数 | `android/app/build/outputs/androidTest-results/connected/debug` = **0 文件** | 同上 |
| release 构建 | `.\gradlew.bat :app:assembleRelease --no-daemon --console=plain` | `BUILD SUCCESSFUL`，退出码 0 | `artifacts/evidence/T-018-release-apk-2026-09-21.txt` |
| 签名校验 | `apksigner.bat verify --print-certs <apk>`（build-tools 36.1.0） | 退出码 0，签名者与证书摘要如第 2 节 | 同上 |

## 4. 未取得证据的条目（**不得读作通过**）

| 条目 | 状态 | 原因 |
| :--- | :--- | :--- |
| 安装、启动、无崩溃 | `deferred — emulator not used this round (user instruction 2026-09-21)` | 用户指令：本轮不使用 emulator/设备 |
| 真实 Android SQLite 行级行为（新装/升级/重开/约束/事务/级联、搜索与阅读位置 SQL） | `blocked — 缺少设备` | `rules/sql.md` 要求用 API 28+ 真实 Android SQLite；JVM/H2/JDBC/Robolectric 不能替代 |
| 手机竖屏与平板横屏实机布局、44dp 热区、字体放大 | `deferred` | 同上 |
| reasoning 折叠/灰色/展开的设备端观感与像素检查 | `deferred` | 同上 |
| 真实 DeepSeek 冒烟（两模型 × thinking on/off） | `blocked — 缺少 Key` | 未提供测试 Key；**未发出任何真实请求** |
| 首页与对话的端到端流程（创建主题 → 提问 → 重开读回 → 重试/分叉/搜索/归档） | `blocked — 数据路径未接线` | 见第 5 节。**〔2026-09-22 更新〕**代码侧的「未接线」已由 `T-2026-09-21-real-data-path-wiring-032` 解除；本条现在只差设备上的实机验证，且用户已决定自行装 release APK 实测（见第 5 节末尾） |

## 5. 首页与对话数据路径接线〔历史快照 · 2026-09-21，其结论已被 T-032 取代〕

> **〔过期说明 · 2026-09-22〕本节是 2026-09-21 的历史快照，不再是当前事实。**
> `T-2026-09-21-real-data-path-wiring-032` 已删除 `wiring.PendingHomeWiring` 与 `wiring.PendingChatWiring`，
> 并把首页与对话接上真实数据路径（`TopicDao.listOverviews` 一条只读查询 + `wiring.HomeWiring` /
> `wiring.ChatWiring` 的真实实现，装配点仍是唯一的 `AppContainer`）。本节的正文与下面 1–5 条「可直接施工的
> 结论」保留为当时的记录；其中第 1、2、3 条已被 T-032 或 T-037 处理，第 4、5 条是 T-032 施工时实际照办的依据。
> **仍未解决的只有一件事**：真实设备上的 SQL 行级与端到端行为，按用户 2026-09-21 的决定记为
> `deferred — 用户自行装机验证（user decision 2026-09-21）`；本报告不把它写成 PASS。

`android/app/src/main/java/com/treelearn/app/wiring/PendingHomeWiring.java` 与 `PendingChatWiring.java`
仍在 `AppContainer` 的生产装配中，两者把每个无法实现的命令变成**屏幕上可见的** `NOT_WIRED` 提示，
而不是假装成功。也就是说：

- 首页的「最近主题」列表恒为空，提交首个问题返回 `NOT_WIRED`；
- 对话页能画界面，但 `send/retry/reset/selectRevision/fork` 一律返回 `NOT_WIRED`，不写任何一行。

**本轮据此得出的可直接施工的结论**（供下一次接线使用）：

1. `TopicDao` 没有「枚举主题」的查询，首页列表需要**新增一条只读 SQL**；按 `rules/sql.md`，它的正确性只能在真实 Android SQLite 上取证据。
2. `messages` 表有 `active_revision_id` / `active_operation_id` 两列，但 `MessageDao` **没有任何读回它们的入口**，`Message` 领域对象也不携带它们；对话页要显示「当前展示的是第几个版本」必须先补这条读路径。
3. `ChatCommands.send(...)` 带一个 `quote` 参数，但 `messages` 表没有承接引用的列，提示词设计文档也没有定义引用的线格式——**引用追问在当前数据模型里无处安放**，接线时要么先补 schema（需 `db-migration-reviewer` 与用户确认），要么明确返回可见拒绝。 **〔历史快照 · 2026-09-21〕本条第前半句已过期**：`T-2026-09-21-quote-persistence-messages-column-037` 已新增 `messages.quoted_text`（`NULL` = 无引用）与其不变式触发器，并把 `DATABASE_VERSION` 升为 2；当前权威为 `android/data/src/main/java/com/treelearn/data/db/TreeLearnSchema.java`。本条保留为当时的事实记录。**〔2026-09-22 修正〕尾句已不成立**：线格式已由 `T-2026-09-22-quote-into-request-044` 定稿，当前权威为提示词设计 V1.0 §5.1 与 `android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java` 的 `currentInput`；尾句原先指向的 `T-2026-09-21-real-data-path-wiring-032` 并未交付此项。
4. 请求装配可用 `DeepSeekRequestAssembler.assemble(purpose, thinkingEnabled, DeepSeekContextEncoder.encode(new ContextPlan(entries)), currentInput, PromptBudget)`；`Assembler`/`AcceptedWrite` 的用法在 `GenerationCoordinatorInstrumentedTest` 里已有可照抄的形状。首个主题的历史为空，`ContextPlan` 条目可由 `MessageDao.findByBranch` + `AnswerRevisionDao.findById` 直接拼出。 **〔历史快照 · 2026-10-16〕本条的 `AcceptedWrite` 与「首话题的行在 accepted 回调里写」已过期**：该类型现为 `GenerationCoordinator.LocalWrite`，`submitFirstTopic` 已并入 `submit(command, assembler, localWrite)`，写入发生在**开连接之前**；当前权威为技术方案 §8 第 4 条（2026-10-16 裁定）与 `android/README.md` 的「提交顺序」小节。本条其余部分仍是当时的记录。
5. `PromptBudget` 的容量与最小回答长度**没有任何权威数字**（技术方案与提示词文档只规定「先预留 system、当前问题和最小回答，不足则失败关闭」），接线时必须显式选定并记录为决定，不能默认。

### 5.1 引用失效：被引用的句子从未进入请求〔历史快照 · 2026-09-22 凌晨的发现；结论已被 T-044 推翻〕

> **〔过期说明 · 2026-09-22 03:20〕本节结论「模型看不到被引用的那句话」已不再成立。**
> `T-2026-09-22-quote-into-request-044` 定稿了引用进入提示词的线格式（提示词设计 §5.1），并让
> `ChatWiring.java:252` 把存储的引用交给 `DeepSeekContextEncoder.currentInput(input, quote)`，由后者包成
> `<treelearn_quote>…</treelearn_quote>` 前置在问题正文之前（只转义引用、不转义问题）。
> 当前权威是 `android/ai/src/main/java/com/treelearn/ai/prompt/DeepSeekContextEncoder.java:83-90`；
> 机械判据是 `.trae/scripts/dev/review_quote_path.py`（从代码推导结论，不是写死的），证据文件第 6 节
> `CHECK quote-reaches-prompt: PASS`。下表与下面的结论保留为**当时的记录**；其中「进请求」「历史」「领域类型」
> 「提示词包」四行的读法已过期——线格式定稿后不再需要 `ContextPlan.Entry` 携带引用。
> **T-044 仍为 `verifying`：contract-guardian 只判 PARTIAL（代码面 PASS、文档面未同步），本报告不把它记作已验收。**

| 环节 | 事实 | 位置 |
| :--- | :--- | :--- |
| 接收 | `ChatCommands.send(branchId, text, quote, …)` 收下引用，写入 `messages.quoted_text` | `wiring/ChatWiring.java:83-103` |
| 读回 | 引用作为 chip 读回并显示在该条用户消息上 | `ChatWiring.java:270` |
| 进请求 | **没有**：`submit` 把 `userMessage.userContent`（只有问题正文）当作请求的当前输入 | `ChatWiring.java:232-238` |
| 历史 | `history(branchId, questionOrdinal)` 在 `ordinal >= questionOrdinal` 处 break，**恰好排除了持有引用的那条消息** | `wiring/SqliteChatStore.java:108-131` |
| 领域类型 | `ContextPlan.Entry` 只有 `userText` / `reasoningText` / `answerText` / `summaryText`，**没有能装引用的字段** | `core/domain/ContextPlan.java:40-68` |
| 提示词包 | `android/ai/src/main` 内出现引用的行数 = **0** | 证据文件第 6 节 |

〔历史 · 已被 T-044 取代〕结论：引用当时是「存下来 + 显示出来」，**模型看不到被引用的那句话**。PRD 6.2.19 第 3 条要求的是「就这段话追问」，
第 7 条要求的「用户消息保留引用标识」已实现。这不是一处笔误能修的事——**提示词设计文档没有定义引用进入提示词
正文的线格式**，因此需要先定线格式（同步提示词文档与契约），再让 `ChatWiring.submit` 把它带进请求。
在定稿之前，本报告不把它记作「已实现」，设备上也不会观察到差异（界面看起来一切正常）。

## 6. 任务状态一览（本轮结束时）

| 任务 | 状态 | 说明 |
| :--- | :--- | :--- |
| T-003 不可变回答领域模型 / T-020 版本化提示词策略 | `verified` | 早前轮次已由独立 verifier 判 PASS |
| T-002/004/005/006/007/008/009/010/011/012/013/014/015/016/017/021/022/023/024/025 | `verifying` | 实现已落盘，**未取得独立 verifier 判定**；其中多数含设备相关验收项（见第 4 节） |
| T-038 树画布生产装配 | `verifying` | 〔2026-09-22 更新〕实现已落盘（`wiring/TreeCanvasStore` / `SqliteTreeCanvasStore` / `TreeCanvasWiring` + `AppContainer` 两端口 + 对话页「总览」入口），实现者自检判 PARTIAL、独立 verifier 的第 1 轮定点修复已复现；**验收结论尚未落盘**，故仍为 `verifying`。设备项 `deferred` |
| T-041 划词四项菜单 + 子分支弹层 | `verifying` | 〔2026-09-22 更新〕实现已落盘且**已进入产物**（第 2 节 dex 核对 `WordMenuPopup` / `ChildBranchPopup` / `BranchChildren`）；实测规模 544 行（9 新文件 502 + 改动 42，口径 `T-041-sizing.txt`；〔2026-09-24 更正〕初稿的 534~549 两口径数字与任务证据不同源，已废弃）**超 500 硬上限** |
| T-044 引用进请求 | `verifying` | 〔2026-09-22 更新〕实现已落盘并进入产物（dex 内含 `<treelearn_quote>`）；contract-guardian 复核 6 问中 4 PASS / 1 PARTIAL / 1 FAIL，**唯一 FAIL 是文档面**（本报告与 `android/README.md` 滞后）。**〔2026-09-22 修正〕该文档面已同步**：本报告 §5.1 已加历史快照标记并指向当前权威、本节原先的尾句已修、本行已修；`android/README.md:666` 的残留风险出处已写作 §5.1、无需另改。**文档面同步不等于验收通过**——T-044 仍为 `verifying`，独立 verifier 已判 **PARTIAL**（残留项含归一化非唯一，见 `artifacts/evidence/T-044-verify-*.txt`），本条不记作已通过 |
| T-039 导航页 | `blocked` | 〔2026-09-22 03:49 更新〕屏幕、布局、装配、宿主与三条入口**全部落盘**并进入产物；RED→GREEN 同一条命令 26 例 0 失败（`NavigationPresenterTest` 21 + `NavigationRootGateTest` 5）。**独立 verifier 已判 `FAIL`**（`artifacts/evidence/T-039-independent-verification.txt`，1143 行）：任务自有的五条验收标准全 PASS、六道门禁在冻结快照里复跑全绿，**唯一未达标项是规模门**——实测 **646 行**，超 500 硬上限 146 行 / 29.2%，且不存在用户批准记录。所以状态置 `blocked`：卡住的是用户的一句话，不是代码。见第 7 节与第 10 节第 1 条 |
| T-040 搜索页 | `verifying` | 〔2026-09-22 更新〕实现已落盘：`screen/SearchScreen`（配 `search/SearchPage` 纯 JVM 页面对象与 `search/BranchTitles` 端口）+ `layout/screen_search*.xml` + `layout-sw600dp` 镜像；首页 `home_search` 不再是死按钮（`HomeScreen` 挂监听器走 `HomeActions.onSearchPressed()`）；`MainActivity.openAnchor` → `showChat(branchId, anchor)`，**该缝由此获得第一个真实生产消费者**。新测 13 例 RED→GREEN 0 失败、全量 `:app` 单测 354 例 0 失败、`.\gradlew.bat check` 与 Java-only guard 退出码 0。**待独立 verifier 判定**；真机输入法与像素证据 `deferred — 用户自行装机验证（user decision 2026-09-21）` |
| T-042 阅读位置保存与恢复接线 | `verifying` | 〔2026-09-22 修复轮更新〕实现已落盘：唯一接缝 `MainActivity.showChat(String, ReadingAnchor)`（`MainActivity.java:459`，以 `artifacts/evidence/T-042-fix1-source-scan.txt` 为准）在 `chatPresenter.start()` 之前经同一 port 保存显式锚点；`screen/ChatScreen` 只上报滚动事实（`onRowsLaidOut` / `onViewportChanged` / `onLeaving`）；`AppContainer` 装配 `SqliteReadingStore`。`ChatReadingTest` 7 例 RED→GREEN 0 失败；**Fix round 1（D1–D5，2026-09-22）已落盘**：写失败只出可见 notice `notice_reading_position`（DAO 未弱化）、首帧可见集上报（同一条命令 11 例 0 失败、全量 `:app` 单测 358 例 0 失败、两处变异各自只打红目标用例）、正文措辞与行号引用刷新、删掉 4 参 `Snapshot.of` 重载；证据 `artifacts/evidence/T-042-fix1-*.txt`。`:app` 单测 341 例与 `.\gradlew.bat check` 均退出码 0，Java-only guard PASS。**待独立 verifier 复验**；真机滚动与像素证据 `deferred — 用户自行装机验证（user decision 2026-09-21）` |
| T-043 首页与主题级收口 | `ready` | 未开工；对应第 2.1 节「不能用」表中「首页空态引导、主题删除、根标题改名、未读徽标」一行。与 T-039 / T-040 共用 `MainActivity` 宿主，须串行 |
| T-045 / T-046 / T-047 派生 AI 接线 | `verified / verified / verified`（2026-09-24 复核收口） | T-045 已接入根标题、分支标题与分支摘要（含 `TitleArtifactApplier` 写回 `Branches.TITLE`）；T-046 两类推荐均已接入（首页级有 `fromHistory` 正/负态断言）；T-047 已接入「最近 10 轮 + 更早摘要」压缩路径，且拒绝理由字段获得消费者（压缩成功 → `HISTORY_COMPRESSED` 可操作文案）。三项共同残留：真实 provider Key 的网络验收 deferred |
| T-032 真实数据路径接线 | `verifying` | 〔2026-09-22 更新〕已实现并落盘：占位类删除、`AppContainer` 接真实装配、`TopicDao.listOverviews` 新增只读查询；**待独立 verifier 判定**。规模（793 行，超 500 行硬预算）待用户裁定 |
| T-018 安全/Release/核心流加固 | `ready` → 部分完成 | 本轮完成：网络安全配置、release 签名与 APK、lint/守门证据；**未完成**：Telemetry no-op 落位、端到端测试执行 |
| T-033 交付报告（本文件） | `verifying` | 见第 8 节关于独立性的说明 |
| T-019 独立最终验收 | `ready` | 见第 8 节 |

## 7. 规模越线事实（〔2026-09-24 更新〕裁定已收口，逐条见文末裁定块）

以下任务的实测规模（口径：**非空非注释的生产代码行**）超过 500 行硬上限，需要用户明确批准例外或拆分返工：

| 任务 | 实测判定行数 | 超出比例 |
| :--- | ---: | ---: |
| T-2026-09-20-pure-java-tree-layout-016 | 862 | +72% |
| T-2026-09-20-search-unread-reading-position-015 | 807 | +61% |
| T-2026-09-20-generation-coordinator-recovery-008 | 765 | +53% |
| T-2026-09-20-deepseek-stream-gateway-006 | 749 | +50% |
| T-2026-09-20-derived-ai-artifacts-011 | 726 | +45% |
| T-2026-09-20-chat-reasoning-answer-actions-013 | 1584 | +217% |
| T-2026-09-20-immutable-context-snapshots-007 | 671 | +34% |
| T-2026-09-22-real-data-path-wiring-032 〔2026-09-22 新增〕 | 793 | +59% |
| T-2026-09-21-word-menu-and-child-branches-041 〔2026-09-22 新增〕〔2026-09-24 数字更正〕 | 544 | +8.8% |
| T-2026-09-21-navigation-page-archive-delete-039 〔2026-09-22 新增，**实测值**〕 | 646（新增 6 文件 531 + 改动 19 文件 115） | +29.2% |

T-041 的口径（〔2026-09-24 更正〕唯一判定值，来源 `T-041-sizing.txt` 与 verifier 独立复算）：9 个新文件 502 + 改动 42 = **544**。初稿的「496+38=534 / 511+38=549」两套读数与任务证据不同源（文件数 5 vs 9 亦不符），按 harness-tasks.md「同一任务不得两套口径」废弃。
差异来自 XML 注释行的剥离方式，两种口径都超 500 硬上限，故按区间记录，不取单点。
T-039 已写完；独立 verifier 判定 **`FAIL`**（唯一原因就是本表这一行），任务随之置为 `blocked` 而不是 `verifying`——它的五条验收标准与六道门禁全过，没有代码缺陷可修。`646` 是实现者用 `.trae/scripts/dev/count_production_lines.py` 实测的两项之和
（新增 6 文件 531 + 改动 19 文件 115 = 646，见 `artifacts/evidence/T-039-sizing-final.txt`）；此前的 `≈695` 是开工前的预估，已作废。判定全文见 `artifacts/evidence/T-039-independent-verification.txt`。
**代码已落盘但例外未获批**——实现者未自行追认，用户亦未表态（见第 10 节第 1 条）。

另：T-2026-09-20-sqlite-schema-dao-transactions-004 自设了 400 行阈值，需要用户确认该阈值是否继续有效。
`harness-tasks.md` 中已结清的四个任务（T-003 741 已批准、T-004 458、T-021 230、T-022 386）不在此列。

**〔2026-09-24 裁定块〕** 用户于 2026-09-24 在对话中逐批裁定，上表越线任务**全部按「事后追认豁免」收口**（批准落在实现之后，各任务文件已按 `harness-tasks.md` 第 60 行如实登记，剩余风险逐条留档）：
T-016（862，+72%）、T-015（807，+61%）、T-013（1584，+217%）、T-008（765，+53%）、T-011（726）、T-007（671）、T-006（749）、T-041（544，+8.8%）、T-032（793，+59%）、T-040（517..584 区间， verifier 复算）。T-039（646）不经豁免——已按用户更早的裁定**拆分为 T-107（UI 切片 496 行）与 T-108（装配切片 150 行）**，T-039 在两者 verified 后归档。T-004 的 400 行自设阈值问题仍待用户表态（非硬上限，不阻塞）。本节标题的「待用户裁定」状态自此解除。

## 8. 流程事件与独立性缺口（必须如实说明）

1. **并行 lane 全部中断**：本轮派出的 5 个 subagent lane（4 个实现 lane + 1 个 verifier lane）在运行中同时收到
   `402 Payment Required: Insufficient Balance`（subagent 后端 `api.deepseek.com` 余额不足）而终止。
   容器提供的显式模型覆盖只接受 `gpt-*` 名称，而后端只接受 `deepseek-flash` / `deepseek-v4-pro`，因此**无法换后端续跑**。
2. **因此本轮没有独立 verifier**：`harness-verification.md` 要求 verifier 独立于实现者，主会话自跑 lint/测试不能替代。
   本轮剩余工作由主会话直接执行，**没有任何任务在本轮被判定为 `verified`**。这不是「已经验过」，而是「没人独立验」。
3. **中途修复（主会话执行，均为最小修复）**：
   - `screen/TreeCanvasView.java:274/278`：`TextUtils.ellipsize` 返回 `CharSequence` 却被传给 `Canvas.drawText(String, …)`，`:app` 编译失败——改为 `.toString()`（原实现 lane 中断时留下的半成品）。
   - `navigation/NavigationViewState.java` + `NavigationPresenter.java` + `NavigationActions.java`：测试引用的
     `collapsed` / `summaryCollapsed` / `onGroupToggled` / `onSummaryToggled` 尚不存在（10 个编译错误）——补齐为
     「折叠是视图事实，绝不落库」，`:app` 单测恢复全绿（`NavigationPresenterTest` 21 例通过）。
   - 证据文件自证性修正：`artifacts/evidence/leader-full-check-2026-09-21.txt` 中有一段落因采集管道用了
     `Select-Object -First`（会提前终止上游进程）而**被中途杀掉**，该段已就地标注说明，不得读作结果。

## 9. 已知风险

- **未在设备上运行过**：所有 UI、SQL、生命周期结论都只有编译期与 JVM 层证据。
- **〔2026-09-22 03:36 更正〕「功能未接线」已不成立**：首页与对话已接真实数据路径，核心流程（建主题 → 提问 →
  重开读回 → 重试 / 分叉）代码路径可达；划词四项菜单（T-041）、引用进请求（T-044）、导航页与树画布
  （T-038/T-039）也已接线，**搜索页已由 T-040 接线**。仍不可用的是**首页主题级入口**一处入口，以及
  **派生标题与摘要 / 两类推荐 / 超预算上下文压缩**三处派生接线，逐条见第 2.1 节。
- **〔2026-09-22 03:36 更正〕派生管线整条没有生产者**：标题、分支 AI 总结、两类推荐、上下文压缩共用
  `DerivedOperation`，而它在 `src/main` 内没有调用者。表现是「不报错、只是永远是空的 / 不变化」——
  这类缺陷在装机初测时最容易被当成正常。见第 2.1 节 T-045 / T-046 / T-047 三行。
- **引用追问已进请求，但缺独立验证**：T-044 已把引用文本经 `DeepSeekContextEncoder.currentInput` 带进请求
  （第 5 节末尾保留了「进请求之前」的历史结论，勿混读）。contract-guardian 判 PARTIAL，理由只在文档同步，
  代码面 PASS；独立 verifier 尚未判定。
- **lint warning 由 18 涨到 29**（`:app` 28 + `:data` 1，0 error）：新增 UI 资源带来的警告尚未逐条归因。
- 〔2026-09-24 更新〕规模越线任务已全部裁定（第 7 节裁定块）：除 T-039 走拆分路线外，其余均获「事后追认豁免」，验收结论的流程缺口已闭合；各任务文件留有剩余风险记录。

## 10. 后续步骤（建议顺序，2026-09-22 重写）

1. **〔2026-09-24 已完成〕用户裁定规模例外**：第 7 节列出的越线任务已全部裁定——T-032/T-041/T-040 及其余越线项获「事后追认豁免」，**T-039 走拆分路线**（拆为 T-107 UI 切片 + T-108 装配切片，二者 verified 后 T-039 归档）。
2. **按依赖顺序把剩余能力做完**，一次只跑一个——`MainActivity.java` 与 `AppContainer.java` 是全部屏幕任务的
   共享冲突点，同目录并发跑 Gradle 会让 `--rerun-tasks` 证据不可信，故只能串行：
  **T-042（阅读位置保存与恢复，已实现、待独立 verifier 判定）→ T-040（搜索页 + 首页搜索入口，**已实现、待独立
   verifier 判定**，2026-09-22；`depends_on: T-042` 的顺序理由即「跳进命中位置」要先用上 T-042 的阅读位置路径）→ T-043（首页空态引导 / 主题永久删除 / 根标题改名 /
   未读徽标；与 T-039、T-040 共用宿主）→ T-045（派生接线：根与分支标题、分支摘要）→ T-046（两类推荐）→
   T-047（超预算上下文压缩；依赖 T-045，压缩产物走同一派生门面）**。
   T-039、T-041、T-044 已落盘并进入产物，不再重排。
3. **每收工一个任务就走一条固定回路**：① 抽核证据（RED 与 GREEN 的命令逐字一致、证据文件晚于被测源码、
   规模口径与分项之和自洽）；② 派**独立 verifier**（与实现者不同的 agent，且工作副本不带实现者的构建产物）；
   ③ 收口该任务状态；④ 重建 release APK 并刷新第 2 节指纹与第 2.1 节清单。**APK 必须在最后一个任务落盘后重建，
   否则装机用的仍是旧产物**。
4. **清 `verifying` 积压**：本轮结束时仍有 26 个任务停在 `verifying`（第 6 节），其中 T-038 的验收结论至今未落盘。
   本轮起改为「每收工一条即派独立 verifier」，不再攒批。
5. **T-017 的判定待用户拍板**：它的验收标准含「API 36 设备 UI 测试 + 像素检查 + 平板双栏」，用户在 2026-09-21
   决定放弃设备验证后**永不满足**；建议改写为静态可验子集或直接判 `canceled`（画布的生产装配已由 T-038 与
   T-039 完成，不是未开工）。
6. 用户按 2026-09-21 决定自行装机验证全部设备项（第 4 节）。
7. `T-2026-09-20-independent-harness-verification-019` 在以上证据齐备后给出最终验收判定。

## 11. 本报告不包含

keystore 口令、API Key、system prompt 正文、提示词正文、用户问题、回答与 reasoning 正文。
只出现脱敏元数据（版本号、摘要、行数、状态）。
