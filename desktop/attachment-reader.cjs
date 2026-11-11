const fs = require('node:fs/promises');
const path = require('node:path');
const { MAX_ATTACHMENT_BYTES, validateAttachment } = require('./attachment-data.cjs');

async function readSelectedAttachment(filePath) {
  const name = typeof filePath === 'string' ? path.basename(filePath) : '';
  // Validate the name before accessing the selected path.
  validateAttachment(name, '');
  let handle;
  try {
    const info = await fs.lstat(filePath);
    if (!info.isFile() || info.isSymbolicLink()) throw new Error('ATTACHMENT_INVALID');
    handle = await fs.open(filePath, 'r');
    const initial = await handle.stat();
    if (!initial.isFile()) throw new Error('ATTACHMENT_INVALID');
    if (info.ino && initial.ino && (info.ino !== initial.ino || info.dev !== initial.dev)) throw new Error('ATTACHMENT_READ_FAILED');
    if (initial.size > MAX_ATTACHMENT_BYTES) throw new Error('ATTACHMENT_TOO_LARGE');
    const buffer = Buffer.alloc(MAX_ATTACHMENT_BYTES + 1);
    let offset = 0;
    while (offset < buffer.length) {
      const { bytesRead } = await handle.read(buffer, offset, buffer.length - offset, null);
      if (!bytesRead) break;
      offset += bytesRead;
    }
    const final = await handle.stat();
    if (offset > MAX_ATTACHMENT_BYTES) throw new Error('ATTACHMENT_TOO_LARGE');
    if (offset !== initial.size || final.size !== initial.size || final.mtimeMs !== initial.mtimeMs || final.ctimeMs !== initial.ctimeMs) throw new Error('ATTACHMENT_READ_FAILED');
    let content;
    try { content = new TextDecoder('utf-8', { fatal: true, ignoreBOM: true }).decode(buffer.subarray(0, offset)); }
    catch { throw new Error('ATTACHMENT_ENCODING'); }
    return validateAttachment(name, content);
  } catch (error) {
    if (['ATTACHMENT_INVALID','ATTACHMENT_TOO_LARGE','ATTACHMENT_ENCODING','ATTACHMENT_READ_FAILED'].includes(error?.message)) throw error;
    throw new Error('ATTACHMENT_READ_FAILED');
  } finally { if (handle) await handle.close(); }
}
module.exports = { readSelectedAttachment };
