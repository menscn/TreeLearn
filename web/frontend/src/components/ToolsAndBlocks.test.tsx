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

  // 「朗读回答」随 T-128 移除：Android 用户裁决删掉语音播放（ChatScreen 注释：
  // "speech has no learner-visible signal left"），动作行不再有朗读入口。
});

// T-128 回答动作行：Android view_answer_actions.xml / AnswerActionBar —— 四个纯图标 + 行尾相对时间。
describe('MessageView answer action row (T-128)', () => {
  afterEach(() => {
    cleanup();
    vi.clearAllMocks();
  });

  const fiveMinutesAgo = () => Date.now() - 5 * 60 * 1000;

  it('carries the four glyph actions and the trailing time, and nothing else', () => {
    renderView(
      <MessageView message={aiMessage({ createdAtMillis: fiveMinutesAgo() })} annotationsEnabled
        onReset={vi.fn()} onForkAnswer={vi.fn()} onListChildren={vi.fn()} childBranchCount={2} />,
    );
    const row = screen.getByTestId('actions-m-ai');
    // 恰好四个按钮：复制 / 重新生成 / 新建分支 / 分支列表。
    expect(row.querySelectorAll('button')).toHaveLength(4);
    expect(screen.getByRole('button', { name: '复制回答' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '重新生成' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '新建分支' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '子分支列表（2）' })).toBeInTheDocument();
    // 纯图标：四个按钮自身都不带可见文字（Android：All four leads are glyphs）；
    // 计数徽标是按钮外的角标 span，不影响按钮本身是纯图标。
    for (const button of row.querySelectorAll('button')) expect(button.textContent?.trim()).toBe('');
    expect(screen.getByTestId('children-m-ai').parentElement?.querySelector('.message__action-count')?.textContent).toBe('2');
    // 行尾相对时间。
    expect(screen.getByTestId('time-m-ai').textContent).toContain('分钟前');
    // 朗读与翻译都不在动作行里。
    expect(screen.queryByRole('button', { name: '朗读回答' })).toBeNull();
    expect(row.contains(screen.getByTestId('translation-start-m-ai'))).toBe(false);
  });

  it('hides the child-branch entry entirely when the answer has no children', () => {
    renderView(<MessageView message={aiMessage()} annotationsEnabled onListChildren={vi.fn()} childBranchCount={0} />);
    expect(screen.queryByTestId('children-m-ai')).toBeNull();
    expect(screen.getByTestId('actions-m-ai').querySelectorAll('button')).toHaveLength(1);
  });

  it('keeps the version switch out of the action row but still reachable', () => {
    const two = aiMessage({
      versions: [
        { id: 'rev-1', messageId: 'm-ai', revisionNo: 1, reasoningContent: '', answerContent: '第一版', model: 'm', thinkingEnabled: false, promptPolicyVersion: 'p', createdAtMillis: 0 },
        { id: 'rev-2', messageId: 'm-ai', revisionNo: 2, reasoningContent: '', answerContent: '第二版', model: 'm', thinkingEnabled: false, promptPolicyVersion: 'p', createdAtMillis: 0 },
      ],
    });
    renderView(<MessageView message={two} annotationsEnabled />);
    const select = screen.getByTestId('versions-m-ai');
    expect(select).toBeInTheDocument();
    expect(screen.getByTestId('actions-m-ai').contains(select)).toBe(false);
  });

  it('hides the whole row while the answer is still streaming', () => {
    renderView(<MessageView message={aiMessage()} annotationsEnabled onReset={vi.fn()} streamText="生成中…" />);
    expect(screen.queryByTestId('actions-m-ai')).toBeNull();
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
    expect(startTranslation).toHaveBeenCalledWith('m-ai', 'rev-1');
    expect(loadDerivedArtifact).toHaveBeenCalledWith('message_translation', 'm-ai', 'rev-1');
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
