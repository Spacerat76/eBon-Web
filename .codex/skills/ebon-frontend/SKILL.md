---
name: ebon-frontend
description: Use when changing eBon React, TypeScript, Vite, routing, API clients, German operational UI, forms, tables, dashboards, reports, review queues, settings, or frontend tests.
---

# eBon Frontend

## Product Experience

Build a utilitarian single-user expense tool, not a marketing site. The UI language is Deutsch. Show the usable dashboard first. Optimize dense operational work for desktop/tablet while keeping mobile usable; follow the system color theme.

Read only the affected UI/API/acceptance sections of `ebon-specification.md`. Use `ebon-adaptive-processing` for profile/learning review screens.

## API and Secrets

- Keep TypeScript types aligned with backend DTOs, validation, and OpenAPI.
- Send app Bearer auth only to protected app APIs. Prefer the Vite `/api` proxy.
- Never hardcode backend URLs, Paperless/OpenRouter credentials, or tokens.
- Never persist the masked value `********` as a secret update.
- Use backend `paperlessDocumentUrl`; if absent, show the document ID rather than constructing a URL.
- Render safe user-facing errors, never stack traces, prompts, raw model responses, or secrets.

## Interaction Rules

- Use scannable tables for operational collections, compact badges for meaningful state, inline form validation, visible loading state, and toast feedback.
- Represent "Ohne Kategorie" only when `categoryId = null` and `categorySource = null`; show no RULE/AI/MANUAL source badge.
- Show `parseSource = AI` clearly and keep AI logs prompt-free.
- Keep save/cancel reachable on long edits and preserve manual values during refresh/reparse flows.
- Require explicit confirmation before `FULL_TEXT` AI reparse.
- Show a preview and confirmation before merge, split, bulk reassignment, retroactive application, restore, or reset.
- Distinguish product family from variant and expose size/unit/package differences. Make effective versus regular price and reversible outlier exclusion explicit.
- For parser/profile suggestions, show trigger, problem, rationale, validation/evidence status, scope, and affected receipts without private raw text.

## Verification

Add focused component/API tests for changed success, loading, empty, validation, and error states. Run the affected E2E flow when present, then:

```bash
cd frontend
npm run build
```

Use `ebon-qa` before completion.
