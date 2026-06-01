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
8. React frontend.
9. Backup/restore and runbook.
10. Hardening and final Docker verification.

## Domain-Specific Guardrails

- `TAG_REMOVED` uses soft-delete on receipts (`deleted_at`, `delete_reason`), never unsafe hard-delete.
- `TAG_REMOVED` is only applied after all Paperless pages are fetched successfully.
- Parser output is valid only if it meets the `PARSED` definition and sum validation from the specification.
- AI parsing must use the fixed JSON schema from F-02 and reject invalid JSON.
- AI categorization is optional. If no `OPENROUTER_API_KEY` exists, items remain uncategorized.
- Category deletion is physical only when unreferenced; otherwise deactivate.
- Secrets returned through settings or backup must be masked or marked for reconfiguration.

## Project Skills

Use the local project skills in `.codex/skills/` when relevant:

- `ebon-devcontainer`: scaffolding and maintaining the dev environment.
- `ebon-backend`: Spring Boot backend, API, data model, security.
- `ebon-parser`: receipt parsing, corpus fixtures, AI parsing fallback.
- `ebon-frontend`: React UI and frontend quality.
- `ebon-qa`: verification, test strategy, final checks.

