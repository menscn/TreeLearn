import { describe, expect, it } from 'vitest';

import type { Branch } from '../api/types';
import { archivedRoots, unreadRollup } from './libraryData';

// Task T-2026-10-10-044: derived read-only aggregates over a branch tree.
function branch(id: string, parentBranchId: string | null, unreadCount: number, archivedAtMillis: number | null = null): Branch {
  return { id, topicId: 't1', parentBranchId, source: null, title: null, createdAtMillis: 1, updatedAtMillis: 1, latestStableSequence: 0, lastSeenStableSequence: 0, unreadCount, collapsed: false, archivedAtMillis };
}

const root = branch('root', null, 0);
const child = branch('child', 'root', 2);
const archived = branch('archived', 'root', 5, 100);
const archivedChild = branch('archived-child', 'archived', 3, 100);

describe('unreadRollup', () => {
  it('sums visible branches and excludes archived subtrees', () => {
    expect(unreadRollup([root, child])).toBe(2);
    expect(unreadRollup([root, child, archived, archivedChild])).toBe(2);
  });
});

describe('archivedRoots', () => {
  it('lists only the top of an archived subtree', () => {
    expect(archivedRoots([root, child, archived, archivedChild]).map((node) => node.id)).toEqual(['archived']);
  });
});
