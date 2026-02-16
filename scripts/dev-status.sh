#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "=== Docker services ==="
docker compose ps || true

echo
echo "=== API health (if app is running) ==="
if curl -fsS http://localhost:8080/api/health >/tmp/api-health.out 2>/dev/null; then
  cat /tmp/api-health.out
  echo
else
  echo "API not reachable at http://localhost:8080/api/health"
fi
