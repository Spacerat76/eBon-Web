# Adaptive Category and Product Learning Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Learn conservative store-specific category mappings and create or assign product families from confirmed parsed positions without allowing AI-only evidence to become trusted variant history.

**Architecture:** Category evidence and product-family creation are separate services invoked only for `ExtractionStatus.CONFIRMED`. Category automation promotes a normalized exact mapping after three distinct receipts or explicit user confirmation. Product AI can return existing candidates or a new-family proposal; a deterministic duplicate/line-type/variant gate owns creation.

**Tech Stack:** Java 21, Spring Boot, JPA, PostgreSQL/Flyway, Jackson, Maven, JUnit 5, Mockito, Testcontainers.

## Global Constraints

- Complete the adaptive parsing foundation first.
- Reuse lifecycle audit/idempotency patterns; do not couple category evidence to parser-profile evidence tables.
- Uncategorized remains `category_id = NULL` and `category_source = NULL`.
- Automatic category rules are store-specific `NORMALIZED_EXACT` only.
- Manual category edits create rules only when the user explicitly confirms `createRule=true`.
- Product AI receives normalized item text, store, price, quantity, and unit, never full receipt text.
- New product-family creation requires confidence `>= 0.98` and similarity below `0.85` against all active family names and active exact product-rule aliases.
- AI-created product mappings remain untrusted for variant history until manually confirmed.

---

### Task 1: Add normalized exact matching and category evidence persistence

**Files:**
- Create: `backend/src/main/resources/db/migration/V33__add_adaptive_category_product_learning.sql`
- Modify: `backend/src/main/java/de/ebon/persistence/model/RuleMatchType.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/CategorizationRule.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/ProductRule.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/CategoryEvidenceStatus.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/CategoryRuleEvidence.java`
- Create: `backend/src/main/java/de/ebon/persistence/repository/CategoryRuleEvidenceRepository.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategorizationRuleMatcher.java`
- Modify: `backend/src/main/java/de/ebon/product/ProductRuleMatcher.java`
- Test: `backend/src/test/java/de/ebon/persistence/MigrationAndRepositorySmokeTests.java`
- Test: `backend/src/test/java/de/ebon/categorization/CategorizationRuleMatcherTests.java`
- Create: `backend/src/test/java/de/ebon/product/ProductRuleMatcherTests.java`

**Interfaces:**
- Produces: `RuleMatchType.NORMALIZED_EXACT` for both rule systems.
- Produces: evidence status `COLLECTING`, `PROMOTED`, `CONFLICTED`.
- Extends rules with `RuleSource source`, nullable `suspendedAt`, and `suspensionReason`.

- [ ] **Step 1: Write failing migration and matcher tests**

Assert normalized exact ignores case, umlaut spelling, punctuation, and whitespace but not additional semantic tokens. Assert evidence uniqueness by receipt/item/category candidate.

```java
assertThat(matcher.matches(normalizedExactRule("coca-cola zero 0,5l"), item("Coca Cola Zero 0.5 L"))).isTrue();
assertThat(matcher.matches(normalizedExactRule("coca cola"), item("Coca Cola Zero"))).isFalse();
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,CategorizationRuleMatcherTests,ProductRuleMatcherTests test`

Expected: `NORMALIZED_EXACT`, evidence table, and source/suspension fields are missing.

- [ ] **Step 3: Implement V33 and one shared normalizer**

Create `backend/src/main/java/de/ebon/rules/NormalizedRuleText.java` and use it from both matchers:

```java
public String normalize(String value) {
    return value == null ? "" : Normalizer.normalize(value, Normalizer.Form.NFKD)
            .toLowerCase(Locale.ROOT)
            .replace("ß", "ss")
            .replaceAll("\\p{M}", "")
            .replaceAll("(?<=\\d)[,.](?=\\d)", ".")
            .replaceAll("[^a-z0-9]+", " ")
            .trim().replaceAll("\\s+", " ");
}
```

Add switch cases that compare the complete normalized strings.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,CategorizationRuleMatcherTests,ProductRuleMatcherTests test`

Expected: migrations and normalized exact behavior pass.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/resources/db/migration/V33__add_adaptive_category_product_learning.sql backend/src/main/java/de/ebon/persistence backend/src/main/java/de/ebon/rules backend/src/main/java/de/ebon/categorization/CategorizationRuleMatcher.java backend/src/main/java/de/ebon/product/ProductRuleMatcher.java backend/src/test/java/de/ebon
git commit -m "feat(rules): add normalized adaptive evidence"
```

### Task 2: Promote category rules after three distinct confirmed receipts

**Files:**
- Create: `backend/src/main/java/de/ebon/categorization/CategoryEvidenceKey.java`
- Create: `backend/src/main/java/de/ebon/categorization/CategoryLearningService.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategorizationService.java`
- Test: `backend/src/test/java/de/ebon/categorization/CategoryLearningServiceTests.java`
- Test: `backend/src/test/java/de/ebon/categorization/CategorizationServiceTests.java`

**Interfaces:**
- Produces: `void CategoryLearningService.recordAcceptedAiAssignment(ReceiptItem item, Category category, BigDecimal confidence)`.
- Produces: `CategorizationRule confirmManualRule(ReceiptItem item, Category category)`.
- Consumes: configured `ai_categorization_min_confidence`, default `0.900`.

- [ ] **Step 1: Write failing evidence/promotion tests**

Cover one/two/three distinct receipts, duplicate items from one receipt counting once, changed AI category creating conflict, manual contradiction, extraction gating, and no global/broad rule creation.

```java
recordEvidence(receipt1, item1, food);
recordEvidence(receipt2, item2, food);
recordEvidence(receipt3, item3, food);
assertThat(ruleRepository.findByActiveTrueOrderByPriorityAscIdAsc())
        .anySatisfy(rule -> assertThat(rule.getMatchType()).isEqualTo(RuleMatchType.NORMALIZED_EXACT));
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `cd backend && mvn -Dtest=CategoryLearningServiceTests,CategorizationServiceTests test`

Expected: evidence is not recorded and no learned rule is promoted.

- [ ] **Step 3: Implement evidence recording and locked promotion**

Record only accepted AI assignments on `CONFIRMED` items. Key evidence by normalized store, normalized full description, and category. At three distinct receipt IDs, create one active store-specific normalized-exact rule with `source=AI_ADAPTED`; mark evidence promoted transactionally.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=CategoryLearningServiceTests,CategorizationServiceTests test`

Expected: promotion, conflict, extraction gating, and uncategorized semantics pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/de/ebon/categorization backend/src/test/java/de/ebon/categorization
git commit -m "feat(categories): learn exact store mappings"
```

### Task 3: Confirm category rules from manual item updates and suspend contradictions

**Files:**
- Modify: `backend/src/main/java/de/ebon/api/dto/ReceiptItemUpdateRequest.java`
- Modify: `backend/src/main/java/de/ebon/api/service/ReceiptApiService.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategoryLearningService.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategorizationService.java`
- Create: `backend/src/test/java/de/ebon/api/ReceiptItemApiContractTests.java`
- Test: `backend/src/test/java/de/ebon/categorization/CategoryLearningServiceTests.java`

**Interfaces:**
- Extends: `ReceiptItemUpdateRequest` with boolean `createCategoryRule` default `false`.
- Produces: immediate `MANUAL` normalized-exact rule only when category is provided and `createCategoryRule=true`.

- [ ] **Step 1: Write failing API and contradiction tests**

Assert a normal manual category edit creates no rule, confirmed edit creates one exact store rule, confirmation with `categoryId=null` returns `400`, and correcting an AI-adapted rule suspends it and requeues only nonmanual assignments from that rule.

```java
mockMvc.perform(patch("/api/receipt-items/{id}", itemId)
        .header(AUTHORIZATION, bearer())
        .contentType(APPLICATION_JSON)
        .content("{\"categoryId\":5,\"createCategoryRule\":true}"))
        .andExpect(status().isOk());
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ReceiptItemApiContractTests,CategoryLearningServiceTests test`

Expected: request flag and suspension behavior are absent.

- [ ] **Step 3: Implement explicit confirmation and contradiction handling**

Call `confirmManualRule` only after the manual assignment succeeds. On contradiction, set `active=false`, record suspension reason `MANUAL_CONTRADICTION`, and clear/re-evaluate only `RULE` assignments made by that learned rule; preserve manual rows.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ReceiptItemApiContractTests,CategoryLearningServiceTests test`

Expected: explicit confirmation and bounded suspension pass.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/src/main/java/de/ebon/api/dto/ReceiptItemUpdateRequest.java backend/src/main/java/de/ebon/api/service/ReceiptApiService.java backend/src/main/java/de/ebon/categorization backend/src/test/java/de/ebon/api/ReceiptItemApiContractTests.java backend/src/test/java/de/ebon/categorization
git commit -m "feat(categories): confirm and suspend learned rules"
```

### Task 4: Extend product AI to propose existing or new families

**Files:**
- Modify: `backend/src/main/java/de/ebon/product/AiProductAssignmentResponse.java`
- Modify: `backend/src/main/java/de/ebon/product/AiProductAssignmentRequest.java`
- Create: `backend/src/main/java/de/ebon/product/AiNewProductFamilyProposal.java`
- Create: `backend/src/main/java/de/ebon/product/OpenRouterAiProductAssignmentClient.java`
- Modify: `backend/src/main/java/de/ebon/product/NoopAiProductAssignmentClient.java`
- Test: `backend/src/test/java/de/ebon/product/OpenRouterAiProductAssignmentClientTests.java`

**Interfaces:**
- Response is exactly one of existing `productFamilyId`/optional variant or `newFamilyProposal`.
- `AiNewProductFamilyProposal` fields: `familyName`, optional `variantName`, `normalizedSize`, `normalizedUnit`, `packageCount`, `confidence`, `reason`.

- [ ] **Step 1: Write failing mocked OpenRouter contract tests**

Assert request includes only item description/store/price/quantity/unit and candidate IDs/names; no raw receipt. Cover existing family, new family, malformed JSON, both branches set, and non-product response.

```java
assertThat(capturedPrompt).contains("Artikel=Bio Milch");
assertThat(capturedPrompt).doesNotContain("rawText", "Bontext");
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=OpenRouterAiProductAssignmentClientTests test`

Expected: real product AI client and proposal contract are missing.

- [ ] **Step 3: Implement fixed-schema client and validation**

Use JSON-object response format and reject responses that select both an existing family and a new proposal. Preserve the existing configured OpenRouter model/settings and mock all tests.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=OpenRouterAiProductAssignmentClientTests test`

Expected: strict response parsing and privacy assertions pass.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/src/main/java/de/ebon/product backend/src/test/java/de/ebon/product/OpenRouterAiProductAssignmentClientTests.java
git commit -m "feat(products): request new family proposals"
```

### Task 5: Gate duplicate families, variants, and non-product lines

**Files:**
- Create: `backend/src/main/java/de/ebon/product/ProductFamilyNameNormalizer.java`
- Create: `backend/src/main/java/de/ebon/product/ProductFamilyDuplicateDetector.java`
- Create: `backend/src/main/java/de/ebon/product/HighConfidenceProductFamilyService.java`
- Create: `backend/src/main/java/de/ebon/product/ProductCreationOutcome.java`
- Modify: `backend/src/main/java/de/ebon/product/ProductAssignmentService.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/ProductAssignmentSource.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/ProductRule.java`
- Test: `backend/src/test/java/de/ebon/product/ProductFamilyDuplicateDetectorTests.java`
- Test: `backend/src/test/java/de/ebon/product/HighConfidenceProductFamilyServiceTests.java`
- Test: `backend/src/test/java/de/ebon/product/ProductAssignmentServiceTests.java`

**Interfaces:**
- Produces: `ProductCreationOutcome createOrReview(ReceiptItem item, AiNewProductFamilyProposal proposal)`.
- Adds: `ProductAssignmentSource.AI_HIGH_CONFIDENCE`.
- Creates: store-specific `NORMALIZED_EXACT` product rule marked untrusted for history.

- [ ] **Step 1: Write failing safety-gate tests**

Cover confidence `0.979` versus `0.980`, exact active-rule alias duplicate, normalized-name similarity `0.85`, safe new family, size-only variant, unknown size family-only, non-product denial, default-category nonoverwrite, and AI-only history exclusion.

```java
assertThat(service.createOrReview(item, proposal("Coca Cola Zero 0.5l", "0.98"))).satisfies(outcome -> {
    assertThat(outcome.family().getName()).isEqualTo("Coca Cola Zero");
    assertThat(outcome.variant().getName()).isEqualTo("0.5 l");
});
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ProductFamilyDuplicateDetectorTests,HighConfidenceProductFamilyServiceTests,ProductAssignmentServiceTests test`

Expected: duplicate gate/new-family creation path is absent.

- [ ] **Step 3: Implement deterministic gate and transactional creation**

Use normalized Levenshtein similarity:

```java
BigDecimal similarity = BigDecimal.ONE.subtract(
        BigDecimal.valueOf(distance).divide(BigDecimal.valueOf(maxLength), 3, RoundingMode.HALF_UP));
if (similarity.compareTo(new BigDecimal("0.85")) >= 0) return NEEDS_REVIEW;
```

Check active family names and normalized `match_value` aliases from active exact product rules first. Create family and optional variant in one transaction, assign the current item, create the store-specific exact rule, and log source/confidence/reason. Extend the V33 database constraint for `receipt_item.product_assignment_source` with `AI_HIGH_CONFIDENCE`. Exclude `AI_HIGH_CONFIDENCE` exact-rule hits from `isTrustedHistory` until manually confirmed.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ProductFamilyDuplicateDetectorTests,HighConfidenceProductFamilyServiceTests,ProductAssignmentServiceTests test`

Expected: threshold, duplicate, variant, non-product, category-preservation, and history tests pass.

- [ ] **Step 5: Commit Task 5**

```bash
git add backend/src/main/java/de/ebon/product backend/src/main/java/de/ebon/persistence/model/ProductAssignmentSource.java backend/src/main/java/de/ebon/persistence/model/ProductRule.java backend/src/test/java/de/ebon/product
git commit -m "feat(products): create safe high-confidence families"
```

### Task 6: Expose audit data and review actions through APIs

**Files:**
- Create: `backend/src/main/java/de/ebon/api/dto/CategoryLearningEvidenceDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/ProductFamilyCreationAuditDto.java`
- Modify: `backend/src/main/java/de/ebon/api/dto/ReceiptItemDto.java`
- Modify: `backend/src/main/java/de/ebon/api/CategorizationRulesController.java`
- Modify: `backend/src/main/java/de/ebon/api/ProductsController.java`
- Test: `backend/src/test/java/de/ebon/api/AdaptiveLearningApiContractTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: `GET /api/categorization-rules/evidence`.
- Produces: `GET /api/products/family-creation-audit`.
- Extends item DTO with automatic family creation source/reason while returning no prompt/raw response.

- [ ] **Step 1: Write failing API contract tests**

Assert bearer auth, pagination, sanitized reason fields, and status/source serialization.

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=AdaptiveLearningApiContractTests test`

Expected: endpoints/DTOs are missing.

- [ ] **Step 3: Implement mappings and update specification**

Document category evidence thresholds, explicit manual rule confirmation, product-family threshold/duplicate behavior, `AI_HIGH_CONFIDENCE`, and untrusted-history semantics in F-03, F-19, API DTOs, and acceptance criteria.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=AdaptiveLearningApiContractTests test`

Expected: authenticated sanitized API contracts pass.

- [ ] **Step 5: Commit Task 6**

```bash
git add backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/api/AdaptiveLearningApiContractTests.java ebon-specification.md
git commit -m "feat(api): expose adaptive learning audits"
```

### Task 7: Downstream learning verification gate

**Files:**
- No planned source changes; return to the responsible task for any demonstrated defect.

**Interfaces:**
- Produces: verified backend category/product learning for the UI plan.
- Consumes: Tasks 1–6.

- [ ] **Step 1: Run focused category and product suites**

Run: `cd backend && mvn -Dtest='Categorization*Tests,Category*Tests,Product*Tests,AdaptiveLearningApiContractTests' test`

Expected: all selected tests pass and external clients are mocked.

- [ ] **Step 2: Run full backend verification**

Run: `cd backend && mvn verify`

Expected: `BUILD SUCCESS` with zero failures.

- [ ] **Step 3: Verify database migration and Docker configuration**

Run: `docker compose config`

Expected: exit `0`; V33 is included in the backend image inputs.

- [ ] **Step 4: Inspect data-safety invariants in tests**

Confirm test assertions cover null uncategorized pairs, manual protection, category nonoverwrite, AI-only history exclusion, and no full receipt text in product prompts.

- [ ] **Step 5: Check repository scope**

Run: `git diff --check` and `git status --short`.

Expected: no whitespace errors and no receipt fixture from live data.
