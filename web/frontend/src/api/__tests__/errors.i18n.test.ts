import { describe, expect, it } from 'vitest';

import { LOCALES, keysOf, translate } from '../../i18n';
import {
  ERROR_CODES,
  ERROR_SPEC,
  FALLBACK_ERROR_KEY,
  NETWORK_UNREACHABLE_KEY,
  errorI18nKey,
} from '../errors';

// The 42 contract error codes (web/contracts/api.md §3.3) must each resolve to a
// user-facing string in both locales, keyed as errors.<code>; the browser never
// sees a backend sentence or an internal code.
describe('error code i18n completeness', () => {
  it('exposes exactly 42 unique contract codes', () => {
    expect(ERROR_CODES).toHaveLength(42);
    expect(new Set(ERROR_CODES).size).toBe(42);
  });

  it('resolves every code to a zh-CN and en-US string', () => {
    for (const locale of LOCALES) {
      const keys = new Set(keysOf(locale));
      for (const code of ERROR_CODES) {
        const key = errorI18nKey(code);
        expect(keys.has(key), `${locale} is missing ${key}`).toBe(true);
        expect(translate(locale, key).length, `${locale} ${key} is empty`).toBeGreaterThan(0);
      }
    }
  });

  it('provides fallback and network keys in both locales', () => {
    for (const locale of LOCALES) {
      const keys = new Set(keysOf(locale));
      expect(keys.has(FALLBACK_ERROR_KEY), `${locale} is missing ${FALLBACK_ERROR_KEY}`).toBe(true);
      expect(keys.has(NETWORK_UNREACHABLE_KEY), `${locale} is missing ${NETWORK_UNREACHABLE_KEY}`).toBe(true);
    }
  });

  it('has no orphan error keys', () => {
    const known = new Set<string>([...ERROR_CODES.map((code) => errorI18nKey(code)), FALLBACK_ERROR_KEY, NETWORK_UNREACHABLE_KEY]);
    for (const key of keysOf('zh-CN')) {
      if (key.startsWith('errors.')) {
        expect(known.has(key), `orphan error key ${key}`).toBe(true);
      }
    }
  });

  it('records a retryable flag and a valid HTTP status for every code', () => {
    const allowedStatuses = new Set([400, 404, 409, 500, 503]);
    for (const code of ERROR_CODES) {
      const spec = ERROR_SPEC[code];
      expect(typeof spec.retryable, `${code} retryable`).toBe('boolean');
      expect(allowedStatuses.has(spec.httpStatus), `${code} http status ${spec.httpStatus}`).toBe(true);
      expect(Array.isArray(spec.params), `${code} params`).toBe(true);
    }
  });
});