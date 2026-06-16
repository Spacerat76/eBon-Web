# eBon-Web

eBon-Web is a single-user expense tracker for electronic receipts imported from Paperless-NGX.

The project is built incrementally from `ebon-specification.md`. The current state includes the reproducible development environment, the Spring Boot backend with sync, parsing, categorization, DTOs, OpenAPI, backup/restore, rolling automatic backups, and tests, plus the React/Vite frontend with dashboard, receipts, search, reports, settings, data maintenance, backup UI, category icons, Selenium smoke tests, and CI workflow. The full system can be started with Docker Compose.

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

## Docker Compose Full System

The root `docker-compose.yml` starts PostgreSQL, builds the backend image, builds the frontend/nginx image, and wires `/api` from the frontend container to the backend container.

Start the full system from the repository root:

```bash
docker compose up --build
```

Open the frontend at:

```text
http://localhost:5173
```

The backend is also published at:

```text
http://localhost:8080
```

`FRONTEND_PORT` defaults to `5173` in `.env.example` so the full Compose frontend uses the same browser port as the Vite dev server. Set `FRONTEND_PORT=80` if you want a traditional web port and it is free on your host.

If you only need the development database while running backend and frontend manually, start just the database:

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

`PAPERLESS_BASE_URL` defaults to `http://localhost:8000` in the backend configuration. If your Paperless-NGX instance is reachable elsewhere, override it in `.env` before starting the backend. For example, a LAN-hosted instance may use `http://paperless:8001` if that hostname is resolvable from the backend container.

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
- `/v3/api-docs` and `/swagger-ui.html` can be public in local development so the browser can load Swagger UI.
- `.env.example` sets `APP_OPENAPI_PUBLIC_ACCESS=false` for the full integration setup, so OpenAPI and Swagger UI are protected by the bearer token.
- Set `APP_OPENAPI_PUBLIC_ACCESS=true` temporarily when you want to use Swagger UI directly in the browser during local development.
- Set `SPRINGDOC_API_DOCS_ENABLED=false` and `SPRINGDOC_SWAGGER_UI_ENABLED=false` if OpenAPI and Swagger UI should be unavailable at runtime instead of merely protected.
- Flyway creates the schema and seed data for default categories and application settings.

Paperless sync endpoints are available in the backend:

```bash
curl -X POST -H "Authorization: Bearer change_me_local_dev_token" http://localhost:8080/api/sync/trigger
curl -H "Authorization: Bearer change_me_local_dev_token" http://localhost:8080/api/sync/status
curl -H "Authorization: Bearer change_me_local_dev_token" "http://localhost:8080/api/sync/log?page=0&size=20"
```

The scheduled sync uses `SYNC_INTERVAL_MINUTES` and starts after `SYNC_INITIAL_DELAY_MS`. Set `SYNC_SCHEDULER_ENABLED=false` for local runs where Paperless-NGX should never be contacted automatically.

## Paperless and OpenRouter Configuration

Important `.env` values for real integration:

- `APP_API_TOKEN`: local bearer token for the eBon-Web API and frontend header.
- `PAPERLESS_BASE_URL`: backend/container-reachable Paperless-NGX API URL.
- `PAPERLESS_PUBLIC_BASE_URL`: browser-reachable Paperless-NGX web URL for document links.
- `PAPERLESS_DOCUMENT_URL_TEMPLATE`: optional explicit document link template, for example `http://paperless.local/documents/{paperlessDocumentId}/details`.
- `PAPERLESS_API_TOKEN`: Paperless-NGX API token. Never put this into frontend source code.
- `PAPERLESS_EBON_TAG`: Paperless tag name used for eBon sync.
- `OPENROUTER_API_KEY`: optional. Without it, AI categorization remains disabled and uncertain items stay uncategorized.
- `OPENROUTER_BASE_URL` and `OPENROUTER_MODEL`: optional OpenRouter endpoint/model configuration.
- `APP_OPENAPI_PUBLIC_ACCESS`: `false` protects Swagger/OpenAPI; `true` is convenient for local browser testing.
- `SPRINGDOC_API_DOCS_ENABLED` and `SPRINGDOC_SWAGGER_UI_ENABLED`: set both to `false` to disable OpenAPI and Swagger UI completely in a hardened runtime.
- `ROLLING_BACKUP_ENABLED`: enables automatic scheduled backups. Default is `false`.
- `ROLLING_BACKUP_DIRECTORY`: directory used by the backend for automatic backups. In Compose this is mounted to the `automatic_backups` Docker volume.
- `ROLLING_BACKUP_RETENTION_COUNT`: maximum number of automatic backup ZIP files kept in the automatic backup directory.
- `ROLLING_BACKUP_CRON`: optional Spring cron expression. It contains spaces, so leave it unset unless you need to override the default `0 0 3 * * *`.

`PAPERLESS_BASE_URL` and `PAPERLESS_PUBLIC_BASE_URL` may legitimately differ. For example, inside Docker the backend might reach Paperless as `http://paperless:8001`, while your browser opens Paperless as `http://192.168.178.155:8001`.

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

Run the Selenium smoke test with mock API data:

```bash
cd frontend
npm run e2e
```

The E2E command starts Vite with `VITE_EBON_MOCK_API=true`, enters a mock API token, and checks Dashboard/navigation, Settings, Backup controls, Search, and Receipts. It does not need PostgreSQL, Paperless-NGX, OpenRouter, or private receipt data. A local Chrome/Chromium or Microsoft Edge installation is required. Chrome uses the bundled `chromedriver`; Edge uses the `edgedriver` package and may download the matching driver on first run. Set `EBON_E2E_BROWSER_BINARY` or `EDGE_BINARY_PATH` if the browser is installed in a non-standard location.

## Smoke Test

After `docker compose up --build`, run these checks from another terminal:

```bash
curl http://localhost:8080/api/health
curl -i http://localhost:8080/api/system/ping
curl -H "Authorization: Bearer $APP_API_TOKEN" http://localhost:8080/api/system/ping
curl -H "Authorization: Bearer $APP_API_TOKEN" http://localhost:8080/api/settings
```

Expected behavior:

- Health returns `{ "status": "UP" }` without auth.
- Protected API endpoints return `401` without a bearer token.
- Protected API endpoints return data with `Authorization: Bearer <APP_API_TOKEN>`.
- The frontend opens at `http://localhost:5173`, accepts the local API token, and loads dashboard data through `/api`.
- Paperless document links use `paperlessDocumentUrl` returned by the backend and never include API tokens.

Manual full-flow smoke test:

1. Open `http://localhost:5173`.
2. Enter `APP_API_TOKEN` in the header.
3. Check Dashboard, Bons, Suche, Reports, Einstellungen, and Backup tabs.
4. In Einstellungen, test the Paperless connection after configuring a real Paperless URL/token.
5. Trigger a manual sync only when the Paperless values are real and correct.
6. Verify deleted receipts are hidden from default lists/reports unless an endpoint explicitly supports `includeDeleted=true`.

## Data Maintenance Safety

The settings UI contains local admin actions for maintenance:

- Re-parse all receipts: keeps manual edits by default; overwriting manual edits must be selected explicitly.
- Reset imported receipt data: deletes imported receipts, receipt items, sync logs, and related parser/AI details only after the exact confirmation text `DELETE_IMPORTED_RECEIPTS`.

These actions are transactional backend operations intended for local administration. They keep categories, categorization rules, app settings, backups, and Flyway history intact.

## Automatic Rolling Backups

Automatic backups are disabled by default. Enable them only after you have chosen a target directory or accepted the Compose volume default:

```env
ROLLING_BACKUP_ENABLED=true
ROLLING_BACKUP_DIRECTORY=/var/lib/ebon/backups/automatic
ROLLING_BACKUP_RETENTION_COUNT=7
```

When enabled, the backend scheduler writes ZIP files named like `ebon-backup-auto-2026-06-16_03-00-00-000.zip`. The ZIP structure and secret masking are the same as manual backups. Paperless/OpenRouter secrets are not exported and must be reconfigured after restore.

Retention deletes only files whose names start with `ebon-backup-auto-` and end with `.zip` in the configured automatic backup directory. Manually downloaded backups such as `ebon-backup-2026-06-16_10-00.zip` are not deleted by automatic retention.

The same application-level backup/restore lock is used for manual backup, restore, and automatic backup. If a manual backup or restore is running, the scheduler skips that run instead of running in parallel.

## Category Icons

Category icons are not free text. The backend exposes the fixed allowed list at:

```bash
curl -H "Authorization: Bearer $APP_API_TOKEN" http://localhost:8080/api/categories/icons
```

Category create/update requests are validated against that list. The settings UI renders an icon select and shows icons alongside category colors; unknown or empty icons fall back visually but are not accepted for new writes.

## CI

The GitHub Actions workflow lives at `.github/workflows/ci.yml` and runs on pull requests and pushes to `main`, `master`, and `codex`.

It executes:

```bash
cd backend && mvn verify
cd frontend && npm ci
cd frontend && npm run build
cd frontend && npm run e2e
docker compose config
```

Backend tests use Testcontainers, so the GitHub runner must have Docker available. The workflow does not require real Paperless-NGX/OpenRouter secrets and disables scheduled sync/rolling backup behavior for CI.

## Version Notes

The Devcontainer uses the target Java 25 image `mcr.microsoft.com/devcontainers/java:dev-25-jdk-bookworm`, Maven 3.9.16, Node.js 24.16.0 LTS, Docker CLI, and PostgreSQL 18. The backend Maven build is configured for Java 25 as well, so the container and build target now match.

The frontend stack is React/React DOM 19.2.7, Vite 8.0.16, `@vitejs/plugin-react` 6.0.2, TypeScript 6.0.3, Tailwind CSS 4.3.0, Recharts 3.8.1, and lucide-react 1.17.0.

PostgreSQL 18 volumes are mounted at `/var/lib/postgresql` rather than `/var/lib/postgresql/data`, matching the official image layout for PostgreSQL 18+.

The root Compose frontend publishes nginx on `FRONTEND_PORT` with a default of `5173` instead of hardcoding port `80`. This keeps Devcontainer and full-system testing on the same browser port. Set `FRONTEND_PORT=80` for a production-like port mapping.

Backend logs include a `traceId` and use Spring Boot structured console logging when `LOG_STRUCTURED_FORMAT` is set. The default example uses `logstash`; set `LOG_LEVEL=DEBUG` temporarily to include request method/path/status/duration logs without headers or secrets.

The Devcontainer intentionally installs Maven, Node.js, and Docker CLI in `.devcontainer/Dockerfile` instead of using Devcontainer Features from `ghcr.io`. This avoids failures in environments where the Feature registry cannot resolve `ghcr.io/devcontainers/features/*`.

The application version is centrally maintained in `backend/pom.xml`. Maven generates Spring Boot build metadata from that version. The backend exposes it through `GET /api/system/info`, OpenAPI `info.version`, and backup `manifest.json` as `appVersion`; the frontend shows it in Settings. For release builds, update the Maven project version first and rebuild the backend image/artifact from that metadata.

## Safety

- Do not commit `.env`.
- Do not commit real Paperless-NGX or OpenRouter tokens.
- Use `.env.example` only for safe placeholder values.
- Do not commit private raw receipt texts or real backup ZIPs.
- Do not put API tokens into frontend source code or Paperless document URLs.
