# Profile Lifecycle, Bootstrap, and Rollback Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate initial merchant profiles from read-only Paperless data, validate AI profile proposals in quarantine, promote them after three complete receipts, monitor active versions, and automatically roll back bounded affected receipts.

**Architecture:** Persist immutable evidence and idempotent adaptive jobs. A dedicated AI client proposes closed-schema profile JSON, while server-side comparison owns every promotion decision. Bootstrap re-parses Paperless raw text through the Legacy parser and never consumes persisted manual assignments.

**Tech Stack:** Java 21, Spring Boot, JPA, PostgreSQL/Flyway, Jackson, Spring TaskExecutor/Scheduler, JUnit 5, Mockito, Testcontainers, Maven.

## Global Constraints

- Complete `2026-07-13-adaptive-parsing-foundation.md` first.
- Follow the master constraints in `2026-07-13-adaptive-receipt-processing.md`.
- Paperless bootstrap may issue only GET requests through `PaperlessClient`.
- Preview is side-effect-free; Apply may write local profile/evidence/job tables only.
- Bootstrap truth is a fresh Legacy parse of Paperless `content`; never query persisted receipt items or manual assignments for expected values.
- Legacy-unrecognized price-like lines remain `UNRESOLVED` and keep the profile in `QUARANTINE`.
- Tests mock Paperless and OpenRouter.
- Generated format profiles remain separate from existing `parse_rule`/`parse_rule_suggestion`; this plan does not bypass user acceptance for AI-generated `parse_rule` entries.
- Shadow AI is comparison evidence only when a profile parse is already valid; adoption of AI output still occurs only through the existing F-02 fallback after deterministic parsing fails.

---

### Task 1: Persist evidence, diffs, jobs, and monitoring counters

**Files:**
- Create: `backend/src/main/resources/db/migration/V32__add_format_profile_lifecycle.sql`
- Create: `backend/src/main/java/de/ebon/persistence/model/ProfileEvidenceMode.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/ProfileEvidenceResult.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/AdaptiveJobType.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/AdaptiveJobStatus.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/FormatProfileEvidence.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/AdaptiveProcessingJob.java`
- Create: `backend/src/main/java/de/ebon/persistence/repository/FormatProfileEvidenceRepository.java`
- Create: `backend/src/main/java/de/ebon/persistence/repository/AdaptiveProcessingJobRepository.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/ReceiptFormatProfile.java`
- Test: `backend/src/test/java/de/ebon/persistence/MigrationAndRepositorySmokeTests.java`
- Test: `backend/src/test/java/de/ebon/persistence/model/PersistenceModelBehaviorTests.java`

**Interfaces:**
- Produces: evidence modes `QUARANTINE`, `POST_PROMOTION`, `SAMPLE`.
- Produces: job types `SHADOW_VERIFY`, `ROLLBACK_REPARSE` with idempotency key uniqueness.
- Produces: repository queries for three distinct Paperless document successes and receipts after the last successful verification.

- [ ] **Step 1: Write failing persistence tests**

Assert duplicate evidence for the same profile/Paperless document/mode is rejected, duplicate open jobs share one idempotency key, and monitoring counters survive reload. `receipt_id` may be null for a Paperless document not yet imported, while `paperless_document_id` is always present.

```java
assertThat(evidenceRepository.countDistinctSuccessfulReceipts(profile.getId(), ProfileEvidenceMode.QUARANTINE))
        .isEqualTo(3);
assertThat(job.getStatus()).isEqualTo(AdaptiveJobStatus.PENDING);
```

- [ ] **Step 2: Run focused tests and confirm RED**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,PersistenceModelBehaviorTests test`

Expected: lifecycle types or V32 tables are missing.

- [ ] **Step 3: Implement V32 and minimal lifecycle methods**

Use exact enum values:

```java
public enum ProfileEvidenceMode { QUARANTINE, POST_PROMOTION, SAMPLE }
public enum ProfileEvidenceResult { MATCH, MISMATCH, INVALID_REFERENCE }
public enum AdaptiveJobType { SHADOW_VERIFY, ROLLBACK_REPARSE }
public enum AdaptiveJobStatus { PENDING, RUNNING, SUCCEEDED, FAILED }
```

Add profile fields `productionHitCount`, `consecutivePostPromotionChecks`, `lastSuccessfulVerificationAt`, `activatedAt`, `suspendedAt`, and `suspensionReason`. Store `paperless_document_id NOT NULL` plus nullable `receipt_id` on evidence; uniqueness uses profile, Paperless document ID, and mode.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,PersistenceModelBehaviorTests test`

Expected: selected tests pass and Flyway reaches V32.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/resources/db/migration/V32__add_format_profile_lifecycle.sql backend/src/main/java/de/ebon/persistence backend/src/test/java/de/ebon/persistence
git commit -m "feat(parser): persist profile lifecycle evidence"
```

### Task 2: Compare profile and reference parses structurally

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileParseDiff.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileParseComparator.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ProfileParseComparatorTests.java`

**Interfaces:**
- Produces: `ProfileParseDiff compare(ProfileInterpretationResult candidate, ReceiptParseResult reference)`.
- `ProfileParseDiff` exposes `matches()`, `requiredFieldDiffs`, `itemDiffs`, and `unresolvedLineNumbers`.

- [ ] **Step 1: Write failing comparison tests**

Cover exact match, OCR-only raw differences, required-field mismatch, item order/count mismatch, quantity/unit/price mismatch, optional null-vs-null, and unresolved lines.

```java
assertThat(comparator.compare(candidate, reference).matches()).isTrue();
assertThat(comparator.compare(candidateWithMissingItem, reference).itemDiffs()).isNotEmpty();
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ProfileParseComparatorTests test`

Expected: compilation fails because comparator/diff types are absent.

- [ ] **Step 3: Implement normalized field and item comparison**

Match the design's promotion criteria exactly. Optional branch/time warnings are non-blocking only when the candidate would persist no conflicting concrete value.

```java
boolean matches = requiredFieldDiffs.isEmpty()
        && itemDiffs.isEmpty()
        && candidate.traces().stream().noneMatch(trace -> trace.lineType() == ParseLineType.UNRESOLVED);
```

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ProfileParseComparatorTests test`

Expected: selected tests pass with structured, raw-text-free diffs.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/de/ebon/parser/profile/ProfileParseDiff.java backend/src/main/java/de/ebon/parser/profile/ProfileParseComparator.java backend/src/test/java/de/ebon/parser/profile/ProfileParseComparatorTests.java
git commit -m "feat(parser): compare profile parse evidence"
```

### Task 3: Generate closed-schema profile proposals through OpenRouter

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/AiFormatProfileProposal.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileProposalSample.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileProposalInput.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/AiFormatProfileProposalClient.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/NoopAiFormatProfileProposalClient.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/OpenRouterAiFormatProfileProposalClient.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/FormatProfileProposalService.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/OpenRouterAiFormatProfileProposalClientTests.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/FormatProfileProposalServiceTests.java`

**Interfaces:**
- Consumes: one or more normalized/minimized profile samples, each containing Legacy partial parse, Legacy error, identity, and profile schema version.
- Produces: `Optional<ReceiptFormatProfile> FormatProfileProposalService.propose(ProfileProposalInput input)` in `QUARANTINE`.

- [ ] **Step 1: Write failing mocked client and validation tests**

Assert fixed JSON response format, no full prompt/response persistence, invalid JSON rejection, unknown schema-field rejection, and validation against the example receipt before save.

```java
when(client.propose(any())).thenReturn(new AiFormatProfileProposal(validProfileJson, "model", tokenUsage));
assertThat(service.propose(input)).get().extracting(ReceiptFormatProfile::getState)
        .isEqualTo(FormatProfileState.QUARANTINE);
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=OpenRouterAiFormatProfileProposalClientTests,FormatProfileProposalServiceTests test`

Expected: proposal types and service are missing.

- [ ] **Step 3: Implement the client and validated persistence**

Define `ProfileProposalSample` as normalized document plus fresh Legacy parse/error and define `ProfileProposalInput` as identity, schema version, source, and one-to-three distinct samples. The system prompt must request only schema version `1` and must state that unmatched plausible lines use `UNRESOLVED`. Runtime fallback supplies one sample; bootstrap supplies up to three distinct samples from the same cluster. The service must validate JSON with `ReceiptFormatDefinitionCodec`, validate against every supplied receipt, and deduplicate by scope/fingerprint/canonical definition hash.

```java
ReceiptFormatDefinition definition = codec.read(response.profileJson());
boolean validForEverySample = input.samples().stream()
        .allMatch(sample -> validator.validate(definition, sample.document()).valid());
if (!validForEverySample) return Optional.empty();
return Optional.of(repository.save(ReceiptFormatProfile.quarantine(input.identity(), codec.write(definition), source)));
```

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=OpenRouterAiFormatProfileProposalClientTests,FormatProfileProposalServiceTests test`

Expected: valid proposals persist once; malformed or unsafe proposals persist nothing.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/src/main/java/de/ebon/parser/profile backend/src/test/java/de/ebon/parser/profile
git commit -m "feat(parser): generate quarantined format profiles"
```

### Task 4: Promote profiles after three complete receipts

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/FormatProfileEvidenceService.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/FormatProfilePromotionService.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/EvidenceOutcome.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/PromotionOutcome.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/FormatProfilePromotionServiceTests.java`

**Interfaces:**
- Produces: `EvidenceOutcome recordEvidence(Long profileId, Long receiptId, Long aiParsingLogId, ProfileEvidenceMode mode, ProfileParseDiff diff)`.
- Produces: `PromotionOutcome evaluatePromotion(Long profileId)`.

- [ ] **Step 1: Write failing promotion state-machine tests**

Cover counts `1/3`, `2/3`, `3/3`, duplicate receipt exclusion, mismatch reset, unresolved-line rejection, transactional retirement of the prior active version, and unique-active conflict retry.

```java
assertThat(service.evaluatePromotion(profileId).state()).isEqualTo(FormatProfileState.ACTIVE);
assertThat(repository.findActiveForIdentity(identity)).singleElement().extracting(ReceiptFormatProfile::getId)
        .isEqualTo(profileId);
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=FormatProfilePromotionServiceTests test`

Expected: promotion services are missing.

- [ ] **Step 3: Implement locked promotion and mismatch reset**

Use a repository pessimistic-write query for the profile and all active siblings. Promote only three distinct `MATCH` receipts and no unresolved trace. Retire the previous active version in the same transaction.

- [ ] **Step 4: Run tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=FormatProfilePromotionServiceTests test`

Expected: all state-machine and concurrency cases pass.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/src/main/java/de/ebon/parser/profile backend/src/test/java/de/ebon/parser/profile/FormatProfilePromotionServiceTests.java
git commit -m "feat(parser): promote verified format profiles"
```

### Task 5: Schedule shadow checks and execute bounded rollback

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/AdaptiveJobPlanner.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/AdaptiveJobRunner.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileRollbackService.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/RollbackOutcome.java`
- Modify: `backend/src/main/java/de/ebon/parser/profile/ProfileAwareReceiptParser.java`
- Modify: `backend/src/main/java/de/ebon/api/service/ReceiptApiService.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/AdaptiveJobPlannerTests.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ProfileRollbackServiceTests.java`

**Interfaces:**
- Produces: `void AdaptiveJobPlanner.afterProfileParse(Receipt receipt, ReceiptFormatProfile profile)`.
- Produces: `RollbackOutcome ProfileRollbackService.suspendAndQueue(Long profileId, ProfileParseDiff diff)`.
- Consumes: existing reparse services with manual-assignment transfer/protection.

- [ ] **Step 1: Write failing monitoring and rollback tests**

Assert checks for production hits 1–5 and 10, 20, 30; no check for 6–9; budget exhaustion leaves a pending job; mismatch suspends immediately; only receipts after `lastSuccessfulVerificationAt` are queued; manual edits remain protected.

```java
assertThat(planner.shouldVerify(5, 5)).isTrue();
assertThat(planner.shouldVerify(6, 5)).isFalse();
assertThat(planner.shouldVerify(10, 5)).isTrue();
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=AdaptiveJobPlannerTests,ProfileRollbackServiceTests test`

Expected: planner/rollback classes are missing.

- [ ] **Step 3: Implement persistent jobs, retries, suspension, and reparse queueing**

Claim jobs transactionally by id, mark `RUNNING`, and return failures to `PENDING` with bounded retry metadata. Invalid/low-confidence AI reference results become `INVALID_REFERENCE` and are retried; they do not suspend profiles. A valid mismatch suspends and enqueues `ROLLBACK_REPARSE` jobs using `profileId:receiptId:profileVersion` as idempotency key.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=AdaptiveJobPlannerTests,ProfileRollbackServiceTests test`

Expected: scheduling sequence, retry semantics, bounded receipt selection, and manual protection pass.

- [ ] **Step 5: Commit Task 5**

```bash
git add backend/src/main/java/de/ebon/parser/profile backend/src/main/java/de/ebon/api/service/ReceiptApiService.java backend/src/test/java/de/ebon/parser/profile
git commit -m "feat(parser): monitor and roll back profiles"
```

### Task 6: Implement side-effect-free Paperless bootstrap preview

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/bootstrap/ProfileBootstrapDocument.java`
- Create: `backend/src/main/java/de/ebon/parser/bootstrap/ProfileBootstrapCluster.java`
- Create: `backend/src/main/java/de/ebon/parser/bootstrap/ProfileBootstrapPreview.java`
- Create: `backend/src/main/java/de/ebon/parser/bootstrap/PaperlessProfileBootstrapService.java`
- Test: `backend/src/test/java/de/ebon/parser/bootstrap/PaperlessProfileBootstrapServiceTests.java`

**Interfaces:**
- Produces: `ProfileBootstrapPreview preview()` with sanitized cluster counts/diffs and no database writes.
- Consumes: `PaperlessClient#fetchDocumentsByTag()`, `ReceiptTextNormalizer`, `ReceiptFormatIdentifier`, and `RuleBasedReceiptParser` directly.

- [ ] **Step 1: Write failing read-only bootstrap tests**

Mock three Paperless documents. Persist deliberately conflicting manual receipt items in the test database and prove preview ignores them by verifying only a freshly constructed Legacy parser result is used. Assert no profile/evidence save call and no raw text in the preview DTO.

```java
verify(paperlessClient).fetchDocumentsByTag();
verifyNoInteractions(receiptItemRepository);
verify(profileRepository, never()).save(any());
assertThat(preview.toString()).doesNotContain("private receipt line");
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=PaperlessProfileBootstrapServiceTests test`

Expected: bootstrap service/DTOs are missing.

- [ ] **Step 3: Implement clustering and fresh Legacy baselines**

Map each `PaperlessDocument.content()` directly to normalization, identity, and `ruleBasedReceiptParser.parse(content)`. Do not load `Receipt` or `ReceiptItem` entities. Cluster by scope key and fingerprint. Mark Legacy-unmatched price-like lines as `UNRESOLVED` in the cluster sample and sanitized diff.

```java
return paperlessClient.fetchDocumentsByTag().stream()
        .map(document -> bootstrapDocument(document.id(), document.content()))
        .collect(Collectors.groupingBy(ProfileBootstrapDocument::clusterKey))
        .entrySet().stream()
        .map(this::previewCluster)
        .toList();
```

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=PaperlessProfileBootstrapServiceTests test`

Expected: preview is side-effect-free, sanitized, grouped, and based only on fresh Legacy parses.

- [ ] **Step 5: Commit Task 6**

```bash
git add backend/src/main/java/de/ebon/parser/bootstrap backend/src/test/java/de/ebon/parser/bootstrap
git commit -m "feat(parser): preview Paperless profile bootstrap"
```

### Task 7: Apply bootstrap profiles idempotently and expose secured APIs

**Files:**
- Create: `backend/src/main/java/de/ebon/api/dto/ProfileBootstrapPreviewDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/ProfileBootstrapApplyRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/ProfileBootstrapApplyResultDto.java`
- Create: `backend/src/main/java/de/ebon/api/AdaptiveParsingController.java`
- Modify: `backend/src/main/java/de/ebon/parser/bootstrap/PaperlessProfileBootstrapService.java`
- Test: `backend/src/test/java/de/ebon/api/AdaptiveParsingApiContractTests.java`
- Test: `backend/src/test/java/de/ebon/parser/bootstrap/PaperlessProfileBootstrapServiceTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: `POST /api/parser/profiles/bootstrap/preview`.
- Produces: `POST /api/parser/profiles/bootstrap/apply` with body `{ "confirmation": "GENERATE_INITIAL_PROFILES" }`.
- Apply returns counts for active, quarantined, unresolved, skipped, and failed clusters.

- [ ] **Step 1: Write failing API and idempotency tests**

Assert bearer authentication, exact confirmation, repeat apply creates no duplicate profile/evidence, complete three-document clusters can become active, and clusters with unresolved Legacy lines remain quarantine.

```java
mockMvc.perform(post("/api/parser/profiles/bootstrap/apply")
        .header(AUTHORIZATION, bearer())
        .contentType(APPLICATION_JSON)
        .content("{\"confirmation\":\"GENERATE_INITIAL_PROFILES\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.quarantinedProfiles").isNumber());
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=AdaptiveParsingApiContractTests,PaperlessProfileBootstrapServiceTests test`

Expected: endpoints/apply path are missing.

- [ ] **Step 3: Implement apply and update the product contract**

Apply must call the same preview builder, propose/validate a profile per cluster, persist `LEGACY_BOOTSTRAP` evidence, and promote only clusters with three complete distinct comparisons. Do not persist raw bootstrap texts outside existing receipts. Update API, F-02, settings/admin workflow, and acceptance criteria in `ebon-specification.md`.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=AdaptiveParsingApiContractTests,PaperlessProfileBootstrapServiceTests test`

Expected: secured preview/apply, confirmation, idempotency, and incomplete-cluster quarantine pass.

- [ ] **Step 5: Commit Task 7**

```bash
git add backend/src/main/java/de/ebon/api backend/src/main/java/de/ebon/parser/bootstrap backend/src/test/java/de/ebon/api backend/src/test/java/de/ebon/parser/bootstrap ebon-specification.md
git commit -m "feat(parser): apply initial profile bootstrap"
```

### Task 8: Integrate lifecycle into sync and execute initial profile generation

**Files:**
- Modify: `backend/src/main/java/de/ebon/sync/PaperlessSyncRunner.java`
- Modify: `backend/src/main/java/de/ebon/parser/AiParsingFallbackService.java`
- Modify: `backend/src/main/java/de/ebon/categorization/CategorizationService.java`
- Modify: `backend/src/main/java/de/ebon/product/ProductAssignmentService.java`
- Create: `backend/src/test/java/de/ebon/sync/PaperlessSyncRunnerTests.java`
- Test: `backend/src/test/java/de/ebon/parser/AiParsingFallbackServiceTests.java`
- Runtime output only: local database profile/evidence rows; no repository receipt artifacts.

**Interfaces:**
- Consumes: successful AI parse to create/evaluate profile evidence.
- Produces: profile proposal/evidence after sync and persistent shadow jobs after active profile use.

- [ ] **Step 1: Write failing sync integration tests**

Prove successful AI fallback creates a quarantined proposal/evidence, active profile hits schedule monitoring, `PARSE_REVIEW` items do not flow into category/product automation, and Paperless pagination behavior remains unchanged.

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=PaperlessSyncRunnerTests,AiParsingFallbackServiceTests test`

Expected: lifecycle collaborators are not invoked.

- [ ] **Step 3: Wire lifecycle collaborators without changing Paperless write behavior**

After parse persistence, record proposal/evidence and plan verification jobs. Make `categorizeItems(Receipt, List<ReceiptItem>)` public and keep its existing transactional entry point; `assignItems` is already public. Guard downstream services:

```java
List<ReceiptItem> confirmed = savedReceipt.getItems().stream()
        .filter(item -> item.getExtractionStatus() == ExtractionStatus.CONFIRMED)
        .toList();
categorizationService.categorizeItems(savedReceipt, confirmed);
productAssignmentService.assignItems(savedReceipt, confirmed);
```

- [ ] **Step 4: Run automated verification before live access**

Run: `cd backend && mvn verify`

Expected: `BUILD SUCCESS`; all Paperless/OpenRouter tests use mocks.

- [ ] **Step 5: Start the local application and run bootstrap preview**

Run: `docker compose up -d --build`

Then, in PowerShell with `APP_API_TOKEN` already set in the environment:

```powershell
$headers = @{ Authorization = "Bearer $env:APP_API_TOKEN" }
$preview = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/parser/profiles/bootstrap/preview" -Headers $headers
$preview | Select-Object totalDocuments,totalClusters,completeClusters,unresolvedClusters,failedClusters
```

Expected: Paperless is read through GET operations only; output contains counts and no receipt text.

- [ ] **Step 6: Apply initial profile generation after inspecting preview counts**

```powershell
$headers = @{ Authorization = "Bearer $env:APP_API_TOKEN" }
$body = @{ confirmation = "GENERATE_INITIAL_PROFILES" } | ConvertTo-Json
$result = Invoke-RestMethod -Method Post -Uri "http://localhost:8080/api/parser/profiles/bootstrap/apply" -Headers $headers -ContentType "application/json" -Body $body
$result | Select-Object activeProfiles,quarantinedProfiles,unresolvedClusters,skippedClusters,failedClusters
```

Expected: complete three-receipt clusters may be active; Legacy-unrecognized positions are counted as unresolved and their profiles remain quarantine. No raw receipt file is created in the repository.

- [ ] **Step 7: Audit database state without reading raw receipt text**

Run a read-only SQL aggregation by profile state, store key, branch scope, and fingerprint prefix. Expected: at most one active profile per identity and no duplicate definition hash.

- [ ] **Step 8: Check repository hygiene and commit Task 8**

Run: `git status --short` and `git diff --check`.

Expected: no generated Paperless receipt or raw bootstrap report appears.

```bash
git add backend/src/main/java/de/ebon/sync/PaperlessSyncRunner.java backend/src/main/java/de/ebon/parser/AiParsingFallbackService.java backend/src/main/java/de/ebon/categorization/CategorizationService.java backend/src/main/java/de/ebon/product/ProductAssignmentService.java backend/src/test/java/de/ebon/sync/PaperlessSyncRunnerTests.java backend/src/test/java/de/ebon/parser/AiParsingFallbackServiceTests.java
git commit -m "feat(parser): integrate adaptive profile lifecycle"
```
