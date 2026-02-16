#!/usr/bin/env sh
set -eu

VM_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
REPO_ROOT="$(CDPATH= cd -- "$VM_DIR/../.." && pwd)"
COMPOSE_FILE="$VM_DIR/docker-compose.vm.yml"
ENV_FILE="$VM_DIR/.env"
HEALTH_URL="${HEALTH_URL:-https://tiendat.tech/api/health}"
PURGE_HOST=0
ASSUME_YES=0

usage() {
  echo "Usage: $0 [--purge-host] [--yes]"
  echo "  --purge-host   Remove ALL Docker containers and images on this host before reload"
  echo "  --yes          Skip confirmation prompt (only with --purge-host)"
}

confirm_purge() {
  if [ "$ASSUME_YES" -eq 1 ]; then
    return 0
  fi

  echo "[warn] This will remove ALL Docker containers and images on this VM."
  printf "Type 'yes' to continue: "
  read -r reply
  if [ "$reply" != "yes" ]; then
    echo "[info] Purge cancelled."
    exit 1
  fi
}

purge_host_docker() {
  echo "[purge] Removing all Docker containers..."
  container_ids="$(docker ps -aq)"
  if [ -n "$container_ids" ]; then
    docker rm -f $container_ids
  else
    echo "[purge] No containers found."
  fi

  echo "[purge] Removing all Docker images..."
  image_ids="$(docker images -aq)"
  if [ -n "$image_ids" ]; then
    docker rmi -f $image_ids
  else
    echo "[purge] No images found."
  fi
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --purge-host)
      PURGE_HOST=1
      ;;
    --yes)
      ASSUME_YES=1
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "[error] Unknown option: $1"
      usage
      exit 1
      ;;
  esac
  shift
done

cd "$VM_DIR"

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] docker is not installed or not in PATH"
  exit 1
fi

if [ ! -f "$ENV_FILE" ]; then
  echo "[error] Missing deploy/vm/.env"
  echo "        Run: cp .env.example .env"
  echo "        Then edit required values"
  exit 1
fi

if [ "$PURGE_HOST" -eq 1 ]; then
  confirm_purge
  purge_host_docker
fi

if command -v git >/dev/null 2>&1; then
  echo "[1/4] Pulling latest code in repo root..."
  if ! git -C "$REPO_ROOT" pull --ff-only; then
    echo "[warn] git pull failed. Resolve git issues, then run again."
    exit 1
  fi
else
  echo "[warn] git not found. Skipping git pull."
fi

echo "[2/4] Rebuilding and restarting VM stack..."
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" up -d --build --remove-orphans

echo "[3/4] Current service status:"
docker compose -f "$COMPOSE_FILE" --env-file "$ENV_FILE" ps

echo "[4/4] Health check (may fail briefly while app boots)..."
if command -v curl >/dev/null 2>&1 && curl -fsS "$HEALTH_URL" >/tmp/vm-health.out 2>/dev/null; then
  echo "[ok] Health endpoint reachable: $HEALTH_URL"
  cat /tmp/vm-health.out
  echo
else
  echo "[warn] Health endpoint not reachable yet: $HEALTH_URL"
  echo "       Check logs: docker compose -f docker-compose.vm.yml --env-file .env logs --tail=120 api proxy"
fi
