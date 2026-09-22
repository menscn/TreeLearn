import re,glob,os,io
src=io.open(r"android/ai/src/main/java/com/treelearn/ai/prompt/PromptBodiesV1.java",encoding="utf-8").read()
blocks=re.findall(r"\"\"\"\n?(.*?)\"\"\"",src,re.S)
bodies=[]
for b in blocks:
    b=b.replace("\\\n","")
    t=b.strip("\n")
    if len(t)>=40: bodies.append(t)
print("bodies parsed:",len(bodies),"lengths",[len(b) for b in bodies])
win=10
wins=set()
for b in bodies:
    for i in range(len(b)-win+1):
        wins.add(b[i:i+win])
print("distinct",win,"-char body windows:",len(wins))
hits=[]
for f in glob.glob("artifacts/evidence/**/*",recursive=True):
    if not os.path.isfile(f): continue
    try: t=io.open(f,encoding="utf-8",errors="ignore").read()
    except: continue
    c=sum(1 for i in range(len(t)-win+1) if t[i:i+win] in wins)
    if c: hits.append((f,c))
print("evidence files containing a shared body window:",len(hits))
for f,c in hits[:10]: print("  ",f,c)