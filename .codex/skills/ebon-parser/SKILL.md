# eBon Parser Skill

Use this skill for receipt parsing, parser rules, parser tests, AI parsing fallback, and parser corpus fixtures.

## Read First

- `ebon-specification.md` sections F-02, F-13, 17.2, 17.3.
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

## AI Fallback Rules

- AI parsing is fallback only after rule-based parsing fails.
- AI output must match the JSON schema in F-02.
- Invalid AI JSON must not be trusted or persisted as parsed data.
- Tests must mock AI responses.
- Automatic AI adaptation may create `parse_rule` entries, never `categorization_rule` entries.

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
