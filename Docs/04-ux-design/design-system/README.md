# 知芽设计系统 · 生长绿（machine-readable mirror）

本目录只做一件事：把**已经存在的**设计系统从原型代码里抽出来，变成 Figma 与 Android 都能直接读的机器可读形式。

## 事实源（唯一权威）

| 位置 | 角色 |
| :--- | :--- |
| [`../知芽-TreeLearn-高保真原型-v1.html`](../知芽-TreeLearn-高保真原型-v1.html) 的 `:root` 区块（第 22–74 行） | **权威**：设计系统的当前态定义，44 个 token 的数值出处 |
| `tokens.json`（本目录） | **镜像**：同一批 token 的分组 + 用途注释，供 Figma 变量与后续工具读取 |
| [`../../../android/app/src/main/res/values/colors.xml`](../../../android/app/src/main/res/values/colors.xml) | **副本**：Android 侧落地的不透明 hex 子集（16 个 `tl_*` 资源） |

> **增量原型 `-v1.1.html` 不构成第二个权威源（2026-09-27 登记）**：`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.1.html` 的 `:root` 区块与 v1 **逐字节相同**（两文件该区块 md5 同为 `87d3f40f699a6b92ada08e48c97dd84c`，token 数 44/44），它在 `:root` **之外**新增的界面层没有引入任何基线之外的新颜色值。因此本表第 1 行的权威源**仍是 v1**：守门脚本按路径硬编码校验 v1，V1.1 不参与三方比对；V1.1 只作为「同一套 token 之上的增量界面稿」存在。判定与本版三项自检的原始输出见 `artifacts/evidence/ux-v11-prototype-2026-09-27.txt`，版本内容与四项范围裁决的记录见 V1.1 文件头部注释与左侧走查清单。

数值**不允许**在本目录被重新定义：这里只镜像原型。三处一致性由脚本判定，不靠人眼：

```bash
python .trae/scripts/dev/check_design_tokens.py            # 校验真实文件
python .trae/scripts/dev/check_design_tokens.py --selftest # 守门自身的正反双向自检
```

## 分组

| 分组 | token | 说明 |
| :--- | :--- | :--- |
| `color` | `bg` `surface` `ink` `ink2` `ink3` `line` `hairline` `green` `green-deep` `green-soft` `green-faint` `blue` `amber` `amber-soft` `red` `red-soft` `paper-rule` `paper-write` | 纸感米白底 + 生长绿主色 + 语义色（未读蓝、警示琥珀、危险红）。`green` 是**动作色**（按钮/发送/锚点/叶芽）。`green-bubble` 已于 2026-09-28 退役：用户消息不再有气泡底 |
| `space` | `s1` `s2` `s3` `s4` `s6` `s8` `gutter` | 4pt 基准，**只用** 4/8/12/16/24/32 六档 |
| `radius` | `r-sm` `r-md` `r-lg` `r-full` | 只保留 3 档 + 全圆 |
| `fontSize` | `fs-display` `fs-h1` `fs-h2` `fs-body` `fs-sm` `fs-cap` | display 与 caption 拉开约 2.4 倍对比（26/11） |
| `shadow` | `sh-1` `sh-2` | 只保留两档 |
| `fontFamily` | `kai` `serif` `brush` `sans` | 正文走系统 `sans-serif`（`sans`），品牌衬线走回退栈（`serif`），本轮不打包字体。**2026-09-27 起 `kai` 只剩 `.home-hero .hh-sub`（首页 hero 副标题）一处引用**：`.screen` 原为楷体、已改为 `sans`，因为对话页设计稿与真机 `themes.xml` 都是 `sans-serif`。`brush` 仍是题字用；`kai` 退役待定 |
| `other` | `lh-read` | 阅读行高 28px，与纸感横格间距对齐 |

`hairline`/`paper-rule`/`paper-write` 是 rgba，Android 的 `<color>` 副本不收录（需要时用 8 位 `#AARRGGBB` 另立资源，并同步扩展守门）。

## 怎么用

- **Figma 侧**：把本文件当作绑定的数值来源。写画布时用 `create_variable` 建变量、`bind_variable_to_node` / `bind_variable_to_paint` 绑定，**不要**在节点上写裸 hex/px。流程见 `.agents/skills/figma-build/SKILL.md`。
- **Android 侧**：实现界面时只从 `colors.xml` 取色，间距/圆角取 `space`/`radius` 档位。新增资源必须能反查到原型里的同名 token。
- **改设计**：先改原型 `:root`，再更新 `tokens.json`，最后同步 `colors.xml`，然后跑守门脚本。跳过任何一步都会被脚本拦下。

## 变更记录

- **2026-09-28 · 视觉微调 5 项（用户拍板）：用户气泡退役 · 纸纹减淡 · 首页 Key 徽标删除 · 操作行与附件胶囊收紧 · 页头标题统一**：
  - **`green-bubble` 退役（token 44→43，Android 资源 16→15）**：用户消息（`.msg-user`）不再有气泡底，改为与 AI 回答（`.msg-ai`）同样的纯文本，只靠右对齐区分角色。原型 `:root` 删 `--green-bubble`，`tokens.json` 删 `color.green-bubble`，`colors.xml` 删 `tl_green_bubble`，`drawable/bg_bubble_user.xml` 删。**连带**：`.msg-user .quote-in` 原靠「白 22% 叠在深色气泡上」取对比，改用 `--green-faint` 实底 + `--green-deep` 字（Android 侧同用 `tl_green_faint` / `tl_green_deep`）。
  - **`paper-rule` 由 `.028` 减淡到 `.018`**：真机上原型强度的横线仍被读作「粗」，透明度降到约 2/3。Android 侧 `PaperRules.RULE_ALPHA` 79→50，页面横线合成色从 (238,235,228) 抬到 (240,237,231)，与纸底 `tl_bg` 的最大通道差由 7 阶降到 4 阶。`PaperRulesTest` 新增「既没消失（≥2 阶）也没变粗（≤5 阶）」的双向夹逼，防止后续再漂。
  - **首页 composer 的 Key 状态徽标删除**：`home_key_hint` / `home_key_icon` / `home_key_text` 三 id、`ic_key` drawable、`home_key_missing` / `home_key_ready` 两条文案一并移除；key 状态由设置页的 key 卡片**唯一**承载。`HomeComposerLayoutTest` 的对应断言反转为「这些 id 不得回来」。
  - **回答操作行与附件胶囊**：`act_children` / `act_version` 字号由 `text_cap`(11sp) 提到 `text_sm`(13sp) 并放宽 `minWidth`；`chat_attach` 胶囊高度由 `touch_min`(44dp) 改为 `wrap_content`，与右侧 `chat_model_chip` 同属 32dp 档。
  - **学习库 / 设置页头统一**：`library_title` 由 `text_h2`(16sp) 提到 `text_h1`(20sp)，与设置页标题（`text_h1`/bold/`tl_ink`）完全一致；学习库标题左侧补 `touch_min` 等宽占位，使两页文字左缘对齐。
  - 本次三处一致性由 `check_design_tokens.py` 判定 0 failure，输出 `measured_tokens=43 measured_resources=15`。
- **2026-09-27 · 对话页 1:1 复刻第 2 批：`.screen` 正文字体由 `kai` 改为 `sans`（用户拍板）**：对话页设计稿与真机 `android/app/src/main/res/values/themes.xml` 的 `android:fontFamily` 都是 `sans-serif`，只有 HTML 原型在用楷体——改的是原型这一方，属**消除设计稿与生产的偏差**，不是引入新风格。**未增删任何 token**，只把原型 `.screen` 的 `font-family` 从 `var(--kai)` 换成 `var(--sans)`，故资源数与 token 数都不变（仍 44 / 16）。`--kai` 保留（现存唯一引用是 `.home-hero .hh-sub`），因此两条霞鹜文楷 CDN `link` **仍不可移除**；`--kai` 退役待定。判据不是目测，四项互相独立：① 声明值回读 `"Noto Sans SC", -apple-system, sans-serif`；② `document.fonts` 注册面为 `Noto Sans SC` **400/500/700** 三档（证明字体真实存在，不是静默回退）；③ 实际参与栅格化的字体由 `CSS.getPlatformFontsForNode` 读出为自定义 webfont（**注意该接口返回的是字体文件内部名 `Noto Sans SC Thin`，是变量字体的默认实例名，不等于渲染字重**——字重看 ② 的注册面）；④ 同字符串、同盒子、同换行的墨量 A/B：气泡内白字像素 **13794 → 15355（+11.3%）**，方向符合楷体（笔画细、粗细有变化）→ 黑体（方头等线、笔画更粗）。证据见 `artifacts/evidence/ux-chat-1to1-render-dommeasure-2026-09-27.json` 的 `platformFonts` / `fontFaces` 两节，方案见 `../知芽-TreeLearn-对话页1比1复刻方案-V1.0.md` §10。
- **2026-09-27 · 新增 `green-bubble`（对话页 1:1 复刻第 1 批，用户拍板）**：按对话页设计原型截图的实测值新增 `color.green-bubble = #3F6C49`，只服务 `.msg-user` 的用户消息气泡底，**不动** `green` 主色。判据不是目测：截图为 2x（824×1367 → 逻辑 412×684dp），纯 Python 解码后逐行扫描，气泡内部 **27716 个像素完全同色 `#3F6C49`**；同图作为噪声基线的 `ink` 正文偏差 3、`ink3` 图标偏差 3、11px 小字标签偏差 12（抗锯齿），故 ΔR=20 的偏差**不可能**由截图偏色或压缩解释，属设计实改值。白字对比度 **6.07:1**，过 WCAG AA（原 `green` 为 6.14:1，无回退）。测量命令与逐项实测见 `artifacts/evidence/ux-chat-1to1-measure-2026-09-27.txt`，方案见 `../知芽-TreeLearn-对话页1比1复刻方案-V1.0.md`。
  - **资源数量 15 → 16 的说明**：`colors.xml` 新增 `tl_green_bubble`。首列「15 个资源」的表述来自 2026-09-22 那次基座同步的**那次变更边界**（当时为吸收 Figma 新稿而限制不扩资源），不是永久约束；本节末「Android 侧」已规定新增资源的合法路径（另立资源 + 能反查到同名 token + 同步守门），本次按该路径执行。`check_design_tokens.py` 判定 `measured_tokens=44 measured_resources=16`，0 failure。
  - 同批改动（不入 token，只在原型 CSS）：`.msg-user` 圆角由 `r-md r-md s1 r-md` 改为四角 `r-md` 统一；`.ai-tag` 加 `min-height:20px` 且 `margin-bottom` 由 `s3` 改 `s4`。两项依据同一份实测证据（气泡四角 inset 序列逐值对称；✦知芽标签↔正文首行中心距实测 40.5）。
  - 遗留：`docs` 侧 `知芽（TreeLearn）对话页视觉重做规格_V1.0.md` §3.3「AI 正文左缘 `green` 细竖线」经用户裁决**作废**（截图与原型均无竖线），已在原文档就地标注。
- **2026-09-22 · 基座同步 Figma 新稿（配色 / 度量 / 字体三处对齐），并修正新稿自带的 2 处对比度回退**：按「先改原型 `:root`、再改本目录镜像、再改 `colors.xml`、最后跑守门」的固定顺序，把 `color`/`radius`/`fontSize`/`shadow`/`fontFamily`/`other` 各分组的**值**换成新稿值（`space` 分组新稿与原值相同，故未变；token 名一个都没动）。两处回退用修正值落地，实测值由 `artifacts/evidence/T-050-design-token-sync.txt` 里的 WCAG 复算脚本产出，不是目测：`ink3` 新稿 `#75867B` 在 `bg`/`surface` 上只有 **3.42 / 3.72**，修正为 **`#627067`**（`bg` **4.62** / `surface` **5.03** / `amber-soft` **4.72**）；`amber` 新稿 `#B45309` 在 `bg` 上只有 **4.45**，修正为 **`#B05108`**（`bg` **4.63** / `surface` **5.04** / `amber-soft` **4.72**）。`blue` 换成新稿值后由旧值在 `bg` 上的 4.16 升到 **4.58**，顺手修好了旧稿的欠账。一处**值域限制**：新稿把 `green-soft`/`green-faint`/`amber-soft`/`red-soft` 写成 rgba，而 `colors.xml` 副本只收不透明 hex、且本次不许改动它的 15 个资源名与数量，所以这四个 token 保留仓库原有的不透明等价色——把新稿 rgba 压平成 hex 会把 `amber-soft` 上的 `ink3`/`amber` 打到 **4.06**，低于 4.5 的验收线。字体：`sans`/`serif` 换成新稿的回退栈并在镜像里注明「本轮不打包字体」与 ROM 衬线残余风险；`kai`/`brush` 在新稿里没有对应项，按「只改值不改名」保留不删。三处一致性由 `check_design_tokens.py`（含 `--selftest`）判定为 0 failure，输出 `measured_tokens=42 measured_resources=15`。`tokens.json` 的 `source_ref`（原型第 22–72 行 `:root`）仍然有效：本轮未增删该区块的行；同时移除了镜像里三处机械提取残留的 note（被错挂在 `red-soft`/`r-full`/`sh-2` 上的小节标题），它们含「全站楷体」等已被本次变更取代的当前态表述。
- **2026-09-21 · `ink3` 数值下沉，三方一致性恢复**：生产侧为达到 WCAG AA 把 `ink3` 由 `#8A938E`（面 3.16:1 / 底 2.92:1，低于 4.5:1 底线）调深为 `#636F66`（5.26:1 / 4.85:1）。该修复当时只落在 `colors.xml`，未同步原型与本镜像，三方一致性守门因此为红。本次按「生产代码是事实、设计稿是意图」把数值补写回原型 `:root` 与本目录镜像，守门恢复 0 failure。以上数值只作为该次变更的记录，当前态数值仍以原型 `:root` 为唯一权威。对比度数字与测量命令见 `artifacts/evidence/T-013-static-ui-checks.txt` 的 CHECK 3，Android 侧说明见 `android/README.md` 的对比度条目。

## 已知缺口

- 间距/圆角/字号尚未落到 Android 资源（目前只有 `colors.xml`），XML 布局出现魔法数字时无处可查。
- 字体：原型用 CDN 加载霞鹜文楷与 Ma Shan Zheng，Android 侧与 Figma 侧的落地方式（打包字体 / 本地字体 / 回退栈）尚未决定。**当前态**：`.screen`（所有页面正文）已走 `sans`，与真机 `themes.xml` 一致；`kai` 只剩 `.home-hero .hh-sub` 一处，`brush` 用于题字。
- 无暗色模式定义；原型只定义了浅色纸感主题。