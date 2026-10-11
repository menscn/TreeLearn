import { useCallback, useEffect, useRef, useState } from 'react';

import { readDraft, writeDraft } from '../store/drafts';

// 草稿恢复 + 防抖保存（任务 T-2026-10-10-030；键切换支持 T-2026-10-10-031）：挂载同步
// 读回一次，输入防抖写入，卸载立即落盘（刷新/重挂载可恢复）；发送成功或显式清除时删除
// 条目。`storageKey` 变化（如切换分支）时先落盘旧键、再读取新键，使各分支草稿互不覆盖。
export function useDraft(storageKey: string, debounceMillis = 400) {
  const [text, setText] = useState(() => readDraft(storageKey));
  const latest = useRef(text);
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const keyRef = useRef(storageKey);

  const flush = useCallback(() => {
    if (timer.current !== null) { clearTimeout(timer.current); timer.current = null; }
    writeDraft(keyRef.current, latest.current);
  }, []);

  useEffect(() => {
    if (keyRef.current === storageKey) return;
    flush();
    keyRef.current = storageKey;
    latest.current = readDraft(storageKey);
    setText(latest.current);
  }, [storageKey, flush]);

  useEffect(() => () => flush(), [flush]);

  const setValue = useCallback((value: string) => {
    setText(value);
    latest.current = value;
    if (timer.current !== null) clearTimeout(timer.current);
    timer.current = setTimeout(flush, debounceMillis);
  }, [debounceMillis, flush]);

  const clear = useCallback(() => {
    setText('');
    latest.current = '';
    flush();
  }, [flush]);

  return { text, setText: setValue, clear };
}