# Coding Meta Search Engine

Spring Boot backend that aggregates coding-related search results from Stack Overflow, ranks them, caches responses in Redis, and stores normalized documents in Postgres.

## Requirements

- Java 17+
- Maven 3.9+
- Docker and Docker Compose

## Run locally

Create `.env` in the project root with your StackExchange key:

```bash
STACKEXCHANGE_API_KEY=your_key_here
```

1. Start infrastructure:

```bash
docker compose up -d
```

Quick start (one command):

```bash
bash scripts/start-dev.sh
```

Stop local services:

```bash
bash scripts/stop-dev.sh
```

Check local status:

```bash
bash scripts/dev-status.sh
```

2. Run application:

```bash
./mvnw spring-boot:run
```

or

```bash
mvn spring-boot:run
```

3. Query API:

```bash
curl "http://localhost:8080/api/search?q=spring%20boot%20dependency%20injection%20error&limit=10&sort=relevance&tags=spring-boot,dependency-injection"
```

Health endpoint:

```bash
curl "http://localhost:8080/api/health"
```

Analytics endpoint:

```bash
curl "http://localhost:8080/api/analytics"
```

## Notes

- Search results are cached with a short TTL.
- Documents are normalized and persisted in table `documents`.
- External provider failures degrade gracefully and do not fail the whole request.
- StackExchange API `backoff` is respected to avoid quota/rate-limit pressure.

## Integration tests

Run integration tests:

```bash
mvn -q test
```

Current integration coverage validates:

- `GET /api/search` response contract
- `GET /api/analytics` summary contract

## VM deployment (single node)

Deployment files are in [deploy/vm/docker-compose.vm.yml](deploy/vm/docker-compose.vm.yml).

Operational docs:

- Pre-deploy checklist: [deploy/vm/DEPLOY_CHECKLIST.md](deploy/vm/DEPLOY_CHECKLIST.md)
- Go-live runbook: [deploy/vm/RUNBOOK.md](deploy/vm/RUNBOOK.md)

1. Copy env template and set values:

```bash
cd deploy/vm
cp .env.example .env
```

2. Ensure DNS `A` record for `tiendat.tech` points to your VM public IP.

3. Start stack:

```bash
docker compose -f docker-compose.vm.yml --env-file .env up -d --build
```

4. Verify health:

```bash
curl "https://tiendat.tech/api/health"
```
