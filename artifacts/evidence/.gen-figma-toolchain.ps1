#requires -Version 5.1
<#
    Generator for the Figma / figwright toolchain evidence:
    artifacts/evidence/figma-toolchain-setup.txt

    Claims proven here, each with the command that produced it and its own exit code:
      1. the ChatGPT/Codex host config (~/.codex/config.toml) carries [mcp_servers.figwright]
         with the version pinned, and still carries every pre-existing server;
      2. the two migrated skills under .agents/skills are byte-identical to the upstream copies
         and their frontmatter names match their directories;
      3. the design-token guard passes on the real files and its positive/negative self-test passes;
      4. the guard is falsifiable: a deliberately drifted colors.xml turns it red, and restoring the
         file byte-for-byte turns it green again (mutation test, hashes printed both times);
      5. the server the config points at really answers the MCP handshake (live npx spawn).

    House rules honoured on purpose (see the failure library in AGENTS.md):
      * streamed capture with Out-File -Encoding utf8 -Append, never ">>" and never Out-String;
      * native stderr captured with $ErrorActionPreference = 'Continue' plus $LASTEXITCODE;
      * every number in this file is printed by the command that measured it;
      * ASCII-only source, so the ANSI/GBK decode trap cannot break the script itself.

    Run from the repository root:
      powershell -ExecutionPolicy Bypass -File artifacts/evidence/.gen-figma-toolchain.ps1
#>
$ErrorActionPreference = 'Continue'

# PowerShell 5.1 decodes native stdout with the console code page (GBK on a zh-CN machine), which
# turns the checks' UTF-8 Chinese output into mojibake and U+FFFD inside this evidence file. The
# assertions at the end of this script verify the fix by scanning the file for U+FFFD.
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {
    Write-Output 'WARN could not set console output encoding to UTF-8'
}
# The standalone snippets fed to `python -` below print Chinese too; without this their stdout would
# be encoded with the locale code page (GBK) and then decoded here as UTF-8, i.e. mojibake again.
$env:PYTHONIOENCODING = 'utf-8'

$root = 'D:\Project\Android\Treelearn'
$evidence = Join-Path $root 'artifacts\evidence\figma-toolchain-setup.txt'
$upstream = 'D:\Project\Skills\figwright-main\skills'
$temp = $env:TEMP
$script:assertFailures = 0
$script:stepFailures = 0

function Write-Evidence {
    param([string]$Line, [switch]$Fresh)
    if ($Fresh) { $Line | Out-File -LiteralPath $evidence -Encoding utf8 }
    else { $Line | Out-File -LiteralPath $evidence -Encoding utf8 -Append }
}

function Assert-That {
    param([bool]$Condition, [string]$Label)
    if ($Condition) { Write-Evidence "ASSERT OK   $Label" }
    else {
        Write-Evidence "ASSERT FAIL $Label"
        $script:assertFailures++
    }
}

function Invoke-Streamed {
    param([string]$Title, [string]$CommandText, [string]$Program, [string[]]$ProgramArgs, [string]$WorkDir)
    Write-Evidence ""
    Write-Evidence "=== $Title ==="
    Write-Evidence "Command: $CommandText"
    Write-Evidence "--- output ---"
    Push-Location $WorkDir
    & $Program @ProgramArgs 2>&1 | ForEach-Object { [string]$_ } | Out-File -LiteralPath $evidence -Encoding utf8 -Append
    $code = $LASTEXITCODE
    Pop-Location
    Write-Evidence "EXITCODE=$code"
    if ($code -ne 0) { $script:stepFailures++ }
    return $code
}

function Invoke-PythonStep {
    param([string]$Title, [string]$Snippet, [string]$SnippetFile)
    Write-Evidence ""
    Write-Evidence "=== $Title ==="
    [System.IO.File]::WriteAllText($SnippetFile, $Snippet, (New-Object System.Text.UTF8Encoding($false)))
    Write-Evidence "Command: python $SnippetFile"
    Write-Evidence "--- snippet source ---"
    $Snippet -split "`n" | ForEach-Object { [string]$_ } | Out-File -LiteralPath $evidence -Encoding utf8 -Append
    Write-Evidence "--- output ---"
    Push-Location $root
    & python $SnippetFile 2>&1 | ForEach-Object { [string]$_ } | Out-File -LiteralPath $evidence -Encoding utf8 -Append
    $code = $LASTEXITCODE
    Pop-Location
    Write-Evidence "EXITCODE=$code"
    if ($code -ne 0) { $script:stepFailures++ }
    return $code
}

if (Test-Path -LiteralPath $evidence) { Remove-Item -LiteralPath $evidence -Force }
Write-Evidence '# Figma / figwright toolchain setup evidence' -Fresh
Write-Evidence "CapturedAt: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Evidence "Working dir: $root"
Write-Evidence 'Script: artifacts/evidence/.gen-figma-toolchain.ps1'
Write-Evidence "Upstream skills: $upstream"

Invoke-Streamed -Title 'Python version used by the checks' -CommandText 'python --version' -Program 'python' -ProgramArgs @('--version') -WorkDir $root | Out-Null
Invoke-Streamed -Title 'Node version used by the probe' -CommandText 'node --version' -Program 'node' -ProgramArgs @('--version') -WorkDir $root | Out-Null

$configSnippet = 'import json, os, tomllib

path = os.path.join(os.environ["USERPROFILE"], ".codex", "config.toml")
data = tomllib.load(open(path, "rb"))
server = data["mcp_servers"]["figwright"]
print("config_path=" + path)
print("command=" + server["command"])
print("args=" + json.dumps(server["args"]))
print("startup_timeout_sec=" + str(server.get("startup_timeout_sec")))
print("all_servers=" + ",".join(sorted(data["mcp_servers"])))
assert server["command"] == "npx", "unexpected command"
assert server["args"][-1] == "@figwright/mcp@0.5.0", "server version is not pinned to 0.5.0"
for name in ("pencil", "node_repl"):
    assert name in data["mcp_servers"], "pre-existing server vanished: " + name
print("OK figwright MCP entry present, version pinned, pre-existing servers intact")
'
Invoke-PythonStep -Title 'ChatGPT/Codex host config carries the figwright MCP server' -Snippet $configSnippet -SnippetFile (Join-Path $temp 'figma-check-config.py') | Out-Null

$skillsSnippet = 'import hashlib, io, os

repo = r"D:\Project\Android\Treelearn"
upstream = r"D:\Project\Skills\figwright-main\skills"
problems = []

def inventory(root):
    out = {}
    for base, _dirs, names in os.walk(root):
        for name in names:
            full = os.path.join(base, name)
            rel = os.path.relpath(full, root).replace(os.sep, "/")
            out[rel] = hashlib.sha256(open(full, "rb").read()).hexdigest()
    return out

for skill in ("figma-build", "figma-codegen"):
    dst = os.path.join(repo, ".agents", "skills", skill)
    src = os.path.join(upstream, skill)
    migrated, original = inventory(dst), inventory(src)
    print("skill=%s files=%d bytes_identical=%s" % (skill, len(migrated), migrated == original))
    if migrated != original:
        problems.append(skill + " differs: " + str(sorted(set(migrated) ^ set(original))))
    lines = io.open(os.path.join(dst, "SKILL.md"), encoding="utf-8").read().splitlines()
    name = next((l.split(":", 1)[1].strip() for l in lines if l.startswith("name:")), "")
    has_desc = any(l.startswith("description:") for l in lines)
    print("  frontmatter_name=%s name_matches_dir=%s has_description=%s" % (name, name == skill, has_desc))
    if name != skill or not has_desc:
        problems.append(skill + ": frontmatter incomplete")
    for rel, digest in sorted(migrated.items()):
        print("  sha256=%s %s" % (digest[:16], rel))
assert not problems, problems
print("OK both migrated skills byte-identical to upstream and self-identifying correctly")
'
Invoke-PythonStep -Title 'Migrated skills match upstream byte-for-byte' -Snippet $skillsSnippet -SnippetFile (Join-Path $temp 'figma-check-skills.py') | Out-Null

$guardSelftest = Invoke-Streamed -Title 'Design-token guard self-test (positive and negative cases)' -CommandText 'python .trae/scripts/dev/check_design_tokens.py --selftest' -Program 'python' -ProgramArgs @('.trae\scripts\dev\check_design_tokens.py', '--selftest') -WorkDir $root
Assert-That ($guardSelftest -eq 0) 'token guard self-test exits 0'

$guardReal = Invoke-Streamed -Title 'Design-token guard on the real files' -CommandText 'python .trae/scripts/dev/check_design_tokens.py' -Program 'python' -ProgramArgs @('.trae\scripts\dev\check_design_tokens.py') -WorkDir $root
Assert-That ($guardReal -eq 0) 'prototype :root, tokens.json and colors.xml agree'

$mutationSnippet = 'import hashlib, os, shutil, subprocess, sys

repo = r"D:\Project\Android\Treelearn"
colors = os.path.join(repo, "android", "app", "src", "main", "res", "values", "colors.xml")
guard = os.path.join(repo, ".trae", "scripts", "dev", "check_design_tokens.py")
backup = colors + ".mutation-backup"

def run():
    done = subprocess.run([sys.executable, guard], cwd=repo, capture_output=True, text=True,
                          encoding="utf-8", errors="replace")
    verdict = [l for l in done.stdout.splitlines() if l.startswith("FAIL") or l.startswith("EXITCODE")]
    return done.returncode, verdict

shutil.copy2(colors, backup)
before = hashlib.sha256(open(colors, "rb").read()).hexdigest()
print("colors.xml sha256 before=%s" % before)

data = open(colors, "rb").read()
assert b"#2E7D4F" in data, "probe value #2E7D4F not found; the mutation would be a no-op"
open(colors, "wb").write(data.replace(b"#2E7D4F", b"#2E7D50"))
print("mutated: tl_green #2E7D4F -> #2E7D50 (one value drifted, nothing else touched)")

red_code, red_lines = run()
print("drifted run exit=%d" % red_code)
for line in red_lines[:4]:
    print("  " + line)

shutil.copy2(backup, colors)
os.remove(backup)
after = hashlib.sha256(open(colors, "rb").read()).hexdigest()
print("colors.xml sha256 after restore=%s identical=%s" % (after, after == before))

green_code, green_lines = run()
print("restored run exit=%d" % green_code)
for line in green_lines[:4]:
    print("  " + line)

assert red_code == 1, "guard stayed green on a drifted value (not falsifiable)"
assert after == before, "restore was not byte-identical"
assert green_code == 0, "guard did not return green after restore"
print("OK guard is falsifiable and the workspace is restored")
'
$mutation = Invoke-PythonStep -Title 'Mutation test: drifted value must turn the guard red, restore must turn it green' -Snippet $mutationSnippet -SnippetFile (Join-Path $temp 'figma-mutation-tokens.py')
Assert-That ($mutation -eq 0) 'mutation red / restore green both observed'

$probe = Invoke-Streamed -Title 'Live MCP handshake (server spawned the way the config spawns it)' -CommandText 'node .trae/scripts/dev/probe_figwright_mcp.mjs 0.5.0' -Program 'node' -ProgramArgs @('.trae\scripts\dev\probe_figwright_mcp.mjs', '0.5.0') -WorkDir $root
Assert-That ($probe -eq 0) 'live handshake exits 0'

$captured = Get-Content -LiteralPath $evidence -Raw
Assert-That ($captured -match 'SERVER=figwright@0\.5\.0') 'handshake reported the server name and version'
Assert-That ($captured -match 'PROTOCOL=2025-06-18') 'handshake reported a protocol version'
Assert-That ($captured -match 'PING_OK=True') 'ping answered ok=true'
Assert-That ($captured -match 'TOOL_COUNT=[0-9][0-9][0-9]') 'tools/list reported a three-digit tool count'
Assert-That (-not ($captured -match [char]0xFFFD)) 'evidence file contains no replacement character'

Write-Evidence ""
Write-Evidence "=== summary ==="
Write-Evidence "assert_failures=$script:assertFailures"
Write-Evidence "step_failures=$script:stepFailures"
Write-Evidence 'note: PLUGIN_CONNECTED=False is expected while the Figma app/plugin is not running; it is a'
Write-Evidence '      live health reading, not a failure of this setup.'
$total = $script:assertFailures + $script:stepFailures
Write-Evidence "EXITCODE=$total"
exit $total