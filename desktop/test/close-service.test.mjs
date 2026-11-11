import test from 'node:test';
import assert from 'node:assert/strict';
import { createRequire } from 'node:module';
const require = createRequire(import.meta.url);
const { closeService } = require('../close-service.cjs');

test('a final save failure keeps the window open and reports a safe actionable error', () => {
  let prevented = false, cleared = false, alert;
  const event = { preventDefault: () => { prevented = true; } };
  let fail = true;
  const service = { close: () => { if (fail) throw new Error('private file path'); } };
  closeService(event, () => service, () => { cleared = true; }, (title, body) => { alert = { title, body }; });
  assert.equal(prevented, true); assert.equal(cleared, false);
  assert.match(alert.body, /磁盘空间和权限/);
  assert.equal(JSON.stringify(alert).includes('private file path'), false);
  fail = false; prevented = false;
  closeService(event, () => service, () => { cleared = true; }, () => { throw new Error('unexpected alert'); });
  assert.equal(prevented, false); assert.equal(cleared, true);
});
