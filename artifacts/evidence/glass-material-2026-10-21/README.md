# 晨雾玻璃材质 · 验收证据（2026-10-21/22）

本目录证明 T-2026-10-21-047 选定的「A 晨雾玻璃 + C 1px 高光边 + 更强通透」已经落到共享 token、Android 生产界面与 Web 工作区，并记录所有可复跑的读数、不可执行项与风险。

- 结论：token 四方一致；Android 定向 TDD 红转绿、全量 JVM 859 例全绿、构建与无 Kotlin 守门通过、真机四屏可见玻璃场景与高光边且原始数据未被清理；Web 定向 TDD 红转绿、全量 111 例全绿、`pnpm verify` 16/16、浏览器 8/8 读数成立。
- 限制：本机浏览器仪器在打印 PASS 后未能自行退出（详见「核心整跑」），核心整跑不能报全绿；本轮没有安装 Playwright，浏览器取证使用仓库既有 CDP/Edge 驱动。
- 边界：未提交、未推送、未新增依赖；未运行会卸载应用的全量 instrumentation。

## 环境

| 项 | 读数 |
|---|---|
| 主机 | Windows 10.0.26200 x64，Git Bash |
| Git HEAD | `f77d34f`（玻璃改动均在工作树，未提交） |
| Android 构建 | Gradle 8.14.3，JDK 21.0.10 |
| Web | Node v25.9.0，pnpm 11.5.2 |
| 浏览器 | Microsoft Edge 155.0.4283.45，无头 CDP |
| 真机 | `ANRBBB5427000010`，Android 17 / API 37，2136×3200，density 420 |
| 应用 | `com.treelearn`，debug APK 2,231,583 bytes |
| 证据时间 | 2026-10-21 23:xxZ 起，2026-10-22 08:xx（+08:00）收口 |

## 命令与读数

| 对象 | 命令 | 预期 | 实际 | 记录 |
|---|---|---|---|---|
| token RED | `python -X utf8 .codex/scripts/dev/check_design_tokens.py` | 非 0（缺 4 个资源与 19 个变量） | 退出 1 | `token-red.txt` |
| token GREEN | 同上 | 0 | 退出 0；63/63/20/63，10 项 OK | `token-green.txt` |
| token 自检 | 同上 `--selftest` | 0 | 退出 0；41 例 | `token-selftest.txt` |
| 生成物漂移 | `python -X utf8 .codex/scripts/dev/gen_design_tokens_css.py --check` | 0 | 退出 0；`bytes=2065 tokens=63` | `051-guards.txt` |
| Android 定向 RED | `./gradlew :app:testDebugUnitTest --tests "…GlassMaterialTokensTest" --tests "…GlassSurfaceLayoutTest"` | 非 0 | 退出 1；11 例中 10 红 | `android-red.txt` |
| Android 定向 GREEN | 同上 | 0 | 退出 0；`BUILD SUCCESSFUL in 30s` | `android-green.txt` |
| Android 构建 | `./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :verifyNoKotlinDex` | 0 | 退出 0；`BUILD SUCCESSFUL in 3m 34s`；`verifyNoKotlinDex: PASS` | `android-build.txt` |
| Android JVM | 汇总 `android/app/build/test-results/testDebugUnitTest/*.xml` | 859 例全绿 | `files=96 tests=859 failures=0 errors=0 skipped=0` | `android-jvm-results.txt` |
| Web 定向 RED | `pnpm --dir frontend test -- test/styles/glass.test.ts` | 非 0 | 退出 1；4 红 / 107 绿 | `web-red.txt` |
| Web 定向 GREEN | 同上 | 0 | 退出 0；111/111 | `web-green.txt` |
| Web 全量 | `pnpm --dir frontend verify` | 0 | 退出 0；`verify steps=16 ok=16`、`VERIFY PASS` | `web-verify.txt` |
| 浏览器读数 | 仓库 CDP 驱动，1440×900 与 390×844 | 8/8 | 8/8 `pass=true`；截图 8 张 | `browser-readings.json` |
| 任务校验 | `python -X utf8 .codex/scripts/validate_tasks.py` | 0 | 退出 0；46 files，errors=0，warnings=15 | `051-fix-verification.txt` |
| 上下文包 | `python -X utf8 .codex/scripts/check_context_pack.py` | 0 | 退出 0 | `051-fix-verification.txt` |
| 事实源 | `python -X utf8 .codex/scripts/check_source_of_truth.py` | 0 | 退出 0 | `051-fix-verification.txt` |
| metrics | `python -X utf8 .codex/scripts/metrics_report.py` | 0 | 退出 0 | `051-fix-verification.txt` |
| 守门自检 | `python -X utf8 .codex/scripts/dev/checkers_selftest.py` | 0 | 退出 0；268/268 | `051-fix-verification.txt` |

## 真机取证

安装方式固定为 `adb install -r`，不卸载、不清数据：

```
./gradlew :app:assembleDebug
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

- 安装前原始库：`treelearn.db` 196,608 bytes（2026-09-29），无 `shared_prefs`，`files/attachments` 存在；读数见 `device-data-before.txt`。
- 安装后行数：`topics=0 branches=0 messages=0 providers=1 models=1`。既有 provider/model 配置保留，主题与消息未被清空出数据。
- 对话与学习库需要内容才能观察阅读面与输入区，因此临时写入主题「晨雾玻璃材质验收」后截取 `chat-android.png`、`library-android.png`；随后按 `device-data-after.txt` 的步骤恢复原始数据库、删除 journal/wal/shm 并重启，`home-android-restored.png`、`library-android-restored.png` 显示恢复后的空态，证明临时夹具已移除。
- 截图：`home-android.png`、`settings-android.png`（原始空态）、`chat-android.png`、`library-android.png`（临时夹具）、`home-android-restored.png`、`library-android-restored.png`、`drawer-android.png`（恢复后打开玻璃抽屉）。
- 复跑脚本：`capture_shots.py`（设备截图，复用仓库 `capture_screen` 的 id 导航；与 Web 浏览器无关）。

## 浏览器取证

- 工具偏差：本机未安装 Playwright，也未新增依赖；改用仓库既有的 `parity_cdp` 驱动，以 Edge 无头实例打开 `http://127.0.0.1:5174/`。读数与截图等价，但工具名与 050 卡最初写的 Playwright 不同，已在 050/051 的 Verification report 登记。
- 视口：1440×900（桌面）与 390×844（窄窗）；桌面与窄窗均无横向溢出。
- 关键读数（`browser-readings.json`）：
  - 侧栏/顶栏：`rgba(255,255,255,0.54)` + `blur(28px) saturate(1.5)` + `inset 0 0 0 1px rgba(255,255,255,0.88)` + `0 12px 32px rgba(22,36,28,0.07)`。
  - 阅读面：`rgba(255,255,255,0.86)`，28px 圆角；composer `0.72`；字段 `0.5`。
  - 窄窗：`data-nav=collapsed` 时无覆盖层；展开时侧栏为玻璃覆盖层且仍可关闭。
- 截图：`desktop-home.png`、`desktop-home-nav-collapsed.png`、`desktop-chat.png`、`desktop-library.png`、`desktop-settings.png`、`narrow-home.png`、`narrow-sidebar-open.png`、`narrow-chat.png`。

## 核心整跑

首轮整跑记录在 `051-core-guards.txt`（39 条 core 守门）：33 绿、3 红、1 连带 INCONCLUSIVE、2 条超时。非绿项与处置：

| 守门 | 首轮读数 | 原因 | 轮后处置 |
|---|---|---|---|
| `check_context_pack` | FAIL，退出 1，无判据行 | `Docs/` 下一张未被引用的 PNG 被当成 UTF-8 读取，脚本抛 `UnicodeDecodeError` | 图片移到 `artifacts/evidence/web-workspace-proposal-2026-10-21/index-shot.png`；复跑 0 |
| `check_source_of_truth` | FAIL，退出 1 | `design-system/README.md:50` 把生成型计数写进正文 | 删除正文里的计数措辞，由守门脚本打印；复跑 0 |
| `metrics_report` | FAIL，退出 1 | 任务卡状态变化后报告过期 | `--write` 重生成；复跑 0 |
| `checkers_selftest` | INCONCLUSIVE，退出 3 | 基线里的 `check_context_pack` 未通过导致连带 | 上述修复后复跑 `OVERALL: PASS`（268/268） |
| `verify_1to1_selftest` | TIMEOUT 600s | 仪器打印 `SELFTEST PASS cases=40` 与 `EXITCODE=0` 后，Node 事件循环仍被浏览器句柄保持，进程不退出，汇总器等不到退出 | 单独复跑同样在打印 PASS 后挂住，手动终止；见 `verify-1to1-selftest-standalone.txt` |
| `cdp_shot_selftest` | TIMEOUT 600s | 同上；打印 `SELFTEST PASS cases=17` 与 `EXITCODE=0` 后不退出 | 单独复跑同样挂住，见 `cdp-shot-selftest-standalone.txt` |

基线：`artifacts/evidence/guards-2026-10-20-final.log` 的 core 整跑为 39/39 绿（`verify_1to1_selftest 217.1s`、`cdp_shot_selftest 39.8s`）。本轮两条浏览器仪器超时属于本机 Edge 子进程滞留，不是这两条判据转红；单独复跑已打印 PASS，但进程未能自行退出。

收口整跑（`051-core-guards-final.txt`，2026-10-22 08:30 起）读数：

```
RUN-ALL-GUARDS pass=37 fail=0 inconclusive=0 fault=0 timeout=2
VERDICT: INCONCLUSIVE (2 guard(s) hit their own timeout)
EXITCODE=3
```

- 37 条全绿，包括 `checkers_selftest rc=0 82.1s`、`web_walk --selftest rc=0 198.5s`、`check_design_tokens`、`check_pointer_liveness --selftest`、`check_run_all_guards` 自检。
- `verify_1to1_selftest`：捕获输出里有 `SELFTEST PASS cases=40 cli=20 muts=18 failures=0` 与 `EXITCODE=0`，但进程不退出，08:44 撞 600s 上限。
- `cdp_shot_selftest`：同样打印 `SELFTEST PASS cases=17 failures=0` 与 `EXITCODE=0` 后被判超时（08:54）。日志中出现的 `VERDICT: FAIL` / `EXITCODE=1` 是该自检内部的负例（`neg-shot-scale-mismatch`，期望即 1），不是本轮失败。
- 之后 10 条快速守门继续执行并全部通过，说明超时没有掩盖其它红项。
- 整跑后的状态收口只改任务卡与证据文档；受影响的 `validate_tasks.py`、`metrics_report.py` 与 `checkers_selftest.py` 已单独复跑全绿，记录在 `051-fix-verification.txt`。

## GAP 与风险

GAP：

- 核心整跑无法在本机退出 0：两条浏览器仪器在 Edge 子进程未退出时撞 600s 上限；单独复跑均打印 PASS/EXITCODE=0，但没有可信的进程退出码。
- 未使用 Playwright（未安装、未新增依赖），浏览器证据来自仓库 CDP/Edge 驱动。
- Android 按设计不做实时背景模糊；场景是静态柔焦＋半透明表面。
- 平板横屏未单独截图；真机只覆盖手机竖屏。
- 对话与学习库的玻璃读数来自临时种子库，已恢复；恢复后只有首页/学习库空态与抽屉截图。
- 未做厂商色偏、对比度仪器测量与低端设备帧率基准；深色模式不在本轮范围。
- `versionName` 在设备读数为 `1.0`，不是 `1.0.0`；不影响材质验证。

风险：

- 半透明白在部分厂商屏幕上可能出现色偏；阅读面保持 86% 白是当前的对比度取舍。
- 低端机多层半透明与 Web `backdrop-filter` 可能有滚动帧率损失，本轮没有性能预算承诺。
- 后续新增页面若继续使用实色 surface 或给玻璃 drawable 加 `backgroundTint`，会绕过材质层并抹掉 1px 高光边。
- `backdrop-filter` 会创建合成层；窄窗覆盖层若外层再加 `transform`，定位可能受影响。
- 生成型计数不得写进正文，否则 `check_source_of_truth` 会再次判红。

## 文件索引

- token：`token-red.txt`、`token-green.txt`、`token-selftest.txt`、`051-guards.txt`
- Android：`android-red.txt`、`android-green.txt`、`android-build.txt`、`android-jvm-results.txt`
- Web：`web-red.txt`、`web-green.txt`、`web-verify.txt`、`browser-readings.json`、`desktop-*.png`、`narrow-*.png`
- 真机：`device-data-before.txt`、`device-data-after.txt`、`home-android*.png`、`library-android*.png`、`chat-android.png`、`settings-android.png`、`drawer-android.png`、`capture_shots.py`
- 守门：`051-core-guards.txt`（首轮）、`051-core-guards-final.txt`（收口整跑）、`051-fix-verification.txt`（轮后复跑）、`verify-1to1-selftest-standalone.txt`、`cdp-shot-selftest-standalone.txt`

## 复跑

```
python -X utf8 .codex/scripts/dev/check_design_tokens.py
python -X utf8 .codex/scripts/dev/check_design_tokens.py --selftest
python -X utf8 .codex/scripts/dev/gen_design_tokens_css.py --check
cd android && ./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :verifyNoKotlinDex
pnpm --dir frontend verify
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
python -X utf8 .codex/scripts/run_all_guards.py --tier core
```

真机复跑请始终使用 `adb install -r`；全量 instrumentation 会卸载应用并清除本地数据库与 API Key。
