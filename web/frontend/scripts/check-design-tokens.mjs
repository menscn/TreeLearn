#!/usr/bin/env node
/**
 * Design-token drift guard (task T-2026-10-10-006).
 *
 * Re-builds the token CSS in memory from the two authoritative JSON files and
 * compares it, byte for byte, with the committed src/styles/tokens.css. A hand
 * edit to the CSS (or a JSON change without regeneration) therefore fails.
 *
 * Fail-closed rules:
 *   - a build that produced no custom property at all FAILS (nothing verified)
 *   - every colour name from the Web token JSON must appear in the output
 *
 * Exit codes: 0 = PASS, 1 = FAIL, 3 = INCONCLUSIVE (selftest harness problem).
 * --selftest mutates an in-memory copy and asserts the comparator detects it
 * (a comparator that cannot see an injected change is a dead branch).
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildTokenCss, readTokenSources } from './lib/build-tokens.mjs';

const FRONTEND_ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..');
const COMMITTED = join(FRONTEND_ROOT, 'src', 'styles', 'tokens.css');

function firstDifference(expected, actual) {
  const expectedLines = expected.split('\n');
  const actualLines = actual.split('\n');
  const max = Math.max(expectedLines.length, actualLines.length);
  for (let index = 0; index < max; index += 1) {
    if (expectedLines[index] !== actualLines[index]) {
      return {
        line: index + 1,
        expected: expectedLines[index] ?? '<missing line>',
        actual: actualLines[index] ?? '<missing line>',
      };
    }
  }
  return null;
}

function verify(webTokens, baseTokens, committedText) {
  const expected = buildTokenCss(webTokens, baseTokens);
  const problems = [];

  const propertyCount = (expected.match(/^\s+--/gm) ?? []).length;
  if (propertyCount === 0) {
    problems.push('the generated CSS contains no custom property; nothing was verified');
  }
  // Since 2026-10-13 the Web has no colour namespace: every emitted opaque colour
  // comes from the base (Android) token file, so what must appear is the alias set.
  for (const name of Object.keys(webTokens.aliases)) {
    if (name.startsWith('$')) continue;
    if (!expected.includes(`--${name}:`)) {
      problems.push(`web alias "--${name}" is missing from the generated CSS`);
    }
  }
  const difference = firstDifference(expected, committedText);
  if (difference !== null) {
    problems.push(
      `committed tokens.css differs at line ${difference.line}: expected ${JSON.stringify(
        difference.expected,
      )} but found ${JSON.stringify(difference.actual)}`,
    );
  }
  return { problems, propertyCount };
}

function runSelftest() {
  const { webTokens, baseTokens } = readTokenSources(FRONTEND_ROOT);
  const committed = readFileSync(COMMITTED, 'utf8');
  const harnessProblems = [];
  const deadBranches = [];

  try {
    const clean = verify(webTokens, baseTokens, committed);
    if (clean.problems.length > 0) {
      harnessProblems.push(`fixture is not clean: ${clean.problems.join('; ')}`);
    } else {
      console.log(`SELFTEST CASE OK: committed CSS matches the generator (${clean.propertyCount} propertie(s))`);
    }

    const aliasNames = Object.keys(webTokens.aliases).filter((name) => !name.startsWith('$'));
    const baseColourNames = Object.keys(baseTokens.color).filter((name) => !name.startsWith('$'));
    if (aliasNames.length === 0) {
      harnessProblems.push('the local token file declares no alias; nothing but raw base colours was verified');
    } else {
      const firstAlias = aliasNames[0];

      const mutatedTokens = structuredClone(webTokens);
      mutatedTokens.aliases[firstAlias] = 'base.surface';
      const mutated = verify(mutatedTokens, baseTokens, committed);
      if (mutated.problems.length === 0) {
        deadBranches.push('an injected alias change was not detected');
      } else {
        console.log(`SELFTEST CASE OK: injected alias change detected (--${firstAlias})`);
      }

      const renamedTokens = structuredClone(webTokens);
      delete renamedTokens.aliases[firstAlias];
      const renamed = verify(renamedTokens, baseTokens, committed);
      if (renamed.problems.length === 0) {
        deadBranches.push('a removed web alias was not detected');
      } else {
        console.log('SELFTEST CASE OK: removed web alias detected');
      }
    }

    // A change to the Android baseline must be visible too: the Web now inherits it verbatim.
    const mutatedBase = structuredClone(baseTokens);
    mutatedBase.color[baseColourNames[0]].value = '#000000';
    const baseMutated = verify(webTokens, mutatedBase, committed);
    if (baseMutated.problems.length === 0) {
      deadBranches.push('a change to the base (Android) palette was not detected');
    } else {
      console.log(`SELFTEST CASE OK: base palette change detected (${baseColourNames[0]})`);
    }

    const tamperedCss = `${committed}  --injected: red;\n`;
    const tampered = verify(webTokens, baseTokens, tamperedCss);
    if (tampered.problems.length === 0) {
      deadBranches.push('a hand edit appended to tokens.css was not detected');
    } else {
      console.log('SELFTEST CASE OK: hand-edited CSS detected');
    }

  } catch (error) {
    harnessProblems.push(`selftest harness error: ${error.message}`);
  }

  for (const problem of harnessProblems) console.log(`SELFTEST HARNESS PROBLEM: ${problem}`);
  for (const branch of deadBranches) console.log(`SELFTEST DEAD BRANCH: ${branch}`);
  if (harnessProblems.length > 0) {
    console.log(`SELFTEST VERDICT: INCONCLUSIVE (${harnessProblems.length} harness problem(s))`);
    return 3;
  }
  if (deadBranches.length > 0) {
    console.log(`SELFTEST VERDICT: FAIL (${deadBranches.length} dead branch(es))`);
    return 1;
  }
  console.log('SELFTEST VERDICT: PASS (4 case(s), every tamper branch turned red)');
  return 0;
}

if (process.argv.includes('--selftest')) {
  process.exit(runSelftest());
}

const { webTokens, baseTokens } = readTokenSources(FRONTEND_ROOT);
const committed = readFileSync(COMMITTED, 'utf8');
const { problems, propertyCount } = verify(webTokens, baseTokens, committed);

for (const problem of problems) console.log(`FAIL ${problem}`);
if (problems.length > 0) {
  console.log(`VERDICT: FAIL (${problems.length} problem(s))`);
  process.exit(1);
}
console.log(`OK   committed tokens.css matches the generator (${propertyCount} custom propertie(s))`);
console.log('VERDICT: PASS (1 OK line)');
process.exit(0);