import { renderHook } from '@testing-library/react';
import { readFileSync } from 'node:fs';
import { join } from 'node:path';
import { describe, expect, it } from 'vitest';

import type { OperationFrameSource, SseFrame } from '../api/sse';
import { useOperationStream } from './useOperationStream';

// The six SSE fixtures are the single shared frame sequences (web/contracts/api.md
// §7). Instead of mocking the state machine, the hook is driven by an injected
// frame source that replays a fixture verbatim; the real OperationEventState inside
// the hook then produces the UI stream state. vitest runs with cwd = web/frontend.
const SSE_DIR = join(process.cwd(), '..', 'contracts', 'fixtures', 'sse');

interface SseFixture { operationId: string; lastEventId: string | null; frames: SseFrame[] }

function loadSse(name: string): SseFixture {
  return JSON.parse(readFileSync(join(SSE_DIR, `${name}.json`), 'utf8')) as SseFixture;
}

function replaySource(frames: SseFrame[]): OperationFrameSource {
  return {
    subscribe: (sink) => {
      for (const frame of frames) sink.onFrame(frame);
      return () => {};
    },
  };
}

function stream(name: string) {
  const fixture = loadSse(name);
  return renderHook(() => useOperationStream({ operationId: fixture.operationId, url: null, lastEventId: fixture.lastEventId, source: replaySource(fixture.frames) }));
}

describe('useOperationStream over the contract SSE fixtures', () => {
  it('stream-stable: appends reasoning/answer deltas and settles STABLE', () => {
    const { result } = stream('stream-stable');
    expect(result.current.state).toBe('STREAMING_ANSWER');
    expect(result.current.reasoning).toBe('先明确光合作用的场所与原料。再说明光能如何被转化为化学能。');
    expect(result.current.answer).toBe('光合作用是植物利用光能把二氧化碳和水合成有机物并释放氧气的过程。');
    expect(result.current.terminal?.kind).toBe('stable');
    expect(result.current.terminal?.revision?.revisionNo).toBe(1);
    expect(result.current.ignoredCount).toBe(0);
  });

  it('stream-failed: keeps the partial answer and surfaces the mapped error code', () => {
    const { result } = stream('stream-failed');
    expect(result.current.answer).toBe('光合作用是植物利用光能');
    expect(result.current.terminal?.kind).toBe('failed');
    expect(result.current.terminal?.errorCode).toBe('RATE_LIMITED');
    expect(result.current.terminal?.params).toEqual({ httpStatus: 429 });
  });

  it('stream-interrupted: preserves partial content and settles INTERRUPTED', () => {
    const { result } = stream('stream-interrupted');
    expect(result.current.reasoning).toBe('先明确光合作用的场所。再说明能量转换。');
    expect(result.current.terminal?.kind).toBe('interrupted');
  });

  it('stream-canceled: keeps the partial answer and settles CANCELED', () => {
    const { result } = stream('stream-canceled');
    expect(result.current.answer).toBe('光合作用是植物利用光能把二氧化碳和水合成有机物');
    expect(result.current.terminal?.kind).toBe('canceled');
  });

  it('reconnect-gap-snapshot: reseeds from Last-Event-ID and detects the gap', () => {
    const { result } = stream('reconnect-gap-snapshot');
    expect(result.current.gaps).toBeGreaterThanOrEqual(1);
    expect(result.current.answer).toBe('光合作用是植物利用光能把二氧化碳和水合成有机物并释放氧气的过程。');
    expect(result.current.terminal?.kind).toBe('stable');
  });

  it('late-duplicate-ignored: ignores duplicate, foreign and out-of-order frames', () => {
    const { result } = stream('late-duplicate-ignored');
    expect(result.current.ignoredCount).toBe(3);
    expect(result.current.answer).toBe('光合作用是植物利用光能');
    expect(result.current.terminal?.kind).toBe('stable');
    expect(result.current.terminal?.revision?.revisionNo).toBe(3);
  });

  it('does not subscribe without an operation id', () => {
    const { result } = renderHook(() => useOperationStream({ operationId: null, url: null, source: replaySource([]) }));
    expect(result.current.answer).toBe('');
    expect(result.current.terminal).toBeNull();
    expect(result.current.state).toBeNull();
  });
});