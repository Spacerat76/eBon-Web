---
name: ebon-qa
description: Use when reviewing eBon changes, selecting verification, defining acceptance tests, or preparing a completion claim, especially for parser, sync, data-loss, secret, backup, restore, product, and external-integration risks.
---

# eBon QA

## Principle

Require fresh evidence before every quality or completion claim. Run focused checks during implementation, then accumulate the full completion gate for every changed surface.

## Verification Ladder

| Changed surface | During work | Before completion |
|---|---|---|
| Markdown, specification, skills | targeted scans | `git diff --check` and skill validation |
| Backend | focused Maven test/class | `cd backend && mvn verify` |
| Frontend | focused Vitest/E2E flow | `cd frontend && npm run build` |
| Compose/devcontainer | affected config check | `docker compose config` |
| Runtime/integration | focused mocked test | rebuild Compose and run affected smoke/E2E flow |

When surfaces overlap, run every applicable completion gate. Treat unavailable, skipped, stale, or partial checks as unverified, never passing.

## Risk Priorities

Test behavior and failure paths, with highest priority for:

- manual-edit preservation and bounded reprocessing;
- transactional restore/reset and previewed destructive actions;
- Paperless pagination safety before `TAG_REMOVED`;
- mocked Paperless/OpenRouter calls and exhausted-call budgets;
- secret, prompt, raw-response, and private-receipt absence from all outputs;
- parser schema, required fields, contiguous indexes, sum tolerance, and corpus regressions;
- uncategorized NULL semantics and conservative product family/variant matching;
- backup/restore coverage for every new persistent table.

Prefer real behavior over mock verification. Add a regression test before fixing a defect. Do not weaken assertions to make an implementation pass.

## Completion Evidence

Before handoff:

1. Re-read the relevant specification acceptance criteria.
2. Inspect the complete diff and unrelated user changes.
3. Run all applicable gates above and read their full output.
4. Run `git status --short` and `git diff --check`.
5. Report commands, outcomes, and anything not verified.

Use the domain skill for detailed invariants; do not reconstruct backend, parser, frontend, or adaptive-processing rules here.
