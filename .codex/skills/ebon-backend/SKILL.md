# eBon Backend Skill

Use this skill for Spring Boot backend work, persistence, REST APIs, security, settings, sync, backup/restore, OpenAPI, and backend tests.

## Read First

- `ebon-specification.md` sections 4, 5, 6, 7, 8, 10, 12, 13, 14, 16, 17.
- `AGENTS.md`.

## Architecture Rules

- Do not expose JPA entities directly from controllers.
- Use explicit DTOs and validation annotations.
- Keep DTOs, OpenAPI schemas, and frontend types aligned.
- Use Flyway for every database schema change.
- Protect every endpoint except `GET /api/health` with Bearer token auth.
- Swagger UI and `/v3/api-docs` must be token-protected or configurable off.

## Data Rules

- Receipts use soft-delete via `deleted_at` and `delete_reason`.
- `TAG_REMOVED` is only applied after complete successful Paperless pagination.
- Uncategorized receipt items must keep both `category_id = NULL` and `category_source = NULL`; never persist a fake "Ohne Kategorie" category or a source badge without a category.
- Category hard-delete is allowed only when unreferenced; otherwise deactivate.
- Secrets must be centrally masked before logging, returning through APIs, or exporting backups.
- Backup restore must be transactional.
- Data-maintenance reset operations must be explicit, confirmed, and transactional. They may delete imported receipts, receipt items, parser/AI/sync detail data, but must keep categories, categorization rules, settings, backups, and Flyway history.
- `receipt.parse_source` must reflect the current parser source: `RULE` for rule-based parser output and `AI` for adopted OpenRouter KI parsing output.
- OpenRouter KI parsing attempts belong in `ai_parsing_log`, not `ai_categorization_log`.
- KI-generated parser rules must be stored as `parse_rule_suggestion` first. They become active `parse_rule` rows only after explicit user acceptance.
- Accepted parser rule suggestions must remain audit-linked to the generated `parse_rule` and may be exported as Flyway migration drafts.
- Backup/restore must include `ai_parsing_log` and `parse_rule_suggestion`, while preserving the default rule that full prompts and raw KI responses are not exported.

## API and Settings Rules

- `ReceiptDTO.paperlessDocumentUrl` is optional and must be built from `PAPERLESS_PUBLIC_BASE_URL` or a configured document URL template, not from secret-bearing API URLs.
- Paperless document links returned by the API must never contain API tokens or other secrets.
- Re-parse-all defaults to preserving manual edits (`overwriteManualEdits=false`) unless the user explicitly confirms overwriting.
- Search, receipt lists, and dashboard links that target uncategorized work must use the same semantic state: `category_id = NULL` and `category_source = NULL`.
- Settings must expose separate KI parsing controls: enabled flag, parsing model, max tokens, temperature, minimum confidence, sync call limit, text mode, and local debug-snippet flag.
- Manual reparse with KI text mode `FULL_TEXT` must require explicit confirmation before sending full receipt text to OpenRouter.
- API DTOs for receipts must expose `parseSource` and a prompt-free `aiParsingSummary` when available.

## External Integrations

- Paperless-NGX auth: `Authorization: Token <PAPERLESS_API_TOKEN>`.
- App auth: `Authorization: Bearer <APP_API_TOKEN>`.
- OpenRouter auth: `Authorization: Bearer <OPENROUTER_API_KEY>`.
- Tests must mock Paperless-NGX and OpenRouter.ai.

## Backend Test Priorities

- Security: unauthorized endpoints return `401`.
- Sync: import, idempotency, pagination failure, sync lock, `TAG_REMOVED`.
- Parser and categorization integration boundaries.
- OpenRouter KI parsing: valid JSON adoption, invalid JSON rejection, low-confidence rejection, sync-call limit, missing API key, disabled fallback, `FULL_TEXT` confirmation, and prompt/response snippet masking.
- Parser rule suggestions: validation, edit, accept, reject, generated `parse_rule`, and migration export.
- Settings: masked secret update semantics.
- Settings/data maintenance: Paperless public URL/template, re-parse-all defaults, and reset safety.
- Backup/restore: dry-run, incompatible manifest, rollback on failure.
- API validation and error format.

## Verification

```bash
cd backend
mvn verify
```

If backend does not exist yet, create the minimal skeleton before adding feature code.
