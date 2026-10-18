// Figwright MCP health probe: start the server the same way Codex does, speak the MCP handshake,
// and print what it actually answered. Nothing here is inferred; every line is printed by the run.
//
//   node .codex/scripts/dev/probe_figwright_mcp.mjs [version]     (default version: 0.5.0)
//
// Exit code: 0 = handshake + tools/list + ping all answered; 1 = anything else (fail closed).
// Needs no Figma plugin: "plugin: null" in the ping result only means the Figma side is not open.
// A global "figwright" MCP server may already own port 3055; this probe spawns its own process and
// becomes a follower (or leader) without touching the other one.

import { spawn } from "node:child_process";
import { performance } from "node:perf_hooks";

const version = process.argv[2] || "0.5.0";
const packageSpec = `@figwright/mcp@${version}`;

// npx on Windows is a cmd shim: killing the shim leaves the real server process behind, so kill
// the whole tree by pid. Failures here are irrelevant to the probe's verdict.
function stopServer() {
  if (process.platform === "win32") {
    try {
      spawn("taskkill", ["/pid", String(child.pid), "/T", "/F"], { stdio: "ignore" });
    } catch {
      /* ignore */
    }
  } else {
    child.kill();
  }
}

function fail(message) {
  console.log(`FAIL ${message}`);
  console.log("EXITCODE=1");
  process.exit(1);
}

const t0 = performance.now();
const child = spawn("npx", ["-y", packageSpec], {
  stdio: ["pipe", "pipe", "pipe"],
  shell: process.platform === "win32",
});

let buffer = "";
const stderr = [];
const pending = new Map();
let nextId = 1;

child.stderr.on("data", (d) => stderr.push(String(d)));
child.stdout.on("data", (d) => {
  buffer += String(d);
  let index;
  while ((index = buffer.indexOf("\n")) >= 0) {
    const line = buffer.slice(0, index).trim();
    buffer = buffer.slice(index + 1);
    if (!line) continue;
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      continue;
    }
    const resolve = pending.get(message.id);
    if (resolve) {
      pending.delete(message.id);
      resolve(message);
    }
  }
});

function request(method, params, timeoutMs, label) {
  const id = nextId++;
  const answered = new Promise((resolve) => pending.set(id, resolve));
  child.stdin.write(`${JSON.stringify({ jsonrpc: "2.0", id, method, params })}\n`);
  return Promise.race([
    answered,
    new Promise((_, reject) => setTimeout(() => reject(new Error(`timeout:${label}`)), timeoutMs)),
  ]);
}

function notify(method, params) {
  child.stdin.write(`${JSON.stringify({ jsonrpc: "2.0", method, params })}\n`);
}

try {
  console.log(`COMMAND=npx -y ${packageSpec}`);
  const init = await request(
    "initialize",
    { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "treelearn-probe", version: "1.0.0" } },
    120000,
    "initialize"
  );
  const info = init.result && init.result.serverInfo;
  if (!info || !info.name) fail(`initialize returned no serverInfo: ${JSON.stringify(init).slice(0, 300)}`);
  console.log(`INIT_MS=${Math.round(performance.now() - t0)}`);
  console.log(`SERVER=${info.name}@${info.version}`);
  console.log(`SERVER_VERSION_MATCHES_REQUEST=${info.version === version}`);
  console.log(`PROTOCOL=${init.result.protocolVersion}`);
  console.log(`INSTRUCTIONS_CHARS=${(init.result.instructions || "").length}`);

  notify("notifications/initialized", {});

  const list = await request("tools/list", {}, 60000, "tools/list");
  const names = ((list.result && list.result.tools) || []).map((t) => t.name).sort();
  if (names.length === 0) fail("tools/list returned zero tools");
  console.log(`TOOL_COUNT=${names.length}`);
  console.log(`TOOLS=${names.join(",")}`);

  const ping = await request("tools/call", { name: "ping", arguments: {} }, 60000, "tools/call ping");
  const text = ((ping.result && ping.result.content) || []).map((c) => c.text || "").join("");
  if (!text) fail("ping returned no content");
  const payload = JSON.parse(text);
  console.log(`PING_OK=${payload.ok === true}`);
  console.log(`PING_ROLE=${payload.server && payload.server.role}`);
  console.log(`PING_RELAY_PORT=${payload.server && payload.server.port}`);
  console.log(`PLUGIN_CONNECTED=${payload.plugin !== null && payload.plugin !== undefined}`);
  console.log(`PLUGIN_SESSIONS=${payload.sessions && payload.sessions.connectedCount}`);
} catch (error) {
  fail(error.message);
} finally {
  stopServer();
  for (const line of stderr.join("").split("\n").filter((l) => l.trim())) {
    console.log(`SERVER_STDERR=${line.trim()}`);
  }
}
console.log("EXITCODE=0");
// The relay keeps the event loop alive on purpose; the probe's own answer is already printed, so
// exit explicitly instead of hanging until a timeout kills the caller.
process.exit(0);