# 知芽设计系统 · 生长绿（machine-readable mirror）

本目录只做一件事：把**已经存在的**设计系统从原型代码里抽出来，变成 Figma 与 Android 都能直接读的机器可读形式。

## 事实源（唯一权威）

| 位置 | 角色 |
| :--- | :--- |
| [`../知芽-TreeLearn-高保真原型-v1.html`](../知芽-TreeLearn-高保真原型-v1.html) 的 `:root` 区块（第 22–72 行） | **权威**：设计系统的当前态定义，42 个 token 的数值出处 |
| `tokens.json`（本目录） | **镜像**：同一批 token 的分组 + 用途注释，供 Figma 变量与后续工具读取 |
| [`../../../android/app/src/main/res/values/colors.xml`](../../../android/app/src/main/res/values/colors.xml) | **副本**：Android 侧落地的不透明 hex 子集（15 个 `tl_*` 资源） |

数值**不允许**在本目录被重新定义：这里只镜像原型。三处一致性由脚本判定，不靠人眼：

```bash
python .trae/scripts/dev/check_design_tokens.py            # 校验真实文件
python .trae/scripts/dev/check_design_tokens.py --selftest # 守门自身的正反双向自检
```

## 分组

| 分组 | token | 说明 |
| :--- | :--- | :--- |
| `color` | `bg` `surface` `ink` `ink2` `ink3` `line` `hairline` `green` `green-deep` `green-soft` `green-faint` `blue` `amber` `amber-soft` `red` `red-soft` `paper-rule` `paper-write` | 纸感米白底 + 生长绿主色 + 语义色（未读蓝、警示琥珀、危险红） |
| `space` | `s1` `s2` `s3` `s4` `s6` `s8` `gutter` | 4pt 基准，**只用** 4/8/12/16/24/32 六档 |
| `radius` | `r-sm` `r-md` `r-lg` `r-full` | 只保留 3 档 + 全圆 |
| `fontSize` | `fs-display` `fs-h1` `fs-h2` `fs-body` `fs-sm` `fs-cap` | display 与 caption 拉开约 2.4 倍对比（26/11） |
| `shadow` | `sh-1` `sh-2` | 只保留两档 |
| `fontFamily` | `kai` `serif` `brush` `sans` | 正文走系统 `sans-serif`（`sans`），品牌衬线走回退栈（`serif`），本轮不打包字体；`kai`/`brush` 是旧设计系统遗留，保留不删、退役待定 |
| `other` | `lh-read` | 阅读行高 28px，与纸感横格间距对齐 |

`hairline`/`paper-rule`/`paper-write` 是 rgba，Android 的 `<color>` 副本不收录（需要时用 8 位 `#AARRGGBB` 另立资源，并同步扩展守门）。

## 怎么用

- **Figma 侧**：把本文件当作绑定的数值来源。写画布时用 `create_variable` 建变量、`bind_variable_to_node` / `bind_variable_to_paint` 绑定，**不要**在节点上写裸 hex/px。流程见 `.agents/skills/figma-build/SKILL.md`。
- **Android 侧**：实现界面时只从 `colors.xml` 取色，间距/圆角取 `space`/`radius` 档位。新增资源必须能反查到原型里的同名 token。
- **改设计**：先改原型 `:root`，再更新 `tokens.json`，最后同步 `colors.xml`，然后跑守门脚本。跳过任何一步都会被脚本拦下。

## 变更记录

- **2026-09-22 · 基座同步 Figma 新稿（配色 / 度量 / 字体三处对齐），并修正新稿自带的 2 处对比度回退**：按「先改原型 `:root`、再改本目录镜像、再改 `colors.xml`、最后跑守门」的固定顺序，把 `color`/`radius`/`fontSize`/`shadow`/`fontFamily`/`other` 各分组的**值**换成新稿值（`space` 分组新稿与原值相同，故未变；token 名一个都没动）。两处回退用修正值落地，实测值由 `artifacts/evidence/T-050-design-token-sync.txt` 里的 WCAG 复算脚本产出，不是目测：`ink3` 新稿 `#75867B` 在 `bg`/`surface` 上只有 **3.42 / 3.72**，修正为 **`#627067`**（`bg` **4.62** / `surface` **5.03** / `amber-soft` **4.72**）；`amber` 新稿 `#B45309` 在 `bg` 上只有 **4.45**，修正为 **`#B05108`**（`bg` **4.63** / `surface` **5.04** / `amber-soft` **4.72**）。`blue` 换成新稿值后由旧值在 `bg` 上的 4.16 升到 **4.58**，顺手修好了旧稿的欠账。一处**值域限制**：新稿把 `green-soft`/`green-faint`/`amber-soft`/`red-soft` 写成 rgba，而 `colors.xml` 副本只收不透明 hex、且本次不许改动它的 15 个资源名与数量，所以这四个 token 保留仓库原有的不透明等价色——把新稿 rgba 压平成 hex 会把 `amber-soft` 上的 `ink3`/`amber` 打到 **4.06**，低于 4.5 的验收线。字体：`sans`/`serif` 换成新稿的回退栈并在镜像里注明「本轮不打包字体」与 ROM 衬线残余风险；`kai`/`brush` 在新稿里没有对应项，按「只改值不改名」保留不删。三处一致性由 `check_design_tokens.py`（含 `--selftest`）判定为 0 failure，输出 `measured_tokens=42 measured_resources=15`。`tokens.json` 的 `source_ref`（原型第 22–72 行 `:root`）仍然有效：本轮未增删该区块的行；同时移除了镜像里三处机械提取残留的 note（被错挂在 `red-soft`/`r-full`/`sh-2` 上的小节标题），它们含「全站楷体」等已被本次变更取代的当前态表述。
- **2026-09-21 · `ink3` 数值下沉，三方一致性恢复**：生产侧为达到 WCAG AA 把 `ink3` 由 `#8A938E`（面 3.16:1 / 底 2.92:1，低于 4.5:1 底线）调深为 `#636F66`（5.26:1 / 4.85:1）。该修复当时只落在 `colors.xml`，未同步原型与本镜像，三方一致性守门因此为红。本次按「生产代码是事实、设计稿是意图」把数值补写回原型 `:root` 与本目录镜像，守门恢复 0 failure。以上数值只作为该次变更的记录，当前态数值仍以原型 `:root` 为唯一权威。对比度数字与测量命令见 `artifacts/evidence/T-013-static-ui-checks.txt` 的 CHECK 3，Android 侧说明见 `android/README.md` 的对比度条目。

## 已知缺口

- 间距/圆角/字号尚未落到 Android 资源（目前只有 `colors.xml`），XML 布局出现魔法数字时无处可查。
- 字体：原型用 CDN 加载霞鹜文楷与 Ma Shan Zheng，Android 侧与 Figma 侧的落地方式（打包字体 / 本地字体 / 回退栈）尚未决定。
- 无暗色模式定义；原型只定义了浅色纸感主题。