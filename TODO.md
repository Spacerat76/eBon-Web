# Open TODOs — prioritized

This file lists remaining open work items after cross-checking the eBon specification against the current backend implementation. Items are grouped by priority (High → Medium → Low). Completed work (migrations, basic rule parser, AI scaffold, naive rule adaptation) has been removed from this list.

## High Priority
 - Paperless sync: deduplicate receipts by `paperless_document_id` (skip or update). (implemented)
 - Paperless sync: handle TAG_REMOVED events (remove/mark receipts). (implemented)
 - Persist sync audits: write `sync_log` and `sync_log_entry` records for each sync run and document action. (implemented)
 - Read API for sync audits: `GET /api/sync/logs`, `GET /api/sync/logs/{id}`, `GET /api/sync/logs/{id}/entries`. (implemented)
 - Unit tests for sync log read API. (implemented)
 - OpenAPI/README docs: add sync log endpoints to docs. (implemented)
 - Parser: AI JSON fallback + persist suggested `parse_rule` when AI provides one. (implemented)
 - CRUD APIs: implement management endpoints for `parse_rule` and `categorization_rule` (OpenAPI annotated, with tests). (parse_rule CRUD implemented)
 - API: add endpoints to query `sync_log`, `sync_log_entry`, `ai_categorization_log`, and `app_settings` (for runtime configuration visibility). (implemented)
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
