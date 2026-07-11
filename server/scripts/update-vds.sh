#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/codexwall"
APP_USER="codexwall"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SOURCE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash server/scripts/update-vds.sh"
  exit 1
fi

if [[ ! -d "$APP_DIR/server" ]]; then
  echo "$APP_DIR/server does not exist. Run install-vds.sh first."
  exit 1
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
  echo "User $APP_USER does not exist. Run install-vds.sh first."
  exit 1
fi

backup_dir="$APP_DIR/backups/server-update-$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$backup_dir"

if [[ -f "$APP_DIR/server/.env" ]]; then
  cp -a "$APP_DIR/server/.env" "$backup_dir/.env"
fi

if [[ -d "$APP_DIR/server/data" ]]; then
  mkdir -p "$backup_dir/data"
  cp -a "$APP_DIR/server/data/." "$backup_dir/data/"
fi

rsync -a --delete \
  --exclude node_modules \
  --exclude .env \
  --exclude data \
  "$SOURCE_DIR/" "$APP_DIR/server/"

chown -R "$APP_USER:$APP_USER" "$APP_DIR"
chmod 600 "$APP_DIR/server/.env" 2>/dev/null || true

cp "$APP_DIR/server/systemd/codexwall.service" /etc/systemd/system/codexwall.service
cp "$APP_DIR/server/systemd/codexwall-collector.service" /etc/systemd/system/codexwall-collector.service
cp "$APP_DIR/server/systemd/codexwall-collector.timer" /etc/systemd/system/codexwall-collector.timer

systemctl daemon-reload
systemctl restart codexwall.service

echo "Updated codexwall server."
echo "Preserved:"
echo "  $APP_DIR/server/.env"
echo "  $APP_DIR/server/data"
echo "Backup:"
echo "  $backup_dir"
echo "Check:"
echo "  systemctl status codexwall.service --no-pager"
echo "  curl http://127.0.0.1:8787/api/codex-limits"
