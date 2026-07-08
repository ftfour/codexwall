#!/usr/bin/env bash
set -euo pipefail

APP_DIR="/opt/codexwall"
APP_USER="codexwall"

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Run as root: sudo bash server/scripts/install-vds.sh"
  exit 1
fi

if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd --system --create-home --home-dir "$APP_DIR" --shell /bin/bash "$APP_USER"
fi

mkdir -p "$APP_DIR"
rsync -a --delete --exclude node_modules "$(dirname "$0")/.." "$APP_DIR/server"
chown -R "$APP_USER:$APP_USER" "$APP_DIR"

if [[ ! -f "$APP_DIR/server/.env" ]]; then
  cp "$APP_DIR/server/.env.example" "$APP_DIR/server/.env"
  chown "$APP_USER:$APP_USER" "$APP_DIR/server/.env"
  chmod 600 "$APP_DIR/server/.env"
fi

cp "$APP_DIR/server/systemd/codexwall.service" /etc/systemd/system/codexwall.service
cp "$APP_DIR/server/systemd/codexwall-collector.service" /etc/systemd/system/codexwall-collector.service
cp "$APP_DIR/server/systemd/codexwall-collector.timer" /etc/systemd/system/codexwall-collector.timer

systemctl daemon-reload
systemctl enable --now codexwall.service

echo "Installed. Edit $APP_DIR/server/.env, then run:"
echo "  systemctl restart codexwall.service"
echo "  systemctl enable --now codexwall-collector.timer"
