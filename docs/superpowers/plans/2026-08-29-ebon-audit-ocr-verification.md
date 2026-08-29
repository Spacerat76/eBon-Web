# eBon Audit OCR and Source Verification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add temporary local PDF/image OCR and a sanitized deterministic eBon parse-preview API so Paperless text can be structurally verified without replacement, persistence, or OpenRouter.

**Architecture:** The audit container invokes pinned Tesseract/Poppler binaries in a per-document temporary directory and holds OCR text only in memory. A protected backend endpoint parses Paperless or OCR text through current profile-aware and Legacy parsers without AI, returning only identity, totals, counts, trace classes, and sanitized error codes; the runner applies deterministic OCR gates and stores only comparison status/counters.

**Tech Stack:** Node.js 24.16.0, TypeScript 6.0.3, Tesseract OCR (`deu+eng`), Poppler, Java 25, Spring Boot 4.0.6, JUnit 5, Mockito, Vitest.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- Paperless text remains primary and is never overwritten automatically.
- OCR text, rendered pages, and originals are temporary and deleted on success and every failure path.
- The OCR command receives fixed arguments, never shell-interpolated receipt data.
- Audit parse preview calls `ProfileAwareReceiptParser` and `RuleBasedReceiptParser` directly; it never invokes `ReceiptParserService`, AI fallback, categorization, product assignment, or persistence appliers.
- API responses contain no raw text, descriptions, extracted field values, profile regex, stack trace, prompt, or model response.
- Original/OCR byte and page ceilings fail closed with sanitized codes.
- Tests use synthetic fixtures and mocks only.

---

### Task 1: Add the deterministic audit parse-preview API

**Files:**
- Create: `backend/src/main/java/de/ebon/audit/AuditTextSource.java`
- Create: `backend/src/main/java/de/ebon/audit/AuditParsePreviewService.java`
- Create: `backend/src/main/java/de/ebon/api/AuditController.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditParsePreviewRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditParseSnapshotDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditParsePreviewDto.java`
- Test: `backend/src/test/java/de/ebon/audit/AuditParsePreviewServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/AuditApiContractTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: `AuditParsePreviewDto AuditParsePreviewService.preview(AuditParsePreviewRequest request)`.
- Produces: protected `POST /api/audit/parse-preview`.
- Consumes: `ReceiptTextNormalizer`, `ReceiptFormatIdentifier`, `ProfileAwareReceiptParser`, and `RuleBasedReceiptParser` only.

- [ ] **Step 1: Write failing service tests proving no AI path**

```java
@Test
void previewReturnsSanitizedCurrentAndLegacySnapshotsWithoutPersistenceOrAi() {
    AuditParsePreviewDto result = service.preview(new AuditParsePreviewRequest(
            970, AuditTextSource.PAPERLESS, VALID_RECEIPT));

    assertThat(result.paperlessDocumentId()).isEqualTo(970);
    assertThat(result.current().itemCount()).isEqualTo(2);
    assertThat(result.current().lineClassCounts()).containsEntry(ParseLineType.POSITION, 2);
    assertThat(result.toString()).doesNotContain("Geheime Artikelzeile");
    verifyNoInteractions(aiReceiptParsingClient, receiptRepository, receiptItemRepository);
}
```

Also test invalid required fields, `PARSE_REVIEW`, current-profile failure despite Legacy success, max request length, Bearer authentication, and DTO serialization without `rawText`.

- [ ] **Step 2: Run focused tests and confirm failure**

Run: `cd backend && mvn -Dtest=AuditParsePreviewServiceTests,AuditApiContractTests test`

Expected: FAIL because audit DTOs, service, and endpoint are absent.

- [ ] **Step 3: Define the closed request and response**

```java
public record AuditParsePreviewRequest(
        @NotNull @Positive Integer paperlessDocumentId,
        @NotNull AuditTextSource source,
        @NotBlank @Size(max = 2_000_000) String rawText) {}

public record AuditParseSnapshotDto(
        ParseStatus parseStatus,
        ParseSource parseSource,
        String storeName,
        String storeBranch,
        LocalDate receiptDate,
        BigDecimal totalAmount,
        BigDecimal itemTotal,
        int itemCount,
        Map<ParseLineType, Integer> lineClassCounts,
        List<Integer> unresolvedLineNumbers,
        Long appliedProfileId,
        Integer appliedProfileVersion,
        String errorCode) {}

public record AuditParsePreviewDto(
        Integer paperlessDocumentId,
        AuditTextSource source,
        String storeNameKey,
        String storeBranchKey,
        String fingerprint,
        int fingerprintVersion,
        AuditParseSnapshotDto current,
        AuditParseSnapshotDto legacy) {}
```

Map free-form parser errors to a fixed code enum/string prefix. Do not expose `ParsedReceiptItem.description`, trace `extractedFields`, or exception messages.

- [ ] **Step 4: Implement deterministic parsing only**

Normalize and identify once. Call `profileAwareReceiptParser.parse(rawText)` for `current` and `ruleBasedReceiptParser.parse(rawText)` for `legacy`. Sum item totals with the same rounding semantics as `ReceiptParseValidator`; classify empty trace lists as `LEGACY_TRACE_UNAVAILABLE`, not as verified line coverage.

Mark the controller `@Validated`, keep Bearer protection through existing security configuration, and ensure request logging does not log bodies.

- [ ] **Step 5: Run tests and backend compile**

Run: `cd backend && mvn -Dtest=AuditParsePreviewServiceTests,AuditApiContractTests test`

Expected: all focused tests PASS and Mockito proves zero AI/persistence calls.

- [ ] **Step 6: Commit the deterministic API**

```bash
git add ebon-specification.md backend/src/main/java/de/ebon/audit backend/src/main/java/de/ebon/api/AuditController.java backend/src/main/java/de/ebon/api/dto/AuditParse* backend/src/test/java/de/ebon/audit backend/src/test/java/de/ebon/api/AuditApiContractTests.java
git commit -m "feat(audit): expose deterministic parse preview"
```

### Task 2: Implement temporary Tesseract/Poppler OCR

**Files:**
- Create: `audit-runner/src/ocr/ocr-engine.ts`
- Create: `audit-runner/src/ocr/process-runner.ts`
- Create: `audit-runner/src/ocr/tesseract-ocr-engine.ts`
- Create: `audit-runner/src/ocr/temp-workspace.ts`
- Test: `audit-runner/src/ocr/tesseract-ocr-engine.test.ts`
- Test: `audit-runner/src/ocr/temp-workspace.test.ts`
- Modify: `audit-runner/Dockerfile`

**Interfaces:**
- Produces: `OcrEngine.recognize(original: PaperlessOriginal): Promise<OcrResult>`.
- Produces: `OcrResult` as `{ text: string; pageCount: number; engine: "tesseract"; language: "deu+eng"; textHash: string }` held in memory only.
- Consumes: `pdftoppm` and `tesseract` through `ProcessRunner.spawn(binary, args, options)` without a shell.

- [ ] **Step 1: Write failing cleanup and fixed-argument tests**

```ts
it.each(["success", "tesseract-failure", "timeout"])("removes every temporary file on %s", async mode => {
  processRunner.mode = mode;
  await engine.recognize(syntheticPdf()).catch(() => undefined);
  expect(await listAuditTempEntries()).toEqual([]);
});

it("never passes file-derived text through a shell", async () => {
  await engine.recognize(syntheticImage("receipt;touch-pwned.png"));
  expect(processRunner.calls).toSatisfyAll(call => call.shell === false);
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- tesseract-ocr-engine.test.ts temp-workspace.test.ts`

Expected: FAIL because OCR components do not exist.

- [ ] **Step 3: Implement bounded OCR**

For PDFs, run:

```text
pdfinfo input.pdf
pdftoppm -r 300 -png -f 1 -l <boundedPageCount> input.pdf page
tesseract page-001.png stdout -l deu+eng --psm 6
```

For PNG/JPEG/TIFF originals, run Tesseract directly. Reject unsupported media, encrypted PDFs, page counts above `AUDIT_MAX_OCR_PAGES_PER_DOCUMENT`, process timeouts, and stdout above `AUDIT_MAX_OCR_TEXT_BYTES`. Keep stdout in memory and compute its hash before returning.

- [ ] **Step 4: Guarantee cleanup**

Use `mkdtemp` under the OS temp directory and a `finally` block with `rm(workspace, { recursive: true, force: true })`. Register startup cleanup only for directories carrying the runner's fixed prefix and older than the configured stale threshold; resolve and verify every candidate remains under `os.tmpdir()` before removal.

- [ ] **Step 5: Pin OCR runtime dependencies**

Extend `audit-runner/Dockerfile` on `node:24.16.0-bookworm-slim` with `tesseract-ocr`, `tesseract-ocr-deu`, `tesseract-ocr-eng`, and `poppler-utils`. Capture `tesseract --version` and `pdftoppm -v` in the image smoke test and document that Debian Bookworm package versions are pinned by the base image snapshot used in the lock/build evidence.

- [ ] **Step 6: Run focused tests and image smoke**

Run:

```bash
cd audit-runner && npm test -- tesseract-ocr-engine.test.ts temp-workspace.test.ts && npm run build
cd .. && docker compose --profile audit build audit
docker compose --profile audit run --rm audit ocr-version
```

Expected: synthetic PDF/image tests PASS; output shows Tesseract and Poppler versions but no receipt text.

- [ ] **Step 7: Commit OCR engine**

```bash
git add audit-runner/src/ocr audit-runner/Dockerfile
git commit -m "feat(audit): add temporary local OCR"
```

### Task 3: Add deterministic OCR gates and structural comparison

**Files:**
- Create: `audit-runner/src/ebon/ebon-client.ts`
- Create: `audit-runner/src/verification/ocr-trigger.ts`
- Create: `audit-runner/src/verification/parse-comparator.ts`
- Create: `audit-runner/src/verification/source-verification.ts`
- Test: `audit-runner/src/verification/ocr-trigger.test.ts`
- Test: `audit-runner/src/verification/parse-comparator.test.ts`
- Test: `audit-runner/src/verification/source-verification.test.ts`

**Interfaces:**
- Produces: `EbonClient.previewParse(documentId, source, rawText): Promise<AuditParsePreview>`.
- Produces: `OcrTrigger.evaluate(document, paperlessPreview, clusterSampleCount): OcrTriggerDecision`.
- Produces: `compareSources(primary, ocr): SourceComparison`.
- Produces: `SourceVerificationStatus = VERIFIED | VERIFIED_WITH_OCR | OCR_DIFFERENCE | PARSE_REVIEW | SOURCE_UNAVAILABLE`.
- `OcrTriggerDecision = { required: boolean; reasons: OcrReasonCode[] }`, where `OcrReasonCode` is a closed string union declared in `ocr-trigger.ts`.
- `SourceComparison = { status: SourceVerificationStatus; relevantDifference: boolean; diffCodes: SourceDiffCode[]; counters: SourceComparisonCounters }`, with closed diff-code and numeric-counter types declared in `parse-comparator.ts`.

- [ ] **Step 1: Write failing gate matrix tests**

```ts
it.each([
  [preview({ itemCount: 0 }), "NO_ITEMS"],
  [preview({ totalAmount: null }), "MISSING_TOTAL"],
  [preview({ unresolvedLineNumbers: [7] }), "UNRESOLVED_LINES"],
  [preview({ storeBranchKey: "" }), "MISSING_BRANCH_EVIDENCE"]
])("requests OCR for %s", (input, reason) => {
  expect(trigger.evaluate(document(), input, 3).reasons).toContain(reason);
});

it("accepts price/date formatting noise but rejects item-count or amount differences", () => {
  expect(compareSources(primary(), ocrEquivalent())).toMatchObject({ relevantDifference: false });
  expect(compareSources(primary(), ocrWithMissingItem())).toMatchObject({ relevantDifference: true });
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- ocr-trigger.test.ts parse-comparator.test.ts source-verification.test.ts`

Expected: FAIL because the verification pipeline is absent.

- [ ] **Step 3: Implement the eBon client origin boundary**

Send `Authorization: Bearer <APP_API_TOKEN>` only to the exact configured eBon origin. `previewParse` may send raw text in the request body but never logs it, caches it, attaches it to errors, or returns it from the client wrapper.

- [ ] **Step 4: Implement OCR trigger rules**

Trigger on missing/short Paperless text, missing required fields/items, total difference above `0.02`, unresolved lines, identity conflict, layout drift, missing branch evidence, or the first three distinct documents in a new cluster. Deduplicate reasons and enforce per-document/per-run OCR page limits before download/rendering.

- [ ] **Step 5: Compare structured values only**

Normalize merchant/branch keys, dates, `BigDecimal` strings, item counts/totals, and line-class counts. Do not compare or retain raw descriptions. Emit fixed diff codes such as `STORE_MISMATCH`, `BRANCH_MISMATCH`, `DATE_MISMATCH`, `TOTAL_MISMATCH`, `ITEM_COUNT_MISMATCH`, `ITEM_TOTAL_MISMATCH`, and `UNRESOLVED_LINE_MISMATCH`.

- [ ] **Step 6: Run tests and commit**

Run: `cd audit-runner && npm test -- ocr-trigger.test.ts parse-comparator.test.ts source-verification.test.ts && npm run build`

```bash
git add audit-runner/src/ebon audit-runner/src/verification
git commit -m "feat(audit): verify Paperless text with local OCR"
```

### Task 4: Integrate source verification with state and privacy checks

**Files:**
- Modify: `audit-runner/src/state/audit-state.ts`
- Modify: `audit-runner/src/inventory/invalidation.ts`
- Modify: `audit-runner/src/main.ts`
- Create: `audit-runner/src/privacy/private-data-guard.ts`
- Test: `audit-runner/src/privacy/private-data-guard.test.ts`
- Test: `audit-runner/src/verification/source-verification.integration.test.ts`

**Interfaces:**
- Adds document fields: `sourceVerificationStatus`, `ocrReasonCodes`, `ocrPageCount`, `ocrTextHash`, `sourceDiffCodes`, and sanitized parse counters.
- Adds CLI command: `verify-sources`.
- Produces: `PrivateDataGuard.assertSerializable(value)` before every state/report/history write.

- [ ] **Step 1: Write failing persistence-privacy tests**

```ts
it("refuses raw receipt-shaped fields before serialization", () => {
  expect(() => guard.assertSerializable({ rawText: "MILCH 1,99" })).toThrow("PRIVATE_FIELD_FORBIDDEN");
});

it("persists hashes and diff codes but no source text", async () => {
  await verifier.verify(documentWithPrivateText());
  const serialized = await readFile(statePath, "utf8");
  expect(serialized).toContain("OCR_DIFFERENCE");
  expect(serialized).not.toContain("MILCH 1,99");
});
```

- [ ] **Step 2: Run the tests and verify failure**

Run: `cd audit-runner && npm test -- private-data-guard.test.ts source-verification.integration.test.ts`

- [ ] **Step 3: Implement state integration and fail-closed serialization**

Permit only declared audit-state keys and scalar/count/hash/code values. Explicitly reject keys matching `raw`, `content`, `ocrText`, `prompt`, `response`, `token`, `authorization`, `bytes`, and `original` except the approved `originalHash` scalar.

Checkpoint after each document. A missing original becomes `SOURCE_UNAVAILABLE`; an OCR failure records a fixed code and continues. A relevant source difference becomes `OCR_DIFFERENCE`; no mutation or product phase is scheduled.

- [ ] **Step 4: Verify all affected surfaces**

Run:

```bash
cd backend && mvn -Dtest=AuditParsePreviewServiceTests,AuditApiContractTests test
cd ../audit-runner && npm test && npm run build
cd .. && docker compose config
docker compose --profile audit build audit backend
git diff --check
```

Expected: all checks PASS; a synthetic smoke run leaves no files outside the state/history/lock contract and reports zero OpenRouter calls.

- [ ] **Step 5: Commit source-verification integration**

```bash
git add audit-runner/src backend/src ebon-specification.md docker-compose.yml
git commit -m "feat(audit): checkpoint sanitized source verification"
```
