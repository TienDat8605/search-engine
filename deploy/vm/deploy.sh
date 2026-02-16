#!/usr/bin/env sh
set -eu

VM_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
cd "$VM_DIR"

PURGE_HOST=0
ASSUME_YES=0

usage() {
  echo "Usage: $0 [--purge-host] [--yes]"
  echo "  --purge-host   Remove ALL Docker containers and images on this host before deploy"
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

if ! command -v docker >/dev/null 2>&1; then
  echo "[error] docker is not installed or not in PATH"
  exit 1
fi

if [ ! -f ".env" ]; then
  echo "[error] Missing deploy/vm/.env"
  echo "        Run: cp .env.example .env"
  echo "        Then edit DB_PASSWORD and STACKEXCHANGE_API_KEY"
  exit 1
fi

if [ "$PURGE_HOST" -eq 1 ]; then
  confirm_purge
  purge_host_docker
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
