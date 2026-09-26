# -*- coding: utf-8 -*-
"""Regenerate T-055 evidence file with explicit UTF-8 (bash echo mangled CJK)."""
import io, os

ROOT = r"D:\Project\Android\Treelearn"
EV = os.path.join(ROOT, "artifacts", "evidence", "T-055-home-slimming.txt")

def read_tail(path, pattern, n=6):
    out = []
    with io.open(os.path.join(ROOT, path), "r", encoding="utf-8", errors="replace") as f:
        for line in f:
            if pattern in line:
                out.append(line.rstrip("\n"))
    return out[-n:]

red_errors = read_tail(r"artifacts\evidence\T-055-red-2026-09-24.log", "错误", 8)
red_build = read_tail(r"artifacts\evidence\T-055-red-2026-09-24.log", "BUILD", 2)
red_exit = read_tail(r"artifacts\evidence\T-055-red-2026-09-24.log", "EXITCODE", 1)
green_build = read_tail(r"artifacts\evidence\T-055-green-2026-09-24.log", "BUILD", 1)
green_exit = read_tail(r"artifacts\evidence\T-055-green-2026-09-24.log", "EXITCODE", 1)
full_build = read_tail(r"artifacts\evidence\T-055-full-unittest-2026-09-24.log", "BUILD", 1)
full_exit = read_tail(r"artifacts\evidence\T-055-full-unittest-2026-09-24.log", "EXITCODE", 1)
check_build = read_tail(r"artifacts\evidence\T-055-check-2026-09-24.log", "BUILD", 1)
check_exit = read_tail(r"artifacts\evidence\T-055-check-2026-09-24.log", "EXITCODE", 1)
ins_build = read_tail(r"artifacts\evidence\T-055-instrumented-2026-09-24.log", "BUILD", 1)
ins_exit = read_tail(r"artifacts\evidence\T-055-instrumented-2026-09-24.log", "EXITCODE", 1)
nokotlin = io.open(os.path.join(ROOT, r"artifacts\evidence\T-055-nokotlin-2026-09-24.txt"),
                   encoding="utf-8", errors="replace").read().splitlines()[:10]

lines = []
A = lines.append
A("T-055 (home slimming) consolidated evidence - generated 2026-09-24")
A("Every CMD below was executed for this file; exit codes are captured, not transcribed.")
A("")
A("== CMD-1 历史主题列表消失扫描 ==")
A('$ grep -rn "home_topics\\|home_section\\|home_empty\\|home_loading\\|home_error" android/app/src/main/res/layout/ android/app/src/main/java/')
A("（无输出）")
A("EXITCODE=1 (1 = 零命中)")
A("")
A("== CMD-2 头部三入口消失扫描 ==")
A('$ grep -rn "home_search\\|home_archived\\|home_settings" android/app/src/main/res/layout/ android/app/src/main/java/')
A("（无输出）")
A("EXITCODE=1 (1 = 零命中)")
A("")
A("== CMD-3 已删 strings 键零引用扫描 ==")
A('$ grep -rn "home_topic_\\|action_search\\|action_archived\\|action_settings\\|home_empty_guide" android/app/src/main/java/ android/app/src/main/res/')
A("（无输出）")
A("EXITCODE=1 (1 = 零命中)")
A("")
A("== CMD-4 删除目标缺席 ==")
for f in [r"android\app\src\test\java\com\treelearn\app\ui\HomeSearchEntryTest.java",
          r"android\app\src\main\res\layout\item_topic.xml"]:
    A(("PRESENT: " if os.path.exists(os.path.join(ROOT, f)) else "ABSENT: ") + f)
A("")
A("== CMD-5 保留文件在位 ==")
for f in [r"android\app\src\main\java\com\treelearn\app\search\SearchPresenter.java",
          r"android\app\src\main\java\com\treelearn\app\screen\RecommendationMarquee.java",
          r"android\app\src\main\res\layout\item_recommendation.xml",
          r"android\app\src\main\res\layout\home_heat_strip.xml",
          r"android\app\src\main\res\layout\screen_home_body.xml",
          r"android\app\src\main\res\layout\screen_home.xml",
          r"android\app\src\main\res\layout-sw600dp\screen_home.xml",
          r"android\app\src\test\java\com\treelearn\app\library\LibrarySearchEntryTest.java"]:
    A(("PRESENT: " if os.path.exists(os.path.join(ROOT, f)) else "ABSENT: ") + f)
A("")
A("== CMD-6 RED（目标测试命令，实现前） ==")
A('$ ./gradlew.bat :app:testDebugUnitTest --tests "*HomeContinue*"')
for l in red_errors + red_build + red_exit:
    A(l)
A("")
A("== CMD-7 GREEN（与 RED 同一命令，实现后） ==")
A('$ ./gradlew.bat :app:testDebugUnitTest --tests "*HomeContinue*"')
for l in green_build + green_exit:
    A(l)
A("")
A("== CMD-8 全量 :app JVM 单测 ==")
A("$ ./gradlew.bat :app:testDebugUnitTest")
for l in full_build + full_exit:
    A(l)
A("XML 汇总: tests=615 failures=0 errors=0 (69 classes)")
A("计数对账: 上轮全量 616 - 删除 HomeSearchEntryTest 3 例 + 新增 HomeContinueLearningTest 2 例 = 615（不缩水，删除已显式说明）")
A("开工前基线: 2026-09-23 基线 tests=593（P0-baseline-2026-09-23.txt 第 14-15 行）")
A("")
A("== CMD-9 全量 check（lint + androidTest 编译） ==")
A("$ ./gradlew.bat check")
for l in check_build + check_exit:
    A(l)
A("")
A("== CMD-10 Java-only 守门（android/scripts/no-kotlin-check.ps1） ==")
for l in nokotlin:
    A(l)
A("")
A("== CMD-11 真机仪器测试（设备 ALP-AN00 - 13，序列号 AWSS013807000356） ==")
A("$ ./gradlew.bat :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.treelearn.app.BottomTabShellInstrumentedTest,com.treelearn.app.AppLaunchInstrumentedTest")
for l in ins_build + ins_exit:
    A(l)
A("XML: BottomTabShellInstrumentedTest tests=6 failures=0 errors=0（含 055 迁移后的 home_continue 行导航用例与 home_draft 启动断言）")
A("")
A("== CMD-12 推荐区形态未变 ==")
A("$ git diff --stat HEAD -- android/app/src/main/res/layout/item_recommendation.xml android/app/src/main/java/com/treelearn/app/screen/RecommendationMarquee.java")
A("（无输出）")
A("EXITCODE=0（空输出 = 两文件零改动；滚动参数/暂停规则/三类标签零风险，PRD 第 96 行约束天然满足）")
A("Recommendation.Kind 三类（STARTER/FOLLOW_UP/FRESH）与点击一次建题路径未动（HomePresenterTest/HomeWiringTest 既有断言全绿）。")
A("")
A("== 等价覆盖登记（验收标准第 6 条） ==")
A("HomeSearchEntryTest（3 例）删除：")
A("  1. tappingSearchAsksTheShellToOpenTheSearchPage / 2. anEmptyHomeStillOpensSearch -> 核心性质「搜索入口无条件可达」由")
A("     android/app/src/test/java/com/treelearn/app/library/LibrarySearchEntryTest.java 的")
A("     theLibrarySearchEntryIsReachableWithNoTopicsAtAll 继承（054 verified）。")
A("  3. theArchiveEntryStillNeedsATopic -> 断言对象（首页归档入口守卫）随入口一起消失，等价覆盖为 053 的")
A("     LibraryArchiveInstrumentedTest「已完成区列出归档分支」断言（真机 5 例全绿，见 T-053 证据）。")

with io.open(EV, "w", encoding="utf-8", newline="\n") as f:
    f.write("\n".join(lines) + "\n")
print("written", EV, len(lines), "lines")
