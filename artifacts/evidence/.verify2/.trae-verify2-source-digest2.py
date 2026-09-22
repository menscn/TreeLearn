import hashlib, pathlib
src = pathlib.Path(r"D:\Project\Android\Treelearn\android\ai\src\main\java\com\treelearn\ai\prompt\PromptBodiesV1.java").read_bytes().decode("utf-8")
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
def render_variants(body, closing):
    b=body[:]
    ind=[len(x)-len(x.lstrip(" ")) for x in b if x.strip()]
    ind.append(len(closing)-len(closing.lstrip(" ")))
    cut=min(ind) if ind else 0
    out=[x[cut:] if len(x)>=cut else x for x in b]
    j="\n".join(out)
    return {"join":j, "joinNL":j+"\n", "raw": "\n".join(b)}
for (body,closing),nm in zip(blocks,names):
    vs=render_variants(body,closing)
    parts = " ".join(f"{k}={len(v)}" for k,v in vs.items())
    print(f"  {nm:32} bodyLines={len(body)} {parts}")