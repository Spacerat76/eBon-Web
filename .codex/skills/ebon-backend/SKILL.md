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
- Category hard-delete is allowed only when unreferenced; otherwise deactivate.
- Secrets must be centrally masked before logging, returning through APIs, or exporting backups.
- Backup restore must be transactional.

## External Integrations

- Paperless-NGX auth: `Authorization: Token <PAPERLESS_API_TOKEN>`.
- App auth: `Authorization: Bearer <APP_API_TOKEN>`.
- OpenRouter auth: `Authorization: Bearer <OPENROUTER_API_KEY>`.
- Tests must mock Paperless-NGX and OpenRouter.ai.

## Backend Test Priorities

- Security: unauthorized endpoints return `401`.
- Sync: import, idempotency, pagination failure, sync lock, `TAG_REMOVED`.
- Parser and categorization integration boundaries.
- Settings: masked secret update semantics.
- Backup/restore: dry-run, incompatible manifest, rollback on failure.
- API validation and error format.

## Verification

```bash
cd backend
mvn verify
```

If backend does not exist yet, create the minimal skeleton before adding feature code.

