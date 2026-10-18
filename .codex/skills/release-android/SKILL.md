---
name: release-android
description: Treelearn 本地 Android release 构建、Java-only 守门与 APK 体积检查。
---

# release-android

用户请求打 release、出 APK 或发版时使用。该技能只负责本地构建、验证和报告，不自动签名、上传、提交或推送。

## 流程

1. 在 `android/` 运行项目支持的 Gradle release 构建。
2. 运行 `android/scripts/no-kotlin-check.ps1`，确认无 Kotlin 源码、插件或依赖。
3. 检查 release APK 是否存在、大小是否在项目当前规格允许范围内。
4. 需要设备验证时启动实际 APK，记录初始 Activity 和关键冒烟结果。
5. 把命令、环境、结果和未验证项写入当前 Task 的 Verification report。

## 边界

- 不修改生成的 Web 资源镜像。
- 不把构建成功当作 Java-only 或设备行为验证。
- 不自动处理签名密钥、渠道发布或远程构建；若项目后续新增受控远程构建能力，应另立 Treelearn 专属流程。
