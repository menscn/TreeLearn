/**
 * T-053 语音输入：Web SpeechRecognition 封装。
 *
 * 能力探测驱动 UI（`SpeechRecognition` 或 `webkitSpeechRecognition`，Chrome 系）；
 * `startListening` 把中间/最终转写经回调交出，错误一律静默结束（语音是增益能力，
 * 不阻塞键盘输入主路径）。
 */

type RecognitionHandle = { stop: () => void };

interface SpeechRecognitionLike {
  lang: string;
  interimResults: boolean;
  continuous: boolean;
  onresult: ((event: { resultIndex: number; results: ArrayLike<ArrayLike<{ transcript: string }> & { isFinal: boolean }> }) => void) | null;
  onerror: (() => void) | null;
  onend: (() => void) | null;
  start: () => void;
  stop: () => void;
}

function recognitionCtor(): (new () => SpeechRecognitionLike) | null {
  if (typeof window === 'undefined') return null;
  const w = window as unknown as Record<string, unknown>;
  const ctor = (w.SpeechRecognition ?? w.webkitSpeechRecognition) as
    | (new () => SpeechRecognitionLike) | undefined;
  return typeof ctor === 'function' ? ctor : null;
}

export function voiceInputAvailable(): boolean {
  return recognitionCtor() !== null;
}

/** 开始听写；返回停止句柄。`onFinal` 收最终句（追加用），`onInterim` 收中间态（可选）。 */
export function startListening(options: {
  onFinal: (text: string) => void;
  onInterim?: (text: string) => void;
  onError?: () => void;
}): RecognitionHandle | null {
  const Ctor = recognitionCtor();
  if (Ctor === null) return null;
  const recognition = new Ctor();
  recognition.lang = document.documentElement.lang || 'zh-CN';
  recognition.interimResults = true;
  recognition.continuous = false;
  recognition.onresult = (event) => {
    let interim = '';
    for (let i = event.resultIndex; i < event.results.length; i += 1) {
      const result = event.results[i];
      const transcript = result[0]?.transcript ?? '';
      if (result.isFinal) {
        if (transcript.trim() !== '') options.onFinal(transcript);
      } else {
        interim += transcript;
      }
    }
    if (interim !== '' && options.onInterim) options.onInterim(interim);
  };
  recognition.onerror = () => options.onError?.();
  // 识别自然结束；句柄失效由调用方置灰。
  recognition.onend = () => {};
  recognition.start();
  return { stop: () => recognition.stop() };
}
