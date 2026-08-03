#!/usr/bin/env bash
#
# Puts nginx in front of the backend services and terminates TLS for the API
# domain, so every app can talk to ONE https origin instead of a dozen
# cleartext http://<ip>:80xx ports.
#
# Idempotent: safe to re-run on every deploy. It reinstalls the vhost and
# reloads nginx, but only requests a certificate when one is missing or is
# within 30 days of expiry (certbot's own renew timer handles the rest).
#
# Env:
#   API_DOMAIN   domain to serve + certify   (default api.ggfix.in)
#   CERTBOT_EMAIL  registration/expiry email (default admin@<apex domain>)
#   SKIP_TLS=1   install the vhost on port 80 only, do not touch certbot
#
set -euo pipefail

API_DOMAIN="${API_DOMAIN:-api.ggfix.in}"
CERTBOT_EMAIL="${CERTBOT_EMAIL:-admin@${API_DOMAIN#*.}}"
SKIP_TLS="${SKIP_TLS:-0}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VHOST_SRC="$SCRIPT_DIR/nginx-ggfix-api.conf"
VHOST_DST="/etc/nginx/conf.d/ggfix-api.conf"

log() { echo "[setup-nginx-tls] $*"; }

install_packages() {
  if command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y nginx
    # AL2023 ships certbot in the main repo; fall back to pip when absent.
    if ! sudo dnf install -y certbot python3-certbot-nginx; then
      log "certbot rpm unavailable, installing via pip"
      sudo dnf install -y python3-pip
      sudo python3 -m pip install --upgrade certbot certbot-nginx
      sudo ln -sf "$(command -v certbot || echo /usr/local/bin/certbot)" /usr/bin/certbot
    fi
  elif command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y nginx certbot python3-certbot-nginx
  else
    echo "Unsupported package manager. Install nginx + certbot manually." >&2
    exit 1
  fi
}

install_vhost() {
  if [[ ! -f "$VHOST_SRC" ]]; then
    echo "Missing $VHOST_SRC" >&2
    exit 1
  fi

  sudo mkdir -p /var/www/html

  # Re-installing the plain :80 vhost would drop certbot's TLS edits, so once a
  # certificate exists we leave the live vhost alone and only validate it.
  if [[ -d "/etc/letsencrypt/live/$API_DOMAIN" && -f "$VHOST_DST" ]]; then
    log "certificate present; keeping existing vhost (certbot-managed)"
  else
    log "installing vhost for $API_DOMAIN -> $VHOST_DST"
    sed "s/__SERVER_NAME__/$API_DOMAIN/g" "$VHOST_SRC" | sudo tee "$VHOST_DST" >/dev/null
  fi

  sudo nginx -t
  sudo systemctl enable nginx
  sudo systemctl reload nginx 2>/dev/null || sudo systemctl restart nginx
}

cert_needs_renewal() {
  local cert="/etc/letsencrypt/live/$API_DOMAIN/fullchain.pem"
  [[ -f "$cert" ]] || return 0
  # -checkend takes seconds; 30 days = 2592000
  sudo openssl x509 -checkend 2592000 -noout -in "$cert" >/dev/null 2>&1 && return 1 || return 0
}

obtain_certificate() {
  if [[ "$SKIP_TLS" == "1" ]]; then
    log "SKIP_TLS=1 — leaving the vhost on port 80 only"
    return
  fi

  if ! cert_needs_renewal; then
    log "certificate for $API_DOMAIN is valid for >30 days; nothing to do"
    return
  fi

  log "requesting/renewing certificate for $API_DOMAIN"
  # HTTP-01 needs inbound :80 from the internet and the A record already live.
  if sudo certbot --nginx \
      --non-interactive --agree-tos \
      --email "$CERTBOT_EMAIL" \
      --domains "$API_DOMAIN" \
      --redirect; then
    log "certificate installed"
  else
    echo >&2
    echo "certbot failed for $API_DOMAIN. Usual causes:" >&2
    echo "  * the A record does not resolve to THIS host yet" >&2
    echo "  * the security group does not allow inbound tcp/80 from 0.0.0.0/0" >&2
    echo "  * Let's Encrypt rate limit (5 failures/hour/domain)" >&2
    echo "Re-run after fixing, or set SKIP_TLS=1 to deploy http-only." >&2
    exit 1
  fi

  sudo systemctl enable --now certbot-renew.timer 2>/dev/null \
    || sudo systemctl enable --now certbot.timer 2>/dev/null \
    || log "no certbot systemd timer found — confirm auto-renewal manually"
}

install_packages
install_vhost
obtain_certificate

log "done. $API_DOMAIN now fronts ports 8081-8092."
