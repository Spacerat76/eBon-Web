# eBon-Web

Repository initialized in the workspace.

This repository was created by GitHub Copilot on your request.

## API — Sync endpoints

This project exposes a small sync API to trigger and inspect Paperless synchronization.

- `POST /api/sync` — trigger a full sync of new Paperless documents. Returns `202 Accepted`.
- `POST /api/sync/document/{id}` — trigger sync for a single Paperless document id. Returns `200 OK` on success or `500 Internal Server Error` on failure.
- `GET /api/sync/status` — returns JSON with sync status fields: `lastSyncAt`, `lastSyncedCount`, `lastErrorCount`, `lastDurationMs`.

Example using `curl`:

```bash
curl -X POST http://localhost:8080/api/sync
curl -X POST http://localhost:8080/api/sync/document/123
curl http://localhost:8080/api/sync/status
```

## Scheduled background sync

The backend can run an automatic background sync job that polls Paperless-NGX for new documents and imports them automatically. Configure the interval (in minutes) with `ebon.sync-interval-minutes` (default: `60`). To disable automatic sync set the value to `0`.

Example override when running locally:

```bash
java -jar backend/target/ebon-backend-0.1.0-SNAPSHOT.jar --ebon.sync-interval-minutes=120
```

## Database migrations

This project uses Flyway for schema migrations. Migration scripts are packaged under `backend/src/main/resources/db/migration` (classpath) and a filesystem copy can be placed at `backend/db/migrations` for deployment scenarios. The backend will execute Flyway migrations on startup when Flyway is enabled (see `application.yml`).

The integration tests pick up migrations from the classpath. The E2E test harness applies the packaged migration early so the schema exists for tests (there is a small test-time fallback that applies the SQL directly when needed).

To run the application locally without Postgres, you can override the datasource to use H2 in-memory (useful for development and tests):

```bash
mvn -f backend/pom.xml -DskipTests package
java -jar backend/target/ebon-backend-0.1.0-SNAPSHOT.jar --spring.datasource.url=jdbc:h2:mem:ebondb --ebon.app-api-token=devtoken
```

## Deduplication behavior

When the backend imports documents from Paperless-NGX it deduplicates by the `paperless_document_id` field. The full automatic sync will NOT re-import or overwrite existing receipts — documents already imported are skipped to avoid accidental overwrites. To re-import and update an already-imported document, use the explicit per-document re-import endpoint (`POST /api/sync/document/{id}`), which performs a targeted parse/import of the specified Paperless document.

New documents are inserted with `importedAt` and `updatedAt` set to the import time; explicit re-imports update existing rows and items and set an `updatedAt` timestamp.

When deploying to production, ensure Postgres is available and that the Flyway migrations are applied on startup.

## Sync audit logs

The sync process now persists an audit record for each full sync run in the `sync_log` table and per-document actions in `sync_log_entry`.

- `sync_log`: one row per sync run with `startedAt`, `finishedAt`, `status`, `total_documents`, `succeeded`, `failed`.
- `sync_log_entry`: one row per document processed containing `sync_log_id`, `paperless_document_id`, `action` (`INSERTED`, `UPDATED`, `TAG_REMOVED`, `ERROR`) and an optional `message`.

These tables are created by the Flyway V1 migration. The backend writes entries automatically during sync runs; a future API will expose read endpoints to query past sync runs.

API endpoints (read-only) for sync audits:

- `GET /api/sync/logs` — list recent sync runs.
- `GET /api/sync/logs/{id}` — get details for a sync run including per-document entries.
- `GET /api/sync/logs/{id}/entries` — list per-document entries for a sync run.

Per-document re-imports (`POST /api/sync/document/{id}`) create a dedicated `sync_log` run and a single `sync_log_entry` for the document. The entry `action` will be `INSERTED` when a new receipt is created, `UPDATED` when an existing receipt is overwritten via the explicit re-import, or `ERROR` when the re-import failed. This allows operators to audit targeted re-import actions separately from scheduled/full sync runs.

These endpoints are annotated for OpenAPI and will appear in the Swagger UI if enabled.

## Parse rules (runtime)

The backend supports rule-based parsing of receipt text via `parse_rule` entries stored in the database. A `parse_rule` can contain a regular expression (with named capture groups such as `store`, `date`, `total`) and a priority. Rules are evaluated in descending priority order and, when matched, the parser will extract fields from the named groups.

Create `parse_rule` rows directly (SQL or admin UI) for now. A management API is now available to manage parse rules at runtime.

Parse rule management API endpoints:

- `GET /api/parse-rules` — list all parse rules
- `GET /api/parse-rules/{id}` — get a single parse rule
- `POST /api/parse-rules` — create a parse rule (JSON body)
- `PUT /api/parse-rules/{id}` — update a parse rule (JSON body)
- `DELETE /api/parse-rules/{id}` — delete a parse rule

Example create using `curl`:

```bash
curl -X POST http://localhost:8080/api/parse-rules \
	-H "Content-Type: application/json" \
	-d '{"name":"Example rule","regex":"^ITEM_REGEX$","priority":10}'
```

Parser parse status behavior:

- **PARSED**: A receipt is marked `PARSED` only when the parser successfully extracted `total_amount`, `receipt_date`, and `store_name`, and at least one `receipt_item` with a non-null `total_price` is present. This mirrors the project specification (F-02.6).
- **PARSE_ERROR**: If one or more of the required fields are missing the parser sets `parse_status = PARSE_ERROR` and fills `parse_error_message` with details of the missing fields. Partially extracted data is still persisted.

## AI fallback (scaffold)

The project includes a scaffolded AI client used as a parsing/categorization fallback when rule-based parsing fails. By default the `AiClient` bean is a no-op. To enable a real AI provider, set the `OPENROUTER_API_KEY` (or configure `ebon.openrouter-api-key`) and ensure the `openrouter-base-url`/model are set in `application.yml`.

The current OpenRouter client is a minimal scaffold and will need a proper prompt and response parsing to be production-ready. AI requests/responses are logged into `ai_categorization_log` for auditing.

AI fallback now supports structured JSON responses: when the AI returns structured `items`, the parser will create corresponding `receipt_item` rows with quantity, unit, unit price and total where available. Additionally, the AI may return a suggested parsing `regex` and metadata; when provided the backend will persist this suggestion as a `parse_rule` (with a default priority) so admins can review and adopt the rule later. This behavior is enabled when an `AiClient` implementation returns a populated `AiParseResult`.

## Rule adaptation (basic)

When users manually correct item categories, the system can create a simple `categorization_rule` automatically (currently the item description is stored as the rule pattern). This is a naive starter implementation intended to be improved with better pattern extraction and rule scoring.

Rule-based categorization:

- The backend evaluates active `categorization_rule` entries in descending `priority` order. Each rule contains a `pattern` (regular expression) and a `category_id` to assign when the pattern matches.
- A rule matches when its `pattern` matches either the `receipt_item.description` or the `receipt.store_name` (case-insensitive). On match, the item's `category` is set to the rule's `category_id` and `category_source` is set to `RULE`.
- If no rule matches, a simple heuristic fallback assigns a `Groceries` category when `raw_text` contains `supermarket`, otherwise `Uncategorized`.

## Admin endpoints (AI logs & settings)

The backend exposes lightweight admin read endpoints that allow querying AI categorization logs and runtime settings stored in the database. These are intended for operators and admin UIs and are annotated for OpenAPI.

- `GET /api/admin/ai-categorization-logs` — list AI categorization requests/responses (payloads, model, cost, createdAt)
- `GET /api/admin/app-settings` — list runtime key/value settings
- `GET /api/admin/app-settings/{key}` — get a single setting by key

Example query for settings:

```bash
curl http://localhost:8080/api/admin/app-settings
curl http://localhost:8080/api/admin/app-settings/feature_x_enabled
```

## Integration tests (Testcontainers)

An end-to-end integration test using Testcontainers (Postgres) and WireMock is included under `backend/src/test/java/de/spacerat76/ebon/PaperlessSyncE2ETest.java`.

- The test is disabled by default to avoid requiring Docker for regular `mvn test` runs.
- To enable it, remove the `@Disabled` annotation from the test class and ensure Docker is running locally.

Run the integration test (once enabled) with:

```bash
# Linux / macOS
RUN_INTEGRATION_TESTS=true mvn -f backend/pom.xml -DskipTests=false test

# Windows (PowerShell)
$env:RUN_INTEGRATION_TESTS='true'; mvn -f backend/pom.xml -DskipTests=false test
```

The integration test verifies Flyway migrations are applied and that a full Paperless sync inserts receipts and writes sync audit logs into Postgres.

## Security: API token for admin & OpenAPI

The server protects API endpoints with a simple API token scheme. Set the token via the `APP_API_TOKEN` environment variable (or `ebon.app-api-token` property) and include it on requests using the `X-API-TOKEN` header or an `Authorization: Bearer <token>` header.

Example accessing an admin endpoint with curl:

```bash
curl -H "X-API-TOKEN: mysecrettoken" http://localhost:8080/api/admin/app-settings
```

The OpenAPI UI (`/swagger-ui`) and raw docs (`/v3/api-docs`) are protected by the same token when `APP_API_TOKEN` is set.

## Docker compose (local dev)

There is a simple `docker-compose.yml` at the repository root that starts a Postgres database and the backend service. The backend is built with the included `backend/Dockerfile`.

Usage:

```bash
docker compose up --build
```

The backend will be available at `http://localhost:8080`. The environment value `APP_API_TOKEN` is set in the compose file to `devtoken` for convenience — change it in production.

## Metrics & structured logs

The backend exposes Prometheus metrics via Spring Boot Actuator at `/actuator/prometheus` and standard metrics under `/actuator/metrics`.

- Prometheus scraping: point your Prometheus server at `http://<host>:8080/actuator/prometheus`.
- Logs are emitted as structured JSON to stdout (configured via `logback-spring.xml`) for easy ingestion by log aggregators.

Warning: actuator endpoints are currently accessible without the API token for convenience on local/dev runs; secure these endpoints appropriately in production (e.g. restrict access to internal network or require the admin token).




