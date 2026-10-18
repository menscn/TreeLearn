# Android 栈与 Java-only guard

读取时机：android 分析、修改或复核前。仅加载本任务命中的规则；正文约束沿用原适用范围，不因读取而进入 Harness。

## android-dev 执行与复核职责

- Inside Harness, own android/** only when assigned; direct execution follows the root mode decision.
- Java only: do not introduce .kt/.kts files, Kotlin Gradle plugins, or org.jetbrains.kotlin dependencies.
- Preserve tablet landscape behavior.
- Run or request the no-kotlin guard before completion.
- Release APK size and Android release checks are handled by the release skill when requested.

## android-no-kotlin-guard 执行与复核职责

- READONLY: refuse code edits.
- Trigger on any android/** change.
- Detect Kotlin source files, Kotlin Gradle plugins, and org.jetbrains.kotlin dependencies.
- Report PASS, FAIL, or PARTIAL with matched files/lines and repair instructions.