r"""正反双向自检 for measure_screen.py（切片 003）。

每条检测分支跑两次：一次健康输入必须绿，一次注入该违规必须被判。注入本身是否真的发生
要先断言——「没执行到的负向对照」和「执行了且通过的负向对照」在输出上长得一模一样，
这是本仓库失败案例库里反复命中的形态。

路径不在此处再解析一遍：夹具的相对路径由被测脚本自己按它的解析根还原，本自检只观察它的
退出码。若这里也写一次「上溯几级」，两个入口迟早漂移。

退出码（与被判脚本同一纪律，且互不重叠）：
  0 = 每个用例都符合期望
  1 = 至少一个用例不符（某条检测分支坏了或 fail-open）
  3 = 自检本身没跑完，因此本次结论不成立

用法：python .codex/scripts/dev/parity/measure_screen.py --selftest
"""
import json, os, shutil, struct, subprocess, sys, zlib

HERE = os.path.dirname(os.path.abspath(__file__))
SCRIPT = os.path.join(HERE, "measure_screen.py")
EXPECTED_CASES = 19
PNG_SIG = b"\x89PNG\r\n\x1a\n"


def _chunk(typ, payload):
    body = typ + payload
    return struct.pack(">I", len(payload)) + body + struct.pack(">I", zlib.crc32(body) & 0xFFFFFFFF)


def _encode_rgb(path, w, h, buf):
    data = (PNG_SIG + _chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
            + _chunk(b"IDAT", zlib.compress(b"".join(
                b"\x00" + bytes(buf[y * w * 3:(y + 1) * w * 3]) for y in range(h)), 6))
            + _chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(data)


def write_flat_png(path, w, h, rgb):
    """A genuine single-colour RGB PNG. Only an image can exercise the branches that read pixels;
    a missing file is caught by a different branch (input exists) and would prove nothing here."""
    _encode_rgb(path, w, h, bytearray(bytes(rgb) * w * h))


def write_probe_uniform_png(path, src_path):
    """The real sample with only its four §0 sample points forced to one colour.
    This is what the 废图自检 actually looks like in a stray capture: background under all four
    probes, content elsewhere. A fully blank image cannot stand in for it - it crashes later
    (§3 on an empty ok_rows) and therefore only proves the crash path, never that the waste
    counter itself contributes to the exit code."""
    from png_probe import Img
    im = Img(src_path)
    if im.ch != 3:
        return False
    buf = bytearray(im.buf)
    target = bytes(buf[0:3])
    for x, y in ((10, 50), (10, im.h // 2), (10, im.h - 10), (im.w - 10, 50)):
        i = (y * im.w + x) * im.ch
        buf[i:i + 3] = target
    _encode_rgb(path, im.w, im.h, buf)
    return True


def run(profile_path):
    if not os.path.exists(SCRIPT):
        print("INSTRUMENT FAULT: judged script not found next to the selftest: " + SCRIPT)
        print("EXITCODE=3")
        return 3
    try:
        with open(profile_path, encoding="utf-8") as h:
            base = json.load(h)
    except Exception as e:
        print("INSTRUMENT FAULT: cannot read the shipped profile %s (%s) - without a baseline the "
              "selftest certifies nothing" % (profile_path, e))
        print("EXITCODE=3")
        return 3
    tmp = tempfile_mkdtemp()
    try:
        rc = _cases(tmp, base, profile_path)
        print("longest scratch path in this run = %d chars (Windows rejects beyond 259; kept under "
              "240 so a longer product name cannot push it over)" % _deepest[0])
        if _deepest[0] >= 240:
            print("VERDICT: FAIL - scratch path budget exceeded")
            return 1
        return rc
    finally:
        try:
            shutil.rmtree(tmp)
            if os.path.exists(tmp):
                print("WARN selftest scratch still present: %s" % tmp)
        except Exception as e:
            print("WARN could not remove selftest scratch %s (%s) - cleanup is a warning, "
                  "not a judgement" % (tmp, e))


def tempfile_mkdtemp():
    import tempfile
    return tempfile.mkdtemp(prefix="tl-parity-selftest-")


_deepest = [0]


def _profile(tmp, name, base, mutate=None):
    """Derivative profiles are written to scratch; the shipped file is never edited by a test."""
    d = json.loads(json.dumps(base))
    if mutate:
        mutate(d)
    p = os.path.join(tmp, name + ".json")
    with open(p, "w", encoding="utf-8", newline="\n") as h:
        json.dump(d, h, ensure_ascii=False)
    return p


def _invoke(tmp, name, args):
    out = os.path.join(tmp, name + ".out.txt")
    _deepest[0] = max(_deepest[0], len(out))
    with open(out, "w", encoding="utf-8", newline="\n") as h:
        p = subprocess.run([sys.executable, "-X", "utf8", SCRIPT] + args,
                           stdout=h, stderr=subprocess.STDOUT)
    with open(out, encoding="utf-8", errors="replace") as h:
        text = h.read()
    TEXT[name] = text
    return p.returncode, text


TEXT = {}


def _abs_shot():
    """源图的绝对路径只从被测脚本自己打印的「# 被测对象」行取。解析根属于那条脚本；
    这里再算一遍「上溯几级」就成了同一条规则的两个入口，迟早漂移。"""
    for line in TEXT.get("pos-design", "").splitlines():
        if line.startswith("# 被测对象: "):
            return line[len("# 被测对象: "):].strip()
    return None


def _cases(tmp, base, shipped):
    results = []
    FAULT = "INSTRUMENT FAULT"

    def case(name, args, want_rc, want_in, want_not_in=()):
        rc, text = _invoke(tmp, name, args)
        judged = [l.strip() for l in text.splitlines()
                  if l.strip().startswith(("FAIL", FAULT))]
        for probe in want_in:
            if probe not in text:
                results.append((name, "BAD-INJECTION", rc, want_rc,
                                "witness %r absent - the fault never executed; first judgement lines: %s"
                                % (probe, judged[:1])))
                return
        for probe in want_not_in:
            if probe in text:
                results.append((name, "WITNESS-PRESENT", rc, want_rc,
                                "unexpected %r in a run that must stay clean" % probe))
                return
        if rc != want_rc:
            label = "POSITIVE-CONTROL-FAULT" if (want_rc == 0 and rc == 3) else "RC-MISMATCH"
            results.append((name, label, rc, want_rc,
                            "judgement lines: %s" % (judged[:2],)))
            return
        results.append((name, "ok", rc, want_rc, ""))

    def kill_pills(d):
        d["screens"]["chat"]["sections"]["pills"]["min_px_in_row"] = 99999

    def drop(d, path):
        cur = d
        for part in path.split(".")[:-1]:
            cur = cur[part]
        cur.pop(path.split(".")[-1])

    def setdef(k, v):
        return lambda d: d["defaults"].__setitem__(k, v)

    shot = lambda key: base["screens"]["chat"][key]

    # 健康样本：两种制式都必须绿；右缘校正必须在渲染制式下把 22.0 还原成 20.0
    case("pos-design", ["--screen", "chat", "--profile", shipped], 0, ["shot_kind=design", "EXITCODE=0"])
    case("pos-render", ["--screen", "chat", "--kind", "render", "--profile", shipped], 0,
         ["shot_kind=render → 右缘本次扣除量=4", "EXITCODE=0"])
    case("offset-reproduces-gutter", ["--screen", "chat", "--kind", "render", "--profile", shipped],
         0, ["校正后 20.0 逻辑"])
    # 允许跳过的分支必须真的被走到，否则它是一条从未有人到达的豁免
    case("pos-allowance-exercised", ["--screen", "chat", "--kind", "render",
                                     "--profile", _profile(tmp, "allow_pills", base,
                                                           lambda d: (kill_pills(d), d["screens"]["chat"].__setitem__(
                                                               "render_skip_allowed", ["== 7."])))],
         0, ["按档案允许跳过"], want_not_in=["FAIL 小节"])

    # 输入缺失 / 档案缺失 / 档案非法 / 屏不存在 / 制式非法 -> 3（结论不可信）
    case("neg-image-missing", ["--screen", "chat", "--profile", shipped,
                               "--shot", os.path.join(tmp, "nope.png")], 3,
         [FAULT, "input image not found"])
    case("neg-profile-missing", ["--screen", "chat", "--profile", os.path.join(tmp, "nope.json")],
         3, [FAULT, "profile not found"])
    badjson = os.path.join(tmp, "broken.json")
    with open(badjson, "w", encoding="utf-8", newline="\n") as h:
        h.write("{ not json at all ")
    case("neg-profile-not-json", ["--screen", "chat", "--profile", badjson], 3,
         [FAULT, "not readable JSON"])
    case("neg-unknown-screen", ["--screen", "nosuchscreen", "--profile", shipped], 3,
         [FAULT, "is not in"])
    case("neg-unknown-kind", ["--screen", "chat", "--kind", "tablet", "--profile", shipped], 3,
         [FAULT, "neither design nor render"])
    # 档案缺窗口 / 缺墨区 / 必需节清单为空 -> 3：宁可不出数，也不用另一个屏的窗口硬量
    case("neg-window-missing", ["--screen", "chat",
                                "--profile", _profile(tmp, "no_window", base,
                                                      lambda d: drop(d, "screens.chat.sections.appbar"))],
         3, [FAULT, "has no sections.appbar"])
    case("neg-ink-zones-empty", ["--screen", "chat",
                                 "--profile", _profile(tmp, "no_zones", base,
                                                       lambda d: d["screens"]["chat"].__setitem__(
                                                           "ink_zones", []))],
         3, [FAULT, "no ink_zones"])
    case("neg-require-sections-empty", ["--screen", "chat",
                                        "--profile", _profile(tmp, "no_req", base,
                                                              lambda d: d["screens"]["chat"].__setitem__(
                                                                  "require_sections", []))],
         3, [FAULT, "empty require_sections"])

    # 偏移常量被删或归零 -> 1（这是判据，不是仪器故障；静默当 0 会让渲染图右缘读数普遍偏大 4）
    case("neg-offset-constant-deleted", ["--screen", "chat",
                                         "--profile", _profile(tmp, "del_scrollbar", base,
                                                               setdef("right_scrollbar_logical_px", None))],
         1, ["FAIL offset constant missing"])
    case("neg-offset-constant-zero", ["--screen", "chat",
                                      "--profile", _profile(tmp, "zero_scale", base,
                                                            setdef("scale_physical_per_logical", 0))],
         1, ["FAIL offset constant missing"])
    # 制式与图不符 -> 1：左缘深色带实测与档案常量互为回归锁
    case("neg-design-kind-on-render-image", ["--screen", "chat", "--profile", shipped,
                                             "--shot", shot("render_shot")], 1,
         ["FAIL shot_kind=design"])
    case("neg-render-kind-on-design-image", ["--screen", "chat", "--kind", "render",
                                            "--profile", shipped, "--shot", shot("sample_shot")], 1,
         ["FAIL 左缘偏移实测"])
    # 必需节没出数 -> 1（同一个变异在允许表里则必须走上面的 pos-allowance-exercised）
    case("neg-required-section-silent", ["--screen", "chat", "--kind", "render",
                                         "--profile", _profile(tmp, "kill_pills", base, kill_pills)],
         1, ["FAIL 小节 '== 7.' 未出数"])
    # 废图自检分两条独立分支，必须各配一个夹具：
    # (a) 整幅纯色 → 后面 §3 在空 ok_rows 上崩。崩溃=没跑完，走 3，不能与「判据判 FAIL」的 1 同码。
    flat = os.path.join(tmp, "flat.png")
    write_flat_png(flat, 824, 1367, (255, 255, 255))
    case("neg-flat-image-crashes-to-3", ["--screen", "chat", "--profile", shipped, "--shot", flat], 3,
         ["自检失败", FAULT, "uncaught exception"])
    # (b) 四个取样点同色但内容还在 → 跑得完，rc=1 的唯一来源就是废图计数。
    #     没有这条，把 _waste 整行删掉上面那条照样红（红在崩溃上），废图计数就成了假见证。
    src = _abs_shot()
    waste = os.path.join(tmp, "probe-uniform.png")
    built = bool(src) and os.path.exists(src) and write_probe_uniform_png(waste, src)
    if not built:
        print("VERDICT: INCONCLUSIVE - the probe-uniform fixture could not be built (source %r). "
              "Leaving the waste-counter branch untested and reporting the rest as green is exactly "
              "the 'skipped case counted as PASS' shape this repo forbids." % src)
        print("EXITCODE=3")
        return 3
    case("neg-waste-counter-counted", ["--screen", "chat", "--profile", shipped, "--shot", waste], 1,
         ["自检失败", "废图自检命中 = 1", "COUNT 判据行 = 0", "本段新增判定 = 0"])

    bad = [r for r in results if r[1] != "ok"]
    for name, status, rc, want, note in results:
        print("CASE %-30s rc=%-3s want=%-3s %-22s %s" % (name, rc, want, status, note[:100]))
    print("%d case(s) executed, expectation table says %d, %d failure(s)"
          % (len(results), EXPECTED_CASES, len(bad)))
    if len(results) != EXPECTED_CASES:
        print("VERDICT: INCONCLUSIVE - the case count moved without EXPECTED_CASES being updated, "
              "so this run cannot certify itself")
        print("EXITCODE=3")
        return 3
    if bad:
        print("VERDICT: FAIL")
        print("EXITCODE=1")
        return 1
    print("VERDICT: PASS (%d cases, positive and negative for every branch)" % len(results))
    print("EXITCODE=0")
    return 0
