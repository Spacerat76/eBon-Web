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
- Are tests focused on behavior rather than implementation trivia?
