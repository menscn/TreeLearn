# 知芽（TreeLearn）· 设置页重设计方案 V1.0

| 项 | 值 |
|---|---|
| 版本 | V1.1（已落地） |
| 日期 | 2026-09-28 |
| 状态 | **Android 已按本稿落地**（用户 2026-09-28 16:59 拍板「执行」，四项待拍板均按建议执行：落地全部映射、标题保留「API KEY · DeepSeek」、删除文字链左对齐、provider_admin 同批重排） |
| 配套产物 | `知芽-TreeLearn-设置页重设计-交互稿-V1.0.html`（同目录，可点击走查） |
| 用户裁决 | 2026-09-28 用户在 A/B/C 三方向中选定 **C · 大改**（iOS 式分组列表 + 二级页返回栈）；同日二裁：**替换 KEY 表单收成单行，保存按钮改为 44dp 绿底白勾图标**（输入框 + 图标按钮同行） |

---

## 一、背景与问题

真机截图（2026-09-28）走查发现设置页三处问题，根因均已在代码定位：

| # | 现象 | 根因证据 | 性质 |
|---|---|---|---|
| 1 | 「保存」按钮归属被误读 | `screen_settings_body.xml:114-142`：保存与删除塞在**同一个水平 LinearLayout**，挂在输入框下方；保存没有属于自己的表单容器 | 布局 |
| 2 | 「删除 KEY」与「保存」同排同权 | 同上：一个是正向提交、一个是不可逆破坏性动作，却同为 44dp 按钮、并排 8dp，红框紧贴绿按钮 | 层级 |
| 3 | 整页结构不清晰 | 六行能力入口是六张独立 `bg_card`（`:155-374`，各自 `marginTop=space_s2`），无分组、无连通；入口图标 `ic_nav_more`（「···」）与原型已裁决的「进入下一屏一律用『›』」（`知芽-TreeLearn-高保真原型-v1.1.html:2932`，评审 M6）不一致 | 结构 + 偏差 |

## 二、目标与非目标

**目标**
1. 设置根页收成**纯分组导航列表**：浏览类信息（入口行）与编辑类信息（表单）在视觉与空间上分离。
2. Key 管理独立成**二级页**，带标准返回栈；保存/删除各归其位，全页只保留一个实心主按钮。
3. 破坏性动作（删除 Key）降权为文字链 + 二次确认弹层，收窄误触面。
4. 消除与原型 v1.1 的「···」vs「›」偏差。

**非目标**
- 不改 PRD 契约：§6.2.13（BASE-17）要求的**掩码展示、替换、删除**三个能力全部保留，只换位置。
- 不改五个既有二级页（提供商/模型/搜索/语音/文件）的内部设计，它们已是带返回栈的二级页。
- 不新增 design token（维持 43 token / 15 `tl_*` 资源基数不变）。

## 三、方案（C）

### 3.1 信息架构

```
设置（一级 tab，根页 = 纯分组列表）
├─ 分组「AI 提供商」
│   ├─ API KEY · DeepSeek     → [新] Key 管理二级页
│   ├─ 提供商管理              → providers（现有二级页）
│   └─ 模型设置                → modelSettings（现有二级页）
├─ 分组「能力」
│   ├─ 搜索服务                → searchServices（现有二级页）
│   ├─ 语音                    → voice（现有二级页）
│   └─ 文件管理                → files（现有二级页）
└─ 分组「数据边界」
    └─ 静态说明卡（不可点，沿用现有 boundary 文案）
```

要点：
- 根页不再直接承载任何表单。原 Key 卡整体迁出，变成「API KEY · DeepSeek」一行入口，**副标题内联当前状态**（掩码 · 可用 / 无效 / 未配置），用户不进二级页也能读到 Key 健康度。
- 分组卡 = 单张 `flush` 卡 + 行间 `hairline` 发丝线（沿用 v1.1 `.set-link` 既有 pattern），六个「···」全部改「›」。
- Key 二级页带页面标题「API KEY · DeepSeek」+ 返回箭头，落入 MainActivity 既有 sub-settings room 机制（Android 端栈已存在，只加一间房）。

### 3.2 Key 管理二级页规格

**「当前 KEY」状态卡**
- 第一行：钥匙图标 + 掩码值 + 状态 pill（`key-ok` / `key-bad` / `key-none`，三态文案与 `strings.xml:148-150` 一致）。
- 说明行：「仅存本机，可随时替换或删除。无云同步与备份，卸载后不可恢复。」
- 发丝线分隔后：**「删除此 Key」红色文字链**（13sp，配垃圾桶图标，左对齐）。仅在 Key 存在时显示。点击弹确认层，不直接执行。

**「替换 KEY」表单卡**
- **输入框与保存图标按钮同行**（用户 2026-09-28 裁决：单行、按钮用图标不用文字）：右侧为 44dp 绿底白勾图标按钮（`--green` 底、`--surface` 勾形、radius 12），按按压态 `--green-deep`；44dp 满足 `touch_min`。
- 图标按钮**必须带 `aria-label` / `android:contentDescription="保存"`**（纯图形控件的无名替代）。
- 表单卡底部一行 11sp 灰字说明：「保存后立即生效，原文不再展示；替换不会影响已存主题与历史回答。」
- 同款单行图标表单适用于**全部三处 Key 表单**：Key 页（替换 KEY）、提供商编辑页（替换 KEY）、引擎详情页（替换 KEY）。

**删除确认弹层**（bottom sheet，沿用 v1.1 `.sheet` 形制）
- 标题「删除 DeepSeek API Key？」+ 后果说明（Key 守卫拦截、已存主题不受影响、不可恢复）。
- 按钮：取消（ghost）｜确认删除（danger）。确认后 Key 状态转「未配置」，toast 反馈。

### 3.3 按钮语义层级（本次设计的核心裁决）

| 层级 | 动作 | 形式 | 位置 |
|---|---|---|---|
| L1 提交 | 保存 Key | 44dp 绿底白勾**图标按钮**（唯一实心元素，带 contentDescription） | 替换表单卡内，与输入框同行右侧 |
| L2 辅助 | 状态 pill / 说明文字 | 文本 | 当前 KEY 卡 |
| L3 破坏性 | 删除此 Key | 红色文字链 + 确认弹层 | 当前 KEY 卡内，发丝线下方 |

原则：**一屏至多一个实心主按钮；正向提交与破坏性动作永不同排同权。**

### 3.4 五个二级页与两个三级页（V1.1 交互稿扩量）

用户追问「其他设置选项」后，交互稿从「根页 + Key 页」扩为**全设置段可走查**（V1.1）：

| 页面 | 层级 | 本稿处理 |
|---|---|---|
| 提供商管理 | 二级 | 自 v1.1 移植：提供商卡 + 启用开关 + 新建 sheet；根页副标题实时显示「已启用 N 家 · 共 M 家」 |
| 提供商编辑 | 三级 | 自 v1.1 移植，**Key 表单重排为新层级**（见下）；模型清单可拉取/逐项删除 |
| 模型设置 | 二级 | 自 v1.1 移植：三分工行 + 思考预算 + 聊天建议开关；模型选择器改用底部 sheet（v1.1 为锚点 popover，同族交互简化，不影响结构文案） |
| 搜索服务 / 引擎详情 | 二级/三级 | 自 v1.1 移植；引擎详情页 **Key 表单同样重排为新层级** |
| 语音 / 文件管理 | 二级 | 自 v1.1 移植：试听按钮、孤儿清理确认流程 |

**新增发现（扩展了原问题清单）**：Key 表单的反模式不止根页一处——
原型 `知芽-TreeLearn-高保真原型-v1.1.html:3446-3461`（提供商编辑）与 `:3649-3658`（引擎详情）
都是「按钮贴输入框 + 删除大按钮在下」；Android 真机已建的 `screen_provider_admin.xml:178-194`
（`provider_key_save` + `provider_key_delete` 同排）**同样中招**。
本稿已把这两处统一为 §3.3 的 L1/L3 层级；落地时须同步修改 `screen_provider_admin.xml`。

### 3.5 交互稿走查路径

打开 `知芽-TreeLearn-设置页重设计-交互稿-V1.0.html`：
1. 根页浏览三组列表 → 点「API KEY · DeepSeek」进二级页；
2. 二级页点「删除此 Key」→ 确认弹层 → 确认后变「未配置」态；粘贴任意内容保存 → 变「可用」态；
3. 提供商管理：切换 AiHubMix 开关 → 进 DeepSeek 编辑页，核对「当前 KEY / 替换 KEY」两卡与新按钮层级；
4. 模型设置：点任意分工行 → 按提供商分组选模型；思考预算五档选「高」；
5. 搜索服务：进智谱引擎详情，核对 Key 表单新层级；
6. 文件管理：清理孤儿（4 行 → 2 行），单文件删除走确认弹层。
（演示控制已移到交互稿侧栏说明，不占产品页面——v1.1 里「演示数据」区块在真机产品中本就不存在。）
（已验证：`artifacts/tmp/verify_settings_redesign.mjs` 10/10 PASS、pageerror=0、EXITCODE=0，2026-09-28。）

## 四、与既有裁决 / 契约的关系

| 既有约束 | 本方案关系 |
|---|---|
| PRD §6.2.13（BASE-17）：掩码展示、替换、删除 | 全保留，仅迁移位置；无需改 PRD |
| PRD §6.2.14：Key 不可用 → 阻止发送并引导配置 | 入口行副标题直接暴露「未配置 · 发送将被阻止」，引导路径不变 |
| 评审 M6（v1.1:2932）：进入下一屏一律「›」 | 根页全部入口行统一为「›」，消除真机偏差 |
| 2026-09-28 裁决：设置根页顶栏 = 返回箭头 + 标题 | 保持不变（真机已是此形制，`screen_settings_body.xml:11-39`） |
| Token 守门（43 token / 15 资源基数） | 不新增 token，复用 `--line/--hairline/--red/--green` 全部既有值；改动后跑 `check_design_tokens.py` 应为 `0 check(s) failed` |

## 五、Android 落地映射（2026-09-28 已执行）

**落地结果与 §5 预估的偏差**：
- `ic_chevron_right.xml` 已由首页线先行入库（v1 原型 chip 注释），本稿直接复用，**未再新增**；
- `bg_save_ico.xml` 圆角取 `radius_md`（14dp）——dimens 无 12dp 档，且「布局只引用名字，不内联 dp」，取最接近的既有 token；
- `screen_provider_admin.xml` 的删除文字链初始 `gone`，可见性由 `ProviderAdminScreen.renderEditor` 按 `editor.key.line` 驱动；
- 提供商编辑页与 Key 页的**删除一律先弹 `AlertDialog` 确认**（§3.3 L3 语义，三处行为一致）；

| 文件 | 实际动作 | 状态 |
|---|---|---|
| `res/layout/screen_settings_body.xml` | 重写为两张 flush 分组卡（「AI 提供商」「能力」），Key 卡移除改「API KEY · DeepSeek」入口行（副标题动态），六处 `ic_nav_more` → `ic_chevron_right` | 完成 |
| `res/layout/screen_settings_key.xml` + `screen_settings_key_body.xml` | **新增**：Key 二级页（状态卡 + 替换表单卡，结构 §3.2） | 完成 |
| `res/drawable/ic_check.xml`、`res/drawable/bg_save_ico.xml` | **新增**：白勾矢量 + 绿底图标按钮 selector（radius_md） | 完成 |
| `screen/SettingsKeyScreen.java` | **新增**：状态卡 + 表单卡 + 删除确认弹层，密钥处理规则（char[] 缓冲、掩码唯一、离窗清场）原样随迁 | 完成 |
| `screen/SettingsScreen.java` | 重写为纯分组导航列表；`render` 只画 Key 入口行副标题（掩码 + 判定三态） | 完成 |
| `MainActivity.java` | `keyAdminScreen` 第六间房：`showKeyAdmin()` 复用 `settingsPresenter`（不设新 presenter），`render(SettingsViewState)` 按 `keyAdminScreen` 是否在屏路由；返回栈条件与 `showSettings()` 清场同步 | 完成 |
| `screen_provider_admin.xml` + `ProviderAdminScreen.java` | Key 表单重排为单行图标按钮 + 红字删除链（确认弹层先行），`renderEditor` 按 Key 存在性控制删除链可见性 | 完成 |
| `strings.xml` | 新增 12 条（分组头/入口行/Key 页/确认弹层/contentDescription）；删除 2 条无引用旧条目（`settings_key_section`、`settings_key_save`） | 完成 |

**验证记录（2026-09-28）**：
- `check_design_tokens.py`：`0 check(s) failed`，43 token / 15 `tl_*` 资源基数不变；
- XML 注释 `--` 全树扫描：0 违规；
- `:app:compileDebugJavaWithJavac` BUILD SUCCESSFUL（首轮在共享 `app/build/` 上撞别线缓存锁竞态，`-PbuildDir=build-settingsfix --no-build-cache` 独立目录重跑即过）；
- `verifyNoKotlinDex` PASS（3 APK 零 Kotlin 字节码）；
- 真机：APK 装机成功、应用可启动、首页渲染正常；设置段走查被设备占用打断（共用平板有人在使用，且首装包 2 分钟内被别线测试流程卸载），**待设备空闲后按 §3.5 补走查**。

**其余四间房（模型/搜索/语音/文件）在真机端目前是代码拼装的占位 stub**（无独立布局文件），后续按各自 SPEC 落地时直接采用 v1.1 页面结构即可，本方案不重复展开。

## 六、待拍板项（2026-09-28 已全部按建议执行）

| # | 议题 | 建议 → 执行 |
|---|---|---|
| 1 | 本方案是否按稿落地 | 按交互稿执行 §5 映射 → **已落地**（用户拍板「执行」） |
| 2 | 根页「API KEY」入口行标题 | 「API KEY · DeepSeek」→ **按建议执行**（`settings_key_entry_title`） |
| 3 | 删除文字链对齐 | 左对齐（状态卡内容流内）→ **按建议执行** |
| 4 | `screen_provider_admin.xml` 的 Key 表单是否同批落地 | 同批 → **已同批重排** |
