# eBon QA Skill

Use this skill before finishing work, when reviewing changes, or when adding tests and acceptance criteria.

## Read First

- `ebon-specification.md` sections 12, 13, 14, 16, 17.
- `AGENTS.md`.

## Quality Priorities

- Correctness beats feature breadth.
- Parser and sync behavior must be deterministic.
- External services must be mocked in tests.
- Data-loss risks must be tested before UI polish.
- Secrets must never leak through logs, API responses, backups, screenshots, fixtures, or errors.
- OpenRouter KI parsing must be tested only with mocks/test doubles; no real KI calls in automated tests or CI.
- Prompt and raw response data must be absent by default from logs, backups, API responses, screenshots, and fixtures.

## Required Checks by Change Type

Spec/docs only:

```bash
git diff --check
```

Backend:

```bash
cd backend
mvn verify
```

Frontend:

```bash
cd frontend
npm run build
```

Docker/devcontainer:

```bash
docker compose config
```

Full app, when scaffolded:

```bash
docker compose up --build
```

## Review Checklist

- Does the change match `ebon-specification.md`?
- Are DTOs and OpenAPI updated together?
- Are migrations included for schema changes?
- Are manually edited receipt items protected from unintended overwrite?
- Is `TAG_REMOVED` safe against partial Paperless failures?
- Are category deactivate/delete semantics correct?
- Are uncategorized items represented as `category_id = NULL` and `category_source = NULL`, without a fake category or source badge?
- Are Paperless document links built from a public URL/template and free of tokens or secrets?
- Do dashboard labels and filters make "Letzte Bons", "Bonus", and "Ohne Kategorie" unambiguous?
- Are data-maintenance reset operations transactional, explicitly confirmed, and limited to imported receipt data while keeping categories, rules, settings, backups, and Flyway history?
- Are backup/restore paths transactional and lock writes?
- Are all new secrets masked?
- Does KI parsing adoption require valid schema, required fields, contiguous item indexes, sum tolerance, and minimum confidence?
- Are `receipt.parse_source`, `ai_parsing_log`, and `parse_rule_suggestion` updated consistently?
- Is automatic `parse_rule` creation blocked until a user accepts a parser rule suggestion?
- Does automatic sync respect `ai_parsing_sync_call_limit` and leave clear `PARSE_ERROR` reasons when skipped?
- Does `FULL_TEXT` KI parsing require explicit confirmation in manual reparse flows?
- Are parser rule suggestions validated, editable, acceptable, rejectable, and exportable as migration drafts?
- Do backup/restore include `ai_parsing_log` and `parse_rule_suggestion` without full prompts/raw responses?
- Do UI tests cover the "per KI geparst" badge, KI parsing log display, parser suggestion workflow, and FULL_TEXT confirmation?
- Are tests focused on behavior rather than implementation trivia?
