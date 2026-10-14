// 分叉锚点 hash：与共享域 ContextSnapshotCodec.digestOf 同一算法——quote 的 UTF-8
// 字节小写十六进制 SHA-256（web/shared/domain/src/main/java/com/treelearn/core/domain/
// ContextSnapshotCodec.java:55；Android 侧 ChatWiring.fork 亦走该 codec）。
// crypto.subtle 只在安全上下文（https 或 localhost）可用；不可用时调用方必须拒绝
// 选文分叉（workspace.fork.hashUnavailable），不得退回到与 Android 不一致的摘要。
export async function sha256Hex(text: string): Promise<string> {
  const subtle = globalThis.crypto?.subtle;
  if (subtle === undefined) throw new Error('crypto.subtle unavailable (insecure context)');
  const bytes = new TextEncoder().encode(text);
  const digest = await subtle.digest('SHA-256', bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, '0')).join('');
}
