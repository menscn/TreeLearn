import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { createLearningService } = require('../learning-service.cjs');
const secrets = { isAsyncEncryptionAvailable: async () => true, encryptStringAsync: async value => Buffer.from(value), decryptStringAsync: async value => ({ result: Buffer.from(value).toString(), shouldReEncrypt: false }) };
const uid = number => `${number.toString(16).padStart(8, '0')}-0000-4000-8000-000000000000`;
const answer = value => new Response(JSON.stringify({ choices: [{ message: { content: value } }] }), { status: 200 });
const hash = file => createHash('sha256').update(readFileSync(file)).digest('hex');

function fixtureV2(database) {
  const db = new DatabaseSync(database);
  db.exec(`PRAGMA foreign_keys=ON; BEGIN;
    CREATE TABLE configuration (id INTEGER PRIMARY KEY CHECK(id=1), base_url TEXT NOT NULL, model TEXT NOT NULL, encrypted_key BLOB, default_persona TEXT NOT NULL DEFAULT 'default');
    CREATE TABLE topics (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, persona TEXT NOT NULL DEFAULT 'default');
    CREATE TABLE messages (id TEXT PRIMARY KEY, topic_id TEXT NOT NULL REFERENCES topics(id), role TEXT NOT NULL CHECK(role IN ('user','assistant')), content TEXT NOT NULL, created_at INTEGER NOT NULL, status TEXT NOT NULL, request_id TEXT, draft_scope TEXT, persona TEXT NOT NULL DEFAULT 'default');
    CREATE UNIQUE INDEX message_request ON messages(request_id) WHERE role='user';
    CREATE TABLE drafts (scope TEXT PRIMARY KEY, text TEXT NOT NULL);
    INSERT INTO configuration VALUES(1,'https://example.test','model',X'00010203','strict-mentor');
    INSERT INTO topics VALUES('${uid(1)}','原主题',100,200,'late-night');
    INSERT INTO messages VALUES('${uid(2)}','${uid(1)}','user','旧问题',100,'complete','${uid(3)}','${uid(1)}','late-night');
    INSERT INTO messages VALUES('${uid(4)}','${uid(1)}','assistant','旧答案',101,'complete','${uid(3)}',NULL,'late-night');
    INSERT INTO drafts VALUES('${uid(1)}','旧根草稿');
    PRAGMA application_id=1414287921; PRAGMA user_version=2; COMMIT;`);
  db.close();
}

test('v2 migrates to a root branch preserving all original data and message order', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-v2-')); const database = path.join(root, 'learning.sqlite'); let service;
  try {
    fixtureV2(database);
    service = createLearningService({ database, secrets });
    const state = await service.getState(); const topic = state.topics[0];
    assert.equal(topic.rootBranchId, uid(1));
    assert.equal(topic.branches.length, 1);
    assert.equal(topic.branches[0].id, uid(1));
    assert.equal(topic.branches[0].parentBranchId, null);
    assert.deepEqual(topic.branches[0].messages.map(m => [m.id,m.content,m.branchId,m.persona]), [[uid(2),'旧问题',uid(1),'late-night'],[uid(4),'旧答案',uid(1),'late-night']]);
    assert.equal(state.drafts[uid(1)], '旧根草稿');
    assert.equal(state.settings.defaultPersona, 'strict-mentor');
    service.close(); service = null;
    const db = new DatabaseSync(database, { readOnly: true });
    try { assert.equal(db.prepare('PRAGMA user_version').get().user_version, 4); assert.equal(Buffer.from(db.prepare('SELECT encrypted_key FROM configuration').get().encrypted_key).toString('hex'), '00010203'); assert.equal(db.prepare('PRAGMA foreign_key_check').all().length, 0); assert.equal(db.prepare('PRAGMA integrity_check').get().integrity_check, 'ok'); } finally { db.close(); }
    service = createLearningService({ database, secrets });
    assert.equal((await service.getState()).topics[0].branches[0].messages.length, 2);
  } finally { if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('branch freezes complete source context, supports nested branches and rejects tampering', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-context-')); const database = path.join(root, 'learning.sqlite');
  let service, observer; const requests = []; let sequence = 0;
  try {
    service = createLearningService({ database, secrets, tutorPrompt: 'synthetic baseline', transport: async (_url, options) => { requests.push(JSON.parse(options.body).messages); return answer(`回答${++sequence}`); } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '根问题', requestId: uid(10) })).topicId;
    let rootBranch = (await service.getState()).topics[0].branches[0];
    const source = rootBranch.messages.find(m => m.role === 'assistant');
    const input = { topicId, parentBranchId: topicId, sourceMessageId: source.id, requestId: uid(11) };
    const branchId = (await service.createBranch(input)).branchId;
    assert.equal((await service.createBranch(input)).branchId, branchId);
    await assert.rejects(service.createBranch({ ...input, sourceMessageId: rootBranch.messages[0].id }), /REQUEST_INVALID/);
    await service.saveDraft({ topicId, branchId, text: '分支草稿' });
    await service.sendMessage({ topicId, text: '后来的父问题', requestId: uid(12) });
    await service.sendMessage({ topicId, branchId, text: '分支问题', requestId: uid(13) });
    const childPrompt = requests.at(-1).filter(m => m.role !== 'system').map(m => m.content);
    assert.deepEqual(childPrompt, ['根问题','回答1','分支问题']);
    let state = await service.getState(); const child = state.topics[0].branches.find(b => b.id === branchId);
    const childAnswer = child.messages.find(m => m.role === 'assistant');
    const grandId = (await service.createBranch({ topicId, parentBranchId: branchId, sourceMessageId: childAnswer.id, requestId: uid(14) })).branchId;
    await service.sendMessage({ topicId, branchId: grandId, text: '孙分支问题', requestId: uid(15) });
    assert.deepEqual(requests.at(-1).filter(m => m.role !== 'system').map(m => m.content), ['根问题','回答1','分支问题','回答3','孙分支问题']);
    observer = new DatabaseSync(database);
    observer.prepare('UPDATE messages SET content=? WHERE id=?').run('被修改的来源', source.id);
    observer.close(); observer = null;
    await assert.rejects(service.sendMessage({ topicId, branchId, text: '不得发送', requestId: uid(16) }), /CONTEXT_CHANGED/);
    assert.equal(requests.length, 4);
    state = await service.getState();
    assert.equal(state.topics[0].branches.find(b => b.id === branchId).messages.some(m => m.content === '不得发送'), false);
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('branch creation rejects non-complete, wrong-parent and conflicting requests', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-invalid-')); const database = path.join(root, 'learning.sqlite');
  let service, release, started, pending;
  const began = new Promise(resolve => { started = resolve; });
  const gate = new Promise(resolve => { release = resolve; });
  let block = false;
  try {
    service = createLearningService({ database, secrets, transport: async () => {
      if (block) { started(); await gate; }
      return answer('合成回答');
    } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '问题', requestId: uid(30) })).topicId;
    const otherId = (await service.sendMessage({ text: '其他', requestId: uid(31) })).topicId;
    let state = await service.getState(); const rootBranch = state.topics.find(t => t.id === topicId).branches[0];
    const source = rootBranch.messages.find(m => m.role === 'assistant');
    await assert.rejects(service.createBranch({ topicId: otherId, parentBranchId: otherId, sourceMessageId: source.id, requestId: uid(32) }), /REQUEST_INVALID/);
    const input = { topicId, parentBranchId: topicId, sourceMessageId: source.id, requestId: uid(33) };
    const child = (await service.createBranch(input)).branchId;
    await assert.rejects(service.createBranch({ ...input, parentBranchId: child }), /REQUEST_INVALID/);
    await assert.rejects(service.createBranch({ ...input, requestId: uid(34), quote: '合成' }), /REQUEST_INVALID/);
    block = true;
    pending = service.sendMessage({ topicId, text: '进行中', requestId: uid(35) });
    await began;
    await assert.rejects(service.createBranch({ ...input, requestId: uid(36) }), /BUSY/);
    await assert.rejects(service.deleteTopic({ topicId }), /BUSY/);
    release(); await pending; pending = null;
    assert.equal((await service.getState()).topics.find(t => t.id === topicId).branches.length, 2);
  } finally { if (release) release(); if (pending) await pending.catch(() => {}); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('branch retry and cancellation preserve independent drafts and topic deletion removes its tree', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-retry-')); const database = path.join(root, 'learning.sqlite');
  let service, ready, aborted = false, active, hold = false; const began = new Promise(resolve => { ready = resolve; });
  try {
    service = createLearningService({ database, secrets, transport: async (_url, options) => {
      if (!hold) return answer('完整回答');
      return new Response(new ReadableStream({ start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"部分回答"}}]}\n\n'));
        options.signal.addEventListener('abort', () => { aborted = true; controller.error(new Error('cancelled')); });
      } }), { headers: { 'content-type': 'text/event-stream' } });
    } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '根问题', requestId: uid(40) })).topicId;
    let state = await service.getState(); const source = state.topics[0].messages.find(m => m.role === 'assistant');
    const branchId = (await service.createBranch({ topicId, parentBranchId: topicId, sourceMessageId: source.id, requestId: uid(41) })).branchId;
    await service.saveDraft({ topicId, text: '根草稿' });
    await service.saveDraft({ topicId, branchId, text: '分支草稿' });
    await service.saveDraft({ text: '首页草稿' });
    hold = true;
    active = service.sendMessage({ topicId, branchId, text: '分支草稿', requestId: uid(42) }, ready);
    await began;
    await assert.rejects(service.deleteTopic({ topicId }), /BUSY/);
    assert.deepEqual(await service.cancelMessage({ requestId: uid(42) }), { cancelled: true });
    await assert.rejects(active, /REQUEST_CANCELLED/); active = null;
    assert.equal(aborted, true);
    state = await service.getState();
    assert.equal(state.drafts[branchId], '分支草稿'); assert.equal(state.drafts[topicId], '根草稿');
    assert.deepEqual(state.topics[0].branches.find(b => b.id === branchId).messages.map(m => m.status), ['cancelled','cancelled']);
    hold = false;
    await service.retryMessage({ requestId: uid(42) });
    state = await service.getState();
    assert.equal(state.drafts[branchId], undefined); assert.equal(state.drafts[topicId], '根草稿');
    assert.deepEqual(state.topics[0].branches.find(b => b.id === branchId).messages.map(m => m.status), ['complete','cancelled','complete']);
    await service.deleteTopic({ topicId });
    state = await service.getState(); assert.equal(state.topics.length, 0); assert.equal(state.drafts.home, '首页草稿'); assert.equal(state.settings.hasApiKey, true);
  } finally { if (active) { await service.cancelMessage({ requestId: uid(42) }); await active.catch(() => {}); } if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('oversized context is rejected without sending or discarding the draft', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-limit-')); const database = path.join(root, 'learning.sqlite');
  let service, observer, requests = 0;
  try {
    service = createLearningService({ database, secrets, transport: async () => { requests++; return answer('合成回答'); } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '根问题', requestId: uid(50) })).topicId;
    const source = (await service.getState()).topics[0].messages.find(m => m.role === 'assistant');
    observer = new DatabaseSync(database);
    const insert = observer.prepare("INSERT INTO messages(id,topic_id,branch_id,role,content,created_at,status,persona) VALUES(?,?,?,'assistant',?,?,'complete','default')");
    for (let i = 1000; i < 1201; i++) insert.run(uid(i), topicId, topicId, '合成历史', i);
    observer.close(); observer = null;
    const latest = (await service.getState()).topics[0].messages.at(-1);
    await assert.rejects(service.createBranch({ topicId, parentBranchId: topicId, sourceMessageId: latest.id, requestId: uid(51) }), /CONTEXT_TOO_LARGE/);
    await service.saveDraft({ topicId, text: '不能丢失的草稿' });
    await assert.rejects(service.sendMessage({ topicId, text: '不能丢失的草稿', requestId: uid(52) }), /CONTEXT_TOO_LARGE/);
    assert.equal(requests, 1);
    const state = await service.getState(); assert.equal(state.drafts[topicId], '不能丢失的草稿'); assert.equal(state.topics[0].branches.length, 1);
    assert.equal(state.topics[0].messages.some(m => m.content === '不能丢失的草稿'), false);
    assert.ok(source.id);
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('snapshot rejects same-topic sibling message contamination even when its digest is recomputed', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-lineage-')); const database = path.join(root, 'learning.sqlite');
  let service, observer, requests = 0;
  try {
    service = createLearningService({ database, secrets, transport: async () => answer(`合成答案${++requests}`) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '根问题', requestId: uid(60) })).topicId;
    const rootAnswer = (await service.getState()).topics[0].messages.find(m => m.role === 'assistant');
    const a = (await service.createBranch({ topicId, parentBranchId: topicId, sourceMessageId: rootAnswer.id, requestId: uid(61) })).branchId;
    const b = (await service.createBranch({ topicId, parentBranchId: topicId, sourceMessageId: rootAnswer.id, requestId: uid(62) })).branchId;
    await service.sendMessage({ topicId, branchId: b, text: '兄弟支问题', requestId: uid(63) });
    observer = new DatabaseSync(database);
    const sibling = observer.prepare("SELECT id,role,content FROM messages WHERE branch_id=? AND role='assistant'").get(b);
    const branch = observer.prepare('SELECT snapshot_json FROM branches WHERE id=?').get(a);
    const refs = JSON.parse(branch.snapshot_json);
    refs.unshift({ id: sibling.id, role: sibling.role, digest: createHash('sha256').update(sibling.content).digest('hex') });
    const snapshot = JSON.stringify(refs);
    observer.prepare('UPDATE branches SET snapshot_json=?,snapshot_digest=? WHERE id=?').run(snapshot, createHash('sha256').update(snapshot).digest('hex'), a);
    observer.close(); observer = null;
    await assert.rejects(service.sendMessage({ topicId, branchId: a, text: '不能混支', requestId: uid(64) }), /CONTEXT_CHANGED/);
    assert.equal(requests, 2);
    assert.equal((await service.getState()).topics[0].branches.find(branch => branch.id === a).messages.length, 0);
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('snapshot rejects omission of prior complete messages when its digest is recomputed', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-omission-')); const database = path.join(root, 'learning.sqlite');
  let service, observer, requests = 0;
  try {
    service = createLearningService({ database, secrets, transport: async () => answer(`合成答案${++requests}`) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '第一问题', requestId: uid(70) })).topicId;
    await service.sendMessage({ topicId, text: '第二问题', requestId: uid(71) });
    const source = (await service.getState()).topics[0].messages.at(-1);
    const branchId = (await service.createBranch({ topicId, parentBranchId: topicId, sourceMessageId: source.id, requestId: uid(72) })).branchId;
    observer = new DatabaseSync(database);
    const branch = observer.prepare('SELECT snapshot_json FROM branches WHERE id=?').get(branchId);
    const refs = JSON.parse(branch.snapshot_json);
    refs.splice(0, 1);
    const snapshot = JSON.stringify(refs);
    observer.prepare('UPDATE branches SET snapshot_json=?,snapshot_digest=? WHERE id=?').run(snapshot, createHash('sha256').update(snapshot).digest('hex'), branchId);
    observer.close(); observer = null;
    await assert.rejects(service.sendMessage({ topicId, branchId, text: '不可遗漏前文', requestId: uid(73) }), /CONTEXT_CHANGED/);
    assert.equal(requests, 2);
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('failed earlier turns cannot be retried after the source and later completed turns stay excluded', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-terminal-')); const database = path.join(root, 'learning.sqlite');
  let service, observer, requests = 0, allow = false; const messagesSent = [];
  try {
    service = createLearningService({ database, secrets, transport: async (_url, options) => {
      requests++; messagesSent.push(JSON.parse(options.body).messages);
      return allow ? answer(`合成回答${requests}`) : new Response('synthetic', { status: 500 });
    } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await assert.rejects(service.sendMessage({ text: '失败的旧问题', requestId: uid(80) }), /REQUEST_FAILED/);
    const topicId = (await service.getState()).topics[0].id;
    service.close(); service = null;
    observer = new DatabaseSync(database);
    observer.prepare("UPDATE messages SET status='pending' WHERE request_id=?").run(uid(80));
    observer.close(); observer = null;
    service = createLearningService({ database, secrets, transport: async (_url, options) => { requests++; messagesSent.push(JSON.parse(options.body).messages); return answer(`合成回答${requests}`); } });
    assert.equal((await service.getState()).topics[0].messages[0].status, 'interrupted');
    allow = true;
    await service.sendMessage({ topicId, text: '分叉来源问题', requestId: uid(81) });
    const source = (await service.getState()).topics[0].messages.at(-1);
    const branchId = (await service.createBranch({ topicId, parentBranchId: topicId, sourceMessageId: source.id, requestId: uid(82) })).branchId;
    await assert.rejects(service.retryMessage({ requestId: uid(80) }), /STALE_RETRY/);
    await service.sendMessage({ topicId, text: '分叉后的父问题', requestId: uid(83) });
    await service.sendMessage({ topicId, branchId, text: '分叉问题', requestId: uid(84) });
    assert.deepEqual(messagesSent.at(-1).filter(m => m.role !== 'system').map(m => m.content), ['分叉来源问题','合成回答2','分叉问题']);
    observer = new DatabaseSync(database);
    observer.prepare("UPDATE messages SET status='complete' WHERE request_id=? AND role='user'").run(uid(80));
    observer.close(); observer = null;
    await assert.rejects(service.sendMessage({ topicId, branchId, text: '禁止追问', requestId: uid(85) }), /CONTEXT_CHANGED/);
    assert.equal(requests, 4);
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('v2 migration failure rolls back, unknown schema remains untouched', () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-branch-rollback-')); const database = path.join(root, 'learning.sqlite');
  const original = DatabaseSync.prototype.exec; let injected = false, unexpected;
  try {
    fixtureV2(database); const before = hash(database);
    DatabaseSync.prototype.exec = function(sql) {
      if (sql.includes('ALTER TABLE messages ADD COLUMN branch_id')) { injected = true; throw new Error('synthetic failure'); }
      return original.call(this, sql);
    };
    assert.throws(() => { unexpected = createLearningService({ database, secrets }); }, /STORAGE_ERROR/);
    DatabaseSync.prototype.exec = original;
    assert.equal(injected, true); assert.equal(hash(database), before);
    const db = new DatabaseSync(database); db.exec('PRAGMA application_id=0'); db.close();
    const foreign = hash(database);
    assert.throws(() => createLearningService({ database, secrets }), /STORAGE_ERROR/);
    assert.equal(hash(database), foreign);
  } finally { DatabaseSync.prototype.exec = original; if (unexpected) unexpected.close(); rmSync(root, { recursive: true, force: true }); }
});
