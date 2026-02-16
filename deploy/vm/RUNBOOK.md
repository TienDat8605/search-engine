# Go-Live Runbook (Single VM)

This runbook deploys Phase 1 stack (`proxy + api + postgres + redis`) for `tiendat.tech`.

## 0) One-time setup on VM

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
# Install Docker + Compose plugin if not already installed
```

## 1) Prepare workspace

```bash
git clone <your-repo-url> search_engine
cd search_engine
cp deploy/vm/.env.example deploy/vm/.env
```

Edit `deploy/vm/.env`:

```env
DB_PASSWORD=<strong_password>
STACKEXCHANGE_API_KEY=<your_key>
```

## 2) Optional preflight

```bash
mvn -q test
mvn -q -DskipTests compile
```

## 3) Deploy

```bash
cd deploy/vm
docker compose -f docker-compose.vm.yml --env-file .env up -d --build
```

Or use the helper script:

```bash
cd deploy/vm
bash deploy.sh
```

## 4) Verify services

```bash
docker compose -f docker-compose.vm.yml ps
docker compose -f docker-compose.vm.yml logs --tail=150 api
docker compose -f docker-compose.vm.yml logs --tail=100 proxy
```

## 5) Verify endpoints

```bash
curl "https://tiendat.tech/api/health"
curl "https://tiendat.tech/api/search?q=spring%20boot%20di%20error&limit=10&offset=0&sort=relevance"
curl "https://tiendat.tech/api/analytics"
```

## 6) Rollback / restart

### Restart all

```bash
cd deploy/vm
docker compose -f docker-compose.vm.yml --env-file .env restart
```

### Recreate API only

```bash
cd deploy/vm
docker compose -f docker-compose.vm.yml --env-file .env up -d --build api
```

### Stop stack

```bash
cd deploy/vm
docker compose -f docker-compose.vm.yml --env-file .env down
```

## 7) Common issues

- TLS certificate not issued:
  - Check DNS points to VM and ports 80/443 are open.
- API cannot connect Postgres:
  - Confirm `DB_PASSWORD` in `.env` matches compose service.
- Search returns empty:
  - Validate `STACKEXCHANGE_API_KEY`, inspect `api` logs, and check backoff state from `/api/health`.
