# Agent Workflows

This document gives future AI agents short, repeatable workflows for this project. `ebon-specification.md` remains the authoritative source.

## Starting Any Task

1. Read `AGENTS.md`.
2. Read the relevant section of `ebon-specification.md`.
3. Select the matching project skill from `.codex/skills/`.
4. Inspect existing files before editing.
5. Make the smallest useful change.
6. Run the narrowest relevant verification.

## New Feature Workflow

1. Identify the feature ID in the specification.
2. Confirm data model, API DTOs, UI behavior, and tests affected.
3. Add or update tests first when behavior is risky.
4. Implement backend contract and validation.
5. Update frontend types and UI.
6. Run backend and frontend verification.

## Parser Fixture Workflow

1. Add `<store_case>.txt` to `backend/src/test/resources/corpus/`.
2. Add matching `<store_case>.expected.json`.
3. Run parser tests and observe failure.
4. Update parser rules or store-specific strategy.
5. Re-run parser tests and `mvn verify`.

## Sync Workflow

1. Mock Paperless-NGX responses.
2. Test import, idempotency, pagination, and failure paths.
3. Confirm `TAG_REMOVED` only runs after complete successful pagination.
4. Confirm repeated syncs do not duplicate receipts.

## Secret Handling Workflow

1. Treat all tokens and passwords as sensitive.
2. Use masked values in settings responses.
3. Ignore `"********"` on settings update.
4. Exclude or mask secrets in backup export.
5. Add tests for logs, API responses, and backup JSON when secret behavior changes.

