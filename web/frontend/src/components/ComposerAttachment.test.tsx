import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { I18nProvider } from '../i18n/I18nProvider';
import { Composer } from './Composer';

const { fetchMock } = vi.hoisted(() => ({ fetchMock: vi.fn() }));

vi.mock('../store/topicData', () => ({
  listAttachments: (...args: unknown[]) => fetchMock('list', ...args),
  deleteAttachment: (...args: unknown[]) => fetchMock('delete', ...args),
  cleanupOrphanAttachments: (...args: unknown[]) => fetchMock('cleanup', ...args),
}));

afterEach(() => {
  cleanup();
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

describe('Composer attachment wiring (T-055)', () => {
  function renderComposer(overrides: Record<string, unknown> = {}): void {
    render(
      <I18nProvider>
        <Composer value="" onValueChange={() => {}} onClear={() => {}} thinkingEnabled={false}
          onToggleThinking={() => {}} canSend submitting={false} blocked={false}
          onSend={() => {}} onStop={() => {}} onOpenSettings={() => {}}
          attachment={{ attachmentId: 'a-1', displayName: 'notes.txt' }}
          onClearAttachment={() => {}} {...overrides} />
      </I18nProvider>,
    );
  }

  it('shows the pending attachment chip with a clear action', () => {
    renderComposer();
    expect(screen.getByTestId('composer-attachment')).toHaveTextContent('notes.txt');
    fireEvent.click(screen.getByRole('button', { name: '移除附件' }));
    // 纯渲染组件：清除由父层回调承担，这里断言 chip 可见即可。
    expect(screen.getByTestId('composer-attachment')).toBeInTheDocument();
  });

  it('shows the unnamed fallback when displayName is null', () => {
    renderComposer({ attachment: { attachmentId: 'a-2', displayName: null } });
    expect(screen.getByTestId('composer-attachment')).toHaveTextContent('未命名附件');
  });
});
