import io
def patch(path, marker, offset, newline_text):
    lines=io.open(path,encoding="utf-8",newline="").readlines()
    idx=[i for i,l in enumerate(lines) if marker in l]
    print(path,"marker",marker,"hits",idx)
    assert len(idx)==1, ("marker not unique",path,marker,idx)
    i=idx[0]+offset
    assert lines[i].endswith("\n"), ("target line has no newline",path,i)
    assert not lines[i].strip().startswith("- 代码量"), ("offset landed inside wrong bullet",path,i)
    lines.insert(i+1, newline_text+"\n")
    io.open(path,"w",encoding="utf-8",newline="").write("".join(lines))
    print("  inserted after line",i+1)

patched="  \u53e6\u6309 `.trae` \u88c1\u5b9a\u7684 grep \u53e3\u5f84\u590d\u7b97 = **749**\uff08\u4e0e\u4efb\u52a1\u81ea\u62a5\u9010\u5b57\u76f8\u540c\uff0c\u89c1 `VERIFY2-sizing-ruling-method.txt`\uff09\u3002"
patch(r".trae/tasks/T-2026-09-20-deepseek-stream-gateway-006.md","**748**",2,patched)
patched7="  \u53e6\u6309 `.trae` \u88c1\u5b9a\u7684 grep \u53e3\u5f84\u590d\u7b97 = **671**\uff08664 + `DomainErrorCode` 7\uff0c\u89c1 `VERIFY2-sizing-ruling-method.txt`\uff09\u3002"
patch(r".trae/tasks/T-2026-09-20-immutable-context-snapshots-007.md","**662**",2,patched7)
patched16="  \u53e6\u6309\u88c1\u5b9a grep \u53e3\u5f84\u590d\u7b97 = **861**\uff08\u4e24\u79cd\u53e3\u5f84\u90fd > 500\uff0c\u89c1 `VERIFY2-sizing-ruling-method.txt`\uff09\u3002"
patch(r".trae/tasks/T-2026-09-20-pure-java-tree-layout-016.md","**862**",1,patched16)
print("done")