# Codex Harness Task: Web 文案资源表与设备时区模块（补登卡）

id: T-2026-10-18-013
title: Web 文案资源表与设备时区模块（补登卡）
template_version: 2
task_type: implementation
status: verified
parent_requirement: T-2026-10-18-web-h5-android-parity-requirement-005.md
requirements_confirmed: true
generated_by: agent
owner: frontend-dev
owner_lane: frontend
stack: frontend
estimated_production_lines: 76
shared_code_touched: false
depends_on: [T-2026-10-18-web-frontend-skeleton-build-entry-011.md]
tdd_mode: required

## Requirement collaboration brief

- Problem/background: 005 父卡把 013 定义为「文案资源表与设备时区」，该工作已随 011/012 同轮交付进工作树，但卡片正文从未提交，`.codex/tasks/` 因此缺号——本卡补登，并同时修掉补登过程中实测出的真实判据缺口（见 RED），不是纯登记。
- Users/scenarios: 写屏的改动需要按 key 取简中文案；任何时刻展示都要按当前设备时区格式化，且用户跨时区后旧文案不能保持旧时区。
- Expected outcome: 用户可见文案只有 `src/copy` 一处来源（缺键大声失败）；时间只在展示这一帧换设备时区，状态层保留 epoch 毫秒。
- Core flow and reversibility: 文案表 → 查表入口 → 设备时区格式化 → 单测与守门；全部为新增文件，回退即删除两个模块与其测试，不影响已交付的 011/012。
- Inputs/outputs: 输入 `android/app/src/main/res/values/strings.xml` 的键名与 `Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §11 阶段 2；输出 `src/copy/**`、`src/utils/deviceTime.ts`、`test/copy/copy.test.ts`、`test/utils/deviceTime.test.ts`。
- State and exception handling: 缺键不做静默回退（抛 `MissingCopyKey` 并点名该键）；非法 epoch 毫秒与非法 date-only 形状一律 `RangeError`；时区缺分量时报出具体分量名。
- Constraints and trade-offs: 只做简中、不要求第二语言齐备（2026-10-15 疑虑 4 裁定）；时间文案绝不持久化；不引入 date-fns/dayjs 等依赖。
- AI recommendation and rationale: 判据缺口必须用变异实验暴露而不是靠读代码相信覆盖——本片正是这样发现小时进制无人判别的。
- User choice and custom additions: 用户授权「按 Harness 一站到底、过程中不要让我确定」，并要求改动按片提交本地仓库。
- AI-autonomous decisions: ① 013 用补登卡而非重写历史；② 新增断言取 `Date.UTC(2026,2,7,15,4,9)` 这一具体下午时刻并同时断言整串与小时切片。
- Facts: 本卡全部命令本轮真实执行，见 Verification report 与（013 片原有门禁产物 `...-013-gate.txt` 7 块、`OVERALL_EXITCODE=0` 本轮未改写，新证据另档）与 `artifacts/evidence/T-2026-10-18-web-frontend-slice-013-hourcycle-gap.txt`；`check_device_time.mjs` 自身的 3 条变异电池见同目录 `...-013-mutation.txt`（先于本卡交付）。
- Assumptions: 本表 15 个键中有 12 个与 `strings.xml` 同名同文（2026-10-20 复算 `text_mismatch=0`），另 3 个 `state_fixture_unavailable`/`state_service_unreachable`/`state_back` 是 Android 没有对应概念的 Web 独有状态，与 `zh-CN.ts` 头部注释一致。
- Unknowns: 键集完整差分尚未机械化（见 Gaps ①）。
- Risks: 若阶段 3 不补 `strings.xml` 键差分守门，「1:1」会在文案层失真。
- Deferred: 逐屏文案迁入、键集差分守门。
- Non-goals: 不做第二语言、不做时区持久化、不做日历/相对时间（"3 分钟前"）格式化。
- Skipped non-critical questions and reason: 未追问 `pad()` 是否换成 `Intl` 的 `2-digit` 直接输出，因为两种写法都被同一断言判定，属实现细节。

## Goal

用户可见文案只有 `src/copy` 一处来源；时间只在展示这一帧按设备时区格式化，状态层保留 epoch 毫秒。

## Scope

### In scope
- `frontend/src/copy/zh-CN.ts`（简中文案表，键名沿用 `android/app/src/main/res/values/strings.xml`）、`frontend/src/copy/index.ts`（`copyText`/`hasCopy`/`copyKeys`/`MissingCopyKey`）。
- `frontend/src/utils/deviceTime.ts`（`deviceTimeZone`/`formatDateTime`/`formatDateOnly` + `DeviceZoneOverride`）。
- 本轮新增：`frontend/test/utils/deviceTime.test.ts` 的 24 小时制断言。

### Out of scope
- 逐屏文案补齐（阶段 3）；第二语言（2026-10-15 疑虑 4 裁定不做）；时区持久化（禁止）。

### Behavior impact — applicable surfaces only
- Intended change and old behavior to preserve: 净新增模块，被 011 的壳层与 012 的适配器消费；Android 侧 `strings.xml` 与 `Guard.java` 行为不变。
- Producer/consumer/sibling entrypoints and platform branches (code evidence): 生产者 `src/copy/index.ts`、`src/utils/deviceTime.ts`；消费者 `frontend/src/**`（由 `check_user_copy_i18n.mjs` 与 `check_device_time.mjs` 各自扫描判定）；平板档不适用（疑虑 8：1:1 基线只有手机档）。
- Native bridge or independent-client compatibility/update requirements: N/A（无跨端契约正文，阶段七才与 `backend/` 对契约）。
- Behavior-neutral non-applicability reason, if applicable: 无。

## Documentation impact record

- Changed runtime facts: 前端规则「文案只经 `src/copy`」与「时间只在展示帧换设备时区」两条承诺现在都有被测实现与守门消费者。
- Authoritative code/config/migration evidence: `frontend/src/copy/index.ts`、`frontend/src/utils/deviceTime.ts`、`frontend/scripts/check_device_time.mjs`。
- Documentation surfaces checked: `.codex/rules/frontend.md`（设备时区与时间展示、user-copy guard 两节）、`Docs/spec/SPEC-2026-10-15-web-h5-android-parity.md` §11 阶段 2。
- Documentation files updated: 本卡；005 父卡的子片状态回写。
- Producer/consumer/sibling/compatibility paths checked: `copyKeys()` 与 `strings.xml` 的键对齐留到阶段 3 逐屏时按屏核对，本片不声明已对齐。
- Superseded phrases/examples reverse-searched: 无被取代正文。
- `documentation impact: none` reason and evidence, 若适用：不适用。
- Historical/PRD handling: 无。

## Acceptance criteria

- [x] `copyText` 缺键时抛 `MissingCopyKey` 并在消息里点名该键；不做静默回退。
- [x] `deviceTimeZone()` 每次调用现读，不缓存。
- [x] `formatDateTime` 拒绝非正/非安全的 epoch 毫秒；`formatDateOnly` 只放行 `YYYY-MM-DD`。
- [x] 跨时区与夏令时形状有断言覆盖（UTC+14 与 UTC-11 落不同自然日；America/New_York 冬夏差一小时）。
- [x] 本轮补齐：下午时刻必须输出 24 小时制（原 `deviceTime` 5 例全部取上午时刻，h23↔h12 互换无人判红）。
- [x] 与 `strings.xml` 的手工差分出结果：同名 12 键文本零失配，3 个 Web 独有键，390 个 Android 键尚未迁入（2026-10-20 复算：`fe=15 ax=402 only_fe=3 only_ax=390 text_mismatch=0`）。
- [x] `npx vitest run test/copy/copy.test.ts test/utils/deviceTime.test.ts` 与 `node scripts/check_device_time.mjs`（含 `--selftest`）全 rc=0。

## TDD execution record

### RED — failing test first
- Test/scenario: 变异实验（承重实测，不是回忆）——同时破坏两处：`copy/index.ts` 的 `hasCopy` 改成恒 `true`、`deviceTime.ts` 的 `hourCycle: "h23"` 改成 `"h12"`，看现有判据各自是否有人判红
- Command: `npx vitest run test/copy/copy.test.ts test/utils/deviceTime.test.ts`
- Exit code: 1
- Observed failure: `Test Files 1 failed | 1 passed (2)` / `Tests 1 failed | 7 passed (8)`——copy 侧判红一条（`× 缺键时大声失败并在消息里点名该键`）；deviceTime 侧 `✓ test/utils/deviceTime.test.ts (5 tests)` **全绿**。同轮 `node frontend/scripts/check_device_time.mjs` → `findings=0 rc=0`，也无人判红。即：小时进制在这 5 例里是**不可判定**的——原有断言全部取上午时刻，h12 下 `pick("hour")` 仍被 `pad()` 补成两位，而 dayPeriod 分量从不参与输出，所以 `15:04` 与 `03:04` 在断言里同形。
- Failure reason validated: 是——红在两个真实缺口上，且第二个缺口比第一个更糟（判据失明而非判据判红）。处置不是放宽判据：新增取下午时刻的断言，再跑同一条变异确认可红（`AssertionError: expected '2026-03-07 03:04:09' to be '2026-03-07 15:04:09'`，`Tests 1 failed | 5 passed (6)`），随后按 `sha256sum -c` 还原被变异的两份代码。

### GREEN — minimum implementation
- Implementation: `src/copy/{zh-CN.ts,index.ts}` + `src/utils/deviceTime.ts`（补登的既有交付）+ 本片新增的下午时刻断言
- Command: `npx vitest run test/copy/copy.test.ts test/utils/deviceTime.test.ts`
- Exit code: 0
- Passing evidence: `Test Files 2 passed (2)` / `Tests 9 passed (9)`（copy 3 + deviceTime 6）；`node scripts/check_device_time.mjs` → `scanned=9 exempt=utils/deviceTime.ts exempt-in-tree=true findings=0` / `device time ok — 时间展示只经 src/utils/deviceTime.ts 一处` rc=0；`node scripts/check_device_time.mjs --selftest` → `SELFTEST cases=11 floor=8 fail=0`
- Regression evidence: `pnpm --dir frontend verify` → `verify steps=11 ok=11 bad=0 fault=0` / `VERIFY PASS` / `OVERALL_EXITCODE=0`；变异还原后 `sha256sum -c artifacts/tmp/card013/before.sha` → `frontend/src/utils/deviceTime.ts: OK` 与 `frontend/src/copy/index.ts: OK`（证据见 `artifacts/evidence/T-2026-10-18-web-frontend-slice-013-hourcycle-gap.txt`）

### REFACTOR — optional
- Cleanup: 下午时刻断言同时断言整串、小时切片，并显式拒绝 `上午|下午|AM|PM` 泄漏，避免只测一个字符位置。
- Regression evidence: 该断言在 h12 变异下判红、还原后判绿，双向都真实跑过；`check_device_time.mjs` 的判决文案与判据面未改。

### TDD or sizing exception — only with prior user approval
- Exception type: 补登卡（工作先于卡片交付）
- Reason: 005 父卡已在册，013 号子卡正文从未提交；本轮以现存代码与实测重建记录，并顺带修掉实测发现的断言缺口。
- User approval: 用户 2026-10-18 授权「按 Harness 一站到底、过程中不要让我确定」，并要求改动按片提交本地仓库。
- Alternative verification: 全部判据本轮真实重跑（见 Verification report 命令清单），不引用任何未复现的历史结论。

## Implementation notes

- 规模 76 行（命令：`python -X utf8 .codex/scripts/dev/count_production_lines.py frontend/src/copy/index.ts frontend/src/copy/zh-CN.ts frontend/src/utils/deviceTime.ts`，分项 21/16/39）。
- 文案表把「缺键」做成抛错而不是回退键名：`RESOURCES[key] === undefined` 即 `MissingCopyKey`，与 015 的守门形成同一语义（静默回退会把「这条文案没写」读成「文案就是这样」）。
- `formatDateTime` 用 `formatToParts` 逐项取值而非整串正则切分，所以时区缺分量时会定点报「未给出 X 分量」，不会输出半截时间。
- 豁免注释（`i18n-ignore`）加在本模块 3 处诊断字符串上：它们是日志/测试断言用的 detail，不到达 UI；UI 状态文案仍走 `src/copy`。

## Verification report

- Verdict: PASS（独立复核，2026-10-20：七条验收全绿；文案表计数按当日现场复算为 `fe=15 / ax=402 / only_fe=3 / only_ax=390 / text_mismatch=0`，取代旧快照的 14/2/389/401。）
- Evidence: 单元证据 = 全量 `vitest run` 11 文件 51 例全绿，其中 copy 3 例 + deviceTime 6 例（`artifacts/evidence/web-parity-2026-10-20/frontend-gates.txt`）；守门证据 = `check_device_time.mjs` rc=0 且 `--selftest` 11 例 fail=0；变异证据 = h12 变异判红（`03:04:09` vs `15:04:09`）与 `hasCopy` 变异判红（`缺键时大声失败并在消息里点名该键` 一例红），两次变异均以 `sha256sum -c` 确认还原；汇总证据 = `pnpm --dir frontend verify` 现为 16 步 rc=0（旧记录 11 步已过期）。环境：本机 Windows / node 24.18。
- Changed/deleted assertion rationale and equivalent behavior coverage: 无断言被删；`hasCopy` 那条变异只是临时破坏、已还原，正式代码仍按原语义判定。
- Risks: 键名与 Android `strings.xml` 的逐条对齐目前是 2026-10-20 的手工复算（`fe=15 ax=402 only_fe=3 only_ax=390`，同名键文本零失配），尚无机械守门；后续逐屏继续迁移时必须把它做成一支差分脚本，否则「1:1」会在文案层失真——`only_ax=390` 说明绝大多数 Android 文案还没有 Web 对应物。
- Gaps: ① 文案表只有 15 条，Android 侧 402 条里的 390 条尚未迁入（逐屏迁移继续）；② `check_device_time.mjs` 只判「谁调用了时间 API」，不判输出格式，格式判据靠本轮新增的单测；③ 独立复核轮已于 2026-10-20 由本期 verifier 完成，结论记在本节 Verdict 行。

## Handoff notes

- Completed: 文案表与设备时区模块在册并通过全部判据；下午时刻断言缺口已闭合。
- In progress: 无。
- Blocker: 无。
- Unblock condition: 无。
- Next steps: 阶段一夹具采集（清单全条目）→ 阶段 3 逐屏 1:1（每屏迁入自身文案并补 strings.xml 键差分守门）。
- Watch-outs: ① 时区断言必须至少有一条取下午时刻，否则小时进制无判据；② 时间文案不得持久化，状态层只留 epoch 毫秒；③ 新增可见文案要走 `src/copy`，否则 `pnpm lint` 当场判红。
