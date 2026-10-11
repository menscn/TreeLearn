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
  for (const name of Object.keys(webTokens.color)) {
    if (!expected.includes(`--${name}:`)) {
      problems.push(`web colour "--${name}" is missing from the generated CSS`);
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

    const mutatedTokens = structuredClone(webTokens);
    mutatedTokens.color['web-sage'] = '#000000';
    const mutated = verify(mutatedTokens, baseTokens, committed);
    if (mutated.problems.length === 0) {
      deadBranches.push('an injected colour change was not detected');
    } else {
      console.log('SELFTEST CASE OK: injected colour change detected');
    }

    const tamperedCss = `${committed}  --injected: red;\n`;
    const tampered = verify(webTokens, baseTokens, tamperedCss);
    if (tampered.problems.length === 0) {
      deadBranches.push('a hand edit appended to tokens.css was not detected');
    } else {
      console.log('SELFTEST CASE OK: hand-edited CSS detected');
    }

    const renamedTokens = structuredClone(webTokens);
    delete renamedTokens.color['web-white'];
    const renamed = verify(renamedTokens, baseTokens, committed);
    if (renamed.problems.length === 0) {
      deadBranches.push('a removed web colour was not detected');
    } else {
      console.log('SELFTEST CASE OK: removed web colour detected');
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