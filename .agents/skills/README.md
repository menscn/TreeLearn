# 仓库级技能（Codex 自动发现）

本目录存放**从上游原样迁移**的第三方技能。Codex 会扫描仓库根与当前目录向上每一级的 `.agents/skills`，
所以放在这里不需要任何安装步骤，任何在本仓库内启动的会话都能直接加载。

## 当前内容

| 技能 | 用途 | 何时被触发 |
| :--- | :--- | :--- |
| [`figma-build/`](figma-build/SKILL.md) | 把描述或代码**写进** Figma 画布：复用目标文件已有的组件 / 变量 / 样式，只补真正缺的那部分 | 「在 Figma 里做这个」「把这个界面搬到 Figma」「先出个设计稿」 |
| [`figma-codegen/`](figma-codegen/SKILL.md) | 把 Figma 选中内容**读成**与项目技术栈一致的代码，复用已有组件与 token | 「把这个设计转成代码」「实现这个 frame」 |

两者都只是编排层，**必须**有已连接的 `figwright` MCP server（配置在用户级 `~/.codex/config.toml`）
才有工具可用。连接的判断方式是调用 `ping`，返回里 `plugin` 非 null 才算真的接上。

## 来源与版本

- 上游：`awdr74100/figwright`（MIT），版本 **0.5.0** —— 与 Figma 端 Figwright 插件、MCP server 同版本。
- 迁移方式：目录整体复制，**保持逐字节一致**，便于用上游重新核对。核对与自检：
  `powershell -ExecutionPolicy Bypass -File artifacts/evidence/.gen-figma-toolchain.ps1`
  （其中「Migrated skills match upstream byte-for-byte」小节会打印每个文件的 sha256 与比对结果）。
- 不要在本目录里直接改技能内容：改了就和上游分叉，以后无法用哈希核对。需要调整时改上游副本、重新复制，
  或在 `.trae/rules/ux-design.md` 里补充**项目自己的**约束。

## 升级步骤

1. 升级 Figma 端插件（`Plugins → Development → Import plugin from manifest…`）与 `~/.codex/config.toml`
   里的 `@figwright/mcp@<版本>`，**两端必须同版本**。
2. 用新版本覆盖本目录下的两个技能目录。
3. 重跑上面那条证据脚本，确认哈希比对仍为 `bytes_identical=True`。

## 与项目规则的关系

使用这些技能的**项目约定**（设计事实源、token 绑定、设计质量底线、证据要求）写在
[`../../.trae/rules/ux-design.md`](../../.trae/rules/ux-design.md)，本文件只记录来源与升级方式。