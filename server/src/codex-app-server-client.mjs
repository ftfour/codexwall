import { spawn } from "node:child_process";
import readline from "node:readline";

export async function readRateLimitsFromAppServer(options = {}) {
  const command = options.command || process.env.CODEX_APP_SERVER_COMMAND || "codex";
  const args = options.args || splitArgs(process.env.CODEX_APP_SERVER_ARGS || "app-server");
  const timeoutMs = Number.parseInt(process.env.CODEX_APP_SERVER_TIMEOUT_MS || "30000", 10);

  const client = new JsonRpcStdioClient(command, args, timeoutMs);
  try {
    await client.start();
    await client.request("initialize", {
      clientInfo: {
        name: "codexwall",
        title: "Codex Limits Wallpaper VDS collector",
        version: "1.0.0"
      },
      capabilities: {
        experimentalApi: true,
        optOutNotificationMethods: [
          "thread/started",
          "item/started",
          "item/completed",
          "item/agentMessage/delta"
        ]
      }
    });
    client.notify("initialized", {});
    return await client.request("account/rateLimits/read", {});
  } finally {
    client.close();
  }
}

export function normalizeCodexRateLimits(response, now = new Date()) {
  const source = response?.rateLimits ?? response?.result?.rateLimits ?? response;
  if (!source || typeof source !== "object") {
    throw new Error("Codex app-server response does not contain rateLimits");
  }

  const buckets = collectBuckets(source);
  const fiveHour = pickWindow(buckets, 300, { max: 24 * 60 }) || pickPrimary(source);
  const weekly = pickWindow(buckets, 10_080, { min: 24 * 60 }) || pickSecondary(source);

  if (!fiveHour?.resetsAt) {
    throw new Error("Codex app-server response does not contain a five-hour reset window");
  }
  if (!weekly?.resetsAt) {
    throw new Error("Codex app-server response does not contain a weekly reset window");
  }

  return {
    five_hour_percent_left: percentLeft(fiveHour.usedPercent),
    five_hour_resets_at: unixSecondsToIso(fiveHour.resetsAt),
    weekly_percent_left: percentLeft(weekly.usedPercent),
    weekly_resets_at: unixSecondsToIso(weekly.resetsAt),
    updated_at: now.toISOString()
  };
}

class JsonRpcStdioClient {
  constructor(command, args, timeoutMs) {
    this.command = command;
    this.args = args;
    this.timeoutMs = timeoutMs;
    this.nextId = 1;
    this.pending = new Map();
    this.stderr = "";
    this.proc = null;
    this.rl = null;
  }

  async start() {
    this.proc = spawn(this.command, this.args, {
      stdio: ["pipe", "pipe", "pipe"],
      env: process.env
    });

    this.proc.stderr.on("data", (chunk) => {
      this.stderr += chunk.toString("utf8");
    });
    this.proc.on("exit", (code, signal) => {
      const error = new Error(`codex app-server exited before response (code=${code}, signal=${signal})${this.stderr ? `: ${this.stderr.trim()}` : ""}`);
      for (const pending of this.pending.values()) pending.reject(error);
      this.pending.clear();
    });

    this.rl = readline.createInterface({ input: this.proc.stdout });
    this.rl.on("line", (line) => this.handleLine(line));

    await new Promise((resolve, reject) => {
      this.proc.once("spawn", resolve);
      this.proc.once("error", reject);
    });
  }

  request(method, params) {
    const id = this.nextId++;
    this.send({ method, id, params });
    return new Promise((resolve, reject) => {
      const timer = setTimeout(() => {
        this.pending.delete(id);
        reject(new Error(`Timed out waiting for ${method}`));
      }, this.timeoutMs);
      this.pending.set(id, {
        resolve: (value) => {
          clearTimeout(timer);
          resolve(value);
        },
        reject: (error) => {
          clearTimeout(timer);
          reject(error);
        }
      });
    });
  }

  notify(method, params) {
    this.send({ method, params });
  }

  send(message) {
    this.proc.stdin.write(`${JSON.stringify(message)}\n`);
  }

  handleLine(line) {
    if (!line.trim()) return;
    let message;
    try {
      message = JSON.parse(line);
    } catch {
      return;
    }
    if (message.id == null || !this.pending.has(message.id)) return;
    const pending = this.pending.get(message.id);
    this.pending.delete(message.id);
    if (message.error) {
      pending.reject(new Error(message.error.message || JSON.stringify(message.error)));
      return;
    }
    pending.resolve(message.result ?? {});
  }

  close() {
    if (this.rl) this.rl.close();
    if (this.proc && !this.proc.killed) {
      this.proc.kill("SIGTERM");
      setTimeout(() => {
        if (this.proc && !this.proc.killed) this.proc.kill("SIGKILL");
      }, 1000).unref?.();
    }
  }
}

function collectBuckets(rateLimits) {
  const buckets = [];
  addBucket(buckets, rateLimits.primary);
  addBucket(buckets, rateLimits.secondary);
  if (rateLimits.rateLimits && rateLimits.rateLimits !== rateLimits) {
    addBucket(buckets, rateLimits.rateLimits.primary);
    addBucket(buckets, rateLimits.rateLimits.secondary);
  }
  const byId = rateLimits.rateLimitsByLimitId || {};
  for (const value of Object.values(byId)) {
    addBucket(buckets, value?.primary);
    addBucket(buckets, value?.secondary);
  }
  return buckets;
}

function addBucket(buckets, bucket) {
  if (!bucket || typeof bucket !== "object") return;
  if (bucket.usedPercent == null || bucket.resetsAt == null) return;
  buckets.push(bucket);
}

function pickWindow(buckets, targetMins, bounds) {
  const filtered = buckets.filter((bucket) => {
    const mins = Number(bucket.windowDurationMins);
    if (!Number.isFinite(mins)) return false;
    if (bounds?.min != null && mins < bounds.min) return false;
    if (bounds?.max != null && mins > bounds.max) return false;
    return true;
  });
  filtered.sort((a, b) => Math.abs(Number(a.windowDurationMins) - targetMins) - Math.abs(Number(b.windowDurationMins) - targetMins));
  return filtered[0] || null;
}

function pickPrimary(rateLimits) {
  return rateLimits.primary || rateLimits.rateLimits?.primary || null;
}

function pickSecondary(rateLimits) {
  return rateLimits.secondary || rateLimits.rateLimits?.secondary || null;
}

function percentLeft(usedPercent) {
  const used = Number(usedPercent);
  if (!Number.isFinite(used)) throw new Error(`Invalid usedPercent: ${usedPercent}`);
  return Math.max(0, Math.min(100, Math.round(100 - used)));
}

function unixSecondsToIso(value) {
  const seconds = Number(value);
  if (!Number.isFinite(seconds)) throw new Error(`Invalid resetsAt: ${value}`);
  return new Date(seconds * 1000).toISOString();
}

function splitArgs(value) {
  const args = [];
  const pattern = /"([^"]*)"|'([^']*)'|[^\s]+/g;
  for (const match of value.matchAll(pattern)) {
    args.push(match[1] ?? match[2] ?? match[0]);
  }
  return args;
}
