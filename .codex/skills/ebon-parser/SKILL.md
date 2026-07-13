---
name: ebon-parser
description: Use when changing eBon receipt parsing, OCR normalization, parser rules, parse validation, parser corpus fixtures, AI parsing fallback, parse traces, or parser reparse behavior.
---

# eBon Parser

## Contract

Read `ebon-specification.md` F-02, F-13, and relevant acceptance criteria. A receipt is `PARSED` only with `total_amount`, `receipt_date`, `store_name`, and at least one item with valid `total_price`; optional time/branch may be absent. Item totals must match the receipt within `0.02`.

Use `ebon-adaptive-processing` additionally for merchant/branch profiles, fingerprints, quarantine, shadow checks, rollback, or Paperless bootstrap.

## Deterministic Parsing

- Normalize German numbers (`1,99`, `1.234,56`) without losing negative amounts.
- Merge multiline descriptions and keep `position_index` contiguous and unique.
- Preserve quantity, unit, unit/total price, discount, deposit, and package text faithfully.
- Keep positional discounts, coupons, deposits, bags, and other valid receipt positions. Do not infer product families or variants inside the parser.
- Store only bonus points/balance newly earned on this receipt, never the loyalty account balance.
- Preserve useful partial output with a clear non-success status and error reason.

## AI Fallback Boundary

- Invoke adoption fallback only after deterministic parsing fails. Shadow comparison of a valid profile result is evidence, not replacement.
- Require the fixed F-02 JSON schema, required fields, numeric/date validity, contiguous indexes, `0.02` sum tolerance, and `ai_parsing_min_confidence`.
- Set `parse_source = AI` only for adopted AI output and `RULE` for valid deterministic output.
- Respect `ai_parsing_sync_call_limit`; leave an explicit error when exhausted.
- Require explicit confirmation for manual `FULL_TEXT`; otherwise minimize text.
- Log attempts in `ai_parsing_log` without full prompts/raw responses by default; mock AI in tests.
- Store generated parser rules as `parse_rule_suggestion`, never active `parse_rule`. Validate regex, example extraction, and tax/TSE/payment collisions before user acceptance. Never create categorization rules here.

## Corpus and Verification

Keep anonymized fixtures in `backend/src/test/resources/corpus/`; every receipt `.txt` has a matching `.expected.json`. Add a regression fixture/test before parser fixes.

Run the narrow parser test first, then:

```bash
cd backend
mvn test
mvn verify
```

Use `ebon-qa` before completion.
