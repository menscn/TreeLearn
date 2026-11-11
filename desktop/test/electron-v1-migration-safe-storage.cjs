/* Real Windows safeStorage plus a synthetic, identified V1 database. */
const { app, safeStorage } = require('electron');
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { createLearningService } = require('../learning-service.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-v1-safe-migration-'));
const database = path.join(root, 'learning.sqlite');
const key = 'synthetic-v1-migration-credential';
const TOPIC = '138a80af-c47b-4846-8e19-5a217dc117f3';
const MESSAGE = '238a80af-c47b-4846-8e19-5a217dc117f3';
const REQUEST = '338a80af-c47b-4846-8e19-5a217dc117f3';
const assert = (condition, message) => { if (!condition) throw new Error(message); };
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();

app.whenReady().then(async () => {
  let service;
  try {
    assert(process.platform === 'win32', 'Windows required');
    assert(await safeStorage.isAsyncEncryptionAvailable(), 'OS encryption unavailable');
    const encrypted = await safeStorage.encryptStringAsync(key);
    assert(Buffer.isBuffer(encrypted) && !encrypted.includes(Buffer.from(key)), 'ciphertext contains plaintext');
    const db = new DatabaseSync(database);
    try {
      db.exec(`PRAGMA foreign_keys=ON; BEGIN;
        CREATE TABLE configuration (id INTEGER PRIMARY KEY CHECK(id=1), base_url TEXT NOT NULL, model TEXT NOT NULL, encrypted_key BLOB);
        CREATE TABLE topics (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL);
        CREATE TABLE messages (id TEXT PRIMARY KEY, topic_id TEXT NOT NULL REFERENCES topics(id), role TEXT NOT NULL CHECK(role IN ('user','assistant')), content TEXT NOT NULL, created_at INTEGER NOT NULL, status TEXT NOT NULL, request_id TEXT, draft_scope TEXT);
        CREATE UNIQUE INDEX message_request ON messages(request_id) WHERE role='user';
        CREATE TABLE drafts (scope TEXT PRIMARY KEY, text TEXT NOT NULL);
        PRAGMA application_id=1414287921; PRAGMA user_version=1; COMMIT;`);
      db.prepare('INSERT INTO configuration VALUES(1,?,?,?)').run('https://example.test', 'synthetic-model', encrypted);
      db.prepare('INSERT INTO topics VALUES(?,?,?,?)').run(TOPIC, '合成旧主题', 100, 200);
      db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?,?)').run(MESSAGE, TOPIC, 'user', '合成旧问题', 100, 'complete', REQUEST, TOPIC);
      db.prepare('INSERT INTO drafts VALUES(?,?)').run(TOPIC, '合成旧草稿');
    } finally { db.close(); }
    assert(!fs.readFileSync(database).includes(Buffer.from(key)), 'v1 database contains plaintext key');
    console.log('V1_SAFE_STORAGE_ENCRYPTED_OK');
    let sent = false;
    service = createLearningService({ database, secrets: safeStorage, tutorPrompt: '合成学习导师',
      transport: async (url, options) => {
        assert(url === 'https://example.test/chat/completions', 'unexpected endpoint');
        assert(options.headers.authorization === `Bearer ${key}`, 'decrypted key unavailable');
        const payload = JSON.parse(options.body);
        assert(payload.messages.some(message => message.content === '合成旧问题'), 'old history absent');
        sent = true;
        return new Response(JSON.stringify({ choices: [{ message: { content: '合成迁移后回答' }, finish_reason: 'stop' }] }), {
          status: 200, headers: { 'content-type': 'application/json' }
        });
      } });
    let state = await service.getState();
    assert(state.settings.hasApiKey && state.settings.defaultPersona === 'default', 'settings migration');
    assert(state.topics[0].id === TOPIC && state.topics[0].persona === 'default'
      && state.topics[0].messages[0].content === '合成旧问题' && state.drafts[TOPIC] === '合成旧草稿', 'history or draft migration');
    assert(!JSON.stringify(state).includes(key), 'key exposed in state');
    const raw = new DatabaseSync(database, { readOnly: true });
    try {
      assert(raw.prepare('PRAGMA user_version').get().user_version === 2, 'version did not migrate');
      assert(raw.prepare('PRAGMA application_id').get().application_id === 1414287921, 'application id changed');
      assert(raw.prepare('PRAGMA integrity_check').get().integrity_check === 'ok', 'integrity');
      assert(raw.prepare('PRAGMA foreign_key_check').all().length === 0, 'foreign key');
      assert(Buffer.from(raw.prepare('SELECT encrypted_key FROM configuration').get().encrypted_key).equals(encrypted), 'encrypted key changed');
    } finally { raw.close(); }
    console.log('V1_TO_V2_DATA_PRESERVED_OK');
    await service.sendMessage({ topicId: TOPIC, text: '合成迁移后追问', requestId: crypto.randomUUID() });
    assert(sent, 'synthetic transport not called');
    service.close(); service = null;
    service = createLearningService({ database, secrets: safeStorage });
    state = await service.getState();
    assert(state.topics[0].messages.some(message => message.content === '合成迁移后回答')
      && state.drafts[TOPIC] === '合成旧草稿', 'data did not survive reopen');
    assert(!fs.readFileSync(database).includes(Buffer.from(key)), 'v2 database contains plaintext key');
    service.close(); service = null;
    console.log('V1_MIGRATION_KEY_SEND_REOPEN_OK');
    app.quit();
  } catch (error) {
    if (service) { try { service.close(); } catch {} }
    console.error('V1_MIGRATION_SAFE_STORAGE_FAIL', error.message);
    app.exit(1);
  }
}).catch(() => { console.error('V1_MIGRATION_SAFE_STORAGE_FAIL startup'); app.exit(1); });
