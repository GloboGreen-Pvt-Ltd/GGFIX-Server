#!/usr/bin/env bash
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/repair-shop-saas}"
APP_USER="${APP_USER:-repairshop}"

install_packages() {
  if command -v dnf >/dev/null 2>&1; then
    sudo dnf install -y java-25-amazon-corretto-headless docker git tar gzip openssl
    sudo dnf install -y docker-compose-plugin || true
  elif command -v apt-get >/dev/null 2>&1; then
    sudo apt-get update
    sudo apt-get install -y openjdk-25-jre-headless docker.io git tar gzip openssl
    sudo apt-get install -y docker-compose-plugin || sudo apt-get install -y docker-compose
  else
    echo "Unsupported Linux package manager. Install Java 25, Docker, git, tar, gzip, and openssl manually." >&2
    exit 1
  fi
}

create_user_and_dirs() {
  if ! id "$APP_USER" >/dev/null 2>&1; then
    sudo useradd --system --home-dir "$APP_DIR" --shell /sbin/nologin "$APP_USER"
  fi

  sudo mkdir -p "$APP_DIR/bin" "$APP_DIR/services" "$APP_DIR/postgres/init"
  sudo chown -R "$APP_USER:$APP_USER" "$APP_DIR"
}

enable_docker() {
  sudo systemctl enable --now docker
}

install_packages
create_user_and_dirs
enable_docker

echo "EC2 base setup is ready at $APP_DIR."
echo "Deploy with GitHub Actions or run deploy/aws/deploy-from-artifact.sh from a deployment bundle."
