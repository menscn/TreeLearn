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
| `fontSize` | `fs-display` `fs-h1` `fs-h2` `fs-body` `fs-sm` `fs-cap` | display 与 caption 拉开 2.7 倍对比 |
| `shadow` | `sh-1` `sh-2` | 只保留两档 |
| `fontFamily` | `kai` `serif` `brush` `sans` | 正文/标题用霞鹜文楷，品牌题字 Ma Shan Zheng，`sans` 仅原型脚手架用 |
| `other` | `lh-read` | 阅读行高 32px，与纸感横格间距对齐 |

`hairline`/`paper-rule`/`paper-write` 是 rgba，Android 的 `<color>` 副本不收录（需要时用 8 位 `#AARRGGBB` 另立资源，并同步扩展守门）。

## 怎么用

- **Figma 侧**：把本文件当作绑定的数值来源。写画布时用 `create_variable` 建变量、`bind_variable_to_node` / `bind_variable_to_paint` 绑定，**不要**在节点上写裸 hex/px。流程见 `.agents/skills/figma-build/SKILL.md`。
- **Android 侧**：实现界面时只从 `colors.xml` 取色，间距/圆角取 `space`/`radius` 档位。新增资源必须能反查到原型里的同名 token。
- **改设计**：先改原型 `:root`，再更新 `tokens.json`，最后同步 `colors.xml`，然后跑守门脚本。跳过任何一步都会被脚本拦下。

## 变更记录

- **2026-09-21 · `ink3` 数值下沉，三方一致性恢复**：生产侧为达到 WCAG AA 把 `ink3` 由 `#8A938E`（面 3.16:1 / 底 2.92:1，低于 4.5:1 底线）调深为 `#636F66`（5.26:1 / 4.85:1）。该修复当时只落在 `colors.xml`，未同步原型与本镜像，三方一致性守门因此为红。本次按「生产代码是事实、设计稿是意图」把数值补写回原型 `:root` 与本目录镜像，守门恢复 0 failure。以上数值只作为该次变更的记录，当前态数值仍以原型 `:root` 为唯一权威。对比度数字与测量命令见 `artifacts/evidence/T-013-static-ui-checks.txt` 的 CHECK 3，Android 侧说明见 `android/README.md` 的对比度条目。

## 已知缺口

- 间距/圆角/字号尚未落到 Android 资源（目前只有 `colors.xml`），XML 布局出现魔法数字时无处可查。
- 字体：原型用 CDN 加载霞鹜文楷与 Ma Shan Zheng，Android 侧与 Figma 侧的落地方式（打包字体 / 本地字体 / 回退栈）尚未决定。
- 无暗色模式定义；原型只定义了浅色纸感主题。