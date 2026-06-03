# eBon-Web

eBon-Web is a single-user expense tracker for electronic receipts imported from Paperless-NGX.

The project is built incrementally from `ebon-specification.md`. The current state includes the reproducible development environment, Docker database foundation, and the Spring Boot backend. The frontend is intentionally not scaffolded yet.

## Prerequisites

- Docker Desktop or Docker Engine with Docker Compose
- VS Code with the Dev Containers extension, or another Devcontainer-compatible IDE

You do not need Java, Maven, Node.js, or PostgreSQL installed on the host. The Devcontainer provides them.

## First Setup

1. Copy `.env.example` to `.env`.
2. Replace example secrets in `.env` before connecting to real services.
3. Open the repository in VS Code.
4. Run `Dev Containers: Reopen in Container`.

The Devcontainer starts a local PostgreSQL database and forwards these ports:

- `5432` PostgreSQL
- `8080` backend API

Frontend port `5173` is intentionally not forwarded yet because no frontend service exists.

## Verify the Devcontainer

Inside the Devcontainer, run:

```bash
java -version
mvn -version
node --version
npm --version
```

From the host or inside the Devcontainer, validate Compose files with:

```bash
docker compose config
docker compose -f .devcontainer/docker-compose.devcontainer.yml config
```

## Current Docker Compose Scope

The root `docker-compose.yml` currently starts only PostgreSQL. Backend and frontend services will be added in later phases after their projects exist.

Start the development database with:

```bash
docker compose up db
```

## Backend Skeleton

Phase 2 adds a Spring Boot backend under `backend/`.

Run the backend from the Devcontainer:

```bash
cd backend
mvn verify
mvn clean spring-boot:run
```

The backend now uses PostgreSQL and runs Flyway migrations automatically at startup. In the Devcontainer, PostgreSQL is started by the Devcontainer Compose setup. Outside the Devcontainer, start the database first:

```bash
docker compose up db
```

Smoke checks:

```bash
curl http://localhost:8080/api/health
curl -i http://localhost:8080/api/system/ping
curl -H "Authorization: Bearer change_me_local_dev_token" http://localhost:8080/api/system/ping
curl -H "Authorization: Bearer change_me_local_dev_token" http://localhost:8080/api/sync/status
```

Expected behavior:

- `GET /api/health` is public and returns `{ "status": "UP" }`.
- Other endpoints require `Authorization: Bearer <APP_API_TOKEN>`.
- `/v3/api-docs` and `/swagger-ui.html` are public in local development so the browser can load Swagger UI.
- Set `APP_OPENAPI_PUBLIC_ACCESS=false` to protect OpenAPI and Swagger UI with the same bearer token.
- Flyway creates the schema and seed data for default categories and application settings.

Paperless sync endpoints are available in the backend:

```bash
curl -X POST -H "Authorization: Bearer change_me_local_dev_token" http://localhost:8080/api/sync/trigger
curl -H "Authorization: Bearer change_me_local_dev_token" http://localhost:8080/api/sync/status
curl -H "Authorization: Bearer change_me_local_dev_token" "http://localhost:8080/api/sync/log?page=0&size=20"
```

The scheduled sync uses `SYNC_INTERVAL_MINUTES` and starts after `SYNC_INITIAL_DELAY_MS`. Set `SYNC_SCHEDULER_ENABLED=false` for local runs where Paperless-NGX should never be contacted automatically.

Some dm eBons contain the branch address only as an image. The parser extracts the dm branch code from the text header and resolves it through `app.parser.dm-branch-mappings`. Use the base code before `/` when possible, so codes like `D482/1` and `D482/2` map to the same branch:

```properties
app.parser.dm-branch-mappings.D482=Example Street 1, 12345 Example City
```

If Maven fails with `Operation not permitted` while writing `backend/target`, remove the generated build directory once inside the Devcontainer and start again:

```bash
sudo rm -rf backend/target
cd backend
mvn clean spring-boot:run
```

`backend/target` contains generated Maven output only and must not be committed.

## Version Notes

The Devcontainer uses the documented fallback Java 21 LTS image `mcr.microsoft.com/devcontainers/java:1-21-bookworm`, Maven 3.9.x, Node.js 22, Docker CLI, and PostgreSQL 18.

PostgreSQL 18 volumes are mounted at `/var/lib/postgresql` rather than `/var/lib/postgresql/data`, matching the official image layout for PostgreSQL 18+.

Deviation from target versions: `ebon-specification.md` targets Java 25, but the Java 25 Devcontainer image was not available as `mcr.microsoft.com/devcontainers/java:1-25-bookworm`. Per the specification's fallback rule, local development uses Java 21 LTS until a Java 25 Devcontainer image is available.

The Devcontainer intentionally installs Maven, Node.js, and Docker CLI in `.devcontainer/Dockerfile` instead of using Devcontainer Features from `ghcr.io`. This avoids failures in environments where the Feature registry cannot resolve `ghcr.io/devcontainers/features/*`.

## Safety

- Do not commit `.env`.
- Do not commit real Paperless-NGX or OpenRouter tokens.
- Use `.env.example` only for safe placeholder values.
