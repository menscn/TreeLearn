import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { mkdtempSync, readFileSync, rmSync } from 'node:fs';
import { createHash } from 'node:crypto';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { createLearningService } = require('../learning-service.cjs');
const secrets = {
  isAsyncEncryptionAvailable: async () => true,
  encryptStringAsync: async value => Buffer.from(`synthetic:${value}`),
  decryptStringAsync: async value => ({ result: Buffer.from(value).toString().slice(10), shouldReEncrypt: false })
};
const personas = {
  default: '', 'hand-holding': 'synthetic hand-holding', 'strict-mentor': 'synthetic strict-mentor',
  'study-buddy': 'synthetic study-buddy', 'sarcastic-friend': 'synthetic sarcastic-friend',
  'late-night': 'synthetic late-night', 'learning-repair': 'synthetic learning-repair'
};
const TOPIC = '138a80af-c47b-4846-8e19-5a217dc117f3';
const MESSAGE = '238a80af-c47b-4846-8e19-5a217dc117f3';
const REQUEST = '338a80af-c47b-4846-8e19-5a217dc117f3';

function createV1(database) {
  const db = new DatabaseSync(database);
  db.exec(`PRAGMA foreign_keys=ON; BEGIN;
    CREATE TABLE configuration (id INTEGER PRIMARY KEY CHECK(id=1), base_url TEXT NOT NULL, model TEXT NOT NULL, encrypted_key BLOB);
    CREATE TABLE topics (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);
    CREATE TABLE messages (id TEXT PRIMARY KEY, topic_id TEXT NOT NULL REFERENCES topics(id), role TEXT NOT NULL CHECK(role IN ('user','assistant')), content TEXT NOT NULL, created_at INTEGER NOT NULL, status TEXT NOT NULL, request_id TEXT, draft_scope TEXT);
    CREATE UNIQUE INDEX message_request ON messages(request_id) WHERE role='user';
    CREATE TABLE drafts (scope TEXT PRIMARY KEY, text TEXT NOT NULL);
    INSERT INTO configuration VALUES(1, 'https://example.test', 'model', X'0001020304');
    INSERT INTO topics VALUES('${TOPIC}', '旧主题', 100, 200);
    INSERT INTO messages VALUES('${MESSAGE}', '${TOPIC}', 'user', '旧问题', 100, 'complete', '${REQUEST}', '${TOPIC}');
    INSERT INTO drafts VALUES('${TOPIC}', '未提交的草稿');
    PRAGMA application_id=1414287921; PRAGMA user_version=1; COMMIT;`);
  db.close();
}

test('identified v1 migrates forward while preserving settings, encrypted key, topic, messages and drafts', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-v1-'));
  const database = path.join(root, 'learning.sqlite'); let service;
  try {
    createV1(database);
    service = createLearningService({ database, secrets, tutorPrompt: 'synthetic baseline', personaPrompts: personas });
    const state = await service.getState();
    assert.equal(state.settings.defaultPersona, 'default');
    assert.equal(state.settings.baseUrl, 'https://example.test');
    assert.equal(state.settings.hasApiKey, true);
    assert.equal(state.topics[0].persona, 'default');
    assert.equal(state.topics[0].messages[0].persona, 'default');
    assert.equal(state.topics[0].messages[0].content, '旧问题');
    assert.equal(state.drafts[TOPIC], '未提交的草稿');
    service.close(); service = null;
    const db = new DatabaseSync(database, { readOnly: true });
    try {
      assert.equal(db.prepare('PRAGMA user_version').get().user_version, 4);
      assert.equal(Buffer.from(db.prepare('SELECT encrypted_key FROM configuration').get().encrypted_key).toString('hex'), '0001020304');
      assert.equal(db.prepare('PRAGMA foreign_key_check').all().length, 0);
      assert.equal(db.prepare('PRAGMA integrity_check').get().integrity_check, 'ok');
    } finally { db.close(); }
    service = createLearningService({ database, secrets, personaPrompts: personas });
    assert.equal((await service.getState()).drafts[TOPIC], '未提交的草稿');
  } finally { if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('topic persona is captured per request and retry cannot rewrite an earlier style', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-send-'));
  let service; const systems = []; let failed = true;
  try {
    service = createLearningService({ database: path.join(root, 'learning.sqlite'), secrets, tutorPrompt: 'synthetic baseline', personaPrompts: personas,
      transport: async (_url, options) => {
        systems.push(JSON.parse(options.body).messages[0].content);
        if (failed) { failed = false; return new Response('synthetic', { status: 500 }); }
        return new Response(JSON.stringify({ choices: [{ message: { content: 'synthetic answer' } }] }), { status: 200 });
      } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await service.setPersona({ persona: 'strict-mentor' });
    await assert.rejects(service.sendMessage({ text: '我的问题', requestId: REQUEST }), /REQUEST_FAILED/);
    let state = await service.getState(); const topicId = state.topics[0].id;
    await service.setPersona({ topicId, persona: 'late-night' });
    await service.retryMessage({ requestId: REQUEST });
    state = await service.getState();
    assert.equal(state.settings.defaultPersona, 'strict-mentor');
    assert.equal(state.topics[0].persona, 'late-night');
    assert.equal(state.topics[0].messages[0].persona, 'strict-mentor');
    assert.equal(state.topics[0].messages[1].persona, 'strict-mentor');
    assert.deepEqual(systems, ['synthetic baseline\nsynthetic strict-mentor', 'synthetic baseline\nsynthetic strict-mentor']);
    await assert.rejects(service.setPersona({ topicId, persona: 'untrusted-instruction' }), /REQUEST_INVALID/);
  } finally { if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('failed migration rolls back all earlier schema changes and preserves v1 data', () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-rollback-'));
  const database = path.join(root, 'learning.sqlite');
  let injected = false;
  const original = DatabaseSync.prototype.exec;
  const hash = () => createHash('sha256').update(readFileSync(database)).digest('hex');
  try {
    createV1(database); const before = hash();
    DatabaseSync.prototype.exec = function(sql) {
      if (sql === "ALTER TABLE topics ADD COLUMN persona TEXT NOT NULL DEFAULT 'default'") {
        injected = true; throw new Error('synthetic migration failure');
      }
      return original.call(this, sql);
    };
    assert.throws(() => createLearningService({ database, secrets, personaPrompts: personas }), /STORAGE_ERROR/);
    DatabaseSync.prototype.exec = original;
    assert.equal(injected, true, 'failure must be injected after the first migration step');
    assert.equal(hash(), before, 'failed migration must retain original database bytes');
    const db = new DatabaseSync(database, { readOnly: true });
    try {
      assert.equal(db.prepare('PRAGMA user_version').get().user_version, 1);
      assert.equal(db.prepare("SELECT name FROM pragma_table_info('configuration') WHERE name='default_persona'").get(), undefined);
      assert.equal(db.prepare('SELECT text FROM drafts').get().text, '未提交的草稿');
      assert.equal(Buffer.from(db.prepare('SELECT encrypted_key FROM configuration').get().encrypted_key).toString('hex'), '0001020304');
    } finally { db.close(); }
  } finally { DatabaseSync.prototype.exec = original; rmSync(root, { recursive: true, force: true }); }
});

test('an unmarked v1 database is rejected and its bytes remain untouched', () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-foreign-'));
  const database = path.join(root, 'learning.sqlite');
  try {
    createV1(database);
    const db = new DatabaseSync(database); db.exec('PRAGMA application_id=0'); db.close();
    const before = readFileSync(database);
    let unexpected;
    try { assert.throws(() => { unexpected = createLearningService({ database, secrets, personaPrompts: personas }); }, /STORAGE_ERROR/); }
    finally { if (unexpected) unexpected.close(); }
    assert.deepEqual(readFileSync(database), before);
  } finally { rmSync(root, { recursive: true, force: true }); }
});

test('changing persona while a request runs affects the next request only', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-inflight-'));
  let service, resolveFirst, signalStarted, calls = 0;
  const started = new Promise(resolve => { signalStarted = resolve; });
  const gate = new Promise(resolve => { resolveFirst = resolve; });
  const systems = [];
  try {
    service = createLearningService({ database: path.join(root, 'learning.sqlite'), secrets, tutorPrompt: 'synthetic baseline', personaPrompts: personas,
      transport: async (_url, options) => {
        systems.push(JSON.parse(options.body).messages[0].content);
        if (++calls === 1) { signalStarted(); await gate; }
        return new Response(JSON.stringify({ choices: [{ message: { content: '合成回答' } }] }), { status: 200 });
      } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await service.setPersona({ persona: 'strict-mentor' });
    const sending = service.sendMessage({ text: '第一次', requestId: REQUEST });
    await started;
    const topicId = (await service.getState()).topics[0].id;
    await service.setPersona({ topicId, persona: 'late-night' });
    resolveFirst(); await sending;
    await service.sendMessage({ topicId, text: '第二次', requestId: '438a80af-c47b-4846-8e19-5a217dc117f3' });
    const state = await service.getState();
    assert.equal(state.topics[0].persona, 'late-night');
    assert.deepEqual(state.topics[0].messages.map(message => message.persona), ['strict-mentor','strict-mentor','late-night','late-night']);
    assert.deepEqual(systems, ['synthetic baseline\nsynthetic strict-mentor', 'synthetic baseline\nsynthetic late-night']);
  } finally { if (resolveFirst) resolveFirst(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});

test('unknown persisted persona falls back to default without rewriting the stored value', async () => {
  const root = mkdtempSync(path.join(os.tmpdir(), 'treelearn-persona-unknown-'));
  const database = path.join(root, 'learning.sqlite');
  let service, observer; const systems = [];
  try {
    service = createLearningService({ database, secrets, tutorPrompt: 'synthetic baseline', personaPrompts: personas,
      transport: async (_url, options) => {
        systems.push(JSON.parse(options.body).messages[0].content);
        return new Response(JSON.stringify({ choices: [{ message: { content: '合成回答' } }] }), { status: 200 });
      } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const topicId = (await service.sendMessage({ text: '第一次', requestId: REQUEST })).topicId;
    service.close(); service = null;
    observer = new DatabaseSync(database);
    observer.prepare('UPDATE configuration SET default_persona=? WHERE id=1').run('future-style');
    observer.prepare('UPDATE topics SET persona=? WHERE id=?').run('future-style', topicId);
    observer.prepare('UPDATE messages SET persona=? WHERE topic_id=?').run('future-style', topicId);
    observer.close(); observer = null;
    service = createLearningService({ database, secrets, tutorPrompt: 'synthetic baseline', personaPrompts: personas,
      transport: async (_url, options) => {
        systems.push(JSON.parse(options.body).messages[0].content);
        return new Response(JSON.stringify({ choices: [{ message: { content: '再次回答' } }] }), { status: 200 });
      } });
    const state = await service.getState();
    assert.equal(state.settings.defaultPersona, 'future-style');
    assert.equal(state.topics[0].persona, 'future-style');
    assert.equal(state.topics[0].messages[0].persona, 'future-style');
    await service.sendMessage({ topicId, text: '第二次', requestId: '438a80af-c47b-4846-8e19-5a217dc117f3' });
    assert.equal(systems.at(-1), 'synthetic baseline');
    service.close(); service = null;
    observer = new DatabaseSync(database, { readOnly: true });
    assert.equal(observer.prepare('SELECT default_persona FROM configuration WHERE id=1').get().default_persona, 'future-style');
    assert.equal(observer.prepare('SELECT persona FROM topics WHERE id=?').get(topicId).persona, 'future-style');
    assert.equal(observer.prepare('SELECT persona FROM messages WHERE request_id=? AND role=?').get(REQUEST, 'user').persona, 'future-style');
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(root, { recursive: true, force: true }); }
});
