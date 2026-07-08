import http from "node:http";
import crypto from "node:crypto";
import { URL } from "node:url";
import { readLimits, writeLimits } from "./limits.mjs";

const host = process.env.HOST || "127.0.0.1";
const port = Number.parseInt(process.env.PORT || "8787", 10);
const adminUser = process.env.ADMIN_USER || "admin";
const adminPassword = process.env.ADMIN_PASSWORD || "";
const internalToken = process.env.INTERNAL_TOKEN || "";

const server = http.createServer(async (req, res) => {
  try {
    const url = new URL(req.url || "/", `http://${req.headers.host || "localhost"}`);

    if (req.method === "GET" && url.pathname === "/health") {
      return sendText(res, 200, "ok\n");
    }

    if (req.method === "GET" && url.pathname === "/api/codex-limits") {
      return sendJson(res, 200, await readLimits());
    }

    if (url.pathname === "/admin") {
      if (!isAuthorized(req)) return requestAuth(res);
      if (req.method === "GET") return sendHtml(res, 200, renderAdmin(await readLimits(), ""));
      if (req.method === "POST") {
        const form = new URLSearchParams(await readBody(req));
        const saved = await writeLimits({
          five_hour_percent_left: form.get("five_hour_percent_left"),
          five_hour_resets_at: form.get("five_hour_resets_at"),
          weekly_percent_left: form.get("weekly_percent_left"),
          weekly_resets_at: form.get("weekly_resets_at"),
          updated_at: new Date().toISOString()
        });
        return sendHtml(res, 200, renderAdmin(saved, "Saved"));
      }
    }

    if (req.method === "POST" && url.pathname === "/internal/limits") {
      if (!internalToken || req.headers.authorization !== `Bearer ${internalToken}`) {
        return sendJson(res, 401, { error: "unauthorized" });
      }
      const saved = await writeLimits(JSON.parse(await readBody(req)));
      return sendJson(res, 200, saved);
    }

    return sendJson(res, 404, { error: "not_found" });
  } catch (error) {
    return sendJson(res, 400, { error: error.message || "bad_request" });
  }
});

server.listen(port, host, () => {
  console.log(`codexwall server listening on http://${host}:${port}`);
});

function isAuthorized(req) {
  if (!adminPassword) return false;
  const header = req.headers.authorization || "";
  if (!header.startsWith("Basic ")) return false;
  const decoded = Buffer.from(header.slice("Basic ".length), "base64").toString("utf8");
  const index = decoded.indexOf(":");
  const user = decoded.slice(0, index);
  const password = decoded.slice(index + 1);
  return timingSafeEqual(user, adminUser) && timingSafeEqual(password, adminPassword);
}

function timingSafeEqual(a, b) {
  const left = Buffer.from(a);
  const right = Buffer.from(b);
  return left.length === right.length && crypto.timingSafeEqual(left, right);
}

function requestAuth(res) {
  res.writeHead(401, {
    "WWW-Authenticate": 'Basic realm="Codex Limits"',
    "Content-Type": "text/plain; charset=utf-8"
  });
  res.end("Authentication required\n");
}

function sendJson(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(`${JSON.stringify(body)}\n`);
}

function sendText(res, status, body) {
  res.writeHead(status, { "Content-Type": "text/plain; charset=utf-8" });
  res.end(body);
}

function sendHtml(res, status, body) {
  res.writeHead(status, {
    "Content-Type": "text/html; charset=utf-8",
    "Cache-Control": "no-store"
  });
  res.end(body);
}

async function readBody(req) {
  const chunks = [];
  for await (const chunk of req) chunks.push(chunk);
  return Buffer.concat(chunks).toString("utf8");
}

function renderAdmin(limits, message) {
  return `<!doctype html>
<html lang="ru">
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1">
  <title>Codex Limits</title>
  <style>
    :root { color-scheme: dark; font-family: system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
    body { margin: 0; background: #050505; color: #fff; }
    main { width: min(520px, calc(100vw - 32px)); margin: 32px auto; }
    h1 { font-size: 22px; font-weight: 600; margin: 0 0 24px; }
    label { display: block; color: #aaa; font-size: 12px; text-transform: uppercase; margin: 18px 0 6px; }
    input { width: 100%; box-sizing: border-box; background: #101010; color: #fff; border: 1px solid #333; padding: 12px; font-size: 16px; border-radius: 6px; }
    button { margin-top: 22px; width: 100%; border: 0; border-radius: 6px; padding: 13px 16px; font-size: 16px; color: #fff; background: #e53935; }
    p { color: #aaa; }
    .ok { color: #66bb6a; min-height: 20px; }
    code { color: #ddd; }
  </style>
</head>
<body>
  <main>
    <h1>Codex Limits</h1>
    <form method="post" action="/admin">
      <label>5 hours percent left</label>
      <input name="five_hour_percent_left" type="number" min="0" max="100" value="${escapeHtml(limits.five_hour_percent_left)}" required>
      <label>5 hours resets at</label>
      <input name="five_hour_resets_at" value="${escapeHtml(limits.five_hour_resets_at)}" required>
      <label>Weekly percent left</label>
      <input name="weekly_percent_left" type="number" min="0" max="100" value="${escapeHtml(limits.weekly_percent_left)}" required>
      <label>Weekly resets at</label>
      <input name="weekly_resets_at" value="${escapeHtml(limits.weekly_resets_at)}" required>
      <button type="submit">Save</button>
    </form>
    <p class="ok">${escapeHtml(message)}</p>
    <p>Endpoint: <code>/api/codex-limits</code></p>
  </main>
</body>
</html>`;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}
