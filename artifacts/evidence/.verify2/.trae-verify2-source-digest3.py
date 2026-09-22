import hashlib, pathlib
src = pathlib.Path(r"D:\Project\Android\Treelearn\android\ai\src\main\java\com\treelearn\ai\prompt\PromptBodiesV1.java").read_bytes().decode("utf-8")
targets = {
 "f9ac51e5712b031fbae2eeaa77daeba32934a05089f724060ba01de97e85de0d":("LEARNING_ANSWER_TUTOR",715),
 "2ed824cc691e0ac00e7970e3b98171d02cefce5f2a5a45b585390fbc4bfe7dd9":("THINKING_ADDENDUM",101),
 "9a939cbe2c19c67ec9edd6a4f2fdd0f9321c560d271b334e8602b9c84f3f4b79":("DERIVED_PROCESSOR_BASE",186),
 "35daa90046f4f5474b0aeaf4b581bc797004d944605f259fe9823a7e69a703bd":("ROOT_TITLE_SUFFIX",102),
 "2211c74eeb96d2b3e7fa3f0da7c265da00b238f8df6ac0da4b3c2efa18bbd655":("BRANCH_TITLE_SUFFIX",116),
 "9eed73311670155b52649c434d03a13c0af44362869157c6273658f9db02b1ca":("BRANCH_SUMMARY_SUFFIX",156),
 "db03fdc3a492e56947455c0aa53107dce5c9c9a732816c3af144803476974eb1":("ANSWER_RECOMMENDATIONS_SUFFIX",200),
 "2122a1157db897d8e5fb5da2c4aebf9924ef1c6d8c91873d3ae08d1e725303b3":("HOME_RECOMMENDATIONS_SUFFIX",187),
 "ab3415cac396fd4b142da37bbcb63f8b86d164735bb836c9ec8be91da4dba20f":("CONTEXT_COMPRESSION_SUFFIX",174),
}
lines = src.replace("\r\n","\n").split("\n")
blocks=[]; names=[]; i=0
while i < len(lines):
    if 'static final String' in lines[i] and '"""' in lines[i]:
        nm = lines[i].split("static final String")[1].split("=")[0].strip()
        body=[]; i+=1
        while i < len(lines) and '"""' not in lines[i]:
            body.append(lines[i]); i+=1
        closing = lines[i] if i < len(lines) else ""
        blocks.append((body,closing)); names.append(nm); i+=1
    else: i+=1
def value(body, closing):
    ind=[len(x)-len(x.lstrip(" ")) for x in body if x.strip()]
    ind.append(len(closing)-len(closing.lstrip(" ")))
    cut=min(ind) if ind else 0
    out=[x[cut:] if len(x)>=cut else x for x in body]
    # text block: leading newline dropped; joining uses '\n'; a trailing '\' escapes the terminator
    res=[]; pending=""
    for idx,l in enumerate(out):
        if l.endswith("\\"):
            pending += l[:-1]
            continue
        res.append(pending + l)
        pending = ""
    if pending: res.append(pending)
    return "\n".join(res)
found=0
for (b,c),nm in zip(blocks,names):
    v=value(b,c); d=hashlib.sha256(v.encode("utf-8")).hexdigest()
    exp=targets.get(d)
    if exp and exp[0]==nm:
        found+=1; print(f"  MATCH  {nm:32} chars={len(v)} (claim {exp[1]})")
    else:
        print(f"  NO     {nm:32} chars={len(v)} digest={d[:16]}...")
print(f"matched {found}/9")