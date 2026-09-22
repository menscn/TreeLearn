import io
path=r".trae/tasks/T-2026-09-21-versioned-learning-prompt-policy-020.md"
t=io.open(path,encoding="utf-8",newline="").read()
a="  verifier \u8bc1\u636e `VERIFY2-{jvm-suites,T020-leakscan,T020-transcription,sizing,static-findings}.txt`\u3002"
b="  verifier \u8bc1\u636e `VERIFY2-{jvm-suites,T020-leakscan,T020-transcription,round2-static,sizing,static-findings}.txt`\u3002"
assert t.count(a)==1, ("anchor A count", t.count(a))
t=t.replace(a,b)
c="    \u5728\u65ad\u8a00\u8f6c\u7ea2\u65f6\u53ea\u6253\u5370\u957f\u5ea6\u4e0e SHA-256\uff08`SystemBodyAssertions.java:28-34`\uff09\u3002"
assert t.count(c)==1, ("anchor C count", t.count(c))
nl="\r\n" if "\r\n" in t else "\n"
d=c+nl+("    \u53e6\u505a**\u6700\u957f\u8fde\u7eed\u4e32**\u590d\u6838\uff1a\u5bf9 9 \u6bb5\u6b63\u6587\u5207 20 \u5b57\u7a97\u53e3\u626b\u5168\u90e8 166 \u4e2a\u8bc1\u636e\u6587\u4ef6\uff0c"
        "**\u5171\u4eab\u8fde\u7eed\u4e32 = 0**\uff08`VERIFY2-round2-static.txt` \u00a7A\uff09\u3002")
t=t.replace(c,d)
io.open(path,"w",encoding="utf-8",newline="").write(t)
print("patched ok, newline=", repr(nl))