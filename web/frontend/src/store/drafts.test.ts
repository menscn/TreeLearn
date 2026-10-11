import { beforeEach, describe, expect, it } from 'vitest';

import { clearDraft, DRAFT_TTL_MILLIS, draftKeyFor, HOME_DRAFT_KEY, readDraft, writeDraft } from './drafts';

// Drafts are a recoverable local cache, not authoritative data: keyed per
// branch/home, discarded once expired (retention) or explicitly cleared.
describe('draft storage', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('keys drafts per branch and for home', () => {
    expect(draftKeyFor(null)).toBe(HOME_DRAFT_KEY);
    expect(draftKeyFor('b1')).toBe('draft:b1');
  });

  it('round-trips a draft and drops empty text instead of storing it', () => {
    writeDraft('draft:b1', 'hello', 1000);
    expect(readDraft('draft:b1', 1000)).toBe('hello');

    writeDraft('draft:b1', '', 1000);
    expect(readDraft('draft:b1', 1000)).toBe('');
    expect(localStorage.getItem('draft:b1')).toBeNull();
  });

  it('discards a draft older than the retention window', () => {
    writeDraft('draft:b1', 'stale', 1000);
    expect(readDraft('draft:b1', 1000 + DRAFT_TTL_MILLIS + 1)).toBe('');
    expect(localStorage.getItem('draft:b1')).toBeNull();
  });

  it('removes a draft on explicit clear', () => {
    writeDraft('draft:b1', 'x', 1000);
    clearDraft('draft:b1');
    expect(localStorage.getItem('draft:b1')).toBeNull();
  });
});