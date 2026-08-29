---
name: ebon-product-audit
description: Use when auditing product-family or variant assignments for receipt items from verified eBon receipt-audit blocks, correcting obvious assignments, or resuming an interactive product audit.
---

# eBon Product Audit

## Purpose

Audit product assignments interactively with Codex in the verified merchant/branch order from `ebon-receipt-audit`. Use `ebon-adaptive-processing` for family/variant rules and `ebon-qa` for completion evidence. Read the product-audit section of `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`.

## Start or Resume

Run the script through pinned Node:

```powershell
$script = '.codex/skills/ebon-product-audit/scripts/product-audit.mjs'
docker run --rm --env-file .env -e EBON_BASE_URL=http://host.docker.internal:8080 -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node $script next --state var/ebon-codex-audit
```

`next` uses only `VERIFIED` receipt blocks and writes current items plus active families/variants to a private temporary batch.

## Audit One Block

1. Inspect every item in the batch. Separate real products from discounts, payment/metadata lines, and unresolved parser errors. Do not product-audit an incorrectly parsed item.
2. Compare existing and proposed families/variants. A family is the comparable product; size, weight, volume, count, and package structure belong in a variant.
3. Apply an obvious unprotected assignment only at confidence `>= 0.98`. Reuse an active family when possible. Create a family only when its normalized name is unique, no active family/alias has similarity `>= 0.85`, and the source is a safe product line. Never create broad product rules during this audit.
4. Never automatically change `MANUAL`, `CONFIRMED`, `REJECTED`, or `NO_PRODUCT` state. Show the current assignment, exact proposed target, and reason. Set `userConfirmedManual=true` only after the user explicitly confirms that exact item and target in the current conversation.
5. `NO_PRODUCT` also requires explicit user confirmation because it uses the manual review endpoint.
6. Before every mutation, the script re-reads the receipt and checks the frozen family, variant, source, and status. A mismatch or partial failure stops further mutations; report it without retrying.
7. Record ambiguous items with `record-open`: use `PROPOSED`, `USER_CONFIRMATION_REQUIRED`, or `NO_SENSIBLE_PROPOSAL`. A proposal is optional when no defensible target exists.

Closed decisions use `apply --decisions <file>`. Each item ends as `APPLIED` or one of the open statuses. Applying or completing the block deletes its private batch; use `cleanup` after interruption.

## Guardrails

- Never persist descriptions, receipt text, prompts, responses, or credentials.
- AI-only assignments do not establish trusted variant history.
- Do not infer audit approval as approval for manual changes.
