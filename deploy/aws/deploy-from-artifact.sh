#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/repair-shop-saas}"
APP_USER="${APP_USER:-repairshop}"
# All 12 services. NOTE: each Spring Boot service needs ~300 MB RAM, so the full set
# requires a ~4 GB instance (t3.medium). On a 1 GB t3.micro only ~3 fit before the
# kernel OOM-kills them - upsize the instance before enabling the whole list, or trim
# SERVICES in /opt/repair-shop-saas/.env to the subset the instance can hold.
SERVICES_DEFAULT="auth-service ticket-service user-service shop-service technician-service inventory-service marketplace-service pickup-service notification-service subscription-service master-data-service order-service"

# Production database lives on AWS RDS and is managed outside this deployment.
# We connect to it via JDBC only - we never provision or migrate it from here.
#
# The password is deliberately NOT defaulted here: this repository is public, so a
# literal would publish the production credential. It comes from the DB_PASSWORD
# environment variable (the GitHub Actions secret of the same name), or is carried
# over from the .env written by a previous deploy.
RDS_DB_HOST="${DB_HOST:-ggfixservice.cdaiqaog82ho.ap-south-1.rds.amazonaws.com}"
RDS_DB_PORT="${DB_PORT:-5432}"
RDS_DB_NAME="${DB_NAME:-ggfixservice}"
RDS_DB_USER="${DB_USER:-postgres}"
RDS_DB_PASSWORD="${DB_PASSWORD:-}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PAYLOAD_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1. Run deploy/aws/install-ec2.sh on the EC2 instance first." >&2
    exit 1
  fi
}

random_secret() {
  openssl rand -base64 48 | tr -d '\n'
}

# Pull a single value out of the existing .env without sourcing the whole file.
read_env_value() {
  local key="$1"
  if [[ -f "$APP_DIR/.env" ]]; then
    sudo sed -n "s/^${key}=//p" "$APP_DIR/.env" | head -n1
  fi
}

# Always (re)write the env file so the DB connection points at RDS, while
# preserving any secrets that were generated/set on a previous deploy.
ensure_env_file() {
  local jwt_secret cloud_name cloud_key cloud_secret cloud_folder

  jwt_secret="$(read_env_value JWT_SECRET)"
  if [[ -z "$jwt_secret" ]]; then
    jwt_secret="$(random_secret)"
  fi

  # Fall back to the value a previous deploy already wrote, so an operator who
  # set it once by hand on the box does not have to re-supply it every time.
  if [[ -z "$RDS_DB_PASSWORD" ]]; then
    RDS_DB_PASSWORD="$(read_env_value DB_PASSWORD)"
  fi

  if [[ -z "$RDS_DB_PASSWORD" ]]; then
    echo "ERROR: DB_PASSWORD is not set and $APP_DIR/.env has no previous value." >&2
    echo "Set the DB_PASSWORD repository secret, or write it into $APP_DIR/.env on the host." >&2
    exit 1
  fi

  cloud_name="$(read_env_value CLOUDINARY_CLOUD_NAME)"
  cloud_key="$(read_env_value CLOUDINARY_API_KEY)"
  cloud_secret="$(read_env_value CLOUDINARY_API_SECRET)"
  cloud_folder="$(read_env_value CLOUDINARY_FOLDER)"
  cloud_folder="${cloud_folder:-ggfix/master}"

  # S3 media (media.ggfix.in). Read back from the existing .env first so a value set
  # on the box survives — this heredoc REWRITES .env on every deploy, so anything not
  # carried through here is silently lost and uploads start failing again.
  # Credentials are deliberately absent: the SDK resolves them from the EC2 instance
  # role, which needs s3:PutObject/s3:DeleteObject on the bucket.
  aws_region="$(read_env_value AWS_REGION)"
  aws_region="${aws_region:-ap-south-1}"
  aws_bucket="$(read_env_value AWS_S3_BUCKET)"
  aws_bucket="${aws_bucket:-ggfix-media-1762}"
  aws_base_url="$(read_env_value AWS_S3_BASE_URL)"
  aws_base_url="${aws_base_url:-https://media.ggfix.in}"

  sudo tee "$APP_DIR/.env" >/dev/null <<EOF
DB_HOST=$RDS_DB_HOST
DB_PORT=$RDS_DB_PORT
DB_NAME=$RDS_DB_NAME
DB_USER=$RDS_DB_USER
DB_PASSWORD=$RDS_DB_PASSWORD
JWT_SECRET=$jwt_secret
JWT_EXPIRY_MS=86400000
CLOUDINARY_CLOUD_NAME=$cloud_name
CLOUDINARY_API_KEY=$cloud_key
CLOUDINARY_API_SECRET=$cloud_secret
CLOUDINARY_FOLDER=$cloud_folder
AWS_REGION=$aws_region
AWS_S3_BUCKET=$aws_bucket
AWS_S3_BASE_URL=$aws_base_url
JAVA_OPTS="-Xms64m -Xmx160m"
SERVICES="$SERVICES_DEFAULT"
EOF
}

prepare_layout() {
  if ! id "$APP_USER" >/dev/null 2>&1; then
    sudo useradd --system --home-dir "$APP_DIR" --shell /sbin/nologin "$APP_USER"
  fi

  sudo mkdir -p "$APP_DIR/bin" "$APP_DIR/services"
  sudo install -m 0755 "$SCRIPT_DIR/run-service.sh" "$APP_DIR/bin/run-service"
  sudo install -m 0644 "$SCRIPT_DIR/repair-shop-saas@.service" /etc/systemd/system/repair-shop-saas@.service
}

load_env() {
  # .env is written by sudo tee (root-owned) and holds secrets, so it is not
  # world-readable. Read it with sudo - like read_env_value above - instead of
  # sourcing it directly, which fails with "Permission denied" for the
  # unprivileged deploy user before copy_jars chowns it to $APP_USER.
  set -a
  # shellcheck disable=SC1091
  source <(sudo cat "$APP_DIR/.env")
  set +a
  SERVICES="${SERVICES:-$SERVICES_DEFAULT}"
}

copy_jars() {
  for service in $SERVICES; do
    source_jar="$PAYLOAD_DIR/services/$service/$service.jar"
    if [[ ! -f "$source_jar" ]]; then
      echo "Deployment bundle is missing $source_jar" >&2
      exit 1
    fi

    sudo mkdir -p "$APP_DIR/services/$service"
    sudo install -m 0644 "$source_jar" "$APP_DIR/services/$service/$service.jar"
  done

  sudo chown -R "$APP_USER:$APP_USER" "$APP_DIR"
}

restart_services() {
  sudo systemctl daemon-reload

  for service in $SERVICES; do
    sudo systemctl enable "repair-shop-saas@$service"
    sudo systemctl restart "repair-shop-saas@$service"
  done

  for service in $SERVICES; do
    for _ in $(seq 1 30); do
      if sudo systemctl is-active --quiet "repair-shop-saas@$service"; then
        echo "$service is active"
        break
      fi

      if sudo systemctl is-failed --quiet "repair-shop-saas@$service"; then
        sudo systemctl --no-pager --full status "repair-shop-saas@$service" || true
        exit 1
      fi

      sleep 2
    done
  done
}

require_command java
require_command openssl

prepare_layout
ensure_env_file
load_env
copy_jars
restart_services

echo "Deployment complete. Services connect to RDS at $RDS_DB_HOST:$RDS_DB_PORT/$RDS_DB_NAME."
