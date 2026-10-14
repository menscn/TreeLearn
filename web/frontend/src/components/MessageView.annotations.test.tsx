import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { Message } from '../api/types';
import { I18nProvider } from '../i18n/I18nProvider';
import { MessageView } from './MessageView';
import { selectionToRange, proseIndexForRange } from '../lib/selectionRange';

// T-049 批注前端消费：§6.10 GET/POST/DELETE 接线、PROSE 高亮渲染、
// 划选→原文偏移（data-md-* → selectionToRange）、NOTE 弹窗提交、列表删除。

function json(body: unknown, status = 200): Response {
  return { ok: status >= 200 && status < 300, status, text: async () => JSON.stringify(body) } as unknown as Response;
}

const ANSWER = '太阳把水加热，植物吸收阳光进行光合作用。';

function aiMessage(): Message {
  return {
    id: 'm1',
    branchId: 'b0',
    role: 'AI',
    ordinal: 1,
    userContent: null,
    quotedText: '',
    createdAtMillis: 1,
    activeRevisionId: 'r1',
    activeOperationId: null,
    attachments: [],
    versions: [{ id: 'r1', messageId: 'm1', revisionNo: 1, reasoningContent: '', answerContent: ANSWER, model: 'deepseek-v4-pro', thinkingEnabled: false, promptPolicyVersion: 'pv', createdAtMillis: 1 }],
  };
}

type Call = { method: string; url: string; body?: unknown };

function installFetch(handlers: { annotations?: () => Response; write?: (body: unknown) => Response; remove?: () => Response } = {}) {
  const calls: Call[] = [];
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    const method = (init?.method ?? 'GET').toUpperCase();
    const body = typeof init?.body === 'string' ? JSON.parse(init.body) : undefined;
    calls.push({ method, url, body });
    if (method === 'GET' && url === '/api/messages/m1/annotations') return handlers.annotations ? handlers.annotations() : json({ messageId: 'm1', annotations: [] });
    if (method === 'POST' && url === '/api/messages/m1/annotations') return handlers.write ? handlers.write(body) : json({ messageId: 'm1', annotations: [] });
    if (method === 'DELETE' && url.startsWith('/api/annotations/')) return handlers.remove ? handlers.remove() : json({ deletedAnnotationId: url.split('/').pop() });
    return json({}, 404);
  });
  vi.stubGlobal('fetch', fetchMock);
  return calls;
}

function renderMessage(message: Message) {
  return render(<I18nProvider><MessageView message={message} annotationsEnabled /></I18nProvider>);
}

describe('annotation frontend consumption (T-049)', () => {
  beforeEach(() => {
    vi.stubGlobal('crypto', { randomUUID: () => 'cmd-uuid' });
  });
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.getSelection()?.removeAllRanges();
  });

  it('renders persisted PROSE highlights as marks', async () => {
    installFetch({ annotations: () => json({ messageId: 'm1', annotations: [{ id: 'an1', kind: 'HIGHLIGHT', container: 'PROSE', proseIndex: 0, tableIndex: -1, rowIndex: -1, colIndex: -1, start: 0, end: 6, body: '' }] }) });
    const { container } = renderMessage(aiMessage());
    // 同一文本会同时出现在答案高亮 <mark> 与批注列表引文 <q> 中——在答案容器内定向查询。
    const mark = await waitFor(() => {
      const found = (container.querySelector('.message__answer') as HTMLElement).querySelector('mark.message__anchor');
      if (found === null) throw new Error('mark not rendered yet');
      return found;
    });
    expect(mark.tagName).toBe('MARK');
    expect(mark.textContent).toBe('太阳把水加热');
  });

  it('maps a DOM selection back to raw-text offsets via data-md attributes', () => {
    const { container } = render(<I18nProvider><MessageView message={aiMessage()} annotationsEnabled /></I18nProvider>);
    const answer = container.querySelector('.message__answer') as HTMLElement;
    const spans = Array.from(answer.querySelectorAll('[data-md-start]'));
    expect(spans.length).toBeGreaterThan(0);
    // 选中第一个 span 的前 2 个字符（「太阳」= 原文 0..2）。
    const target = spans.find((span) => span.getAttribute('data-md-start') === '0') as HTMLElement;
    const textNode = target.firstChild as Text;
    const range = document.createRange();
    range.setStart(textNode, 0);
    range.setEnd(textNode, 2);
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    const result = selectionToRange(answer, selection);
    expect(result).toEqual({ start: 0, end: 2 });
    expect(proseIndexForRange(answer, selection)).toBeGreaterThanOrEqual(0);
  });

  it('offers the action bar on selection and posts a HIGHLIGHT', async () => {
    const calls = installFetch({ write: (body) => json({ messageId: 'm1', annotations: [{ id: 'an9', kind: 'HIGHLIGHT', container: 'PROSE', proseIndex: 0, tableIndex: -1, rowIndex: -1, colIndex: -1, start: (body as { start: number }).start, end: (body as { end: number }).end, body: '' }] }) });
    const { container } = renderMessage(aiMessage());
    const answer = container.querySelector('.message__answer') as HTMLElement;
    const span = answer.querySelector('[data-md-start]') as HTMLElement;
    const textNode = span.firstChild as Text;
    const range = document.createRange();
    range.setStart(textNode, 0);
    range.setEnd(textNode, Math.min(4, textNode.textContent?.length ?? 0));
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    fireEvent.mouseUp(answer);
    fireEvent.click(await screen.findByTestId('annotation-highlight'));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && call.url === '/api/messages/m1/annotations' && (call.body as { kind: string }).kind === 'HIGHLIGHT')).toBe(true));
    const writeCall = calls.find((call) => call.method === 'POST') as Call;
    expect(writeCall.body).toMatchObject({ container: 'PROSE', start: 0 });
    expect(writeCall.body).toHaveProperty('commandId');
    const mark = await waitFor(() => {
      const found = (container.querySelector('.message__answer') as HTMLElement).querySelector('mark.message__anchor');
      if (found === null) throw new Error('mark not rendered yet');
      return found;
    });
    expect(mark.tagName).toBe('MARK');
    expect(mark.textContent).toBe('太阳把水');
  });

  it('submits a NOTE through the dialog and renders it in the list', async () => {
    const calls = installFetch({ write: (body) => json({ messageId: 'm1', annotations: [{ id: 'an2', kind: 'NOTE', container: 'PROSE', proseIndex: 0, tableIndex: -1, rowIndex: -1, colIndex: -1, start: 0, end: 2, body: (body as { body: string }).body }] }) });
    const { container } = renderMessage(aiMessage());
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
    fireEvent.click(await screen.findByTestId('annotation-note'));
    fireEvent.change(await screen.findByTestId('annotation-note-input'), { target: { value: '重点记一下' } });
    fireEvent.click(screen.getByTestId('annotation-note-submit'));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST' && (call.body as { body?: string }).body === '重点记一下')).toBe(true));
    expect(await screen.findByText('重点记一下')).toBeInTheDocument();
  });

  it('deletes an annotation from the list', async () => {
    const calls = installFetch({ annotations: () => json({ messageId: 'm1', annotations: [{ id: 'an3', kind: 'NOTE', container: 'PROSE', proseIndex: 0, tableIndex: -1, rowIndex: -1, colIndex: -1, start: 0, end: 2, body: '备注' }] }) });
    renderMessage(aiMessage());
    const item = await screen.findByText('备注');
    expect(item).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '删除批注' }));
    await waitFor(() => expect(calls.some((call) => call.method === 'DELETE' && call.url === '/api/annotations/an3')).toBe(true));
    await waitFor(() => expect(screen.queryByText('备注')).toBeNull());
  });
});

// T-129 划词菜单：Android popup_word_action.xml —— 六格图标行，顺序为
// 问 AI / 新建主题 / 高亮 / 批注 / 复制 / 全选；「菜单格一律图标承载，不得文字按钮」。
describe('word menu (T-129)', () => {
  afterEach(() => {
    cleanup();
    vi.unstubAllGlobals();
    window.getSelection()?.removeAllRanges();
  });

  function selectChars(container: HTMLElement, length: number) {
    const answer = container.querySelector('.message__answer') as HTMLElement;
    const span = answer.querySelector('[data-md-start]') as HTMLElement;
    const textNode = span.firstChild as Text;
    const range = document.createRange();
    range.setStart(textNode, 0);
    range.setEnd(textNode, Math.min(length, textNode.textContent?.length ?? 0));
    const selection = window.getSelection();
    selection?.removeAllRanges();
    selection?.addRange(range);
    fireEvent.mouseUp(answer);
    return answer;
  }

  const CELLS = ['annotation-quote', 'annotation-fork', 'annotation-highlight', 'annotation-note', 'annotation-copy', 'annotation-select-all'];

  it('draws the six icon cells in Android order, with no text button', async () => {
    installFetch();
    const { container } = render(
      <I18nProvider>
        <MessageView message={aiMessage()} annotationsEnabled onQuoteText={vi.fn()} onForkSelection={vi.fn()} />
      </I18nProvider>,
    );
    selectChars(container, 4);
    const menu = await screen.findByTestId('word-menu');
    expect(Array.from(menu.querySelectorAll('button')).map((button) => button.getAttribute('data-testid'))).toEqual(CELLS);
    // 菜单格一律图标承载，不得文字按钮。
    for (const button of menu.querySelectorAll('button')) expect(button.textContent?.trim()).toBe('');
  });

  it('copies only the selected passage', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
    installFetch();
    const { container } = render(<I18nProvider><MessageView message={aiMessage()} annotationsEnabled /></I18nProvider>);
    selectChars(container, 4);
    fireEvent.click(await screen.findByTestId('annotation-copy'));
    await waitFor(() => expect(writeText).toHaveBeenCalledWith(ANSWER.slice(0, 4)));
  });

  it('widens the selection in place so 全选之后的高亮覆盖整条回答', async () => {
    const calls = installFetch({ write: (body) => json({ messageId: 'm1', annotations: [{ id: 'an9', kind: 'HIGHLIGHT', container: 'PROSE', proseIndex: 0, tableIndex: -1, rowIndex: -1, colIndex: -1, start: (body as { start: number }).start, end: (body as { end: number }).end, body: '' }] }) });
    const { container } = render(<I18nProvider><MessageView message={aiMessage()} annotationsEnabled /></I18nProvider>);
    selectChars(container, 4);
    fireEvent.click(await screen.findByTestId('annotation-select-all'));
    fireEvent.click(screen.getByTestId('annotation-highlight'));
    await waitFor(() => expect(calls.some((call) => call.method === 'POST')).toBe(true));
    const writeCall = calls.find((call) => call.method === 'POST') as Call;
    expect(writeCall.body).toMatchObject({ container: 'PROSE', start: 0, end: ANSWER.length });
  });

  it('offers no cancel cell and closes on Escape', async () => {
    installFetch();
    const { container } = render(<I18nProvider><MessageView message={aiMessage()} annotationsEnabled /></I18nProvider>);
    selectChars(container, 4);
    const menu = await screen.findByTestId('word-menu');
    // Android 的六格里没有「取消」格（点空白处收起）。
    expect(screen.queryByTestId('annotation-cancel')).toBeNull();
    fireEvent.keyDown(window, { key: 'Escape' });
    await waitFor(() => expect(screen.queryByTestId('word-menu')).toBeNull());
    expect(menu).toBeTruthy();
  });
});
