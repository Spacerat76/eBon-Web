# eBon-Web

eBon-Web is a single-user expense tracker for electronic receipts imported from Paperless-NGX.

The project is built incrementally from `ebon-specification.md`. The current state includes the reproducible development environment, Docker database foundation, the Spring Boot backend with sync, parsing, categorization, DTOs, OpenAPI, and tests, plus the React/Vite frontend shell with dashboard foundation and API client.

## Prerequisites

- Docker Desktop or Docker Engine with Docker Compose
- VS Code with the Dev Containers extension, or another Devcontainer-compatible IDE

You do not need Java, Maven, Node.js, or PostgreSQL installed on the host. The Devcontainer provides them.

If you run the backend directly on the host instead of inside the Devcontainer, use Java 25. The backend Maven build targets Java 25 and will fail on older host JDKs with `Releaseversion 25 nicht unterstützt`.

## First Setup

1. Copy `.env.example` to `.env`.
2. Replace example secrets in `.env` before connecting to real services.
3. Open the repository in VS Code.
4. Run `Dev Containers: Reopen in Container`.

The Devcontainer starts a local PostgreSQL database and forwards these ports:

- `5432` PostgreSQL
- `8080` backend API
- `5173` frontend Vite dev server

The example environment file uses `DB_HOST=db` so the backend talks to the Compose database service inside the Devcontainer. If you run the backend directly on the host, change `DB_HOST` to `localhost`.

See [Devcontainer Mounts and Docker Access](#devcontainer-mounts-and-docker-access) for the project-specific Docker and build-output setup.

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

## Devcontainer Mounts and Docker Access

The Devcontainer is the default development environment. It deliberately keeps the standard project paths while isolating build artifacts that commonly cause ownership or platform issues between Windows, Docker Desktop, and Linux containers.

The repository itself is bind-mounted into the container:

```text
..:/workspace:cached
```

Build-output paths that are written frequently are mounted as named Docker volumes inside the Devcontainer:

```text
devcontainer_backend_target:/workspace/backend/target
devcontainer_frontend_node_modules:/workspace/frontend/node_modules
```

This means:

- Maven still uses the normal `backend/target` directory.
- npm still uses the normal `frontend/node_modules` directory.
- Host builds and Devcontainer builds do not fight over file ownership in those directories.
- There is no custom Maven target path to remember.

Inside the Devcontainer, `EBON_DEVCONTAINER=true` activates a Maven clean profile that clears the mounted `backend/target` contents without trying to delete the mountpoint itself. This is why `mvn clean verify` can run both on the host and in the Devcontainer.

The Devcontainer also mounts the host Docker socket:

```text
/var/run/docker.sock:/var/run/docker.sock
```

This lets Testcontainers start PostgreSQL and Ryuk as sibling containers through Docker Desktop. It is not Docker-in-Docker. The startup script `.devcontainer/init-devcontainer.sh` adds the `vscode` user to the socket group and fixes ownership of the mounted build volumes.

`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal` is set so tests running inside the Devcontainer can connect back to Testcontainers-managed services exposed through Docker Desktop. Docker Desktop must be running before integration tests are executed.

After changing Devcontainer mounts or Docker access settings, rebuild the container with `Dev Containers: Rebuild Container`.

## Current Docker Compose Scope

The root `docker-compose.yml` currently starts only PostgreSQL. Backend and frontend development servers are run separately from Devcontainer terminals.

Start the development database with:

```bash
docker compose up db
```

## Backend

The Spring Boot backend lives under `backend/`.

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

`PAPERLESS_BASE_URL` defaults to `http://localhost:8000` in the backend configuration. If your Paperless-NGX instance is reachable elsewhere, override it in `.env` before starting the backend. For example, a LAN-hosted instance may use `http://paperless:8001`.

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

If a container was opened before the named `backend/target` volume existed and Maven still fails with `Operation not permitted`, rebuild the Devcontainer first. If the stale generated directory still exists inside the container, remove it once and rerun Maven:

```bash
sudo rm -rf backend/target
cd backend
mvn clean spring-boot:run
```

`backend/target` contains generated Maven output only and must not be committed. The normal Devcontainer behavior is described in [Devcontainer Mounts and Docker Access](#devcontainer-mounts-and-docker-access).

## Frontend

The React frontend lives under `frontend/`.

Run the frontend from the Devcontainer:

```bash
cd frontend
npm install
npm run dev
```

Open the UI at:

```text
http://localhost:5173
```

The development server uses a Vite proxy: frontend requests to relative `/api/...` URLs are forwarded to `http://localhost:8080`. Start the backend separately before opening the dashboard:

```bash
cd backend
mvn spring-boot:run
```

The frontend does not contain hardcoded API tokens. Enter your local `APP_API_TOKEN` in the UI header when you want to load protected backend data.

Build the frontend with:

```bash
cd frontend
npm run build
```

## Version Notes

The Devcontainer uses the target Java 25 image `mcr.microsoft.com/devcontainers/java:dev-25-jdk-bookworm`, Maven 3.9.16, Node.js 24.16.0 LTS, Docker CLI, and PostgreSQL 18. The backend Maven build is configured for Java 25 as well, so the container and build target now match.

The frontend stack is React/React DOM 19.2.7, Vite 8.0.16, `@vitejs/plugin-react` 6.0.2, TypeScript 6.0.3, Tailwind CSS 4.3.0, Recharts 3.8.1, and lucide-react 1.17.0.

PostgreSQL 18 volumes are mounted at `/var/lib/postgresql` rather than `/var/lib/postgresql/data`, matching the official image layout for PostgreSQL 18+.

The Devcontainer intentionally installs Maven, Node.js, and Docker CLI in `.devcontainer/Dockerfile` instead of using Devcontainer Features from `ghcr.io`. This avoids failures in environments where the Feature registry cannot resolve `ghcr.io/devcontainers/features/*`.

## Safety

- Do not commit `.env`.
- Do not commit real Paperless-NGX or OpenRouter tokens.
- Use `.env.example` only for safe placeholder values.
