# Codexwall VDS server

This folder contains a tiny dependency-free Node.js server for the Android live wallpaper.

It provides:

- `GET /api/codex-limits` for the Android app.
- `GET/POST /admin` protected by Basic Auth for manual updates from your phone.
- `POST /internal/limits` for trusted JSON pushes.
- `collect-codex-limits.mjs`, a systemd-friendly collector that starts `codex app-server`, calls `account/rateLimits/read`, and writes `data/limits.json`.

## Important

The default collector uses Codex app-server:

```env
CODEX_COLLECTOR_MODE=app-server
CODEX_APP_SERVER_COMMAND=codex
CODEX_APP_SERVER_ARGS=app-server
```

It sends JSON-RPC over stdio:

```json
{ "method": "account/rateLimits/read", "id": 2 }
```

Then it maps Codex `usedPercent` to the wallpaper's percent-left fields:

```text
five_hour_percent_left = 100 - primary.usedPercent
weekly_percent_left    = 100 - secondary.usedPercent
```

If the app-server method changes or is unavailable in your installed Codex CLI, you can use command fallback:

```env
CODEX_COLLECTOR_MODE=command
CODEX_LIMITS_COMMAND=/path/to/custom-script-that-prints-json
```

The fallback command must print this final JSON shape:

```json
{
  "five_hour_percent_left": 63,
  "five_hour_resets_at": "2026-07-06T18:27:00+03:00",
  "weekly_percent_left": 28,
  "weekly_resets_at": "2026-07-10T14:20:00+03:00",
  "updated_at": "2026-07-06T16:09:00+03:00"
}
```

If automatic collection is not available yet, the server still works through `/admin`.

## Local run

```bash
cd server
cp .env.example .env
node src/server.mjs
```

Open:

```text
http://127.0.0.1:8787/api/codex-limits
http://127.0.0.1:8787/admin
```

## VDS install

On the VDS:

```bash
sudo apt update
sudo apt install -y nodejs npm nginx rsync
sudo bash server/scripts/install-vds.sh
```

Edit:

```bash
sudo nano /opt/codexwall/server/.env
```

Set strong values:

```env
ADMIN_USER=admin
ADMIN_PASSWORD=very-long-password
INTERNAL_TOKEN=another-long-secret
```

Restart:

```bash
sudo systemctl restart codexwall.service
```

Check:

```bash
curl http://127.0.0.1:8787/api/codex-limits
```

## VDS update without changing config or data

Use this when the server is already installed in `/opt/codexwall/server` and you want to update code/systemd units only. It preserves:

- `/opt/codexwall/server/.env`
- `/opt/codexwall/server/data/`
- existing Nginx configuration
- whether `codexwall-collector.timer` is enabled or disabled

On the VDS, from a fresh checkout or unpacked release:

```bash
sudo bash server/scripts/update-vds.sh
```

The script:

1. Backs up the current `.env` and `data/` to `/opt/codexwall/backups/server-update-<timestamp>/`.
2. Syncs the new `server/` files while excluding `.env`, `data/`, and `node_modules`.
3. Updates the systemd unit files.
4. Runs `systemctl daemon-reload`.
5. Restarts only `codexwall.service`.

Check after update:

```bash
systemctl status codexwall.service --no-pager
curl http://127.0.0.1:8787/api/codex-limits
```

## Codex CLI on VDS

Install and authenticate Codex CLI under the same user that runs the collector:

```bash
sudo -iu codexwall
curl -fsSL https://chatgpt.com/codex/install.sh | sh
codex --version
codex login
codex --help
codex app-server --help
exit
```

Check that the collector can talk to app-server:

```bash
sudo -iu codexwall
cd /opt/codexwall/server
set -a && source .env && set +a
node src/collect-codex-limits.mjs
cat data/limits.json
exit
```

If `codex` is not in PATH for the `codexwall` user, set the absolute path:

```env
CODEX_APP_SERVER_COMMAND=/opt/codexwall/.local/bin/codex
```

Enable timer:

```bash
sudo systemctl enable --now codexwall-collector.timer
systemctl list-timers | grep codexwall
```

Logs:

```bash
journalctl -u codexwall.service -f
journalctl -u codexwall-collector.service -n 100
```

## Nginx and HTTPS

Copy `nginx/codexwall.conf` to `/etc/nginx/sites-available/codexwall`, edit `server_name`, then:

```bash
sudo ln -s /etc/nginx/sites-available/codexwall /etc/nginx/sites-enabled/codexwall
sudo nginx -t
sudo systemctl reload nginx
```

For HTTPS, use Certbot or Caddy. With Certbot:

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d your-domain.example
```

Use this URL in the Android app:

```text
https://your-domain.example/api/codex-limits
```
