const path = require('node:path');
const crypto = require('node:crypto');
const MAX_ATTACHMENT_BYTES = 128 * 1024;
const MAX_ATTACHMENT_CHARS = 20000;
const ATTACHMENT_ID = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
function fail(code) { throw new Error(code); }
function validateAttachment(name, content) {
  if (typeof name !== 'string' || !name || name.length > 255 || /[\\/\u0000-\u001f\u007f]/.test(name) || !['.txt', '.md'].includes(path.extname(name).toLowerCase())) fail('ATTACHMENT_INVALID');
  if (typeof content !== 'string' || content.includes('\0') || (typeof content.isWellFormed === 'function' && !content.isWellFormed())) fail('ATTACHMENT_ENCODING');
  if (content.length > MAX_ATTACHMENT_CHARS || Buffer.byteLength(content, 'utf8') > MAX_ATTACHMENT_BYTES) fail('ATTACHMENT_TOO_LARGE');
  return { name, content, size: Buffer.byteLength(content, 'utf8') };
}
function attachmentDigest(id, name, content) { return crypto.createHash('sha256').update(JSON.stringify([id, name, content]), 'utf8').digest('hex'); }
function assertStoredAttachments(db) {
  const drafts = db.prepare('SELECT attachment_id,attachment_name,attachment_content FROM drafts WHERE attachment_id IS NOT NULL OR attachment_name IS NOT NULL OR attachment_content IS NOT NULL').iterate();
  for (const row of drafts) {
    try {
      if (typeof row.attachment_id !== 'string' || !ATTACHMENT_ID.test(row.attachment_id)) fail('STORAGE_ERROR');
      validateAttachment(row.attachment_name, row.attachment_content);
    } catch { fail('STORAGE_ERROR'); }
  }
  const messages = db.prepare('SELECT id,role,attachment_id,attachment_name,attachment_content,attachment_digest FROM messages WHERE attachment_id IS NOT NULL OR attachment_name IS NOT NULL OR attachment_content IS NOT NULL OR attachment_digest IS NOT NULL').iterate();
  for (const row of messages) {
    try {
      if (row.role !== 'user' || typeof row.attachment_id !== 'string' || !ATTACHMENT_ID.test(row.attachment_id) || typeof row.attachment_digest !== 'string') fail('STORAGE_ERROR');
      validateAttachment(row.attachment_name, row.attachment_content);
      if (row.attachment_digest !== attachmentDigest(row.attachment_id, row.attachment_name, row.attachment_content)) fail('STORAGE_ERROR');
    } catch { const error = new Error('STORAGE_ERROR'); error.attachmentMessageId = row.id; throw error; }
  }
}
function metadata(id, name, content, preview = false) {
  const value = validateAttachment(name, content);
  return preview ? { id, name, size: value.size, preview: content } : { id, name, size: value.size };
}
module.exports = { MAX_ATTACHMENT_BYTES, validateAttachment, attachmentDigest, metadata, assertStoredAttachments };
