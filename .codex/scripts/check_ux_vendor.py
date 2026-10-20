"""原型第三方依赖的字节身份守门（ROADMAP D14）。

用法（从任意目录）：
    python -X utf8 .codex/scripts/check_ux_vendor.py            # 判真实文件
    python -X utf8 .codex/scripts/check_ux_vendor.py --selftest # 在临时副本里注入缺陷，逐条证明会响

为什么需要它：`Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html` 的界面是 React+Babel **在页面里现场编译**
出来的。这三个脚本原先从 `unpkg.com` 取，于是「本地优先」的 Web 渲染判据实际上把可用性押在第三方 CDN
与一台机器的代理设置上——代理一不通，`cdp_shot_selftest` 判红、`verify_1to1_selftest` 报仪器故障，
而读数本身分不出「原型回归」与「这台机器此刻出不去」（ROADMAP D11③/D14 各留了一次实测读数）。
2026-10-20 用户裁定按 D14 修法 ① 把三个脚本收编进 `Docs/04-ux-design/vendor/`。本脚本守的就是这次收编：
`Docs/04-ux-design/vendor/manifest.json` 声明的字节身份 ↔ 磁盘上的真实字节 ↔ 原型 `<head>` 的实际引用，
三方任一处漂移即红。

它同时是那条边界的仪器：结构渲染**不许**有任何 `http(s)://` 的 `<script src>`；字体样式表仍留在公网
（装饰性依赖，缺了只影响字形不影响结构），但它们必须逐条记在 manifest 里——新增一个公网面而不登记，
这条守门就红。没有这一步，「默认不引入远程依赖」在项目里又只是一句主张。

期望 sha384 的权威落点是 `Docs/04-ux-design/vendor/manifest.json`，不由本脚本硬编码一份，也不抄回 HTML：
HTML 里当时确实带过 `integrity`，但实测 `Edg/155.0.4283.45` 在 `file://` 下会对带 `integrity` 或
`crossorigin` 的本地脚本按 CORS 模式取子资源并拒绝执行（`#root` 停在 0），所以那两个属性已从标签上摘掉。
校验位置因此从「浏览器执行前」移到「守门比对」——校验本身一项没少，而且变成断网也判得动的比对。

退出码：0 = 全部判绿；1 = 至少一条 FAIL；3 = `--selftest` 自身故障（夹具不健康或用例数对不上）。
本脚本**不联网**：所有比对读本地字节，否则它就变成了它要防的那条依赖。
"""

import base64
import hashlib
import io
import json
import os
import re
import shutil
import sys
import tempfile
from contextlib import redirect_stdout

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from harness_check_lib import Checks, enable_utf8_stdout, exists, read_text, repo_path  # noqa: E402

PROTOTYPE = "Docs/04-ux-design/知芽-TreeLearn-高保真原型-v1.html"
MANIFEST = "Docs/04-ux-design/vendor/manifest.json"
VENDOR_DIR = "Docs/04-ux-design/vendor"
SCHEMA = "treelearn-ux-vendor/1"

# 下限全部写在裸字面量上，不由被检集合推导：清空 manifest 的 scripts 列表若仍是绿，
# 这条守门就只剩「读到了 JSON」这一点点用处（ROADMAP D1 那一族）。
# 实测当前为 3 个脚本 / 4 个公网面；只会在判据被删窄时先红，增删依赖要显式改这两个数。
MIN_SCRIPTS = 3
MIN_REMOTE_FACES = 4
# 自检用例数的裸下限：分支被静默删空时守门会「零用例全过」地打 PASS（本仓库同型缺陷命中过三次）。
MIN_SELFTEST_CASES = 21

SCRIPT_TAG_RE = re.compile(r"<script\b[^>]*>", re.I)
LINK_TAG_RE = re.compile(r"<link\b[^>]*>", re.I)
ATTR_RE = re.compile(r"""\b(src|href|integrity|crossorigin)\s*=\s*["']([^"']*)["']""", re.I)
EXTERNAL_RE = re.compile(r"^(?:https?:)?//", re.I)
SRI_ATTR_RE = re.compile(r"\b(integrity|crossorigin)\s*=", re.I)


def _integrity(data):
    return "sha384-" + base64.b64encode(hashlib.sha384(data).digest()).decode("ascii")


def _read_bytes(rel, root=None):
    with io.open(repo_path(rel, root), "rb") as handle:
        return handle.read()


def _attr_map(tag):
    return dict((k.lower(), v) for k, v in ATTR_RE.findall(tag))


def script_srcs(html):
    """返回 [(src, 是否带 SRI 属性)]，只含带 `src` 的 `<script>` 标签。"""
    out = []
    for tag in SCRIPT_TAG_RE.finditer(html):
        text = tag.group(0)
        found = _attr_map(text)
        if "src" in found:
            out.append((found["src"], bool(SRI_ATTR_RE.search(text))))
    return out


def link_urls(html):
    """按出现顺序返回 HTML 里所有指向外网的 `<link href>`，去重。"""
    seen = []
    for tag in LINK_TAG_RE.finditer(html):
        href = _attr_map(tag.group(0)).get("href", "")
        if EXTERNAL_RE.match(href) and href not in seen:
            seen.append(href)
    return seen


def load_manifest(root, c):
    """读并解析 manifest；任何一步不成立都判红并返回 None（调用方据此停在这条前置上）。"""
    if not exists(MANIFEST, root):
        c.fail("vendor manifest present", "path={0}".format(MANIFEST))
        return None
    try:
        raw = _read_bytes(MANIFEST, root)
    except OSError as exc:                       # 存在但读不动＝无从认证，不能当没这回事
        c.fail("vendor manifest readable", "error={0}".format(exc))
        return None
    try:
        text = raw.decode("utf-8")
    except UnicodeDecodeError as exc:
        c.fail("vendor manifest readable", "not-utf8 offset={0}".format(exc.start))
        return None
    try:
        doc = json.loads(text)
    except ValueError as exc:
        c.fail("vendor manifest parses as JSON", "error={0}".format(exc))
        return None
    if not isinstance(doc, dict):
        c.fail("vendor manifest parses as JSON", "top-level={0}".format(type(doc).__name__))
        return None
    c.ok("vendor manifest parses as JSON", "keys={0}".format(sorted(doc)))
    c.check(doc.get("schema") == SCHEMA, "vendor manifest schema",
            "found={0!r} want={1!r}".format(doc.get("schema"), SCHEMA))
    scripts = doc.get("scripts")
    if not isinstance(scripts, list):
        c.fail("vendor manifest parses as JSON", "scripts type={0}".format(type(scripts).__name__))
        return None
    if not c.check(len(scripts) >= MIN_SCRIPTS, "vendor manifest script floor",
                   "measured={0} floor={1}".format(len(scripts), MIN_SCRIPTS)):
        return None
    return doc


def check_bytes(root, c, scripts):
    """磁盘字节 ↔ manifest：长度与摘要分开判，坏在哪一格就报哪一格。"""
    names = []
    for entry in scripts:
        name = str(entry.get("file", ""))
        names.append(name)
        if not name or name != os.path.basename(name):
            c.fail("vendor manifest entry shape",
                   "file={0!r} 必须只是 vendor/ 下的一个文件名".format(name))
            continue
        rel = "{0}/{1}".format(VENDOR_DIR, name)
        if not exists(rel, root):
            c.fail("vendor script absent:" + name, "path={0}".format(rel))
            continue
        data = _read_bytes(rel, root)
        declared = str(entry.get("integrity", ""))
        computed = _integrity(data)
        c.check(declared == computed, "vendor script digest:" + name,
                "declared={0} measured={1} bytes={2}".format(declared, computed, len(data)))
        c.check(int(entry.get("bytes", -1)) == len(data), "vendor script size:" + name,
                "declared={0} measured={1}".format(entry.get("bytes"), len(data)))
    dup = sorted({n for n in names if names.count(n) > 1})
    c.check(not dup, "vendor manifest duplicate file", "duplicated={0}".format(dup))
    return set(n for n in names if n)


def check_references(root, c, html, scripts):
    """HTML ↔ manifest：引用一一对应；结构渲染不许碰外网脚本；本地脚本不许带 SRI 属性。"""
    wanted = dict(("./vendor/{0}".format(s["file"]), 1)
                  for s in scripts if s.get("file"))
    external, sri, referenced, undeclared = [], [], set(), []
    for src, has_sri in script_srcs(html):
        if EXTERNAL_RE.match(src):
            external.append(src)
        elif not src.startswith("./vendor/"):
            c.fail("vendor reference shape", "既不是 vendor 也不是外网的脚本引用：{0}".format(src))
        else:
            referenced.add(src)
            if src not in wanted:
                undeclared.append(src)
        if has_sri:
            sri.append(src)
    c.check(not external, "no external script src",
            "外网脚本引用={0}".format(external))
    c.check(not sri, "local script free of integrity/crossorigin",
            "带 SRI 属性的本地标签={0}".format(sri))
    c.check(not undeclared, "undeclared vendor reference",
            "HTML 引用了但没登记={0}".format(undeclared))
    c.check(not sorted(set(wanted) - referenced), "vendor reference present",
            "登记了但 HTML 没引用={0}".format(sorted(set(wanted) - referenced)))


def check_remote(root, c, html, doc):
    """仍留在公网的面必须逐条登记；两边各自漂移都要红。"""
    remote = doc.get("remote")
    if not isinstance(remote, list):
        c.fail("vendor manifest parses as JSON", "remote type={0}".format(type(remote).__name__))
        return
    declared = [str(r.get("url", "")) for r in remote if isinstance(r, dict) and r.get("url")]
    if not c.check(len(declared) >= MIN_REMOTE_FACES, "vendor manifest remote floor",
                   "measured={0} floor={1}".format(len(declared), MIN_REMOTE_FACES)):
        return
    found = link_urls(html)
    missing = [u for u in found if u not in declared]
    stale = [u for u in declared if u not in found]
    c.check(not missing, "remote face declared",
            "没登记的公网面={0}".format(missing[:1]))
    c.check(not stale, "remote face still live",
            "登记了但已不在 HTML={0}".format(stale[:1]))


def check_dir_extras(root, c, declared_files):
    """vendor/ 里不许有没登记的文件：半个替换能躲过上面每一条按名字比对的判据。"""
    base = repo_path(VENDOR_DIR, root)
    if not os.path.isdir(base):
        c.fail("vendor dir present", "path={0}".format(VENDOR_DIR))
        return
    extras = [n for n in sorted(os.listdir(base))
              if n != "manifest.json" and os.path.isfile(os.path.join(base, n))
              and n not in declared_files]
    c.check(not extras, "vendor dir has only declared files",
            "磁盘上有但没登记={0}".format(extras))


def judge(root=None):
    """跑完全部判据并把逐条读数打到 stdout，返回 Checks（自检要拿它的失败集）。"""
    c = Checks("check_ux_vendor")
    if not exists(PROTOTYPE, root):
        c.fail("prototype present", "path={0}".format(PROTOTYPE))
        return c
    doc = load_manifest(root, c)
    if doc is None:
        c.note("checks skipped", "manifest 读不出来，后面的比对没有对象（这一行不是绿）")
        return c
    c.check(doc.get("prototype") == PROTOTYPE, "vendor manifest points at the prototype",
            "declared={0!r} want={1!r}".format(doc.get("prototype"), PROTOTYPE))
    html = read_text(PROTOTYPE, root)
    declared = check_bytes(root, c, doc["scripts"])
    check_references(root, c, html, doc["scripts"])
    check_remote(root, c, html, doc)
    check_dir_extras(root, c, declared)
    return c


def run(root=None):
    return judge(root).finish()


# ── 自检：在真文件的临时副本里注入缺陷，证明每一条判据都会响 ──
# 夹具是**当前真实的**原型 + vendor + manifest，所以正控不需要手抄期望值（ROADMAP D4）。
# 断言只认 Checks 的失败集成员，不认「标签字符串在输出里出现过」：同一个标签在 OK 行也在场，
# 拿子串判会把「分支坏了自己不响」读成用例通过。

def _copy_fixture(work):
    os.makedirs(repo_path(VENDOR_DIR, work), exist_ok=True)
    for rel in (PROTOTYPE, MANIFEST):
        shutil.copyfile(repo_path(rel), repo_path(rel, work))
    base = repo_path(VENDOR_DIR)
    for name in sorted(os.listdir(base)):
        if name.endswith(".js"):
            shutil.copyfile(os.path.join(base, name), repo_path("{0}/{1}".format(VENDOR_DIR, name), work))


def _mutate_html(work, old, new, count=1):
    rel = repo_path(PROTOTYPE, work)
    with io.open(rel, encoding="utf-8", newline="") as handle:
        text = handle.read()
    assert old in text, "注入目标文本不在场，夹具已过期：{0!r}".format(old)
    with io.open(rel, "w", encoding="utf-8", newline="") as handle:
        handle.write(text.replace(old, new, count))


def _read_doc(work):
    with io.open(repo_path(MANIFEST, work), encoding="utf-8") as handle:
        return json.load(handle)


def _write_doc(work, doc):
    with io.open(repo_path(MANIFEST, work), "w", encoding="utf-8", newline="\n") as handle:
        json.dump(doc, handle, ensure_ascii=False, indent=2)
        handle.write("\n")


def _patch_doc(work, key, value):
    doc = _read_doc(work)
    doc[key] = value
    _write_doc(work, doc)


def _patch_script(work, field, value):
    doc = _read_doc(work)
    doc["scripts"][0][field] = value
    _write_doc(work, doc)


def _vendor_path(work, name):
    return repo_path("{0}/{1}".format(VENDOR_DIR, name), work)


def case_external_script(work):
    _mutate_html(work, '<script src="./vendor/babel.min.js">',
                 '<script src="https://unpkg.com/@babel/standalone/babel.min.js">')


def case_protocol_relative(work):
    _mutate_html(work, '<script src="./vendor/react.development.js">',
                 '<script src="//unpkg.com/react/umd/react.development.js">')


def case_integrity_attr(work):
    _mutate_html(work, '<script src="./vendor/react.development.js">',
                 '<script src="./vendor/react.development.js" integrity="sha384-AAA=">')


def case_crossorigin_attr(work):
    _mutate_html(work, '<script src="./vendor/react.development.js">',
                 '<script src="./vendor/react.development.js" crossorigin="anonymous">')


def case_delete_vendor(work):
    os.remove(_vendor_path(work, "react.development.js"))


def case_flip_byte(work):
    path = _vendor_path(work, "react.development.js")
    with io.open(path, "rb") as handle:
        data = bytearray(handle.read())
    data[10] = (data[10] + 1) % 256
    with io.open(path, "wb") as handle:
        handle.write(bytes(data))


def case_append_byte(work):
    with io.open(_vendor_path(work, "react.development.js"), "ab") as handle:
        handle.write(b"\n")


def case_delete_manifest(work):
    os.remove(repo_path(MANIFEST, work))


def case_manifest_not_json(work):
    with io.open(repo_path(MANIFEST, work), "w", encoding="utf-8", newline="\n") as handle:
        handle.write("{not json")


def case_manifest_not_utf8(work):
    with io.open(repo_path(MANIFEST, work), "wb") as handle:
        handle.write(b"\xff\xfe\x00broken")


def case_manifest_scripts_empty(work):
    _patch_doc(work, "scripts", [])


def case_manifest_wrong_digest(work):
    _patch_script(work, "integrity",
                  "sha384-" + base64.b64encode(b"not-the-real-digest").decode("ascii"))


def case_remote_face_undeclared(work):
    """登记数仍够下限，但其中一条被换成 HTML 里没有的 URL：只可能由比对判红，不可能由下限判红。"""
    doc = _read_doc(work)
    doc["remote"][1]["url"] = "https://cdn.example.com/swapped-face.css"
    _write_doc(work, doc)


def case_remote_face_stale(work):
    _mutate_html(work, '<link rel="stylesheet" '
                       'href="https://cdn.jsdelivr.net/npm/lxgw-wenkai-webfont@1.7.0/'
                       'lxgwwenkai-bold.css">', "")


def case_new_external_link(work):
    _mutate_html(work, "<style>",
                 '<link rel="stylesheet" href="https://cdn.example.com/new-face.css">\n<style>')


def case_reference_removed(work):
    _mutate_html(work, '<script src="./vendor/react-dom.development.js"></script>\n', "")


def case_reference_undeclared(work):
    _mutate_html(work, '<script src="./vendor/babel.min.js"></script>',
                 '<script src="./vendor/babel.min.js"></script>\n'
                 '<script src="./vendor/sneaked-in.js"></script>')


def case_stray_vendor_file(work):
    with io.open(_vendor_path(work, "sneaked-in.js"), "wb") as handle:
        handle.write(b"console.log(1)")


def case_schema_bump(work):
    _patch_doc(work, "schema", SCHEMA + "-draft")


def case_prototype_path(work):
    _patch_doc(work, "prototype", "Docs/04-ux-design/另一个原型.html")


def case_prototype_missing(work):
    os.remove(repo_path(PROTOTYPE, work))


CASES = [
    ("external-script-src", case_external_script, "no external script src"),
    ("protocol-relative-script", case_protocol_relative, "no external script src"),
    ("integrity-on-local-script", case_integrity_attr, "local script free of integrity/crossorigin"),
    ("crossorigin-on-local-script", case_crossorigin_attr,
     "local script free of integrity/crossorigin"),
    ("vendor-file-deleted", case_delete_vendor, "vendor script absent:react.development.js"),
    ("vendor-byte-flipped", case_flip_byte, "vendor script digest:react.development.js"),
    ("vendor-byte-appended", case_append_byte, "vendor script size:react.development.js"),
    ("manifest-deleted", case_delete_manifest, "vendor manifest present"),
    ("manifest-not-json", case_manifest_not_json, "vendor manifest parses as JSON"),
    ("manifest-not-utf8", case_manifest_not_utf8, "vendor manifest readable"),
    ("manifest-scripts-cleared", case_manifest_scripts_empty, "vendor manifest script floor"),
    ("manifest-digest-wrong", case_manifest_wrong_digest,
     "vendor script digest:react.development.js"),
    ("remote-face-undeclared", case_remote_face_undeclared, "remote face declared"),
    ("remote-face-stale", case_remote_face_stale, "remote face still live"),
    ("new-cdn-link-added", case_new_external_link, "remote face declared"),
    ("declared-script-unreferenced", case_reference_removed, "vendor reference present"),
    ("undeclared-script-referenced", case_reference_undeclared, "undeclared vendor reference"),
    ("stray-file-in-vendor-dir", case_stray_vendor_file, "vendor dir has only declared files"),
    ("schema-changed", case_schema_bump, "vendor manifest schema"),
    ("prototype-path-wrong", case_prototype_path, "vendor manifest points at the prototype"),
    ("prototype-deleted", case_prototype_missing, "prototype present"),
]

# 每个用例可能动到的文件；注入后只回写真变了的，避免每例重写 4.3 MB。
TOUCHED = (PROTOTYPE, MANIFEST) + tuple(
    "{0}/{1}".format(VENDOR_DIR, n) for n in
    ("react.development.js", "react-dom.development.js", "babel.min.js", "sneaked-in.js"))


def selftest():
    print("Command: python -X utf8 .codex/scripts/check_ux_vendor.py --selftest")
    work = tempfile.mkdtemp(prefix="tl-ux-vendor-")
    cases = bad = 0
    try:
        print("Workspace: {0}".format(work))
        _copy_fixture(work)
        buf = io.StringIO()
        with redirect_stdout(buf):
            base = judge(work)
        if base.failures:
            # 健康夹具自己不绿 ⇒ 后面每一例的判决都不可信；中止并判仪器故障，不要跑完再解释为什么全红。
            print("INSTRUMENT FAULT: healthy fixture copy did not pass; failures={0}; aborting, "
                  "the {1} injected cases would prove nothing".format(base.failures, len(CASES)))
            print("OVERALL: INCONCLUSIVE")
            print("EXITCODE=3")
            return 3
        print("BASELINE fixture checks_failed=0")
        originals = {}
        for rel in TOUCHED:
            if os.path.isfile(repo_path(rel, work)):
                originals[rel] = _read_bytes(rel, work)
        for name, mutate, label in CASES:
            mutate(work)
            buf = io.StringIO()
            with redirect_stdout(buf):
                c = judge(work)
            cases += 1
            ok = bool(c.failures) and label in c.failures
            print("CASE {0:02d} {1:<32} failed={2:<2} label={3} -> {4}".format(
                cases, name, len(c.failures), label, "ok" if ok else "MISMATCH"))
            if not ok:
                bad += 1
                print("  failures={0}".format(c.failures[:4]))
            for rel in TOUCHED:
                path = repo_path(rel, work)
                data = originals.get(rel)
                if data is None:
                    if os.path.isfile(path):      # 用例新建的文件（stray / sneaked-in）
                        os.remove(path)
                elif not os.path.isfile(path) or _read_bytes(rel, work) != data:
                    with io.open(path, "wb") as handle:
                        handle.write(data)
        print("SELFTEST cases={0} mismatch={1} expected={2} floor={3}".format(
            cases, bad, len(CASES), MIN_SELFTEST_CASES))
    finally:
        shutil.rmtree(work, ignore_errors=True)
    if bad:
        print("OVERALL: FAIL")
        print("EXITCODE=1")
        return 1
    if cases != len(CASES) or cases < MIN_SELFTEST_CASES:
        print("OVERALL: INCONCLUSIVE")
        print("EXITCODE=3")
        return 3
    print("OVERALL: PASS")
    print("EXITCODE=0")
    return 0


def main(argv):
    enable_utf8_stdout()
    if "--selftest" in argv:
        return selftest()
    return run()


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
