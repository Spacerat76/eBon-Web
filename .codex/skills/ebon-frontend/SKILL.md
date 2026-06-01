# eBon Frontend Skill

Use this skill for React, TypeScript, Vite, shadcn/ui, Tailwind CSS, dashboards, forms, tables, reports, and frontend integration work.

## Read First

- `ebon-specification.md` sections 8, 9, 14, 16, 17.
- `AGENTS.md`.

## Product Rules

- The app is a utilitarian single-user expense tool, not a marketing site.
- First screen after load is the usable dashboard.
- UI language is German.
- Desktop and tablet are primary; mobile must remain usable.
- Dark mode and light mode follow the system setting.

## API Rules

- Use the DTO contracts from specification section 8.4.
- Include `Authorization: Bearer <APP_API_TOKEN>` on protected API calls.
- Treat `GET /api/health` as public.
- Never persist `"********"` as a secret value.
- Keep generated or handwritten TypeScript API types aligned with backend DTOs.

## UI Rules

- Use dense, scannable layouts for operational work.
- Use tables for receipt lists, receipt items, rules, categories, and reports.
- Use badges for parse status and category source.
- Use inline validation for forms.
- Use skeletons or spinners for async states.
- Use toast notifications for success and error feedback.
- Do not expose raw stack traces or secrets in UI errors.

## Core Flows

- Dashboard summary and sync status.
- Receipt list and receipt detail/editing.
- Search with filters and pagination.
- Reports with charts, table, and CSV export.
- Settings for Paperless, OpenRouter, sync interval, currency.
- Category and rule management.
- Backup download, validate, and restore confirmation.

## Verification

```bash
cd frontend
npm run build
```

When E2E tests exist, run the focused flow touched by the change.

