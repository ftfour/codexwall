import readline from "node:readline";

const rl = readline.createInterface({ input: process.stdin });

rl.on("line", (line) => {
  if (!line.trim()) return;
  const message = JSON.parse(line);
  if (message.method === "initialize") {
    respond(message.id, { protocolVersion: "2025-01-01", serverInfo: { name: "fake-codex", version: "1.0.0" } });
    return;
  }
  if (message.method === "account/rateLimits/read") {
    respond(message.id, {
      rateLimits: {
        limitId: "codex",
        primary: {
          usedPercent: 37,
          windowDurationMins: 300,
          resetsAt: 1783416420
        },
        secondary: {
          usedPercent: 72,
          windowDurationMins: 10080,
          resetsAt: 1783750800
        }
      }
    });
  }
});

function respond(id, result) {
  process.stdout.write(`${JSON.stringify({ id, result })}\n`);
}
