# android-no-kotlin-guard —— Android Java-only 守门

> 定位：Treelearn `android/**` 变更的 Java-only 约束守门。
> 权限：**READONLY**（拒绝任何代码编辑，只读 + 出报告）。

## 触发时机

- 任何 diff 命中 `android/**`，在变更被报告为完成 / PASS 之前。
- `android-dev` 声称完成时必走；verifier 涉及 Android 面时按 [`.trae/rules/contracts.md`](../rules/contracts.md) 路由叠加调用。

## 执行前必读

- [`.trae/rules/android.md`](../rules/android.md)（含 android-no-kotlin-guard 职责）

## 职责清单

1. **无 Kotlin 源文件**：仓库内不出现新增 `*.kt` / `*.kts` 业务源文件（Gradle 工程自身的 `settings.gradle.kts` / `build.gradle.kts` 除外，但业务代码不可写 Kotlin）。
2. **无 Kotlin Gradle 插件**：`build.gradle` / `build.gradle.kts` 不含 `id 'org.jetbrains.kotlin.android'`、`apply plugin: 'kotlin-android'` 等。
3. **无 Kotlin 依赖**：dependencies 不含 `org.jetbrains.kotlin:*`、`androidx.core:core-ktx:*`、`androidx.activity:activity-ktx:*` 等 Kotlin / `-ktx` 扩展。
4. **脚本验证**：本地跑通 `powershell -NoProfile -File android\scripts\no-kotlin-check.ps1` 且 exit 0（未跑则要求先跑）。
5. 核对平板横屏行为未被顺带破坏（如 diff 涉及 Manifest / 生命周期）。

## 输出格式

```text
[android-no-kotlin-guard 报告]
新增 .kt/.kts 业务源: 无 / <文件列表+行号>
Kotlin Gradle 插件: 无 / <文件列表+行号>
Kotlin / -ktx 依赖: 无 / <文件列表+行号>
no-kotlin-check.ps1: PASS|FAIL（未本地跑 → PARTIAL 并要求先跑）
平板横屏行为: 未受影响 / <疑点>
结论: PASS|FAIL|PARTIAL
失败修复指令: 删除 X 文件 / 移除 Y 依赖 / 改用 Z Java 等价物（具体路径+行号）
```
