/* Actual Windows safeStorage and a synthetic identified V2 database, migrated by production. */
const { app, safeStorage } = require('electron');
const { DatabaseSync } = require('node:sqlite');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const crypto = require('node:crypto');
const { createLearningService } = require('../learning-service.cjs');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-v2-safe-migration-'));
const database = path.join(root, 'learning.sqlite');
const evidence = path.resolve(__dirname, '../../artifacts/evidence', `desktop-v2-safe-migration-${path.basename(root)}`);
const key = 'synthetic-v2-migration-credential';
const TOPIC = '138a80af-c47b-4846-8e19-5a217dc117f3';
const USER = '238a80af-c47b-4846-8e19-5a217dc117f3';
const ASSISTANT = '438a80af-c47b-4846-8e19-5a217dc117f3';
const REQUEST = '338a80af-c47b-4846-8e19-5a217dc117f3';
const inputs = ['desktop/learning-service.cjs', 'desktop/branch-context.cjs', 'desktop/test/electron-v2-branch-migration-safe-storage.cjs'];
const assert = (condition, message) => { if (!condition) throw new Error(message); };
const hashes = () => Object.fromEntries(inputs.map(name => [name, crypto.createHash('sha256').update(fs.readFileSync(path.resolve(__dirname, '../..', name))).digest('hex')]));
app.setPath('userData', path.join(root, 'profile'));
app.disableHardwareAcceleration();

app.whenReady().then(async () => {
  let service;
  try {
    fs.mkdirSync(evidence, { recursive: true });
    const before = hashes();
    fs.writeFileSync(path.join(evidence, 'hashes-before.json'), JSON.stringify(before, null, 2));
    assert(process.platform === 'win32', 'Windows required');
    assert(await safeStorage.isAsyncEncryptionAvailable(), 'OS encryption unavailable');
    const encrypted = await safeStorage.encryptStringAsync(key);
    assert(Buffer.isBuffer(encrypted) && !encrypted.includes(Buffer.from(key)), 'ciphertext contains plaintext');
    const db = new DatabaseSync(database);
    try {
      db.exec(`PRAGMA foreign_keys=ON; BEGIN;
        CREATE TABLE configuration (id INTEGER PRIMARY KEY CHECK(id=1), base_url TEXT NOT NULL, model TEXT NOT NULL, encrypted_key BLOB, default_persona TEXT NOT NULL DEFAULT 'default');
        CREATE TABLE topics (id TEXT PRIMARY KEY, title TEXT NOT NULL, created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL, persona TEXT NOT NULL DEFAULT 'default');
        CREATE TABLE messages (id TEXT PRIMARY KEY, topic_id TEXT NOT NULL REFERENCES topics(id), role TEXT NOT NULL CHECK(role IN ('user','assistant')), content TEXT NOT NULL, created_at INTEGER NOT NULL, status TEXT NOT NULL, request_id TEXT, draft_scope TEXT, persona TEXT NOT NULL DEFAULT 'default');
        CREATE UNIQUE INDEX message_request ON messages(request_id) WHERE role='user';
        CREATE TABLE drafts (scope TEXT PRIMARY KEY, text TEXT NOT NULL);
        PRAGMA application_id=1414287921; PRAGMA user_version=2; COMMIT;`);
      db.prepare('INSERT INTO configuration VALUES(1,?,?,?,?)').run('https://example.test', 'synthetic-model', encrypted, 'strict-mentor');
      db.prepare('INSERT INTO topics VALUES(?,?,?,?,?)').run(TOPIC, '合成旧主题', 100, 200, 'late-night');
      db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?,?,?)').run(USER, TOPIC, 'user', '合成旧问题', 100, 'complete', REQUEST, TOPIC, 'late-night');
      db.prepare('INSERT INTO messages VALUES(?,?,?,?,?,?,?,?,?)').run(ASSISTANT, TOPIC, 'assistant', '合成旧回答', 101, 'complete', REQUEST, null, 'late-night');
      db.prepare('INSERT INTO drafts VALUES(?,?)').run(TOPIC, '合成旧草稿');
    } finally { db.close(); }
    assert(!fs.readFileSync(database).includes(Buffer.from(key)), 'v2 database contains plaintext key');
    console.log('V2_SAFE_STORAGE_ENCRYPTED_OK');
    let sent = false;
    const options = { database, secrets: safeStorage, tutorPrompt: '合成学习导师', personaPrompts: { default: '', 'late-night': '合成深夜风格' },
      transport: async (url, request) => {
        assert(url === 'https://example.test/chat/completions', 'unexpected endpoint');
        assert(request.headers.authorization === `Bearer ${key}`, 'decrypted key unavailable');
        const payload = JSON.parse(request.body);
        assert(payload.messages[0].content === '合成学习导师\n合成深夜风格', 'persona prompt missing');
        assert(payload.messages.some(message => message.content === '合成旧问题') && payload.messages.some(message => message.content === '合成旧回答'), 'old history absent');
        sent = true;
        return new Response(JSON.stringify({ choices: [{ message: { content: '合成迁移后回答' }, finish_reason: 'stop' }] }), {
          status: 200, headers: { 'content-type': 'application/json' }
        });
      } };
    service = createLearningService(options);
    let state = await service.getState();
    const topic = state.topics[0], branch = topic.branches[0];
    assert(state.settings.hasApiKey && state.settings.defaultPersona === 'strict-mentor', 'settings migration');
    assert(topic.id === TOPIC && topic.persona === 'late-night' && topic.rootBranchId === TOPIC
      && branch.id === TOPIC && branch.parentBranchId === null && branch.messages.length === 2
      && branch.messages[0].id === USER && branch.messages[0].content === '合成旧问题'
      && branch.messages[1].id === ASSISTANT && branch.messages[1].content === '合成旧回答'
      && branch.messages.every(message => message.persona === 'late-night') && state.drafts[TOPIC] === '合成旧草稿', 'history or draft migration');
    assert(!JSON.stringify(state).includes(key), 'key exposed in state');
    const raw = new DatabaseSync(database, { readOnly: true });
    try {
      assert(raw.prepare('PRAGMA user_version').get().user_version === 3, 'version did not migrate');
      assert(raw.prepare('PRAGMA application_id').get().application_id === 1414287921, 'application id changed');
      assert(raw.prepare('PRAGMA integrity_check').get().integrity_check === 'ok', 'integrity');
      assert(raw.prepare('PRAGMA foreign_key_check').all().length === 0, 'foreign key');
      assert(Buffer.from(raw.prepare('SELECT encrypted_key FROM configuration').get().encrypted_key).equals(encrypted), 'encrypted key changed');
    } finally { raw.close(); }
    console.log('V2_TO_V3_DATA_PRESERVED_OK');
    await service.sendMessage({ topicId: TOPIC, branchId: TOPIC, text: '合成迁移后追问', requestId: crypto.randomUUID() });
    assert(sent, 'synthetic transport not called');
    service.close(); service = null;
    service = createLearningService(options);
    state = await service.getState();
    assert(state.settings.defaultPersona === 'strict-mentor' && state.topics[0].persona === 'late-night'
      && state.topics[0].branches[0].messages.some(message => message.content === '合成迁移后回答' && message.persona === 'late-night')
      && state.drafts[TOPIC] === '合成旧草稿', 'data did not survive reopen');
    assert(!fs.readFileSync(database).includes(Buffer.from(key)), 'v3 database contains plaintext key');
    service.close(); service = null;
    const after = hashes();
    fs.writeFileSync(path.join(evidence, 'hashes-after.json'), JSON.stringify(after, null, 2));
    assert(JSON.stringify(before) === JSON.stringify(after), 'inputs changed during test');
    fs.writeFileSync(path.join(evidence, 'result.json'), JSON.stringify({ testExit: 0, inputsStable: true, version: 3, sqlite: process.versions.sqlite }, null, 2));
    console.log('V2_MIGRATION_KEY_SEND_REOPEN_OK');
    console.log(`EVIDENCE_DIR ${evidence}`);
    app.quit();
  } catch (error) {
    if (service) { try { service.close(); } catch {} }
    console.error('V2_MIGRATION_SAFE_STORAGE_FAIL', error.message);
    app.exit(1);
  }
}).catch(() => { console.error('V2_MIGRATION_SAFE_STORAGE_FAIL startup'); app.exit(1); });
