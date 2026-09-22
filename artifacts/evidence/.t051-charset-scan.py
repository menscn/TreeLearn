#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""T-051：对改动文件做 UTF-8 替换字符扫描，确认无问号占位或乱码。"""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
FILES = [
    ROOT / "Docs" / "03-prd-draft" / "V3.2-知芽（TreeLearn）产品需求文档（PRD）.md",
    ROOT / "Docs" / "01-demand" / "结构化需求基准说明书_知芽（TreeLearn）_V3.1增量.md",
    ROOT / "Docs" / "04-ux-design" / "知芽-TreeLearn-高保真原型-v1.html",
]

bad = 0
for path in FILES:
    raw = path.read_bytes()
    try:
        raw.decode("utf-8")
        strict = "ok"
    except UnicodeDecodeError as exc:
        strict = f"FAIL({exc})"
        bad += 1
    text = raw.decode("utf-8", errors="replace")
    n = text.count("\ufffd")
    bad += n
    print(f"{path.relative_to(ROOT)}: utf8_strict={strict} U+FFFD={n} bytes={len(raw)}")

print(f"total_replacement_chars={bad}")
sys.exit(1 if bad else 0)
