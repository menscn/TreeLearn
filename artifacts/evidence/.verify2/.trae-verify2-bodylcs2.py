import re,glob,os,io
src=io.open(r"android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java",encoding="utf-8").read()
blocks=re.findall(r"\"\"\"\n?(.*?)\"\"\"",src,re.S)
bodies=[b.replace("\\\n","").strip("\n") for b in blocks if len(b.replace("\\\n","").strip("\n"))>=40]
files=[f for f in glob.glob("artifacts/evidence/**/*",recursive=True) if os.path.isfile(f)]
for k in (40,28,20,14):
    wins=set()
    for b in bodies:
        for i in range(len(b)-k+1): wins.add(b[i:i+k])
    hits=[]
    for f in files:
        try: t=io.open(f,encoding="utf-8",errors="ignore").read()
        except: continue
        for i in range(max(0,len(t)-k+1)):
            if t[i:i+k] in wins: hits.append((f,t[i:i+k])); break
    print(f"window={k:3d}  files sharing a run: {len(hits)}")
    for f,w in hits[:6]: print("     ",f,"::",w)