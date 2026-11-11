import test from 'node:test';
import assert from 'node:assert/strict';
import http from 'node:http';
import { mkdtempSync, rmSync } from 'node:fs';
import path from 'node:path';
import os from 'node:os';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const { createLearningService } = require('../learning-service.cjs');
const secrets = { isAsyncEncryptionAvailable: async () => true, encryptStringAsync: async value => Buffer.from(value).reverse(), decryptStringAsync: async value => ({ result: Buffer.from(value).reverse().toString(), shouldReEncrypt: false }) };

async function listen(server) { await new Promise(resolve => server.listen(0, '127.0.0.1', resolve)); return `http://127.0.0.1:${server.address().port}`; }
async function shutdown(server) { server.closeAllConnections(); await new Promise(resolve => server.close(resolve)); }

test('real HTTP transport streams answers and refuses redirecting credentials', async () => {
  const directory = mkdtempSync(path.join(os.tmpdir(), 'treelearn-http-test-'));
  let service, redirected = 0, seen, mode = 'stream';
  const target = http.createServer((_req, res) => { redirected++; res.end('unexpected'); });
  const targetUrl = await listen(target);
  const provider = http.createServer(async (req, res) => {
    const chunks = []; for await (const chunk of req) chunks.push(chunk);
    seen = { path: req.url, auth: req.headers.authorization, body: JSON.parse(Buffer.concat(chunks).toString()) };
    if (mode === 'redirect') { res.writeHead(302, { location: targetUrl }); res.end(); return; }
    res.writeHead(200, { 'content-type': 'text/event-stream; charset=utf-8' });
    res.write('data: {"choices":[{"delta":{"content":"真实"}}]}\n\n');
    setTimeout(() => { if (!res.destroyed) { res.write('data: {"choices":[{"delta":{"content":"流"}}]}\n\ndata: [DONE]\n\n'); res.end(); } }, 20);
  });
  const providerUrl = await listen(provider);
  try {
    // Only this test transport redirects the validated HTTPS address to a loopback mock server.
    service = createLearningService({ database: path.join(directory, 'learning.sqlite'), secrets, tutorPrompt: 'synthetic system prompt', transport: (_url, options) => fetch(`${providerUrl}/chat/completions`, options) });
    await service.saveSettings({ baseUrl: 'https://example.test', model: 'test', apiKey: 'synthetic-key' });
    const progress = [];
    await service.sendMessage({ text: '测试问题', requestId: '8e15d40d-5b17-46d5-a059-4e7765b6f4ac' }, value => progress.push(value.content));
    assert.equal(seen.path, '/chat/completions');
    assert.equal(seen.auth, 'Bearer synthetic-key');
    assert.equal(seen.body.stream, true);
    assert.deepEqual(seen.body.messages.map(m => m.role), ['system', 'user']);
    assert.deepEqual(progress, ['真实', '真实流']);
    mode = 'redirect';
    await assert.rejects(service.sendMessage({ text: '第二个问题', requestId: '9e15d40d-5b17-46d5-a059-4e7765b6f4ac' }), /NETWORK_ERROR/);
    assert.equal(redirected, 0);
    assert.equal((await service.getState()).topics[0].messages[0].status, 'failed');
  } finally {
    if (service) service.close();
    await shutdown(provider); await shutdown(target);
    rmSync(directory, { recursive: true, force: true });
  }
});
