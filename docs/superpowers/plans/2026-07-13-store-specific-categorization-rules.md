# Store-Specific Categorization Rules Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an optional exact, case-insensitive store constraint to description categorization rules and safely categorize the confirmed open receipt items.

**Architecture:** A nullable `store_name` column extends the existing rule entity without changing global-rule behavior. The central matcher owns the AND semantics, so automatic categorization, preview, and bulk apply remain consistent; API and settings UI expose the same optional constraint.

**Tech Stack:** Java 21, Spring Boot, Jakarta Validation, JPA, PostgreSQL/Flyway, JUnit/AssertJ/Mockito, React, TypeScript, Vitest/Testing Library.

## Global Constraints

- Keep changes aligned with `ebon-specification.md`, especially F-03 and F-07.
- Preserve `category_id = NULL` and `category_source = NULL` for unresolved items; do not create a fake category.
- Never overwrite manually edited, already categorized, or soft-deleted receipt data.
- Keep DTOs, backend validation, frontend types, and UI behavior consistent.
- Use mocked/test data only; do not call Paperless-NGX or OpenRouter.
- Verify backend with `cd backend && mvn verify` and frontend with `cd frontend && npm run build`.

---

### Task 1: Persist and match an optional store constraint

**Files:**
- Create: `backend/src/main/resources/db/migration/V29__add_store_specific_categorization_rules.sql`
- Modify: `backend/src/main/java/de/ebon/persistence/model/CategorizationRule.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategorizationRuleMatcher.java`
- Test: `backend/src/test/java/de/ebon/categorization/CategorizationRuleMatcherTests.java`
- Test: `backend/src/test/java/de/ebon/persistence/model/PersistenceModelBehaviorTests.java`

**Interfaces:**
- Produces: `CategorizationRule#getStoreName(): String`, constructor/update support for nullable `storeName`.
- Produces: `CategorizationRuleMatcher#matches` requiring both the existing predicate and exact normalized store equality when `storeName` is set.

- [ ] **Step 1: Write failing matcher and model tests**

Add cases proving a global description rule still matches, `storeName = " REWE "` matches receipt store `rewe`, a different/null receipt store does not match, and updating with blank input normalizes to `null`.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `cd backend && mvn -Dtest=CategorizationRuleMatcherTests,PersistenceModelBehaviorTests test`

Expected: compilation/test failure because `storeName` access and constructor/update parameters do not exist.

- [ ] **Step 3: Add the schema and minimal model/matcher implementation**

Migration schema statement:

```sql
ALTER TABLE categorization_rule
    ADD COLUMN store_name VARCHAR(255);
```

Normalize stores through one model helper:

```java
private static String normalizeStoreName(String value) {
    return value == null || value.isBlank() ? null : value.trim();
}
```

Matcher store guard:

```java
private boolean matchesStoreConstraint(CategorizationRule rule, ReceiptItem item) {
    if (rule.getStoreName() == null) return true;
    String actual = item.getReceipt() == null ? null : item.getReceipt().getStoreName();
    return actual != null && actual.trim().equalsIgnoreCase(rule.getStoreName());
}
```

Return `matchesStoreConstraint(rule, item) && existingPredicate` from `matches`.

- [ ] **Step 4: Run the focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=CategorizationRuleMatcherTests,PersistenceModelBehaviorTests test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/resources/db/migration/V29__add_store_specific_categorization_rules.sql backend/src/main/java/de/ebon/persistence/model/CategorizationRule.java backend/src/main/java/de/ebon/categorization/CategorizationRuleMatcher.java backend/src/test/java/de/ebon/categorization/CategorizationRuleMatcherTests.java backend/src/test/java/de/ebon/persistence/model/PersistenceModelBehaviorTests.java
git commit -m "feat(categories): support store-specific rules"
```

### Task 2: Extend rule API, validation, preview, and management

**Files:**
- Modify: `backend/src/main/java/de/ebon/api/dto/CategorizationRuleDto.java`
- Modify: `backend/src/main/java/de/ebon/api/dto/CategorizationRuleRequest.java`
- Modify: `backend/src/main/java/de/ebon/api/dto/CategorizationRulePreviewRequest.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategorizationRuleManagementService.java`
- Test: `backend/src/test/java/de/ebon/categorization/CategorizationRuleManagementServiceTests.java`
- Create: `backend/src/test/java/de/ebon/api/CategorizationApiContractTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: nullable `String storeName` in rule DTO, request, and preview request.
- Produces: request validation error when `matchField == STORE_NAME` and `storeName` is nonblank.

- [ ] **Step 1: Write failing service and API tests**

Cover create/update/list round-trip, preview passing the store constraint to the matcher, trimming blank to `null`, and HTTP 400 for `{ "matchField":"STORE_NAME", "storeName":"REWE" }`.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `cd backend && mvn -Dtest=CategorizationRuleManagementServiceTests,CategorizationApiContractTests test`

Expected: compilation/assertion failures for missing `storeName` and validation.

- [ ] **Step 3: Implement DTO propagation and cross-field validation**

Add `@Size(max = 255) String storeName` to requests and DTO. Add an `@AssertTrue` record method:

```java
@AssertTrue(message = "Eine zusätzliche Händlerbedingung ist nur für Beschreibungsregeln erlaubt.")
public boolean isStoreConstraintValid() {
    return matchField != RuleMatchField.STORE_NAME || storeName == null || storeName.isBlank();
}
```

Pass `storeName` through transient preview rules, create/update calls, and `toDto`.

Update F-03/F-07 to state that description rules may have an optional exact, case-insensitive `store_name` AND constraint and that it is available in management and preview.

- [ ] **Step 4: Run the focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=CategorizationRuleManagementServiceTests,CategorizationApiContractTests test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/de/ebon/api/dto backend/src/main/java/de/ebon/categorization/CategorizationRuleManagementService.java backend/src/test/java/de/ebon/categorization/CategorizationRuleManagementServiceTests.java backend/src/test/java/de/ebon/api/CategorizationApiContractTests.java ebon-specification.md
git commit -m "feat(categories): expose store constraints in API"
```

### Task 3: Add store constraint editing to Settings UI

**Files:**
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/mock-api.ts`
- Modify: `frontend/src/pages/settings-page.tsx`
- Test: `frontend/src/pages/settings-page.test.tsx`

**Interfaces:**
- Consumes: nullable `storeName` on categorization rule DTO/create/update/preview payloads.
- Produces: labeled input `Nur bei Händler (optional)` for `DESCRIPTION` rules only.

- [ ] **Step 1: Write failing UI tests**

Test that the input appears for `DESCRIPTION`, its value is sent during preview/save and restored during edit, the rule list displays `Nur bei Händler: REWE`, and switching to `STORE_NAME` clears and hides it.

- [ ] **Step 2: Run the focused test and confirm RED**

Run: `cd frontend && npm test -- --run src/pages/settings-page.test.tsx`

Expected: queries/assertions fail because the field is absent.

- [ ] **Step 3: Implement types and UI behavior**

Add `storeName?: string | null` to `CategorizationRuleDTO`, `CategorizationRuleRequest`, and `CategorizationRulePreviewRequest`. Extend `emptyRule`, edit, normalize, preview and save payloads. Render:

```tsx
{ruleDraft.matchField === "DESCRIPTION" ? (
  <label>
    Nur bei Händler (optional)
    <Input value={ruleDraft.storeName ?? ""} onChange={(event) => onRuleDraftChange({ ...ruleDraft, storeName: event.target.value })} />
  </label>
) : null}
```

When changing to `STORE_NAME`, set `storeName: null` in the same state update.

- [ ] **Step 4: Run the focused test and confirm GREEN**

Run: `cd frontend && npm test -- --run src/pages/settings-page.test.tsx`

Expected: all settings-page tests pass.

- [ ] **Step 5: Commit Task 3**

```bash
git add frontend/src/lib/types.ts frontend/src/lib/mock-api.ts frontend/src/pages/settings-page.tsx frontend/src/pages/settings-page.test.tsx
git commit -m "feat(settings): edit store-specific category rules"
```

### Task 4: Seed confirmed rules and safely backfill open positions

**Files:**
- Create: `backend/src/main/resources/db/migration/V30__categorize_confirmed_open_items.sql`
- Test: `backend/src/test/java/de/ebon/persistence/MigrationAndRepositorySmokeTests.java`

**Interfaces:**
- Consumes: `categorization_rule.store_name` from Task 1.
- Produces: idempotent rules and backfill for the confirmed mapping below.

- [ ] **Step 1: Write a failing migration smoke test**

Assert rule existence/category/store constraint for every mapping, assert `BEDIENUNGSTHEKE` has `store_name = 'REWE'`, assert `ORIGINAL` has no seeded rule, and assert backfill excludes manual and soft-deleted rows.

- [ ] **Step 2: Run the migration test and confirm RED**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests test`

Expected: missing V30 rules/assertions fail.

- [ ] **Step 3: Implement the idempotent seed and guarded backfill**

Use exact description rules for:

```text
Fleisch und Wurst: SPARERIBS; RD HUEFTE; ROULADE FRZ
Vorrat und Fertiggerichte: KIPA GEF. VEGAN; Nasi Goreng; Baml Goreng; CORNICHONS KRAEU; DELIKATESS SENF; TAFELMEERRETTICH
Suesswaren und Snacks: TRIOLADE; Verano Vanilla
Haushalt: FH-DOSE 450ML; TIEFKUEHLTASCHE; Paradies Baby C Power; Paradies Micro AAA 4 St
Baby und Kind: Mayben B&K Sonnencreme 100ml; SauBär Badezubehör Pad
Koerperpflege: essence Nagelkleber fix it!; o.b.ExtraProtect Super 42St
Lebensmittel: LEBENSMITTEL
Fleisch und Wurst with store_name REWE: BEDIENUNGSTHEKE
```

Insert only when category, description, match type and normalized nullable store constraint do not already exist. Backfill through `receipt` with `receipt.deleted_at IS NULL`, and update only items with null category/source and `is_manually_edited = false`. Do not seed `ORIGINAL`.

- [ ] **Step 4: Run migration and categorization tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,CategorizationRuleMatcherTests,CategorizationRuleManagementServiceTests test`

Expected: all selected tests pass.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/src/main/resources/db/migration/V30__categorize_confirmed_open_items.sql backend/src/test/java/de/ebon/persistence/MigrationAndRepositorySmokeTests.java
git commit -m "data(categories): classify confirmed open items"
```

### Task 5: Full verification and live-data audit

**Files:**
- No planned file changes; fix only defects demonstrated by verification and repeat the failed check.

**Interfaces:**
- Consumes: completed backend, migrations, and frontend.
- Produces: verification evidence and remaining-open audit.

- [ ] **Step 1: Run backend verification**

Run: `cd backend && mvn verify`

Expected: BUILD SUCCESS with all tests passing.

- [ ] **Step 2: Run frontend verification**

Run: `cd frontend && npm run build`

Expected: tests, TypeScript compilation, and Vite build succeed.

- [ ] **Step 3: Rebuild the local Compose application**

Run: `docker compose up -d --build`

Expected: backend, frontend, and database containers become healthy/running and Flyway applies V29/V30.

- [ ] **Step 4: Audit remaining open items read-only**

Run a PostgreSQL query grouping non-deleted receipt items where both category fields are null. Expected: `ORIGINAL` remains; no confirmed description remains open. If newly imported ambiguous descriptions appear, report them instead of guessing.

- [ ] **Step 5: Check formatting and repository state**

Run: `git diff --check` and `git status --short`

Expected: no whitespace errors; only intentional changes are present.
