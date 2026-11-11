/* Normal Electron process exit, followed by a separate process reopening the same isolated profile. */
const { spawnSync } = require('node:child_process');
const electron = require('electron');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');

const root = fs.mkdtempSync(path.join(os.tmpdir(), 'treelearn-cold-e2e-'));
const phaseScript = path.join(__dirname, 'desktop-cold-restart-phase.cjs');
for (const phase of ['write', 'reopen']) {
  const result = spawnSync(electron, [phaseScript, phase, root], { encoding: 'utf8', timeout: 20000, windowsHide: true });
  if (result.error || result.status !== 0 || !result.stdout.includes(`COLD_RESTART_${phase.toUpperCase()}_OK`)) {
    console.error(`DESKTOP_COLD_RESTART_FAIL phase=${phase} exit=${result.status} error=${result.error?.message || ''}`);
    console.error(result.stderr);
    process.exit(1);
  }
  if (result.stdout.includes('synthetic-cold-restart-key') || result.stderr.includes('synthetic-cold-restart-key')) {
    console.error('DESKTOP_COLD_RESTART_FAIL secret output');
    process.exit(1);
  }
  console.log(`COLD_RESTART_${phase.toUpperCase()}_PROCESS_OK`);
}
console.log('DESKTOP_COLD_RESTART_OK');
