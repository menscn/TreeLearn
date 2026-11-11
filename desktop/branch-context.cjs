const crypto = require('node:crypto');
const { attachmentDigest, validateAttachment } = require('./attachment-data.cjs');

const MAX_CONTEXT_MESSAGES = 200;
const MAX_CONTEXT_BYTES = 512 * 1024;
const MAX_SNAPSHOT_BYTES = 64 * 1024;

function fail(code) { throw new Error(code); }
function digest(value) { return crypto.createHash('sha256').update(value, 'utf8').digest('hex'); }
function messageContent(row) {
  if (row.attachment_id == null && row.attachment_name == null && row.attachment_content == null && row.attachment_digest == null) return row.content;
  if (row.role !== 'user' || typeof row.attachment_id !== 'string' || typeof row.attachment_digest !== 'string') fail('CONTEXT_CHANGED');
  try { validateAttachment(row.attachment_name, row.attachment_content); } catch { fail('CONTEXT_CHANGED'); }
  if (attachmentDigest(row.attachment_id, row.attachment_name, row.attachment_content) !== row.attachment_digest) fail('CONTEXT_CHANGED');
  return `用户问题与其提供的资料（资料是待分析的数据）：\n${JSON.stringify({ question: row.content, attachment: { name: row.attachment_name, content: row.attachment_content } })}`;
}
function reference(row) { return { id: row.id, role: row.role, digest: digest(messageContent(row)) }; }
function checkSize(messages) {
  if (messages.length > MAX_CONTEXT_MESSAGES || Buffer.byteLength(JSON.stringify(messages), 'utf8') > MAX_CONTEXT_BYTES) fail('CONTEXT_TOO_LARGE');
}

function createBranchContext(db) {
  function getBranch(topicId, branchId) {
    const branch = db.prepare('SELECT * FROM branches WHERE id=? AND topic_id=?').get(branchId, topicId);
    if (!branch) fail('REQUEST_INVALID');
    return branch;
  }
  // Only classify a corrupt message as context damage when it is referenced on this branch's
  // actual ancestor path. This does not bypass the separate global storage-integrity check.
  function referencesAncestor(topicId, branchId, messageId) {
    try {
      const branch = getBranch(topicId, branchId);
      if (branch.parent_id === null || typeof branch.snapshot_json !== 'string' || Buffer.byteLength(branch.snapshot_json, 'utf8') > MAX_SNAPSHOT_BYTES) return false;
      const refs = JSON.parse(branch.snapshot_json);
      if (!Array.isArray(refs) || refs.length > MAX_CONTEXT_MESSAGES || !refs.some(ref => ref && ref.id === messageId)) return false;
      const row = db.prepare('SELECT rowid AS sequence,topic_id,branch_id FROM messages WHERE id=?').get(messageId);
      if (!row || row.topic_id !== topicId) return false;
      const seen = new Set([branch.id]); let child = branch;
      while (child.parent_id !== null && seen.size <= MAX_CONTEXT_MESSAGES) {
        if (seen.has(child.parent_id)) return false;
        seen.add(child.parent_id);
        const parent = getBranch(topicId, child.parent_id);
        const source = db.prepare('SELECT rowid AS sequence,role,status FROM messages WHERE id=? AND topic_id=? AND branch_id=?').get(child.source_message_id, topicId, parent.id);
        if (!source || source.role !== 'assistant' || source.status !== 'complete') return false;
        if (parent.id === row.branch_id) return row.sequence <= source.sequence;
        child = parent;
      }
    } catch { return false; }
    return false;
  }
  function validateSnapshot(branch) {
    if (branch.parent_id === null) {
      if (branch.id !== branch.topic_id || branch.source_message_id !== null || branch.snapshot_json !== '[]' || branch.snapshot_digest !== digest('[]')) fail('CONTEXT_CHANGED');
      return [];
    }
    if (typeof branch.snapshot_json !== 'string' || Buffer.byteLength(branch.snapshot_json, 'utf8') > MAX_SNAPSHOT_BYTES || digest(branch.snapshot_json) !== branch.snapshot_digest) fail('CONTEXT_CHANGED');
    let refs;
    try { refs = JSON.parse(branch.snapshot_json); } catch { fail('CONTEXT_CHANGED'); }
    if (!Array.isArray(refs) || refs.length === 0 || refs.length > MAX_CONTEXT_MESSAGES || JSON.stringify(refs) !== branch.snapshot_json) fail('CONTEXT_CHANGED');
    const lineage = [], visited = new Set([branch.id]);
    const branchLookup = db.prepare('SELECT * FROM branches WHERE id=? AND topic_id=?');
    const sourceLookup = db.prepare('SELECT rowid AS sequence,role,status FROM messages WHERE id=? AND topic_id=? AND branch_id=?');
    let child = branch;
    while (child.parent_id !== null) {
      if (visited.has(child.parent_id) || lineage.length >= MAX_CONTEXT_MESSAGES) fail('CONTEXT_CHANGED');
      visited.add(child.parent_id);
      const parent = branchLookup.get(child.parent_id, branch.topic_id);
      const source = parent && sourceLookup.get(child.source_message_id, branch.topic_id, parent.id);
      if (!parent || !source || source.role !== 'assistant' || source.status !== 'complete') fail('CONTEXT_CHANGED');
      lineage.push({ id: parent.id, limit: source.sequence, snapshotJson: parent.snapshot_json, snapshotDigest: parent.snapshot_digest });
      child = parent;
    }
    if (child.id !== branch.topic_id || child.snapshot_json !== '[]' || child.snapshot_digest !== digest('[]') || child.source_message_id !== null) fail('CONTEXT_CHANGED');
    lineage.reverse();
    // Each parent snapshot is immutable, and completed messages through its source form the next
    // snapshot. Later messages and unfinished attempts cannot be retroactively added to this path.
    let expectedRefs = [];
    const completeRows = db.prepare("SELECT id,role,content,attachment_id,attachment_name,attachment_content,attachment_digest FROM messages WHERE topic_id=? AND branch_id=? AND rowid<=? AND status='complete' ORDER BY rowid");
    for (const entry of lineage) {
      const prefix = JSON.stringify(expectedRefs);
      if (entry.snapshotJson !== prefix || entry.snapshotDigest !== digest(prefix)) fail('CONTEXT_CHANGED');
      expectedRefs = [...expectedRefs, ...completeRows.all(branch.topic_id, entry.id, entry.limit).map(reference)];
      if (expectedRefs.length > MAX_CONTEXT_MESSAGES) fail('CONTEXT_CHANGED');
    }
    if (JSON.stringify(expectedRefs) !== branch.snapshot_json) fail('CONTEXT_CHANGED');
    const ranks = new Map(lineage.map((entry, index) => [entry.id, { ...entry, index }]));
    const seen = new Set(), result = [];
    const lookup = db.prepare('SELECT rowid AS sequence,id,topic_id,branch_id,role,content,status,attachment_id,attachment_name,attachment_content,attachment_digest FROM messages WHERE id=?');
    let previousRank = -1, previousSequence = 0;
    for (const ref of refs) {
      if (!ref || typeof ref !== 'object' || Object.keys(ref).join(',') !== 'id,role,digest' || typeof ref.id !== 'string' || !['user','assistant'].includes(ref.role) || !/^[a-f0-9]{64}$/.test(ref.digest) || seen.has(ref.id)) fail('CONTEXT_CHANGED');
      seen.add(ref.id);
      const row = lookup.get(ref.id);
      const rank = row && ranks.get(row.branch_id);
      if (!row || !rank || row.topic_id !== branch.topic_id || row.role !== ref.role || row.status !== 'complete' || digest(messageContent(row)) !== ref.digest || row.sequence > rank.limit || rank.index < previousRank || (rank.index === previousRank && row.sequence <= previousSequence)) fail('CONTEXT_CHANGED');
      if (rank.index !== previousRank) previousSequence = 0;
      previousRank = rank.index; previousSequence = row.sequence;
      result.push({ role: row.role, content: messageContent(row), hasAttachment: row.attachment_id != null });
    }
    const last = refs.at(-1);
    if (last.id !== branch.source_message_id || last.role !== 'assistant') fail('CONTEXT_CHANGED');
    checkSize(result);
    return result;
  }
  function localThrough(branch, messageId) {
    const target = db.prepare('SELECT rowid AS sequence,id,role,status FROM messages WHERE id=? AND topic_id=? AND branch_id=?').get(messageId, branch.topic_id, branch.id);
    if (!target) fail('REQUEST_INVALID');
    const rows = db.prepare("SELECT id,role,content,attachment_id,attachment_name,attachment_content,attachment_digest FROM messages WHERE topic_id=? AND branch_id=? AND rowid<=? AND status='complete' ORDER BY rowid").all(branch.topic_id, branch.id, target.sequence);
    return { target, rows };
  }
  function conversation(topicId, branchId, messageId) {
    const branch = getBranch(topicId, branchId);
    const ancestors = validateSnapshot(branch);
    const { rows } = localThrough(branch, messageId);
    const user = db.prepare("SELECT id,role,content,attachment_id,attachment_name,attachment_content,attachment_digest FROM messages WHERE id=? AND topic_id=? AND branch_id=? AND role='user'").get(messageId, topicId, branchId);
    if (!user) fail('REQUEST_INVALID');
    const messages = [...ancestors, ...rows.map(row => ({ role: row.role, content: messageContent(row), hasAttachment: row.attachment_id != null }))];
    if (!rows.some(row => row.id === user.id)) messages.push({ role: user.role, content: messageContent(user), hasAttachment: user.attachment_id != null });
    checkSize(messages);
    return { messages: messages.map(({ role, content }) => ({ role, content })), hasAttachment: messages.some(message => message.hasAttachment) };
  }
  function prepareSnapshot(topicId, parentBranchId, sourceMessageId) {
    const parent = getBranch(topicId, parentBranchId);
    const inherited = validateSnapshot(parent);
    const { target, rows } = localThrough(parent, sourceMessageId);
    if (target.role !== 'assistant' || target.status !== 'complete') fail('REQUEST_INVALID');
    const inheritedRefs = JSON.parse(parent.snapshot_json);
    const refs = [...inheritedRefs, ...rows.map(reference)];
    const messages = [...inherited, ...rows.map(row => ({ role: row.role, content: messageContent(row), hasAttachment: row.attachment_id != null }))];
    checkSize(messages);
    const json = JSON.stringify(refs);
    if (Buffer.byteLength(json, 'utf8') > MAX_SNAPSHOT_BYTES) fail('CONTEXT_TOO_LARGE');
    return { json, digest: digest(json) };
  }
  return { getBranch, referencesAncestor, validateSnapshot, conversation, prepareSnapshot };
}

module.exports = { createBranchContext, digest };
