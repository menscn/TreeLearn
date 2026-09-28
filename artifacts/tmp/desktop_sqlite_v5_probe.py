# -*- coding: utf-8 -*-
"""Desktop-SQLite cross-check for the V4 -> V5 annotation migration (T-2026-09-28-026).

The statements below are transcribed 1:1 from TreeLearnSchema.java (line refs in the
evidence file): createTable(Annotations.TABLE, ANNOTATIONS_DEFINITIONS) at lines
541-569 + 1063-1072, annotationsAnchorIndexStatement() at 646-652,
annotationsMessageIndexStatement() at 659-662. The authoritative engine evidence
stays the API-28+ device run; this desktop pass only proves the same SQL is accepted
by a standard SQLite with the same constraint semantics.
"""
import sqlite3
import sys

CREATE_ANNOTATIONS = (
    "CREATE TABLE message_annotations ("
    "id TEXT NOT NULL PRIMARY KEY, "
    "message_id TEXT NOT NULL REFERENCES messages(id) ON DELETE CASCADE, "
    "container TEXT NOT NULL, "
    "prose_index INTEGER NOT NULL, "
    "table_index INTEGER NOT NULL, "
    "row_index INTEGER NOT NULL, "
    "col_index INTEGER NOT NULL, "
    "start_offset INTEGER NOT NULL, "
    "end_offset INTEGER NOT NULL, "
    "kind TEXT NOT NULL, "
    "content_digest TEXT NOT NULL, "
    "body TEXT NOT NULL DEFAULT '', "
    "created_at INTEGER NOT NULL, "
    "updated_at INTEGER NOT NULL, "
    "CHECK (end_offset > start_offset), "
    "CHECK (kind IN ('HIGHLIGHT','NOTE')), "
    "CHECK (kind <> 'NOTE' OR body <> ''), "
    "CHECK (kind <> 'HIGHLIGHT' OR body = ''), "
    "CHECK ((container = 'PROSE' AND prose_index >= 0 AND table_index = -1"
    " AND row_index = -1 AND col_index = -1) OR (container = 'CELL'"
    " AND prose_index = -1 AND table_index >= 0 AND row_index >= 0 AND col_index >= 0)))"
)
ANCHOR_INDEX = (
    "CREATE UNIQUE INDEX idx_annotations_anchor ON message_annotations("
    "message_id, container, prose_index, table_index, row_index, col_index,"
    " kind, start_offset, end_offset)"
)
MESSAGE_INDEX = "CREATE INDEX idx_annotations_message ON message_annotations(message_id)"
MIGRATION = [CREATE_ANNOTATIONS, ANCHOR_INDEX, MESSAGE_INDEX]

INSERT = ("INSERT INTO message_annotations(id, message_id, container, prose_index,"
          " table_index, row_index, col_index, start_offset, end_offset, kind,"
          " content_digest, body, created_at, updated_at)"
          " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)")

results = []


def check(name, fn):
    try:
        fn()
        results.append((name, "PASS", ""))
    except AssertionError as e:
        results.append((name, "FAIL", str(e)))
    except Exception as e:  # noqa: BLE001
        results.append((name, "FAIL", "%s: %s" % (type(e).__name__, e)))


def refuse(db, why, params):
    """The insert must be rejected with an integrity-class error."""
    try:
        db.execute(INSERT, params)
    except sqlite3.IntegrityError:
        return
    raise AssertionError("statement was accepted: " + why)


def accept(db, params):
    db.execute(INSERT, params)


def count(db):
    return db.execute("SELECT COUNT(*) FROM message_annotations").fetchone()[0]


def probe_constraints():
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys = ON")
    db.execute("CREATE TABLE messages(id TEXT NOT NULL PRIMARY KEY)")
    db.execute("INSERT INTO messages(id) VALUES('m1')")
    for statement in MIGRATION:
        db.execute(statement)

    def check_rejections():
        cases = [
            ("empty range", "a1", "PROSE", (1, -1, -1, -1), 5, 5, "HIGHLIGHT", ""),
            ("inverted range", "a2", "PROSE", (1, -1, -1, -1), 7, 5, "HIGHLIGHT", ""),
            ("unknown kind", "a3", "PROSE", (1, -1, -1, -1), 0, 5, "MARK", ""),
            ("unknown container", "a4", "TABLE", (-1, 0, 0, 0), 0, 5, "HIGHLIGHT", ""),
            ("NOTE empty body", "a5", "PROSE", (1, -1, -1, -1), 0, 5, "NOTE", ""),
            ("HIGHLIGHT with body", "a6", "PROSE", (1, -1, -1, -1), 0, 5, "HIGHLIGHT", "t"),
            ("PROSE with table idx", "a7", "PROSE", (0, 0, -1, -1), 0, 5, "HIGHLIGHT", ""),
            ("CELL with prose idx", "a8", "CELL", (0, -1, -1, -1), 0, 5, "HIGHLIGHT", ""),
            ("CELL negative row", "a9", "CELL", (-1, 0, -1, 0), 0, 5, "HIGHLIGHT", ""),
        ]
        for why, ann_id, cont, sent, s, e, kind, body in cases:
            refuse(db, why, (ann_id, "m1", cont, sent[0], sent[1], sent[2], sent[3],
                             s, e, kind, "digest-alpha", body, 1, 1))

    check("ddl rejects the nine malformed CHECK cases", check_rejections)

    def check_anchor():
        # Same range + same kind: refused by the unique anchor.
        accept(db, ("u1", "m1", "PROSE", 1, -1, -1, -1, 10, 20, "HIGHLIGHT", "d", "", 1, 1))
        refuse(db, "duplicate anchor (same kind, same range)",
               ("u2", "m1", "PROSE", 1, -1, -1, -1, 10, 20, "HIGHLIGHT", "d", "", 1, 1))
        # Same range + other kind: kind is part of the anchor, so it coexists.
        accept(db, ("u3", "m1", "PROSE", 1, -1, -1, -1, 10, 20, "NOTE", "d", "note", 1, 1))
        n = count(db)
        assert n == 2, "same range across kinds must coexist, got %d rows" % n

    check("unique anchor dedups; same range across kinds coexists", check_anchor)

    def check_fk():
        refuse(db, "annotation for a missing message",
               ("f1", "m-ghost", "PROSE", 0, -1, -1, -1, 0, 5, "HIGHLIGHT", "d", "", 1, 1))
        db.execute("DELETE FROM messages WHERE id='m1'")
        n = count(db)
        assert n == 0, "cascade must remove annotation rows, got %d" % n
        assert not db.execute("PRAGMA foreign_key_check").fetchall(), "foreign_key_check not empty"
        assert db.execute("PRAGMA integrity_check").fetchone()[0] == "ok", "integrity_check not ok"

    check("FK refuses missing message; cascade clears rows; pragmas ok", check_fk)
    db.close()


def probe_upgrade():
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys = ON")
    db.execute("CREATE TABLE messages(id TEXT NOT NULL PRIMARY KEY)")
    db.execute("INSERT INTO messages(id) VALUES('m-v4')")
    db.execute("PRAGMA user_version = 4")
    for statement in MIGRATION:
        db.execute(statement)
    db.execute("PRAGMA user_version = 5")
    assert db.execute("PRAGMA user_version").fetchone()[0] == 5, "version not stamped"
    assert db.execute("SELECT COUNT(*) FROM messages").fetchone()[0] == 1, "V4 rows lost"
    accept(db, ("v1", "m-v4", "PROSE", 0, -1, -1, -1, 0, 5, "HIGHLIGHT", "d", "", 1, 1))
    assert count(db) == 1, "migrated table must be immediately writable"
    db.close()


check("fresh V5: three migration statements accepted on desktop SQLite", probe_constraints)
check("upgrade path: V4 file + migration keeps rows and is writable", probe_upgrade)

print("== desktop SQLite V4->V5 probe ==")
failed = 0
for name, status, detail in results:
    print("[%s] %s%s" % (status, name, (" -- " + detail) if detail else ""))
    if status == "FAIL":
        failed += 1
print("SUMMARY: %d check(s), %d failed" % (len(results), failed))
sys.exit(1 if failed else 0)
