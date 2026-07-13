---
name: ebon-backend
description: Use when changing eBon Spring Boot code, persistence, Flyway migrations, REST DTOs, OpenAPI, security, settings, Paperless sync, OpenRouter integration, backup, restore, reset, or backend tests.
---

# eBon Backend

## Spec Routing

Search `ebon-specification.md` headings with `rg` and read only the sections governing the changed feature plus its acceptance criteria. Use `ebon-parser` for parsing internals and `ebon-adaptive-processing` for learned profile/rule lifecycles.

## Persistence and API

- Use Flyway for every schema change; keep constraints, JPA models, repositories, backup/restore, and reset semantics consistent.
- Return explicit DTOs, never JPA entities. Align validation, OpenAPI, error format, and frontend types.
- Protect all endpoints except `GET /api/health` with app Bearer auth. Keep Swagger protected or configurable off.
- Preserve `receipt.parse_source`: `RULE` for deterministic parsing and `AI` only for an adopted validated AI parse.

## Integrity

- Soft-delete receipts. Apply `TAG_REMOVED` only after complete successful Paperless pagination.
- Represent uncategorized items only as `category_id = NULL` and `category_source = NULL`.
- Hard-delete categories only when unreferenced; otherwise deactivate.
- Make restore/reset/merge/split/bulk/retroactive operations previewed where applicable, explicitly confirmed, transactional, and protective of manual edits and unrelated master data.
- Keep imported-receipt reset separate from product-data reset. Preserve settings, backups, rules, master data, and Flyway history according to the specification.

## Integrations and Privacy

- Keep Paperless, app, and OpenRouter credentials server-side and use their distinct auth schemes.
- Mock Paperless/OpenRouter in tests. Mask secrets centrally in logs, APIs, errors, and backups.
- Store parsing attempts in `ai_parsing_log`; do not store/export full prompts or raw responses by default.
- Persist AI parser rules as `parse_rule_suggestion` first. Activate `parse_rule` only after user acceptance and retain the audit link.
- Build `paperlessDocumentUrl` only from a browser-public URL/template.

## Product Persistence

- Allow one product assignment per item. Preserve family/variant size, unit, and package distinctions.
- Keep product rules separate from categorization rules. A default category fills only an empty category.
- Exclude AI-only assignments from trusted history. Make price exclusions reversible and auditable.
- Use `NO_PRODUCT` for true non-product lines; deposits/bags may remain products when useful.

## Verification

Write focused tests for changed behavior and failure paths, then run:

```bash
cd backend
mvn verify
```

Use `ebon-qa` before completion and add every other changed-surface gate.
