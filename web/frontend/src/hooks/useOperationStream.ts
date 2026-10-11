import { useEffect, useRef, useState } from 'react';

import { createOperationFrameSource, OperationEventState, type OperationFrameSource, type SseStreamError, type SseFrame, type TerminalEvent } from '../api/sse';
import type { GenerationState } from '../api/types';

// 工作区流式视图（任务 T-2026-10-10-031）：把 sse.ts 的 OperationEventState 接到 React。
// 状态机不 mock：hook 自己持有真实 OperationEventState；传输通过可注入的 OperationFrameSource
// 提供——生产用 createOperationFrameSource（fetch-event-source），测试注入 fixtures 帧序列。
// 每个帧 apply 后刷新视图，使 reasoning_delta/answer_delta 的增量即时上屏；终态后停止订阅。
export interface OperationStreamState {
  state: GenerationState | null;
  reasoning: string;
  answer: string;
  terminal: TerminalEvent | null;
  gaps: number;
  ignoredCount: number;
  error: SseStreamError | null;
}

const EMPTY: OperationStreamState = { state: null, reasoning: '', answer: '', terminal: null, gaps: 0, ignoredCount: 0, error: null };

export interface UseOperationStreamOptions {
  /** 订阅的操作 id；为 null 时不建立订阅。 */
  operationId: string | null;
  /** 操作的事件流地址（契约 §5.6 `sseUrl`）；测试注入 source 时可传 null。 */
  url: string | null;
  lastEventId?: string | null;
  /** 注入的帧源；缺省时按 url 建传输帧源。 */
  source?: OperationFrameSource | null;
}

export function useOperationStream(options: UseOperationStreamOptions): OperationStreamState {
  const { operationId, url, lastEventId = null, source = null } = options;
  const [view, setView] = useState<OperationStreamState>(EMPTY);
  // source 通过 ref 读取而非依赖：它只在 operationId/url 变化时换绑，避免内联创建的帧源
  // 因引用每次都变而反复重订阅。
  const sourceRef = useRef(source);
  sourceRef.current = source;

  useEffect(() => {
    const frameSource = sourceRef.current;
    if (operationId === null || (url === null && frameSource === null)) {
      setView(EMPTY);
      return;
    }
    const engine = new OperationEventState(operationId, {}, lastEventId);
    let ignored = 0;
    const publish = () => setView({ state: engine.state, reasoning: engine.reasoning, answer: engine.answer, terminal: engine.terminal, gaps: engine.gapsDetected, ignoredCount: ignored, error: null });
    publish();
    return (frameSource ?? createOperationFrameSource(url as string, lastEventId)).subscribe({
      onFrame: (frame: SseFrame) => {
        if (!engine.apply(frame).applied) ignored += 1;
        publish();
        return engine.terminated;
      },
      onError: (error) => setView((prev) => ({ ...prev, error })),
    });
  }, [operationId, url, lastEventId]);

  return view;
}