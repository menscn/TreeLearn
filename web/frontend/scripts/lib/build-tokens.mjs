/**
 * Shared design-token builder (task T-2026-10-10-006).
 *
 * Single implementation used by BOTH the generator (writes src/styles/tokens.css)
 * and the guard (re-builds in memory and compares), so the committed CSS can
 * never drift from the authoritative JSON files:
 *   - Docs/04-ux-design/web-local-design-tokens.json  (Web mapping: colour-free)
 *   - Docs/04-ux-design/design-system/tokens.json     (base colours + scales)
 *
 * Since 2026-10-13 (parent T-2026-10-13-123) the Web client has NO colour
 * namespace of its own: every opaque colour is emitted straight from the base
 * token file, and the local JSON may only reference it (aliases) or derive
 * transparency from it (glass rgba of a base token's own channels).
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
  lines.push(' * Source: Docs/04-ux-design/design-system/tokens.json (base colours + scales)');
  lines.push(' *         Docs/04-ux-design/web-local-design-tokens.json (references and derived transparency)');
  lines.push(' * Regenerate: pnpm generate:tokens    Guard: pnpm check:design-tokens');
  lines.push(' */');
  lines.push(':root {');

  const emit = (name, value, note) => {
    const comment = note ? ` /* ${String(note).replaceAll('*/', '* /')} */` : '';
    lines.push(`  --${name}: ${value};${comment}`);
  };

  lines.push('  /* Base colours - the single source of every opaque colour (Android design baseline) */');
  for (const [name, token] of Object.entries(baseTokens.color)) {
    if (name.startsWith('$')) continue;
    emit(name, token.value, token.note);
  }

  lines.push('');
  lines.push('  /* Web aliases: references into the base palette, never new values */');
  for (const [alias, target] of Object.entries(webTokens.aliases)) {
    if (alias.startsWith('$')) continue;
    const reference = target.startsWith('ref:')
      ? target.slice('ref:'.length)
      : target.replace(/^base\./, '');
    if (reference === alias) {
      continue; // the base colour is already emitted under this exact name
    }
    if (baseTokens.color[reference] === undefined) {
      throw new Error(`alias "--${alias}" points at unknown base colour "${reference}"`);
    }
    emit(alias, `var(--${reference})`);
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
  lines.push('  /* overlay surfaces (derived from Android surface/ink; no backdrop blur) */');
  for (const [name, rawValue] of Object.entries(webTokens.glass)) {
    if (name.startsWith('$')) continue;
    const value = rawValue.startsWith('ref:') ? `var(--${rawValue.slice('ref:'.length)})` : rawValue;
    emit(`glass-${name}`, value);
  }

  lines.push('}');
  return `${lines.join('\n')}\n`;
}