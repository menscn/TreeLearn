import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';
import { DatabaseSync } from 'node:sqlite';
import fs from 'node:fs';

const require = createRequire(import.meta.url);
const { createLearningService } = require('../learning-service.cjs');
const secrets = {
  isAsyncEncryptionAvailable: async () => true,
  encryptStringAsync: async value => Buffer.from(`encrypted:${value}`).reverse(),
  decryptStringAsync: async value => ({ result: Buffer.from(value).reverse().toString().slice(10), shouldReEncrypt: false })
};

test('restart restores conversations and encrypted credentials without exposing secret', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-service-test-'));
  const database = path.join(directory, 'learning.sqlite');
  const token = 'synthetic-secret-2ad7';
  let request;
  const transport = async (url, options) => {
    request = { url, options };
    return new Response(JSON.stringify({ choices: [{ message: { content: '合成回答' } }] }), { status: 200 });
  };
  try {
    let service = createLearningService({ database, secrets, transport });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'model-test', apiKey: token });
    await service.saveDraft({ text: '原始草稿' });
    const result = await service.sendMessage({ text: '什么是树？', requestId: 'ae73776d-a0f4-4f0f-8b97-2362e42f7093' });
    assert.equal(request.url, 'https://example.test/chat/completions');
    assert.equal(request.options.redirect, 'error');
    assert.equal(JSON.parse(request.options.body).messages.at(-1).content, '什么是树？');
    service.close();
    service = createLearningService({ database, secrets, transport });
    const state = await service.getState();
    assert.equal(state.settings.hasApiKey, true);
    assert.ok(!JSON.stringify(state).includes(token));
    assert.equal(state.topics[0].id, result.topicId);
    assert.deepEqual(state.topics[0].messages.map(m => m.content), ['什么是树？', '合成回答']);
    assert.equal(state.drafts.home, '原始草稿');
    service.close();
    assert.equal(readFileSync(database).includes(Buffer.from(token)), false);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test('failed request preserves draft and retry does not duplicate the user message', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-retry-test-'));
  const database = path.join(directory, 'learning.sqlite');
  const id = 'c9090608-a063-4c23-8bd8-5d891cc564b6';
  let fail = true;
  const transport = async () => fail ? new Response('private provider details', { status: 401 }) :
    new Response(JSON.stringify({ choices: [{ message: { content: '重试成功' } }] }), { status: 200 });
  try {
    let service = createLearningService({ database, secrets, transport });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'model-test', apiKey: 'synthetic' });
    await service.saveDraft({ text: '保留的草稿' });
    await assert.rejects(service.sendMessage({ text: '保留的草稿', requestId: id }), /AUTH_FAILED/);
    let state = await service.getState();
    assert.equal(state.drafts.home, '保留的草稿');
    assert.equal(state.topics[0].messages[0].status, 'failed');
    service.close();
    service = createLearningService({ database, secrets, transport });
    fail = false;
    await service.retryMessage({ requestId: id });
    state = await service.getState();
    assert.deepEqual(state.topics[0].messages.map(m => m.content), ['保留的草稿', '重试成功']);
    assert.equal(state.topics[0].messages[0].status, 'complete');
    assert.equal(state.drafts.home, undefined);
    service.close();
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test('rejects insecure endpoint and unavailable encryption without saving secrets', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-credentials-test-'));
  try {
    const service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets: { ...secrets, isAsyncEncryptionAvailable: async () => false } });
    await assert.rejects(service.saveSettings({ baseUrl: 'http://example.test', model: 'm', apiKey: 'synthetic' }), /INVALID_SETTINGS/);
    await assert.rejects(service.saveSettings({ baseUrl: 'https://example.test', model: 'm', apiKey: 'synthetic' }), /ENCRYPTION_UNAVAILABLE/);
    assert.equal((await service.getState()).settings.hasApiKey, false);
    service.close();
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test('streams split UTF-8 events and preserves an interrupted partial answer for retry', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-stream-test-'));
  const requestId = '54a92737-a14b-4b33-a4a5-edca4e548d80';
  let attempt = 0;
  let service;
  const transport = async (_url, options) => {
    assert.equal(JSON.parse(options.body).stream, true);
    attempt++;
    const text = attempt === 1
      ? 'data: {"choices":[{"delta":{"content":"部分"}}]}\n\n'
      : 'data: {"choices":[{"delta":{"content":"完整"}}]}\n\ndata: [DONE]\n\n';
    const bytes = new TextEncoder().encode(text);
    return new Response(new ReadableStream({ start(controller) {
      for (let i = 0; i < bytes.length; i += 3) controller.enqueue(bytes.slice(i, i + 3));
      controller.close();
    } }), { headers: { 'content-type': 'text/event-stream' } });
  };
  try {
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const progress = [];
    await assert.rejects(service.sendMessage({ text: '请解释', requestId }, value => progress.push(value)), /REQUEST_FAILED/);
    assert.equal(progress[0].content, '部分');
    let state = await service.getState();
    assert.deepEqual(state.topics[0].messages.map(m => [m.role, m.content, m.status]), [['user','请解释','failed'], ['assistant','部分','failed']]);
    service.close(); service = null;
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport });
    await service.retryMessage({ requestId });
    state = await service.getState();
    assert.deepEqual(state.topics[0].messages.map(m => m.content), ['请解释', '部分', '完整']);
    assert.deepEqual(state.topics[0].messages.map(m => m.status), ['complete', 'failed', 'complete']);
    service.close(); service = null;
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('retains received answer when a later write in the completion transaction fails', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-rollback-test-'));
  const database = path.join(directory, 'learning.sqlite');
  let service, observer;
  try {
    service = createLearningService({ database, secrets, transport: async () => new Response(JSON.stringify({ choices: [{ message: { content: '已收到的回答' } }] }), { status: 200 }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    observer = new DatabaseSync(database);
    observer.exec("CREATE TRIGGER test_completion_failure BEFORE UPDATE ON topics WHEN EXISTS(SELECT 1 FROM messages WHERE role='assistant' AND status='complete') BEGIN SELECT RAISE(ABORT, 'synthetic failure'); END");
    await assert.rejects(service.sendMessage({ text: '合成提问', requestId: 'b4827473-0bf4-4173-a933-174470010c12' }), /STORAGE_ERROR/);
    const state = await service.getState();
    assert.deepEqual(state.topics[0].messages.map(m => [m.role,m.content,m.status]), [['user','合成提问','failed'], ['assistant','已收到的回答','failed']]);
  } finally { if (observer) observer.close(); if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('refuses retry of an older failed question after a newer conversation turn', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-old-retry-test-'));
  let service, count = 0;
  try {
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport: async () => ++count === 1
      ? new Response('synthetic', { status: 500 })
      : new Response(JSON.stringify({ choices: [{ message: { content: '较新回答' } }] }), { status: 200 }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const oldRequest = 'b4827473-0bf4-4173-a933-174470010c12';
    await assert.rejects(service.sendMessage({ text: '旧问题', requestId: oldRequest }), /REQUEST_FAILED/);
    const topicId = (await service.getState()).topics[0].id;
    await service.sendMessage({ topicId, text: '新问题', requestId: 'a4827473-0bf4-4173-a933-174470010c12' });
    await assert.rejects(service.retryMessage({ requestId: oldRequest }), /STALE_RETRY/);
    assert.equal(count, 2);
    assert.deepEqual((await service.getState()).topics[0].messages.map(m => m.content), ['旧问题','新问题','较新回答']);
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('cancels a malformed provider stream and retains partial answer', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-abort-test-'));
  let service, cancelled = false, signal;
  try {
    const transport = async (_url, options) => {
      signal = options.signal;
      return new Response(new ReadableStream({
        start(controller) { controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"部分"}}]}\n\ndata: {invalid}\n\n')); },
        cancel() { cancelled = true; }
      }), { headers: { 'content-type': 'text/event-stream' } });
    };
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await assert.rejects(service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }), /REQUEST_FAILED/);
    assert.equal(cancelled, true);
    assert.equal(signal.aborted, true);
    assert.equal((await service.getState()).topics[0].messages.at(-1).content, '部分');
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('does not mark provider-truncated streams as complete', async () => {
  for (const reason of ['length', 'content_filter']) {
    const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-truncate-test-'));
    let service;
    try {
      const text = `data: {"choices":[{"delta":{"content":"部分"}}]}\n\ndata: {"choices":[{"delta":{},"finish_reason":"${reason}"}]}\n\ndata: [DONE]\n\n`;
      service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport: async () => new Response(text, { headers: { 'content-type': 'text/event-stream' } }) });
      await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
      await assert.rejects(service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }), /INCOMPLETE_RESPONSE/);
      const messages = (await service.getState()).topics[0].messages;
      assert.deepEqual(messages.map(m => m.status), ['failed', 'failed']);
      assert.equal(messages.at(-1).content, '部分');
    } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
  }
});

test('explicit cancellation aborts generation and preserves partial work across restart', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-cancel-test-'));
  const database = path.join(directory, 'learning.sqlite');
  const requestId = '8e15d40d-5b17-46d5-a059-4e7765b6f4ac';
  let service, abortObserved = false, ready;
  const received = new Promise(resolve => { ready = resolve; });
  try {
    const transport = async (_url, options) => new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"尚未完成"}}]}\n\n'));
        options.signal.addEventListener('abort', () => { abortObserved = true; controller.error(new Error('aborted')); });
      }
    }), { headers: { 'content-type': 'text/event-stream' } });
    service = createLearningService({ database, secrets, transport });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await service.saveDraft({ text: '我的草稿' });
    const sending = service.sendMessage({ text: '我的草稿', requestId }, () => ready());
    await received;
    assert.deepEqual(await service.cancelMessage({ requestId }), { cancelled: true });
    await assert.rejects(sending, /REQUEST_CANCELLED/);
    assert.equal(abortObserved, true);
    let state = await service.getState();
    assert.equal(state.drafts.home, '我的草稿');
    assert.deepEqual(state.topics[0].messages.map(m => [m.content,m.status]), [['我的草稿','cancelled'], ['尚未完成','cancelled']]);
    service.close(); service = null;
    service = createLearningService({ database, secrets });
    state = await service.getState();
    assert.deepEqual(state.topics[0].messages.map(m => m.status), ['cancelled', 'cancelled']);
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('closing service interrupts active generation and preserves its last partial answer', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-close-test-'));
  const database = path.join(directory, 'learning.sqlite');
  let service, ready, aborted = false;
  const received = new Promise(resolve => { ready = resolve; });
  try {
    service = createLearningService({ database, secrets, transport: async (_url, options) => new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"已接收"}}]}\n\n'));
        options.signal.addEventListener('abort', () => { aborted = true; controller.error(new Error('aborted')); });
      }
    }), { headers: { 'content-type': 'text/event-stream' } }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const pending = service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }, () => ready()).catch(() => {});
    await received;
    service.close(); service = null;
    await pending;
    assert.equal(aborted, true);
    service = createLearningService({ database, secrets });
    assert.deepEqual((await service.getState()).topics[0].messages.map(m => [m.content,m.status]), [['问题','interrupted'], ['已接收','interrupted']]);
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('cancellation wins when a provider completes after ignoring abort', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-cancel-race-test-'));
  const database = path.join(directory, 'learning.sqlite');
  const requestId = '8e15d40d-5b17-46d5-a059-4e7765b6f4ac';
  let service, stream, ready;
  const received = new Promise(resolve => { ready = resolve; });
  try {
    service = createLearningService({ database, secrets, transport: async () => new Response(new ReadableStream({
      start(controller) { stream = controller; controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"部分"}}]}\n\n')); }
    }), { headers: { 'content-type': 'text/event-stream' } }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await service.saveDraft({ text: '问题' });
    const pending = service.sendMessage({ text: '问题', requestId }, () => ready());
    await received;
    await service.cancelMessage({ requestId });
    stream.enqueue(new TextEncoder().encode('data: [DONE]\n\n')); stream.close();
    await assert.rejects(pending, /REQUEST_CANCELLED/);
    const state = await service.getState();
    assert.equal(state.drafts.home, '问题');
    assert.deepEqual(state.topics[0].messages.map(m => [m.content,m.status]), [['问题','cancelled'], ['部分','cancelled']]);
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('completes at the SSE terminator without waiting for the connection to close', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-done-test-'));
  let service, timeout, cancelled = false;
  try {
    const body = new ReadableStream({
      start(controller) { controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"回答"}}]}\n\ndata: {"choices":[{"delta":{},"finish_reason":"stop"}]}\n\ndata: [DONE]\n\n')); },
      cancel() { cancelled = true; }
    });
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport: async () => new Response(body, { headers: { 'content-type': 'text/event-stream' } }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const deadline = new Promise((_, reject) => { timeout = setTimeout(() => reject(new Error('SSE_DONE_WAITED_FOR_EOF')), 1000); });
    await Promise.race([service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }), deadline]);
    assert.equal(cancelled, true);
    assert.equal((await service.getState()).topics[0].messages.at(-1).content, '回答');
  } finally { clearTimeout(timeout); if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('concurrent home submissions do not create duplicate topics', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-home-busy-test-'));
  let service, release, started;
  const began = new Promise(resolve => { started = resolve; });
  const gate = new Promise(resolve => { release = resolve; });
  try {
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport: async () => {
      started(); await gate;
      return new Response(JSON.stringify({ choices: [{ message: { content: '回答' } }] }), { status: 200 });
    } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const first = service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' });
    await began;
    const second = service.sendMessage({ text: '问题', requestId: '9e15d40d-5b17-46d5-a059-4e7765b6f4ac' }).then(() => 'ACCEPTED', e => e.message);
    const outcome = await Promise.race([second, new Promise(resolve => setTimeout(() => resolve('BLOCKED'), 100))]);
    release(); await first; await second;
    assert.equal(outcome, 'BUSY');
    assert.equal((await service.getState()).topics.length, 1);
  } finally { if (release) release(); if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('close reports a failed final save and keeps the database available', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-close-error-test-'));
  const database = path.join(directory, 'learning.sqlite');
  let service, observer, ready;
  const received = new Promise(resolve => { ready = resolve; });
  try {
    service = createLearningService({ database, secrets, transport: async (_url, options) => new Response(new ReadableStream({
      start(controller) {
        controller.enqueue(new TextEncoder().encode('data: {"choices":[{"delta":{"content":"部分"}}]}\n\n'));
        options.signal.addEventListener('abort', () => controller.error(new Error('aborted')));
      }
    }), { headers: { 'content-type': 'text/event-stream' } }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const pending = service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }, () => ready()).catch(() => {});
    await received;
    observer = new DatabaseSync(database);
    observer.exec("CREATE TRIGGER test_close_failure BEFORE UPDATE ON messages WHEN NEW.status='interrupted' BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
    assert.throws(() => service.close(), /STORAGE_ERROR/);
    assert.ok((await service.getState()).topics.length);
    observer.exec('DROP TRIGGER test_close_failure');
    await pending;
  } finally { if (observer) observer.close(); if (service) { try { service.close(); } catch {} } rmSync(directory, { recursive: true, force: true }); }
});

test('JSON provider truncation preserves the partial answer', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-json-truncate-test-'));
  let service;
  try {
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, transport: async () =>
      new Response(JSON.stringify({ choices: [{ message: { content: '已收到的部分' }, finish_reason: 'length' }] }), { status: 200 }) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await assert.rejects(service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }), /INCOMPLETE_RESPONSE/);
    assert.deepEqual((await service.getState()).topics[0].messages.map(m => [m.content,m.status]), [['问题','failed'], ['已收到的部分','failed']]);
  } finally { if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('deleting a topic is atomic and keeps other topics, drafts and key', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-delete-test-'));
  const database = path.join(directory, 'learning.sqlite');
  let service, observer, release;
  try {
    let block = false, started;
    const began = new Promise(resolve => { started = resolve; });
    const gate = new Promise(resolve => { release = resolve; });
    service = createLearningService({ database, secrets, transport: async () => {
      if (block) { started(); await gate; }
      return new Response(JSON.stringify({ choices: [{ message: { content: '回答' } }] }), { status: 200 });
    } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    const a = (await service.sendMessage({ text: '第一题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' })).topicId;
    const b = (await service.sendMessage({ text: '第二题', requestId: '9e15d40d-5b17-46d5-a059-4e7765b6f4ac' })).topicId;
    await service.saveDraft({ text: '首页草稿' });
    await service.saveDraft({ topicId: a, text: '甲草稿' });
    await service.saveDraft({ topicId: b, text: '乙草稿' });
    observer = new DatabaseSync(database);
    observer.exec("CREATE TRIGGER test_delete_failure BEFORE DELETE ON topics BEGIN SELECT RAISE(ABORT,'synthetic failure'); END");
    await assert.rejects(service.deleteTopic({ topicId: a }));
    let state = await service.getState();
    assert.equal(state.topics.find(t => t.id === a).messages.length, 2);
    assert.equal(state.drafts[a], '甲草稿');
    observer.exec('DROP TRIGGER test_delete_failure');
    block = true;
    const active = service.sendMessage({ topicId: a, text: '进行中', requestId: 'ae15d40d-5b17-46d5-a059-4e7765b6f4ac' });
    await began;
    await assert.rejects(service.deleteTopic({ topicId: a }), /BUSY/);
    release(); await active;
    await service.deleteTopic({ topicId: a });
    state = await service.getState();
    assert.deepEqual(state.topics.map(t => t.id), [b]);
    assert.equal(state.drafts[a], undefined);
    assert.equal(state.drafts[b], '乙草稿');
    assert.equal(state.drafts.home, '首页草稿');
    assert.equal(state.settings.hasApiKey, true);
  } finally { if (release) release(); if (observer) observer.close(); if (service) service.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('fails closed when checking an existing database path is denied', () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-path-test-'));
  const database = path.join(directory, 'learning.sqlite');
  const original = fs.lstatSync;
  let unexpected;
  try {
    fs.lstatSync = function(pathname, ...args) {
      if (pathname === database) { const error = new Error('synthetic denied'); error.code = 'EACCES'; throw error; }
      return original.call(this, pathname, ...args);
    };
    assert.throws(() => { unexpected = createLearningService({ database, secrets }); }, /STORAGE_ERROR/);
    assert.equal(fs.existsSync(database), false);
  } finally { fs.lstatSync = original; if (unexpected) unexpected.close(); rmSync(directory, { recursive: true, force: true }); }
});

test('refuses a dangling database symlink when the platform allows creating one', t => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-symlink-test-'));
  const database = path.join(directory, 'learning.sqlite');
  try {
    try { fs.symlinkSync(path.join(directory, 'nonexistent-target'), database, 'file'); }
    catch (error) { if (['EPERM','EACCES','ENOSYS'].includes(error.code)) { t.skip('file symlink creation is unavailable'); return; } throw error; }
    assert.throws(() => createLearningService({ database, secrets }), /STORAGE_ERROR/);
    assert.equal(fs.lstatSync(database).isSymbolicLink(), true);
    assert.equal(fs.existsSync(path.join(directory, 'nonexistent-target')), false);
  } finally { rmSync(directory, { recursive: true, force: true }); }
});

test('asynchronous secret updates serialize with settings and a concurrent send', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-async-secret-test-'));
  let service, release;
  const gate = new Promise(resolve => { release = resolve; });
  const asyncSecrets = {
    isAsyncEncryptionAvailable: async () => true,
    encryptStringAsync: async value => { await gate; return Buffer.from(value).reverse(); },
    decryptStringAsync: async value => ({ result: Buffer.from(value).reverse().toString(), shouldReEncrypt: false })
  };
  try {
    let authorization, requestedModel;
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets: asyncSecrets, transport: async (_url, options) => {
      authorization = options.headers.authorization; requestedModel = JSON.parse(options.body).model;
      return new Response(JSON.stringify({ choices: [{ message: { content: '回答' } }] }), { status: 200 });
    } });
    const first = service.saveSettings({ baseUrl: 'https://example.test', model: 'first', apiKey: 'synthetic-async' });
    const second = service.saveSettings({ baseUrl: 'https://example.test', model: 'second' });
    const send = service.sendMessage({ text: '问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' });
    const all = Promise.all([first, second, send]);
    all.catch(() => {});
    assert.throws(() => service.close(), /BUSY/);
    release(); await all;
    assert.equal(authorization, 'Bearer synthetic-async');
    assert.equal(requestedModel, 'second');
    assert.equal((await service.getState()).settings.model, 'second');
  } finally { if (release) release(); if (service) { try { service.close(); } catch {} } rmSync(directory, { recursive: true, force: true }); }
});

test('cancellation during asynchronous key decryption prevents a request and keeps the draft', async () => {
  const directory = mkdtempSync(path.join(tmpdir(), 'treelearn-cancel-prepare-test-'));
  let service, release, decrypting, pending, count = 0;
  const gate = new Promise(resolve => { release = resolve; });
  const started = new Promise(resolve => { decrypting = resolve; });
  const asyncSecrets = {
    isAsyncEncryptionAvailable: async () => true,
    encryptStringAsync: async value => Buffer.from(value).reverse(),
    decryptStringAsync: async value => { decrypting(); await gate; return { result: Buffer.from(value).reverse().toString(), shouldReEncrypt: false }; }
  };
  try {
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets: asyncSecrets, transport: async () => { count++; throw new Error('must not send'); } });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic' });
    await service.saveDraft({ text: '未发送的草稿' });
    const requestId = '8e15d40d-5b17-46d5-a059-4e7765b6f4ac';
    pending = service.sendMessage({ text: '未发送的草稿', requestId });
    pending.catch(() => {});
    await started;
    assert.deepEqual(await service.cancelMessage({ requestId }), { cancelled: true });
    release();
    await assert.rejects(pending, /REQUEST_CANCELLED/);
    const state = await service.getState();
    assert.equal(count, 0); assert.equal(state.topics.length, 0); assert.equal(state.drafts.home, '未发送的草稿');
  } finally { if (release) release(); if (pending) await pending.catch(() => {}); if (service) { try { service.close(); } catch {} } rmSync(directory, { recursive: true, force: true }); }
});
