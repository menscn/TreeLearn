#!/usr/bin/env python -X utf8
"""
阶段一采集仪器：把 Android 真机上的一屏拍成一份**可机械比较的结构夹具**（T-2026-10-18-019）。

为什么不是截图加人手抄的数：`screens.json` 那套像素墨迹档案每条屏要人工圈扫描窗口，
14 条屏全要基线时它供不上；而 `uiautomator dump` 直接给出每个节点的 resource-id、文本
与像素包围盒，按 display density 归一成逻辑 dp 之后，就是「Web 侧同一条记录该长成什么样」
的判据本体。截图仍然采，但它是给人看的第二份证据，不参与判定。

约定（改了等于改判据，必须在任务里显式记录并重跑同一屏）：
- 夹具信封字段与 `frontend/src/platform/RuntimePort.ts` 逐字相同：schema_version、screen、
  source_commit、captured_at_ms、device、payload。payload 是唯一允许自由生长的部分。
- dp = px / (density / 160)，四舍五入到 0.01dp；不减状态栏，改为把状态栏与导航栏高度作为
  inset 单独记录，避免「谁减过一次」在两份代码里各写一遍。
- 节点保留条件：有 resource-id、或有可见文本、或有 content-desc、或 clickable。其余是布局壳，
  留着只会让比对表读成噪声。
- 空集合一律判 FAIL：一次采到零节点、或到达判据（expect_root）没出现，都是「没采到被核对象」，
  不是「这屏本来就没东西」。

用法：
  python -X utf8 .codex/scripts/dev/parity/capture_screen.py --probe
  python -X utf8 .codex/scripts/dev/parity/capture_screen.py --screen chat
  python -X utf8 .codex/scripts/dev/parity/capture_screen.py --all
  python -X utf8 .codex/scripts/dev/parity/capture_screen.py --selftest
退出码：0=跑完且判据全成立 1=跑完且有判据判 FAIL 2=用法错误 3=仪器自身故障（缺 adb、设备不在、
路由表读不出、dump 不是合法 XML）。
"""
import argparse
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(HERE, "..", "..", "..", ".."))
ROUTE_FILE = os.path.join(HERE, "android_routes.json")
OUT_DIR = os.path.join(REPO, "artifacts", "fixtures", "parity")
DUMP_PATH = "/sdcard/treelearn_capture.xml"
SCHEMA_VERSION = 1
MIN_TEXT_NODES = 1

EXIT = {"OK": 0, "FAIL": 1, "USAGE": 2, "FAULT": 3}

BOUNDS = re.compile(r"^\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]$")


class Fault(Exception):
    """仪器自身故障：没有这一档就分不清「没采到」与「采到但判不过」。"""


class JudgementFail(Exception):
    """跑完了，但判据不成立。"""


def run(cmd, binary=False):
    try:
        proc = subprocess.run(cmd, shell=False, capture_output=True, timeout=90)
    except FileNotFoundError:
        raise Fault("找不到 adb（命令：%s）；本仪器需要一台真机或模拟器" % cmd[0])
    except subprocess.TimeoutExpired:
        raise Fault("命令超时：%s" % " ".join(cmd))
    if proc.returncode != 0:
        raise Fault("命令失败 rc=%d：%s\n%s" % (
            proc.returncode, " ".join(cmd), proc.stderr.decode("utf-8", "replace").strip()))
    return proc.stdout if binary else proc.stdout.decode("utf-8", "replace")


def adb(shell_cmd, serial=None, binary=False):
    base = ["adb"] + (["-s", serial] if serial else []) + ["shell"] + shell_cmd
    return run(base, binary=binary)


def device_facts(serial=None):
    size = adb(["wm", "size"], serial)
    dens = adb(["wm", "density"], serial)
    m = re.search(r"(\d+)x(\d+)", size)
    d = re.search(r"(\d+)", dens)
    if not m or not d:
        raise Fault("读不到屏幕尺寸或密度：%r / %r" % (size.strip(), dens.strip()))
    width_px, height_px = int(m.group(1)), int(m.group(2))
    dpi = int(d.group(1))
    scale = dpi / 160.0
    if scale <= 0:
        raise Fault("密度归一系数不成立：%s" % dpi)
    model = adb(["getprop", "ro.product.model"], serial).strip()
    return {"model": model, "width_px": width_px, "height_px": height_px,
            "dpi": dpi, "scale": scale, "serial": serial or ""}


ANIM_KEYS = ("window_animation_scale", "transition_animation_scale", "animator_duration_scale")


class QuietIdle:
    """采集期间把三支动画缩放压到 0，退出时逐键还原成它本来的值。

    为什么必须动设备设置：本 App 的对话页有持续失效的动效（ChatMotion / 骨架屏），
    uiautomator 等不到 idle 就 dump 不出层级——实测动画开着时 rc=0、stdout 空、
    stderr 是 `could not get idle state.`。缩放为 0 不改变静止布局，只给出可重复的静止帧；
    还原放在 finally 里，仪器崩了也不把用户设备留在关掉动画的状态。
    """

    def __init__(self, serial=None):
        self.serial = serial
        self.saved = {}

    def __enter__(self):
        for key in ANIM_KEYS:
            out = adb(["settings", "get", "global", key], self.serial).strip()
            self.saved[key] = out if out and "null" not in out else "1.0"
            adb(["settings", "put", "global", key, "0"], self.serial)
        return self

    def __exit__(self, *exc):
        for key, value in self.saved.items():
            adb(["settings", "put", "global", key, value], self.serial)
        print("ANIMATION_SCALES_RESTORED %s" % ", ".join("%s=%s" % (k, v) for k, v in sorted(self.saved.items())))
        return False


def dump_hierarchy(serial=None):
    last_err = ""
    for attempt in range(3):
        out = adb(["uiautomator", "dump", DUMP_PATH], serial)
        if "dumped to" in out:
            raw = run(["adb"] + (["-s", serial] if serial else []) + ["exec-out", "cat", DUMP_PATH],
                      binary=True)
            adb(["rm", "-f", DUMP_PATH], serial)
            if not raw.strip():
                raise JudgementFail("dump 回来是空的（0 字节）：屏没亮或被安全策略挡住")
            if not raw.lstrip()[:1] == b"<":
                raise Fault("cat 回来的不是 XML（前 120 字节：%r）" % raw[:120])
            return raw
        last_err = out.strip()
        time.sleep(0.8)
    raise JudgementFail("uiautomator 三次都没写出层级文件（第 %d 次：%s）；"
                        "若窗口仍在持续动效，确认动画缩放已被压到 0" % (3, last_err[:160]))


def screenshot(serial=None):
    """`screencap -p` 不带路径时把 PNG 直接写到 stdout，经 exec-out 回到本机，不在设备上留文件。"""
    return run(["adb"] + (["-s", serial] if serial else []) + ["exec-out", "screencap", "-p"],
               binary=True)


def sleep_ms(value):
    """路由表里的等待一律写毫秒；这里换算成秒。直接 sleep(2600) 会把一屏卡成 43 分钟。"""
    time.sleep(float(value) / 1000.0)


def to_dp(px, scale):
    return round(px / scale, 2)


def parse_nodes(xml_bytes, scale):
    try:
        root = ET.fromstring(xml_bytes.decode("utf-8", "replace"))
    except ET.ParseError as exc:
        raise Fault("dump 不是合法 XML：%s" % exc)
    nodes = []

    def walk(el, depth, parent):
        a = el.attrib
        rid = a.get("resource-id", "").split("/")[-1]
        text = a.get("text", "") or ""
        desc = a.get("content-desc", "") or ""
        clickable = a.get("clickable") == "true"
        keep = bool(rid) or bool(text) or bool(desc) or clickable
        if keep:
            m = BOUNDS.match(a.get("bounds", ""))
            if not m:
                raise JudgementFail("节点包围盒不合格：%r（class=%s）" % (a.get("bounds", ""), a.get("class", "")))
            l, t, r, b = (int(g) for g in m.groups())
            node = {"id": rid, "class": a.get("class", ""), "depth": depth, "parent": parent,
                    "bounds_dp": [to_dp(l, scale), to_dp(t, scale),
                                  to_dp(r - l, scale), to_dp(b - t, scale)],
                    "clickable": clickable, "scrollable": a.get("scrollable") == "true",
                    "enabled": a.get("enabled") != "false", "text": text, "desc": desc}
            nodes.append(node)
        for child in el:
            walk(child, depth + 1, rid if keep and rid else parent)

    walk(root, 0, "")
    return nodes


def insets_dp(nodes, facts):
    """状态栏高度 = 最靠上的节点上沿；导航条 = 视口高减去最靠下的节点下沿。都从本次实测里现场算。"""
    if not nodes:
        return {"status_bar_dp": 0.0, "nav_bar_dp": 0.0}
    top = min(n["bounds_dp"][1] for n in nodes)
    bottom = max(n["bounds_dp"][1] + n["bounds_dp"][3] for n in nodes)
    return {"status_bar_dp": round(top, 2),
            "nav_bar_dp": round(max(0.0, to_dp(facts["height_px"], facts["scale"]) - bottom), 2)}


def tap(step, nodes, facts):
    """按 id / 文本 / desc 在**当前这次 dump** 里定位一次点击；定位不到就判 FAIL，不猜坐标。"""
    by, value = step.get("by"), step.get("value")
    if by == "back":
        adb(["input", "keyevent", "4"], facts.get("serial") or None)
        return
    if by == "swipe":
        w, h = facts["width_px"], facts["height_px"]
        fx, fy = {"left": (int(w * 0.98), int(h * 0.5)), "right": (int(w * 0.02), int(h * 0.5)),
                  "up": (int(w * 0.5), int(h * 0.75)), "down": (int(w * 0.5), int(h * 0.25))}[value]
        tx, ty = {"left": (int(w * 0.1), int(h * 0.5)), "right": (int(w * 0.9), int(h * 0.5)),
                  "up": (int(w * 0.5), int(h * 0.28)), "down": (int(w * 0.5), int(h * 0.72))}[value]
        adb(["input", "swipe", str(fx), str(fy), str(tx), str(ty), "220"],
            facts.get("serial") or None)
        return
    if by not in ("id", "text", "desc"):
        raise JudgementFail("路由不认识这种定位方式：by=%r" % by)
    key = {"id": "id", "text": "text", "desc": "desc"}[by]
    match = next((n for n in nodes if n[key] == value and n["bounds_dp"][2] > 0), None)
    if match is None:
        if step.get("optional"):
            # 入口不在当前屏上：起点本来就是要找的那屏时，「再点一次主页」就是无操作。
            # 可选步只允许用在「把起点归一」的前几步；到达判据仍由 expect_root 把最后一道关。
            return
        raise JudgementFail("当前屏找不到 %s=%r（能定位的节点：%d 个）" % (by, value, len(nodes)))
    cx = int((match["bounds_dp"][0] + match["bounds_dp"][2] / 2) * facts["scale"])
    cy = int((match["bounds_dp"][1] + match["bounds_dp"][3] / 2) * facts["scale"])
    adb(["input", "tap", str(cx), str(cy)], facts.get("serial") or None)


def launch_reset(facts):
    pkg = facts["package"]
    adb(["am", "force-stop", pkg], facts["serial"] or None)
    adb(["monkey", "-p", pkg, "-c", "android.intent.category.LAUNCHER", "1"], facts["serial"] or None)


def current(scale, facts):
    nodes = parse_nodes(dump_hierarchy(facts["serial"] or None), scale)
    return nodes


def capture(screen, route, facts, quiet=False):
    scale = facts["scale"]
    steps = [s for preset in route.get("uses", []) for s in route["presets"][preset]]
    steps = steps + list(route.get("steps", []))
    if route.get("reset", "launch") == "launch":
        launch_reset({"package": route["package"], "serial": facts["serial"]})
        sleep_ms(route.get("settle_ms", 2600))
    else:
        sleep_ms(route.get("settle_ms", 700))
    for step in steps:
        nodes = parse_nodes(dump_hierarchy(facts["serial"] or None), scale)
        tap(step, nodes, facts)
        sleep_ms(step.get("settle_ms", route.get("settle_ms", 1400)))
    nodes = parse_nodes(dump_hierarchy(facts["serial"] or None), scale)
    ids = {n["id"] for n in nodes}
    expect = route.get("expect_root")
    if expect and expect not in ids:
        raise JudgementFail("到达判据不成立：期望根 id=%r，本次实到 id 里没有它（共 %d 个 id）"
                            % (expect, len(ids)))
    text_nodes = [n for n in nodes if n["text"]]
    if len(nodes) < int(route.get("min_nodes", 1)):
        raise JudgementFail("节点数下限不成立：min_nodes=%d got=%d"
                            % (int(route.get("min_nodes", 1)), len(nodes)))
    if len(text_nodes) < MIN_TEXT_NODES:
        raise JudgementFail("可见文本节点数下限不成立：got=%d" % len(text_nodes))
    png = screenshot(facts["serial"] or None)
    if len(png) < 1000 or png[:8] != b"\x89PNG\r\n\x1a\n":
        raise JudgementFail("截图不是合法 PNG（字节数 %d）" % len(png))
    head = run(["git", "-C", REPO, "rev-parse", "HEAD"])
    commit = head.strip()
    if not re.fullmatch(r"[0-9a-f]{40}", commit):
        raise Fault("读不到 40 位提交号：%r" % commit)
    shot_name = "%s-android.png" % screen
    envelope = {
        "schema_version": SCHEMA_VERSION,
        "screen": screen,
        "source_commit": commit,
        "captured_at_ms": int(time.time() * 1000),
        "device": "%s %dx%d@%ddpi%s" % (facts["model"], facts["width_px"], facts["height_px"],
                                       facts["dpi"], (" " + facts["serial"]) if facts["serial"] else ""),
        "payload": {
            "device": {"width_px": facts["width_px"], "height_px": facts["height_px"],
                       "dpi": facts["dpi"], "scale": scale,
                       "viewport_dp": [to_dp(facts["width_px"], scale), to_dp(facts["height_px"], scale)]},
            "insets_dp": insets_dp(nodes, facts),
            "root_id": expect or "",
            "shot": shot_name,
            "shot_sha256": hashlib.sha256(png).hexdigest(),
            "route": [{"by": s.get("by"), "value": s.get("value")} for s in steps],
            "node_count": len(nodes),
            "nodes": nodes,
        },
    }
    os.makedirs(OUT_DIR, exist_ok=True)
    json_path = os.path.join(OUT_DIR, "%s-android.json" % screen)
    with open(json_path, "w", encoding="utf-8") as fh:
        json.dump(envelope, fh, ensure_ascii=False, indent=1, sort_keys=False)
    with open(os.path.join(OUT_DIR, shot_name), "wb") as fh:
        fh.write(png)
    if not quiet:
        print("CAPTURED screen=%s nodes=%d text=%d json=%s bytes=%d png_bytes=%d" % (
            screen, len(nodes), len(text_nodes), os.path.relpath(json_path, REPO),
            os.path.getsize(json_path), len(png)))
    return envelope


def read_routes():
    if not os.path.isfile(ROUTE_FILE):
        raise Fault("路由表不存在：%s" % ROUTE_FILE)
    with open(ROUTE_FILE, encoding="utf-8") as fh:
        return json.load(fh)


def envelope_problems(env, screen):
    """回读校验：夹具是判据的输入，写坏了要在采集当场判 FAIL，而不是等前端读不懂再回头。"""
    bad = []
    for field in ("schema_version", "screen", "source_commit", "captured_at_ms", "device", "payload"):
        if field not in env:
            bad.append("缺字段 %s" % field)
    if env.get("screen") != screen:
        bad.append("screen=%r 与文件名 %r 不一致" % (env.get("screen"), screen))
    if not re.fullmatch(r"[0-9a-f]{40}", str(env.get("source_commit", ""))):
        bad.append("source_commit 不是 40 位十六进制")
    if not isinstance(env.get("captured_at_ms"), int) or env.get("captured_at_ms", 0) <= 0:
        bad.append("captured_at_ms 必须是正的 epoch 毫秒")
    nodes = (env.get("payload") or {}).get("nodes") or []
    if not nodes:
        bad.append("nodes 为空")
    return bad


def selftest():
    sections = []
    scale = 3.25

    def check(title, ok, detail):
        sections.append((title, ok, detail))
        print("%s %s — %s" % ("PASS" if ok else "FAIL", title, detail))

    xml = ('<?xml version="1.0"?><hierarchy rotation="0">'
           '<node resource-id="com.treelearn:id/home_root" class="android.widget.FrameLayout" '
           'bounds="[0,115][1200,2537]">'
           '<node resource-id="" class="android.widget.LinearLayout" bounds="[0,115][1200,300]">'
           '<node resource-id="com.treelearn:id/home_search" class="android.widget.TextView" '
           'text="搜索" bounds="[65,141][1135,284]" clickable="true"/></node></node></hierarchy>').encode("utf-8")
    nodes = parse_nodes(xml, scale)
    check("== 1. 节点保留条件", len(nodes) == 2,
          "布局壳（无 id 无文本不可点）被剔除，got=%d want=2" % len(nodes))
    check("== 2. dp 归一", nodes[0]["bounds_dp"] == [0.0, 35.38, 369.23, 745.23],
          "115px 在 3.25 密度下读作 %s dp" % nodes[0]["bounds_dp"][1])
    check("== 3. 父链沿用最近的有 id 节点", nodes[1]["parent"] == "home_root"
          and nodes[1]["depth"] == 3, "parent=%r depth=%d" % (nodes[1]["parent"], nodes[1]["depth"]))
    ins = insets_dp(nodes, {"height_px": 2652, "scale": scale})
    check("== 4. inset 现场算", ins["status_bar_dp"] == 35.38 and ins["nav_bar_dp"] == 35.39, str(ins))

    failed = None
    try:
        tap({"by": "id", "value": "nope"}, nodes, {"scale": scale, "width_px": 1200, "height_px": 2652})
    except JudgementFail as exc:
        failed = str(exc)
    check("== 5. 定位不到即 FAIL（不猜坐标）", failed is not None and "找不到 id" in failed, failed or "竟然点到了")

    bad = None
    try:
        parse_nodes('<hierarchy><node resource-id="com.treelearn:id/x" bounds="[0,0]"/></hierarchy>'.encode("utf-8"), scale)
    except JudgementFail as exc:
        bad = str(exc)
    check("== 6. 被保留节点的包围盒不合格即 FAIL", bad is not None, bad or "竟然接受了坏包围盒")

    got = parse_nodes(b'<?xml version="1.0"?><hierarchy></hierarchy>', scale)
    check("== 7. 空层级返回空表，由 min_nodes 当场判 FAIL", got == [],
          "got=%d（空表不抛异常，判定权在采集侧的下限校验）" % len(got))

    env = {"schema_version": SCHEMA_VERSION, "screen": "chat", "source_commit": "0" * 40,
           "captured_at_ms": 1, "device": "x", "payload": {"nodes": [{"id": "a"}]}}
    check("== 8. 合格信封无问题", envelope_problems(env, "chat") == [], str(envelope_problems(env, "chat")))
    wrong = dict(env, screen="home")
    check("== 9. screen 与文件名不一致被抓", envelope_problems(wrong, "chat") != [],
          str(envelope_problems(wrong, "chat")))
    nofield = {k: v for k, v in env.items() if k != "source_commit"}
    check("== 10. 缺必需字段被抓", any("source_commit" in p for p in envelope_problems(nofield, "chat")),
          str(envelope_problems(nofield, "chat")))
    empty_nodes = json.loads(json.dumps(env))
    empty_nodes["payload"]["nodes"] = []
    check("== 11. 空 nodes 判 FAIL", any("nodes 为空" in p for p in envelope_problems(empty_nodes, "chat")),
          "空集合不得读成通过")

    ok = all(s[1] for s in sections)
    print("SECTIONS=%d PASS=%d FAIL=%d" % (len(sections), sum(1 for s in sections if s[1]),
                                           sum(1 for s in sections if not s[1])))
    print("SELFTEST_AT=%s" % datetime.now(timezone.utc).isoformat())
    print("EXITCODE=%d" % (EXIT["OK"] if ok else EXIT["FAIL"]))
    return EXIT["OK"] if ok else EXIT["FAIL"]


def main(argv=None):
    ap = argparse.ArgumentParser(add_help=False)
    ap.add_argument("--screen")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--probe", action="store_true")
    ap.add_argument("--selftest", action="store_true")
    ap.add_argument("--serial")
    ap.add_argument("--no-reset", action="store_true")
    ap.add_argument("--quiet", action="store_true")
    opts, extra = ap.parse_known_args(argv)
    if extra:
        print("USAGE unknown=%s" % " ".join(extra))
        print(__doc__)
        return EXIT["USAGE"]
    if opts.selftest and (opts.screen or opts.all or opts.probe):
        print("USAGE --selftest 不与其他动作同时给")
        return EXIT["USAGE"]
    try:
        if opts.selftest:
            return selftest()
        with QuietIdle(opts.serial):
            return run_device(opts)
    except JudgementFail as exc:
        print("FAIL %s" % exc)
        print("EXITCODE=%d" % EXIT["FAIL"])
        return EXIT["FAIL"]
    except Fault as exc:
        print("FAULT %s" % exc)
        print("EXITCODE=%d" % EXIT["FAULT"])
        return EXIT["FAULT"]


def run_device(opts):
    facts = device_facts(opts.serial)
    if opts.probe:
        nodes = current(facts["scale"], facts)
        print("PROBE model=%s %dx%d@%d nodes=%d" % (facts["model"], facts["width_px"],
                                                    facts["height_px"], facts["dpi"], len(nodes)))
        for n in nodes:
            if n["id"] or n["text"]:
                print("  %-24s %-28s %-22s %s" % (n["id"], n["class"].split(".")[-1],
                                                  n["text"][:22], n["bounds_dp"]))
        print("EXITCODE=%d" % (EXIT["OK"] if nodes else EXIT["FAIL"]))
        return EXIT["OK"] if nodes else EXIT["FAIL"]
    routes = read_routes()
    wanted = sorted(routes["screens"]) if opts.all else ([opts.screen] if opts.screen else [])
    if not wanted:
        print("USAGE 需要 --screen <名> / --all / --probe / --selftest 之一")
        return EXIT["USAGE"]
    if not opts.all and opts.screen not in routes["screens"]:
        print("USAGE 路由表里没有屏 %r（可用：%s）" % (opts.screen, ", ".join(sorted(routes["screens"]))))
        return EXIT["USAGE"]
    bad = []
    for screen in wanted:
        route = dict(routes["screens"][screen])
        route["package"] = routes["package"]
        route["presets"] = routes["presets"]
        if opts.no_reset:
            route["reset"] = "none"
        try:
            env = capture(screen, route, facts, quiet=opts.quiet)
            problems = envelope_problems(env, screen)
            if problems:
                bad.append("%s: %s" % (screen, problems))
                print("FAIL screen=%s 回读校验不通过：%s" % (screen, problems))
        except JudgementFail as exc:
            bad.append("%s: %s" % (screen, exc))
            print("FAIL screen=%s %s" % (screen, exc))
    print("CAPTURE_TOTAL=%d BAD=%d out_dir=%s" % (len(wanted), len(bad), os.path.relpath(OUT_DIR, REPO)))
    for b in bad:
        print("  BAD %s" % b)
    print("EXITCODE=%d" % (EXIT["OK"] if not bad else EXIT["FAIL"]))
    return EXIT["OK"] if not bad else EXIT["FAIL"]


if __name__ == "__main__":
    sys.exit(main())
