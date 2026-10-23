#!/usr/bin/env python -X utf8
"""Capture the four glass-material screens from the connected device into this evidence folder.

This reuses the parity route helpers for id-based navigation and screenshots, but writes PNGs here
instead of overwriting the structural fixtures: the task asks for visual evidence, not new fixtures.
"""
import os
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
PARITY = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".codex", "scripts", "dev", "parity"))
sys.path.insert(0, PARITY)

import capture_screen as cap  # noqa: E402

FACTS = cap.device_facts()
PKG = "com.treelearn"


def nodes():
    return cap.parse_nodes(cap.dump_hierarchy(), FACTS["scale"])


def tap_id(rid, optional=False, settle_ms=1400):
    cap.tap({"by": "id", "value": rid, "optional": optional}, nodes(), FACTS)
    cap.sleep_ms(settle_ms)


def ensure_home():
    tap_id("tab_home", optional=True)
    for rid in ("chat_back", "navigation_back", "settings_back"):
        tap_id(rid, optional=True)


def shot(name, expect):
    current = nodes()
    ids = {n["id"] for n in current}
    if expect and expect not in ids:
        raise SystemExit("%s: missing arrival id %r (saw %d ids)" % (name, expect, len(ids)))
    png = cap.screenshot()
    if len(png) < 1000 or png[:8] != b"\x89PNG\r\n\x1a\n":
        raise SystemExit("%s: screenshot is not a PNG" % name)
    path = os.path.join(HERE, "%s-android.png" % name)
    with open(path, "wb") as fh:
        fh.write(png)
    print("CAPTURED %s nodes=%d expect=%s bytes=%d" % (name, len(current), expect, len(png)))


cap.launch_reset({"package": PKG, "serial": None})
cap.sleep_ms(2600)

ensure_home()
shot("home", "home_root")

tap_id("tab_library")
shot("library", "library_root")

ensure_home()
tap_id("home_topic_card")
shot("chat", "chat_root")

ensure_home()
tap_id("home_settings_button")
shot("settings", "settings_root")

print("DONE")
