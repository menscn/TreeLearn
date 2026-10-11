import { afterEach, describe, expect, it, vi } from 'vitest';

import { currentPath, matchRoute, navigate, subscribeLocation } from './router';

// Minimal internal router (no routing dependency). These tests lock the four
// supported paths, the 404 fallback, and the History API plumbing.
describe('matchRoute', () => {
  it('maps the supported paths and falls back to notFound', () => {
    expect(matchRoute('/')).toEqual({ name: 'home' });
    expect(matchRoute('')).toEqual({ name: 'home' });
    expect(matchRoute('/library')).toEqual({ name: 'library' });
    expect(matchRoute('/stats')).toEqual({ name: 'stats' });
    expect(matchRoute('/topic/abc-1')).toEqual({ name: 'topic', id: 'abc-1', branchId: null });
    expect(matchRoute('/topic/abc-1/')).toEqual({ name: 'topic', id: 'abc-1', branchId: null });
    expect(matchRoute('/settings')).toEqual({ name: 'settings', section: null });
    expect(matchRoute('/settings/models')).toEqual({ name: 'settings', section: 'models' });
    expect(matchRoute('/deep/unknown')).toEqual({ name: 'notFound', path: '/deep/unknown' });
  });

  it('decodes the topic id segment', () => {
    expect(matchRoute('/topic/a%20b')).toEqual({ name: 'topic', id: 'a b', branchId: null });
  });

  it('reads the optional branch query parameter for the topic route', () => {
    expect(matchRoute('/topic/abc-1', '?branch=b-2')).toEqual({ name: 'topic', id: 'abc-1', branchId: 'b-2' });
    expect(matchRoute('/topic/abc-1', '?other=x')).toEqual({ name: 'topic', id: 'abc-1', branchId: null });
    expect(matchRoute('/library', '?branch=b-2')).toEqual({ name: 'library' });
  });
});

describe('history navigation', () => {
  afterEach(() => {
    window.history.replaceState({}, '', '/');
  });

  it('pushes the path and notifies subscribers', () => {
    const listener = vi.fn();
    const unsubscribe = subscribeLocation(listener);
    navigate('/library');
    expect(currentPath()).toBe('/library');
    expect(listener).toHaveBeenCalledTimes(1);
    unsubscribe();
    navigate('/');
    expect(listener).toHaveBeenCalledTimes(1);
  });

  it('ignores a navigation to the current path', () => {
    navigate('/library');
    const listener = vi.fn();
    const unsubscribe = subscribeLocation(listener);
    navigate('/library');
    expect(listener).not.toHaveBeenCalled();
    unsubscribe();
  });

  it('reacts to a browser back/forward popstate', () => {
    const listener = vi.fn();
    const unsubscribe = subscribeLocation(listener);
    window.dispatchEvent(new PopStateEvent('popstate'));
    expect(listener).toHaveBeenCalledTimes(1);
    unsubscribe();
  });
});