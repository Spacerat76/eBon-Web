# Product Family Seeding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add conservative product master data for current recurring receipt items and assign the existing local receipt data through the audited product-assignment service.

**Architecture:** An additive Flyway migration creates product families, size-safe variants, and store-specific exact rules. A focused integration test migrates an empty PostgreSQL database and proves that representative data is available and usable by the product-assignment service. The `NO_PRODUCT` classifier is extended only for non-product accounting lines.

**Tech Stack:** Java 25, Spring Boot, Flyway, PostgreSQL Testcontainers, JUnit 5, AssertJ.

---

### Task 1: Verify the seeded product-master contract

**Files:**
- Create: `backend/src/test/java/de/ebon/product/ProductSeedMigrationTests.java`
- Create: `backend/src/main/resources/db/migration/V26__seed_product_families_and_rules.sql`
- Create: `backend/src/main/resources/db/migration/V27__seed_additional_product_families_and_rules.sql`

- [ ] **Step 1: Write the failing integration test**

```java
@Test
void migrationSeedsStoreSpecificCocaColaAndDmWaterRules() {
    migrateEmptyDatabase();

    assertThat(productFamilyId("Coca-Cola Zero")).isNotNull();
    assertThat(productVariantId("Coca-Cola Zero", "0.33 l")).isNotNull();
    assertThat(ruleCount("REWE", "CC Z 0,33L EW FL")).isEqualTo(1);
    assertThat(defaultCategory("Denkmit Destilliertes Wasser")).isEqualTo("Haushalt");
}
```

- [ ] **Step 2: Run the test and verify the expected failure**

Run: `cd backend && mvn "-Dtest=ProductSeedMigrationTests" test`

Expected: FAIL because the product seed migrations and their seeded data do not exist yet.

- [ ] **Step 3: Add the seed migration**

Create additive V26 and V27 migrations containing temporary mapping tables with `family_name`, `category_name`, `store_name`, `match_value`, and optional `variant_name`. Insert distinct families, variants with explicit package sizes only, and exact rules. Do not insert raw receipt text, personal data, API data, or generic guessed mappings.

- [ ] **Step 4: Run the isolated migration test**

Run: `cd backend && mvn "-Dtest=ProductSeedMigrationTests" test`

Expected: PASS with Coca-Cola Zero, Denkmit, and the additional REWE household product master data available from the migrations.

### Task 2: Keep accounting lines out of product matching

**Files:**
- Modify: `backend/src/test/java/de/ebon/product/ProductAssignmentServiceTests.java`
- Modify: `backend/src/main/java/de/ebon/product/ProductAssignmentService.java`

- [ ] **Step 1: Write the failing unit test**

```java
@ParameterizedTest
@ValueSource(strings = {"Sofortstorno - Abteibrot", "PFAND 0,25 EURO", "CC gratis"})
void accountingLineIsMarkedNoProduct(String description) {
    ReceiptItem item = new ReceiptItem(0, description, new BigDecimal("-0.25"));

    productAssignmentService().assignItems(receiptWith(item), List.of(item));

    assertThat(item.getProductAssignmentStatus()).isEqualTo(ProductAssignmentStatus.NO_PRODUCT);
}
```

- [ ] **Step 2: Run the test and verify the expected failure**

Run: `cd backend && mvn "-Dtest=ProductAssignmentServiceTests" test`

Expected: FAIL for at least cancellation or deposit lines because the non-product pattern is too narrow.

- [ ] **Step 3: Extend the explicit non-product pattern**

Add narrowly scoped German terms for `storno`, `pfand`, and `gratis` to `NON_PRODUCT_LINE`. Keep ordinary product descriptions eligible for rules and review.

- [ ] **Step 4: Run the unit test**

Run: `cd backend && mvn "-Dtest=ProductAssignmentServiceTests" test`

Expected: PASS; all accounting-line examples are `NO_PRODUCT`.

### Task 3: Apply and audit the current local data

**Files:**
- No repository file changes.

- [ ] **Step 1: Build and restart the backend**

Run: `BACKEND_PORT=18080 docker compose up -d --build backend`

Expected: Flyway V26 and V27 succeed and the backend health check is healthy.

- [ ] **Step 2: Trigger the existing product-assignment API once**

Run: `POST /api/products/assignments/run` with `{ "openOnly": true }` and the local bearer token.

Expected: All active items are evaluated through the existing audited service. The currently registered product-KI client is a no-op, so no external KI call is made.

- [ ] **Step 3: Inspect the persisted result**

Run SQL counts by `product_assignment_status`, product family, and store.

Expected: Rules create `AUTO_ASSIGNED` entries; unclear items are `NEEDS_REVIEW`; no accounting line has a product family.

### Task 4: Full verification

**Files:**
- Verify: `backend/`

- [ ] **Step 1: Run the backend suite**

Run: `cd backend && mvn verify`

Expected: PASS.

- [ ] **Step 2: Check the patch**

Run: `git diff --check`

Expected: no whitespace errors.
