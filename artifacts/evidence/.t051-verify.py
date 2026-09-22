#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-051 结构断言：新文件全量性、9 处改写落位、F 编号集合一致、推荐区 4 行未变。"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
V31 = ROOT / "Docs" / "03-prd-draft" / "V3.1-知芽（TreeLearn）产品需求文档（PRD）.md"
V32 = ROOT / "Docs" / "03-prd-draft" / "V3.2-知芽（TreeLearn）产品需求文档（PRD）.md"

failures: list[str] = []


def check(cond: bool, msg: str) -> None:
    print(("PASS " if cond else "FAIL ") + msg)
    if not cond:
        failures.append(msg)


# ---- 1. 新文件存在且为全量文档 ----
check(V32.is_file(), f"V3.2 存在：{V32.name}")
t31 = V31.read_text(encoding="utf-8")
t32 = V32.read_text(encoding="utf-8")
l31, l32 = t31.splitlines(), t32.splitlines()
print(f"INFO V3.1 行数={len(l31)}  V3.2 行数={len(l32)}")

h32 = [ln for ln in l32 if ln.startswith("# ")]
print("INFO V3.2 一级章节：")
for ln in h32:
    print("     " + ln)
check(len(h32) >= 8, "V3.2 一级章节数 >= 8（全量，非增量条）")
check("只记录变更点" not in t32, "V3.2 未出现「只记录变更点」的增量条形态")
check("V3.2 变更性质说明" in t32, "V3.2 开头含变更性质说明")
check("F01–F21 与 V3.1 完全一致" in t32, "V3.2 明示功能条目未增未减")


# ---- 2. 9 处改写逐条落位 ----
def locate(snippet: str) -> list[int]:
    return [i + 1 for i, ln in enumerate(l32) if snippet in ln]


REWRITES: list[tuple[int, str, str]] = [
    (1, "L91", "一级入口由底部标签栏承载（主页 / 学习库 / 设置）：主页负责“开始新建”"),
    (2, "L164", "A[用户进入主页] --> B[看到根问题输入框 / 推荐区 / 继续学习；回查历史主题走学习库]"),
    (3, "L227", "| F16 | 首页信息架构统一 | 【P0】 | 一级导航由底部标签栏承载，首页不重复承载主题列表，消除双主页割裂"),
    (4, "L693", "V3.2 起一级导航由底部标签栏承载，主题列表与历史回查统一归入学习库，该割裂由架构层面彻底消除。"),
    (5, "L703", "1. 首页不再是唯一的入口页；一级入口由底部标签栏承载"),
    (6, "L706", "4. 主题列表由学习库的「进行中」分段承载"),
    (7, "L742", "不得阻断首页输入与继续学习、以及学习库列表的使用。"),
    (8, "L902", "- **首页不重复承载主题列表**：首版一级入口由底部标签栏承载"),
    (9, "L977", "| **首页信息架构统一** | 一级入口由底部标签栏承载（主页/学习库/设置），首页不重复承载主题列表（F16）"),
    # ---- 第 10 条：执行时补入的覆盖面 ----
    (10, "L697", "1. 用户首次进入知芽（TreeLearn），在主页看到品牌区、根问题输入框与推荐起点"),
    (11, "L698", "2. 用户提交根问题并退出后，进入学习库即可在「进行中」分段看到自己的主题列表并直接点击继续"),
    (12, "L699", "3. 用户需要配置 Key 时点底部标签栏的「设置」，需要查看已归档内容时进入学习库的「已完成」分段"),
    (13, "L707", "5. 学习库「进行中」分段为空时展示引导空提示，并提供载入示例主题的入口"),
    (14, "L708", "6. 首版不再保留独立的空状态页与独立的新主题页；输入在主页、列表在学习库"),
    (15, "L712", "1. 若无学习记录，则学习库「进行中」分段显示引导空提示而非空白"),
    (16, "L714", "3. 若主题数量较多，则学习库列表通过纵向滚动承载，不新增分页或二级入口。"),
    (17, "L965", "| 根标题即主题身份 | 主题列表复用根标题；手改或刷新同步；无独立主题名（BASE-06） | 学习库的主题列表直接显示根分支标题"),
]
for idx, origin, snippet in REWRITES:
    hits = locate(snippet)
    check(len(hits) == 1, f"改写 {idx}（V3.1 {origin}）落位 V3.2 行 {hits}：{snippet[:32]}…")


# ---- 3. 功能条目零增删 ----
pat = re.compile(r"\| F[0-9]+")
f31 = sorted({m.group(0) for m in pat.finditer(t31)})
f32 = sorted({m.group(0) for m in pat.finditer(t32)})
check(f31 == f32, f"F 编号集合一致：{len(f31)} 个 -> {' '.join(f31)}")
if f31 != f32:
    print(f"INFO 差集 V3.1-only={set(f31) - set(f32)} V3.2-only={set(f32) - set(f31)}")


# ---- 4. 推荐区 4 行一字未改 ----
RECOMMEND_LINES = [
    "| 1 | 首页增加推荐主题，结合已学内容推荐，可点击，弹幕滚动 | F17 | 新增功能 |",
    "- 新增交互不得引入新的验收红线：滚动中的推荐项必须可稳定点击、长按选中必须有明确反馈、浮层菜单不得被屏幕边缘裁切、重置不得迁移已有子分支。（F17、F19、F20）",
    "| F17 | AI 推荐学习主题 | 【P0】 | 依据已学内容生成推荐主题，弹幕滚动展示，点击直接创建主题并开始对话 | BASE-22 |",
    "| **AI 推荐学习主题** | 依据已学内容推荐，滚动展示，点击直接创建主题开聊（F17） | 推荐区双行反向滚动，交互时暂停；分「顺着你学的 / 换个方向 / 入门」标签；点击直接创建主题并开始生成 | 一致 |",
]
for i, line in enumerate(RECOMMEND_LINES, start=1):
    in31 = l31.count(line)
    in32 = l32.count(line)
    check(in31 == 1 and in32 == 1, f"推荐区保护行 {i} 在 V3.1/V3.2 均恰出现 1 次（{in31}/{in32}）")
check("重点卡" not in t32, "V3.2 无「重点卡」表述")


# ---- 6. 扩围反向检索：5 个检索词在 V3.2 零命中、在 V3.1 有命中 ----
FORBIDDEN = ["唯一入口页", "无第二套首页", "头部右侧依次", "历史主题列表", "首页历史主题"]
for term in FORBIDDEN:
    n32 = t32.count(term)
    n31 = t31.count(term)
    check(n32 == 0 and n31 > 0, f"检索词「{term}」：V3.2 命中 {n32}（应 0）/ V3.1 命中 {n31}（应 >0）")

# ---- 7. 宽口径复核：旧口径主题词仍有的命中，逐条说明为何保留 ----
LEGIT_TERMS = ["历史主题", "品牌头", "两个主页"]
for term in LEGIT_TERMS:
    hits = [i + 1 for i, ln in enumerate(l32) if term in ln]
    print(f"INFO 宽口径「{term}」在 V3.2 命中行 {hits}（历史记录 / 新 IA 描述 / F17 推荐语义，逐行判定见证据文件第 [10] 节）")


# ---- 5. 改写面之外无其它差异 ----
only31 = [ln for ln in l31 if ln not in set(l32)]
only32 = [ln for ln in l32 if ln not in set(l31)]
print(f"INFO V3.1 独有行={len(only31)}  V3.2 独有行={len(only32)}")
for ln in only31:
    print("     -| " + ln[:70])
for ln in only32:
    print("     +| " + ln[:70])

print("SUMMARY failures=%d" % len(failures))
sys.exit(1 if failures else 0)
