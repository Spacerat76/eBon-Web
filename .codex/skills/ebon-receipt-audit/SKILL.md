---
name: ebon-receipt-audit
description: Use when auditing all Paperless eBons against current deterministic parser rules, grouping receipts by merchant or branch, correcting recurring parse errors, or resuming an interactive receipt audit.
---

# eBon Receipt Audit

## Purpose

Audit Paperless receipts interactively with Codex, largest merchant first. Inspect every receipt in a merchant/branch block before changing rules; then rerun the entire block. This audit never uses OpenRouter.

Read `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md` and use `ebon-parser` plus `ebon-qa` for parser changes and completion evidence.

## Start or Resume

Run the script through pinned Node. It reads `.env` but ignores OpenRouter settings:

```powershell
$script = '.codex/skills/ebon-receipt-audit/scripts/receipt-audit.mjs'
docker run --rm --env-file .env -e EBON_BASE_URL=http://host.docker.internal:8080 -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node $script inventory --state var/ebon-codex-audit
docker run --rm --env-file .env -e EBON_BASE_URL=http://host.docker.internal:8080 -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node $script next --state var/ebon-codex-audit
```

Use `inventory` only for a new or explicitly refreshed run. Use `next` to resume the first unfinished block.

## Audit One Block

1. Open the temporary batch path printed by `next`.
2. Compare Paperless text with merchant, branch, date, total, every parsed item, and every plausible price/position line in the trace.
3. Finish inspecting all receipts before editing anything. Collect recurring and isolated errors separately.
4. If Paperless text looks incomplete, inspect the original read-only and use local OCR only as evidence; never replace Paperless text.
5. Correct an obvious configurable rule only when it explains the collected errors and has a bounded merchant/branch scope. Preview impact when supported.
6. Before changing parser source code or tests, stop and ask the user. State the cause, files, proposed behavior, and regression test. Approval for the audit is not approval for that code change.
7. Rerun the complete block after every accepted correction. No regression and no silently lost plausible line are required.
8. Present ambiguous cases together. Each ends as `NEEDS_USER` with a concrete proposal or `NO_SENSIBLE_PROPOSAL` with a reason.

Record every receipt with `VERIFIED`, `NEEDS_USER`, or `NO_SENSIBLE_PROPOSAL` using `record --decisions <file>`. The decision file contains IDs, statuses, and reason codes only. Recording deletes the private batch. Use `cleanup` after an interrupted session.

## Guardrails

- Paperless is GET-only; incomplete pagination invalidates the new inventory.
- Reparse always disables AI and preserves manual receipt edits.
- Never copy receipt/OCR text into progress, Git, reports, prompts saved to disk, or errors.
- Missing local receipts stay `UNMATCHED_PAPERLESS`; do not invent or silently sync them.
