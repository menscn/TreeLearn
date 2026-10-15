"""由 tokens.json 生成前端 CSS 变量文件：设计数值只能有一份来源。

用法（从任意目录）：
    python .trae/scripts/dev/gen_design_tokens_css.py            # 写 frontend/src/styles/tokens.css
    python .trae/scripts/dev/gen_design_tokens_css.py --stdout   # 只打印，不落盘
    python .trae/scripts/dev/gen_design_tokens_css.py --check    # 已落盘内容与再生成是否逐字节相同

退出码：0 成功 / 一致；1 漂移（已落盘内容与再生成不同）；2 用法错误；3 输入不可读或解析退化
（「本次没跑成」与「跑完发现不符」是两种结论，不得共用退出码）。

权威顺序固定为 原型 :root → tokens.json → 本文件。反向改这个 CSS 一律视为漂移：
守门 `.trae/scripts/dev/check_design_tokens.py` 会在同一次运行里重新生成并逐字节比对，
所以「手改一个色」不会留下任何可通过的状态，只能回到 tokens.json 改一次、生成一次。
"""

import contextlib
import hashlib
import io
import json
import os
import re
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
REPO_ROOT = os.path.dirname(os.path.dirname(os.path.dirname(HERE)))

TOKENS = "Docs/04-ux-design/design-system/tokens.json"
CSS = "frontend/src/styles/tokens.css"
MIN_TOKENS = 40
MIN_SELFTEST_CASES = 13  # 裸字面量，与被检清单无关：用例少一条即判 FAIL（详见 selftest 末尾）
DATE_RE = re.compile(r"\d{4}-\d{2}-\d{2}")


def fault(message):
    print("INSTRUMENT FAULT: " + message)
    print("EXITCODE=3")
    return 3


def load_tokens(root=None):
    """返回有序 {token名: 值}；解析不出足够条目即判故障，不返回空字典让下游误判「一致」。"""
    base = root or REPO_ROOT
    path = os.path.join(base, TOKENS.replace("/", os.sep))
    if not os.path.exists(path):
        raise ValueError("tokens.json not found: " + path)
    with io.open(path, encoding="utf-8") as handle:
        data = json.load(handle)
    out = {}
    for group, entries in data.items():
        if group.startswith("$") or not isinstance(entries, dict):
            continue
        for name, entry in entries.items():
            if not isinstance(entry, dict) or "value" not in entry:
                continue
            if name in out:
                raise ValueError("duplicate token name: " + name)
            out[name] = entry["value"]
    if len(out) < MIN_TOKENS:
        raise ValueError("parsed only %d tokens (< %d), parsing degraded" % (len(out), MIN_TOKENS))
    return out


def render_css(tokens):
    """确定性输出：顺序跟随 tokens 的来源顺序，不含时间戳——「重新生成必须逐字节相同」这条
    漂移校验的存在前提就是同输入同字节，任何 per-run 熵都会让它恒红。"""
    payload = "".join("%s\1%s\n" % (name, value) for name, value in tokens.items())
    digest = hashlib.sha256(payload.encode("utf-8")).hexdigest()
    lines = [
        "/* 生成文件，不要手改。",
        "   来源链：原型 :root → tokens.json → 本文件（反向改本文件一律视为漂移）。",
        "   生成器：.trae/scripts/dev/gen_design_tokens_css.py",
        "   漂移判定：python -X utf8 .trae/scripts/dev/check_design_tokens.py",
        "   token 数：%d" % len(tokens),
        "   内容 sha256：%s */" % digest,
        ":root {",
    ]
    for name, value in tokens.items():
        lines.append("  --%s: %s;" % (name, value))
    lines.append("}")
    text = "\n".join(lines) + "\n"
    if DATE_RE.search(text):
        raise ValueError("generated CSS contains a date-like string; regeneration would never be byte-stable")
    return text


def css_path(base):
    return os.path.join(base, CSS.replace("/", os.sep))


def main(argv, base=None):
    base = base or REPO_ROOT
    unknown = [a for a in argv if a.startswith("-") and a not in ("--stdout", "--check", "--selftest")]
    if unknown:
        print("USAGE ERROR: unrecognised argument(s): %s" % unknown)
        print("EXITCODE=2")
        return 2
    if "--selftest" in argv:
        return selftest()
    try:
        tokens = load_tokens(base)
    except (ValueError, OSError, json.JSONDecodeError) as exc:
        return fault("tokens.json could not be read as the token source: %s" % exc)
    text = render_css(tokens)

    if "--stdout" in argv:
        sys.stdout.write(text)
        print("EXITCODE=0")
        return 0

    out = css_path(base)
    # 判定行报绝对路径：自检跑在临时目录，只写仓库相对路径读者无法确认判的是哪一份文件。
    if "--check" in argv:
        if not os.path.exists(out):
            print("FAIL generated CSS is missing: %s (%s) - a missing artefact is drift, not a skip" % (CSS, out))
            print("EXITCODE=1")
            return 1
        with io.open(out, encoding="utf-8", newline="") as handle:
            shipped = handle.read()
        if shipped != text:
            print("FAIL generated CSS drifted: %s (%s) differs from a fresh regeneration byte-for-byte" % (CSS, out))
            print("   shipped bytes=%d rendered bytes=%d" % (len(shipped.encode("utf-8")), len(text.encode("utf-8"))))
            print("   fix: re-run the generator, never edit the CSS by hand")
            print("EXITCODE=1")
            return 1
        print("OK generated CSS matches a fresh regeneration byte-for-byte — %s (%s) bytes=%d tokens=%d" % (
            CSS, out, len(text.encode("utf-8")), len(tokens)))
        print("EXITCODE=0")
        return 0

    os.makedirs(os.path.dirname(out), exist_ok=True)
    with io.open(out, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)
    print("OK wrote %s bytes=%d tokens=%d" % (out, len(text.encode("utf-8")), len(tokens)))
    print("EXITCODE=0")
    return 0


def declaration_lines(text):
    """只取声明行。文件头的内容 sha256 随任何值变化，把它算进「差异行数」就归因不到具体 token。"""
    return [x for x in text.splitlines() if x.lstrip().startswith("--")]


def selftest():
    """正反双向：健康输入必须逐字节稳定，且每种「手改」形态都必须被 --check 的形状判红。"""
    tokens = {}
    for i in range(MIN_TOKENS + 1):
        tokens["t-%d" % i] = "#%06X" % (i * 7919 % 0xFFFFFF)
    executed = [0]
    bad = [0]

    def case(cond, ok_label, fail_label, detail=""):
        executed[0] += 1
        if cond:
            print("OK %s%s" % (ok_label, " — " + detail if detail else ""))
        else:
            bad[0] += 1
            print("FAIL %s%s" % (fail_label, " — " + detail if detail else ""))

    a = render_css(tokens)
    b = render_css(dict(tokens))
    case(a == b,
         "same input -> same bytes, so the drift check has something to compare against",
         "regeneration of the same input is not byte-identical - the drift check cannot exist",
         "bytes=%d" % len(a))
    case(render_css(tokens) != render_css(dict(reversed(list(tokens.items())))),
         "output order follows the source order",
         "output order ignored the source order, so the file is not a faithful mirror")
    case(DATE_RE.search(a) is None,
         "generated text carries no date-like string",
         "generated text contains a date-like string - regeneration would never be byte-stable")

    tampered = dict(tokens)
    name = sorted(tampered)[0]
    # 新值必须与旧值不同，否则「改动一个值」退化成 no-op，这条断言永远不会红（实测踩过：
    # 夹具里 t-0 本身就是 #000000）。
    new = "#7A7A7A" if tampered[name] != "#7A7A7A" else "#8B8B8B"
    tampered[name] = new
    t = render_css(tampered)
    a_decl, t_decl = declaration_lines(a), declaration_lines(t)
    lost = [x for x in a_decl if x not in t_decl]
    gained = [x for x in t_decl if x not in a_decl]
    # 改值在声明行上的形状是「少一行、多一行」，两者是同一行的前后态；要求 gained 为空
    # 等于要求一个不存在的外观（实测把这条正控判成缺陷）。
    case(t != a and len(lost) == 1 and len(gained) == 1 and lost[0].lstrip().startswith("--" + name)
         and hashlib.sha256(a.encode("utf-8")).hexdigest()
         != hashlib.sha256(t.encode("utf-8")).hexdigest(),
         "a single changed value moves exactly its own declaration and the header fingerprint",
         "a changed value did not move exactly one declaration plus the fingerprint",
         "%s -> %s lost=%d gained=%d" % (name, new, len(lost), len(gained)))

    dropped = dict(tokens)
    dropped.pop(name)
    d_decl = declaration_lines(render_css(dropped))
    lost = [x for x in a_decl if x not in d_decl]
    gained = [x for x in d_decl if x not in a_decl]
    case(len(lost) == 1 and not gained and lost[0].lstrip().startswith("--" + name),
         "dropping a token removes exactly its own declaration",
         "dropping a token did not remove exactly its own declaration",
         "declarations %d -> %d lost=%d gained=%d" % (len(a_decl), len(d_decl), len(lost), len(gained)))
    case(len(a_decl) == len(tokens),
         "every token becomes exactly one declaration",
         "declaration count does not equal token count - a token was silently dropped",
         "measured=%d tokens=%d" % (len(a_decl), len(tokens)))

    try:
        load_tokens(root=os.path.join(REPO_ROOT, "artifacts", "tmp", "_no_such_root"))
        raised = None
    except ValueError as exc:
        raised = str(exc)
    case(raised is not None,
         "a missing tokens.json raises instead of returning an empty set",
         "a missing tokens.json was accepted as an empty token set",
         (raised or "")[:60])

    # 走生产入口而不是只比 render_css：「探针形状 ≠ 生产形状」是本仓库反复命中的同源缺陷，
    # 所以 --check 的每种结局（一致 / 漂移 / 缺失 / 用法错 / 输入故障）都由 main(argv, base=) 真实跑出。
    built = []
    scratch = tempfile.mkdtemp(prefix="tl-gentok-")
    built.append(scratch)
    seq = [0]

    def fresh(values):
        root = os.path.join(scratch, "case-%d" % seq[0])
        seq[0] += 1
        src = os.path.join(root, *TOKENS.split("/"))
        os.makedirs(os.path.dirname(src))
        with io.open(src, "w", encoding="utf-8", newline="\n") as handle:
            json.dump({"g": dict((n, {"value": v}) for n, v in values.items())}, handle)
        out = css_path(root)
        os.makedirs(os.path.dirname(out))
        with io.open(out, "w", encoding="utf-8", newline="\n") as handle:
            handle.write(render_css(values))
        built.append(out)
        return root, out

    def run(base, *args):
        buf = io.StringIO()
        with contextlib.redirect_stdout(buf):
            code = main(list(args), base=base)
        return code, buf.getvalue()

    healthy = dict(tokens)
    one_char_is_real = [False]
    try:
        root, out = fresh(healthy)
        rc, text = run(root, "--check")
        case(rc == 0 and "byte-for-byte" in text,
             "production --check accepts a healthy generated CSS",
             "production --check rejected a healthy generated CSS", "rc=%d" % rc)

        # hex 末位翻转：不动结构，正是「手改一个色」的最小形态
        with io.open(out, encoding="utf-8", newline="") as handle:
            body = handle.read()
        idx = body.index("#")
        flipped = "0" if body[idx + 6] != "0" else "1"
        one_char = body[:idx + 6] + flipped + body[idx + 7:]
        one_char_is_real[0] = one_char != body
        if one_char_is_real[0]:
            with io.open(out, "w", encoding="utf-8", newline="\n") as handle:
                handle.write(one_char)
            rc, text = run(root, "--check")
        else:
            rc, text = 0, ""
        case(one_char_is_real[0] and rc == 1 and "drifted" in text,
             "production --check flips on a single hex digit",
             "a single hex-digit edit was not judged drift (or the mutation was a no-op)",
             "mutation_real=%s rc=%d" % (one_char_is_real[0], rc))

        root2, out2 = fresh(healthy)
        os.remove(out2)
        rc, text = run(root2, "--check")
        case(rc == 1 and "missing" in text,
             "production --check judges a deleted generated CSS as drift, never a skip",
             "a deleted generated CSS was not judged drift", "rc=%d" % rc)

        root3, _out3 = fresh(healthy)
        rc, text = run(root3, "--bogus")
        case(rc == 2 and "USAGE ERROR" in text,
             "production entry rejects an unknown flag with a usage error",
             "an unknown flag was not a usage error", "rc=%d" % rc)

        root4, out4 = fresh(healthy)
        os.remove(out4)
        os.remove(os.path.join(root4, *TOKENS.split("/")))
        rc, text = run(root4, "--check")
        case(rc == 3 and "INSTRUMENT FAULT" in text,
             "production --check reports an instrument fault when tokens.json is gone",
             "a missing tokens.json was reported as a verdict instead of a fault", "rc=%d" % rc)
    finally:
        # 清理失败只告警、不改判定：把「没删干净」和「用例不符」共用退出码会让病因被读错。
        try:
            shutil.rmtree(scratch)
        except OSError as exc:
            print("WARN scratch cleanup failed (%s); judgement unchanged" % exc)

    longest = max(len(p) for p in built)
    # Windows 260 字符上限：路径超长会让清理抛异常，而清理失败曾把整套判定打死过。
    case(longest <= 200, "scratch path stays clear of the Windows 260 limit",
         "scratch path is too close to the Windows 260 limit",
         "longest=%d margin=%d" % (longest, 260 - longest))

    # 用例数必须恰好等于这个数：少一条说明有判据被删或从未接入，多一条说明该下限过期。
    # 用 == 而不是 >=，因为这里不给「合理增长」留余量——增长必须是一次显式编辑。
    # 本条是「对用例数本身的元断言」，不计入用例数（与 check_design_tokens.py 同口径）。
    if executed[0] == MIN_SELFTEST_CASES:
        print("OK selftest executed exactly the expected number of cases — measured=%d" % executed[0])
    else:
        bad[0] += 1
        print("FAIL selftest case count moved without the floor being updated — "
              "measured=%d expected=%d" % (executed[0], MIN_SELFTEST_CASES))

    print("SELFTEST cases=%d failures=%d" % (executed[0], bad[0]))
    if bad[0]:
        print("EXITCODE=1")
        return 1
    print("EXITCODE=0")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
