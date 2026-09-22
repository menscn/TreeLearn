import zipfile, hashlib, io, sys, os
p = os.path.join("android","app","build","outputs","apk","release","app-release.apk")
data = open(p,"rb").read()
print("apk-path: " + os.path.abspath(p))
print("apk-bytes: %d" % len(data))
print("apk-sha256: %s" % hashlib.sha256(data).hexdigest())
print("apk-mtime: %s" % __import__("datetime").datetime.fromtimestamp(os.path.getmtime(p)).strftime("%Y-%m-%d %H:%M:%S"))
z = zipfile.ZipFile(io.BytesIO(data))
names = z.namelist()
dex = sorted(n for n in names if n.endswith(".dex"))
print("dex-entries: %s" % ",".join(dex))
blob = b"".join(z.read(n) for n in dex)
syms = ["NoticeText","READING_POSITION","noticeReadingFailure","visibleRows","onRowsLaidOut",
        "Notice","ReadingSession","SqliteReadingStore","onViewportChanged","renderPosition",
        "ChatReadingTest","ChatPresenterTest","Lorg/junit","Lorg/jetbrains/kotlin","Landroidx/"]
for s in syms:
    print("  %-22s dex-occurrences=%d" % (s, blob.count(s.encode())))
arsc = z.read("resources.arsc") if "resources.arsc" in names else b""
print("resources.arsc bytes=%d" % len(arsc))
for s in ["notice_reading_position","chat_notice","notice_storage","notice_reading_failure"]:
    print("  %-24s utf8=%d utf16le=%d" % (s, arsc.count(s.encode("utf-8")), arsc.count(s.encode("utf-16-le"))))