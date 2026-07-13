# Adaptive Receipt Processing Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a deterministic, self-learning receipt-processing pipeline with versioned store/branch format profiles, controlled AI promotion and rollback, conservative category learning, and high-confidence product-family creation.

**Architecture:** Work is split into four independently reviewable plans. The parser foundation establishes immutable profile definitions and line traces; lifecycle work adds AI proposals, read-only Paperless bootstrap, promotion, monitoring, and rollback; downstream learning adds category and product-family adaptation; the final plan exposes review and operational workflows and closes backup, reset, metrics, and E2E requirements.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, PostgreSQL 16, Flyway, Jackson, Maven, JUnit 5, Mockito, Testcontainers, React, TypeScript, Vite, Vitest, Testing Library, Selenium, Docker Compose.

## Global Constraints

- `ebon-specification.md` remains the canonical product contract and must be updated with every API, schema, status, or behavior change.
- Devcontainer and Docker Compose are the supported runtime; do not assume host Java, Maven, Node.js, or PostgreSQL.
- Paperless access for profile bootstrap is strictly read-only and may use only the existing `GET` document operations.
- Automatically promoted format profiles are versioned `receipt_format_profile` records, not `parse_rule` rows; existing AI `parse_rule_suggestion` acceptance rules remain unchanged.
- Shadow AI validates an already accepted profile result and never replaces it unless the normal rule/profile result has failed the parsing contract.
- Bootstrap baselines must re-run the Legacy parser from Paperless raw text; persisted receipt items, manual corrections, categories, and product assignments are forbidden as comparison truth.
- Legacy-unrecognized position-like lines remain `UNRESOLVED`; do not infer them from manual data.
- Tests must never call real Paperless-NGX or OpenRouter; use mocks or test doubles.
- A valid AI parse still requires schema validation, required fields, contiguous indices, sum tolerance `0.02`, and configured minimum confidence.
- Full prompts and raw AI responses are not persisted by default; no tokens, private receipts, or unmasked secrets enter fixtures, logs, backups, or commits.
- `PARSE_REVIEW` gates category and product automation to `CONFIRMED` items only.
- Manual receipt, item, category, and product decisions are never silently overwritten.
- Automatic parser-profile promotion requires three distinct complete receipts; post-promotion checks cover the first five hits and every tenth hit afterward.
- Automatic category rules are store-specific `NORMALIZED_EXACT` only; broad rules require user confirmation.
- New product-family creation requires confidence `>= 0.98`, deterministic duplicate checks, and safe variant separation.
- Backend verification: `cd backend && mvn verify`.
- Frontend verification: `cd frontend && npm run build`.
- Docker verification: `docker compose config` and, at final integration, `docker compose up -d --build` plus smoke tests.

---

## Plan Set and Dependency Order

| Order | Plan | Deliverable | Depends on |
|---|---|---|---|
| 1 | [Adaptive Parsing Foundation](2026-07-13-adaptive-parsing-foundation.md) | Profile schema/interpreter, fingerprints, line traces, `PARSE_REVIEW`, Legacy partial-rule fix | Current repository |
| 2 | [Profile Lifecycle, Bootstrap, and Rollback](2026-07-13-profile-lifecycle-bootstrap-rollback.md) | AI profile proposals, evidence, promotion, monitoring, rollback, initial read-only Paperless generation | Plan 1 |
| 3 | [Adaptive Category and Product Learning](2026-07-13-adaptive-category-product-learning.md) | Conservative category evidence and high-confidence product-family creation | Plan 1; consumes Plan 2 job/audit patterns |
| 4 | [Review UI, Operations, and Hardening](2026-07-13-adaptive-processing-ui-operations.md) | Review workflows, bootstrap UI, metrics, backup/reset, E2E, full-system rollout | Plans 1–3 |

The plans are separate because parser execution, lifecycle orchestration, downstream semantic learning, and UI/operations can each be reviewed and released independently. Do not begin a later plan until the dependency plan's full verification gate is green.

## Requirement Traceability

| Requirement | Implemented and verified in |
|---|---|
| Store/optional branch identity plus stable layout fingerprint | Foundation Tasks 2 and 5 |
| Declarative versioned profiles and complete line classification | Foundation Tasks 1, 3, and 4 |
| Preserve partial Legacy results and keep Legacy-unknown positions open | Foundation Task 5; Lifecycle Tasks 6–8 |
| AI takes over the current failed receipt and proposes a quarantined profile | Lifecycle Tasks 3 and 8 |
| Three distinct complete receipts promote without regressions | Lifecycle Tasks 2 and 4 |
| First five production hits and every tenth hit use shadow AI | Lifecycle Task 5 |
| Mismatch suspends immediately, rolls back the affected window, and protects manual work | Lifecycle Task 5 |
| Initial profiles are generated from read-only Paperless data and fresh Legacy parses | Lifecycle Tasks 6–8 |
| Manual category correction learns only after explicit confirmation | Category/Product Task 3; UI Task 4 |
| AI category learning needs three conflict-free receipts and only creates store-specific normalized-exact rules | Category/Product Tasks 1 and 2 |
| High-confidence (`>= 0.98`) product-family creation with duplicate, line-type, and variant gates | Category/Product Tasks 4 and 5 |
| Review UI, kill switches, auditability, backup/reset, metrics, and E2E | UI/Operations Tasks 1–8 |

## Milestone Gates

### Milestone 1: Deterministic foundation

- [ ] Complete every task in `2026-07-13-adaptive-parsing-foundation.md`.
- [ ] Confirm the Legacy parser still passes the existing corpus.
- [ ] Confirm accepted dynamic item rules supplement partial parses instead of only zero-item parses.
- [ ] Confirm no real external calls occur.
- [ ] Commit only after `mvn verify` and `npm run build` pass for changed surfaces.

### Milestone 2: Controlled learning and initial profiles

- [ ] Complete every task in `2026-07-13-profile-lifecycle-bootstrap-rollback.md`.
- [ ] Run mocked bootstrap tests before any live read.
- [ ] Run the live Paperless bootstrap preview and inspect only sanitized cluster/diff output.
- [ ] Apply the initial bootstrap after preview; Paperless remains read-only while local profile/evidence tables are written.
- [ ] Export no raw receipt text and commit no generated private fixture.
- [ ] Record counts by profile state (`ACTIVE`, `QUARANTINE`, `SUSPENDED`) without private text.

### Milestone 3: Downstream learning

- [ ] Complete every task in `2026-07-13-adaptive-category-product-learning.md`.
- [ ] Confirm unresolved or `NEEDS_REVIEW` extracted items create no category/product learning evidence.
- [ ] Confirm AI-only product rules never become trusted variant history.
- [ ] Confirm existing and manually selected categories are preserved.

### Milestone 4: User workflows and operational safety

- [ ] Complete every task in `2026-07-13-adaptive-processing-ui-operations.md`.
- [ ] Verify backup/restore and imported-receipt reset semantics for all new tables.
- [ ] Run frontend build, backend verify, Docker config, Docker rebuild, and Selenium smoke tests.
- [ ] Run `git diff --check` and inspect `git status --short` before final handoff.

## Commit Strategy

Use the commit boundaries defined inside each child plan. Do not combine migrations, parser behavior, category learning, product-family creation, and UI into one commit. Each commit must have a focused failing test first and a passing narrow verification before moving on.

## Final Acceptance Audit

- [ ] Every requirement in `docs/superpowers/specs/2026-07-13-adaptive-receipt-processing-design.md` maps to a completed task in one child plan.
- [ ] `ebon-specification.md`, migrations, JPA models, DTOs, OpenAPI annotations, frontend types, and UI labels agree.
- [ ] Live Paperless bootstrap used only GET requests and fresh Legacy parses.
- [ ] Manual assignments were not read as bootstrap truth.
- [ ] Legacy-unrecognized positions remain open and visible, not invented.
- [ ] Initial profiles exist for observed merchant/format clusters; incomplete clusters remain in quarantine.
- [ ] Promotion, five-hit monitoring, tenth-hit sampling, suspension, and bounded rollback are verified.
- [ ] Category and product learning is reversible and auditable.
- [ ] No secret or private receipt content appears in git history or generated reports.
