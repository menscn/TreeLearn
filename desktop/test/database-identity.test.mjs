import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { createHash } from 'node:crypto';
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { createLearningService } = require('../learning-service.cjs');
const APPLICATION_ID = 0x544c5231;
const secrets = { isAsyncEncryptionAvailable: async () => true, encryptStringAsync: async value => Buffer.from(value), decryptStringAsync: async value => ({ result: Buffer.from(value).toString(), shouldReEncrypt: false }) };
const digest = file => createHash('sha256').update(readFileSync(file)).digest('hex');

function rejectedWithoutMutation(database) {
  const before = digest(database);
  let unexpected;
  try { assert.throws(() => { unexpected = createLearningService({ database, secrets }); }, /STORAGE_ERROR/); }
  finally { if (unexpected) unexpected.close(); }
  assert.equal(digest(database), before, 'unknown database bytes must not change');
}

test('an unrelated version-one SQLite database is not recovered or modified', () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-foreign-db-'));
  const database = path.join(dir, 'learning.sqlite');
  try {
    const foreign = new DatabaseSync(database);
    foreign.exec("CREATE TABLE messages(status TEXT); INSERT INTO messages VALUES('pending'); PRAGMA user_version=1");
    foreign.close();
    rejectedWithoutMutation(database);
    const read = new DatabaseSync(database, { readOnly: true });
    try { assert.equal(read.prepare('SELECT status FROM messages').get().status, 'pending'); } finally { read.close(); }
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('version-zero databases containing non-table schema objects are never initialized', () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-unknown-schema-'));
  const database = path.join(dir, 'learning.sqlite');
  try {
    const foreign = new DatabaseSync(database);
    foreign.exec('CREATE VIEW unrelated AS SELECT 1 AS value; PRAGMA user_version=0'); foreign.close();
    rejectedWithoutMutation(database);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('identified but modified schemas and future versions fail before recovery', async () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-tampered-db-'));
  try {
    const modified = path.join(dir, 'modified.sqlite');
    let service = createLearningService({ database: modified, secrets });
    await service.saveDraft({ text: '保留的合成草稿' }); service.close();
    let db = new DatabaseSync(modified);
    db.exec(`PRAGMA application_id=${APPLICATION_ID}; CREATE TRIGGER unexpected AFTER UPDATE ON messages BEGIN DELETE FROM drafts; END`);
    db.close();
    rejectedWithoutMutation(modified);

    const future = path.join(dir, 'future.sqlite');
    service = createLearningService({ database: future, secrets }); service.close();
    db = new DatabaseSync(future);
    db.exec(`PRAGMA application_id=${APPLICATION_ID}; PRAGMA user_version=999`); db.close();
    rejectedWithoutMutation(future);
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('newly created identified database reopens without losing data', async () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-identified-db-'));
  const database = path.join(dir, 'learning.sqlite');
  let service, db;
  try {
    service = createLearningService({ database, secrets });
    await service.saveDraft({ text: '合成草稿' }); service.close(); service = null;
    db = new DatabaseSync(database, { readOnly: true });
    assert.equal(db.prepare('PRAGMA application_id').get().application_id, APPLICATION_ID);
    assert.equal(db.prepare('PRAGMA user_version').get().user_version, 4);
    db.close(); db = null;
    service = createLearningService({ database, secrets });
    assert.equal((await service.getState()).drafts.home, '合成草稿');
  } finally { if (db) db.close(); if (service) service.close(); rmSync(dir, { recursive: true, force: true }); }
});

test('an existing empty file can be initialized without touching sibling files', async () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-empty-db-'));
  const database = path.join(dir, 'learning.sqlite'), sibling = path.join(dir, 'other.txt');
  let service;
  try {
    writeFileSync(database, ''); writeFileSync(sibling, 'untouched');
    service = createLearningService({ database, secrets });
    await service.saveDraft({ text: '合成草稿' }); service.close(); service = null;
    service = createLearningService({ database, secrets });
    assert.equal((await service.getState()).drafts.home, '合成草稿');
    assert.equal(readFileSync(sibling, 'utf8'), 'untouched');
  } finally { if (service) service.close(); rmSync(dir, { recursive: true, force: true }); }
});

test('legacy unmarked version one and weakened unique constraint fail without alteration', () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-schema-defect-'));
  try {
    for (const defect of ['unmarked', 'nonunique-index']) {
      const database = path.join(dir, `${defect}.sqlite`);
      const service = createLearningService({ database, secrets }); service.close();
      const db = new DatabaseSync(database);
      if (defect === 'unmarked') db.exec('PRAGMA application_id=0');
      else db.exec("DROP INDEX message_request; CREATE INDEX message_request ON messages(request_id) WHERE role='user'");
      db.close();
      rejectedWithoutMutation(database);
    }
  } finally { rmSync(dir, { recursive: true, force: true }); }
});

test('rejecting a foreign active WAL database does not alter the database or WAL bytes', () => {
  const dir = mkdtempSync(path.join(os.tmpdir(), 'treelearn-foreign-wal-'));
  const database = path.join(dir, 'learning.sqlite');
  let foreign;
  try {
    foreign = new DatabaseSync(database);
    foreign.exec("PRAGMA journal_mode=WAL; PRAGMA wal_autocheckpoint=0; CREATE TABLE messages(status TEXT); INSERT INTO messages VALUES('pending'); PRAGMA user_version=1");
    const wal = `${database}-wal`;
    assert.equal(existsSync(wal), true);
    const dbBefore = digest(database), walBefore = digest(wal);
    let unexpected;
    try { assert.throws(() => { unexpected = createLearningService({ database, secrets }); }, /STORAGE_ERROR/); }
    finally { if (unexpected) unexpected.close(); }
    assert.equal(digest(database), dbBefore);
    assert.equal(digest(wal), walBefore);
    assert.equal(foreign.prepare('SELECT status FROM messages').get().status, 'pending');
  } finally { if (foreign) foreign.close(); rmSync(dir, { recursive: true, force: true }); }
});
