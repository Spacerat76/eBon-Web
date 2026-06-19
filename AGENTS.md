# Agent Instructions for eBon-Web

This repository is intended to be implemented by AI coding agents. The canonical product contract is `ebon-specification.md`; agents must read the relevant sections before making changes.

## Project Shape

- Backend: Spring Boot, Java, Maven, PostgreSQL, Flyway, OpenAPI.
- Frontend: React, TypeScript, Vite, shadcn/ui, Tailwind CSS, Recharts.
- Runtime: Docker Compose.
- Development: Devcontainer-first. Do not assume Java, Maven, Node.js, or PostgreSQL are installed on the host.
- Auth: single-user Bearer token only: `Authorization: Bearer <APP_API_TOKEN>`.

## Default Working Rules

- Keep changes aligned with `ebon-specification.md`.
- Prefer small, verifiable increments over broad rewrites.
- Preserve user changes. Do not revert unrelated files.
- Add tests with behavior changes, especially for parser, sync, categorization, backup/restore, secrets, and API validation.
- Do not call real Paperless-NGX or OpenRouter.ai from tests. Use mocks or test doubles.
- Never commit real tokens, passwords, raw private receipts, or unmasked secrets.
- Keep DTOs, OpenAPI docs, backend validation, and frontend types consistent.
- Use Devcontainer files and `.env.example` as the source of truth for local setup.

## Required Verification

Run the narrowest useful verification after every meaningful change:

- Backend: `cd backend && mvn verify`
- Frontend: `cd frontend && npm run build`
- Docker smoke test: `docker compose up --build`
- Markdown/spec-only changes: `git diff --check`

If a command cannot run because the project is not scaffolded yet, document that in the final response and verify the files that do exist.

## Implementation Order

Follow the phases in `ebon-specification.md` section 16:

1. Devcontainer, `.env.example`, Docker foundation.
2. Backend skeleton, security, health, OpenAPI.
3. Data model, Flyway, repositories.
4. Paperless sync with mock tests.
5. Parser with corpus tests and AI fallback mocks.
6. Categorization with rules and mocked AI.
7. REST DTOs and validation.
8. Frontend shell with React/Vite/TypeScript, base routing, dashboard foundation, and API client.
9. Receipts UI with receipt list, receipt detail view, raw text view, edit mode, category/status badges, re-parse, and soft-delete display.
10. Search, reports, settings, category/rule management, CSV export, and frontend secret handling.
11. Backup/restore with dry-run, transactional restore, write lock, restore runbook, and backup UI.
12. Real integration, Docker full-system verification, logging, secret masking, README, smoke test, and final hardening.
13. CI, Selenium E2E smoke tests, rolling scheduled backups, category icons, and software versioning.
14. OpenRouter AI parsing fallback with controlled AI parse adoption, AI parsing logs, parser rule suggestions, UI review workflow, fixture preview, and migration export.
15. Phase 15a product foundation: product families, product variants, product assignment on receipt items, product rules/synonyms, automatic assignment after sync/reparse, and backup/restore/reset semantics.
16. Phase 15b product review and maintenance: review queue, manual corrections, merge/split, product management, rule suggestions from manual assignments, and retroactive apply with preview.
17. Phase 15c product price comparison and exports: product pages, store comparison, unit prices, last/minimum/average/median prices, outlier handling, search/report/CSV integration.

## Domain-Specific Guardrails

- `TAG_REMOVED` uses soft-delete on receipts (`deleted_at`, `delete_reason`), never unsafe hard-delete.
- `TAG_REMOVED` is only applied after all Paperless pages are fetched successfully.
- Parser output is valid only if it meets the `PARSED` definition and sum validation from the specification.
- AI parsing must use the fixed JSON schema from F-02 and reject invalid JSON. A KI parse may be adopted only when schema validation, required fields, contiguous item indexes, sum tolerance, and `ai_parsing_min_confidence` pass.
- Successful KI parsing sets `receipt.parse_source = AI`; successful rule-based parsing sets `receipt.parse_source = RULE`.
- OpenRouter KI parsing attempts must be written to `ai_parsing_log` without storing full prompts or raw responses by default.
- KI-generated parser rules must first be stored as `parse_rule_suggestion`; never create active `parse_rule` entries automatically without user acceptance.
- `FULL_TEXT` AI parsing for manual reparse requires explicit UI confirmation. Automatic sync must respect `ai_parsing_sync_call_limit`.
- Parser rule suggestions must explain trigger, problem, solution rationale, and validation status, and accepted suggestions may be exported as Flyway migration drafts.
- AI categorization is optional. If no `OPENROUTER_API_KEY` exists, items remain uncategorized.
- Uncategorized items are represented as `category_id = NULL` and `category_source = NULL`; do not create or persist a fake "Ohne Kategorie" category.
- Bonus fields store only points or balance newly earned in the receipt, never the current loyalty-account balance.
- Paperless document links must be built from a browser-reachable public URL or URL template and must never contain API tokens or other secrets.
- Data-maintenance reset operations must be explicit, transactional, and limited to imported receipt data and related detail/log data; categories, categorization rules, settings, backups, and Flyway history must remain intact.
- Category deletion is physical only when unreferenced; otherwise deactivate.
- Secrets returned through settings or backup must be masked or marked for reconfiguration.
- Rolling automatic backups must use the same secret masking as manual backups and must not delete manually downloaded backups.
- CI and E2E tests must run without real Paperless-NGX/OpenRouter tokens or private receipt data.
- Product assignment uses product families plus variants: e.g. `Coca Cola Zero` is a family, while `0.33l` and `0.5l` bottles are distinct variants.
- A `receipt_item` may have at most one product assignment. Multi-product bundle splitting is out of scope for Phase 15.
- Product assignment must not silently merge distinct sizes, units, or package structures. Unknown size may assign only a family unless clear trusted history supports a variant.
- Trusted history for product assignment includes manual assignments, accepted suggestions/rules, and rule-based automatic matches; AI-only matches must not establish clear history by themselves.
- Product rules are separate from categorization rules. A product family may fill an empty category via its default category, but product assignment must not overwrite an existing or manually set category.
- Product assignment may use rules, trusted history, and KI. High-confidence KI may assign automatically; uncertain matches must go to the product review queue.
- KI product assignment may send normalized item text, store, price, and quantity, but not full receipt raw text. Do not store full KI prompts or raw responses by default.
- `NO_PRODUCT` is valid for non-product lines such as pure discounts, coupons, payment lines, and rounding differences. Deposit and bag lines may be modeled as normal product families when useful.
- Product price comparisons use the effective paid price by default and may also show regular price only when safely derivable.
- Backup/restore must include product master data, rules, assignments, review state, and price-exclusion data. The imported-receipts reset keeps product master data/rules; a separate explicit action is required to reset product data.

## Project Skills

Use the local project skills in `.codex/skills/` when relevant:

- `ebon-devcontainer`: scaffolding and maintaining the dev environment.
- `ebon-backend`: Spring Boot backend, API, data model, security.
- `ebon-parser`: receipt parsing, corpus fixtures, AI parsing fallback.
- `ebon-frontend`: React UI and frontend quality.
- `ebon-qa`: verification, test strategy, final checks.
