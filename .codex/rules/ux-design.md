# UX 设计与 Figma 联动

读取时机：`Docs/04-ux-design/**` 分析、改稿前；把设计落到 Figma 或从 Figma 取数前；完成前的设计证据核对。仅加载本任务命中的规则。

## 设计事实源

- 设计系统的**当前态权威**是 `Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的 `:root` 区块（这条链的权威定义在 `Docs/04-ux-design/design-system/README.md`）；
  `Docs/04-ux-design/design-system/tokens.json` 是同一批数值的机器可读镜像；
  `android/app/src/main/res/values/colors.xml` 是 Android 侧副本（只承载不透明 hex 子集）；
  `frontend/src/styles/tokens.css` 是前端侧副本，**只能生成、不得手抄**。
  token 与资源的**个数不在本文写死**（此处曾长期留着一个与实测不符的旧数字）——以守门输出的 `measured_*` 为准。
- 四方一致性只认脚本，不认人眼：

  ```bash
  python .codex/scripts/dev/gen_design_tokens_css.py            # 由 tokens.json 生成/刷新前端 CSS
  python .codex/scripts/dev/gen_design_tokens_css.py --check     # 只判「已落盘的是否等于重新生成」
  python .codex/scripts/dev/check_design_tokens.py              # 四方逐值比对 + 前端裸色值守门
  python .codex/scripts/dev/check_design_tokens.py --selftest    # 守门自身的正反双向自检
  ```

- 改设计的顺序固定：**先改原型 `:root` → 同步 `tokens.json` → 同步 `colors.xml` → 重新生成 `tokens.css` → 跑守门脚本**。任何一处单独改都会让守门变红；直接编辑 `tokens.css` 也算单独改。
- 数值只能引用 token：Figma 节点不得写裸 hex/px，XML 布局不得出现临场数字。档位扩充要先落到原型 `:root`——间距只用 `s1/s2/s3/s4/s6/s8`（4pt 基准），圆角只用 `r-sm/r-md/r-lg/r-full`，阴影只用 `sh-1/sh-2`，字号只用 `fs-*`。

## Figma 链路（figwright MCP + 两个技能）

一次性安装（详细命令见本节末）：

1. **MCP server**：已写入用户级 `~/.codex/config.toml` 的 `[mcp_servers.figwright]`（`npx` 启动，版本**锁定 0.5.0**，与 Figma 端插件同版本）。改配置后需重启 ChatGPT/Codex 客户端才生效。
2. **Figma 插件**：Figma 桌面端 → Plugins → Development → Import plugin from manifest… 选 `figwright-plugin-v0.5.0/manifest.json`，运行插件；面板显示 Connected 才算接上。
3. **技能**：`.agents/skills/figma-build`（描述/代码 → 画布）、`.agents/skills/figma-codegen`（画布 → 代码）。Codex 从仓库根的 `.agents/skills` 自动发现，无需额外安装。

连接自检：调用 `ping` 工具，返回中 `plugin` 字段非 null 才算真正接通；`sessions.connectedCount = 0` 表示 Figma 侧插件没在运行，不要在这种情况下开始改稿。

故障速查：

- relay 默认端口 **3055**（可用环境变量 `FIGWRIGHT_PORT` 改）；同一时刻只允许一个 server 当 leader，重复启动的第二个进程会变成 follower。
- server 与插件之间是本机私有协议（实测 protocol 0.1.0）：**两端版本必须一致**。升级一端必须同时升级另一端，不要只把 `config.toml` 里的版本号往上抬。
- 首次启动需联网下载依赖（实测冷启动 11.5s、热启动 2.7s），已把 `startup_timeout_sec` 放宽到 120s。

## 设计质量底线

设计"不好看"多数不是审美问题，而是这几条没被约束：

- **层级**：一屏只允许一个主标题层级；字号只取 `fs-*` 档位，不出现临场值。`display` 与 `cap` 的对比是这套系统的性格，不要压平。
- **间距**：同屏同级别元素用同一档间距，所有间距落在 4pt 网格上；不要用"看起来差不多"的 14/18/22。
- **中文排版**：正文行高按 `lh-read`（32px）；标题避免尾行只剩一两个字，靠文案长度与 `maxLines` 控制，不要指望平台自动避孤字。
- **对比度**：正文/次要文字与纸感底色按 WCAG AA 核对（正文 4.5:1，大字 3:1）；`ink3` 只用于非关键信息。
- **触达**：可点击区域不小于 44×44 dp。
- **深浅**：目前只有浅色纸感主题，不要引入未定义的暗色或纯黑阴影。
- **验证闭环**：每改完一块用 `get_screenshot` 复看（溢出、裁切、错位、档位不一致），发现问题就改到干净为止；`empty: true` 表示该节点没渲染出内容。

## 证据

- 改稿后用 `get_screenshot` / `save_screenshots` 导出被改节点，落到 `artifacts/evidence/`，文件名带任务或范围标识。
- 证据文件必须自带生成时间、命令、被核对象与关键判据行；写进任何文档的数字都要说明"从哪个文件读到"，不允许凭记忆写。
- 设计稿与生产代码不一致时：**生产代码是事实，设计稿是意图**。记录偏差并单独路由修复，不静默改稿、不静默改代码。

## 边界

- 只改本项目对应的 Figma 文件；不要借 MCP 修改其它项目的画布。
- Figma 是设计表达层，不产出生产代码：Android 侧仍受 Java-only 与零第三方依赖约束，不得因设计需要引入 Kotlin/AndroidX 或新依赖。
- 设计文档属当前态文档，改稿要同步 `Docs/04-ux-design/**` 的说明文字，不要在文档里复制一份 token 数值清单——只指向 `tokens.json` 与守门脚本。