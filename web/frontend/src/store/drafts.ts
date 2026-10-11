// 草稿持久化（任务 T-2026-10-10-030）：按「分支/主页」键存 localStorage，是可恢复
// 缓存而非权威数据。清理策略：显式清除 + 读取时丢弃超过 30 天的条目（写入为 {text,
// savedAtMillis}，读取按 TTL 校验即完成留存回收）。存储不可用（隐私模式/无
// localStorage）时静默降级：草稿只留内存，不阻塞输入。
export const DRAFT_KEY_PREFIX = 'draft:';
export const HOME_DRAFT_KEY = 'draft:home';
export const DRAFT_TTL_MILLIS = 30 * 24 * 60 * 60 * 1000;

interface StoredDraft { text: string; savedAtMillis: number }

export function draftKeyFor(branchId: string | null): string {
  return branchId === null ? HOME_DRAFT_KEY : `${DRAFT_KEY_PREFIX}${branchId}`;
}

function storage(): Storage | null {
  try { return window.localStorage; } catch { return null; }
}

function parse(raw: string | null): StoredDraft | null {
  if (raw === null) return null;
  try {
    const value = JSON.parse(raw) as Partial<StoredDraft>;
    return typeof value.text === 'string' && typeof value.savedAtMillis === 'number' ? (value as StoredDraft) : null;
  } catch { return null; }
}

function expired(draft: StoredDraft, nowMillis: number): boolean {
  return nowMillis - draft.savedAtMillis > DRAFT_TTL_MILLIS;
}

export function readDraft(key: string, nowMillis = Date.now()): string {
  const store = storage();
  const draft = store === null ? null : parse(store.getItem(key));
  if (store === null || draft === null) return '';
  if (expired(draft, nowMillis)) { store.removeItem(key); return ''; }
  return draft.text;
}

export function writeDraft(key: string, text: string, nowMillis = Date.now()): void {
  const store = storage();
  if (store === null) return;
  if (text.length === 0) { store.removeItem(key); return; }
  store.setItem(key, JSON.stringify({ text, savedAtMillis: nowMillis } satisfies StoredDraft));
}

export function clearDraft(key: string): void {
  storage()?.removeItem(key);
}