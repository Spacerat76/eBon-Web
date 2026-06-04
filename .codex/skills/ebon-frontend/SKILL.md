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
- Prefer the Vite proxy for `/api` in development so the frontend does not require ad hoc backend CORS.
- Do not hardcode backend URLs, API tokens, Paperless tokens, or OpenRouter keys in frontend source.
- Use `paperlessDocumentUrl` from the backend for Paperless links. If absent, show the Paperless ID as text rather than building a secret-bearing URL in the browser.
- Keep generated or handwritten TypeScript API types aligned with backend DTOs.

## UI Rules

- Use dense, scannable layouts for operational work.
- Use tables for receipt lists, receipt items, rules, categories, and reports.
- Use badges for parse status and category source.
- Use inline validation for forms.
- Use skeletons or spinners for async states.
- Use toast notifications for success and error feedback.
- Do not expose raw stack traces or secrets in UI errors.
- "Ohne Kategorie" is a UI state for items with `categoryId = null` and `categorySource = null`; do not show RULE/AI/MANUAL badges for it.
- Keep save/cancel actions reachable while editing long receipts, for example with a sticky action bar.
- Dashboard wording must be explicit: "Letzte Bons" is quick navigation to recent receipts; "Bonus" means newly earned points/balance for the selected period.
- Dashboard category and bonus widgets should support month, last quarter, last year, and custom date-range filters when the phase requires final reports/settings work.

## Core Flows

- Dashboard summary and sync status.
- Receipt list and receipt detail/editing.
- Receipt links to Paperless documents when `paperlessDocumentUrl` is available.
- Uncategorized work queue via `uncategorizedOnly=true`.
- Search with filters and pagination.
- Reports with charts, table, and CSV export.
- Settings for Paperless, OpenRouter, sync interval, currency.
- Settings for Paperless public URL/document URL template and AI categorization confidence.
- Data maintenance settings: re-parse all receipts and reset imported receipt data with explicit confirmation.
- Category and rule management.
- Backup download, validate, and restore confirmation.

## Verification

```bash
cd frontend
npm run build
```

When E2E tests exist, run the focused flow touched by the change.
