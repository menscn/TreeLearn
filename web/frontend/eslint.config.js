import js from '@eslint/js';
import tseslint from 'typescript-eslint';

// Dependency direction (water flows downward), enforced mechanically:
//   pages -> layouts -> components -> hooks -> store -> api -> platform -> utils
// A file may import its own layer and any layer BELOW it; importing a layer
// above it is a reverse dependency and fails lint.
const LAYERS = [
  'pages',
  'layouts',
  'components',
  'hooks',
  'store',
  'api',
  'platform',
  'utils',
];

function forbiddenPatternsFor(layer) {
  const higherLayers = LAYERS.slice(0, LAYERS.indexOf(layer));
  return higherLayers.map((dir) => ({
    group: [`**/${dir}/*`, `**/${dir}/**`],
    message: `dependency direction: "${layer}" must not import the higher layer "${dir}"`,
  }));
}

export default tseslint.config(
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**', 'scripts/**', 'vite.config.ts'],
  },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  ...LAYERS.map((layer) => ({
    files: [`src/${layer}/**/*.{ts,tsx}`],
    rules: {
      'no-restricted-imports': ['error', { patterns: forbiddenPatternsFor(layer) }],
      // A dynamic import() would bypass no-restricted-imports entirely
      // (verified by the T-2026-10-10-004 verifier), so layer files must use
      // static imports only.
      'no-restricted-syntax': [
        'error',
        {
          selector: 'ImportExpression',
          message: 'dynamic import() bypasses the dependency-direction guard; use a static import',
        },
      ],
    },
  })),
  {
    files: ['src/**/*.{ts,tsx}'],
    rules: {
      // The user-copy i18n guard (scripts/check-user-copy-i18n.mjs) catches
      // hardcoded copy; this rule keeps console noise out of production paths.
      'no-console': ['warn', { allow: ['error', 'warn'] }],
    },
  },
);