import hashlib, pathlib, re
REPO = pathlib.Path(r"D:\Project\Android\Treelearn")
doc = REPO / "Docs/05-technical-design/\u77e5\u82bd\uff08TreeLearn\uff09DeepSeek\u81ea\u9002\u5e94\u5b66\u4e60\u5bfc\u5e08\u63d0\u793a\u8bcd\u8bbe\u8ba1_V1.0.md"
raw = doc.read_bytes().decode("utf-8").replace("\r\n","\n")
lines = raw.split("\n")
blocks=[]; cur=None; info=None
for i,l in enumerate(lines):
    s=l.strip()
    if s.startswith("```"):
        if cur is None:
            cur=[]; info=s
        else:
            blocks.append((info,cur)); cur=None
    elif cur is not None:
        cur.append(l)
targets = {
 "LEARNING_ANSWER_TUTOR": (715,"f9ac51e5712b031fbae2eeaa77daeba32934a05089f724060ba01de97e85de0d"),
 "THINKING_ADDENDUM": (101,"2ed824cc691e0ac00e7970e3b98171d02cefce5f2a5a45b585390fbc4bfe7dd9"),
 "DERIVED_PROCESSOR_BASE": (186,"9a939cbe2c19c67ec9edd6a4f2fdd0f9321c560d271b334e8602b9c84f3f4b79"),
 "ROOT_TITLE_SUFFIX": (102,"35daa90046f4f5474b0aeaf4b581bc797004d944605f259fe9823a7e69a703bd"),
 "BRANCH_TITLE_SUFFIX": (116,"2211c74eeb96d2b3e7fa3f0da7c265da00b238f8df6ac0da4b3c2efa18bbd655"),
 "BRANCH_SUMMARY_SUFFIX": (156,"9eed73311670155b52649c434d03a13c0af44362869157c6273658f9db02b1ca"),
 "ANSWER_RECOMMENDATIONS_SUFFIX": (200,"db03fdc3a492e56947455c0aa53107dce5c9c9a732816c3af144803476974eb1"),
 "HOME_RECOMMENDATIONS_SUFFIX": (187,"2122a1157db897d8e5fb5da2c4aebf9924ef1c6d8c91873d3ae08d1e725303b3"),
 "CONTEXT_COMPRESSION_SUFFIX": (174,"ab3415cac396fd4b142da37bbcb63f8b86d164735bb836c9ec8be91da4dba20f"),
}
def sha(t): return hashlib.sha256(t.encode("utf-8")).hexdigest()
# candidate normalisations
variants = {}
for info, body in blocks:
    txt = "\n".join(body)
    cands = {
      "join": txt,
      "join+trailNL": txt + "\n",
      "stripNL": txt.strip("\n"),
    }
    for k,v in cands.items():
        variants.setdefault(sha(v), []).append((info, len(v), k))
print("doc fenced blocks:", len(blocks))
found=0
for name,(chars,digest) in targets.items():
    hit = variants.get(digest)
    if hit:
        found+=1
        print(f"  MATCH  {name:32} chars(claim)={chars} blockLen={hit[0][1]} mode={hit[0][2]} fence={hit[0][0]}")
    else:
        print(f"  NO     {name:32} chars(claim)={chars} digest={digest[:16]}...")
print(f"matched {found}/{len(targets)}")