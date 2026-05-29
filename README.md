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

This project uses Flyway for schema migrations. Migration scripts are located in `backend/db/migrations` and will be executed automatically by the backend when Flyway is enabled (see `application.yml`).

To run the application locally without Postgres, you can override the datasource to use H2 in-memory (useful for development and tests):

```bash
mvn -f backend/pom.xml -DskipTests package
java -jar backend/target/ebon-backend-0.1.0-SNAPSHOT.jar --spring.datasource.url=jdbc:h2:mem:ebondb --ebon.app-api-token=devtoken
```

When deploying to production, ensure Postgres is available and that the Flyway migrations are applied on startup.

## Parse rules (runtime)

The backend supports rule-based parsing of receipt text via `parse_rule` entries stored in the database. A `parse_rule` can contain a regular expression (with named capture groups such as `store`, `date`, `total`) and a priority. Rules are evaluated in descending priority order and, when matched, the parser will extract fields from the named groups.

Create `parse_rule` rows directly (SQL or admin UI) for now; a future management API will expose CRUD for rules.

## AI fallback (scaffold)

The project includes a scaffolded AI client used as a parsing/categorization fallback when rule-based parsing fails. By default the `AiClient` bean is a no-op. To enable a real AI provider, set the `OPENROUTER_API_KEY` (or configure `ebon.openrouter-api-key`) and ensure the `openrouter-base-url`/model are set in `application.yml`.

The current OpenRouter client is a minimal scaffold and will need a proper prompt and response parsing to be production-ready. AI requests/responses are logged into `ai_categorization_log` for auditing.

## Rule adaptation (basic)

When users manually correct item categories, the system can create a simple `categorization_rule` automatically (currently the item description is stored as the rule pattern). This is a naive starter implementation intended to be improved with better pattern extraction and rule scoring.


