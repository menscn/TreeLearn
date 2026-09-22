import re,glob,os,io
src=io.open(r"android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java",encoding="utf-8").read()
blocks=re.findall(r"\"\"\"\n?(.*?)\"\"\"",src,re.S)
bodies=[]
for b in blocks:
    b=b.replace("\\\n","").strip("\n")
    if len(b)>=40: bodies.append(b)
print("bodies:",len(bodies),[len(b) for b in bodies])
files=[]
for f in glob.glob("artifacts/evidence/**/*",recursive=True):
    if os.path.isfile(f): files.append(f)
print("evidence files scanned:",len(files))
for k in (60,40,28,20,16,12):
    wins=set()
    for b in bodies:
        for i in range(len(b)-k+1): wins.add(b[i:i+k])
    hits=[]
    for f in files:
        try: t=io.open(f,encoding="utf-8",errors="ignore").read()
        except: continue
        for i in range(max(0,len(t)-k+1)):
            if t[i:i+k] in wins:
                hits.append((f,t[i:i+k])); break
    print(f"window={k:3d}  files with a shared run: {len(hits)}")
    for f,w in hits[:8]: print("     ",f,"::",w)
    if not hits:
        break