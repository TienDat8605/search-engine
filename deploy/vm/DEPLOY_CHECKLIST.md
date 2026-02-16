# VM Pre-Deploy Checklist (Phase 1)

Use this checklist before running production deployment on `tiendat.tech`.

## 1) VM baseline

- [ ] Ubuntu/Debian VM with at least `2 vCPU`, `4 GB RAM`, `50 GB disk`
- [ ] `docker` and `docker compose` installed
- [ ] Server time synced (`timedatectl status`)
- [ ] Firewall allows `80/tcp` and `443/tcp`

## 2) DNS + domain

- [ ] `A` record for `tiendat.tech` points to VM public IP
- [ ] DNS propagated (`dig tiendat.tech +short` returns VM IP)

## 3) Secrets and env

- [ ] Copy and configure env file:
  - `cp deploy/vm/.env.example deploy/vm/.env`
- [ ] Set strong `DB_PASSWORD`
- [ ] Set valid `STACKEXCHANGE_API_KEY`
- [ ] Verify no secrets committed to git

## 4) Resource guardrails (4GB VM)

- [ ] API JVM max heap stays at `-Xmx1024m`
- [ ] Redis maxmemory remains around `384mb`
- [ ] Postgres uses default container settings initially
- [ ] Confirm no extra heavy services on same VM

## 5) Build + tests

- [ ] Run tests: `mvn -q test`
- [ ] Build check: `mvn -q -DskipTests compile`

## 6) Deploy readiness

- [ ] `deploy/vm/docker-compose.vm.yml` present
- [ ] `deploy/vm/Caddyfile` present and domain correct
- [ ] `Dockerfile` present at repo root

## 7) Post-deploy verification targets

- [ ] `https://tiendat.tech/api/health` returns `status=UP`
- [ ] `https://tiendat.tech/api/search?...` returns results JSON
- [ ] `https://tiendat.tech/api/analytics` returns summary JSON
- [ ] Homepage `https://tiendat.tech/` loads and search works
