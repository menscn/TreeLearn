# -*- coding: utf-8 -*-
"""三面网络探针：同一个 URL 分别「走注册表代理」「显式禁用代理」「交给 curl」各取一次读数。

用法：
    python -X utf8 .codex/scripts/dev/net_probe.py [URL ...]

这不是守门：它不判绿也不判红，只打印读数（与 `dev/count_*.py` 同一族）。它存在的原因是
`cdp_shot_selftest` / `verify_1to1_selftest` 这类要浏览器真渲染的仪器，红的时候有两种病因——
「产物回归」与「这台机器此刻出不去」——而整跑读数本身分辨不了。ROADMAP 的 D11② / D12② / D14
三次为这件事现写驱动，前两份留在 gitignore 的 `artifacts/tmp/` 里没人能复用，所以按 D13 的教训上收。

默认 URL 就是那条依赖的正文（`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html:9` 起的三个
`<script src>`，外加 example.com 作对照）：改动那三行时这个默认会跟着失效，
`check_pointer_liveness.py` 不查字符串，所以要人工核对——见 ROADMAP 的 D14。
退出码：探针跑完就是 0；一个 URL 都取不到时打 `NO-READING` 并以 3 退出（取不到读数不等于判绿）。
"""

import os
import subprocess
import sys
import urllib.error
import urllib.request

DEFAULT_URLS = [
    ("react", "https://unpkg.com/react@18.3.1/umd/react.development.js"),
    ("react-dom", "https://unpkg.com/react-dom@18.3.1/umd/react-dom.development.js"),
    ("babel", "https://unpkg.com/@babel/standalone@7.29.0/babel.min.js"),
    ("example", "https://example.com/"),
]
TIMEOUT = 25


def registry_proxy():
    try:
        import winreg
    except ImportError:
        return "非 Windows，没有注册表代理可走"
    key = r"Software\Microsoft\Windows\CurrentVersion\Internet Settings"
    try:
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key) as handle:
            enable = winreg.QueryValueEx(handle, "ProxyEnable")[0]
            server = winreg.QueryValueEx(handle, "ProxyServer")[0]
    except OSError as exc:
        return "读注册表失败: %s" % exc
    return "ProxyEnable=%s ProxyServer=%s" % (enable, server)


def fetch(url, use_proxy):
    if use_proxy:
        opener = urllib.request.build_opener()
    else:
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        with opener.open(url, timeout=TIMEOUT) as resp:
            return "status=%s bytes=%d" % (resp.status, len(resp.read()))
    except urllib.error.HTTPError as exc:
        return "HTTP %s" % exc.code
    except Exception as exc:  # 网络栈把失败包成什么就打什么，不在这里猜病因
        return "%s: %s" % (type(exc).__name__, str(exc)[:120])


def via_curl(url):
    try:
        proc = subprocess.run(
            ["curl", "-s", "-o", os.devnull, "-m", str(TIMEOUT), "-w", "%{http_code}", url],
            capture_output=True, text=True, encoding="utf-8", errors="replace")
    except OSError:
        return None
    return "http_code=%s curl_rc=%s" % (proc.stdout.strip(), proc.returncode)


def main(argv):
    targets = [(url, url) for url in argv[1:]] or DEFAULT_URLS
    print("registry : %s" % registry_proxy())
    readings = 0
    for label, url in targets:
        print("%-10s %s" % (label, url))
        for face, value in (("via-registry", fetch(url, True)), ("no-proxy", fetch(url, False)),
                            ("curl", via_curl(url))):
            if value is None:
                print("  %-12s 这一面没跑起来" % face)
                continue
            readings += 1
            print("  %-12s %s" % (face, value))
    if not readings:
        print("NO-READING 三面探针一条读数也没取到，本轮无从判断")
        return 3
    print("PROBE targets=%d readings=%d — 只打印读数，不判绿不判红" % (len(targets), readings))
    return 0


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
