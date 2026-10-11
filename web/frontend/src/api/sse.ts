import { fetchEventSource } from '@microsoft/fetch-event-source';

import { isErrorCode, type ApiErrorParams } from './errors';
import { isAnswerRevision, isGenerationState, type AnswerRevision, type GenerationState } from './types';

// SSE 客户端：唯一权威为 web/contracts/api.md §7。
// 状态机（OperationEventState）与传输（subscribeOperationEvents）分离：状态机是纯
// 逻辑、可被 fixtures 帧序列注入驱动；传输用项目统一的认证流客户端
// @microsoft/fetch-event-source，不用原生 EventSource。
// 客户端规则（api.md §7.3）：按 operationId 过滤外来事件；按 seq 去重（重复=等于
// 已处理最大值；乱序=小于最大值）；snapshot 作为部分内容权威刷新；终态后停止；
// 未知事件类型忽略并记录，不抛给 UI；心跳注释由传输层按 EventSource 规范丢弃。

export const SSE_EVENT_TYPES = ['snapshot', 'reasoning_delta', 'answer_delta', 'state', 'stable', 'failed', 'interrupted', 'canceled'] as const;
export type SseEventType = (typeof SSE_EVENT_TYPES)[number];
export type TerminalKind = 'stable' | 'failed' | 'interrupted' | 'canceled';
export type IgnoreReason = 'foreign operationId' | 'duplicate seq' | 'out-of-order seq' | 'unknown event' | 'malformed frame' | 'after terminal';

export interface SseFrame { id?: string; event: string; data: unknown }
export interface FrameApplyResult { applied: boolean; seq?: number; reason?: IgnoreReason }
export interface IgnoredFrame { id?: string; event: string; reason: IgnoreReason }
export interface SnapshotView { state: GenerationState | null; reasoning: string; answer: string }
export interface TerminalEvent { kind: TerminalKind; revision?: AnswerRevision; errorCode?: string; params?: ApiErrorParams }
export interface OperationEventHandlers { onSnapshot?: (snapshot: SnapshotView) => void; onState?: (state: GenerationState) => void; onTerminal?: (terminal: TerminalEvent) => void; onIgnored?: (frame: IgnoredFrame) => void }

// 传输层失败的可观察错误：只给分类与（可选）状态码，不带网络库原始报错文本，
// 供 UI 转成安全文案（rules/frontend.md 用户错误提示）。
export type SseStreamErrorKind = 'connection' | 'closed-before-terminal' | 'unexpected-status';
export interface SseStreamError { kind: SseStreamErrorKind; status?: number }
export interface SubscribeOperationEventsOptions { operationId: string; url: string; lastEventId?: string | null; handlers?: OperationEventHandlers; onError?: (error: SseStreamError) => void }

// 低层帧源：把传输层收到的每个原始帧交给调用方（`onFrame` 返回 true 表示调用方已到终态，
// 传输层随即关闭）。状态机（OperationEventState）仍由调用方持有——`subscribeOperationEvents`
// 与工作区 hook 共用同一帧源，测试用 fixtures 帧序列替换 `OperationFrameSource` 即可驱动 UI。
export interface OperationFrameSink { onFrame: (frame: SseFrame) => boolean | void; onError?: (error: SseStreamError) => void }
export interface OperationFrameSource { subscribe: (sink: OperationFrameSink) => () => void }

export function createOperationFrameSource(url: string, lastEventId?: string | null): OperationFrameSource {
  return {
    subscribe(sink) {
      const controller = new AbortController();
      const headers: Record<string, string> = {};
      if (lastEventId) headers['Last-Event-ID'] = lastEventId;
      void fetchEventSource(url, {
        method: 'GET',
        headers,
        signal: controller.signal,
        openWhenHidden: true,
        onopen: async (response) => {
          if (!response.ok) {
            sink.onError?.({ kind: 'unexpected-status', status: response.status });
            throw new Error(`unexpected SSE response status ${response.status}`);
          }
        },
        onmessage: (message) => {
          if (message.event === '') return;
          if (sink.onFrame({ id: message.id || undefined, event: message.event, data: message.data }) === true) controller.abort();
        },
        onclose: () => {
          sink.onError?.({ kind: 'closed-before-terminal' });
          throw new Error('SSE stream closed before a terminal event');
        },
        onerror: (error) => {
          sink.onError?.({ kind: 'connection' });
          throw error;
        },
      }).catch(() => controller.abort());
      return () => controller.abort();
    },
  };
}

export function isSseEventType(value: string): value is SseEventType {
  return (SSE_EVENT_TYPES as readonly string[]).includes(value);
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
}

function asParams(value: unknown): ApiErrorParams | undefined {
  const record = asRecord(value);
  if (!record) return undefined;
  const params: Record<string, string | number | boolean> = {};
  for (const [key, entry] of Object.entries(record)) {
    if (typeof entry === 'string' || typeof entry === 'number' || typeof entry === 'boolean') params[key] = entry;
  }
  return params;
}

export class OperationEventState {
  private cursor = 0;
  private readonly appliedSeqList: number[] = [];
  private readonly ignoredFrameList: IgnoredFrame[] = [];
  private gapCount = 0;
  private terminalEvent: TerminalEvent | null = null;
  private generationState: GenerationState | null = null;
  private reasoningContent = '';
  private answerContent = '';

  constructor(private readonly operationId: string, private readonly handlers: OperationEventHandlers = {}, lastEventId?: string | null) {
    const parsed = lastEventId ? Number.parseInt(lastEventId, 10) : Number.NaN;
    if (Number.isFinite(parsed) && parsed > 0) this.cursor = parsed;
  }

  get appliedSeqs(): readonly number[] { return this.appliedSeqList; }
  get state(): GenerationState | null { return this.generationState; }
  get reasoning(): string { return this.reasoningContent; }
  get answer(): string { return this.answerContent; }
  get gapsDetected(): number { return this.gapCount; }
  get terminated(): boolean { return this.terminalEvent !== null; }
  get terminal(): TerminalEvent | null { return this.terminalEvent; }

  apply(frame: SseFrame): FrameApplyResult {
    if (this.terminalEvent !== null) return this.ignore(frame, 'after terminal');
    const data = this.parse(frame.data);
    if (data === null) return this.ignore(frame, 'malformed frame');
    if (data.operationId !== this.operationId) return this.ignore(frame, 'foreign operationId');
    const seq = typeof data.seq === 'number' && Number.isFinite(data.seq) ? data.seq : null;
    if (seq === null) return this.ignore(frame, 'malformed frame');
    if (seq === this.cursor) return this.ignore(frame, 'duplicate seq');
    if (seq < this.cursor) return this.ignore(frame, 'out-of-order seq');
    // 任意能解析出合法 seq 的帧都推进 cursor；未知事件类型只忽略内容，不制造假缺口。
    if (seq > this.cursor + 1) this.gapCount += 1;
    this.cursor = seq;
    if (!isSseEventType(frame.event)) return this.ignore(frame, 'unknown event');
    this.appliedSeqList.push(seq);
    this.dispatch(frame.event, data);
    return { applied: true, seq };
  }

  private parse(raw: unknown): Record<string, unknown> | null {
    if (typeof raw === 'string') {
      try {
        return asRecord(JSON.parse(raw));
      } catch {
        return null;
      }
    }
    return asRecord(raw);
  }

  private ignore(frame: SseFrame, reason: IgnoreReason): FrameApplyResult {
    const ignored: IgnoredFrame = { id: frame.id, event: frame.event, reason };
    this.ignoredFrameList.push(ignored);
    this.handlers.onIgnored?.(ignored);
    return { applied: false, reason };
  }

  private dispatch(event: SseEventType, data: Record<string, unknown>): void {
    switch (event) {
      case 'snapshot': {
        const state = isGenerationState(data.state) ? data.state : null;
        this.generationState = state;
        this.reasoningContent = typeof data.reasoningContent === 'string' ? data.reasoningContent : '';
        this.answerContent = typeof data.answerContent === 'string' ? data.answerContent : '';
        this.handlers.onSnapshot?.({ state, reasoning: this.reasoningContent, answer: this.answerContent });
        return;
      }
      case 'reasoning_delta':
        if (typeof data.delta === 'string') this.reasoningContent += data.delta;
        return;
      case 'answer_delta':
        if (typeof data.delta === 'string') this.answerContent += data.delta;
        return;
      case 'state':
        if (isGenerationState(data.state)) {
          this.generationState = data.state;
          this.handlers.onState?.(data.state);
        }
        return;
      case 'stable':
        this.settle({ kind: 'stable', revision: isAnswerRevision(data.revision) ? data.revision : undefined });
        return;
      case 'failed':
        this.settle({ kind: 'failed', errorCode: isErrorCode(data.errorCode) ? data.errorCode : undefined, params: asParams(data.params) });
        return;
      case 'interrupted':
        this.settle({ kind: 'interrupted' });
        return;
      case 'canceled':
        this.settle({ kind: 'canceled' });
        return;
    }
  }

  private settle(terminal: TerminalEvent): void {
    this.terminalEvent = terminal;
    this.handlers.onTerminal?.(terminal);
  }
}

// 订阅一个操作的 SSE 流并返回取消函数。断线由传输层按 Last-Event-ID 重连；终态后
// 主动关闭。onerror 抛出以阻止无限重连，避免把网络库原始报错透给 UI（边界转换）。
export function subscribeOperationEvents(options: SubscribeOperationEventsOptions): () => void {
  const state = new OperationEventState(options.operationId, options.handlers, options.lastEventId);
  return createOperationFrameSource(options.url, options.lastEventId).subscribe({
    onFrame: (frame) => {
      state.apply(frame);
      return state.terminated;
    },
    onError: (error) => {
      if (!state.terminated) options.onError?.(error);
    },
  });
}