# eBon Audit Pilot, Full Run, and Acceptance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prove the audit safely on mocks and representative live clusters, then complete the full Codex-assisted Paperless corpus audit with verified backup, progress, rollback, privacy, and idempotent rerun evidence.

**Architecture:** A preflight command verifies configuration, connectivity, GET-only Paperless behavior, state privacy, backend compatibility, and zero OpenRouter audit paths without mutations. A mocked E2E exercises every route before a live read-only inventory and representative pilot; high-confidence apply is unlocked only after a validated eBon backup and pilot rollback. The full run remains interactive and checkpoints after every document/cluster/decision block.

**Tech Stack:** Docker Compose, audit-runner CLI, Java/Spring Boot backend, React frontend, WireMock/Testcontainers, Vitest, Selenium, local Tesseract/Poppler, Codex in ChatGPT app.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- All preceding audit plans and adaptive prerequisite plans must be complete and verified.
- Live Paperless access stays GET-only. Inspect actual method evidence before continuing from inventory to pilot.
- The pilot/full audit is interactive with Codex; do not install or call a Codex API and do not call OpenRouter from the audit path.
- Do not write raw receipt/OCR text, originals, prompts, model responses, or tokens to progress, logs, screenshots, plan evidence, Git, or test artifacts.
- A local eBon backup contains private data and stays under existing gitignored `backups/`; never attach or commit it.
- No mutation occurs during preflight, inventory, source verification, parser comparison, or proposal generation.
- Direct product apply is an explicitly started phase after backup and preview. Profile candidates still enter quarantine.
- Stop at any unexpected count drop, pagination failure, manual-protection conflict, relevant OCR mismatch, changed preview, OpenRouter request, or private-output finding.

---

### Task 1: Add a read-only preflight and operational runbook

**Files:**
- Create: `audit-runner/src/preflight/preflight-service.ts`
- Create: `audit-runner/src/preflight/preflight-result.ts`
- Test: `audit-runner/src/preflight/preflight-service.test.ts`
- Modify: `audit-runner/src/main.ts`
- Create: `docs/audit-runbook.md`
- Modify: `README.md`

**Interfaces:**
- Adds CLI: `preflight`.
- Produces: `PreflightResult` containing sanitized status codes and version/count metadata only.
- Consumes: Paperless first-page GET, eBon health/system/audit-capability endpoints, OCR binary version checks, local workdir permissions, and state schema validation.

- [ ] **Step 1: Write failing preflight mutation/OpenRouter tests**

```ts
it("uses no mutation endpoint and no OpenRouter origin", async () => {
  const result = await service.run();
  expect(result.ready).toBe(true);
  expect(http.requests).toSatisfyAll(request => request.method === "GET");
  expect(http.requests.map(request => request.url)).not.toContainEqual(expect.stringMatching(/openrouter/i));
});
```

Also test invalid workdir permissions, stale lock, unsupported state schema, backend capability mismatch, missing OCR language, failed Paperless authentication, and sanitized errors.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- preflight-service.test.ts`

- [ ] **Step 3: Implement fail-closed preflight**

Check exact backend API contract version, app/Paperless origins, `GET` capability, state directory mode, free disk floor, Tesseract `deu+eng`, Poppler, limits, current lock, and absence of OpenRouter fields in effective audit configuration. Do not download originals or enumerate the full corpus during preflight.

- [ ] **Step 4: Write the German runbook**

Document exact commands and stop conditions:

```bash
docker compose --profile audit run --rm audit preflight
docker compose --profile audit run --rm audit inventory
docker compose --profile audit run --rm audit verify-sources
docker compose --profile audit run --rm audit verify-profiles
docker compose --profile audit run --rm audit report
docker compose --profile audit run --rm audit status
```

Document Codex work-item handling, Paperless original inspection, backup/validation through “Einstellungen → Backup & Restore”, direct apply confirmation, one-block Markdown preview/apply, UI handoff, pause/resume, stale lock recovery, rollback, and privacy cleanup.

- [ ] **Step 5: Run tests and commit**

Run: `cd audit-runner && npm test -- preflight-service.test.ts && npm run build`

```bash
git add audit-runner/src/preflight audit-runner/src/main.ts docs/audit-runbook.md README.md
git commit -m "docs(audit): add safe preflight and runbook"
```

### Task 2: Build a fully mocked audit E2E and rollback drill

**Files:**
- Create: `audit-runner/src/e2e/full-audit-flow.test.ts`
- Create: `audit-runner/src/e2e/fixtures/paperless-pages.ts`
- Create: `audit-runner/src/e2e/fixtures/ebon-responses.ts`
- Create: `audit-runner/src/e2e/fixtures/synthetic-receipt.pdf`
- Create: `backend/src/test/java/de/ebon/audit/AuditFullFlowIntegrationTests.java`
- Modify: `frontend/e2e/run-smoke.mjs`

**Interfaces:**
- Produces: one synthetic end-to-end run covering inventory, OCR, profiles, products, Markdown, UI, apply, rollback, interruption, and unchanged rerun.
- Uses only synthetic receipt names/text and mock external servers.

- [ ] **Step 1: Write the failing scenario assertions**

The fixture set contains:

1. three same-cluster complete receipts;
2. a fourth layout mismatch;
3. one OCR-confirmed scan;
4. one relevant OCR difference;
5. one high-confidence product proposal;
6. one medium-confidence Markdown proposal;
7. one UI conflict;
8. one manual-protected item;
9. one missing local receipt;
10. one recoverable interruption after checkpoint.

Assert exact document/cluster/item counts, zero Paperless non-GET requests, zero OpenRouter requests, no raw private fixture markers in state/report/history, profile quarantine/promotion boundaries, one direct product apply, one Markdown manual apply, one rollback, and zero work on unchanged rerun.

- [ ] **Step 2: Run the scenario and verify failure**

Run:

```bash
cd backend && mvn -Dtest=AuditFullFlowIntegrationTests test
cd ../audit-runner && npm test -- full-audit-flow.test.ts
```

- [ ] **Step 3: Add only missing integration glue demonstrated by the scenario**

Return to the responsible prior plan for behavioral defects. This task may add test harness/configuration code but must not bypass a failed gate or weaken assertions.

- [ ] **Step 4: Add browser smoke for all UI handoffs**

Run the mocked frontend, open product/receipt/profile links generated by the test state, apply one user-confirmed block through the UI/API, and verify German success/error states and unsaved-change protection.

- [ ] **Step 5: Run the complete mocked gate**

```bash
cd backend && mvn verify
cd ../frontend && npm test && npm run build && npm run e2e
cd ../audit-runner && npm test && npm run build
cd .. && docker compose config
docker compose --profile audit build audit backend frontend
git diff --check
```

- [ ] **Step 6: Commit mocked E2E evidence**

```bash
git add backend/src/test/java/de/ebon/audit audit-runner/src/e2e frontend/e2e/run-smoke.mjs
git commit -m "test(audit): cover complete mocked audit flow"
```

### Task 3: Execute the live read-only inventory and representative pilot

**Files:**
- Local only, never commit: `var/ebon-audit/audit-state.json`
- Local only, never commit: `var/ebon-audit/progress.md`
- Local only, never commit: `var/ebon-audit/decision-history.jsonl`
- Modify after sanitized review: `docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md`

**Interfaces:**
- Produces: accepted complete Paperless snapshot and verified representative pilot clusters.
- Requires: explicit user authorization already granted for read-only Paperless/original access.

- [ ] **Step 1: Capture the fresh preflight and repository state**

Run:

```bash
git status --short
git diff --check
docker compose ps
docker compose --profile audit run --rm audit preflight
```

Expected: only known source changes; services healthy; preflight reports ready and OpenRouter audit calls `0`.

- [ ] **Step 2: Run complete inventory without mutations**

```bash
docker compose --profile audit run --rm audit inventory
docker compose --profile audit run --rm audit status
docker compose --profile audit run --rm audit report
```

Inspect Paperless request evidence. Continue only if every request is GET, all pagination pages completed, the count is plausible relative to Paperless, and `progress.md` contains the same document total as `audit-state.json`.

- [ ] **Step 3: Select pilot clusters from actual sanitized counts**

Select the largest merchant, a multi-branch merchant, an OCR-triggered scan/photo cluster, multiple layouts for one merchant, a cluster with fewer than three receipts, and a cluster with unresolved/merged lines. Record only counts and opaque fingerprint prefixes in the committed milestone; exact branch/document IDs remain local.

- [ ] **Step 4: Run source and parser/profile verification interactively**

```bash
docker compose --profile audit run --rm audit verify-sources --pilot
docker compose --profile audit run --rm audit verify-profiles --pilot
docker compose --profile audit run --rm audit report
```

Codex handles one generated work item at a time and may open the listed Paperless original read-only. Candidate profiles are previewed then applied only to quarantine. Stop on any raw-text persistence, unexplained count change, OCR replacement attempt, or OpenRouter request.

- [ ] **Step 5: Create and validate the eBon backup before product apply**

Use the existing Backup & Restore UI/API to download a backup into gitignored `backups/`. Immediately upload it to `/api/backup/validate` and require `valid=true` with every expected table including `audit_operation_receipt`. Do not continue with an unvalidated backup.

- [ ] **Step 6: Run pilot product routes and rollback one block**

Generate product work, submit Codex proposals, preview/apply the explicit direct batch, process one medium block through Markdown, send conflicts to UI, and roll back one selected test operation. Re-read affected receipt items and confirm manual-protected items are unchanged.

- [ ] **Step 7: Record sanitized pilot evidence and commit**

Update the master plan with counts only: documents, merchants, branches, clusters, OCR statuses, profile states, product routes, manual-protection checks, backup validation, rollback result, and OpenRouter audit calls `0`.

```bash
git add docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md
git commit -m "docs(audit): record verified live pilot"
```

### Task 4: Complete the full Codex-assisted corpus audit

**Files:**
- Local only: `var/ebon-audit/*`
- Modify after sanitized completion: `docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md`

**Interfaces:**
- Produces: complete audit status for every current tagged Paperless eBon and every verified position.

- [ ] **Step 1: Process all remaining source/parser clusters**

Run/resume `verify-sources`, `verify-profiles`, and `report`. Codex processes cluster work items until every current document is `VERIFIED`, `VERIFIED_WITH_OCR`, `OCR_DIFFERENCE`, `PARSE_REVIEW`, or `SOURCE_UNAVAILABLE`, and every cluster has a profile lifecycle status.

- [ ] **Step 2: Process every eligible product position**

For verified documents only, process all remaining product work items. Apply direct `>=0.98` proposals in explicitly confirmed bounded batches, edit/confirm medium blocks one at a time, and route low/conflicting proposals to UI. Leave parser-open items without product assignment.

- [ ] **Step 3: Reconcile progress before completion**

Require these equalities/invariants:

```text
inventory documents = sum(all source verification statuses)
clusters = sum(all profile lifecycle statuses)
eligible verified positions = direct + markdown + ui + protected + open-explicit
manual protected before = manual protected after
OpenRouter audit calls = 0
Paperless non-GET calls = 0
```

- [ ] **Step 4: Run the unchanged idempotency audit**

Repeat preflight, inventory, source verification, profile verification, product work generation, and report without changing inputs. Expected: zero changed documents, zero OCR pages, zero Codex work items, zero profile/product mutations, zero new decision-history lines, and unchanged report counters apart from run/check timestamps.

- [ ] **Step 5: Run final software gates**

```bash
cd backend && mvn verify
cd ../frontend && npm test && npm run build && npm run e2e
cd ../audit-runner && npm test && npm run build
cd .. && docker compose config
docker compose --profile audit build audit backend frontend
git diff --check
git status --short
```

Inspect all output. Do not claim completion for a skipped or failed gate.

- [ ] **Step 6: Record sanitized final evidence and commit**

Update the master plan with exact test counts, sanitized corpus counts/statuses, remaining explicit review counts, backup/rollback/idempotency evidence, Git status, and known limitations. Never commit `var/ebon-audit/` or the backup.

```bash
git add docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md docs/audit-runbook.md
git commit -m "docs(audit): record completed corpus verification"
```
