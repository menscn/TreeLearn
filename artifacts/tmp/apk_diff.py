"""Robust entry-level comparison of two APKs (zipfile, not `unzip -l` parsing)."""
import sys, zipfile

A, B = sys.argv[1], sys.argv[2]
za, zb = zipfile.ZipFile(A), zipfile.ZipFile(B)
ia = {i.filename: i for i in za.infolist()}
ib = {i.filename: i for i in zb.infolist()}

print(f"A = {A}  ({sum(i.compress_size for i in za.infolist())} compressed bytes of entries)")
print(f"B = {B}  ({sum(i.compress_size for i in zb.infolist())} compressed bytes of entries)")
print(f"A entries={len(ia)}  B entries={len(ib)}")
print()

only_a = sorted(set(ia) - set(ib))
only_b = sorted(set(ib) - set(ia))
print(f"--- only in A ({len(only_a)}) ---")
for n in only_a:
    print(f"   {ia[n].compress_size:>8} {n}")
print(f"--- only in B ({len(only_b)}) ---")
for n in only_b:
    print(f"   {ib[n].compress_size:>8} {n}")
print()

deltas = []
for n in set(ia) & set(ib):
    d = ib[n].compress_size - ia[n].compress_size
    if d:
        deltas.append((d, n, ia[n].compress_size, ib[n].compress_size))
deltas.sort(key=lambda t: -abs(t[0]))
print(f"--- shared entries with size delta ({len(deltas)}) ---")
for d, n, sa, sb in deltas[:20]:
    print(f"   {d:+8}  {n}   (A={sa} B={sb})")
print()
sum_shared = sum(d for d, _, _, _ in deltas)
sum_only = sum(ib[n].compress_size for n in only_b) - sum(ia[n].compress_size for n in only_a)
print(f"total delta from shared entries = {sum_shared:+}")
print(f"total delta from A-only/B-only entries = {sum_only:+}")
print(f"sum = {sum_shared + sum_only:+}")
