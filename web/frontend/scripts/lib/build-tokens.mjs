/**
 * Shared design-token builder (task T-2026-10-10-006).
 *
 * Single implementation used by BOTH the generator (writes src/styles/tokens.css)
 * and the guard (re-builds in memory and compares), so the committed CSS can
 * never drift from the two authoritative JSON files:
 *   - Docs/04-ux-design/web-local-design-tokens.json  (Web colour namespace + glass)
 *   - Docs/04-ux-design/design-system/tokens.json     (base scales + base colours)
 *
 * The alias resolution mirrors the prototype's WEB_MINT_TOKENS block: web-*
 * aliases become var() references, base.* aliases resolve to the base token.
 */
import { readFileSync } from 'node:fs';
import { join } from 'node:path';

export function readTokenSources(frontendRoot) {
  const repoRoot = join(frontendRoot, '..', '..');
  const webTokens = JSON.parse(
    readFileSync(join(repoRoot, 'Docs', '04-ux-design', 'web-local-design-tokens.json'), 'utf8'),
  );
  const baseTokens = JSON.parse(
    readFileSync(join(repoRoot, 'Docs', '04-ux-design', 'design-system', 'tokens.json'), 'utf8'),
  );
  return { webTokens, baseTokens };
}

// Base colours that survive into the Web namespace (the rest are superseded by
// the web aliases and are intentionally not emitted).
const BASE_COLOUR_NAMES = [
  'ink',
  'ink2',
  'hairline',
  'red',
  'red-soft',
  'amber',
  'amber-soft',
  'blue',
  'yellow-highlight',
  'green-deep',
];

const SCALE_GROUPS = [
  ['space', 'space'],
  ['radius', 'radius'],
  ['fontSize', 'fontSize'],
  ['shadow', 'shadow'],
];

export function buildTokenCss(webTokens, baseTokens) {
  const lines = [];
  lines.push('/*');
  lines.push(' * Generated file - do not edit by hand.');
  lines.push(' * Source: Docs/04-ux-design/web-local-design-tokens.json (Web namespace, glass)');
  lines.push(' *         Docs/04-ux-design/design-system/tokens.json (base scales + base colours)');
  lines.push(' * Regenerate: pnpm generate:tokens    Guard: pnpm check:design-tokens');
  lines.push(' */');
  lines.push(':root {');

  const emit = (name, value, note) => {
    const comment = note ? ` /* ${String(note).replaceAll('*/', '* /')} */` : '';
    lines.push(`  --${name}: ${value};${comment}`);
  };

  lines.push('  /* Web colour namespace (user-selected mint palette) */');
  for (const [name, value] of Object.entries(webTokens.color)) {
    emit(name, value);
  }

  lines.push('');
  lines.push('  /* Semantic aliases (resolution mirrors the prototype WEB_MINT_TOKENS block) */');
  for (const [alias, target] of Object.entries(webTokens.aliases)) {
    if (target.startsWith('web-')) {
      emit(alias, `var(--${target})`);
      continue;
    }
    const baseName = target.replace(/^base\./, '');
    if (baseName === alias) {
      continue; // the base colour is already emitted under this exact name
    }
    if (BASE_COLOUR_NAMES.includes(baseName)) {
      emit(alias, `var(--${baseName})`);
    } else {
      emit(alias, baseTokens.color[baseName].value);
    }
  }

  lines.push('');
  lines.push('  /* Base colours kept for state and reading surfaces */');
  for (const name of BASE_COLOUR_NAMES) {
    const token = baseTokens.color[name];
    if (token === undefined) {
      throw new Error(`base token "${name}" missing from design-system/tokens.json`);
    }
    emit(name, token.value, token.note);
  }

  for (const [jsonGroup, cssName] of SCALE_GROUPS) {
    lines.push('');
    lines.push(`  /* ${jsonGroup} */`);
    for (const [name, token] of Object.entries(baseTokens[jsonGroup])) {
      if (name.startsWith('$')) continue;
      emit(name, token.value, token.note);
    }
  }

  lines.push('');
  lines.push('  /* typography */');
  emit('font-serif', baseTokens.fontFamily.serif.value);
  emit('font-sans', baseTokens.fontFamily.sans.value);
  emit('lh-read', baseTokens.other['lh-read'].value, baseTokens.other['lh-read'].note);

  lines.push('');
  lines.push('  /* page background depth layers (Web namespace) */');
  for (const [name, value] of Object.entries(webTokens.background ?? {})) {
    if (name.startsWith('$')) continue;
    emit(`bg-${name}`, value);
  }

  lines.push('');
  lines.push('  /* glass surfaces (Web namespace) */');
  for (const [name, value] of Object.entries(webTokens.glass)) {
    if (name.startsWith('$')) continue;
    emit(`glass-${name}`, value);
  }

  lines.push('}');
  return `${lines.join('\n')}\n`;
}