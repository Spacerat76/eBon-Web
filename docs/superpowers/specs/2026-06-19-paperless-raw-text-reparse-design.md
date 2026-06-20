# Paperless Raw Text Reparse Design

## Goal

Before an individual receipt is reparsed, eBon checks whether Paperless-NGX now contains different extracted text and lets the user decide whether to adopt it.

## Scope

- Applies only to the receipt-detail reparse flow.
- Bulk reparse always uses the stored `receipt.raw_text`.
- The comparison normalizes CRLF/LF line endings only. Whitespace and content differences remain meaningful.
- The status response contains no raw text, hashes, tokens, or Paperless URL details.

## API Contract

`GET /api/receipts/{id}/paperless-raw-text-status` returns one of:

- `UNCHANGED`: Paperless text is equivalent to the stored text.
- `CHANGED`: Paperless text differs from the stored text.
- `UNAVAILABLE`: Paperless could not be read; no external error details are exposed.

`POST /api/receipts/{id}/reparse` accepts `rawTextSource=STORED|PAPERLESS` and defaults to `STORED` for API compatibility. `PAPERLESS` fetches the document again immediately before parsing. It updates `receipt.raw_text` and applies the parse in one transaction; failed fetching leaves the receipt unchanged.

## UI Flow

1. The user selects `Erneut parsen` in a receipt detail.
2. The UI requests the raw-text status.
3. For `CHANGED`, a dialog offers `Neuen Rohtext uebernehmen und parsen`, `Gespeicherten Rohtext verwenden`, and cancel.
4. For `UNCHANGED`, the UI reparses the stored text directly.
5. For `UNAVAILABLE`, the UI explains that Paperless is unavailable and only offers a reparse with the stored text or cancel.

Manual-edit and FULL_TEXT confirmations remain independent safeguards and continue to apply after the raw-text choice.

## Testing

- Mocked Paperless tests cover unchanged, changed, and unavailable source text.
- Reparse tests verify that only confirmed `PAPERLESS` input updates `raw_text` and parses that value.
- Contract tests protect endpoint security and the DTO response.
- Frontend mock and Selenium smoke tests cover the changed-text dialog and both choices.
