#!/usr/bin/env bash
set -euo pipefail

VM_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$VM_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] docker is not installed or not in PATH"
  exit 1
fi

if [[ ! -f ".env" ]]; then
  echo "[error] Missing deploy/vm/.env"
  echo "        Run: cp .env.example .env"
  echo "        Then edit DB_PASSWORD and STACKEXCHANGE_API_KEY"
  exit 1
fi

echo "[1/3] Building and starting VM stack..."
docker compose -f docker-compose.vm.yml --env-file .env up -d --build

echo "[2/3] Current service status:"
docker compose -f docker-compose.vm.yml ps

echo "[3/3] API health check (may fail briefly while app is booting)..."
if curl -fsS "https://tiendat.tech/api/health" >/tmp/vm-health.out 2>/dev/null; then
  echo "[ok] Public health endpoint is reachable"
  cat /tmp/vm-health.out
  echo
else
  echo "[warn] Public health endpoint not reachable yet."
  echo "       Check logs: docker compose -f docker-compose.vm.yml logs --tail=120 api proxy"
fi
