import { beforeEach, describe, expect, it } from 'vitest';

import { LAYOUT_STORAGE_KEY, readLayout, writeLayout } from './layoutPreference';

// Layout choice is a local view preference (A focus / B side-by-side / C tree
// placeholder). It is a recoverable cache, not authoritative data: bad or
// missing values fall back to A and storage failures degrade silently.
describe('workspace layout preference', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('defaults to the focus layout when nothing is stored', () => {
    expect(readLayout()).toBe('A');
  });

  it('round-trips a stored layout', () => {
    writeLayout('B');
    expect(localStorage.getItem(LAYOUT_STORAGE_KEY)).toBe('B');
    expect(readLayout()).toBe('B');
  });

  it('falls back to the focus layout for an unknown stored value', () => {
    localStorage.setItem(LAYOUT_STORAGE_KEY, 'Z');
    expect(readLayout()).toBe('A');
  });
});