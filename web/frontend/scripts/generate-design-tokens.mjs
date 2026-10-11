#!/usr/bin/env node
/**
 * Writes web/frontend/src/styles/tokens.css from the two authoritative token
 * JSON files (task T-2026-10-10-006). The output is committed, and
 * scripts/check-design-tokens.mjs re-builds it in memory to detect drift.
 */
import { mkdirSync, writeFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { buildTokenCss, readTokenSources } from './lib/build-tokens.mjs';

const FRONTEND_ROOT = join(fileURLToPath(new URL('.', import.meta.url)), '..');
const OUTPUT = join(FRONTEND_ROOT, 'src', 'styles', 'tokens.css');

const { webTokens, baseTokens } = readTokenSources(FRONTEND_ROOT);
const css = buildTokenCss(webTokens, baseTokens);

mkdirSync(dirname(OUTPUT), { recursive: true });
writeFileSync(OUTPUT, css, 'utf8');
console.log(`OK   wrote ${OUTPUT} (${css.split('\n').length - 1} line(s))`);