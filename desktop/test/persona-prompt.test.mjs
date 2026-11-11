import test from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { extractPersonaPrompts } from '../scripts/extract-persona-prompts.mjs';

const repo = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
const javaPersona = path.join(repo, 'android/core/src/main/java/com/treelearn/core/domain/Persona.java');
const javaGuard = path.join(repo, 'android/core/src/main/java/com/treelearn/core/domain/Guard.java');
const javaPrompts = path.join(repo, 'android/ai/src/main/java/com/treelearn/ai/prompt/PersonaPrompts.java');

test('derived desktop styles equal the compiled Android policy for all seven identities', t => {
  const javaHome = process.env.JAVA_HOME;
  const javac = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'javac.exe' : 'javac') : 'javac';
  const java = javaHome ? path.join(javaHome, 'bin', process.platform === 'win32' ? 'java.exe' : 'java') : 'java';
  if (javaHome && (!existsSync(javac) || !existsSync(java))) { t.skip('JDK tools unavailable'); return; }
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-prompt-'));
  try {
    const runner = path.join(root, 'ExportPersona.java');
    writeFileSync(runner, `import com.treelearn.core.domain.Persona;
import com.treelearn.ai.prompt.PersonaPrompts;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
public class ExportPersona {
  public static void main(String[] args) {
    for (Persona value : Persona.values()) {
      String encoded = Base64.getEncoder().encodeToString(PersonaPrompts.addendum(value).getBytes(StandardCharsets.UTF_8));
      System.out.println(value.wireId + "\\t" + encoded);
    }
  }
}`);
    const compiled = spawnSync(javac, ['-encoding', 'UTF-8', '-d', root, javaGuard, javaPersona, javaPrompts, runner], { encoding: 'utf8' });
    if (compiled.error?.code === 'ENOENT') { t.skip('JDK tools unavailable'); return; }
    assert.equal(compiled.status, 0, compiled.stderr);
    const executed = spawnSync(java, ['-cp', root, 'ExportPersona'], { encoding: 'utf8' });
    assert.equal(executed.status, 0, executed.stderr);
    const expected = Object.fromEntries(executed.stdout.trimEnd().split(/\r?\n/).map(line => {
      const tab = line.indexOf('\t');
      assert.ok(tab > 0);
      return [line.slice(0, tab), Buffer.from(line.slice(tab + 1), 'base64').toString('utf8')];
    }));
    const actual = extractPersonaPrompts(readFileSync(javaPersona, 'utf8'), readFileSync(javaPrompts, 'utf8'));
    assert.deepEqual({ ...actual }, expected);
    assert.equal(Object.keys(actual).length, 7);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('unsupported source changes fail closed rather than generating missing styles', () => {
  const personas = readFileSync(javaPersona, 'utf8');
  const prompts = readFileSync(javaPrompts, 'utf8');
  assert.throws(() => extractPersonaPrompts(personas.replace('LATE_NIGHT("late-night")', 'LATE_NIGHT("unknown-style")'), prompts));
  assert.throws(() => extractPersonaPrompts(personas, prompts.replace('case LATE_NIGHT ->', 'case UNKNOWN ->')));
});
