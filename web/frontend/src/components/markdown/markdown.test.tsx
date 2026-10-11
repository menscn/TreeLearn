import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import type { Message } from '../../api/types';
import { I18nProvider } from '../../i18n/I18nProvider';
import { MessageView } from '../MessageView';
import { Markdown } from './Markdown';

// 渲染级测试：断言真实 React 元素树（无 dangerouslySetInnerHTML）。
function renderMd(text: string, highlight: { start: number; end: number } | null = null) {
  return render(
    <I18nProvider>
      <Markdown text={text} highlight={highlight} />
    </I18nProvider>,
  );
}

describe('markdown block coverage', () => {
  it('renders h1/h2/h3 headings', () => {
    const { container } = renderMd('# One\n\n## Two\n\n### Three');
    expect(screen.getByRole('heading', { level: 1, name: 'One' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: 'Two' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 3, name: 'Three' })).toBeInTheDocument();
    expect(container.querySelectorAll('h4,h5,h6')).toHaveLength(0);
  });

  it('degrades an out-of-range heading level to a paragraph', () => {
    const { container } = renderMd('#### Not a heading');
    expect(screen.queryByRole('heading')).toBeNull();
    expect(container.querySelector('.md-paragraph')?.textContent).toBe('#### Not a heading');
  });

  it('keeps soft line breaks inside a paragraph', () => {
    const { container } = renderMd('line one\nline two');
    expect(container.querySelector('.md-paragraph')?.textContent).toBe('line one\nline two');
  });

  it('renders unordered and ordered lists', () => {
    const { container } = renderMd('- alpha\n- beta\n\n1. first\n2. second');
    expect(container.querySelectorAll('ul.md-list > li')).toHaveLength(2);
    expect(container.querySelectorAll('ol.md-list > li')).toHaveLength(2);
  });

  it('degrades a marker without a following space to a paragraph', () => {
    const { container } = renderMd('-nospace');
    expect(container.querySelector('ul')).toBeNull();
    expect(container.querySelector('.md-paragraph')?.textContent).toBe('-nospace');
  });

  it('renders a blockquote', () => {
    const { container } = renderMd('> quoted line');
    expect(container.querySelector('blockquote.md-quote')?.textContent).toContain('quoted line');
  });

  it('renders a thematic break', () => {
    const { container } = renderMd('text\n\n---\n\nmore');
    expect(container.querySelector('hr.md-hr')).not.toBeNull();
  });

  it('renders a fenced code block with a language label, copy button and code text', () => {
    const { container } = renderMd('```js\nconst x = 1;\n```');
    expect(container.querySelector('.md-code__pre code')?.textContent).toBe('const x = 1;');
    expect(screen.getByText('js')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '复制代码' })).toBeInTheDocument();
  });

  it('uses a localized fallback label for a fenced block without a language', () => {
    renderMd('```\nplain\n```');
    expect(screen.getByText('代码')).toBeInTheDocument();
  });

  it('renders a GFM table with header and per-column alignment classes', () => {
    const { container } = renderMd('| A | B | C |\n| --- | :--: | --: |\n| 1 | 2 | 3 |');
    const headers = container.querySelectorAll('table.md-table thead th');
    expect(headers).toHaveLength(3);
    expect(headers[0].className).toContain('md-align-left');
    expect(headers[1].className).toContain('md-align-center');
    expect(headers[2].className).toContain('md-align-right');
    expect(container.querySelectorAll('table.md-table tbody tr td')).toHaveLength(3);
  });

  it('degrades a table missing its delimiter row to a paragraph', () => {
    const { container } = renderMd('| A | B |\n| 1 | 2 |');
    expect(container.querySelector('table')).toBeNull();
    expect(container.querySelector('.md-paragraph')).not.toBeNull();
  });
});

describe('markdown inline coverage', () => {
  it('renders strong and emphasis', () => {
    const { container } = renderMd('this is **bold** and *italic*');
    expect(container.querySelector('strong')?.textContent).toBe('bold');
    expect(container.querySelector('em')?.textContent).toBe('italic');
  });

  it('degrades unclosed strong markers to literal text', () => {
    const { container } = renderMd('an **unclosed bold');
    expect(container.querySelector('strong')).toBeNull();
    expect(container.querySelector('.md-paragraph')?.textContent).toBe('an **unclosed bold');
  });

  it('renders inline code and degrades an unclosed backtick to literal text', () => {
    const first = renderMd('use `npm test` now');
    expect(first.container.querySelector('code.md-inline-code')?.textContent).toBe('npm test');
    first.unmount();
    const second = renderMd('a `dangling');
    expect(second.container.querySelector('code.md-inline-code')).toBeNull();
  });

  it('renders an http and an https link with safe rel and target', () => {
    renderMd('[a](http://example.com) and [b](https://example.com/path)');
    const links = screen.getAllByRole('link');
    expect(links).toHaveLength(2);
    for (const link of links) {
      expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      expect(link).toHaveAttribute('target', '_blank');
    }
  });

  it('renders block math and inline math as styled literal text (readable degradation)', () => {
    const first = renderMd('$$x^2 + y^2$$');
    expect(first.container.querySelector('.md-math--block')?.textContent).toBe('x^2 + y^2');
    first.unmount();
    const second = renderMd('the value $x^2$ here');
    expect(second.container.querySelector('.md-math')?.textContent).toBe('x^2');
  });

  it('degrades an unclosed inline dollar to literal text', () => {
    const { container } = renderMd('cost is $5 and more');
    expect(container.querySelector('.md-math')).toBeNull();
    expect(container.querySelector('.md-paragraph')?.textContent).toBe('cost is $5 and more');
  });
});

describe('markdown streaming tolerance', () => {
  it('renders an unclosed fence as a code block through the end of the document', () => {
    const { container } = renderMd('answer\n\n```python\nprint(1)\nprint(2)');
    expect(container.querySelector('.md-code__pre code')?.textContent).toBe('print(1)\nprint(2)');
    expect(screen.getByText('python')).toBeInTheDocument();
  });

  it('keeps an unclosed block-formula opener as literal text', () => {
    const { container } = renderMd('before\n\n$$\nx^2');
    expect(container.querySelector('.md-math--block')).toBeNull();
    expect(container.textContent).toContain('$$');
  });

  it('degrades a half-written table (header without delimiter) to a paragraph', () => {
    const { container } = renderMd('| col A | col B');
    expect(container.querySelector('table')).toBeNull();
    expect(container.querySelector('.md-paragraph')).not.toBeNull();
  });
});

describe('markdown XSS safety', () => {
  it('never injects a script element and keeps the markup as text', () => {
    const payload = '<script>alert(1)</script>';
    const { container } = renderMd(payload);
    expect(container.querySelector('script')).toBeNull();
    expect(container.querySelector('.md-paragraph')?.textContent).toBe(payload);
  });

  it('never injects an img element from an onerror payload', () => {
    const payload = '<img src=x onerror=alert(2)>';
    const { container } = renderMd(payload);
    expect(container.querySelector('img')).toBeNull();
    expect(container.querySelector('.md-paragraph')?.textContent).toBe(payload);
  });

  it('joins nested raw tags into inert text', () => {
    const { container } = renderMd('<b><i>bold</i></b>');
    expect(container.querySelector('b')).toBeNull();
    expect(container.querySelector('i')).toBeNull();
  });

  it('does not create an anchor for a javascript: link', () => {
    const { container } = renderMd('[click](javascript:alert(1))');
    expect(container.querySelector('a')).toBeNull();
    expect(container.textContent).toContain('click');
  });

  it('does not create an anchor for a data: link', () => {
    const { container } = renderMd('[x](data:text/html;base64,PHNjcmlwdD4=)');
    expect(container.querySelector('a')).toBeNull();
  });

  it('preserves UTF-8 and emoji content', () => {
    const { container } = renderMd('你好 🌱 **粗体**');
    expect(container.querySelector('strong')?.textContent).toBe('粗体');
    expect(container.textContent).toContain('🌱');
  });
});

describe('markdown bounded input', () => {
  it('renders a very long single line without crashing', () => {
    const long = 'a'.repeat(200000);
    const { container } = renderMd(long);
    expect(container.querySelector('.md-paragraph')).not.toBeNull();
  });

  it('renders deeply nested emphasis without unbounded recursion', () => {
    const deep = `${'*'.repeat(400)}x${'*'.repeat(400)}`;
    const { container } = renderMd(deep);
    expect(container.textContent).toContain('x');
  });
});

describe('markdown code copy', () => {
  function stubClipboard(writeText: (text: string) => Promise<void>) {
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true });
  }
  afterEach(() => {
    Reflect.deleteProperty(navigator as unknown as Record<string, unknown>, 'clipboard');
  });

  it('copies the code text and announces success', async () => {
    const writeText = vi.fn().mockResolvedValue(undefined);
    stubClipboard(writeText);
    renderMd('```\ncopy me\n```');
    fireEvent.click(screen.getByRole('button', { name: '复制代码' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('已复制'));
    expect(writeText).toHaveBeenCalledWith('copy me');
  });

  it('announces a readable failure without throwing when the clipboard rejects', async () => {
    stubClipboard(vi.fn().mockRejectedValue(new Error('denied')));
    renderMd('```\ncopy me\n```');
    fireEvent.click(screen.getByRole('button', { name: '复制代码' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('复制失败，请手动选择文本。'));
  });

  it('announces a readable failure when the clipboard is unavailable', async () => {
    Object.defineProperty(navigator, 'clipboard', { value: undefined, configurable: true });
    renderMd('```\ncopy me\n```');
    fireEvent.click(screen.getByRole('button', { name: '复制代码' }));
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('复制失败，请手动选择文本。'));
  });
});

describe('MessageView markdown integration', () => {
  function aiAnswer(answer: string): Message {
    return {
      id: 'a1', branchId: 'b', role: 'AI', ordinal: 1, userContent: null, quotedText: '', createdAtMillis: 1,
      activeRevisionId: 'r1', activeOperationId: null, attachments: [],
      versions: [{ id: 'r1', messageId: 'a1', revisionNo: 1, reasoningContent: '', answerContent: answer, model: 'm', thinkingEnabled: false, promptPolicyVersion: 'pv', createdAtMillis: 1 }],
    };
  }
  function userMessage(text: string, quotedText = ''): Message {
    return { id: 'u1', branchId: 'b', role: 'USER', ordinal: 0, userContent: text, quotedText, createdAtMillis: 1, activeRevisionId: null, activeOperationId: null, attachments: [], versions: [] };
  }

  it('renders a markdown AI answer as real elements', () => {
    render(<I18nProvider><MessageView message={aiAnswer('# 标题\n\n- 一\n- 二')} /></I18nProvider>);
    expect(screen.getByRole('heading', { level: 1, name: '标题' })).toBeInTheDocument();
    expect(screen.getAllByRole('listitem')).toHaveLength(2);
  });

  it('still renders a USER quote block and the markdown question', () => {
    const { container } = render(<I18nProvider><MessageView message={userMessage('**问题**', '被引用的片段')} /></I18nProvider>);
    expect(container.querySelector('blockquote.message__quote')?.textContent).toBe('被引用的片段');
    expect(container.querySelector('.message__user strong')?.textContent).toBe('问题');
  });

  it('highlights an anchor range inside the rendered answer', () => {
    render(<I18nProvider><MessageView message={aiAnswer('光合作用是植物利用光能的过程')} highlight={{ start: 5, end: 9 }} /></I18nProvider>);
    const mark = screen.getByText('植物利用');
    expect(mark.tagName.toLowerCase()).toBe('mark');
  });
});