#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] docker is not installed or not in PATH"
  exit 1
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "[error] mvn is not installed or not in PATH"
  exit 1
fi

echo "[1/4] Starting Postgres + Redis..."
docker compose up -d

echo "[2/4] Waiting for containers to become healthy..."
for i in {1..40}; do
  pg_status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' search-engine-postgres 2>/dev/null || true)"
  redis_status="$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}unknown{{end}}' search-engine-redis 2>/dev/null || true)"

  if [[ "$pg_status" == "healthy" && "$redis_status" == "healthy" ]]; then
    echo "[ok] Postgres and Redis are healthy"
    break
  fi

  if [[ "$i" -eq 40 ]]; then
    echo "[error] Timed out waiting for containers to become healthy"
    echo "       postgres=$pg_status redis=$redis_status"
    echo "       Run: docker compose ps && docker compose logs --tail=100"
    exit 1
  fi

  sleep 2
done

echo "[3/4] Compiling quickly (skip tests)..."
mvn -q -DskipTests compile

echo "[4/4] Starting Spring Boot app on http://localhost:8080 ..."
echo "      Press Ctrl+C to stop the app."

mvn -q spring-boot:run
