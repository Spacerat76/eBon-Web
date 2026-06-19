# eBon Parser Skill

Use this skill for receipt parsing, parser rules, parser tests, AI parsing fallback, and parser corpus fixtures.

## Read First

- `ebon-specification.md` sections F-02, F-13, F-19, 17.2, 17.3 when parser output affects product assignment.
- `AGENTS.md`.

## Parser Contract

A receipt is `PARSED` only if all are true:

- `total_amount` exists.
- `receipt_date` exists.
- `store_name` exists.
- At least one `receipt_item` exists with valid `total_price`.

Optional fields such as `receipt_time` and `store_branch` may be missing.

Bonus fields must describe only what was newly earned in this receipt:

- `bonus_balance`: newly earned monetary loyalty balance for this purchase, not the current account balance.
- `bonus_points`: newly earned loyalty points for this purchase, not the current points balance.
- `bonus_type`: loyalty program name such as Payback, DeutschlandCard, or Bonusclub.

## Normalization Rules

- Parse German numbers: `1,99` -> `1.99`, `1.234,56` -> `1234.56`.
- Keep negative amounts, coupons, discounts, and deposits when they appear as receipt positions.
- Merge multi-line item descriptions into one `description`.
- Keep `position_index` contiguous and unique per receipt.
- Sum of item totals may differ from receipt total by at most `0.02`.
- Preserve parsed quantity, unit, unit price, total price, discounts, deposits, and package-like text as faithfully as possible because product assignment and unit-price reports depend on them.
- Do not infer product families or variants inside the receipt parser. Product assignment is a later workflow after parsing and categorization.
- Keep pure discounts, coupons, payment lines, deposits, and bags as receipt items when they appear as positions; Phase 15 decides whether they are products or `NO_PRODUCT`.

## AI Fallback Rules

- AI parsing is fallback only after rule-based parsing fails.
- AI output must match the JSON schema in F-02.
- Invalid AI JSON must not be trusted or persisted as parsed data.
- Valid AI JSON may be adopted only when required fields, contiguous `position_index`, numeric/date parsing, sum tolerance `0.02`, and `ai_parsing_min_confidence` all pass.
- Adopted AI parses must set `receipt.parse_source = AI`; valid rule-based parses must set `receipt.parse_source = RULE`.
- The OpenRouter prompt should include minimized or explicitly confirmed full text, rule-parser partial output, and the rule-parser failure reason.
- Automatic sync must respect `ai_parsing_sync_call_limit`; when the limit is reached the receipt remains `PARSE_ERROR` with a clear reason.
- `FULL_TEXT` manual reparse requires explicit user confirmation.
- AI parsing attempts must be logged in `ai_parsing_log` without storing full prompts or raw responses by default.
- Tests must mock AI responses.
- AI may propose parser rules as `parse_rule_suggestion` entries, never as active `parse_rule` entries directly.
- Parser rule suggestions must include trigger, problem description, solution rationale, validation status, and may become active `parse_rule` entries only after user acceptance.
- AI parsing and parser rule suggestions must never create `categorization_rule` entries.
- Parser rule suggestion validation must reject invalid regexes, no-match regexes, wrong extractions, and obvious tax/TSE/payment-line collisions.

## Corpus Rules

Store fixtures under:

```text
backend/src/test/resources/corpus/
```

Each receipt text must have a matching `.expected.json`.

Minimum fixture set:

- `rewe_simple`
- `aldi_discount`
- `dm_bonus`
- `lidl_multiline_items`
- `parse_error_missing_total`

## Verification

Run parser-focused tests first, then full backend verification:

```bash
cd backend
mvn test
mvn verify
```
