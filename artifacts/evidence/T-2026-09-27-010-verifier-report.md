# T-2026-09-27-010 独立只读 verifier 报告（更新）

- 复核日期：2026-09-28
- 复核角色：独立只读 verifier
- 复核范围：activity_main.xml shell_backdrop 层级、首页手机/平板去重、chat/navigation/tree 背景遮挡、PaperRuleBackdrop 职责说明、HomeBackdropInstrumentedTest 覆盖，以及本轮透明标签栏修复。
- 执行约束：未修改生产代码、测试代码或任务文件；未运行整包长测试。
- 静态证据：D:\Project\Android\Treelearn\artifacts\evidence\T-2026-09-27-010-verifier-static.txt
- 定向运行：HomeBackdropInstrumentedTest，3 tests / 0 failures / 0 errors / 0 skipped，退出码 0。

## 本轮修复结论：ACCEPT

针对用户本轮指定的修复项，可以 ACCEPT：

- phone 与 sw600dp screen_tab_bar.xml 的外层背景均已改为 @android:color/transparent。
- HomeBackdropInstrumentedTest 新增并实际执行了 tab_bar 透明、screen_home 无第二层 PaperRuleBackdrop、chat/navigation/tree 根布局不使用不透明 tl_bg 的断言。
- 定向 instrumentation 的 3 个用例全部通过。
- activity_main.xml 的唯一 shell_backdrop 层级、首页两套布局去重、路由根布局透出、PaperRuleBackdrop 职责说明仍保持通过。

## 证据细节

- D:\Project\Android\Treelearn\android\app\src\main\res\layout\screen_tab_bar.xml:16-21：phone tab_bar 使用透明背景并保留顶部 hairline。
- D:\Project\Android\Treelearn\android\app\src\main\res\layout-sw600dp\screen_tab_bar.xml:9-14：tablet tab_bar 使用透明背景并保留顶部 hairline。
- D:\Project\Android\Treelearn\android\app\src\androidTest\java\com\treelearn\app\HomeBackdropInstrumentedTest.java:69-94：新增透明度、重复 backdrop 和不透明根背景断言。
- D:\Project\Android\Treelearn\android\app\build\outputs\androidTest-results\connected\debug\TEST-ALP-AN00 - 13-_app-.xml：本轮定向 XML，3/3 通过。

## 剩余边界：任务整体仍为 PARTIAL

若按 T-2026-09-27-010 的全部验收标准，而不是本轮指定修复项，任务仍应保持 PARTIAL，不建议仅凭本轮定向测试改为 verified：

- HomeBackdropInstrumentedTest 的 screen_home 去重断言使用 phone target context；没有直接在 sw600dp context inflate screen_home。源码静态检查已确认 phone/sw600dp 两套布局都没有 PaperRuleBackdrop，因此这是覆盖形状的轻微缺口，不是当前生产故障。
- 本轮没有生成修复后的手机/平板全页面截图；旧的 android-shell-backdrop-2026-09-27.png 早于透明标签栏修复，不能作为 post-fix 截图证据。
- 本轮遵守要求未重跑整包长测试，因此不重新裁决既有整套设备测试的历史失败，也不把它们归因到本修复。

## 建议

- 本轮修复可继续进入后续任务；若要把 T-010 整体标记 verified，补一张修复后的 phone 与 sw600dp shell 截图，并可选地让测试在 sw600dp context 直接 inflate screen_home。

## 文件与改动安全

- 本次只读源码、布局、测试源和定向 XML 结果，并更新 artifacts/evidence 下的 verifier 报告与静态证据。
- 未修改任何 android/** 生产文件、测试源文件或 .trae/tasks/*.md。
