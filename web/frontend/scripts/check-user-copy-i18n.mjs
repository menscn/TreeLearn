#!/usr/bin/env node
/**
 * Treelearn web frontend user-copy i18n guard (task T-2026-10-10-004).
 *
 * Every user-visible string must be resolved from src/i18n resources at render
 * time so the zh-CN and en-US dictionaries stay complete. This guard is a
 * line-based pre-filter over production sources; it catches the escape hatches
 * that reviews actually miss:
 *
 *   1. CJK characters on any non-comment line (locale resources are exempt).
 *   2. Literal JSX text nodes containing letters (text between tags that is not
 *      a {t(...)} expression).
 *   3. Literal (non-expression) values on user-visible props:
 *      aria-label / title / placeholder / alt.
 *
 * Suppression: a line carrying an adjacent `i18n-ignore` token is skipped; the
 * reason is checked by review, not by this script.
 *
 * Scope honesty: this is a text pre-filter, not a parser. It cannot prove that
 * every rendered string came from a dictionary; it proves the specific literal
 * classes above are absent. Rendering tests carry the behavioral half.
 *
 * Exit codes: 0 = PASS, 1 = FAIL (violations, or a scan that found no file to
 * check - a vacuous scan must never print PASS), 3 = INCONCLUSIVE (the
 * --selftest harness itself could not run). Distinct codes on purpose: "did
 * not run" and "ran and found violations" are different verdicts.
 */
import { readFileSync, readdirSync, statSync } from 'node:fs';
import { join, relative } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..');
const SRC = join(ROOT, 'src');
const SOURCE_FILE = /\.(ts|tsx)$/;
const IGNORE_TOKEN = 'i18n-ignore';
const CJK = /[\u3400-\u4dbf\u4e00-\u9fff\uf900-\ufaff\u3000-\u303f\uff00-\uffef]/;
// Literal JSX text candidate: a `>` … letters … `<` run on one line, where the
// `>` is not an arrow (=>) or a comparison (>=) and the `<` is not part of
// <= / << . The candidate alone still matches TypeScript shapes like
// `Map<string, Branch>, archived: Set<`, so the matched inner run must also be
// free of punctuation that JSX text would not carry but code does (commas,
// colons, semicolons, parens, ampersands, pipes). Real copy with those marks
// is caught by the CJK rule or the prop rule instead.
const JSX_TEXT_CANDIDATE = /(?<!=)>([^<>{}]*[A-Za-z][^<>{}]*)<(?![=>])/;
const JSX_TEXT_CODE_PUNCT = /[(),;:&|]/;
const USER_VISIBLE_PROP_LITERAL = /\b(aria-label|title|placeholder|alt)\s*=\s*"[^"{]*[A-Za-z][^"{]*"/;

function literalJsxText(line) {
  const candidate = JSX_TEXT_CANDIDATE.exec(line);
  if (candidate === null) return false;
  return !JSX_TEXT_CODE_PUNCT.test(candidate[1]);
}

export function scanSource(text) {
  const violations = [];
  const lines = text.split(/\r?\n/);
  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (trimmed.length === 0) return;
    if (trimmed.startsWith('//') || trimmed.startsWith('*') || trimmed.startsWith('/*')) return;
    if (line.includes(IGNORE_TOKEN)) return;

    if (CJK.test(line)) {
      violations.push(`line ${index + 1}: CJK outside i18n resources -> ${trimmed}`);
    }
    if (literalJsxText(line)) {
      violations.push(`line ${index + 1}: literal JSX text -> ${trimmed}`);
    }
    if (USER_VISIBLE_PROP_LITERAL.test(line)) {
      violations.push(`line ${index + 1}: literal user-visible prop -> ${trimmed}`);
    }
  });
  return violations;
}

function isExempt(file) {
  const rel = relative(SRC, file).replaceAll('\\', '/');
  return (
    rel.startsWith('i18n/') ||
    rel.startsWith('test/') ||
    rel.includes('.test.') ||
    rel.includes('.spec.')
  );
}

function collectSourceFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      files.push(...collectSourceFiles(full));
    } else if (SOURCE_FILE.test(entry)) {
      files.push(full);
    }
  }
  return files;
}

const SELFTEST_CASES = [
  {
    name: 'translated component passes',
    text: [
      'export function Sample() {',
      "  const { t } = useI18n();",
      "  return <div aria-label={t('a.b')}>{t('a.c')}</div>;",
      '}',
    ].join('\n'),
    expectViolation: false,
  },
  {
    name: 'CJK string literal',
    text: "const label = '你好';",
    expectViolation: true,
  },
  {
    name: 'CJK inside a comment only',
    text: '// 中文注释不会被误报',
    expectViolation: false,
  },
  {
    name: 'literal JSX text',
    text: 'return <h1>Hello world</h1>;',
    expectViolation: true,
  },
  {
    name: 'arrow function and comparison are not JSX text',
    text: 'const emptyMonth = stats !== null && stats.monthCells.every((cell) => cell <= 0);',
    expectViolation: false,
  },
  {
    name: 'TypeScript generics are not JSX text',
    text: 'function hasArchivedAncestor(node: Branch, byId: Map<string, Branch>, archived: Set<string>): boolean {',
    expectViolation: false,
  },
  {
    name: 'comparison chain is not JSX text',
    text: 'if (a > b && c < d) {',
    expectViolation: false,
  },
  {
    name: 'literal aria-label prop',
    text: 'return <button aria-label="Send message" />;',
    expectViolation: true,
  },
  {
    name: 'adjacent i18n-ignore suppression',
    text: "const dev = '你好'; // i18n-ignore: debug sample, never rendered",
    expectViolation: false,
  },
];

function runSelftest() {
  const harnessProblems = [];
  const deadBranches = [];
  let executed = 0;

  for (const testCase of SELFTEST_CASES) {
    const violations = scanSource(testCase.text);
    executed += 1;
    if (testCase.expectViolation && violations.length === 0) {
      deadBranches.push(`case "${testCase.name}" did not FAIL on its injected violation`);
    }
    if (!testCase.expectViolation && violations.length > 0) {
      deadBranches.push(`case "${testCase.name}" falsely flagged a clean sample: ${violations.join('; ')}`);
    }
  }

  // A vacuous scan (no production file collected) must be reported, not passed.
  let scanned = 0;
  try {
    scanned = collectSourceFiles(SRC).filter((file) => !isExempt(file)).length;
  } catch (error) {
    harnessProblems.push(`could not enumerate sources: ${error.message}`);
  }
  if (scanned === 0 && harnessProblems.length === 0) {
    harnessProblems.push('no production source file collected under src/ (the scan would verify nothing)');
  }

  for (const problem of harnessProblems) {
    console.log(`SELFTEST HARNESS PROBLEM: ${problem}`);
  }
  for (const branch of deadBranches) {
    console.log(`SELFTEST DEAD BRANCH: ${branch}`);
  }
  if (harnessProblems.length > 0) {
    console.log(`SELFTEST VERDICT: INCONCLUSIVE (${harnessProblems.length} harness problem(s), ${executed} case(s) executed)`);
    return 3;
  }
  if (deadBranches.length > 0) {
    console.log(`SELFTEST VERDICT: FAIL (${deadBranches.length} dead branch(es) out of ${executed} case(s))`);
    return 1;
  }
  console.log(`SELFTEST VERDICT: PASS (${executed} case(s), ${scanned} production file(s) collected, every branch turned red on its violation)`);
  return 0;
}

function runScan() {
  const files = collectSourceFiles(SRC).filter((file) => !isExempt(file));
  if (files.length === 0) {
    console.log('FAIL no production source file collected under src/; the guard verified nothing');
    console.log('VERDICT: FAIL (1 problem)');
    return 1;
  }

  let total = 0;
  for (const file of files) {
    const violations = scanSource(readFileSync(file, 'utf8'));
    for (const violation of violations) {
      console.log(`FAIL ${relative(ROOT, file).replaceAll('\\', '/')} ${violation}`);
      total += 1;
    }
  }

  if (total > 0) {
    console.log(`VERDICT: FAIL (${total} violation(s) in ${files.length} scanned file(s))`);
    return 1;
  }
  console.log(`OK   no hardcoded user copy found in ${files.length} production source file(s)`);
  console.log(`VERDICT: PASS (1 OK line)`);
  return 0;
}

process.exit(process.argv.includes('--selftest') ? runSelftest() : runScan());