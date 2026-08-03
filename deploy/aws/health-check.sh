#!/usr/bin/env bash
#
# Post-deploy health gate for the ggfix backend.
#
# Probes the public API domain (default https://api.ggfix.in) end to end:
#   1. nginx edge      -> GET /health
#   2. every service   -> GET /<prefix>/actuator/health, expecting {"status":"UP"}
#
# Runs from the GitHub Actions runner after deployment, and is safe to run by
# hand from any machine. Exits non-zero if anything is still down once the
# retry budget is spent, so a bad deploy fails the workflow instead of going
# unnoticed.
#
# Usage:
#   ./health-check.sh                                  # https://api.ggfix.in
#   API_BASE=https://api.ggfix.in ./health-check.sh
#   API_BASE=http://13.205.198.41 ./health-check.sh    # pre-TLS / direct IP
#   RETRIES=40 RETRY_DELAY=5 ./health-check.sh
#
set -uo pipefail

API_BASE="${API_BASE:-https://api.ggfix.in}"
RETRIES="${RETRIES:-30}"
RETRY_DELAY="${RETRY_DELAY:-10}"
CURL_TIMEOUT="${CURL_TIMEOUT:-10}"
# Services allowed to be missing without failing the gate (space separated).
# Keep empty to require all of them.
OPTIONAL_SERVICES="${OPTIONAL_SERVICES:-}"

# prefix:service-name — prefix must match nginx-ggfix-api.conf
SERVICE_PREFIXES=(
  "auth:auth-service"
  "ticket:ticket-service"
  "user:user-service"
  "shop:shop-service"
  "technician:technician-service"
  "inventory:inventory-service"
  "marketplace:marketplace-service"
  "pickup:pickup-service"
  "notification:notification-service"
  "subscription:subscription-service"
  "master:master-data-service"
  "order:order-service"
)

pass=0
fail=0
skipped=0
failed_names=()
LAST_DETAIL=""

is_optional() {
  local name="$1"
  for opt in $OPTIONAL_SERVICES; do
    [[ "$opt" == "$name" ]] && return 0
  done
  return 1
}

# Retries until the endpoint answers as expected or the budget runs out.
# Echoes the last observed status/body so failures are diagnosable from the log.
probe() {
  local url="$1" expect_up="$2"
  local attempt body code err
  local out_f err_f
  out_f="$(mktemp)"
  err_f="$(mktemp)"

  for ((attempt = 1; attempt <= RETRIES; attempt++)); do
    # Keep stdout and stderr apart: merging them corrupts the body, and curl's
    # transport errors ("connection refused") are the useful part when the
    # status line never arrives.
    code="$(curl -sS --max-time "$CURL_TIMEOUT" -o "$out_f" -w '%{http_code}' "$url" 2>"$err_f")" || true
    body="$(tr -d '\r\n' <"$out_f")"
    err="$(tr -d '\r\n' <"$err_f")"

    if [[ "$code" == "200" ]]; then
      if [[ "$expect_up" != "1" ]] || printf '%s' "$body" | grep -q '"status"[[:space:]]*:[[:space:]]*"UP"'; then
        LAST_DETAIL="HTTP $code"
        rm -f "$out_f" "$err_f"
        return 0
      fi
    fi

    if [[ "$code" == "000" || -z "$code" ]]; then
      LAST_DETAIL="unreachable: ${err:0:160}"
    else
      LAST_DETAIL="HTTP ${code} ${body:0:160}"
    fi

    if ((attempt < RETRIES)); then
      sleep "$RETRY_DELAY"
    fi
  done

  rm -f "$out_f" "$err_f"
  return 1
}

echo "=============================================="
echo " ggfix backend health check"
echo " target : $API_BASE"
echo " budget : ${RETRIES} attempts x ${RETRY_DELAY}s"
echo "=============================================="
echo

# ---- 1. Edge (DNS + TLS + nginx) -------------------------------------------
printf '%-22s ' "nginx edge"
if probe "$API_BASE/health" 1; then
  echo "UP   ($LAST_DETAIL)"
else
  echo "DOWN ($LAST_DETAIL)"
  echo
  echo "The edge probe failed, so no service check can succeed."
  echo "Check, in order:"
  echo "  * DNS      : dig +short ${API_BASE#*://}"
  echo "  * firewall : EC2 security group must allow inbound 80 and 443"
  echo "  * nginx    : sudo systemctl status nginx; sudo nginx -t"
  echo "  * TLS      : sudo certbot certificates"
  exit 1
fi

# The edge is up, so services should answer quickly. Shorten per-service retries
# to keep a fully-broken service from burning the whole budget.
SERVICE_RETRIES="${SERVICE_RETRIES:-12}"
RETRIES="$SERVICE_RETRIES"

# ---- 2. Each service -------------------------------------------------------
for entry in "${SERVICE_PREFIXES[@]}"; do
  prefix="${entry%%:*}"
  name="${entry#*:}"

  printf '%-22s ' "$name"
  if probe "$API_BASE/$prefix/actuator/health" 1; then
    echo "UP   ($LAST_DETAIL)"
    pass=$((pass + 1))
  elif is_optional "$name"; then
    echo "SKIP ($LAST_DETAIL, optional)"
    skipped=$((skipped + 1))
  else
    echo "DOWN ($LAST_DETAIL)"
    fail=$((fail + 1))
    failed_names+=("$name")
  fi
done

echo
echo "----------------------------------------------"
echo "up: $pass   down: $fail   skipped: $skipped"
echo "----------------------------------------------"

if ((fail > 0)); then
  echo
  echo "Unhealthy: ${failed_names[*]}"
  echo "On the EC2 host, inspect with:"
  for n in "${failed_names[@]}"; do
    echo "  sudo journalctl -u repair-shop-saas@$n -n 80 --no-pager"
  done
  exit 1
fi

echo "All required services are healthy."
