import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { I18nProvider } from '../i18n/I18nProvider';
import { FilesPage } from './FilesPage';
import type { Attachment } from '../api/types';

const { fetchMock } = vi.hoisted(() => ({ fetchMock: vi.fn() }));

vi.mock('../store/topicData', () => ({
  listAttachments: (...args: unknown[]) => fetchMock('list', ...args),
  deleteAttachment: (...args: unknown[]) => fetchMock('delete', ...args),
  cleanupOrphanAttachments: (...args: unknown[]) => fetchMock('cleanup', ...args),
}));

function entry(partial: Partial<Attachment>): Attachment {
  return {
    attachmentId: 'a-1', displayName: 'notes.txt', mimeType: 'text/plain', sizeBytes: 5,
    parseState: 'parsed', present: true, referenceCount: 0, ...partial,
  };
}

function renderPage(): void {
  render(<I18nProvider><FilesPage /></I18nProvider>);
}

afterEach(() => {
  cleanup();
  fetchMock.mockReset();
  vi.unstubAllGlobals();
});

describe('FilesPage (T-055, settings/files section)', () => {
  it('lists attachments with usage, reference counts and parse states', async () => {
    fetchMock.mockReturnValue(Promise.resolve({
      attachments: [
        entry({ attachmentId: 'a-1', referenceCount: 2 }),
        entry({ attachmentId: 'a-2', displayName: null, parseState: 'parse_failed', referenceCount: 0 }),
      ],
      usageBytes: 42,
    }));
    renderPage();
    expect(await screen.findByTestId('files-usage')).toHaveTextContent('2');
    expect(await screen.findByText('notes.txt')).toBeInTheDocument();
    expect(screen.getByText(/被 2 条消息引用/)).toBeInTheDocument();
    expect(screen.getByText(/解析失败/)).toBeInTheDocument();
    // 无引用条目出现删除按钮；被引用条目没有。
    expect(screen.getByTestId('files-delete-a-2')).toBeInTheDocument();
    expect(screen.queryByTestId('files-delete-a-1')).toBeNull();
  });

  it('deletes an unreferenced attachment and reloads', async () => {
    let deleted = false;
    fetchMock.mockImplementation((kind: string) => {
      if (kind === 'list') {
        return deleted ? Promise.resolve({ attachments: [], usageBytes: 0 })
          : Promise.resolve({ attachments: [entry({})], usageBytes: 5 });
      }
      if (kind === 'delete') { deleted = true; return Promise.resolve({ deletedAttachmentId: 'a-1' }); }
      return Promise.reject(new Error('unexpected'));
    });
    renderPage();
    await screen.findByText('notes.txt');
    fireEvent.click(screen.getByTestId('files-delete-a-1'));
    await waitFor(() => expect(fetchMock).toHaveBeenCalledWith('delete', 'a-1'));
    await waitFor(() => expect(screen.getByText('暂无附件。')).toBeInTheDocument());
  });

  it('runs orphan cleanup and shows the emptied list', async () => {
    let cleaned = false;
    fetchMock.mockImplementation((kind: string) => {
      if (kind === 'cleanup') { cleaned = true; return Promise.resolve({ deletedAttachmentIds: ['a-9'], freedBytes: 9 }); }
      if (kind === 'list') {
        return cleaned ? Promise.resolve({ attachments: [], usageBytes: 0 })
          : Promise.resolve({ attachments: [entry({ attachmentId: 'a-9' })], usageBytes: 9 });
      }
      return Promise.reject(new Error('unexpected'));
    });
    renderPage();
    await screen.findByTestId('files-cleanup');
    fireEvent.click(screen.getByTestId('files-cleanup'));
    await waitFor(() => expect(screen.getByText('暂无附件。')).toBeInTheDocument());
  });

  it('shows the error note when listing fails', async () => {
    fetchMock.mockReturnValue(Promise.reject(new Error('down')));
    renderPage();
    expect(await screen.findByRole('alert')).toHaveTextContent('附件列表加载失败');
  });
});
