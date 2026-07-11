import assert from "node:assert/strict";
import fs from "node:fs/promises";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import { spawn } from "node:child_process";
import { fileURLToPath } from "node:url";

const port = await freePort();
const dataDir = await fs.mkdtemp(path.join(os.tmpdir(), "codexwall-refresh-"));
const server = spawn(process.execPath, ["src/server.mjs"], {
  cwd: fileURLToPath(new URL("..", import.meta.url)),
  env: {
    ...process.env,
    HOST: "127.0.0.1",
    PORT: String(port),
    DATA_DIR: dataDir,
    REFRESH_TOKEN: "test-refresh-token",
    REFRESH_MIN_INTERVAL_SECONDS: "0",
    APP_UPDATE_VERSION: "9.8.7",
    APP_UPDATE_APK_URL: "https://example.test/app.apk",
    APP_UPDATE_PAGE_URL: "https://example.test/release",
    APP_UPDATE_NOTES: "Test release",
    CODEX_APP_SERVER_COMMAND: process.execPath,
    CODEX_APP_SERVER_ARGS: "tests/fake-app-server.mjs"
  },
  stdio: ["ignore", "pipe", "pipe"]
});

try {
  await waitForServer(server);

  const health = await fetchJson(`http://127.0.0.1:${port}/health`);
  assert.equal(health.ok, true);
  assert.equal(health.refresh_auth_required, true);

  const app = await fetchJson(`http://127.0.0.1:${port}/app/latest`);
  assert.equal(app.version, "9.8.7");
  assert.equal(app.apk_url, "https://example.test/app.apk");

  const unauthorized = await fetch(`http://127.0.0.1:${port}/api/codex-limits/refresh`, { method: "POST" });
  assert.equal(unauthorized.status, 401);

  const refresh = await fetchJson(`http://127.0.0.1:${port}/api/codex-limits/refresh`, {
    method: "POST",
    headers: { Authorization: "Bearer test-refresh-token" }
  });
  assert.equal(refresh.five_hour_percent_left, 63);
  assert.equal(refresh.weekly_percent_left, 28);

  const snapshot = await fetchJson(`http://127.0.0.1:${port}/api/codex-limits`);
  assert.deepEqual(snapshot, refresh);

  console.log(JSON.stringify(refresh));
} finally {
  server.kill("SIGTERM");
  await fs.rm(dataDir, { recursive: true, force: true });
}

async function fetchJson(url, options) {
  const response = await fetch(url, options);
  assert.equal(response.status, 200);
  return response.json();
}

function waitForServer(proc) {
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => reject(new Error("Timed out waiting for test server")), 5000);
    proc.stdout.on("data", (chunk) => {
      if (chunk.toString("utf8").includes("codexwall server listening")) {
        clearTimeout(timer);
        resolve();
      }
    });
    proc.stderr.on("data", (chunk) => {
      process.stderr.write(chunk);
    });
    proc.on("exit", (code, signal) => {
      clearTimeout(timer);
      reject(new Error(`Test server exited early: code=${code}, signal=${signal}`));
    });
  });
}

function freePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      server.close(() => resolve(address.port));
    });
    server.on("error", reject);
  });
}
