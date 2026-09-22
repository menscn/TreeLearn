#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-051：为两个历史文件各加一条指向 V3.2 的批注（只增行，不改内容行）。

- 先落盘 051 编辑前的字节快照到 .t051-base/，供 diff 证明「只有新增行」；
- 原位字节/换行符/BOM 一律保持，不改任何既有内容行。
"""

from __future__ import annotations

import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "artifacts" / "evidence" / ".t051-base"
BASE.mkdir(parents=True, exist_ok=True)

INC = ROOT / "Docs" / "01-demand" / "结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md"
HTML = ROOT / "Docs" / "04-ux-design" / "知芽-TreeLearn-高保真原型-v1.html"

INC_ANCHOR = "首版只有一个首页。根问题输入、模型与思考模式入口、推荐学习主题、历史主题列表、搜索、已归档和设置入口在同一首页组织，不新增第二套品牌首页或独立新手首页。历史主题仍以根分支标题作为唯一显示身份。"
INC_NOTE = (
    "> **批注（2026-09-22 · T-2026-09-22-051）：口径已被 V3.2 取代。** "
    "本段「首版只有一个首页」的口径**不再有效**；当前权威表述见 "
    "`Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md`：一级入口由底部标签栏承载"
    "（主页 / 学习库 / 设置），首页不再是唯一的入口页，主题列表与归档内容改由学习库承载，"
    "搜索的唯一入口是学习库顶部的搜索框。上段内容作为 V3.1 冻结历史**原样保留**，不随本批修改。"
)

HTML_ANCHOR = (
    '          <li><b>单一首页</b>：品牌头 + 根问题输入框（带书写格线）+ 推荐弹幕 + 历史主题列表 + '
    '搜索/归档/设置入口，全在一页（v1.2）</li>'
)
HTML_NOTE = (
    '          <li><b>【叙事已作废 · 2026-09-22 · T-2026-09-22-051】</b>'
    '本条「单一首页」与上一条「全站正文用霞鹜文楷」两处口径均已被 Figma 新稿与 '
    '<code>Docs/03-prd-draft/V3.2-知芽（TreeLearn）产品需求文档（PRD）.md</code> 取代：'
    '新口径下首页不再是唯一的入口页，一级入口由底部标签栏（主页 / 学习库 / 设置）承载，'
    '主题列表与归档内容归学习库，搜索唯一入口是学习库顶部搜索框；'
    '字体口径也已作废——正文改用系统 sans-serif、品牌字走衬线回退栈（已由 T-050 落地）。'
    '<b>但请注意</b>：作废的只是上述信息架构叙事，本文件的 <code>:root</code> 区块'
    '<b>仍是设计 token 的权威源</b>（数值已由 T-050 更新），不随该叙事一并作废。'
    '本条为新增批注，未改动被指向的原有文案。</li>'
)


def snapshot(path: Path) -> None:
    dst = BASE / path.name
    shutil.copy2(path, dst)
    print(f"SNAP {path.name} -> {dst.relative_to(ROOT)}")


def edit(path: Path, anchor: str, note: str, encoding: str) -> None:
    snapshot(path)
    raw = path.read_bytes()
    text = raw.decode(encoding)
    n = text.count(anchor)
    if n != 1:
        print(f"FAIL {path.name}: 锚点命中 {n} 次（应为 1），中止", file=sys.stderr)
        raise SystemExit(2)
    # 只插入新行，不动锚点行本身
    text = text.replace(anchor, anchor + "\n" + note, 1)
    path.write_bytes(text.encode(encoding))
    print(f"ANNOTATE {path.name}（新增 1 行，锚点行未改）")


def main() -> int:
    edit(INC, INC_ANCHOR, INC_NOTE, "utf-8")
    edit(HTML, HTML_ANCHOR, HTML_NOTE, "utf-8-sig")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
