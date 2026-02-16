#!/usr/bin/env sh
set -eu

VM_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
COMPOSE_FILE="$VM_DIR/docker-compose.vm.yml"
ENV_FILE="$VM_DIR/.env"

cd "$VM_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] docker is not installed or not in PATH"
  exit 1
fi

echo "Stopping VM stack..."
if [ -f "$ENV_FILE" ]; then
  docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" down --remove-orphans
else
  docker compose -f "$COMPOSE_FILE" down --remove-orphans
fi

echo "Done."
