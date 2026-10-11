import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import type { AnswerRevision, BranchSource, Message } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { SideBySideView } from './SideBySideView';

function revision(id: string, answer: string): AnswerRevision {
  return { id, messageId: 'm', revisionNo: 1, reasoningContent: '', answerContent: answer, model: 'deepseek-v4-pro', thinkingEnabled: false, promptPolicyVersion: 'pv', createdAtMillis: 1 };
}

function aiMessage(id: string, answer: string): Message {
  return { id, branchId: 'b', role: 'AI', ordinal: 1, userContent: null, quotedText: '', createdAtMillis: 1, activeRevisionId: 'r1', activeOperationId: null, attachments: [], versions: [revision('r1', answer)] };
}

function userMessage(id: string, text: string): Message {
  return { id, branchId: 'b', role: 'USER', ordinal: 0, userContent: text, quotedText: '', createdAtMillis: 1, activeRevisionId: null, activeOperationId: null, attachments: [], versions: [] };
}

function source(overrides: Partial<BranchSource> = {}): BranchSource {
  return {
    parentBranchId: 'parent',
    sourceMessageId: 'src-1',
    sourceRevisionId: 'r1',
    anchor: { startInclusive: null, endExclusive: null, quote: null, hash: null },
    contextSnapshotId: 'snap-1',
    ...overrides,
  };
}

function renderView(props: Partial<Parameters<typeof SideBySideView>[0]> = {}) {
  return render(
    <I18nProvider>
      <SideBySideView
        currentTitle="光合作用的分支追问"
        currentMessages={[userMessage('c0', '那光反应在哪里进行？'), aiMessage('c1', '光反应在类囊体薄膜上进行。')]}
        source={source()}
        sourceTitle="光合作用是什么"
        sourceMessages={[userMessage('s0', '光合作用是什么？'), aiMessage('src-1', '光合作用是植物利用光能把二氧化碳和水合成有机物的过程。')]}
        {...props}
      />
    </I18nProvider>,
  );
}

describe('SideBySideView', () => {
  it('renders the current branch and its fork source side by side', () => {
    renderView();
    expect(screen.getByRole('heading', { name: '光合作用的分支追问' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '光合作用是什么' })).toBeInTheDocument();
    expect(screen.getByText('光反应在类囊体薄膜上进行。')).toBeInTheDocument();
    expect(screen.getByText(/合成有机物的过程。$/)).toBeInTheDocument();
  });

  it('highlights the anchor range inside the located source message', () => {
    renderView({
      source: source({ anchor: { startInclusive: 5, endExclusive: 9, quote: '植物利用', hash: 'h' } }),
    });
    const mark = screen.getByText('植物利用');
    expect(mark.tagName.toLowerCase()).toBe('mark');
  });

  it('shows a readable fallback when the source message is not among the loaded messages', () => {
    renderView({ sourceMessages: [userMessage('other', '别的消息')] });
    expect(screen.getByText('来源消息不在已加载内容中，请回到来源分支查看。')).toBeInTheDocument();
  });

  it('states the source snapshot id', () => {
    renderView();
    expect(screen.getByText(/snap-1/)).toBeInTheDocument();
  });

  it('explains that a root branch has no fork source', () => {
    renderView({ source: null, sourceTitle: null });
    expect(screen.getByText('当前分支是根分支，没有分叉来源。')).toBeInTheDocument();
  });
});