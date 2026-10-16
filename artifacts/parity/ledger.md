# 1:1 台账（生成物，勿手改）

- 生成命令：`python -X utf8 .trae/scripts/dev/gen_parity_ledger.py`
- 本次实际参数：(无，全部走默认路径)
- 清单登记来源提交：754e86331a44fd44bb9c1540b4d0e8f0b34002b0；本行现场读到的 HEAD：754e86331a44fd44bb9c1540b4d0e8f0b34002b0
- 清单文件哈希（本次读到的字节）：`b6c06ac5c0857ec769e8d33bcf561cfd2f951b98ef096ff0d13ab1d25b65aaa3`
- 实测面哈希：已落盘 `1ad50c642f4b1fbeef44df8eaf2589489dff5c37b4b4ae986e243a72c9542374` ／ 当前树重导出 `1ad50c642f4b1fbeef44df8eaf2589489dff5c37b4b4ae986e243a72c9542374`
- 清单新鲜度：一致（当前树重导出的实测面与已落盘清单同哈希）
- 规格指针（规格里不写范围数字，只指向清单）：登记的 True ／ 现场重读的 True → 一致
- 行为仪器（verify_1to1.mjs）：not-adopted:artifacts/tmp/verify_1to1.mjs — 未收编时本列只报缺证据，不报通过。
- 每格的含义：它说的是**证据在不在**，不是还原对不对。MEASURED 只表示 Android 侧基线记录在位且必需节齐全；Web 侧是否等价，要等阶段 3 的比对给出另一份记录。

| 条目 | 类别 | 结构层 | 视觉层 | 行为层 |
|---|---|---|---|---|
| ChatScreen | 屏 | 根布局 screen_chat / 入口 2 处（constructed,swap）/ sw600dp=有 | MEASURED：记录含 8 节，必需节 7/7 齐，样图在位 | REPLAY-SPEC：回放规格 2 次点击 / 6 项 DOM 检查（.msg-user） |
| FileManagerScreen | 屏 | 自绘（无根布局） / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| HomeDrawerScreen | 屏 | 自绘（无根布局） / 入口 2 处（addView,constructed）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| HomeScreen | 屏 | 根布局 screen_home / 入口 2 处（constructed,swap）/ sw600dp=有 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| LibraryScreen | 屏 | 根布局 screen_library / 入口 2 处（constructed,swap）/ sw600dp=有 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| ModelSettingsScreen | 屏 | 自绘（无根布局） / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| NavigationScreen | 屏 | 根布局 screen_navigation / 入口 2 处（constructed,swap）/ sw600dp=有 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| ProviderAdminScreen | 屏 | 根布局 screen_provider_admin / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| SearchServiceSettingsScreen | 屏 | 自绘（无根布局） / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| SettingsKeyScreen | 屏 | 根布局 screen_settings_key / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| SettingsScreen | 屏 | 根布局 screen_settings / 入口 2 处（constructed,swap）/ sw600dp=有 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| StatsDetailScreen | 屏 | 根布局 screen_stats_detail / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| TreeCanvasScreen | 屏 | 根布局 screen_tree / 入口 2 处（constructed,swap）/ sw600dp=有 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| VoiceSettingsScreen | 屏 | 自绘（无根布局） / 入口 2 处（constructed,swap）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| panel_search_picker ← ChatScreen | 浮层 | 浮层 panel_search_picker（dialog）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← ChatScreen | 浮层 | 浮层 代码绘制，无布局文件（popup_menu）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| popup_child_branches ← ChildBranchPopup | 浮层 | 浮层 popup_child_branches（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← HomeDrawerScreen | 浮层 | 浮层 代码绘制，无布局文件（code_drawn_host）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← ModelSettingsScreen | 浮层 | 浮层 代码绘制，无布局文件（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| popup_model_picker ← ModelThinkingPicker | 浮层 | 浮层 popup_model_picker（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← NavigationScreen | 浮层 | 浮层 代码绘制，无布局文件（popup_menu）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← NavigationScreen | 浮层 | 浮层 代码绘制，无布局文件（alert_dialog）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← ProviderAdminScreen | 浮层 | 浮层 代码绘制，无布局文件（alert_dialog）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← ProviderAdminScreen | 浮层 | 浮层 代码绘制，无布局文件（alert_dialog）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← SearchServiceSettingsScreen | 浮层 | 浮层 代码绘制，无布局文件（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| (代码绘制) ← SettingsKeyScreen | 浮层 | 浮层 代码绘制，无布局文件（alert_dialog）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| view_node_popup ← TreeCanvasScreen | 浮层 | 浮层 view_node_popup（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| popup_word_action ← WordMenuPopup | 浮层 | 浮层 popup_word_action（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| popup_word_more ← WordMenuPopup | 浮层 | 浮层 popup_word_more（popup_window）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |
| overlay_fullscreen_editor ← ChatScreen | 浮层 | 浮层 overlay_fullscreen_editor（included_hidden）/ sw600dp=无 | NO-PROFILE：屏档案里没有这个键 | NO-PROFILE：没有夹具回放规格可查 |

## 汇总（全部由上面的行现场计数得出）

- 台账条目 = 屏 14 + 浮层 16 = 30
- 视觉层 各状态：MEASURED=1, NO-PROFILE=29
- 视觉层 状态合计 = 30（必须等于台账条目数）
- 行为层 各状态：NO-PROFILE=29, REPLAY-SPEC=1
- 行为层 状态合计 = 30（必须等于台账条目数）
- 自绘屏 5 个（清单里 self_drawn=True 的条数，逐条见上表）
- sw600dp 变体屏 6 个
- 未被任何入口引用的屏 = 0；未被引用的布局文件 = 0
- 范围条目的权威是本清单的 `counts`：屏 14 ／ 浮层 16 ／ 自绘 5；规格正文里形如「N 个屏幕」「N 个浮层」「N 个自绘屏」的行：12, 149（是否为历史引用由人来标注，本行不判）

判定：全部通过

EXITCODE=0
