# eBon Product-Family and Assignment Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Verify every position from parser-verified receipts, apply only deterministically safe Codex proposals at confidence `>= 0.98`, route medium confidence to Markdown and conflicts to UI, and preserve manual/trusted-history semantics.

**Architecture:** A sanitized backend inventory exposes IDs, hashes, structural product context, and current assignment state for verified local receipts. Codex submits an existing/new-family decision to a preview endpoint; backend services re-check the current item, duplicate/variant/non-product/branch/manual gates, freeze an impact receipt, and apply transactionally. The runner persists proposal metadata and routes it by confidence without storing item descriptions.

**Tech Stack:** Java 25, Spring Boot 4.0.6, existing product-learning services, PostgreSQL 18, Node.js 24.16.0, TypeScript 6.0.3, JUnit 5, Mockito, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- Complete `2026-07-13-adaptive-category-product-learning.md` before this plan.
- Reuse `ProductFamilyNameNormalizer`, `ProductFamilyDuplicateDetector`, `HighConfidenceProductFamilyService`, product rule/history services, and `AI_HIGH_CONFIDENCE` semantics.
- Runner schedules product work only for `VERIFIED` or `VERIFIED_WITH_OCR`; backend independently requires `ParseStatus.PARSED` and `ExtractionStatus.CONFIRMED`.
- Never overwrite `MANUAL + CONFIRMED`, `NO_PRODUCT`, or explicitly rejected assignments.
- Direct Codex proposals are AI evidence (`AI_HIGH_CONFIDENCE`/`AUTO_ASSIGNED`), not manual confirmation and not trusted variant history.
- Markdown-confirmed/edited proposals become `MANUAL + CONFIRMED` only after preview/apply.
- New family requires confidence `>= 0.98`, normalized uniqueness, active alias similarity `< 0.85`, safe line type, and valid family/variant separation.
- Exact store rules need three distinct conflict-free receipts unless the user explicitly confirms. Broad/global/regex/contains rules stay in UI.
- Product family default categories fill only NULL category/source.
- Every direct/bulk operation uses the generic `AuditOperationService` from the parser/profile plan.

---

### Task 1: Expose a sanitized verified-item inventory

**Files:**
- Create: `backend/src/main/java/de/ebon/audit/AuditProductInventoryService.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductItemDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductInventoryDto.java`
- Modify: `backend/src/main/java/de/ebon/api/AuditController.java`
- Modify: `backend/src/main/java/de/ebon/persistence/repository/ReceiptRepository.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditProductInventoryServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/AuditApiContractTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: protected `GET /api/audit/product-items?paperlessDocumentId=<id>`.
- Produces: `AuditProductInventoryDto inventory(Integer paperlessDocumentId)`.
- Consumes: local receipt mapped by unique Paperless document ID.

- [ ] **Step 1: Write failing eligibility/privacy tests**

```java
@Test
void inventoryIncludesOnlyConfirmedExtractionFromParsedReceipt() {
    AuditProductInventoryDto result = service.inventory(970);
    assertThat(result.items()).extracting(AuditProductItemDto::receiptItemId)
            .containsExactly(verifiedItem.getId());
    assertThat(result.toString()).doesNotContain(verifiedItem.getDescription());
}

@Test
void reviewOrErrorReceiptCannotEnterProductAudit() {
    receipt.setParseStatus(ParseStatus.PARSE_REVIEW);
    assertThatThrownBy(() -> service.inventory(970))
            .isInstanceOf(AuditEligibilityException.class);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -Dtest=AuditProductInventoryServiceTests,AuditApiContractTests test`

- [ ] **Step 3: Define the sanitized item contract**

```java
public record AuditProductItemDto(
        Long receiptItemId,
        Long receiptId,
        String descriptionHash,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal totalPrice,
        Long categoryId,
        Long currentFamilyId,
        Long currentVariantId,
        ProductAssignmentSource assignmentSource,
        ProductAssignmentStatus assignmentStatus,
        ExtractionStatus extractionStatus,
        boolean manualProtected) {}
```

Hash `NormalizedRuleText.normalize(description)` from the prerequisite adaptive product plan with SHA-256. Store/branch, receipt date, item count, and Paperless/receipt IDs may appear in the enclosing DTO; item description does not.

- [ ] **Step 4: Implement the server-side eligibility gate**

Require active receipt, `PARSED`, zero unresolved trace count, and `CONFIRMED` item extraction. Return protected assignments for reporting but mark them ineligible for proposals. Missing local receipt produces `LOCAL_RECEIPT_MISSING`, allowing the audit runner to request normal sync rather than invent a product record.

- [ ] **Step 5: Run tests and commit**

Run: `cd backend && mvn -Dtest=AuditProductInventoryServiceTests,AuditApiContractTests test`

```bash
git add ebon-specification.md backend/src/main/java/de/ebon/audit/AuditProductInventoryService.java backend/src/main/java/de/ebon/api/AuditController.java backend/src/main/java/de/ebon/api/dto/AuditProduct* backend/src/main/java/de/ebon/persistence/repository/ReceiptRepository.java backend/src/test/java/de/ebon
git commit -m "feat(audit): expose verified product item inventory"
```

### Task 2: Preview Codex product proposals through deterministic gates

**Files:**
- Create: `backend/src/main/java/de/ebon/audit/AuditProductDecisionService.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditDecisionRoute.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductDecisionRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductDecisionPreviewDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductDecisionApplyRequest.java`
- Modify: `backend/src/main/java/de/ebon/api/AuditController.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditProductDecisionServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/AuditApiContractTests.java`

**Interfaces:**
- Produces: `preview(AuditProductDecisionRequest): AuditProductDecisionPreviewDto`.
- Produces: `apply(AuditProductDecisionApplyRequest): AuditProductDecisionPreviewDto`.
- Produces: protected `POST /api/audit/product-decisions/preview` and `/apply`.
- Produces: closed route enum `AuditDecisionRoute = DIRECT | MARKDOWN | UI`.
- Consumes: `HighConfidenceProductFamilyService`, existing family/variant lookup, `ProductReviewService` manual path, and `AuditOperationService`.

- [ ] **Step 1: Write the failing threshold/manual/duplicate matrix**

```java
@ParameterizedTest
@CsvSource({"0.979,MARKDOWN", "0.980,DIRECT", "1.000,DIRECT"})
void routesConfidenceExactly(BigDecimal confidence, AuditDecisionRoute route) {
    assertThat(service.preview(request(confidence)).route()).isEqualTo(route);
}

@Test
void directProposalCannotOverwriteManualOrCreateASimilarFamily() {
    item.assignProduct(family, null, MANUAL, CONFIRMED, null);
    assertThat(service.preview(newFamilyRequest("Coca-Cola Zero", "0.990")).route())
            .isEqualTo(AuditDecisionRoute.UI);
}
```

Cover existing-family assignment, optional existing variant, new family/new variant, package-as-variant, missing-size family-only, exact normalized alias, similarity exactly `0.850`, safe `NO_PRODUCT`, forbidden payment/discount/tax lines, branch conflict, changed description hash, changed assignment, and no category overwrite.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -Dtest=AuditProductDecisionServiceTests,AuditApiContractTests test`

- [ ] **Step 3: Define a closed proposal union**

```java
public record AuditProductDecisionRequest(
        @NotBlank String proposalId,
        @Positive int revision,
        @NotNull @Positive Long receiptItemId,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String expectedDescriptionHash,
        @NotNull @DecimalMin("0.000") @DecimalMax("1.000") BigDecimal confidence,
        Long productFamilyId,
        @Size(max = 255) String newProductFamilyName,
        Long productVariantId,
        @Size(max = 255) String newProductVariantName,
        BigDecimal normalizedSize,
        @Size(max = 32) String normalizedUnit,
        @Positive Integer packageCount,
        boolean noProduct,
        boolean applySameStoreExactDescription) {}
```

Validation requires exactly one of existing family, new family, or `noProduct`; an existing variant requires its family; a new variant requires a family branch; `noProduct` forbids all product fields.

- [ ] **Step 4: Implement frozen preview routing**

Re-read the item with a write lock, recompute description hash, eligibility, manual protection, duplicate/similarity, line type, unit/package/price plausibility, same-store exact impact, and branch conflicts. Return `DIRECT`, `MARKDOWN`, or `UI` plus fixed reason codes and affected counts. Freeze the request hash/impact through `AuditOperationService`; do not mutate on preview.

- [ ] **Step 5: Implement direct apply semantics**

For `DIRECT` existing-family/new-family results, assign source `AI_HIGH_CONFIDENCE`, status `AUTO_ASSIGNED`, and confidence. Reuse `HighConfidenceProductFamilyService` for creation and variant splitting. For `MARKDOWN`, apply only after a later manual decision flag from the Markdown import, using `ProductReviewService` so the result is `MANUAL + CONFIRMED`. `UI` cannot be applied through this endpoint.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend && mvn -Dtest=AuditProductDecisionServiceTests,HighConfidenceProductFamilyServiceTests,ProductAssignmentServiceTests,AuditApiContractTests test`

```bash
git add backend/src/main/java/de/ebon/audit/AuditProductDecisionService.java backend/src/main/java/de/ebon/api/AuditController.java backend/src/main/java/de/ebon/api/dto/AuditProductDecision* backend/src/test/java/de/ebon
git commit -m "feat(audit): gate Codex product decisions"
```

### Task 3: Build the Codex product work queue and confidence routing

**Files:**
- Create: `audit-runner/src/work/product-work-item.ts`
- Create: `audit-runner/src/work/product-work-queue.ts`
- Create: `audit-runner/src/products/product-audit-service.ts`
- Modify: `audit-runner/src/ebon/ebon-client.ts`
- Modify: `audit-runner/src/state/audit-state.ts`
- Modify: `audit-runner/src/main.ts`
- Test: `audit-runner/src/work/product-work-queue.test.ts`
- Test: `audit-runner/src/products/product-audit-service.test.ts`

**Interfaces:**
- Produces: `ProductWorkItem` with IDs, hashes, structural values, current family/variant IDs, status, local links, and allowed actions.
- Adds CLI commands: `product-work-next`, `submit-product-proposal`, and `apply-direct-products`.
- Consumes: verified document IDs from source/profile phases and backend product inventory/preview APIs.

- [ ] **Step 1: Write failing eligibility and routing tests**

```ts
it("does not queue unresolved, OCR-different, or manual-protected items", async () => {
  const items = queue.build(stateWithMixedEligibility());
  expect(items.map(item => item.receiptItemId)).toEqual([safeOpenItemId]);
});

it.each([
  [0.98, "DIRECT"],
  [0.85, "MARKDOWN"],
  [0.849, "UI"]
])("routes confidence %s to %s", async (confidence, route) => {
  expect(await service.submit(proposal(confidence))).toMatchObject({ route });
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- product-work-queue.test.ts product-audit-service.test.ts`

- [ ] **Step 3: Define privacy-safe work items**

```ts
export interface ProductWorkItem {
  workItemId: string;
  receiptItemId: number;
  receiptId: number;
  paperlessDocumentId: number;
  descriptionHash: string;
  storeName: string;
  storeBranch: string | null;
  quantity: string | null;
  unit: string | null;
  unitPrice: string | null;
  totalPrice: string | null;
  categoryId: number | null;
  currentFamilyId: number | null;
  currentVariantId: number | null;
  allowedActions: Array<"EXISTING_FAMILY" | "NEW_FAMILY" | "NO_PRODUCT" | "SEND_TO_UI">;
  links: { receipt: string; paperless: string; productReview: string };
}
```

The description is inspected interactively through the receipt/UI link and is never stored in the work item.

- [ ] **Step 4: Implement proposal submission and direct-apply batching**

Submit one proposal at a time to backend preview and store its route/reason codes. `apply-direct-products` requires an explicit CLI flag `--confirm-run <runId>`, obeys `maxMutationsPerRun`, checkpoints each applied idempotency key, and stops on any changed preview instead of recalculating silently.

- [ ] **Step 5: Run tests and commit**

Run: `cd audit-runner && npm test -- product-work-queue.test.ts product-audit-service.test.ts && npm run build`

```bash
git add audit-runner/src/work audit-runner/src/products audit-runner/src/ebon/ebon-client.ts audit-runner/src/state/audit-state.ts audit-runner/src/main.ts
git commit -m "feat(audit): route Codex product proposals by confidence"
```

### Task 4: Learn exact store evidence and support controlled rollback

**Files:**
- Modify: `backend/src/main/java/de/ebon/audit/AuditProductDecisionService.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditProductRollbackService.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductRollbackRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductRollbackDto.java`
- Modify: `backend/src/main/java/de/ebon/api/AuditController.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditProductRollbackServiceTests.java`
- Modify: `audit-runner/src/products/product-audit-service.ts`
- Test: `audit-runner/src/products/product-rollback.test.ts`

**Interfaces:**
- Produces: `AuditProductRollbackDto rollback(String idempotencyKey, String expectedAppliedHash)`.
- Produces: protected `POST /api/audit/product-decisions/rollback`.
- Consumes: prior sanitized before/after assignment IDs/status/source/confidence from `AuditOperationReceipt.impact`.
- `AuditProductRollbackDto` contains only idempotency key, operation status, rolled-back item count, fixed result code, and `replayed`; it contains no item description or receipt text.

- [ ] **Step 1: Write failing evidence/manual/rollback tests**

```java
@Test
void threeAiMatchesMayCreateOnlyAnUntrustedStoreExactRule() {
    service.applyDirect(matchesOnThreeDistinctReceipts());
    assertThat(ruleRepository.findAll()).singleElement().satisfies(rule -> {
        assertThat(rule.getMatchType()).isEqualTo(RuleMatchType.NORMALIZED_EXACT);
        assertThat(rule.isTrustedForHistory()).isFalse();
    });
}

@Test
void rollbackRefusesWhenUserChangedTheAssignmentAfterAuditApply() {
    manuallyReassign(item);
    assertThatThrownBy(() -> rollbackService.rollback(key, appliedHash))
            .isInstanceOf(AuditOperationConflictException.class);
}
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd backend && mvn -Dtest=AuditProductDecisionServiceTests,AuditProductRollbackServiceTests test`

- [ ] **Step 3: Implement exact evidence promotion**

Reuse the adaptive product evidence service. Require three distinct receipts, identical normalized full description, same store key, same family/variant, no branch conflict, and zero manual contradiction. Create only `NORMALIZED_EXACT` store-specific rules marked untrusted; user-confirmed Markdown decisions may mark the resulting evidence trusted according to the existing learning plan.

- [ ] **Step 4: Implement guarded rollback**

Lock the target item(s), compare the current assignment tuple with the recorded audit after-state, and restore only the recorded before-state. If any item changed after apply, abort the whole operation. Mark the operation receipt `ROLLED_BACK`; never delete families/variants that acquired unrelated references.

- [ ] **Step 5: Run backend/runner tests and commit**

Run:

```bash
cd backend && mvn -Dtest=AuditProductDecisionServiceTests,AuditProductRollbackServiceTests,ProductAssignmentServiceTests test
cd ../audit-runner && npm test -- product-audit-service.test.ts product-rollback.test.ts && npm run build
```

```bash
git add backend/src/main/java/de/ebon/audit backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/audit audit-runner/src/products
git commit -m "feat(audit): learn and roll back product decisions safely"
```

### Task 5: Run the product-audit completion gate

**Files:**
- Modify: `docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md`

**Interfaces:**
- Produces: verified product boundary for Markdown/UI workflow.

- [ ] **Step 1: Run full affected gates**

```bash
cd backend && mvn verify
cd ../audit-runner && npm test && npm run build
cd .. && docker compose config
docker compose --profile audit build audit backend
git diff --check
git status --short
```

- [ ] **Step 2: Run a synthetic mixed-confidence smoke**

Use one direct `0.98` existing-family proposal, one `0.95` Markdown proposal, one `0.84` UI proposal, one manual-protected item, one unresolved parse item, and one similar-family conflict. Assert exactly one AI high-confidence mutation, no manual overwrite, and no OpenRouter request.

- [ ] **Step 3: Record evidence and commit**

```bash
git add docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md
git commit -m "docs(audit): record product audit milestone"
```
