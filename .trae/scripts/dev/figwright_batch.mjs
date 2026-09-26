// Figwright MCP batch driver: start the server once (cold start ~16s), speak the MCP handshake,
// then run a list of tool calls sequentially and print each result.
//
//   node .trae/scripts/dev/figwright_batch.mjs <commands.json>
//
// commands.json: [ { "tool": "ping", "args": {} }, { "tool": "create_frame", "args": { ... } } ]
// Exit code: 0 = every call answered (result text printed); 1 = handshake or any call failed.
// Results are truncated per call to keep console output reviewable; pass "full": true on a command
// to print it untruncated.

import { spawn } from "node:child_process";
import { readFileSync } from "node:fs";

const version = "0.5.0";
const packageSpec = `@figwright/mcp@${version}`;
const commandsPath = process.argv[2];
if (!commandsPath) {
  console.log("FAIL usage: node figwright_batch.mjs <commands.json>");
  console.log("EXITCODE=1");
  process.exit(1);
}
const commands = JSON.parse(readFileSync(commandsPath, "utf8"));
const MAX_CHARS = 6000;

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

// "$ref:N" in any args string resolves to the id returned by the N-th executed command
// (0-based, counting only calls that actually ran). We deep-search the stored result for the
// first id-ish key because tool responses wrap ids under different names (id / nodeId / pageId).
const results = [];
function findId(value) {
  if (Array.isArray(value)) {
    for (const item of value) {
      const found = findId(item);
      if (found) return found;
    }
    return null;
  }
  if (value && typeof value === "object") {
    for (const key of ["id", "nodeId", "pageId", "variableId", "collectionId"]) {
      if (typeof value[key] === "string") return value[key];
    }
    for (const item of Object.values(value)) {
      const found = findId(item);
      if (found) return found;
    }
  }
  return null;
}
function resolveRefs(value) {
  if (typeof value === "string") {
    const match = /^\$ref:(\d+)$/.exec(value);
    if (match) {
      const stored = results[Number(match[1])];
      const id = findId(stored);
      if (!id) throw new Error(`$ref:${match[1]} resolved to nothing`);
      return id;
    }
    return value;
  }
  if (Array.isArray(value)) return value.map(resolveRefs);
  if (value && typeof value === "object") {
    const out = {};
    for (const [k, v] of Object.entries(value)) out[k] = resolveRefs(v);
    return out;
  }
  return value;
}

let exitCode = 0;
try {
  await request(
    "initialize",
    { protocolVersion: "2025-06-18", capabilities: {}, clientInfo: { name: "treelearn-batch", version: "1.0.0" } },
    120000,
    "initialize"
  );
  notify("notifications/initialized", {});

  for (const cmd of commands) {
    if (cmd.tool === "$schema") {
      const list = await request("tools/list", {}, 60000, "tools/list");
      const tools = (list.result && list.result.tools) || [];
      const match = (cmd.match || []).map((m) => m.toLowerCase());
      const picked = tools.filter((t) => match.some((m) => t.name.toLowerCase().includes(m)));
      for (const t of picked) {
        console.log(`SCHEMA ${t.name} = ${JSON.stringify(t.inputSchema)}`);
      }
      console.log(`---END $schema (${picked.length} matched)---`);
      continue;
    }
    const label = `tools/call ${cmd.tool}`;
    try {
      const args = resolveRefs(cmd.args || {});
      const response = await request("tools/call", { name: cmd.tool, arguments: args }, cmd.timeoutMs || 90000, label);
      const result = response.result || {};
      const text = (result.content || []).map((c) => c.text || "").join("\n");
      // Store the parsed payload when the tool answered with a JSON object so $ref resolution can
      // see ids like { ok, nodeId } returned inside the text content.
      let stored = result;
      try {
        const parsed = JSON.parse(text);
        if (parsed && typeof parsed === "object") stored = parsed;
      } catch {
        /* non-JSON answer (e.g. plain text) — keep raw result */
      }
      results.push(stored);
      const isError = result.isError === true;
      const body = cmd.full === true || text.length <= MAX_CHARS ? text : `${text.slice(0, MAX_CHARS)}\n...[truncated ${text.length - MAX_CHARS} chars]`;
      console.log(`TOOL=${cmd.tool} ERROR=${isError}`);
      console.log(body);
      console.log(`---END ${cmd.tool}---`);
      if (isError) exitCode = 1;
    } catch (error) {
      console.log(`TOOL=${cmd.tool} ERROR=timeout-or-transport`);
      console.log(`FAIL ${error.message}`);
      console.log(`---END ${cmd.tool}---`);
      exitCode = 1;
      break;
    }
  }
} catch (error) {
  console.log(`FAIL ${error.message}`);
  exitCode = 1;
} finally {
  stopServer();
}
console.log(`EXITCODE=${exitCode}`);
process.exit(exitCode);
