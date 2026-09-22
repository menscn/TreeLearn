# 知芽 TreeLearn

> **本地优先的树状 AI 学习助手 —— 让每一次追问都长在它该在的位置。**
>
> 简体中文 | [English](README_EN.md)

[![Platform](https://img.shields.io/badge/platform-Android%20API%2028%2B-3DDC84?style=flat-square)](android/)
[![Language](https://img.shields.io/badge/language-Java%2017-orange?style=flat-square)](android/)
[![License](https://img.shields.io/badge/license-Proprietary%20/%20All%20Rights%20Reserved-red?style=flat-square)](LICENSE)

---

## 这是什么

在普通 AI 聊天工具里学习一个陌生领域时，你大概遇到过这个情况：

> 提出 q1，读到回答 a1，产生疑问 q2；顺着 q2 一路探究下去，回过神来 a1 已经被挤到很远的上面去了，再也找不到当时的阅读位置。

线性会话会把不同回答衍生的追问按时间混排，主线学习与临时探索互相打断，上下文也不再专一。

TreeLearn 的做法是：**把对话变成一棵树**。从一个根问题开始，你可以在任意一个"稳定回答"上长出一条独立分支，分支拥有自己的隔离上下文；探索完再回到来源位置继续读。每条分支还可以继续分叉，形成真正反映你思考路径的结构。

它同时是一款 **local-first（本地优先）** 应用：没有后端、没有账号、没有云同步。所有数据都在你自己手机的 SQLite 里，DeepSeek API Key 只经过 Android Keystore 加密保存。

> **项目状态：早期开发中（WIP）。** 领域层 `:core` 已落地 17 个核心类型，`:app` 与 `:data` 处于骨架阶段，`:ai` 与 `:tree-layout` 尚未填入源码。请勿作为可用产品期待。

---

## 核心概念

| 概念 | 含义 |
| :--- | :--- |
| **主题（Topic）** | 一棵完整的对话树。主题的显示身份直接复用**根分支标题**，不额外维护主题名——同一棵树永远只有一套名字。 |
| **分支（Branch）** | 树上的一条独立对话路径。根分支不可归档；非根分支可以整体归档或删除。 |
| **稳定回答（Stable Answer）** | 生成完成的回答版本。**只有稳定回答可以作为分叉来源**，部分回答只读，不可分叉。 |
| **创建快照（Context Snapshot）** | 分支诞生时被冻结的那段上下文背景。此后父分支的新内容**不会**回流污染子分支。 |
| **思考模式（Thinking Mode）** | DeepSeek 推理内容默认折叠展示、一并持久化，并计入后续上下文。 |

---

## 功能范围

当前共 21 项 P0 功能（F01–F21），全部来自已冻结的需求基准，详见 [PRD V3.1](Docs/03-prd-draft/V3.1-知芽（TreeLearn）产品需求文档（PRD）.md)。

| 分组 | 功能 |
| :--- | :--- |
| **树的形成与身份** | 多主题生命周期与首次创建原子性 · 根标题即主题身份 · 以稳定回答为来源的独立对话树 · 分层上下文与创建快照 |
| **树的浏览与找回** | 聚焦阅读与现场恢复 · 移动端逐层导航 · 树状总览画布（枝长随对话轮数增长）· 标题/摘要/搜索 · 未读状态 · 非根子树归档与删除 |
| **AI 与容错** | 生成状态与分叉资格 · 结合树轮廓的推荐问题 · 本机数据与 DeepSeek 配置 · AI 与网络失败可恢复 · AI 辅助生成降级 |
| **V3.1 新增** | 首页信息架构统一 · AI 推荐学习主题（弹幕滚动）· 模型选择与思考模式 · 划词操作集与引用追问 · 回答内容级操作与 AI 总结 · 自适应学习导师提示词 |

---

## 技术架构

```text
:app          ->  Activity / XML 视图 / 导航 / 依赖装配
:data  :ai    ->  :core
:core         ->  领域模型（纯 JVM，不引用 android.*）
:tree-layout  ->  树几何计算（纯 JVM）
```

| 模块 | 职责 | 明确禁止 |
| :--- | :--- | :--- |
| `:app` | Activity、XML 视图、导航、屏幕控制器、应用级生成协调器 | 不写 SQL，不拼 DeepSeek JSON |
| `:core` | 领域实体、状态机、用例、仓储与 AI 网关接口、供应商无关的提示词契约 | 不依赖 Android / SQLite / HTTP / Kotlin |
| `:data` | SQLite schema/DAO/事务/迁移、搜索、阅读状态、Keystore 密钥 | 不决定业务流程，不调用 DeepSeek |
| `:ai` | DeepSeek HTTPS/SSE、上下文编码、版本化提示词注册表 | 不操作 View / 数据库 |
| `:tree-layout` | 树几何、标签避碰、命中测试、视野拟合 | 不读数据库，不持久化像素坐标 |

### 工具链与约束

| 项 | 值 |
| :--- | :--- |
| Gradle / AGP | 8.14.3 / 8.13.2 |
| JDK | 17（源码与字节码级别同为目标 17） |
| compileSdk / targetSdk | 36 |
| minSdk | 28 |
| 语言 | **Java-only** |
| 第三方依赖（主代码） | **零** |

**为什么是 Java-only —— 这不是偏好，是硬约束。** 团队约定 App 主代码不引入任何 Kotlin 字节码，理由是 APK 体积与运行期可预测性。为此项目自带机械化守门：静态扫描 + Gradle 依赖报告 + **直接扫描构建产物 APK 的 dex 字符串表**。

之所以要做到 dex 级别，是因为这个坑装过一次：`androidx.test:monitor:1.6.1` 的 POM **完全不声明**任何 Kotlin 依赖，依赖图是干净的，但它的 `OutputDirCalculator` 等 2 个类确实是 Kotlin 编译产物，设备上会直接抛 `ClassNotFoundException: kotlin.jvm.internal.Lambda`。只看依赖图发现不了。

同理，主代码也避开了 AndroidX：`appcompat`、`core`、`test` 系列都会传递引入 `kotlin-stdlib`，因此主代码直接使用平台 API。实测 `app-debug.apk` 约 70 KB、3 个 dex、零第三方依赖、零 Kotlin 字节码。

约束的最终依据是硬件实测而非文档声明，验证方式见 [`android/README.md`](android/README.md)。

---

## 快速开始

```bash
# 1. 指定 JDK 17（Gradle 启动 JVM 可用 17 或 21，编译固定用 Java 17 toolchain）
export JAVA_HOME=/path/to/jdk17

# 2. 配置 Android SDK 路径
echo "sdk.dir=/path/to/Android/SDK" > android/local.properties

# 3. 构建
cd android
./gradlew :app:assembleDebug

# 4. 全部检查（含依赖方向守门与 dex 级 Java-only 校验）
./gradlew check
```

Android 常用命令、Release 签名与 DeepSeek 密钥配置见 [`android/README.md`](android/README.md)。

> **Release 签名说明**：keystore 存放于仓库之外，凭据只从进程环境变量读取（`TREELEARN_KEYSTORE_PATH` 等），其次读取已被 gitignore 的 `android/keystore.properties`。密钥与口令不进入仓库、日志、APK 或交付报告。

---

## 目录结构

```text
Treelearn/
├─ android/               Android 客户端（Gradle 多模块工程）
│  ├─ app/                UI 层
│  ├─ core/               领域层（纯 JVM）
│  ├─ data/               本地持久化
│  ├─ ai/                 DeepSeek 网关
│  ├─ tree-layout/        树布局计算
│  └─ scripts/            守门与开发脚本
├─ Docs/                  需求 / PRD / UX / 技术设计文档
├─ artifacts/             守门与验收证据
└─ .trae/                 Harness 工程规则、角色与任务
```

| 文档 | 内容 |
| :--- | :--- |
| [`Docs/01-demand/`](Docs/01-demand) | 结构化需求基准说明书 V3.0 / V3.1 增量 |
| [`Docs/03-prd-draft/`](Docs/03-prd-draft) | 产品需求文档 V3.1 |
| [`Docs/04-ux-design/`](Docs/04-ux-design) | 高保真 UX 原型 + [设计系统 token](Docs/04-ux-design/design-system)（Figma 与 Android 共用同一份数值） |
| [`Docs/05-technical-design/`](Docs/05-technical-design) | 本地优先技术架构、提示词设计、SQLite 方案 |
| [`android/README.md`](android/README.md) | 构建、守门、模块契约、常用命令 |

---

## 工程方法

本仓库采用 **Harness** 工程流程（`AGENTS.md` + `.trae/`）：需求先澄清确认并冻结边界，再按 <= 400 行预算拆任务，实施强制 TDD 与"RED → GREEN"证据留痕，最后由独立 verifier 按专项 guard 验收。

- `.trae/rules/` —— 按栈划分的规则与底线（Android / 后端 / SQL / 契约 / 共享状态 / 可观测性）
- `.trae/tasks/` —— 任务契约文件，机械校验器 `.trae/scripts/validate_tasks.py`
- `artifacts/evidence/` —— 每次守门与验收的执行证据（自带命令行与退出码）
- `.agents/skills/` —— 仓库级技能，Codex 自动发现；当前含 `figma-build`、`figma-codegen`（从 figwright 0.5.0 原样迁移）
- 设计协作 —— Figma 通过 figwright MCP 接入：一次性配置写进用户级 `~/.codex/config.toml`，Figma 桌面端导入插件 manifest；连接自检 `node .trae/scripts/dev/probe_figwright_mcp.mjs`，设计 token 三方一致性守门 `python .trae/scripts/dev/check_design_tokens.py`。约定与故障速查见 [`.trae/rules/ux-design.md`](.trae/rules/ux-design.md)

---

## 许可证

> **本项目不是开源项目，未授予任何开源许可证。**

代码受版权保护，未经书面许可不得使用、复制、修改、分发或用于商业用途。仓库的公开或私有状态**不构成**默示授权。完整条款见 [`LICENSE`](LICENSE)。

第三方依赖仍受其各自许可证管辖，本声明不覆盖这些组件。

---

Copyright (c) 2026 TreeLearn Project. All rights reserved.
