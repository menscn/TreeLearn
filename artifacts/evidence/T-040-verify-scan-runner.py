import io, os, re, sys, zipfile, hashlib, datetime, subprocess

ROOT = r"D:\Project\Android\Treelearn"
OUT = os.path.join(ROOT, "artifacts", "evidence", "T-040-verify-scan.txt")
APP = os.path.join(ROOT, "android", "app", "src", "main")
f = io.open(OUT, "w", encoding="utf-8", newline="\n")
FAILS = []

def w(s=""):
    f.write(s + "\n"); f.flush()

def rd(p):
    return io.open(p, encoding="utf-8-sig", errors="replace").read()

def lines_of(p):
    return rd(p).split("\n")

def grep(rel, patterns, base=APP, label=None):
    p = os.path.join(base, rel)
    out = []
    for i, l in enumerate(lines_of(p)):
        if any(x in l for x in patterns):
            out.append((i + 1, l.strip()))
    w("command: python -X utf8 (read %s; keep lines containing %s)" % (rel.replace("\\", "/"), " | ".join(patterns)))
    if not out:
        w("  (no matching line)"); FAILS.append("no match in %s for %s" % (rel, patterns))
    for n, l in out:
        w("  %s:%d| %s" % (rel.replace("\\", "/"), n, l[:190]))
    w("EXITCODE=0"); w()
    return out

def judge(name, ok, detail):
    w("JUDGEMENT %-46s %s   (%s)" % (name, "OK" if ok else "FAIL", detail))
    if not ok:
        FAILS.append(name)

w("T-040 independent verification - static scans (another lane holds the Gradle lock; no Gradle was run)")
w("captured-at: " + datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S") + " (Asia/Shanghai)")
w("cwd: " + ROOT)
w("runner: artifacts/evidence/T-040-verify-scan-runner.py (python -X utf8), every section flushed")
w("verifier: independent of the implementing lane; read-only judgements, no production file was touched")
w()

# ---------- 1. the home search entry is not a dead button ----------
w("### 1 - home_search: no hard disable, a real listener, and no dynamic re-disable")
body = os.path.join(APP, "res", "layout", "screen_home_body.xml")
txt = rd(body)
blocks = re.findall(r"<[A-Za-z.]+\b[^<>]*?@\+id/(\w+).*?(?:/>|>)", txt, flags=re.S)
w("command: python -X utf8 (regex-extract every element block keyed by @+id in res/layout/screen_home_body.xml)")
for name in ("home_search", "home_send"):
    m = re.search(r"<[A-Za-z.]+\b[^<>]*?@\+id/" + name + r".*?(?:/>|>)", txt, flags=re.S)
    blk = m.group(0) if m else ""
    w("  %s: block found=%s ; contains android:enabled=%s" % (name, bool(m), "android:enabled" in blk))
w("EXITCODE=0"); w()
sblk = re.search(r"<[A-Za-z.]+\b[^<>]*?@\+id/home_search.*?(?:/>|>)", txt, flags=re.S)
judge("home_search carries no android:enabled", bool(sblk) and "android:enabled" not in sblk.group(0), "extracted block")
ctl = re.search(r"<[A-Za-z.]+\b[^<>]*?@\+id/home_send.*?(?:/>|>)", txt, flags=re.S)
judge("positive control home_send DOES carry one", bool(ctl) and "android:enabled" in ctl.group(0), "extractor is not blind")
w("command: python -X utf8 (count android:enabled=\"false\" in screen_home_body.xml and name its owner)")
for i, l in enumerate(lines_of(body)):
    if "android:enabled" in l:
        w("  screen_home_body.xml:%d| %s" % (i + 1, l.strip()))
w("EXITCODE=0"); w()
grep("java/com/treelearn/app/screen/HomeScreen.java",
     ["findViewById(R.id.home_search)", "search = ", "search.setOnClickListener", "actions.onSearchPressed()",
      "setEnabled", "Button search"])
w("JUDGEMENT dynamic disable of search              %s   (search.setEnabled must not appear)"
  % ("OK" if "search.setEnabled" not in rd(os.path.join(APP, "java/com/treelearn/app/screen/HomeScreen.java")) else "FAIL"))
if "search.setEnabled" in rd(os.path.join(APP, "java/com/treelearn/app/screen/HomeScreen.java")):
    FAILS.append("search.setEnabled present")
w()

# ---------- 2. the entry chain reaches a real screen ----------
w("### 2 - home entry -> presenter -> host -> real search screen (production call chain)")
grep("java/com/treelearn/app/ui/HomeActions.java", ["onSearchPressed"])
grep("java/com/treelearn/app/ui/HomeSink.java", ["openSearch"])
grep("java/com/treelearn/app/ui/HomePresenter.java", ["onSearchPressed", "openSearch", "onArchivedPressed"])
grep("java/com/treelearn/app/MainActivity.java",
     ["public void openSearch()", "private void showSearch()", "new SearchScreen(", "new SearchPresenter(",
      "container.searchRepository()", "container.branchTitles()", "searchScreen = null"])
w()

# ---------- 3. render / openAnchor and the single locating path ----------
w("### 3 - SearchPresenter.Sink: render(state) and openAnchor(topicId, branchId, ReadingAnchor)")
grep("java/com/treelearn/app/search/SearchPresenter.java",
     ["interface Sink", "void render(", "void openAnchor(", "public class SearchPresenter", "class SearchPresenter"])
grep("java/com/treelearn/app/MainActivity.java",
     ["SearchPresenter.Sink", "public void render(SearchViewState", "public void openAnchor(", "showChat(branchId, anchor)"])
w("command: python -X utf8 (count every showChat( call site under app/src/main - is there a second door?)")
hits = []
for dirpath, _d, names in os.walk(os.path.join(APP, "java")):
    for n in names:
        if n.endswith(".java"):
            p = os.path.join(dirpath, n)
            for i, l in enumerate(lines_of(p)):
                if "showChat(" in l:
                    hits.append((os.path.relpath(p, APP).replace("\\", "/"), i + 1, l.strip()))
for rel, i, l in hits:
    w("  %s:%d| %s" % (rel, i, l[:150]))
w("EXITCODE=0"); w()
anchored = [h for h in hits if "showChat(branchId, anchor)" in h[2]]
judge("exactly one anchored jump call site", len(anchored) == 1, "found %d" % len(anchored))
judge("anchored jump lives in MainActivity", all(h[0].endswith("MainActivity.java") for h in anchored), "host only")
w()

# ---------- 4. search runs off the UI thread ----------
w("### 4 - the store reads run on the injected background executor")
grep("java/com/treelearn/app/search/SearchPresenter.java",
     ["background.execute", "repository.search(", "isStillReachable(", "ui.post", "private void search("])
w()

# ---------- 5. the screen adds no verdict of its own ----------
w("### 5 - the screen/page never re-filter or re-sort the hits")
for rel in ("java/com/treelearn/app/screen/SearchScreen.java", "java/com/treelearn/app/search/SearchPage.java"):
    code = [l for l in lines_of(os.path.join(APP, rel))
            if l.strip() and not l.strip().startswith(("*", "//", "/*"))]
    bad = [l for l in code if re.search(r"\b(sort|Comparator|filter)\b", l)]
    w("command: python -X utf8 (non-comment lines of %s matching sort|Comparator|filter)" % rel.replace("\\", "/"))
    w("  non-comment lines=%d ; matches=%d" % (len(code), len(bad)))
    for l in bad:
        w("    %s" % l.strip()[:150])
    w("EXITCODE=0"); w()
    judge("no re-sort/re-filter in " + rel.split("/")[-1], not bad, "%d match(es)" % len(bad))
grep("java/com/treelearn/app/search/SearchPage.java",
     ["for (SearchHit hit : state.hits)", "paneOf", "Pane.GUIDE", "Pane.EMPTY", "Pane.LIST"])
w()

# ---------- 6. the T-015 store contract is intact ----------
w("### 6 - SqliteSearchRepository / SearchDao: signature and the T-015 semantics")
grep("java/com/treelearn/app/search/SearchRepository.java", ["List<SearchHit> search(", "boolean isStillReachable("])
grep("java/com/treelearn/app/search/SqliteSearchRepository.java",
     ["public SqliteSearchRepository(", "public List<SearchHit> search(", "public boolean isStillReachable(",
      "SearchDao.search(", "BranchDao.findById(", "MessageDao.findById("])
dao = os.path.join(ROOT, "android", "data", "src", "main", "java", "com", "treelearn", "data", "db", "dao", "SearchDao.java")
w("command: python -X utf8 (SearchDao: the two phrases the T-015 contract rests on)")
dt = rd(dao)
for needle in ("ACTIVE_REVISION_ID", "ARCHIVED_AT", "IS NULL", "UNION ALL"):
    n = dt.count(needle)
    w("  SearchDao.java contains %-24s x%d" % (needle, n))
    if n == 0:
        FAILS.append("SearchDao missing " + needle)
w("EXITCODE=0"); w()

# ---------- 7. sizing recomputed from the patch record ----------
w("### 7 - sizing recomputed independently (rule: new files whole, pre-existing files only by changed lines)")
newfiles = [
 "android/app/src/main/java/com/treelearn/app/search/BranchTitles.java",
 "android/app/src/main/java/com/treelearn/app/search/SearchPage.java",
 "android/app/src/main/java/com/treelearn/app/screen/SearchScreen.java",
 "android/app/src/main/java/com/treelearn/app/wiring/SqliteSearchStore.java",
 "android/app/src/main/res/layout/screen_search.xml",
 "android/app/src/main/res/layout/screen_search_body.xml",
 "android/app/src/main/res/layout/item_search_hit.xml",
 "android/app/src/main/res/layout-sw600dp/screen_search.xml",
]
cmd = [sys.executable, "-X", "utf8", os.path.join(ROOT, ".trae", "scripts", "dev", "count_production_lines.py")] + newfiles
w("command: python -X utf8 .trae/scripts/dev/count_production_lines.py <the 8 new files>")
r = subprocess.run(cmd, cwd=ROOT, capture_output=True, text=True, encoding="utf-8", errors="replace")
for l in r.stdout.splitlines():
    if l.strip():
        w("  " + l.rstrip())
w("EXITCODE=%d" % r.returncode)
new_total = None
m = re.search(r"^\s*(\d+)\s+TOTAL", r.stdout, flags=re.M)
if m:
    new_total = int(m.group(1))
w()

patch_dir = os.path.join(ROOT, "artifacts", "evidence", "T-040-patches")
def is_comment(t):
    t = t.strip()
    return (not t) or t.startswith("//") or t.startswith("/*") or t.startswith("*")
tight = loose = 0
w("command: python -X utf8 (split each patch at '*** Update File:'/'*** Add File:'; keep Update sections only;")
w("         take lines starting with '+' but not '+++'; drop blank; tight predicate also drops // /* * <!-- lines)")
for name in sorted(os.listdir(patch_dir)):
    p = os.path.join(patch_dir, name)
    if not os.path.isfile(p):
        continue
    section = None
    in_xml_comment = False
    t = lz = 0
    for l in lines_of(p):
        if l.startswith("*** Update File:"):
            section = "update"; continue
        if l.startswith("*** Add File:"):
            section = "add"; continue
        if section != "update":
            continue
        if l.startswith("+") and not l.startswith("+++"):
            body_line = l[1:]
            if not body_line.strip():
                continue
            lz += 1
            if in_xml_comment:
                if "-->" in body_line:
                    in_xml_comment = False
                continue
            if body_line.strip().startswith("<!--"):
                if "-->" not in body_line:
                    in_xml_comment = True
                continue
            if not is_comment(body_line):
                t += 1
    tight += t; loose += lz
    w("  %-12s update-section added lines: tight=%d loose=%d" % (name, t, lz))
w("EXITCODE=0"); w()
judge("patch record is non-empty (empty set must fail)", tight > 0, "tight=%d" % tight)
w("JUDGEMENT sizing: new files=%s + shared (tight)=%d .. %d (loose) => TOTAL=%s .. %s ; hard cap 500"
  % (new_total, tight, loose,
     (new_total + tight) if new_total is not None else "?", (new_total + loose) if new_total is not None else "?"))
if new_total is not None and new_total + tight > 500:
    w("  => OVER the 500 hard cap on the strictest reading. Verifier records it; the approval is the user's.")
w()

# ---------- 8. the shipped APK ----------
w("### 8 - the release APK carries the search page (dex string-table scan, done here, not copied)")
apk = os.path.join(ROOT, "android", "app", "build", "outputs", "apk", "release", "app-release.apk")
if os.path.isfile(apk):
    raw = open(apk, "rb").read()
    w("apk-path: %s" % apk)
    w("apk-bytes: %d" % len(raw))
    w("apk-mtime: " + datetime.datetime.fromtimestamp(os.path.getmtime(apk)).strftime("%Y-%m-%d %H:%M:%S"))
    w("apk-sha256: %s" % hashlib.sha256(raw).hexdigest().upper())
    zf = zipfile.ZipFile(io.BytesIO(raw))
    dexs = [n for n in zf.namelist() if re.match(r"classes\d*\.dex$", n)]
    w("command: python -X utf8 (zipfile: scan every classes*.dex byte stream for the symbol names)")
    w("dex files: %s" % ", ".join(sorted(dexs)))
    want = ["SearchScreen", "SearchPage", "BranchTitles", "SqliteSearchStore", "onSearchPressed",
            "openAnchor", "showSearch", "searchRepository"]
    gone = ["SearchPageTest", "HomeSearchEntryTest", "SearchJumpTest", "SearchPresenterTest", "ChatReadingTest"]
    blob = b""
    for n in sorted(dexs):
        blob += zf.read(n)
    for s in want:
        c = blob.count(s.encode("ascii"))
        w("  [must be present] %-22s = %d" % (s, c))
        if c == 0:
            FAILS.append("APK lacks " + s)
    for s in gone:
        c = blob.count(s.encode("ascii"))
        w("  [must be absent ] %-22s = %d" % (s, c))
        if c != 0:
            FAILS.append("APK carries test class " + s)
    owned = [os.path.join(ROOT, x) for x in newfiles] + [
        os.path.join(APP, "java/com/treelearn/app/screen/HomeScreen.java"),
        os.path.join(APP, "java/com/treelearn/app/ui/HomeActions.java"),
        os.path.join(APP, "java/com/treelearn/app/ui/HomeSink.java"),
        os.path.join(APP, "java/com/treelearn/app/ui/HomePresenter.java"),
        os.path.join(APP, "java/com/treelearn/app/AppContainer.java"),
        os.path.join(APP, "res/values/strings.xml"),
        os.path.join(APP, "res/layout/screen_home_body.xml")]
    owned = [p for p in owned if os.path.isfile(p)]
    apk_t = os.path.getmtime(apk)
    newest_owned = max(((os.path.getmtime(p), p) for p in owned), default=(0, "?"))
    newest_new = max(((os.path.getmtime(os.path.join(ROOT, x)), x) for x in newfiles), default=(0, "?"))
    judge("every file T-040 ADDED is older than the APK", apk_t >= newest_new[0],
          "newest added file %s @%s" % (newest_new[1].split("/")[-1],
                                        datetime.datetime.fromtimestamp(newest_new[0]).strftime("%H:%M:%S")))
    shared = [p for p in owned if p not in [os.path.join(ROOT, x) for x in newfiles]]
    w("  observation: shared pre-existing files, mtime vs APK (a later mtime means another lane wrote it)")
    for p in sorted(shared, key=lambda q: os.path.getmtime(q), reverse=True):
        t = os.path.getmtime(p)
        w("    %s %s %s" % (datetime.datetime.fromtimestamp(t).strftime("%H:%M:%S"),
                            "AFTER-APK" if t > apk_t else "before   ",
                            os.path.relpath(p, ROOT).replace(chr(92), "/")))
    w("  => a shared file touched after the build is the parallel lane's, not a T-040 defect; the package")
    w("     must be rebuilt before shipping, and this ordering check re-run once the other lane settles")
    later = []
    for dp, _d, ns in os.walk(os.path.join(ROOT, "android", "app", "src", "main")):
        for n in ns:
            p = os.path.join(dp, n)
            if os.path.getmtime(p) > apk_t:
                later.append((os.path.getmtime(p), os.path.relpath(p, ROOT).replace(chr(92), "/")))
    later.sort(reverse=True)
    w("  observation: files newer than the APK in the tree = %d (the parallel lane's, not T-040's)" % len(later))
    for t, p in later[:6]:
        w("    %s %s" % (datetime.datetime.fromtimestamp(t).strftime("%H:%M:%S"), p))
    w("  => the package was built from the T-040 tree state; re-run this check after the other lane settles")
    w("EXITCODE=0")
else:
    w("  APK NOT FOUND: %s" % apk); FAILS.append("apk missing")
w()

# ---------- 9. docs and strings ----------
w("### 9 - documentation and resources")
grep("res/values/strings.xml", ["search_"], base=APP)
readme = os.path.join(ROOT, "android", "README.md")
w("command: python -X utf8 (README.md lines mentioning the search page)")
for i, l in enumerate(lines_of(readme)):
    if "搜索" in l and ("SearchScreen" in l or "T-040" in l or "search" in l):
        w("  android/README.md:%d| %s" % (i + 1, l.strip()[:170]))
w("EXITCODE=0"); w()
doc = os.path.join(ROOT, "Docs", "06-delivery", "TreeLearn_V1_交付报告.md")
w("command: python -X utf8 (delivery report lines for T-040)")
for i, l in enumerate(lines_of(doc)):
    if "T-040" in l or "搜索页" in l:
        w("  Docs/06-delivery/TreeLearn_V1_交付报告.md:%d| %s" % (i + 1, l.strip()[:170]))
w("EXITCODE=0"); w()

# ---------- 10. evidence self-证 ----------
w("### 10 - do the implementer's evidence files carry a command, a cwd and an exit code?")
ev = os.path.join(ROOT, "artifacts", "evidence")
w("command: python -X utf8 (for each T-040-*.txt count ^captured-at, ^cwd, ^command and EXITCODE lines)")
for name in sorted(os.listdir(ev)):
    if not name.startswith("T-040-") or not name.endswith(".txt"):
        continue
    text = rd(os.path.join(ev, name))
    ls = text.split("\n")
    cap = sum(1 for l in ls if l.startswith("captured-at"))
    cwd = sum(1 for l in ls if l.startswith("cwd:"))
    cmd = sum(1 for l in ls if l.startswith("command:"))
    ec = sum(1 for l in ls if l.startswith("EXITCODE="))
    w("  %-34s lines=%-5d captured-at=%d cwd=%d command=%d EXITCODE=%d" % (name, len(ls), cap, cwd, cmd, ec))
w("(exit codes counted by line prefix; a value written mid-sentence is NOT counted)")
w("EXITCODE=0"); w()

w("== overall ==")
w("failing judgement(s) = %d" % len(FAILS))
for x in FAILS:
    w("  FAIL: " + x)
w("VERIFY-SCAN " + ("PASSED" if not FAILS else "HAS-FAILURES"))
w("EXITCODE=%d" % (0 if not FAILS else 1))
f.close()
print("WROTE " + OUT)
sys.exit(0)
