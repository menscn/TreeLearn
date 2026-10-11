/**
 * T-053 朗读：Web SpeechSynthesis 封装。
 *
 * 能力探测驱动 UI（不支持时按钮不渲染）；同一时刻只保留一段朗读，再次点击即停止。
 * `window.speechSynthesis` 在测试环境不存在——所有入口都先问 `speechAvailable()`。
 */

export function speechAvailable(): boolean {
  return typeof window !== 'undefined' && 'speechSynthesis' in window
    && typeof window.SpeechSynthesisUtterance === 'function';
}

/** 朗读一段文本；再次调用同一句柄前先停掉上一段（同一时刻最多一段）。 */
export function speak(text: string): void {
  if (!speechAvailable() || text.trim() === '') return;
  window.speechSynthesis.cancel();
  const utterance = new window.SpeechSynthesisUtterance(text);
  utterance.lang = document.documentElement.lang || 'zh-CN';
  window.speechSynthesis.speak(utterance);
}

export function stopSpeaking(): void {
  if (speechAvailable()) window.speechSynthesis.cancel();
}
