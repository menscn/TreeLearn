import hashlib, pathlib
src = pathlib.Path(r"D:\Project\Android\Treelearn\android\ai\src\main\java\com\treelearn\ai\prompt\PromptBodiesV1.java").read_bytes().decode("utf-8")
lines = src.replace("\r\n","\n").split("\n")
i=0
while i < len(lines):
    if 'static final String' in lines[i] and '"""' in lines[i]:
        nm = lines[i].split("static final String")[1].split("=")[0].strip()
        body=[]; i+=1
        while i < len(lines) and '"""' not in lines[i]:
            body.append(lines[i]); i+=1
        closing = lines[i] if i < len(lines) else ""
        b=body[:]
        ind=[len(x)-len(x.lstrip(" ")) for x in b if x.strip()]
        ind.append(len(closing)-len(closing.lstrip(" ")))
        cut=min(ind) if ind else 0
        out=[x[cut:] if len(x)>=cut else x for x in b]
        j="\n".join(out)
        if nm=="THINKING_ADDENDUM":
            print(f"  {nm}: bodyLines={len(b)} contentIndent={[len(x)-len(x.lstrip(' ')) for x in b]} closingIndent={len(closing)-len(closing.lstrip(' '))} cut={cut}")
            print(f"    rendered len={len(j)}  tail codepoints={[hex(ord(c)) for c in j[-3:]]}")
            print(f"    raw body line len={len(b[0])} tail codepoints={[hex(ord(c)) for c in b[0][-3:]]}")
        i+=1
    else: i+=1