# Paperless eBon Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a resumable, Codex-assisted audit that verifies every tagged Paperless eBon, discovers merchant/branch/layout identities, improves quarantined parsing profiles, and validates product-family assignments without using OpenRouter in audit mode.

**Architecture:** Finish the existing adaptive-profile lifecycle first, then add a file-backed TypeScript audit runner that talks only to Paperless GET endpoints and protected eBon APIs. Local Tesseract/Poppler OCR is diagnostic-only; the runner persists hashes, counters, proposal IDs, and decisions under gitignored `var/ebon-audit/`, while backend APIs own validation, transactions, manual protection, idempotency receipts, profile quarantine, and product mutation.

**Tech Stack:** Java 25, Spring Boot 4.0.6, Spring Data JPA, PostgreSQL 18, Flyway, Maven, React 19, TypeScript 6.0.3, Node.js 24.16.0, Vite 8, Vitest 4.1.9, Docker Compose, Tesseract OCR (`deu+eng`), Poppler, YAML 2.8.1.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- `ebon-specification.md` remains the product contract; update it before introducing audit APIs, statuses, commands, or runtime settings.
- Audit Paperless access is GET-only. Complete pagination is mandatory before accepting an inventory snapshot.
- Paperless text remains primary. Local OCR is comparison evidence and never replaces `receipt.raw_text` automatically.
- Audit mode is interactive through Codex in the ChatGPT app and must make zero OpenRouter calls; live OpenRouter behavior remains separate.
- Codex is not embedded as an API or background service.
- The runner never connects directly to PostgreSQL. It uses protected eBon APIs for reads and writes.
- `var/ebon-audit/` is local and gitignored. No raw receipt text, OCR text, original file, prompt, model response, token, or secret may enter it or Git.
- Current profile truth is a fresh parse of Paperless content. Persisted positions, categories, products, and manual assignments are not parser reference truth.
- Every plausible line is classified; `UNRESOLVED` blocks automatic product work.
- Profiles are immutable and start in `QUARANTINE`; promotion requires three complete distinct receipts plus full-cluster regression, then five initial and every tenth shadow hit.
- Manual receipt/category/product corrections are never automatically overwritten.
- Direct product correction requires confidence `>= 0.98`, safe line type, duplicate similarity `< 0.85`, consistent unit/package/price evidence, and no branch conflict.
- AI-only audit assignments remain AI evidence and do not create trusted variant history. Markdown-confirmed decisions are manual confirmations.
- Size, weight, volume, and pack structure are variants, not separate families.
- Broad, global, regex, and `CONTAINS` rules require user confirmation.
- Every historical or bulk mutation is previewed, explicitly applied, transactional, idempotent, audited, and reversible.
- Tests and CI mock Paperless and OpenRouter. Real Paperless is used only during the explicitly started read-only pilot/full audit.
- Preserve unrelated changes and run the accumulated verification gate for every changed surface.

---

## Dependency Gate: Finish Existing Adaptive Processing

The deterministic foundation is complete at `a1ce32a`. Before audit-specific profile/product apply work starts, execute these existing plans in order:

1. `docs/superpowers/plans/2026-07-13-profile-lifecycle-bootstrap-rollback.md`
2. `docs/superpowers/plans/2026-07-13-adaptive-category-product-learning.md`
3. `docs/superpowers/plans/2026-07-13-adaptive-processing-ui-operations.md`

The audit plans consume the services and tables defined there: `ProfileParseComparator`, `FormatProfileEvidenceService`, `FormatProfilePromotionService`, `ProfileRollbackService`, `PaperlessProfileBootstrapService`, conservative product-family gates, and the learning/review UI. Do not recreate parallel lifecycle tables or a second profile engine.

## Audit Plan Set and Dependency Order

| Order | Plan | Deliverable | Depends on |
|---|---|---|---|
| 1 | [Runner State and Read-only Inventory](2026-08-29-ebon-audit-runner-inventory.md) | TypeScript CLI, atomic local state, locking, complete Paperless GET inventory, hashing, Compose job | Current repository |
| 2 | [OCR and Source Verification](2026-08-29-ebon-audit-ocr-verification.md) | Temporary local OCR, sanitized deterministic parse API, OCR gates and structural comparison | Audit Plan 1; adaptive foundation |
| 3 | [Merchant, Branch, Parser, and Profile Audit](2026-08-29-ebon-audit-parser-profiles.md) | Cluster work queue, Codex profile intake, quarantine/regression verification, profile status reporting | Audit Plans 1–2; adaptive lifecycle plan |
| 4 | [Product-Family and Assignment Audit](2026-08-29-ebon-audit-products.md) | Verified-item inventory, high-confidence gates, preview/apply/rollback receipts, Codex product work queue | Audit Plan 3; adaptive category/product plan |
| 5 | [Markdown Decisions and UI Handoff](2026-08-29-ebon-audit-markdown-ui.md) | Full `progress.md`, closed decision-block import, two-phase apply, deep links to eBon review UI | Audit Plans 3–4; adaptive UI plan |
| 6 | [Pilot, Full Audit, and Acceptance](2026-08-29-ebon-audit-rollout.md) | Safe pilot, full Codex-assisted corpus audit, verified backup/rollback/idempotent rerun | Audit Plans 1–5 |

Do not begin a dependent plan until its predecessor's focused tests and completion gate pass. Each child plan is independently reviewable and uses its own commits.

## Requirement Traceability

| Requirement | Plan coverage |
|---|---|
| Complete tagged Paperless inventory with incremental hashes | Runner Tasks 2–4 |
| GET-only Paperless and incomplete-pagination rejection | Runner Tasks 3–5 |
| Local PDF/image OCR without persistence or replacement | OCR Tasks 1, 3, and 4 |
| Every merchant, branch, and fingerprint reported | Parser/Profile Tasks 1 and 4 |
| Fresh parser/profile/Legacy comparison | OCR Task 2; Parser/Profile Tasks 1–3 |
| Quarantine, three-receipt/full-cluster promotion, monitoring, rollback | Existing lifecycle plan; Parser/Profile Tasks 2–4 |
| Product audit only after parse verification | Product Tasks 1 and 3 |
| `>= 0.98` direct product gates and `< 0.85` similarity gate | Product Task 2 |
| Medium-confidence block decisions and low-confidence UI handoff | Markdown/UI Tasks 1–4 |
| File state, atomic resume, locking, no raw private data | Runner Tasks 1–2; OCR Task 4; Markdown/UI Task 1 |
| Codex-only audit and zero OpenRouter calls | OCR Task 2; Parser/Profile Task 2; Rollout Tasks 1 and 4 |
| Backup, preview/apply, idempotency, rollback, manual protection | Product Tasks 2 and 4; Markdown/UI Task 3; Rollout Tasks 2–4 |
| Pilot then complete audit and unchanged rerun | Rollout Tasks 2–4 |

## Milestone Gates

### Milestone A: Adaptive prerequisites

- [ ] Complete lifecycle, downstream learning, and adaptive UI plans.
- [ ] Run backend, frontend, Compose, backup/restore, and runtime gates from those plans.
- [ ] Confirm no private Paperless content was committed.

### Milestone B: Read-only audit foundation

- [ ] Complete Audit Plans 1–2.
- [ ] Prove invalid pagination never replaces the prior inventory.
- [ ] Prove original/OCR files are deleted on success, failure, and interruption cleanup.
- [ ] Prove deterministic audit parse endpoints do not wire or call OpenRouter.

### Milestone C: Parser and product decisions

- [ ] Complete Audit Plans 3–4.
- [ ] Prove all profile candidates enter quarantine and use existing evidence/promotion services.
- [ ] Prove direct AI product assignments remain untrusted AI evidence.
- [ ] Prove manual assignments and unresolved parse items cannot be changed automatically.

### Milestone D: Human workflow and full audit

- [ ] Complete Audit Plans 5–6.
- [ ] Prove Markdown imports reject stale, duplicate, unknown, or malformed blocks atomically.
- [ ] Complete the live read-only inventory and pilot before any full apply.
- [ ] Complete the full corpus audit with progress tracked locally.
- [ ] Run an unchanged second audit with zero mutations, OCR work, Codex work, and OpenRouter calls.

## Commit Strategy

Use the commit boundaries in each child plan. Never combine runner scaffolding, backend audit APIs, OCR, profile lifecycle, product mutation, Markdown import, frontend handoff, and live operational evidence in one commit. Generated `var/ebon-audit/` files and private receipts are never staged.

## Final Completion Gate

- [ ] `cd backend && mvn verify`
- [ ] `cd frontend && npm test && npm run build`
- [ ] `cd audit-runner && npm test && npm run build`
- [ ] `docker compose config`
- [ ] `docker compose build audit backend frontend`
- [ ] Focused mocked Audit E2E passes with zero OpenRouter requests.
- [ ] Real Paperless audit request log contains GET only.
- [ ] Backup validation and one controlled rollback pass.
- [ ] `git diff --check` is clean.
- [ ] `git status --short` contains no private/generated audit files.
- [ ] `progress.md` accounts for every current tagged Paperless document, cluster, verified position, and unresolved decision.
