#!/usr/bin/env python3
"""Positive/negative selftest for gen_screen_inventory.py.

The generator's own judgements are only trustworthy if each branch has been seen to fire on a
synthetic tree, and if a degenerate tree cannot print a coverage claim. Fixtures live under
artifacts/tmp/_inv005_run/ and are removed afterwards; nothing here writes into the Android tree.

Exit tiers: 0 all cases matched, 1 a case's judgement did not match, 2 bad usage,
3 instrument fault (fixture could not be built, or fewer cases ran than the literal below).
"""
import io
import os
import shutil
import sys
import tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
try:
    import gen_screen_inventory as G
except ImportError as exc:
    # An uncaught traceback here exits with rc=1, the same tier a *case mismatch* uses, so
    # "the harness could not start" reads as "a judgement did not hold".  Measured before this
    # guard existed: with the generator moved aside the suite printed only a ModuleNotFoundError
    # traceback and rc=1 - no EXITCODE line, no fault token.
    print("INSTRUMENT FAULT: gen_screen_inventory.py is not importable next to this script (%s);"
          " no case can run" % exc)
    print("EXITCODE=3")
    sys.exit(3)

EXPECTED_CASES = 23                      # bare literal; must not be derived from CASES
RUN_ROOT = os.path.join(G.REPO, "artifacts", "tmp", "_inv005_run")
REQUIRED_ATTRS = ("analyse", "main", "render", "report_lines", "spec_link", "layout_census",
                  "arrival_edges", "scan_java_file", "method_spans", "read", "Fault")

CASES = []


def case(name, want_rc):
    def deco(fn):
        CASES.append((name, want_rc, fn))
        return fn
    return deco


def w(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = text.encode("utf-8") if isinstance(text, str) else text
    with open(path, "wb") as fh:
        fh.write(data)


def layout(root_tag, ident=None, hidden=False, extra=""):
    attrs = ['xmlns:android="http://schemas.android.com/apk/res/android"']
    if ident:
        attrs.append('android:id="@+id/%s"' % ident)
    if hidden:
        attrs.append('android:visibility="gone"')
    return "<%s %s>%s</%s>\n" % (root_tag, " ".join(attrs), extra, root_tag)


def screen_java(cls, layout_name=None, extra=""):
    body = ""
    if layout_name:
        body = ('        LayoutInflater.from(context).inflate(R.layout.%s, this, true);\n'
                % layout_name)
    return ("public final class %s extends FrameLayout {\n"
            "    public %s(Context context) {\n        super(context);\n%s    }\n%s}\n"
            % (cls, cls, body, extra))


def popup_java(cls, layout_name):
    return ("public final class %s extends PopupWindow {\n"
            "    public %s(Context context) {\n"
            "        super(context);\n"
            "        View content = LayoutInflater.from(context)"
            ".inflate(R.layout.%s, null, false);\n"
            "        setContentView(content);\n"
            "        PopupWindow self = new PopupWindow(context);\n"
            "        self.setAnimationStyle(0);\n    }\n}\n" % (cls, cls, layout_name))


def dialog_java(cls, layout_name):
    return ("public final class %s {\n"
            "    public void show(Context context) {\n"
            "        View content = LayoutInflater.from(context)"
            ".inflate(R.layout.%s, null, false);\n"
            "        Dialog dialog = new Dialog(context);\n"
            "        dialog.setContentView(content);\n    }\n}\n" % (cls, layout_name))


def menu_java(cls):
    return ("public final class %s {\n"
            "    public void open(Context context, View anchor) {\n"
            "        PopupMenu menu = new PopupMenu(context, anchor);\n"
            "        menu.getMenu().add(Menu.NONE, 1, 1, R.string.app_name);\n"
            "        menu.show();\n    }\n}\n" % cls)


def alert_java(cls):
    return ("public final class %s {\n"
            "    public void ask(Context context) {\n"
            "        new AlertDialog.Builder(context)\n"
            "                .setTitle(R.string.app_name)\n"
            "                .setNegativeButton(R.string.cancel, null)\n"
            "                .show();\n    }\n}\n" % cls)


def four_forms_java(cls, layout_name):
    return ("public final class %s {\n"
            "    public void all(Context context, View anchor) {\n"
            "        View content = LayoutInflater.from(context)"
            ".inflate(R.layout.%s, null, false);\n"
            "        PopupWindow w = new PopupWindow(content, 1, 2, true);\n"
            "        Dialog d = new Dialog(context);\n"
            "        PopupMenu m = new PopupMenu(context, anchor);\n"
            "        new AlertDialog.Builder(context).setTitle(R.string.app_name).show();\n"
            "    }\n}\n" % (cls, layout_name))


def doc_only_java(cls, orphan_layout):
    """Mentions a constructor and a layout from javadoc only, then builds one real PopupMenu."""
    return ("public final class %s {\n"
            "    /**\n"
            "     * The retired variant was {@code new PopupWindow(context)} and it inflated\n"
            "     * R.layout.%s; both are named here purely as documentation.\n"
            "     */\n"
            "    public void open(Context context, View anchor) {\n"
            "        PopupMenu live = new PopupMenu(context, anchor);\n"
            "        live.show();\n    }\n}\n" % (cls, orphan_layout))


def main_activity(names, addview=None, unbalanced=False, field_at_class_level=True):
    fields = "\n".join("    private %s %s;" % (n, n[0].lower() + n[1:]) for n in names)
    if addview:
        fields += "\n    private %s %s;" % (addview[1], addview[0])
    ctor = "\n".join("        %s = new %s(this);" % (n[0].lower() + n[1:], n) for n in names)
    swaps = "\n".join("    public void show%s() { swap(%s); }"
                      % (n[0].lower() + n[1:], n[0].lower() + n[1:]) for n in names)
    tail = ""
    if addview:
        tail = ("\n        %s = new %s(this);\n"
                "        findViewById(R.id.host_panel).addView(%s);\n"
                % (addview[0], addview[1], addview[0]))
    text = ("public final class MainActivity extends Activity {\n%s\n"
            "    public void onCreate() {\n        super.onCreate();\n"
            "        setContentView(R.layout.activity_main);\n%s%s    }\n%s\n}\n"
            % (fields if field_at_class_level else "", ctor, tail, swaps))
    if unbalanced:
        # Truncate right after the last opening brace, i.e. a source file that stops mid-method.
        # Deleting a closing brace instead would not exercise anything: method_spans only walks
        # signatures, so a stray missing "}" is re-synced by the next real "}" and the scanner
        # returns rc=0 on broken input. That blind spot is registered in the task file; this
        # mutation is the shape the instrument does close - depth != 0 at end of file.
        idx = text.rfind("{")
        text = text[:idx + 1] + "\n"
    return text


def build(root, screens=9, popups=3, variants=1, self_drawn=None, hidden_include=None,
          dialog=True, unbalanced=False, empty_screen_dir=False, no_main=False,
          bad_layout_bytes=None, dead_include=False, spec=True,
          rename_ref=None, include_unproven=False, addview=False):
    """Write a synthetic Android-shaped tree; returns the (java, res, spec) paths.

    addview stays False unless the case is about the addView channel: DrawerScreen is itself a
    screen in the screen package, so letting it into every fixture would move the screen and
    self-drawn counts that the other cases assert on.
    """
    java = os.path.join(root, "java")
    res = os.path.join(root, "res")
    names = ["S%02dScreen" % i for i in range(screens)]
    drawn = set(self_drawn or [])
    add = ("drawer", "DrawerScreen") if addview else None
    if not empty_screen_dir:
        for i, n in enumerate(names):
            lname = None
            if n not in drawn:
                lname = rename_ref["target"] if (rename_ref and i == rename_ref["index"]) else \
                    "%s_layout" % n.lower()
            body = ""
            if hidden_include and hidden_include[1] == n and not include_unproven:
                body = ("    public void bind() {\n        findViewById(R.id.%s);\n    }\n"
                        % (hidden_include[0] + "_root"))
            w(os.path.join(java, "screen", n + ".java"), screen_java(n, lname, body))
        for i in range(popups):
            w(os.path.join(java, "screen", "P%02dPopup.java" % i),
              popup_java("P%02dPopup" % i, "popup_p%02d" % i))
        if dialog:
            w(os.path.join(java, "screen", "Picker.java"), dialog_java("Picker", "popup_picker"))
        if add:
            w(os.path.join(java, "screen", "DrawerScreen.java"),
              screen_java("DrawerScreen", None,
                          "    public void build() { addView(new View(getContext())); }\n"))
    else:
        os.makedirs(os.path.join(java, "screen"), exist_ok=True)
    if not no_main:
        w(os.path.join(java, "MainActivity.java"),
          main_activity(names[:screens], addview=add, unbalanced=unbalanced))
    lay = os.path.join(res, "layout")
    w(os.path.join(lay, "activity_main.xml"), layout("LinearLayout", "main_root"))
    for n in names:
        lname = "%s_layout" % n.lower()
        if n in drawn:
            continue
        extra = ""
        if hidden_include and hidden_include[1] == n:
            extra = '<include layout="@layout/%s" />' % hidden_include[0]
        w(os.path.join(lay, lname + ".xml"), layout("FrameLayout", lname + "_root", False, extra))
    if hidden_include:
        w(os.path.join(lay, hidden_include[0] + ".xml"),
          layout("LinearLayout", hidden_include[0] + "_root", True))
    for i in range(popups):
        w(os.path.join(lay, "popup_p%02d.xml" % i), layout("ListView", "popup_p%02d" % i))
    if dialog:
        w(os.path.join(lay, "popup_picker.xml"), layout("RecyclerView", "picker_root"))
    if dead_include:
        w(os.path.join(lay, "broken_host.xml"),
          layout("FrameLayout", "broken_host", False, '<include layout="@layout/nope" />'))
    if bad_layout_bytes is not None:
        w(os.path.join(lay, "activity_main.xml"), bad_layout_bytes)
    for i in range(min(variants, len(names))):
        n = names[i]
        if n not in drawn:
            w(os.path.join(res, "layout-sw600dp", "%s_layout.xml" % n.lower()),
              layout("FrameLayout", "%s_layout_root" % n.lower()))
    spec_path = os.path.join(root, "spec.md")
    if spec is True:
        # Derived from the producer, not hand-copied: the fixture's job is to look like a spec that
        # points at the inventory, and a literal file name here would go stale on a move.
        w(spec_path, "范围：条目数以生成的清单 %s 的 counts 为准，本文不写数字。\n"
          % G.INVENTORY_NAME)
    elif spec:
        w(spec_path, spec)
    return java, res, spec_path


def run(argv):
    """Invoke the generator's main(), returning (rc, stdout)."""
    buf = io.StringIO()
    old = sys.stdout
    sys.stdout = buf
    try:
        rc = G.main(argv)
    finally:
        sys.stdout = old
    return rc, buf.getvalue()


def inv(argv):
    rc, out = run(argv)
    return rc, out, [l for l in out.splitlines() if l.strip()]


def out_dir(root):
    return os.path.join(root, "out", "screen-inventory.json")


@case("pos-basic", 0)
def _c_basic(root):
    java, res, spec = build(root)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("MEASURED screens=9 present", "MEASURED screens=9 " in out),
                ("9 SCREEN lines", out.count("SCREEN S") == 9),
                ("floors hold", "ok=True" in out),
                ("spec cites the inventory, that is the whole judged claim",
                 "SPEC_POINTER cites=True " in out),
                ("restated counts are printed, not judged away",
                 "SPEC_RESTATED count-shaped=0 lines=none " in out)]


@case("pos-self-drawn", 0)
def _c_drawn(root):
    java, res, spec = build(root, self_drawn=["S03Screen"])
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line = [l for l in out.splitlines() if l.startswith("SCREEN S03Screen")]
    return rc, [("self_drawn=True on that screen", bool(line) and "self_drawn=True" in line[0]),
                ("no root layout for it", bool(line) and "root=-" in line[0]),
                ("counted in self_drawn", "self_drawn=1 " in out),
                ("no layout is invented for it", "s03screen_layout" not in out),
                ("it is still reached, so not an unreached screen",
                 "UNREACHED_SCREEN S03Screen" not in out)]


@case("pos-sw600dp-variant", 0)
def _c_variant(root):
    java, res, spec = build(root, variants=2)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    yes = [l for l in out.splitlines() if l.startswith("SCREEN ") and "sw600dp=True" in l]
    return rc, [("exactly two screens carry a variant", len(yes) == 2),
                ("MEASURED reports sw600dp=2", "sw600dp=2 " in out)]


@case("pos-addview-host", 0)
def _c_addview(root):
    java, res, spec = build(root, addview=True)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    host = [l for l in out.splitlines() if l.startswith("SURFACE code_drawn_host")]
    drawn = [l for l in out.splitlines() if l.startswith("SCREEN DrawerScreen ")]
    return rc, [("one code_drawn_host surface", len(host) == 1),
                ("host id recorded", bool(host) and "host_id=host_panel" in host[0]),
                ("DrawerScreen is a self-drawn screen",
                 bool(drawn) and "self_drawn=True" in drawn[0]),
                ("reached through addView", bool(drawn) and "addView" in drawn[0]),
                ("screen count includes the drawer host", "MEASURED screens=10 " in out)]


@case("pos-hidden-include-proven", 0)
def _c_hidden(root):
    java, res, spec = build(root, hidden_include=("s01screen_layout_detail", "S01Screen"))
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line = [l for l in out.splitlines() if "kind" in l or l.startswith("SURFACE included_hidden")]
    return rc, [("included_hidden surface appears", bool(line)),
                ("host layout named", bool(line) and "host=s01screen_layout" in line[0]),
                ("consumer proven", bool(line) and "consumer_proven=True" in line[0]),
                ("not counted as a visible include", "visible_includes=0" in out)]


@case("pos-hidden-include-unproven", 0)
def _c_hidden_unproven(root):
    java, res, spec = build(root, hidden_include=("s02screen_layout_detail", "S02Screen"),
                            include_unproven=True)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line = [l for l in out.splitlines() if l.startswith("SURFACE included_hidden")]
    return rc, [("surface still listed", bool(line)),
                ("consumer proven stays False", bool(line) and "consumer_proven=False" in line[0]),
                ("no silent promotion", "consumer_proven=True" not in out)]


@case("pos-dialog-site", 0)
def _c_dialog(root):
    java, res, spec = build(root)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line = [l for l in out.splitlines() if l.startswith("SURFACE dialog")]
    return rc, [("one dialog surface", len(line) == 1),
                ("layout resolved from the line above it",
                 bool(line) and "layout=popup_picker" in line[0]),
                ("producer is the component class", bool(line) and "producer=Picker" in line[0]),
                ("SURFACES_BY_KIND counts it", '"dialog": 1' in out)]


@case("pos-popup-menu-site", 0)
def _c_menu_site(root):
    java, res, spec = build(root)
    w(os.path.join(java, "screen", "RowMenu.java"), menu_java("RowMenu"))
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line = [l for l in out.splitlines() if l.startswith("SURFACE popup_menu")]
    return rc, [("one popup_menu surface", len(line) == 1),
                ("producer is the component class", bool(line) and "producer=RowMenu" in line[0]),
                ("no layout invented for a menu", bool(line) and "layout=-" in line[0]),
                ("so no sw600dp variant either", bool(line) and "sw600dp=False" in line[0]),
                ("SURFACES_BY_KIND counts it", '"popup_menu": 1' in out),
                ("the popup_window total is untouched by the new pattern",
                 '"popup_window": 3' in out)]


@case("pos-alert-dialog-site", 0)
def _c_alert_site(root):
    java, res, spec = build(root)
    w(os.path.join(java, "screen", "DeleteAsk.java"), alert_java("DeleteAsk"))
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line = [l for l in out.splitlines() if l.startswith("SURFACE alert_dialog")]
    return rc, [("one alert_dialog surface", len(line) == 1),
                ("producer is the component class", bool(line) and "producer=DeleteAsk" in line[0]),
                ("a message dialog carries no layout", bool(line) and "layout=-" in line[0]),
                ("the chained Builder is not also read as new Dialog(", '"dialog": 1' in out),
                ("SURFACES_BY_KIND counts it", '"alert_dialog": 1' in out)]


@case("pos-surface-kinds-disjoint", 0)
def _c_disjoint(root):
    java, res, spec = build(root, popups=0, dialog=False)
    w(os.path.join(java, "screen", "FourForms.java"), four_forms_java("FourForms", "four_chip"))
    w(os.path.join(res, "layout", "four_chip.xml"), layout("ListView", "four_chip"))
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    kinds = sorted(l.split()[1] for l in out.splitlines() if l.startswith("SURFACE "))
    quiet = [l for l in out.splitlines()
             if l.startswith("SURFACE popup_menu") or l.startswith("SURFACE alert_dialog")]
    return rc, [("exactly four surfaces, no duplicates", len(kinds) == 4),
                ("one of each kind - none cross-matched",
                 kinds == ["alert_dialog", "dialog", "popup_menu", "popup_window"]),
                ("the shared layout went to the two view-based kinds",
                 out.count("layout=four_chip") == 2),
                ("nothing invented for the menu or the message dialog",
                 len(quiet) == 2 and all("layout=-" in l for l in quiet))]


@case("pos-comment-line-excluded", 0)
def _c_comment(root):
    java, res, spec = build(root)
    w(os.path.join(java, "screen", "DocOnly.java"), doc_only_java("DocOnly", "retired_chip"))
    w(os.path.join(res, "layout", "retired_chip.xml"), layout("ListView", "retired_chip"))
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("the javadoc constructor minted no surface", '"popup_window": 3' in out),
                ("the live menu in the same file is still counted", '"popup_menu": 1' in out),
                ("a layout named only inside a comment is not a reference",
                 "UNREFERENCED_LAYOUT retired_chip" in out),
                ("that orphan is the only one, so the signal is not drowned",
                 out.count("UNREFERENCED_LAYOUT") == 1)]


@case("pos-determinism", 0)
def _c_determinism(root):
    java, res, spec = build(root, hidden_include=("s04screen_layout_detail", "S04Screen"))
    o1, o2 = out_dir(root) + ".a", out_dir(root) + ".b"
    rc1, out1 = run(["--java", java, "--res", res, "--spec", spec, "--out", o1])
    rc2, out2 = run(["--java", java, "--res", res, "--spec", spec, "--out", o2])
    a, b = open(o1, "rb").read(), open(o2, "rb").read()
    sha = [l for l in out1.splitlines() if "sha256=" in l]
    strict = None
    try:
        a.decode("utf-8")
        strict = True
    except UnicodeDecodeError:
        strict = False
    dig = sha[0].split("sha256=")[1].split()[0] if sha else ""
    import hashlib
    # the two runs are given different --out paths on purpose (each must write its own file),
    # so only the path token may differ; anything else in stdout would be volatile state.
    # the instrument prints that token with forward slashes, so normalise before replacing.
    n1 = out1.replace(o1.replace(os.sep, "/"), "<OUT>")
    n2 = out2.replace(o2.replace(os.sep, "/"), "<OUT>")
    return rc1, [("both runs rc 0", rc1 == 0 and rc2 == 0),
                 ("byte-identical output", a == b),
                 ("no drift between runs besides the --out path token", n1 == n2),
                 ("written file is strict UTF-8", strict),
                 ("printed sha matches recomputed sha",
                  dig == hashlib.sha256(a).hexdigest()),
                 ("json re-reads to the same object",
                  __import__("json").loads(a) == __import__("json").loads(b)),
                 ("inventory keeps non-ascii readable", "ensure_ascii" not in a.decode("utf-8"))]


@case("pos-real-tree", 0)
def _c_real(root):
    rc, out, _ = inv(["--no-write"])
    need = ["FileManagerScreen", "ModelSettingsScreen", "SearchServiceSettingsScreen",
            "VoiceSettingsScreen"]
    checks = []
    for cls in need:
        line = [l for l in out.splitlines() if l.startswith("SCREEN " + cls + " ")]
        checks.append(("%s listed self-drawn" % cls, bool(line) and "self_drawn=True" in line[0]))
    home = [l for l in out.splitlines() if l.startswith("SCREEN HomeDrawerScreen ")]
    checks.append(("HomeDrawerScreen listed self-drawn",
                   bool(home) and "self_drawn=True" in home[0]))
    checks.append(("floors hold on the real tree", "ok=True" in out))
    checks.append(("no screen is unreachable", "UNREACHED_SCREEN" not in out))
    checks.append(("counters printed, not hand-written", "MEASURED screens=" in out))
    menu = [l for l in out.splitlines() if l.startswith("SURFACE popup_menu")]
    alert = [l for l in out.splitlines() if l.startswith("SURFACE alert_dialog")]
    checks += [("the real tree does carry PopupMenu surfaces", len(menu) > 0),
               ("the real tree does carry AlertDialog surfaces", len(alert) > 0),
               ("the spec's version menu is among them (ChatScreen's PopupMenu)",
                any("producer=ChatScreen" in l for l in menu)),
               ("no menu or message dialog was given an invented layout",
                all("layout=-" in l for l in menu + alert)),
               # ModelThinkingPicker names R.layout.view_model_chip in javadoc AND inflates it on a
               # live line; excluding comment lines must keep the live one, so this layout must not
               # be reported as an orphan. Without the live reference the assertion would only prove
               # the exclusion is over-broad.
               ("a layout named in javadoc but inflated on a live line stays referenced",
                "UNREFERENCED_LAYOUT view_model_chip" not in out)]
    return rc, checks


@case("neg-screens-below-floor", 1)
def _c_low_screens(root):
    java, res, spec = build(root, screens=2)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("rc is 1 not 0", rc == 1),
                ("floor line names the shortfall", "FLOOR screens>=%d got=2" % G.MIN_SCREENS in out),
                ("floor says ok=False", "ok=False" in out)]


@case("neg-surfaces-below-floor", 1)
def _c_low_surfaces(root):
    java, res, spec = build(root, popups=0, dialog=False)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("rc is 1", rc == 1),
                ("surfaces shortfall printed", "surfaces>=%d got=0" % G.MIN_SURFACES in out),
                ("screens side still reported", "MEASURED screens=9" in out)]


@case("neg-empty-screen-package", 1)
def _c_empty_package(root):
    java, res, spec = build(root, empty_screen_dir=True)
    rc, out, lines = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    banned = [l for l in lines if l.startswith("SCREEN ")]
    return rc, [("rc is 1, not a silent 0", rc == 1),
                ("zero screens measured", "MEASURED screens=0 " in out),
                ("no SCREEN line printed", not banned),
                ("floor still judged", "ok=False" in out)]


@case("neg-missing-screen-package", 3)
def _c_missing_package(root):
    java, res, spec = build(root, screens=0, popups=3)
    shutil.rmtree(os.path.join(java, "screen"), ignore_errors=True)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("tier 3 fault", rc == 3),
                ("fault names the missing package", "INSTRUMENT FAULT: missing screen package" in out)]


@case("neg-missing-main-activity", 3)
def _c_missing_main(root):
    java, res, spec = build(root, no_main=True)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("tier 3 fault", rc == 3),
                ("fault names the missing input", "INSTRUMENT FAULT: missing input" in out)]


@case("neg-non-utf8-input", 3)
def _c_bad_bytes(root):
    java, res, spec = build(root, bad_layout_bytes=bytes([107, 97, 169, 102, 101]))
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("tier 3 fault", rc == 3),
                ("fault names the encoding problem", "not valid UTF-8" in out)]


@case("neg-unbalanced-java-body", 3)
def _c_unbalanced(root):
    java, res, spec = build(root, unbalanced=True)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("tier 3 fault", rc == 3),
                ("fault names the parse degradation", "unbalanced method body" in out),
                ("fault is attributed to a method and a line",
                 __import__("re").search(r"unbalanced method body for \w+ at line \d+", out) is not None)]


@case("neg-dead-include", 3)
def _c_dead_include(root):
    java, res, spec = build(root, dead_include=True)
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    return rc, [("tier 3 fault", rc == 3),
                ("fault names both sides of the dead include",
                 "dead include: broken_host refers to missing layout nope" in out)]


@case("neg-spec-pointer-missing", 1)
def _c_bad_spec(root):
    # A spec that carries the numbers instead of the pointer: the pointer judgement must not hold
    # (rc 1 - a judgement, not an instrument fault), and the count-shaped scan must name the lines
    # so a reader can see which restatements need labelling. The unreadable-input path stays tier 3.
    java, res, spec = build(root, spec="范围：16 个屏幕与 6 个浮层逐屏还原。\n")
    rc, out, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    rc_missing, out_missing, _ = inv(["--java", java, "--res", res,
                                      "--spec", os.path.join(root, "no-such-spec.md"),
                                      "--no-write"])
    # A spec whose only scope-shaped token uses the bare unit: "14 屏" is what a person writes
    # when they restate a measured count, and the old word list (屏幕/浮层/自绘屏) did not see it.
    java_b, res_b, spec_b = build(root, spec="范围：现值 14 屏，其中 5 屏自绘。")
    rc_bare, bare_out, _ = inv(["--java", java_b, "--res", res_b, "--spec", spec_b, "--no-write"])
    assert rc_bare == 1, "the bare-unit fixture must stay a judgement, got rc=%d" % rc_bare
    return rc, [("pointer absence is a judgement (tier 1), not a fault", rc == 1),
                ("the pointer line reports it", "SPEC_POINTER cites=False " in out),
                ("restatements are printed with their line numbers",
                 "SPEC_RESTATED count-shaped=1 lines=1 " in out),
                ("a bare-屏 restatement is named too (the vocabulary hole that hid 14 屏)",
                 bare_out is not None and "SPEC_RESTATED count-shaped=1 lines=1 " in bare_out),
                ("an unreadable spec is still a fault (tier 3)", rc_missing == 3),
                ("the fault names the missing input",
                 "missing input: " in out_missing and "no-such-spec.md" in out_missing)]


@case("pos-renamed-layout-ref-diff", 0)
def _c_rename(root):
    java, res, spec = build(root)
    rc_a, out_a, _ = inv(["--java", java, "--res", res, "--spec", spec, "--no-write"])
    line_a = [l for l in out_a.splitlines() if l.startswith("SCREEN S05Screen ")][0]
    java2, res2, spec2 = build(os.path.join(root, "b"), rename_ref={"index": 5, "target": "gone"})
    rc_b, out_b, _ = inv(["--java", java2, "--res", res2, "--spec", spec2, "--no-write"])
    line_b = [l for l in out_b.splitlines() if l.startswith("SCREEN S05Screen ")][0]
    return rc_a, [("both trees pass the floors", rc_a == 0 and rc_b == 0),
                  ("baseline has no UNREFERENCED line", "UNREFERENCED_LAYOUT" not in out_a),
                  ("renamed reference is detected", line_a != line_b),
                  ("renamed screen now points at the missing layout",
                   "root=gone" in line_b and "root=s05screen_layout" in line_a),
                  ("the orphan is printed, not dropped",
                   "UNREFERENCED_LAYOUT s05screen_layout" in out_b)]


def main():
    missing = [a for a in REQUIRED_ATTRS if not hasattr(G, a)]
    if missing:
        print("INSTRUMENT FAULT: gen_screen_inventory.py is missing %s" % ", ".join(missing))
        print("EXITCODE=3")
        return 3
    if len(CASES) != EXPECTED_CASES:
        print("INSTRUMENT FAULT: %d case(s) registered but the literal says %d"
              % (len(CASES), EXPECTED_CASES))
        print("EXITCODE=3")
        return 3
    tiers = {rc for _, rc, _ in CASES}
    if not {0, 1, 3} <= tiers:
        print("INSTRUMENT FAULT: cases do not cover exit tiers 0, 1 and 3 (got %s)"
              % sorted(tiers))
        print("EXITCODE=3")
        return 3
    if os.path.isdir(RUN_ROOT):
        shutil.rmtree(RUN_ROOT)
    bad = 0
    executed = 0
    try:
        for name, want, fn in CASES:
            root = tempfile.mkdtemp(prefix="c-", dir=_run_root())
            try:
                rc, checks = fn(root)
            except G.Fault as exc:
                rc, checks = 3, [("fixture raised a fault that the case did not expect", str(exc))]
                bad += 1
                print("CASE %s rc=%d want=%d FAIL (unexpected fault: %s)"
                      % (name, rc, want, exc))
                executed += 1
                continue
            except Exception as exc:
                print("INSTRUMENT FAULT in case %s: %s: %s" % (name, type(exc).__name__, exc))
                print("EXITCODE=3")
                return 3
            executed += 1
            ok = rc == want
            failed = [label for label, passed in checks if not passed]
            if failed:
                ok = False
            print("CASE %-32s rc=%d want=%d %s" % (name, rc, want, "ok" if ok else "FAIL"))
            for label, passed in checks:
                print("  check %-52s %s" % (label, "ok" if passed else "FAIL"))
            if not ok:
                bad += 1
    finally:
        shutil.rmtree(RUN_ROOT, ignore_errors=True)
    if executed != EXPECTED_CASES:
        print("INSTRUMENT FAULT: %d case(s) executed, expected %d" % (executed, EXPECTED_CASES))
        print("EXITCODE=3")
        return 3
    print("cases executed=%d expected=%d failures=%d" % (executed, EXPECTED_CASES, bad))
    code = 1 if bad else 0
    print("VERDICT: %s" % ("FAIL" if bad else "PASS"))
    print("EXITCODE=%d" % code)
    return code


def _run_root():
    os.makedirs(RUN_ROOT, exist_ok=True)
    return RUN_ROOT


if __name__ == "__main__":
    sys.exit(main())
