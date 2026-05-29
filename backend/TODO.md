# eBon Backend — Open TODOs

Generated: 2026-05-29

This file lists open backend tasks discovered by scanning the current codebase and the project specification (`ebon-specification.md`). It focuses on items that are either not implemented, partially implemented, or that should be revisited to align implementation with the spec.

## High priority

- Align re-import behavior with spec: current `PaperlessSyncServiceImpl` updates existing receipts when document IDs are present; the spec requires *no automatic re-import/overwrite* unless an explicit re-import is triggered. Next: decide desired behavior and implement an explicit re-import API. (See: `service/PaperlessSyncServiceImpl.java`)
 - [x] Ensure Parser validation: parser now sets `PARSED` only when required fields (`total_amount`, `receipt_date`, `store_name`) and at least one `receipt_item.total_price` are present. Otherwise `PARSE_ERROR` is set and `parse_error_message` lists missing fields. Unit tests updated.
- [x] Implement rule-based categorization: the service now loads `categorization_rule` records (priority-ordered) and applies `pattern` regex to `receipt_item.description` and `receipt.store_name`. Matched rules assign the `category` (source=`RULE`). Fallback heuristic remains. Unit tests added.

## Medium priority

- [x] Implement AI batch categorization + logging: the `AiCategorizationService` calls the configured `AiClient` per-receipt, persists `ai_categorization_log` entries and exposes a `/api/ai/batch-categorize` endpoint. Unit tests added. Next: add retry/backoff and cost tracking in `OpenRouterAiClient`.
- Harden `OpenRouterAiClient`: currently a minimal scaffold. Implement proper request payloads, response parsing, retries with exponential backoff, and configuration for model/timeout. Add unit tests that mock remote responses.
- [x] Decide/implement TAG_REMOVED semantics: the sync now deletes receipts that lost the Paperless tag and records a `TAG_REMOVED` `sync_log_entry`. Tests and README updated.

## Lower priority / housekeeping

- [x] Replace manual SQL migration used in tests with classpath Flyway migrations for tests.
  Migration SQL moved to `src/main/resources/db/migration/V1__init_schema.sql` and the test harness now picks up classpath migrations. A test-time fallback applies the SQL directly early during test startup to guarantee schema availability when Flyway auto-migration isn't detected in the test environment.
- Remove `NoSchedulerConfig` test hack: tests currently include a local `NoSchedulerConfig` test configuration. Prefer disabling scheduling via a test profile or properties only, and remove the bean-post-processor hack.
- Paperless client retry tuning and backoff: add robust retry policy, configurable attempts and backoff, rate-limit handling and unit tests. (See: `service/PaperlessClient`)
 - [x] Paperless client retry tuning and backoff: add robust retry policy, configurable attempts and backoff, rate-limit handling and unit tests. (See: `service/PaperlessClient`)
- Bulk categorization workflows + UI hooks: implement backend endpoints to support bulk apply/confirm rules and export suggestions for UI integration.
- [x] Add explicit Re-Import API (UC-09) with audit and tests: re-parsing/re-importing a single document on demand now creates a `sync_log` and a `sync_log_entry` with action `INSERTED`/`UPDATED`/`ERROR`. Unit tests added.
- Add DB index migrations: ensure indexes listed in spec are created by migrations and included in `V1__init_schema.sql` or subsequent migration scripts.
- Add categorization rules tests and e2e coverage (tests that exercise rule match priority, AI fallback, and admin log visibility).

## Notes & findings

- Many spec items are scaffolded and partially implemented: parse rules, categorization-rule entities, AI DTOs, admin read endpoints, and Flyway are present.
- Current implementation choices (e.g., marking vs deleting receipts on TAG_REMOVED, parser `PARSED` behavior) should be validated against product requirements — I flagged them above.

If you want, I can:
- open PRs implementing one of the high-priority items (pick one),
- implement the Flyway test-classpath migration change (small, recommended), or
- convert `CategorizationServiceImpl` to use `CategorizationRuleRepository` with tests.
