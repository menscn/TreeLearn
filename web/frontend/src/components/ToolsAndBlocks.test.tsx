import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Message } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { Composer } from './Composer';
import { MessageView } from './MessageView';
import { TranslationBlock } from './TranslationBlock';

const { subscribeMock } = vi.hoisted(() => ({ subscribeMock: vi.fn() }));

vi.mock('../api/sse', () => ({ subscribeOperationEvents: subscribeMock }));
vi.mock('../store/topicData', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../store/topicData')>();
  return { ...actual, startTranslation: vi.fn(), loadDerivedArtifact: vi.fn(), selectRevision: vi.fn() };
});

import { loadDerivedArtifact, selectRevision, startTranslation } from '../store/topicData';

function aiMessage(overrides: Partial<Message> = {}): Message {
  return {
    id: 'm-ai', branchId: 'b1', role: 'AI', ordinal: 2, userContent: null, quotedText: '',
    createdAtMillis: 0, activeRevisionId: 'rev-1', activeOperationId: null, attachments: [],
    versions: [{ id: 'rev-1', messageId: 'm-ai', revisionNo: 1, reasoningContent: '', answerContent: '答案文本', model: 'deepseek-flash', thinkingEnabled: false, promptPolicyVersion: 'tl-answer/1.0.0', createdAtMillis: 0 }],
    ...overrides,
  };
}

function renderView(ui: React.ReactElement) {
  return render(<I18nProvider>{ui}</I18nProvider>);
}

describe('MessageView version tools (T-053)', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    vi.clearAllMocks();
    window.getSelection()?.removeAllRanges();
  });

  it('switches the display version via the select and persists the pointer', async () => {
    vi.mocked(selectRevision).mockResolvedValue({
      id: 'rev-2', messageId: 'm-ai', revisionNo: 2, reasoningContent: '', answerContent: '第二版', model: 'm', thinkingEnabled: false, promptPolicyVersion: 'p', createdAtMillis: 0,
    });
    const two = aiMessage({
      versions: [
        { id: 'rev-1', messageId: 'm-ai', revisionNo: 1, reasoningContent: '', answerContent: '第一版', model: 'm', thinkingEnabled: false, promptPolicyVersion: 'p', createdAtMillis: 0 },
        { id: 'rev-2', messageId: 'm-ai', revisionNo: 2, reasoningContent: '', answerContent: '第二版', model: 'm', thinkingEnabled: false, promptPolicyVersion: 'p', createdAtMillis: 0 },
      ],
    });
    renderView(<MessageView message={two} annotationsEnabled />);
    expect(await screen.findByText('第一版')).toBeInTheDocument();
    fireEvent.change(screen.getByTestId('versions-m-ai'), { target: { value: 'rev-2' } });
    await waitFor(() => expect(selectRevision).toHaveBeenCalledWith('m-ai', 'rev-2'));
    expect(await screen.findByText('第二版')).toBeInTheDocument();
  });

  it('wires the reset button to the onReset callback', () => {
    const onReset = vi.fn();
    renderView(<MessageView message={aiMessage()} annotationsEnabled onReset={onReset} />);
    fireEvent.click(screen.getByRole('button', { name: '重新生成' }));
    expect(onReset).toHaveBeenCalledWith('m-ai');
  });

  it('reads the answer aloud when speech is available', () => {
    const speak = vi.fn();
    const cancel = vi.fn();
    vi.stubGlobal('speechSynthesis', { cancel, speak });
    vi.stubGlobal('SpeechSynthesisUtterance', class Utterance { text: string; constructor(text: string) { this.text = text; } });
    renderView(<MessageView message={aiMessage()} annotationsEnabled />);
    fireEvent.click(screen.getByRole('button', { name: '朗读回答' }));
    expect(speak).toHaveBeenCalled();
  });
});

describe('TranslationBlock (T-053, T-052 slice endpoint)', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  it('starts the derived operation, waits for the stable terminal, and shows the translation', async () => {
    vi.mocked(startTranslation).mockResolvedValue({
      id: 'op-t', commandId: 'c-t', branchId: 'b1', messageId: 'm-ai',
      promptPurpose: 'MESSAGE_TRANSLATION', promptPolicyVersion: 'tl-message-translation/1.0.0',
      model: 'deepseek-flash', thinkingEnabled: false, state: 'STAGED',
      partialReasoning: '', partialAnswer: '', createdAtMillis: 0, errorCode: null, sseUrl: '/api/operations/op-t/events',
    });
    vi.mocked(loadDerivedArtifact).mockResolvedValue({ kind: 'message_translation', scopeId: 'rev-1', stale: false, payload: { text: 'Photosynthesis is the process...' } });
    // 订阅后同步推终态。
    subscribeMock.mockImplementation(({ handlers }: { handlers: { onTerminal: (t: { kind: string }) => void } }) => {
      handlers.onTerminal({ kind: 'stable' });
      return () => {};
    });
    renderView(<TranslationBlock messageId="m-ai" revisionId="rev-1" />);
    fireEvent.click(screen.getByTestId('translation-start-m-ai'));
    expect(await screen.findByText('Photosynthesis is the process...')).toBeInTheDocument();
    expect(loadDerivedArtifact).toHaveBeenCalledWith('MESSAGE_TRANSLATION', 'rev-1', 'rev-1');
  });

  it('shows the failure note when the operation terminates non-stable', async () => {
    vi.mocked(startTranslation).mockResolvedValue({
      id: 'op-t', commandId: 'c-t', branchId: 'b1', messageId: 'm-ai',
      promptPurpose: 'MESSAGE_TRANSLATION', promptPolicyVersion: 'tl-message-translation/1.0.0',
      model: 'deepseek-flash', thinkingEnabled: false, state: 'STAGED',
      partialReasoning: '', partialAnswer: '', createdAtMillis: 0, errorCode: null, sseUrl: '/api/operations/op-t/events',
    });
    subscribeMock.mockImplementation(({ handlers }: { handlers: { onTerminal: (t: { kind: string }) => void } }) => {
      handlers.onTerminal({ kind: 'failed' });
      return () => {};
    });
    renderView(<TranslationBlock messageId="m-ai" revisionId="rev-1" />);
    fireEvent.click(screen.getByTestId('translation-start-m-ai'));
    expect(await screen.findByText('翻译失败，请重试。')).toBeInTheDocument();
  });
});

describe('Composer voice input (T-053)', () => {
  class FakeRecognition {
    static last: FakeRecognition | null = null;
    lang = '';
    interimResults = false;
    continuous = false;
    onresult: ((event: unknown) => void) | null = null;
    onerror: (() => void) | null = null;
    onend: (() => void) | null = null;
    started = false;
    start() { this.started = true; FakeRecognition.last = this; }
    stop() { /* noop */ }
    emitFinal(text: string) {
      this.onresult?.({ resultIndex: 0, results: [{ 0: { transcript: text }, isFinal: true, length: 1 }] });
    }
  }

  beforeEach(() => {
    (window as unknown as Record<string, unknown>).SpeechRecognition = FakeRecognition;
  });
  afterEach(() => {
    delete (window as unknown as Record<string, unknown>).SpeechRecognition;
    cleanup();
    vi.clearAllMocks();
  });

  it('appends the final transcript into the composer value', () => {
    const onValueChange = vi.fn();
    renderView(<Composer value="" onValueChange={onValueChange} onClear={() => {}} thinkingEnabled={false} onToggleThinking={() => {}} canSend submitting={false} blocked={false} onSend={() => {}} onStop={() => {}} onOpenSettings={() => {}} />);
    fireEvent.click(screen.getByRole('button', { name: '语音输入' }));
    FakeRecognition.last?.emitFinal('语音转写内容');
    expect(onValueChange).toHaveBeenCalledWith('语音转写内容');
  });
});
