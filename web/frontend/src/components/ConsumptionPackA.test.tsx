import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { BranchSource, Message } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { MessageView } from './MessageView';
import { SideBySideView } from './SideBySideView';

// T-050 消费包 A：划选「引用追问」回填 Composer 引文、来源定位跳转回调。

function json(body: unknown): Response {
  return { ok: true, status: 200, text: async () => JSON.stringify(body) } as unknown as Response;
}

const ANSWER = '植物利用光能把二氧化碳和水转化为有机物并释放氧气。';

function aiMessage(): Message {
  return {
    id: 'm1', branchId: 'b0', role: 'AI', ordinal: 1, userContent: null, quotedText: '', createdAtMillis: 1,
    activeRevisionId: 'r1', activeOperationId: null, attachments: [],
    versions: [{ id: 'r1', messageId: 'm1', revisionNo: 1, reasoningContent: '', answerContent: ANSWER, model: 'm', thinkingEnabled: false, promptPolicyVersion: 'pv', createdAtMillis: 1 }],
  };
}

function source(): BranchSource {
  return {
    parentBranchId: 'b-parent', sourceRevisionId: 'rev-1',
    sourceMessageId: 'src-m1',
    anchor: { quote: '', hash: '', startInclusive: 0, endExclusive: 4 },
    contextSnapshotId: 'snap-1',
  };
}

describe('T-050 consumption pack A', () => {
  beforeEach(() => {
    vi.stubGlobal('fetch', vi.fn(async () => json({ messageId: 'm1', annotations: [] })));
    vi.stubGlobal('crypto', { randomUUID: () => 'cmd' });
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.getSelection()?.removeAllRanges();
  });

  it('offers quote-and-ask in the selection bar and forwards the selected raw text', () => {
    const onQuoteText = vi.fn();
    const { container } = render(<I18nProvider><MessageView message={aiMessage()} annotationsEnabled onQuoteText={onQuoteText} /></I18nProvider>);
    const answer = container.querySelector('.message__answer') as HTMLElement;
    const span = answer.querySelector('[data-md-start]') as HTMLElement;
    const textNode = span.firstChild as Text;
    const range = document.createRange();
    range.setStart(textNode, 0);
    range.setEnd(textNode, 6);
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    fireEvent.mouseUp(answer);
    fireEvent.click(screen.getByTestId('annotation-quote'));
    expect(onQuoteText).toHaveBeenCalledWith(ANSWER.slice(0, 6));
    // 引用后浮条收起。
    expect(screen.queryByTestId('annotation-quote')).toBeNull();
  });

  it('hides the quote action when no onQuoteText handler is given', () => {
    const { container } = render(<I18nProvider><MessageView message={aiMessage()} annotationsEnabled /></I18nProvider>);
    const answer = container.querySelector('.message__answer') as HTMLElement;
    const span = answer.querySelector('[data-md-start]') as HTMLElement;
    const textNode = span.firstChild as Text;
    const range = document.createRange();
    range.setStart(textNode, 0);
    range.setEnd(textNode, 2);
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    fireEvent.mouseUp(answer);
    expect(screen.queryByTestId('annotation-quote')).toBeNull();
    expect(screen.getByTestId('annotation-highlight')).toBeInTheDocument();
  });

  it('exposes jump-to-source in the side-by-side source column', () => {
    const onJump = vi.fn();
    const srcMessage: Message = {
      id: 'src-m1', branchId: 'b-parent', role: 'AI', ordinal: 1, userContent: null, quotedText: '', createdAtMillis: 1,
      activeRevisionId: 'sr1', activeOperationId: null, attachments: [],
      versions: [{ id: 'sr1', messageId: 'src-m1', revisionNo: 1, reasoningContent: '', answerContent: '这是来源回答。', model: 'm', thinkingEnabled: false, promptPolicyVersion: 'pv', createdAtMillis: 1 }],
    };
    render(
      <I18nProvider>
        <SideBySideView currentTitle="当前" currentMessages={[aiMessage()]} source={source()} sourceTitle="来源" sourceMessages={[srcMessage]} onJumpToSourceMessage={onJump} />
      </I18nProvider>,
    );
    fireEvent.click(screen.getByTestId('jump-to-source'));
    expect(onJump).toHaveBeenCalledWith('src-m1');
  });
});
