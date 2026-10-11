import { beforeEach, describe, expect, it, vi } from 'vitest';

// The transport layer is mocked so the state machine and the error-visibility wiring
// can be exercised without a real network (the library is still the project choice;
// only its I/O boundary is replaced).
const { fetchEventSource } = vi.hoisted(() => ({ fetchEventSource: vi.fn() }));
vi.mock('@microsoft/fetch-event-source', () => ({ fetchEventSource }));

import { OperationEventState, subscribeOperationEvents } from '../sse';

const OPERATION_ID = 'op-1';

function frame(id: string | undefined, event: string, data: unknown) {
  return { id, event, data };
}

type TransportInit = {
  onopen: (response: { ok: boolean; status: number }) => Promise<void> | void;
  onmessage: (message: { id: string; event: string; data: string }) => void;
  onclose: () => void;
  onerror: (error: unknown) => void;
};

function runTransport(invoke: (init: TransportInit) => void): void {
  fetchEventSource.mockImplementation((_url: string, init: unknown) => {
    try {
      invoke(init as TransportInit);
    } catch {
      // The library swallows handler throws for our purposes; mirror that here.
    }
    return Promise.resolve();
  });
}

describe('OperationEventState', () => {
  it('ignores an unknown event type without applying its content', () => {
    const state = new OperationEventState(OPERATION_ID);
    const result = state.apply(frame('1', 'mystery_event', { operationId: OPERATION_ID, seq: 1 }));
    expect(result.applied).toBe(false);
    expect(result.reason).toBe('unknown event');
    expect(state.terminated).toBe(false);
    expect(state.appliedSeqs).toEqual([]);
  });

  it('advances the cursor for an unknown event so it does not fake a gap', () => {
    const state = new OperationEventState(OPERATION_ID);
    state.apply(frame('1', 'mystery_event', { operationId: OPERATION_ID, seq: 1 }));
    const next = state.apply(frame('2', 'answer_delta', { operationId: OPERATION_ID, seq: 2, delta: 'hi' }));
    expect(next.applied).toBe(true);
    expect(state.gapsDetected).toBe(0);
    expect(state.answer).toBe('hi');
  });

  it('filters a foreign operation before seq dedupe', () => {
    const state = new OperationEventState(OPERATION_ID);
    state.apply(frame('1', 'snapshot', { operationId: OPERATION_ID, seq: 1, state: 'SUBMITTING', reasoningContent: '', answerContent: '' }));
    const result = state.apply(frame('9', 'answer_delta', { operationId: 'other-op', seq: 9, delta: 'x' }));
    expect(result.applied).toBe(false);
    expect(result.reason).toBe('foreign operationId');
    expect(state.appliedSeqs).toEqual([1]);
  });

  it('parses string frame data and tolerates a frame with no id line', () => {
    const state = new OperationEventState(OPERATION_ID);
    const result = state.apply({ event: 'answer_delta', data: JSON.stringify({ operationId: OPERATION_ID, seq: 2, delta: 'hi' }) });
    expect(result.applied).toBe(true);
    expect(state.answer).toBe('hi');
  });

  it('rejects malformed frame data as a malformed frame', () => {
    const state = new OperationEventState(OPERATION_ID);
    const result = state.apply(frame('1', 'answer_delta', '{ not json'));
    expect(result.reason).toBe('malformed frame');
  });

  it('stops applying frames after a terminal event', () => {
    const state = new OperationEventState(OPERATION_ID);
    state.apply(frame('1', 'canceled', { operationId: OPERATION_ID, seq: 1 }));
    const after = state.apply(frame('2', 'answer_delta', { operationId: OPERATION_ID, seq: 2, delta: 'late' }));
    expect(after.applied).toBe(false);
    expect(after.reason).toBe('after terminal');
    expect(state.answer).toBe('');
  });

  it('seeds the dedupe cursor from Last-Event-ID and detects a gap', () => {
    const state = new OperationEventState(OPERATION_ID, {}, '5');
    state.apply(frame('12', 'snapshot', { operationId: OPERATION_ID, seq: 12, state: 'STREAMING_ANSWER', reasoningContent: '', answerContent: 'partial' }));
    expect(state.gapsDetected).toBe(1);
    expect(state.appliedSeqs).toEqual([12]);
  });

  it('reports duplicate and out-of-order frames distinctly', () => {
    const state = new OperationEventState(OPERATION_ID);
    state.apply(frame('1', 'answer_delta', { operationId: OPERATION_ID, seq: 1, delta: 'a' }));
    state.apply(frame('3', 'answer_delta', { operationId: OPERATION_ID, seq: 3, delta: 'c' }));
    expect(state.apply(frame('3', 'answer_delta', { operationId: OPERATION_ID, seq: 3, delta: 'c' })).reason).toBe('duplicate seq');
    expect(state.apply(frame('2', 'answer_delta', { operationId: OPERATION_ID, seq: 2, delta: 'b' })).reason).toBe('out-of-order seq');
    expect(state.answer).toBe('ac');
  });
});

describe('subscribeOperationEvents error visibility', () => {
  beforeEach(() => {
    fetchEventSource.mockReset();
  });

  it('reports a connection failure through onError without leaking the raw error', () => {
    runTransport((init) => init.onerror(new Error('boom: raw vendor stack')));
    const onError = vi.fn();
    const unsubscribe = subscribeOperationEvents({ operationId: OPERATION_ID, url: '/api/operations/op-1/events', onError });
    expect(onError).toHaveBeenCalledWith({ kind: 'connection' });
    expect(JSON.stringify(onError.mock.calls)).not.toContain('raw vendor stack');
    unsubscribe();
  });

  it('reports an unexpected status and a premature close', () => {
    runTransport((init) => void Promise.resolve(init.onopen({ ok: false, status: 503 })).catch(() => {}));
    const onStatus = vi.fn();
    subscribeOperationEvents({ operationId: OPERATION_ID, url: '/api/operations/op-1/events', onError: onStatus });
    expect(onStatus).toHaveBeenCalledWith({ kind: 'unexpected-status', status: 503 });

    fetchEventSource.mockReset();
    runTransport((init) => init.onclose());
    const onClose = vi.fn();
    subscribeOperationEvents({ operationId: OPERATION_ID, url: '/api/operations/op-1/events', onError: onClose });
    expect(onClose).toHaveBeenCalledWith({ kind: 'closed-before-terminal' });
  });

  it('does not report an error when the stream already reached a terminal event', () => {
    runTransport((init) => {
      init.onmessage({ id: '1', event: 'canceled', data: JSON.stringify({ operationId: OPERATION_ID, seq: 1 }) });
      init.onclose();
    });
    const onError = vi.fn();
    subscribeOperationEvents({ operationId: OPERATION_ID, url: '/api/operations/op-1/events', onError });
    expect(onError).not.toHaveBeenCalled();
  });
});