---
name: ebon-adaptive-processing
description: Use when changing eBon merchant or branch format profiles, layout fingerprints, AI profile quarantine, promotion evidence, shadow verification, rollback, Paperless profile bootstrap, learned category rules, or automatic product-family creation.
---

# eBon Adaptive Processing

## Scope

Read the focused contracts when needed:

- `docs/superpowers/specs/2026-07-13-adaptive-receipt-processing-design.md`
- `docs/superpowers/plans/2026-07-13-adaptive-receipt-processing.md`

Also use `ebon-parser`, `ebon-backend`, or `ebon-frontend` only when their surfaces change. Use `ebon-qa` before completion.

## Parsing Profiles

- Store closed-schema, declarative, immutable profile versions; never generated code.
- Keep profiles separate from `parse_rule`/`parse_rule_suggestion` acceptance.
- Identify by normalized merchant, optional branch, and stable layout fingerprint that ignores prices, dates, and minor OCR variation.
- Classify every plausible line as position, metadata/payment/total/tax, explicitly safe ignore, or unresolved. Any unresolved plausible line creates review status and blocks downstream automation for that item.

## Lifecycle and Rollback

- Adopt the current AI parse only through the validated F-02 fallback. Save its profile candidate in quarantine.
- Promote after three distinct, complete, conflict-free receipts of the same scope/fingerprint.
- Shadow-check the first five active hits, then every tenth hit. Invalid or low-confidence AI is missing evidence, not a mismatch.
- On a relevant mismatch, suspend immediately and reparse receipts since the last successful shadow check. Preserve all manual corrections and create a new quarantined version rather than mutating history.

## Paperless Bootstrap

- Use Paperless GET operations only. Preview before exact-confirmation Apply.
- Re-run the current Legacy parser on Paperless content; never use persisted items, categories, products, or manual assignments as comparison truth.
- Keep Legacy-unknown plausible positions unresolved and their profiles quarantined.
- Make preview/apply idempotent and sanitized; never export or commit raw private receipt text.

## Category and Product Learning

- Learn only from confirmed parsed items.
- Automatically create only merchant-specific normalized-exact category rules after three conflict-free receipts. Manual corrections learn immediately only with explicit confirmation; broad, global, regex, or contains rules always require confirmation.
- Create a new product family from one AI assignment only at confidence `>= 0.98`, with a unique normalized name, safe line type, and no existing family/alias similarity `>= 0.85`.
- Model size/package as a variant, not a separate family. Keep AI-only matches out of trusted variant history.

## Tests

Mock Paperless and OpenRouter. Cover identity stability, complete line traces, three-receipt promotion, hit scheduling, mismatch suspension, bounded rollback, manual protection, GET-only bootstrap, idempotency, category conflicts, duplicate-family gates, and absence of private text.
