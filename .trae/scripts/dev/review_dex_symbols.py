"""What production symbols are inside the shipped dex (read-only, no device needed).

The release APK is the artifact the user installs, so "is it in the APK" is the only form of
"is it wired" that survives losing the device round. Prints one line per symbol pair.
"""
import sys
import zipfile

PRESENT = [
    b"TreeCanvasWiring",
    b"SqliteTreeCanvasStore",
    b"ChatWiring",
    b"HomeWiring",
    b"SqliteChatStore",
    b"KeystoreSecretStore",
]
ABSENT = [
    b"PendingHomeWiring",
    b"PendingChatWiring",
    b"NOT_WIRED",
    b"NavigationScreen",
    b"SearchScreen",
]


def main(apk):
    with zipfile.ZipFile(apk) as bundle:
        names = [n for n in bundle.namelist() if n.endswith(".dex")]
        print("dex files: " + ", ".join(names))
        blobs = {n: bundle.read(n) for n in names}
    for needle in PRESENT:
        hits = sum(b.count(needle) for b in blobs.values())
        print("%-28s occurrences=%d  expected=present" % (needle.decode(), hits))
    for needle in ABSENT:
        hits = sum(b.count(needle) for b in blobs.values())
        print("%-28s occurrences=%d  expected=absent" % (needle.decode(), hits))


if __name__ == "__main__":
    main(sys.argv[1])
