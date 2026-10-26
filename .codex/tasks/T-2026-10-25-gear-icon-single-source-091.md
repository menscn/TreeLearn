# Filename: T-2026-10-25-gear-icon-single-source-091.md
# Codex Harness Task: 「设置」画成真齿轮且两端同源（X2）

id: T-2026-10-25-091
title: 设置图标改真齿轮并两端同源（X2）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-25-ui-readability-parity-requirement-086.md
requirements_confirmed: true
generated_by: leader
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 40
shared_code_touched: true
depends_on: [T-2026-10-25-ui-asset-parity-judgment-088.md]
tdd_mode: required

## Requirement collaboration brief

- Parent summary: 086 的 X2——「设置」图标在两端确实一致，但那条 pathData 画的是「中心圆 + 八条放射短线」，即一轮太阳；且 pathData 在 `ic_chat_gear.xml` 与 `Icon.tsx` 里手抄两份，改一端不会提醒另一端。
- Problem/background: 太阳与齿轮的差别不在笔画数，而在轮廓：齿要有宽有窄、齿顶是直线而不是一个点。手抄两份的一致性是「碰巧」，项目没有任何判据把两端绑在一起。
- Users/scenarios: 在 Web／exe 与 Android 之间切换的用户——同一个位置写着「设置」，却是一枚认不出的图形；以及后续改图标的人。
- Expected outcome: 两端共用同一条八齿轮廓（32 个顶点），pathData 逐字相同，并有一条判据同时比对两端、且要求轮廓顶点数 ≥ 24（放射线过不了这道门）。
- Core flow and reversibility: 只改 drawable 与 glyph 的 pathData，不触碰任何调用点或布局。回退＝还原两处 pathData。
- Inputs/outputs: 输入是评审板与 A7 判据；输出是 `android/app/src/main/res/drawable/ic_chat_gear.xml` 与 `frontend/src/components/Icon.tsx` 的 `chat_gear`。
- State and exception handling: A7 读不到任一端时判仪器故障而不是判红；顶点数不足视为「画的不是齿轮」。
- Constraints and trade-offs: 不换图标语言（仍是线性 stroke 1.8、24 视口）；不引入图标库；不改使用点（`Icon.tsx` 的 `home_settings_button`／`chat_settings` 不变）。
- AI recommendation and rationale: 086 的 AI 自主决定 ⑤：方向选齿轮而不是滑杆，因为「滑杆」在本产品语义里更像筛选。
- User-confirmation decisions: 22 条全做（X2 在内）。
- AI-autonomous decisions: ① 八齿、齿尖半径 9.4、齿根 6.9、中心孔 3，描边 1.8 不溢出 24 视口；② 判据要求 ≥24 顶点，用几何门槛判「这是齿轮」，而不是靠肉眼。
- Acceptance evidence: A7 先红（`FAIL A7 … 放射线画的是太阳`）后绿（`2 条 pathData 逐字相同，轮廓 31 个顶点`）。
- Facts: `091-red.txt` 与 `091-green.txt`；pathData 在两端逐字相同，A7 数出 31 个顶点（32 个坐标点，首尾共用）。
- Assumptions: 两端 glyph 尺寸与描边一致（Android 1.8dp stroke、Web `STROKE` 组同值）。
- Unknowns: 无。
- Risks: 更密的轮廓在 18dp 显示尺寸下可能糊；当前使用点是 20dp／36dp 键，未在小尺寸使用。
- Deferred: 图标单一来源的「构建期生成」不在本片——本片只保证两端逐字相同并由判据看守。
- Non-goals: 不改其它图标；不改选中态；不改导航图标。
- Skipped non-critical questions and reason: 未问「要不要顺手统一其它图标」——范围外，且没有对应评审发现。

## Goal

把「设置」从一轮太阳改成一只认得出的齿轮，并让两端共用同一条轮廓、由判据看守。

## Scope

### In scope
- `android/app/src/main/res/drawable/ic_chat_gear.xml`、`frontend/src/components/Icon.tsx` 的 `chat_gear`。

### Out of scope
- `check_ui_asset_parity.py` 的 A7 实现本身（→088）；其它图标；图标尺寸与使用点。

### Behavior impact — applicable surfaces only
Follow [regression prevention and behavior evidence](../rules/regression.md).
- Intended change and old behavior to preserve: 图形身份从太阳变齿轮；必须保留 tint 在使用点、24dp 视口、1.8 描边与圆角连接、Android 的无 tint 声明（由 ImageView 着色）。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `ic_chat_gear.xml` 与 `Icon.tsx` 的 `chat_gear`；消费者 `Icon.tsx:50`（`home_settings_button`）、`:62`（`chat_settings`），Android 侧 `screen_home.xml` 与 `screen_chat_body.xml` 的设置入口。
- Native bridge or independent-client compatibility/update requirements: 无。
- Behavior-neutral non-applicability reason, 不适用。

## Documentation impact record

- Changed runtime facts: 「设置」的图形身份与两端 pathData 的同源关系成为可机械复核的事实。
- Authoritative code/config/migration evidence: 两处 pathData 逐字相同；A7 的顶点门槛。
- Documentation surfaces checked: `Docs/04-ux-design/design-system/README.md`（图标段落未单列该 glyph）、`.codex/rules/ux-design.md`。
- Documentation files updated: 无需改文档；图形身份的说明写进两处代码注释。
- Producer/consumer/sibling/compatibility paths checked: 见 Behavior impact。
- Superseded phrases/examples reverse-searched: `grep -rn "IGear\|太阳" frontend/src android/app/src/main/res` 无残留旧描述。
- `documentation impact: none` reason and evidence: 改的是 glyph 几何，不改变任何设计系统条目的取值或命名。
- Historical/PRD handling: 不改写 056 的图标清单历史。

## Acceptance criteria

- [x] 两端 pathData 逐字相同，A7 判绿且轮廓 ≥24 顶点。
- [x] 轮廓是八齿齿轮（齿顶为直线、有齿根），不再是「圆 + 放射线」。
- [x] 使用点、尺寸、描边与着色方式不变。
- [x] A1–A6 不因本片回退：整跑仍全 OK。

## TDD execution record

### RED — failing test first
- Test/scenario: A7 判「齿轮图标两端同源，且轮廓 ≥24 顶点」。
- Command: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py`
- Exit code: 1
- Observed failure: `FAIL A7 齿轮图标两端同源 — Web 侧没有一条顶点数 ≥ 24 的齿轮轮廓：放射线画的是太阳`。
- Failure reason validated: 两端当时都已改成同一份「圆 + 放射线」，所以「逐字相同」这一半是成立的；判据仍然判红，说明红的来源是顶点门槛（几何身份），不是字符串比对——这正是手抄问题之外真正要守的那一半。
- Evidence: `artifacts/evidence/ui-readability-parity-2026-10-25/091-red.txt`。

### GREEN — minimum implementation
- Implementation: 两端换成同一条 32 坐标点的八齿轮廓，中心孔保留为第二个 path。
- Command: `python -X utf8 .codex/scripts/dev/check_ui_asset_parity.py`
- Exit code: 0
- Passing evidence: `OK A7 齿轮图标两端同源 — 2 条 pathData 逐字相同，轮廓 31 个顶点`（`091-green.txt`）。
- Regression evidence: A1–A6 同次整跑全 OK；Android `IconAssetsTest` 与前端 `IconParityTest` 全绿（全量 108 套 / 949 用例、30 文件 / 210 用例）。

### REFACTOR — optional
- Cleanup: 无；pathData 是数据不是逻辑。
- Regression evidence: 不适用。

### LOOP — optional
- Command and rounds: 一轮红、一轮绿。
- Failure identity and repeat count: 单次单缺陷。
- Stop condition hit: A7 OK 且顶点 31 ≥ 24。
- Handoff: 104 注册 A7 进整跑。

### TDD or sizing exception — only with prior user approval
- Exception type: 无
- Reason: 不适用
- User approval: 不适用
- Alternative verification: 不适用

## Implementation notes

- 齿轮的判别写成几何门槛（顶点数），因为「太阳」与「齿轮」在字符串层面都只是 pathData；只有几何能区分设计意图。
- 两端仍各存一份字面量，本片把「碰巧一致」升级成「被判据绑住的一致」；构建期单源留给将来真正出现第三个消费端时再做。

## Verification report

- Verdict: PASS
- Evidence: `091-red.txt`／`091-green.txt`；当前态整跑见 `093-green.txt` 的 A7 行。
- Changed/deleted assertion rationale and equivalent behavior coverage: `IconAssetsTest` 的图标断言随 glyph 更新；覆盖面对「两端同源」由 A7 承担。
- Gaps: 没有 18dp 实际显示尺寸的截图比对。
- Risks: 密集轮廓在极小尺寸下可能糊；当前使用点为 20dp。

## Handoff notes

- Completed: 两端同一条八齿轮廓，A7 转绿。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 不适用。
- Next steps: 104 注册判据。
- Watch-outs: 改任一端都要同步另一端，否则 A7 会红——这是本片想要的效果，不要用豁免绕开。
