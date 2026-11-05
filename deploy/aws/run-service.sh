#!/usr/bin/env bash
set -euo pipefail

service_name="${1:?service name is required}"
env_file="/opt/repair-shop-saas/.env"
jar_file="/opt/repair-shop-saas/services/${service_name}/${service_name}.jar"

if [[ -f "$env_file" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$env_file"
  set +a
fi

exec /usr/bin/java ${JAVA_OPTS:-} -jar "$jar_file"

