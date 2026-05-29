# Project TODOs — Open Points (eBon Backend)

Generated from the spec cross-check (2026-05-29). This file lists open work items derived from the current implementation and the specification.

## High Priority

 - [x] Implement rule-based parser and parse_rule persistence (basic rule engine added)
  - Replace the placeholder ParserServiceImpl with a rule/strategy engine. (done)
  - Extract receipt_date, store_name, total_amount, currency, receipt_items, and bonus fields. (partial)
  - Persist and apply parse_rule entries so parsing can adapt and be reused. (DB table + repository added)

 - Implement AI fallback for parsing & categorization
  - Add OpenRouter/OpenAI client with batching, rate-limiting and retries (exponential backoff).
  - Log AI calls/results to ai_categorization_log and surface errors for rule adaptation.

- [x] Add Flyway DB migrations (completed)
  - Create migration scripts for current domain tables and new tables: parse_rule, categorization_rule, ai_categorization_log, sync_log, sync_log_entry, app_settings.
  - Ensure migrations run in CI and local dev.

- Improve Paperless sync behavior
  - Follow pagination (next links) when fetching documents.
  - Deduplicate existing receipts on import (skip or update existing by paperless_document_id).
  - Handle tag removal events (TAG_REMOVED) per spec—remove or mark receipts accordingly.
  - Persist sync audits to sync_log.

## Medium Priority

- Add scheduled background sync job
  - Implement @Scheduled sync configurable by ebon.sync-interval-minutes.

- Implement categorization rule engine & persistence
  - Store and evaluate categorization_rule entries; allow manual overrides and rule learning.

- Add Testcontainers-based E2E tests
  - Compose integration tests that run Postgres + WireMock + Flyway and validate full sync → parse → categorize flow.

## Low Priority / Infra

- Harden security and docs access
  - Revert temporary OpenAPI/Swagger exemptions and expose docs via secured admin access or documented dev flag.

- Add Dockerfile and docker-compose
  - Provide a local dev compose (Postgres + Paperless mock + backend) and CI pipeline to run unit and integration tests.

- Add API endpoints for rules, logs and settings
  - CRUD for parse_rule and categorization_rule; endpoints to query sync_log and ai_categorization_log and app settings.

- Structured logging and audit
  - Add structured logs/metrics for sync durations, AI call counts/costs, and parse failures.

## Paperless client improvements

- Pagination, retry/circuit-breaker tuning and clearer error handling in PaperlessClientHttp.

## Notes / Context

- Current branch: implementation_5mini
- Starting points / files to examine:
  - backend/src/main/java/de/spacerat76/ebon/service/ParserServiceImpl.java
  - backend/src/main/java/de/spacerat76/ebon/service/PaperlessClientHttp.java
  - backend/src/main/java/de/spacerat76/ebon/service/PaperlessSyncServiceImpl.java

- Local dev quick tip: run the app with an in-memory H2 override when Postgres isn't available: set spring.datasource.url=jdbc:h2:mem:ebondb

---

If you want, I can start by (choose):

1. Creating the Flyway migrations for domain + spec tables, or
2. Implementing the parser rule engine (unit-tested), or
3. Adding the OpenRouter AI client scaffold and logging.

Tell me which to pick and I'll begin implementing it.
