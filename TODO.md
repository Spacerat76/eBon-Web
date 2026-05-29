# Open TODOs — prioritized

This file lists remaining open work items after cross-checking the eBon specification against the current backend implementation. Items are grouped by priority (High → Medium → Low). Completed work (migrations, basic rule parser, AI scaffold, naive rule adaptation) has been removed from this list.

## High Priority

- Implement scheduled background sync job (`@Scheduled`) configurable via `ebon.sync-interval-minutes`.
- Paperless client: follow pagination (`next` links), support server-side tag filtering (`tags__name` / `page_size`) and iterate pages until `next == null`.
- Paperless sync: deduplicate receipts on import (skip or update by `paperless_document_id`).
- Paperless sync: detect and handle `TAG_REMOVED` events (remove or mark receipts) and record the action.
- Persist sync audits: write `sync_log` and `sync_log_entry` records for each sync run and document action.
- Parser enhancements: extract structured `receipt_items` (quantity/unit/unit_price/total_price), `currency`, and bonus fields (`bonus_balance`, `bonus_points`, `bonus_type`).
- Parser AI integration: implement AI JSON fallback that returns structured JSON and persist/adapt `parse_rule` entries derived from AI output.
- Categorization engine: evaluate active `categorization_rule` entries (match_field, match_type, priority) before falling back to AI.
- AI production integration: implement robust OpenRouter/OpenAI client with prompt engineering, response parsing, batching per receipt, rate-limiting, retries/backoff and AI cost tracking; log calls to `ai_categorization_log`.

## Medium Priority

- Rule adaptation improvements: improve pattern extraction, rule scoring/hit-count updates, de-duplication and UI suggestion flow for confirming learned rules.
- CRUD APIs: implement management endpoints for `parse_rule` and `categorization_rule` (OpenAPI annotated, with tests).
- API: add endpoints to query `sync_log`, `sync_log_entry`, `ai_categorization_log`, and `app_settings` (for runtime configuration visibility).
- Testcontainers E2E: add integration tests that spin up Postgres + WireMock + Flyway to validate full sync → parse → categorize flows.
- Security: remove temporary OpenAPI/Swagger exemptions and secure docs/admin endpoints (admin/API-token guard) per the spec.

## Low Priority / Infra

- Docker: add `Dockerfile` and `docker-compose` for local dev (Postgres + Paperless mock + backend) and provide a `README` dev run recipe.
- CI: add pipeline to run unit tests, Testcontainers integration tests, and build artifacts/images.
- Structured logging & metrics: add structured logs and metrics (sync durations, AI call counts/costs, parse failure rates) and wire to Actuator/Prometheus if desired.
- Paperless client tuning: expose configurable retry/circuit-breaker settings and improve error messages and test coverage.
- Bulk operations & UI: implement bulk categorization workflows (apply a category across matching descriptions) and related UI hooks.
- Performance & search: add/verify DB indexes (GIN tsvector for `receipt_item.description`), query tuning and pagination for large datasets.

---

If you want, I can begin by implementing any single high-priority item (recommended order: pagination/dedupe → sync auditing → scheduled job → parser AI JSON fallback). Tell me which to pick and I'll start implementing it with tests and OpenAPI updates.
