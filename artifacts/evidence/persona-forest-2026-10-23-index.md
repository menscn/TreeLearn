# 人设与森林证据索引（2026-10-23）

一句话一行，只说这一单元里真有什么；截图内容与读数不在此复制，要判据就打开对应文件或任务卡。
Android 设备截图按既有拍板由 `adb install -r` 后的真机取屏（不跑全量仪器测试，那会卸载应用并清掉本地数据与 API Key）。

| 单元 | 来源片 | 里面是什么 |
| :--- | :--- | :--- |
| `persona-066-{chat,popover,selected,switched,persisted,home}.png` | T-2026-10-23-066 | Android 对话页 chip、弹层选中、切换后与重开后的持久态、首页无变化，共 6 张真机截图 |
| `persona-067-{chat,popover,back,back-settings,back-settings-gesture,settings,settings-entry,intro,intro-end}.png` | T-2026-10-23-067 | Android 七档介绍页与设置入口，含两条返回来路（对话页来路与设置页来路，后者再按系统返回回到设置）共 9 张 |
| `forest-068/` | T-2026-10-23-068 | Android 森林页：抽屉入口、林地、选中纸条、进入主题、返回首页 5 张截图，加 `forest-068-targeted-results.txt`（定向用例读数）与 `forest-068-green.txt`（GREEN 命令原文输出）、`forest-068-targeted.txt`（定向命令原文） |
| `web-persona-069/persona-069-{chip,popover,narrow-popover,switched,intro,intro-end,settings-entry,settings-intro}.png` | T-2026-10-23-069 | Web 人设面 8 张截图（桌面与窄屏弹层、介绍页首尾、两处设置入口），加 `probe.mjs`（CDP 驱动，可复跑）与 `readings.json`（该驱动写出的 DOM 读数） |
| `web-forest-070/forest-070-{entry,desktop,narrow,selected,selected-second,narrow-selected}.png` | T-2026-10-23-070 | Web 森林面 6 张截图（抽屉入口、桌面林地、窄屏林地、选中纸条含第二棵与窄屏那张），加 `probe.mjs` 与 `readings.json` |
| `artifacts/tmp/070-red.txt`、`artifacts/tmp/070-probe.txt` | 070 | RED 时的失败原文与探针中间读数；tmp 是逐次运行产物，不是验收面 |
| `figwright-forest/`（含 `v2`–`v5`） | 设计探索（片 042 前后） | 森林原型的多轮画稿，**不作为验收证据**：落地判据以 `Docs/spec/SPEC-2026-10-21-my-forest.md` 与上面各单元为准 |

- 人设核心与持久化（T-2026-10-23-064）没有截图单元：它的判据是 JVM 用例本身
  （`PersonaTest`、`PersonaPromptsTest`、`PersonaIdentityTest`、`TreeLearnSchemaPersonaTest`、
  `TopicPersonaDaoStatementTest`），逐条落点写在该卡 Verification report。
- 文档回写（T-2026-10-23-071）不产生新截图：它只回写 PRD 的 BASE-14 注记、`android/README.md` 两节、
  Web parity 规格的 2026-10-23 修订条目，判据为反向检索清零与守门脚本退出码。
- 卡片侧的结论与命令原文一律在 `.codex/tasks/` 对应卡的 RED/GREEN/Verification report，本索引不复制。
