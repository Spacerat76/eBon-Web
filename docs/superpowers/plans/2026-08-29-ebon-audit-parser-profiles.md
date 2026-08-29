# eBon Merchant, Branch, Parser, and Profile Audit Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Turn verified Paperless source snapshots into a complete merchant/branch/fingerprint work queue, accept Codex-authored closed-schema profile candidates without OpenRouter, validate every cluster receipt, and use the existing quarantine/promotion/rollback lifecycle.

**Architecture:** The file-backed runner clusters sanitized parse previews and produces resumable Codex work items. New backend audit APIs validate a Codex candidate against fresh Paperless GET content, store it in quarantine through the existing lifecycle services, record an idempotent operation receipt, and evaluate full-cluster evidence before the normal promotion service may activate it.

**Tech Stack:** Java 25, Spring Boot 4.0.6, PostgreSQL 18/Flyway, existing adaptive profile services, Node.js 24.16.0, TypeScript 6.0.3, JUnit 5, Testcontainers, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- Complete `2026-07-13-profile-lifecycle-bootstrap-rollback.md` before this plan.
- Reuse `ProfileParseComparator`, `FormatProfileEvidenceService`, `FormatProfilePromotionService`, `ProfileRollbackService`, and `PaperlessProfileBootstrapService`.
- Parser reference truth is a fresh Legacy parse of Paperless content; never query persisted items, categories, products, or manual assignments as expected output.
- Codex candidates contain declarative schema version `1`, never code or scripts.
- A candidate enters `QUARANTINE` with `AI_GENERATED` source. Only an explicitly edited UI correction becomes `USER_CORRECTED`.
- A cluster with fewer than three distinct complete receipts or any relevant mismatch remains quarantined.
- Full-cluster regression is additional to the three-receipt minimum.
- Audit APIs never call OpenRouter, even if live OpenRouter is configured.
- All API output and local state are sanitized and contain no raw lines or profile regex bodies.

---

### Task 1: Build stable merchant/branch/fingerprint clusters and Codex work items

**Files:**
- Create: `audit-runner/src/clusters/cluster-key.ts`
- Create: `audit-runner/src/clusters/cluster-builder.ts`
- Create: `audit-runner/src/work/profile-work-item.ts`
- Create: `audit-runner/src/work/profile-work-queue.ts`
- Test: `audit-runner/src/clusters/cluster-builder.test.ts`
- Test: `audit-runner/src/work/profile-work-queue.test.ts`
- Modify: `audit-runner/src/state/audit-state.ts`

**Interfaces:**
- Produces: `ClusterKey = { storeNameKey; storeBranchKey; fingerprint; fingerprintVersion }`.
- Produces: `ClusterBuilder.build(documents): AuditCluster[]`.
- Produces: `ProfileWorkQueue.next(state): ProfileWorkItem | null`.
- `ProfileWorkItem` contains IDs, statuses, counts, profile IDs/versions, diff codes, and local eBon/Paperless links only.

- [ ] **Step 1: Write failing identity and privacy tests**

```ts
it("keeps branch identity separate from layout identity", () => {
  const clusters = builder.build([
    verified({ storeNameKey: "rewe", storeBranchKey: "nord", fingerprint: "fp-1" }),
    verified({ storeNameKey: "rewe", storeBranchKey: "sued", fingerprint: "fp-1" })
  ]);
  expect(clusters).toHaveLength(2);
});

it("creates no work item containing receipt text or item descriptions", () => {
  expect(JSON.stringify(queue.next(stateWithPrivateSources()))).not.toMatch(/MILCH|rawText|ocrText/);
});
```

- [ ] **Step 2: Run tests and confirm failure**

Run: `cd audit-runner && npm test -- cluster-builder.test.ts profile-work-queue.test.ts`

Expected: FAIL because clustering/work queue components are absent.

- [ ] **Step 3: Implement stable cluster keys**

Require nonblank merchant key, 64-character lowercase fingerprint, and positive fingerprint version. Use an empty branch key for store scope and `UNBEKANNTE_FILIALE` only as an explicit open branch value. Sort clusters by unresolved count descending, then document count descending, then key for stable Codex processing order.

- [ ] **Step 4: Define the bounded work item**

```ts
export interface ProfileWorkItem {
  workItemId: string;
  clusterKey: ClusterKey;
  paperlessDocumentIds: number[];
  representativeDocumentIds: number[]; // maximum three
  receiptIds: number[];
  activeProfileId: number | null;
  activeProfileVersion: number | null;
  sourceStatuses: Record<SourceVerificationStatus, number>;
  parseStatuses: Record<string, number>;
  unresolvedLineCount: number;
  diffCodes: string[];
  requiredAction: "PROPOSE_PROFILE" | "REVIEW_DIFFERENCE" | "WAIT_FOR_EVIDENCE" | "NONE";
  links: { receipt: string[]; paperless: string[] };
}
```

Representative IDs are the earliest, median, and latest distinct documents after stable date/ID ordering. Do not include raw source fields.

- [ ] **Step 5: Run tests and commit**

Run: `cd audit-runner && npm test -- cluster-builder.test.ts profile-work-queue.test.ts && npm run build`

```bash
git add audit-runner/src/clusters audit-runner/src/work audit-runner/src/state/audit-state.ts
git commit -m "feat(audit): queue merchant branch format clusters"
```

### Task 2: Add generic backend audit-operation receipts

**Files:**
- Create: `backend/src/main/resources/db/migration/V35__add_audit_operation_receipts.sql`
- Create: `backend/src/main/java/de/ebon/audit/AuditOperationType.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditImpact.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditOperationPreview.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditMutationResult.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditOperationApplyResult.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditOperationConflictException.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/AuditOperationStatus.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/AuditOperationReceipt.java`
- Create: `backend/src/main/java/de/ebon/persistence/repository/AuditOperationReceiptRepository.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditOperationService.java`
- Test: `backend/src/test/java/de/ebon/persistence/AuditOperationReceiptPersistenceTests.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditOperationServiceTests.java`
- Modify: `backend/src/main/java/de/ebon/api/service/BackupService.java`
- Modify: `backend/src/test/java/de/ebon/api/service/BackupServiceTests.java`

**Interfaces:**
- Produces: `AuditOperationPreview reservePreview(String idempotencyKey, AuditOperationType type, String requestHash, AuditImpact impact)`.
- Produces: `AuditOperationApplyResult apply(String idempotencyKey, String previewToken, Supplier<AuditMutationResult> mutation)`.
- Produces: statuses `PREVIEWED`, `APPLIED`, `ROLLED_BACK`, and `FAILED`.
- Consumed later by profile and product audit mutations.

- [ ] **Step 1: Write failing persistence/idempotency tests**

```java
@Test
void sameKeyAndHashReturnsTheFrozenPreviewButDifferentHashConflicts() {
    AuditOperationPreview first = service.reservePreview("PA-42", PROFILE_CANDIDATE, "hash-a", impact());
    assertThat(service.reservePreview("PA-42", PROFILE_CANDIDATE, "hash-a", impact()))
            .isEqualTo(first);
    assertThatThrownBy(() -> service.reservePreview("PA-42", PROFILE_CANDIDATE, "hash-b", impact()))
            .isInstanceOf(AuditOperationConflictException.class);
}

@Test
void applyRunsTheMutationOnce() {
    service.apply("PA-42", preview.token(), mutation);
    AuditOperationApplyResult replay = service.apply("PA-42", preview.token(), mutation);
    verify(mutation, times(1)).get();
    assertThat(replay.replayed()).isTrue();
}
```

- [ ] **Step 2: Run focused tests and verify failure**

Run: `cd backend && mvn -Dtest=AuditOperationReceiptPersistenceTests,AuditOperationServiceTests test`

- [ ] **Step 3: Create the constrained migration**

`audit_operation_receipt` uses `idempotency_key VARCHAR(128) PRIMARY KEY`, `operation_type VARCHAR(48)`, `request_hash CHAR(64)`, `preview_token_hash CHAR(64)`, sanitized `impact JSONB`, status, target type/ID, created/applied/rolled-back timestamps, and a non-sensitive result code. Constraints reject unknown status/type and non-object impact. Do not store request bodies, receipt text, profile definitions, descriptions, prompts, or model responses.

Define `AuditOperationType` as `PROFILE_CANDIDATE`, `PROFILE_REGRESSION`, `PRODUCT_DECISION`, `PRODUCT_ROLLBACK`, and `MARKDOWN_DECISION`. `AuditImpact` contains only target type/ID, affected receipt/item counts, and fixed reason codes. `AuditMutationResult` contains only target type/ID and a fixed result code. `AuditOperationPreview` exposes the idempotency key, type, preview token, status, and sanitized impact; `AuditOperationApplyResult` exposes the stored target/result plus a `replayed` flag. The exception maps to HTTP `409` without echoing request data.

- [ ] **Step 4: Implement transactional reserve/apply/rollback state transitions**

Lock rows with `PESSIMISTIC_WRITE`. Apply only from `PREVIEWED`, return the prior `AuditOperationApplyResult` with `replayed=true` from `APPLIED`, and reject stale tokens or changed request hashes. Mark `FAILED` only after the surrounding mutation transaction rolls back through a separate `REQUIRES_NEW` failure recorder.

- [ ] **Step 5: Extend backup/restore coverage**

Add the table after its referenced master data in `BackupService.backupTables()`. Test backup, validation, restore, sequence behavior, and absence of disallowed private columns.

- [ ] **Step 6: Run tests and commit**

Run: `cd backend && mvn -Dtest=AuditOperationReceiptPersistenceTests,AuditOperationServiceTests,BackupServiceTests test`

```bash
git add backend/src/main/resources/db/migration/V35__add_audit_operation_receipts.sql backend/src/main/java/de/ebon/persistence backend/src/main/java/de/ebon/audit backend/src/main/java/de/ebon/api/service/BackupService.java backend/src/test/java/de/ebon
git commit -m "feat(audit): persist idempotent operation receipts"
```

### Task 3: Accept Codex profile candidates into quarantine without OpenRouter

**Files:**
- Create: `backend/src/main/java/de/ebon/audit/AuditProfileCandidateService.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProfileCandidateRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProfileCandidatePreviewDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProfileCandidateApplyRequest.java`
- Modify: `backend/src/main/java/de/ebon/api/AuditController.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditProfileCandidateServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/AuditApiContractTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: `preview(AuditProfileCandidateRequest): AuditProfileCandidatePreviewDto`.
- Produces: `apply(AuditProfileCandidateApplyRequest): AuditProfileCandidatePreviewDto`.
- Produces: protected `POST /api/audit/profile-candidates/preview` and `/apply`.
- Consumes: existing `ReceiptFormatDefinitionCodec`, validator, `PaperlessClient`, `ProfileParseComparator`, lifecycle evidence service, and `AuditOperationService`.

- [ ] **Step 1: Write failing no-OpenRouter/quarantine tests**

```java
@Test
void applyCreatesOneAiGeneratedQuarantineProfileFromFreshPaperlessSamples() {
    AuditProfileCandidatePreviewDto preview = service.preview(requestForDocuments(10, 11, 12));
    AuditProfileCandidatePreviewDto applied = service.apply(apply(preview.previewToken()));

    assertThat(applied.profileState()).isEqualTo(FormatProfileState.QUARANTINE);
    assertThat(applied.profileSource()).isEqualTo(FormatProfileSource.AI_GENERATED);
    verify(paperlessClient, times(3)).fetchDocumentById(anyInt());
    verifyNoInteractions(aiFormatProfileProposalClient);
}
```

Also cover wrong cluster ID, fewer/more than one-to-three representative IDs, invalid schema, raw definition leakage, stale preview, changed Paperless content, duplicate candidate definition, and manual profile predecessor selection.

- [ ] **Step 2: Run focused tests and verify failure**

Run: `cd backend && mvn -Dtest=AuditProfileCandidateServiceTests,AuditApiContractTests test`

- [ ] **Step 3: Define the closed candidate request**

```java
public record AuditProfileCandidateRequest(
        @NotBlank @Size(max = 128) String proposalId,
        @Positive int revision,
        @NotNull FormatProfileScope scope,
        @NotBlank String storeNameKey,
        String storeBranchKey,
        @NotBlank @Pattern(regexp = "[0-9a-f]{64}") String fingerprint,
        @Positive int fingerprintVersion,
        @NotBlank @Size(max = 100_000) String profileDefinition,
        @Size(min = 1, max = 3) List<@Positive Integer> representativePaperlessDocumentIds) {}
```

The response exposes validation/evidence counts, diff codes, profile ID/version/state, affected receipt count, and preview token, but never returns `profileDefinition` or Paperless content.

- [ ] **Step 4: Implement preview then apply**

Fetch each sample through Paperless GET, normalize/identify it, require the exact requested cluster, run fresh Legacy and candidate-profile parses, validate every line trace, and freeze the sanitized impact through `AuditOperationService`. Apply writes one immutable `AI_GENERATED` profile in `QUARANTINE`; it does not evaluate promotion yet.

- [ ] **Step 5: Run tests and commit**

Run: `cd backend && mvn -Dtest=AuditProfileCandidateServiceTests,AuditApiContractTests test`

```bash
git add ebon-specification.md backend/src/main/java/de/ebon/audit/AuditProfileCandidateService.java backend/src/main/java/de/ebon/api/AuditController.java backend/src/main/java/de/ebon/api/dto/AuditProfileCandidate* backend/src/test/java/de/ebon
git commit -m "feat(audit): admit Codex profiles into quarantine"
```

### Task 4: Validate full clusters and route lifecycle outcomes

**Files:**
- Create: `backend/src/main/java/de/ebon/audit/AuditProfileRegressionService.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProfileRegressionRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProfileRegressionDto.java`
- Modify: `backend/src/main/java/de/ebon/api/AuditController.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditProfileRegressionServiceTests.java`
- Modify: `audit-runner/src/work/profile-work-queue.ts`
- Create: `audit-runner/src/profiles/profile-audit-service.ts`
- Test: `audit-runner/src/profiles/profile-audit-service.test.ts`
- Modify: `audit-runner/src/main.ts`

**Interfaces:**
- Produces: `AuditProfileRegressionDto evaluate(profileId, allClusterDocumentIds)`.
- Adds CLI command: `verify-profiles`.
- Consumes: lifecycle evidence/promotion/rollback services and the audit operation receipt created in Task 2.

- [ ] **Step 1: Write failing full-cluster promotion tests**

```java
@Test
void threeMatchesDoNotPromoteWhenAFourthEligibleClusterReceiptDiffers() {
    AuditProfileRegressionDto result = service.evaluate(profileId, List.of(10, 11, 12, 13));
    assertThat(result.matchCount()).isEqualTo(3);
    assertThat(result.mismatchCount()).isEqualTo(1);
    assertThat(result.profileState()).isEqualTo(FormatProfileState.QUARANTINE);
    verifyNoInteractions(formatProfilePromotionService);
}

@Test
void allEligibleClusterReceiptsAndThreeDistinctMatchesMayPromote() {
    assertThat(service.evaluate(profileId, List.of(10, 11, 12)).profileState())
            .isEqualTo(FormatProfileState.ACTIVE);
}
```

- [ ] **Step 2: Run backend tests and verify failure**

Run: `cd backend && mvn -Dtest=AuditProfileRegressionServiceTests test`

- [ ] **Step 3: Implement complete-cluster evaluation**

Fetch every supplied document fresh by GET, require the same identity, compare the candidate with fresh Legacy/reference parsing, record idempotent evidence, and collect only sanitized diff codes/counts. Any `OCR_DIFFERENCE`, invalid reference, unresolved plausible line, required-field mismatch, item mismatch, or fewer than three distinct complete documents keeps the profile quarantined.

Call `FormatProfilePromotionService.evaluatePromotion(profileId)` only after the full set has zero relevant mismatch. Active-profile mismatch delegates to `ProfileRollbackService.suspendAndQueue` and never mutates the existing definition.

- [ ] **Step 4: Implement runner checkpointing**

`ProfileAuditService` processes one cluster at a time, stores profile IDs, versions, evidence counts, states, and fixed diff codes, then checkpoints. It emits `PROPOSE_PROFILE`, `WAIT_FOR_EVIDENCE`, `READY`, `ACTIVE`, `SUSPENDED`, or `NEEDS_DECISION`; no profile JSON is written locally.

- [ ] **Step 5: Run affected verification**

Run:

```bash
cd backend && mvn -Dtest=AuditProfileCandidateServiceTests,AuditProfileRegressionServiceTests,FormatProfilePromotionServiceTests,ProfileRollbackServiceTests,AuditApiContractTests test
cd ../audit-runner && npm test -- profile-audit-service.test.ts profile-work-queue.test.ts && npm run build
```

Expected: profile lifecycle and runner tests PASS; OpenRouter mock receives zero audit calls.

- [ ] **Step 6: Commit profile regression integration**

```bash
git add backend/src/main/java/de/ebon/audit backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/audit backend/src/test/java/de/ebon/api audit-runner/src/profiles audit-runner/src/work audit-runner/src/main.ts
git commit -m "feat(audit): verify profiles against complete clusters"
```

### Task 5: Run the parser/profile completion gate

**Files:**
- Modify: `docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md`
- Test: all affected backend/runner suites

**Interfaces:**
- Produces: a verified Milestone C parser/profile boundary for the product audit.

- [ ] **Step 1: Run full affected gates**

```bash
cd backend && mvn verify
cd ../audit-runner && npm test && npm run build
cd .. && docker compose config
docker compose --profile audit build audit backend
git diff --check
git status --short
```

- [ ] **Step 2: Run a synthetic mocked cluster smoke**

Use three matching documents plus one mismatching document against mocked Paperless. Confirm the matching cluster activates only when every eligible receipt matches, the mismatching cluster remains quarantined, and no request reaches OpenRouter.

- [ ] **Step 3: Record verified evidence and commit**

Update only the master-plan milestone with exact commands, test counts, and commit IDs. Do not include private fixture content.

```bash
git add docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md
git commit -m "docs(audit): record parser profile milestone"
```
