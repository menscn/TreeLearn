# Web 轻量学习工作区：真实浏览器验收证据（2026-10-21）

本目录是 Harness 片 046 的证据包。全部截图与读数来自本次构建的真实浏览器会话，不是示意稿或单测推断。

## 环境与入口

- 应用入口：`http://127.0.0.1:5199/`（Vite dev server，本次会话运行）。
- 浏览器：`C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe`，headless Edge + CDP。
- 视口：桌面 1440×900；窄窗 390×844（`Emulation.setDeviceMetricsOverride`）；滚动溢出另用 1440×520。
- 驱动脚本：`artifacts/tmp/probe-acceptance-046.mjs`（scratch，不入库）；读数原始值见 `readings.json`。
- 设计参照（只读，未改）：HTML `da1b4e961c44538bda13e411c89bb0697e692904aab038af9ccf4eb4eb289d6f`；说明 MD `b7f7935180698a45fd3599d7c8a557dad0ba562ac1577fe59a99ecd771743c65`（SHA-256）。

## 守门读数（本次构建）

| 命令 | 结果 |
|---|---|
| `pnpm --dir frontend lint` | 退出码 0；tsc 0 错；依赖方向 35 文件 0 发现；设备时间 0 发现；用户可见文案 0 条绕过 `src/copy`；夹具同步 13/13、bad=0、redacted=0 |
| `pnpm --dir frontend test` | 16 文件 / 107 例全部通过 |
| `pnpm --dir frontend verify` | 16 步 ok=16 bad=0 fault=0，`VERIFY PASS` |
| `python -X utf8 .codex/scripts/dev/check_design_tokens.py` | OK=10 FAIL=0；44 token 四方一致；`frontend/src` 52 文件裸色值 0 命中 |
| `python -X utf8 .codex/scripts/validate_tasks.py` | 41 文件 errors=0（13 条行数软上限 warning） |

## 浏览器读数（22/22 通过）

| 场景 | 实际读数 |
|---|---|
| 桌面主页布局 | 页宽 960、侧栏 240、无横向溢出、品牌为 SVG |
| 空格折叠/展开侧栏 | 折叠后 `data-nav=collapsed`、`aria-expanded=false`、宽 76；再按空格恢复 240 |
| 主题 0 对话 | 标题含「注意力权重」、回答正文 2156 字、3 条建议 |
| 正文独立滚动 | 正文滚到 600，顶栏与底部输入区 y/底边坐标不变 |
| 中文输入法 | `compositionstart=1`、`compositionupdate=2`、`compositionend=1`，提交后草稿为「中文草稿」，视图仍为 `#/chat/0`，无额外动作 |
| 复制整条回答 | 页面状态 `copied`；`navigator.clipboard.readText()` 读到 2156 字，开头为「核心先说结论」 |
| 宽窗分支面板 | `position:static`、宽 320、左缘 1120 = 正文右缘 1120（并排）、遮罩 `display:none`、焦点在关闭按钮 |
| Esc 关闭分支面板 | 面板隐藏，焦点回到 `#branch-toggle` |
| 知识树缩放 | 放大夹在 150%、缩小夹在 50%、适应回 100%；Esc 关闭后焦点回到 `#tree-toggle` |
| 浏览器后退/前进 | back 回主页、forward 回对话；草稿仍在、阅读位置 600 恢复 |
| 学习库默认 | 两行主题、无空态 |
| 学习库搜索/筛选/展开 | 查询「关键头」命中 1 行；「进行中」按下；摘要展开且 `aria-expanded=true` |
| 学习库滚动恢复（1440×520） | 列表 `scrollHeight 570 > clientHeight 444`，滚到可滚上限 126；去主页再回，仍为 126，筛选仍为「全部」 |
| 设置分类＋详情只读 | 选中 `search`、详情同步、只读说明存在、动作全 disabled、`role="switch"`=0 |
| 设置恢复（1440×520） | 搜索服务详情 `scrollHeight 816 > clientHeight 444`，滚到 140；去主页再回仍为 140，分类仍为 `search` |
| 窄窗设置 | 侧栏自动收起；分类仍为 `search`；分类条可横向滚动；页面无横向溢出 |
| 窄窗对话 | 草稿「中文草稿」保留；无页面横向溢出 |
| 窄窗分支面板 | `position:absolute`、宽 340、右缘贴 390 视口、遮罩 `display:block`、焦点在关闭按钮 |
| 窄窗 Esc 关闭 | 面板隐藏，焦点回 `#branch-toggle` |
| 窄窗学习库 | 两行、筛选「全部」仍按下、无页面横向溢出 |
| 宽度往返 | 回到 1440×900 后仍在学习库、两行、筛选保持、页宽恢复 960 |

## 截图

| 文件 | 场景 |
|---|---|
| `desktop-home.png` | 1440×900 主页：侧栏、根问题输入、推荐、历史主题 |
| `desktop-home-nav-collapsed.png` | 键盘（空格）折叠侧栏，含 `:focus-visible` 焦点环 |
| `desktop-chat.png` | 1440×900 对话：居中限宽正文、稳定输入区、草稿 |
| `desktop-branch-panel.png` | 宽窗分支导航与正文并排（320 px） |
| `desktop-tree.png` | 知识树覆盖层：真实根节点、100% 缩放、四个工具栏 |
| `desktop-library.png` | 学习库扫描型列表、搜索、状态筛选、摘要收起 |
| `desktop-library-filtered.png` | 搜索「关键头」＋「进行中」＋摘要展开 |
| `desktop-settings.png` | 设置「分类＋详情」，搜索服务只读详情 |
| `narrow-chat.png` | 390×844 对话顶部、草稿与固定输入区 |
| `narrow-branch-overlay.png` | 390×844 分支面板覆盖态与遮罩 |
| `narrow-library.png` | 390×844 学习库；筛选与展开状态保留 |

## 与示意稿的必要差异

- 分支面板与知识树只用夹具能证明的根分支/根节点；示意稿里的演示子节点没有搬入。
- 发送、附件、重置、新建分支、设置保存/删除/替换/开关/试听全部 `disabled`，可访问名与 `title` 标注「尚未接通」；示意稿的演示保存/生成没有进入生产。
- 设置分类行在示意稿的纯名称基础上加了摘要与 chevron，这是 Android 设置列表的真实信息结构；分类名仍是文字，未图标化。
- 主题 1 没有对话夹具，进入后显示「该主题还没有对话基线」；不复用主题 0 的回答。
- 复制是真实能力（真实写入剪贴板）；草稿可写但不发送。
- 图标来自 `android/app/src/main/res/drawable/ic_*.xml` 的同源副本，未复制示意稿的 SVG，也未引入图标依赖。
- 旧快照屏（`SnapshotScreen`/`ScreenRouter`）保留为 parity 证据路径，不在主路径上。

## 仍未接通 / 未验证

- 真实 AI 生成与流式回答、附件上传与删除、分支创建/切换、设置写入与校验、语音试听。
- 主题 1 的对话与分支内容（夹具未采集）。
- 刷新后的草稿/筛选/位置持久化（仅内存 + hash 视图/主题）。
- 后端、账号、同步、遥测。
- 平板 WebView/WebKit 第二环境、读屏器实测、真实 IME 应用（本次用 CDP composition 事件模拟）。
