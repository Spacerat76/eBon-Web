# Adaptive Processing UI, Operations, and Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make adaptive parsing understandable and operable through secured APIs and UI, add trace correction workflows and kill switches, preserve all new data through backup/reset semantics, and verify the complete system.

**Architecture:** Add one consolidated backend query/service boundary for profile lifecycle, jobs, metrics, and trace resolution. Expose it through a dedicated “Lernende Verarbeitung” page and compact receipt-detail review components. Extend existing backup/reset registries rather than creating a parallel export path.

**Tech Stack:** Spring Boot, JPA, OpenAPI, React, TypeScript, Vite, shadcn/ui, Vitest, Testing Library, Selenium, Docker Compose.

## Global Constraints

- Complete parsing foundation, lifecycle/bootstrap, and category/product learning plans first.
- UI text is German and must distinguish parsing, category, and product learning.
- Full prompts, raw AI responses, tokens, and unnecessary receipt raw text must not be returned by learning APIs.
- Bootstrap preview must be shown before Apply and Apply requires exact confirmation.
- Trace correction creates a new `USER_CORRECTED` profile version in quarantine; it never mutates an active profile.
- Backup/restore includes new master/audit data with existing secret masking.
- Imported-receipt reset keeps profiles, categories, rules, product families, and product rules while deleting receipt-bound traces/evidence/jobs.

---

### Task 1: Expose profile lifecycle, jobs, and metrics APIs

**Files:**
- Create: `backend/src/main/java/de/ebon/api/dto/FormatProfileSummaryDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/FormatProfileDetailDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/ProfileEvidenceDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AdaptiveJobDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AdaptiveProcessingMetricsDto.java`
- Create: `backend/src/main/java/de/ebon/api/service/AdaptiveProcessingApiService.java`
- Modify: `backend/src/main/java/de/ebon/api/AdaptiveParsingController.java`
- Test: `backend/src/test/java/de/ebon/api/AdaptiveParsingApiContractTests.java`

**Interfaces:**
- Produces: paged `GET /api/parser/profiles?state=&store=&scope=&page=&size=`.
- Produces: `GET /api/parser/profiles/{id}`, `/api/parser/profiles/{id}/evidence`, `/api/parser/jobs`, and `/api/parser/metrics`.
- DTOs expose structured diffs and counts, not profile prompts/raw AI responses.

- [ ] **Step 1: Write failing API tests**

Assert auth, filters, pagination, detail/evidence ordering, pending job counts, metric totals, and absence of secret/raw-response fields.

```java
mockMvc.perform(get("/api/parser/profiles").header(AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.content[0].state").value("QUARANTINE"));
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=AdaptiveParsingApiContractTests test`

Expected: list/detail/job/metrics endpoints are missing.

- [ ] **Step 3: Implement read-only API mapping**

Use repository projections for counts and page queries. Include identity, scope, fingerprint prefix, version, progress, monitoring counters, timestamps, state, and sanitized reason/diff fields.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=AdaptiveParsingApiContractTests test`

Expected: secured filtered contracts pass and response JSON contains no sensitive fields.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/api/AdaptiveParsingApiContractTests.java
git commit -m "feat(api): expose adaptive processing status"
```

### Task 2: Resolve parse-trace lines into a corrected quarantine profile

**Files:**
- Create: `backend/src/main/java/de/ebon/api/dto/ParseTraceResolutionRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/ParseTraceResolutionResultDto.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ParseTraceResolutionService.java`
- Modify: `backend/src/main/java/de/ebon/api/ReceiptsController.java`
- Modify: `backend/src/main/java/de/ebon/api/service/ReceiptApiService.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ParseTraceResolutionServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/ReceiptApiContractTests.java`

**Interfaces:**
- Produces: `POST /api/receipts/{receiptId}/parse-trace/{lineNumber}/resolve`.
- Request action enum: `CREATE_ITEM`, `MERGE_PREVIOUS`, `MERGE_NEXT`, `METADATA`, `PAYMENT`, `TOTAL`, `TAX`, `IGNORE_SAFE`.
- Produces: corrected receipt result plus new `USER_CORRECTED` profile version in `QUARANTINE`.

- [ ] **Step 1: Write failing resolution tests**

Cover all actions, required item fields, illegal merge boundary, manual-edit protection, no active-profile mutation, reindexing, sum validation, and new-profile creation.

```java
ParseTraceResolutionResult result = service.resolve(receiptId, lineNumber,
        new ParseTraceResolutionRequest(Action.CREATE_ITEM, "Artikel", BigDecimal.ONE, "Stk", null, new BigDecimal("2.99")));
assertThat(result.profile().getState()).isEqualTo(FormatProfileState.QUARANTINE);
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ParseTraceResolutionServiceTests,ReceiptApiContractTests test`

Expected: resolution service/endpoint is absent.

- [ ] **Step 3: Implement transactional correction and new-version generation**

Lock the receipt and active profile, apply the user action to a copy of the profile definition, validate and interpret the whole receipt, then persist a new version with `source=USER_CORRECTED`. Persist corrected receipt/items only after the replacement result validates; retain manual category/product assignments through existing transfer services.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ParseTraceResolutionServiceTests,ReceiptApiContractTests test`

Expected: every action, validation failure, manual protection, and profile-version assertion passes.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/de/ebon/api backend/src/main/java/de/ebon/parser/profile/ParseTraceResolutionService.java backend/src/test/java/de/ebon/parser/profile/ParseTraceResolutionServiceTests.java backend/src/test/java/de/ebon/api/ReceiptApiContractTests.java
git commit -m "feat(parser): resolve uncertain receipt lines"
```

### Task 3: Add frontend contracts and the learning overview page

**Files:**
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/mock-api.ts`
- Create: `frontend/src/pages/adaptive-processing-page.tsx`
- Create: `frontend/src/pages/adaptive-processing-page.test.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/components/app-shell.tsx`
- Test: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: profile/job/metrics/bootstrap/category-evidence/product-audit DTOs from prior plans.
- Produces: route `#/learning` labeled `Lernende Verarbeitung`.

- [ ] **Step 1: Write failing routing and page tests**

Assert navigation, metric cards, filters, `1/3` quarantine progress, `2/5` monitoring progress, suspended reason, pending jobs, category evidence, and automatic product-family audit rows.

```tsx
expect(await screen.findByRole("heading", { name: "Lernende Verarbeitung" })).toBeVisible();
expect(screen.getByText("2 / 3 Belege")).toBeVisible();
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd frontend && npm test -- --run src/pages/adaptive-processing-page.test.tsx src/App.test.tsx`

Expected: route, types, and page are absent.

- [ ] **Step 3: Implement typed API methods and overview UI**

Add API methods:

```ts
formatProfiles(query: FormatProfileQuery): Promise<PageResponse<FormatProfileSummaryDTO>>;
formatProfile(id: number): Promise<FormatProfileDetailDTO>;
adaptiveJobs(query: AdaptiveJobQuery): Promise<PageResponse<AdaptiveJobDTO>>;
adaptiveMetrics(): Promise<AdaptiveProcessingMetricsDTO>;
profileBootstrapPreview(): Promise<ProfileBootstrapPreviewDTO>;
profileBootstrapApply(confirmation: "GENERATE_INITIAL_PROFILES"): Promise<ProfileBootstrapApplyResultDTO>;
```

Use compact tables and badges; never render full profile JSON by default.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd frontend && npm test -- --run src/pages/adaptive-processing-page.test.tsx src/App.test.tsx`

Expected: page and navigation tests pass.

- [ ] **Step 5: Commit Task 3**

```bash
git add frontend/src/lib frontend/src/pages/adaptive-processing-page.tsx frontend/src/pages/adaptive-processing-page.test.tsx frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/components/app-shell.tsx
git commit -m "feat(frontend): add adaptive processing overview"
```

### Task 4: Add receipt trace review and explicit category-rule confirmation

**Files:**
- Modify: `frontend/src/pages/receipts-page.tsx`
- Modify: `frontend/src/pages/receipts-page.test.tsx`
- Modify: `frontend/src/components/receipt-badges.tsx`
- Modify: `frontend/src/components/receipt-badges.test.tsx`
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/api.ts`

**Interfaces:**
- Consumes: `PARSE_REVIEW`, parse trace endpoint/resolution endpoint, `extractionStatus`, and `createCategoryRule`.
- Produces: accessible trace-resolution dialog and checkbox `Als händlerspezifische Regel übernehmen`.

- [ ] **Step 1: Write failing receipt UI tests**

Assert review badge, unresolved-line panel, each resolution action, disabled category/product controls for `NEEDS_REVIEW`, explicit category-rule checkbox default false, and automatic product-family audit badge.

```tsx
expect(screen.getByText("Prüfung erforderlich")).toBeVisible();
expect(screen.getByRole("checkbox", { name: "Als händlerspezifische Regel übernehmen" })).not.toBeChecked();
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd frontend && npm test -- --run src/pages/receipts-page.test.tsx src/components/receipt-badges.test.tsx`

Expected: review status/actions are absent.

- [ ] **Step 3: Implement the trace panel and guarded item actions**

Load traces only for the selected receipt. After successful resolution, refresh receipt, trace, profile metrics, and pending jobs. Send `createCategoryRule` only when the checkbox is checked and a nonnull category is selected.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd frontend && npm test -- --run src/pages/receipts-page.test.tsx src/components/receipt-badges.test.tsx`

Expected: all review, accessibility, and guarded-action assertions pass.

- [ ] **Step 5: Commit Task 4**

```bash
git add frontend/src/pages/receipts-page.tsx frontend/src/pages/receipts-page.test.tsx frontend/src/components/receipt-badges.tsx frontend/src/components/receipt-badges.test.tsx frontend/src/lib
git commit -m "feat(receipts): review uncertain parse lines"
```

### Task 5: Add kill switches and safe bootstrap workflow

**Files:**
- Create: `backend/src/main/resources/db/migration/V34__add_adaptive_processing_settings.sql`
- Modify: `backend/src/main/java/de/ebon/api/dto/SettingsDto.java`
- Modify: `backend/src/main/java/de/ebon/api/service/SettingsService.java`
- Modify: `backend/src/main/java/de/ebon/api/service/DataMaintenanceService.java`
- Modify: `frontend/src/pages/settings-page.tsx`
- Modify: `frontend/src/pages/settings-page.test.tsx`
- Modify: `frontend/src/pages/adaptive-processing-page.tsx`
- Modify: `frontend/src/pages/adaptive-processing-page.test.tsx`
- Test: `backend/src/test/java/de/ebon/api/SettingsApiContractTests.java`

**Interfaces:**
- Adds settings: `adaptive_profile_auto_promotion_enabled`, `adaptive_category_learning_enabled`, `adaptive_product_family_creation_enabled`.
- Bootstrap UI always calls Preview first; Apply is enabled only after typing `GENERATE_INITIAL_PROFILES`.

- [ ] **Step 1: Write failing backend/frontend settings tests**

Assert defaults, round-trip, independent switches, switch-off behavior without deactivating existing rules, preview summary, exact confirmation, and Apply result rendering.

- [ ] **Step 2: Run tests and confirm RED**

Run backend: `cd backend && mvn -Dtest=SettingsApiContractTests test`

Run frontend: `cd frontend && npm test -- --run src/pages/settings-page.test.tsx src/pages/adaptive-processing-page.test.tsx`

Expected: settings and bootstrap confirmation UI are absent.

- [ ] **Step 3: Implement settings and preview-before-apply UI**

When a switch is false, stop only new promotion/learning/creation; do not deactivate existing active profiles/rules/families. Display sanitized cluster counts and unresolved counts before confirmation.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run backend and frontend commands from Step 2 again.

Expected: both suites pass.

- [ ] **Step 5: Commit Task 5**

```bash
git add backend/src/main/resources/db/migration/V34__add_adaptive_processing_settings.sql backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/api/SettingsApiContractTests.java frontend/src/pages frontend/src/lib
git commit -m "feat(settings): control adaptive processing"
```

### Task 6: Extend backup, restore, and reset semantics

**Files:**
- Modify: `backend/src/main/java/de/ebon/api/service/BackupService.java`
- Modify: `backend/src/main/java/de/ebon/api/service/DataMaintenanceService.java`
- Modify: `backend/src/main/java/de/ebon/backup/RollingBackupService.java`
- Test: `backend/src/test/java/de/ebon/api/service/BackupServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/service/DataMaintenanceServiceTests.java`
- Test: `backend/src/test/java/de/ebon/backup/RollingBackupServiceTests.java`

**Interfaces:**
- Backup includes profiles, evidence, traces, jobs, category evidence, rule sources, family creation audit, and settings.
- Imported-receipt reset deletes receipt-bound trace/evidence/jobs and preserves master profiles/rules/families.

- [ ] **Step 1: Write failing backup/reset tests**

Assert backup table order respects foreign keys, restore dry-run validates all new tables, rollback is transactional, secrets/raw prompts remain absent, receipt reset preserves profiles/rules/families, and counters are recomputed.

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=BackupServiceTests,DataMaintenanceServiceTests,RollingBackupServiceTests test`

Expected: new tables are missing from backup/reset registries.

- [ ] **Step 3: Register tables and explicit reset order**

Delete receipt-bound rows in child-first order, then recompute profile/category evidence counts from surviving evidence. Use the existing secret-masking pipeline for manual and rolling backups.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=BackupServiceTests,DataMaintenanceServiceTests,RollingBackupServiceTests test`

Expected: backup, dry-run, restore rollback, reset preservation, and masking tests pass.

- [ ] **Step 5: Commit Task 6**

```bash
git add backend/src/main/java/de/ebon/api/service/BackupService.java backend/src/main/java/de/ebon/api/service/DataMaintenanceService.java backend/src/main/java/de/ebon/backup/RollingBackupService.java backend/src/test/java/de/ebon/api/service backend/src/test/java/de/ebon/backup
git commit -m "feat(backup): preserve adaptive processing data"
```

### Task 7: Add dashboard metrics and Selenium smoke coverage

**Files:**
- Modify: `backend/src/main/java/de/ebon/api/dto/DashboardDto.java`
- Modify: `backend/src/main/java/de/ebon/api/DashboardController.java`
- Modify: `backend/src/main/java/de/ebon/api/service/QueryApiService.java`
- Modify: `backend/src/test/java/de/ebon/api/service/QueryApiServiceTests.java`
- Modify: `backend/src/test/java/de/ebon/api/ApiControllerWebMvcTests.java`
- Modify: `frontend/src/pages/dashboard-page.tsx`
- Modify: `frontend/src/pages/dashboard-page.test.tsx`
- Modify: `frontend/e2e/smoke.mjs`
- Modify: `frontend/src/lib/types.ts`

**Interfaces:**
- Dashboard adds counts for parse reviews, quarantined/suspended profiles, pending adaptive jobs, category conflicts, and product reviews.
- Clicking a count routes to the corresponding filtered page.

- [ ] **Step 1: Write failing backend/frontend/E2E assertions**

Add backend query assertions for soft-delete exclusion and frontend routing/card labels. Extend Selenium smoke to open `Lernende Verarbeitung`, filter `QUARANTINE`, and open one parse-review receipt using mock-safe data.

- [ ] **Step 2: Run focused tests and confirm RED**

Run backend: `cd backend && mvn -Dtest=QueryApiServiceTests,ApiControllerWebMvcTests test`

Run frontend: `cd frontend && npm test -- --run src/pages/dashboard-page.test.tsx`

Expected: adaptive counters and links are absent.

- [ ] **Step 3: Implement metrics and smoke navigation**

Query only nondeleted receipts and active/open lifecycle rows. Keep labels explicit: `Parse-Prüfung`, `Profile in Quarantäne`, `Profile suspendiert`, `Adaptive Aufträge`.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run the backend and frontend commands from Step 2.

Expected: selected suites pass.

- [ ] **Step 5: Commit Task 7**

```bash
git add backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/api/service/QueryApiServiceTests.java backend/src/test/java/de/ebon/api/ApiControllerWebMvcTests.java frontend/src/pages/dashboard-page.tsx frontend/src/pages/dashboard-page.test.tsx frontend/e2e/smoke.mjs frontend/src/lib/types.ts
git commit -m "feat(dashboard): surface adaptive processing health"
```

### Task 8: Full-system verification and initial operational audit

**Files:**
- Modify only defects demonstrated by verification; update `README.md` for operational commands and kill switches.

**Interfaces:**
- Produces: verified application, documented bootstrap/runbook, and sanitized initial profile audit.
- Consumes: all four plans.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn verify`

Expected: `BUILD SUCCESS`, zero failures, mocked external integrations.

- [ ] **Step 2: Run frontend build**

Run: `cd frontend && npm run build`

Expected: Vitest, TypeScript, and Vite complete successfully.

- [ ] **Step 3: Validate and rebuild Docker Compose**

Run: `docker compose config` then `docker compose up -d --build`.

Expected: config exits `0`; backend, frontend, and database start successfully.

- [ ] **Step 4: Run Selenium smoke tests**

Run: `cd frontend && npm run e2e`

Expected: central receipt, learning, settings, and dashboard flows pass without real OpenRouter calls.

- [ ] **Step 5: Re-run bootstrap preview and inspect sanitized state**

Use the PowerShell preview command from the lifecycle plan. Compare cluster counts with the applied result. Expected: Apply is idempotent, no duplicate profiles appear, and unresolved Legacy positions remain open/quarantined.

- [ ] **Step 6: Audit runtime tables with aggregate-only SQL**

Check profile counts by state/scope/store key, evidence counts, pending/failed jobs, `PARSE_REVIEW` counts, category evidence progress, and automatic family audit counts. Do not select `raw_text`, prompt snippets, or response snippets.

- [ ] **Step 7: Document operations**

Add exact UI/API bootstrap steps, kill-switch behavior, rollback/retry behavior, privacy restrictions, and aggregate audit queries to `README.md`.

- [ ] **Step 8: Run repository checks and commit runbook**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors, private fixtures, generated raw reports, or unrelated changes.

```bash
git add README.md
git commit -m "docs: add adaptive processing runbook"
```
