#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

echo "[1/2] Stopping local infrastructure..."
docker compose down

echo "[2/2] Done."
echo "If Spring Boot is still running in another terminal, press Ctrl+C there to stop it."
