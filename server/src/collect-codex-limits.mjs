import { exec } from "node:child_process";
import { promisify } from "node:util";
import { writeLimits } from "./limits.mjs";
import { normalizeCodexRateLimits, readRateLimitsFromAppServer } from "./codex-app-server-client.mjs";

const execAsync = promisify(exec);
const command = process.env.CODEX_LIMITS_COMMAND;
const mode = process.env.CODEX_COLLECTOR_MODE || "app-server";

try {
  const limits = mode === "command"
    ? await readFromCommand()
    : await readFromAppServer();
  const saved = await writeLimits(limits);
  console.log(JSON.stringify(saved));
} catch (error) {
  console.error(error.message || String(error));
  process.exit(1);
}

async function readFromAppServer() {
  const response = await readRateLimitsFromAppServer();
  return normalizeCodexRateLimits(response);
}

async function readFromCommand() {
  if (!command || command.trim() === "") {
    throw new Error("CODEX_LIMITS_COMMAND is empty. Set CODEX_COLLECTOR_MODE=app-server or provide a command that prints limits JSON.");
  }

  const { stdout, stderr } = await execAsync(command, {
    timeout: 120_000,
    maxBuffer: 1024 * 1024,
    env: process.env
  });

  if (stderr.trim()) console.error(stderr.trim());
  return JSON.parse(extractJson(stdout));
}

function extractJson(output) {
  const trimmed = output.trim();
  if (trimmed.startsWith("{")) return trimmed;
  const start = trimmed.indexOf("{");
  const end = trimmed.lastIndexOf("}");
  if (start >= 0 && end > start) return trimmed.slice(start, end + 1);
  throw new Error("Collector command did not print JSON");
}
