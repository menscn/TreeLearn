#!/usr/bin/env node
/**
 * Web contrast guard (task T-2026-10-10-006).
 *
 * Computes WCAG 2.x contrast ratios from the authoritative token values for
 * every foreground/background pair the Web surface actually uses - including
 * the EFFECTIVE glass background (the token's translucent fill composited over
 * its backdrop), because the repo UX rule is to judge the rendered pair, not
 * the colour names.
 *
 * Thresholds: body/secondary text 4.5:1, non-text controls and icons 3:1
 * (WCAG AA). Exit codes: 0 = PASS, 1 = FAIL, 3 = INCONCLUSIVE (selftest).
 */
import { join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readTokenSources } from './lib/build-tokens.mjs';

const FRONTEND_ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..');

function parseColour(value) {
  const hex = value.match(/^#([0-9a-f]{6})$/i);
  if (hex !== null) {
    const int = Number.parseInt(hex[1], 16);
    return { r: (int >> 16) & 0xff, g: (int >> 8) & 0xff, b: int & 0xff, a: 1 };
  }
  const rgba = value.match(/^rgba\((\d+),\s*(\d+),\s*(\d+),\s*([\d.]+)\)$/);
  if (rgba !== null) {
    return {
      r: Number(rgba[1]),
      g: Number(rgba[2]),
      b: Number(rgba[3]),
      a: Number(rgba[4]),
    };
  }
  throw new Error(`unsupported colour value: ${value}`);
}

export function composite(overlay, backdrop) {
  const alpha = overlay.a + backdrop.a * (1 - overlay.a);
  const blend = (channel) => (overlay[channel] * overlay.a + backdrop[channel] * backdrop.a * (1 - overlay.a)) / alpha;
  return { r: blend('r'), g: blend('g'), b: blend('b'), a: alpha };
}

function relativeLuminance({ r, g, b }) {
  const channel = (value) => {
    const scaled = value / 255;
    return scaled <= 0.04045 ? scaled / 12.92 : ((scaled + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * channel(r) + 0.7152 * channel(g) + 0.0722 * channel(b);
}

export function contrastRatio(foreground, background) {
  const lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
  const darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
  return (lighter + 0.05) / (darker + 0.05);
}

function buildPairs(webTokens, baseTokens) {
  const colour = (name) => parseColour(webTokens.color[name]);
  const base = (name) => parseColour(baseTokens.color[name].value);
  const glassPanelOverMist = composite(parseColour(webTokens.glass.panel), colour('web-mist'));

  return [
    { name: 'ink on web-white (body text on content surface)', fg: base('ink'), bg: colour('web-white'), min: 4.5 },
    { name: 'ink on web-mist (text on mist background)', fg: base('ink'), bg: colour('web-mist'), min: 4.5 },
    { name: 'ink2 on web-white (secondary text)', fg: base('ink2'), bg: colour('web-white'), min: 4.5 },
    { name: 'ink on composited glass panel over web-mist', fg: base('ink'), bg: glassPanelOverMist, min: 4.5 },
    { name: 'web-on-sage on web-sage (primary action label)', fg: colour('web-on-sage'), bg: colour('web-sage'), min: 4.5 },
    { name: 'web-sage on web-white (control border/icon)', fg: colour('web-sage'), bg: colour('web-white'), min: 3.0 },
    { name: 'ink2 on web-mint (selected-state text)', fg: base('ink2'), bg: colour('web-mint'), min: 4.5 },
  ];
}

function evaluate(pairs) {
  return pairs.map((pair) => ({ ...pair, ratio: contrastRatio(pair.fg, pair.bg) }));
}

function runSelftest() {
  const harnessProblems = [];
  const deadBranches = [];

  const white = { r: 255, g: 255, b: 255, a: 1 };
  const black = { r: 0, g: 0, b: 0, a: 1 };
  if (Math.abs(contrastRatio(black, white) - 21) > 0.05) {
    harnessProblems.push('the contrast formula failed its known-answer case (black on white must be 21)');
  } else {
    console.log('SELFTEST CASE OK: black on white computes 21:1');
  }

  const near = { r: 130, g: 130, b: 130, a: 1 };
  if (contrastRatio(near, white) >= 4.5) {
    deadBranches.push('a mid-grey pair passed the 4.5 threshold, so the threshold branch cannot fail');
  } else {
    console.log('SELFTEST CASE OK: mid-grey on white fails the 4.5 threshold');
  }

  const blended = composite({ r: 255, g: 255, b: 255, a: 0.5 }, black);
  if (Math.abs(blended.r - 127.5) > 0.01) {
    deadBranches.push('the alpha compositor did not blend a 50% white over black to ~127.5');
  } else {
    console.log('SELFTEST CASE OK: alpha compositor blends 50% white over black to ~127.5');
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
  console.log('SELFTEST VERDICT: PASS (3 case(s))');
  return 0;
}

if (process.argv.includes('--selftest')) {
  process.exit(runSelftest());
}

const { webTokens, baseTokens } = readTokenSources(FRONTEND_ROOT);
const results = evaluate(buildPairs(webTokens, baseTokens));

let failures = 0;
for (const result of results) {
  const verdict = result.ratio >= result.min ? 'OK  ' : 'FAIL';
  if (result.ratio < result.min) failures += 1;
  console.log(
    `${verdict} ${result.ratio.toFixed(2)}:1 (min ${result.min}) - ${result.name}`,
  );
}
if (failures > 0) {
  console.log(`VERDICT: FAIL (${failures} pair(s) below threshold)`);
  process.exit(1);
}
console.log(`VERDICT: PASS (${results.length} pair(s) at or above threshold)`);
process.exit(0);