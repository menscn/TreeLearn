# 方案 A 结构对齐 + 莫兰迪证据索引（2026-10-22）

本目录是 052 父需求（053–059）的全部证据。所有命令都在同一工作树执行；真机验证只做
`adb install -r`，**没有运行任何仪器测试**——`BottomTabShellInstrumentedTest` 的
`@Before/@After` 会 `clearTopics()`，全量 instrumentation 还会卸载应用并清掉本机数据与 API Key。

## 环境

| 项 | 值 |
| :--- | :--- |
| 设备 | `AWSS013807000356`（ALP-AN00，1200×2652，Android 13 / SDK 33） |
| APK | `android/app/build/outputs/apk/debug/app-debug.apk` |
| 应用组件 | `com.treelearn/com.treelearn.app.MainActivity` |
| Web 包管理 | `pnpm@11.5.2`，Node 24 |

## 一键复核命令

```bash
# Android：全量 JVM + 三份 APK + Java-only 守门
cd android
./gradlew :app:testDebugUnitTest :data:testDebugUnitTest \
  :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease verifyNoKotlinDex

# token 四方守门
cd ..
python -X utf8 .codex/scripts/dev/check_design_tokens.py

# Web 出口
pnpm --dir frontend verify

# 任务卡与 core guard
python -X utf8 .codex/scripts/validate_tasks.py
python -X utf8 .codex/scripts/run_all_guards.py --tier core
```

## 各片证据

| 片 | 证据文件 | 结论 |
| :--- | :--- | :--- |
| 053 token 链 | `053-red.txt`、`053-green.txt`、`053-jvm-all.txt` | 6 枚莫兰迪 token 贯通，守门 0 failure |
| 054 字体 | `054-red.txt`、`054-green.txt`、`054-assemble.txt`、`054-token-guard.txt` | 内置 SC 衬线 + OFL，定向 GREEN |
| 055 外壳/首页 | `055-red.txt`、`055-green.txt`、`055-jvm-all.txt`、`055-builds.txt`、`055-token-guard.txt` | 悬浮 tab bar 与首页结构 |
| 056 对话 | `056-red.txt`、`056-green.txt`、`056-jvm-all.txt`、`056-builds.txt`、`056-token-guard.txt`、`056-instrumented.txt` | 胶囊顶栏、阅读面、行内分支栈、dock 输入 |
| 057 学习库/分支导航 | `057-red.txt`、`057-green.txt`、`057-jvm-all.txt`、`057-builds.txt`、`057-token-guard.txt` | 胶囊顶栏、玻璃搜索/分段、28dp 总结面板、节点分支行与虚线空态 |
| 058 Web 材质 | `058-red.txt`、`058-green.txt`、`058-vitest-all.txt`、`058-verify.txt` | 两枚柔光 + `--tint-sage`；`verify` 16/16 |
| 059 整跑 | `059-jvm-all.txt`、`059-android.txt`、`059-token-guard.txt`、`059-guards-core.txt` | 见下方读数 |

`056-instrumented.txt` 只有 99 字节，记录的是「未跑仪器测试」的决定与原因，不是执行结果。
`059-guards-core-before-fix.txt` 与 `059-parity-before-fix.txt` 是修复前的诊断读数（3 条红 + 1 条 INCONCLUSIVE），保留用于说明修复来源，不代表最终状态。

## 059 整跑读数（2026-10-22）

| 检查 | 读数 | 证据 |
| :--- | :--- | :--- |
| app JVM | 887 tests / 0 failures / 0 errors（`--rerun-tasks` 实跑） | `059-jvm-all.txt` |
| data JVM | 65 tests / 0 failures / 0 errors（`--rerun-tasks` 实跑） | `059-jvm-all.txt` |
| debug / androidTest / release APK | `BUILD SUCCESSFUL` | `059-android.txt` |
| `verifyNoKotlinDex` | `PASS (3 APK(s) inspected ...)` | `059-android.txt` |
| token 守门 | `OK=10 FAIL=0 SKIPPED=0`、`0 check(s) failed` | `059-token-guard.txt` |
| Web `verify` | `verify steps=16 ok=16 bad=0 fault=0`、`OVERALL_EXITCODE=0` | `058-verify.txt` |
| Web Vitest | 19 files / 116 tests passed | `058-vitest-all.txt` |
| parity 清单/台账 | 清单重生成（screens=14 surfaces=15），台账 29 行判定全部通过，自检 20/20 | `059-inventory-regen.txt`、`059-ledger-regen.txt`、`059-parity-selftest.txt` |
| core guard | `RUN-ALL-GUARDS pass=39 fail=0 inconclusive=0`、`VERDICT: PASS`、`EXITCODE=0` | `059-guards-core.txt` |

## 真机四屏

按「首页 → 对话 → 分支导航 → 学习库」采集，均保留真机状态栏；`adb install -r` 后本机 3 个真实主题与历史仍在。

| 屏幕 | 文件 | 可见结构 |
| :--- | :--- | :--- |
| 首页 | `059-home.png` | 胶囊顶栏、衬线「知芽」、玻璃输入卡、推荐/历史玻璃卡、悬浮胶囊 tab bar |
| 对话 | `059-chat.png` | 胶囊顶栏（返回/标题/分支/画布/设置）、86% 阅读面、中断提示、dock 输入 |
| 分支导航 | `059-navigation.png` | 胶囊 sans 顶栏、28dp 总结面板 +「AI 总结」、真实列表头「当前 · 0 子分支」、22dp 节点行、虚线空态 |
| 学习库 | `059-library.png` | 胶囊衬线顶栏、全圆角玻璃搜索、单容器分段「进行中 3 / 已完成 0」、紧凑玻璃话题卡、芽标说明 |

## 已知缺口（不得读成已解决）

- 顶部胶囊吸顶、AI 总结渐隐遮罩、导航页平板专属双栏：deferred。
- `tint-rose` / `tint-lilac` / `tint-sand` 已生成但两端都没有消费者；`--tint-sage` 只在 Web 推荐标签上消费。
- 真机四屏只覆盖手机竖屏；Pad 横屏截图仍未采集。
- 未跑仪器测试；`LibraryArchiveInstrumentedTest`、`LibraryTopicSummaryInstrumentedTest` 等行为证据仍停留在最近一次历史执行，本轮没有重跑。
