import { describe, expect, it } from 'vitest';
import { LOCALES, keysOf, translate } from './index';

describe('i18n resources', () => {
  it('every locale defines exactly the same key set', () => {
    const reference = keysOf('zh-CN');
    expect(reference.length).toBeGreaterThan(0);

    for (const locale of LOCALES) {
      expect(keysOf(locale)).toEqual(reference);
    }
  });

  it('translates a key per locale at call time', () => {
    expect(translate('zh-CN', 'app.name')).toBe('知芽');
    expect(translate('en-US', 'app.name')).toBe('TreeLearn');
  });
});