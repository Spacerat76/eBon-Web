# Agent Instructions for eBon-Web

## Source of Truth and Workflow

`ebon-specification.md` is the product contract. Before changing behavior, locate its relevant headings with `rg`, read only those sections, and keep implementation, tests, API contracts, and documentation aligned. Inspect existing code and tests before editing; prefer small, independently verifiable increments and preserve unrelated user changes.

The supported stack is Spring Boot/Java/Maven/PostgreSQL/Flyway/OpenAPI plus React/TypeScript/Vite/shadcn/Tailwind/Recharts, run through Docker Compose. Development is devcontainer-first: do not assume Java, Maven, Node.js, or PostgreSQL are installed on the host. Treat `.devcontainer/`, Compose files, and `.env.example` as setup truth.

## Project Skills

Use the smallest relevant set from `.codex/skills/`; read each selected `SKILL.md` completely before acting.

| Changed surface | Required project skill |
|---|---|
| Devcontainer, Compose, environment, scaffold | `ebon-devcontainer` |
| Spring Boot, persistence, API, security, sync, backup/reset | `ebon-backend` |
| Receipt parser, OCR, corpus, AI parse fallback | `ebon-parser` |
| React, TypeScript, UX, API client | `ebon-frontend` |
| Merchant profiles, quarantine, shadow checks, learning, rollback | `ebon-adaptive-processing` |
| Tests, acceptance criteria, review, completion | `ebon-qa` |

Select multiple skills only when the change crosses those surfaces. `ebon-qa` is required before any completion claim and whenever tests or acceptance criteria are designed.

## Cross-Cutting Guardrails

- Preserve manual receipt, category, and product corrections unless the user explicitly authorizes overwrite.
- Never commit real tokens, passwords, raw private receipts, unmasked secrets, full AI prompts, or raw AI responses. Mask secrets consistently in logs, APIs, backups, fixtures, screenshots, and errors.
- Automated tests and CI must never call real Paperless-NGX or OpenRouter. Use mocks or test doubles.
- Keep backend DTOs, validation, OpenAPI schemas, and frontend types synchronized. Never expose JPA entities directly.
- `TAG_REMOVED` soft-deletes receipts only after every Paperless page was fetched successfully; never hard-delete imported receipts implicitly.
- Uncategorized items are exactly `category_id = NULL` and `category_source = NULL`; never persist a fake "Ohne Kategorie" category.
- Reset, restore, merge, split, bulk reassign, and retroactive rule application must be explicit, previewed where applicable, confirmed, transactional, and protective of unrelated master data.
- Product assignment allows at most one family/variant per item. Never silently merge different sizes, units, or package structures; AI-only matches do not establish trusted variant history.
- Paperless browser links use a public URL/template and never contain API tokens.
- App authentication is `Authorization: Bearer <APP_API_TOKEN>`; integration-specific credentials stay server-side.

## Verification

During implementation, run the narrowest test proving the changed behavior. Before completion, accumulate every gate for the changed surfaces:

| Surface | Completion gate |
|---|---|
| Markdown/spec/skills only | `git diff --check` plus applicable skill validation |
| Backend | `cd backend && mvn verify` |
| Frontend | `cd frontend && npm run build` |
| Compose/devcontainer | `docker compose config` |
| Full runtime/integration behavior | rebuild Compose and run the focused smoke/E2E flow |

If a required command is unavailable or blocked, report the exact reason and the checks that did run. Never describe a skipped, stale, partial, or failed check as passing. Finish by inspecting `git diff`, `git status --short`, and the relevant test output.
