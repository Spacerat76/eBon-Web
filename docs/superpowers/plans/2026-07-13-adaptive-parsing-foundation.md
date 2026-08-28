# Adaptive Parsing Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a deterministic format-profile interpreter, stable store/branch fingerprints, complete line traces, and `PARSE_REVIEW` without regressing the existing receipt corpus.

**Architecture:** Normalize OCR text into immutable numbered lines, identify store/branch/layout, select a versioned profile, and interpret a closed JSON schema into the existing parsed-receipt model plus trace metadata. Keep the Legacy parser as fallback, but fix dynamic item rules so they supplement partial parses.

**Tech Stack:** Java 21, Spring Boot, JPA, PostgreSQL/Flyway, Jackson, JUnit 5, Mockito, Testcontainers, Maven.

**Implementation status (2026-08-29):** Tasks 1–7 completed on `codex`; verified source commit `a1ce32a22baa8d1c8f75e740be2bc98466dbcf6a`. Independent task, whole-foundation and scoped correction reviews are complete with no open Critical/Important findings. Final backend `clean verify`: 492 tests, zero failures/errors/skips; unchanged frontend: 15 focused tests and build passed; isolated Compose rebuild and 10 runtime smoke assertion groups passed. No live Paperless/OpenRouter calls. The historical checklists below describe the executed plan, not pending reimplementation.

**Remaining boundaries:** Lifecycle/bootstrap/semantic-learning/UI-operations plans are not implemented by this milestone. The pre-existing Legacy `10 % Rabatt` tax-table misclassification remains deferred, as do non-blocking per-receipt trace-count query optimization and test-log noise. Profile coverage verifies captured/classified text, not arbitrary real-world semantics of a deliberately mapped description. Optional browser E2E was not run for this foundation milestone.

## Global Constraints

- Follow the master constraints in `docs/superpowers/plans/2026-07-13-adaptive-receipt-processing.md`.
- Migration numbers in this plan assume the current latest migration is `V30`; rebase the numbers only if another migration lands first.
- Profile definitions are immutable JSON values validated against schema version `1`.
- No profile code may execute arbitrary scripts.
- `PARSED` retains the existing sum tolerance `0.02`; `PARSE_REVIEW` is used when required fields are usable but relevant lines remain unresolved.

---

### Task 1: Persist profile definitions, traces, and extraction status

**Files:**
- Create: `backend/src/main/resources/db/migration/V31__add_receipt_format_profiles.sql`
- Create: `backend/src/main/java/de/ebon/persistence/model/FormatProfileScope.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/FormatProfileState.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/FormatProfileSource.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/ParseLineType.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/ExtractionStatus.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/ReceiptFormatProfile.java`
- Create: `backend/src/main/java/de/ebon/persistence/model/ReceiptParseTrace.java`
- Create: `backend/src/main/java/de/ebon/persistence/repository/ReceiptFormatProfileRepository.java`
- Create: `backend/src/main/java/de/ebon/persistence/repository/ReceiptParseTraceRepository.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/ParseStatus.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/Receipt.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/ReceiptItem.java`
- Test: `backend/src/test/java/de/ebon/persistence/MigrationAndRepositorySmokeTests.java`
- Test: `backend/src/test/java/de/ebon/persistence/model/PersistenceModelBehaviorTests.java`

**Interfaces:**
- Produces: `ReceiptFormatProfileRepository#findFirstByStateAndStoreNameKeyAndStoreBranchKeyAndFingerprintAndFingerprintVersionOrderByVersionDesc(...)`.
- Produces: `Receipt#getReceiptFormatProfile()`, `ReceiptItem#getExtractionStatus()`, and `ParseStatus.PARSE_REVIEW`.
- Consumes: existing `receipt`, `receipt_item`, and Flyway conventions.

- [ ] **Step 1: Write failing migration and model tests**

Add assertions that `PARSE_REVIEW` can be persisted, a profile version references its predecessor, only one active profile exists per normalized scope/fingerprint, and trace lines cascade with a receipt.

```java
assertThat(ParseStatus.valueOf("PARSE_REVIEW")).isEqualTo(ParseStatus.PARSE_REVIEW);
assertThat(profile.getState()).isEqualTo(FormatProfileState.QUARANTINE);
assertThat(item.getExtractionStatus()).isEqualTo(ExtractionStatus.CONFIRMED);
```

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,PersistenceModelBehaviorTests test`

Expected: compilation fails for missing profile/extraction types or Flyway smoke assertions fail because V31 is absent.

- [ ] **Step 3: Add the migration and minimal entities**

Use these exact enum contracts:

```java
public enum FormatProfileScope { STORE, BRANCH }
public enum FormatProfileState { QUARANTINE, ACTIVE, SUSPENDED, RETIRED }
public enum FormatProfileSource { AI_GENERATED, LEGACY_BOOTSTRAP, USER_CORRECTED }
public enum ParseLineType { POSITION, METADATA, PAYMENT, TOTAL, TAX, IGNORED_SAFE, UNRESOLVED }
public enum ExtractionStatus { CONFIRMED, NEEDS_REVIEW }
```

V31 must create `receipt_format_profile` and `receipt_parse_trace`, add `receipt.format_profile_id`, `receipt.format_profile_version`, and `receipt_item.extraction_status`, extend the parse-status check with `PARSE_REVIEW`, and enforce one `ACTIVE` row per `(scope, store_name_key, store_branch_key, fingerprint, fingerprint_version)` through a partial unique index.

- [ ] **Step 4: Run the focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=MigrationAndRepositorySmokeTests,PersistenceModelBehaviorTests test`

Expected: selected tests pass and Flyway reaches V31.

- [ ] **Step 5: Commit Task 1**

```bash
git add backend/src/main/resources/db/migration/V31__add_receipt_format_profiles.sql backend/src/main/java/de/ebon/persistence backend/src/test/java/de/ebon/persistence
git commit -m "feat(parser): add format profile persistence"
```

### Task 2: Normalize OCR lines and compute stable format identities

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/NormalizedReceiptLine.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/NormalizedReceiptDocument.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptTextNormalizer.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptFormatIdentity.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptFormatIdentifier.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ReceiptTextNormalizerTests.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ReceiptFormatIdentifierTests.java`

**Interfaces:**
- Produces: `NormalizedReceiptDocument ReceiptTextNormalizer.normalize(String rawText)`.
- Produces: `ReceiptFormatIdentity ReceiptFormatIdentifier.identify(NormalizedReceiptDocument document)`.
- `ReceiptFormatIdentity` fields: `storeName`, `storeNameKey`, `storeBranch`, `storeBranchKey`, `fingerprint`, `fingerprintVersion`.

- [ ] **Step 1: Write failing normalization and fingerprint tests**

Use paired receipt texts with different dates, prices, Bon numbers, whitespace, and single-character OCR noise. Assert equal fingerprints for the same layout and different fingerprints when the item-table/footer structure changes.

```java
assertThat(identifier.identify(normalizer.normalize(first)).fingerprint())
        .isEqualTo(identifier.identify(normalizer.normalize(second)).fingerprint());
assertThat(normalizer.normalize(first).lines().getFirst().originalLineNumber()).isEqualTo(1);
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ReceiptTextNormalizerTests,ReceiptFormatIdentifierTests test`

Expected: compilation fails because the profile normalization package is absent.

- [ ] **Step 3: Implement canonicalization and SHA-256 fingerprint version 1**

The fingerprint input must remove values but retain structural tokens:

```java
String structural = normalizedLines.stream()
        .map(line -> line.matchText()
                .replaceAll("\\b\\d{1,2}[./-]\\d{1,2}[./-]\\d{2,4}\\b", "<DATE>")
                .replaceAll("[-+]?\\d+[.,]\\d{2}", "<AMOUNT>")
                .replaceAll("\\b\\d{4,}\\b", "<ID>"))
        .map(this::lineShape)
        .collect(Collectors.joining("\n"));
```

Keep original text and one-based line numbers; do not persist raw text in the identity object.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ReceiptTextNormalizerTests,ReceiptFormatIdentifierTests test`

Expected: selected tests pass, including OCR-stability and layout-separation cases.

- [ ] **Step 5: Commit Task 2**

```bash
git add backend/src/main/java/de/ebon/parser/profile backend/src/test/java/de/ebon/parser/profile
git commit -m "feat(parser): identify stable receipt formats"
```

### Task 3: Define and validate the closed profile schema

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptFormatDefinition.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileAnchor.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileFieldRule.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileItemRule.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileLineRule.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptFormatDefinitionCodec.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptFormatDefinitionValidator.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ReceiptFormatDefinitionValidatorTests.java`

**Interfaces:**
- Produces: `ReceiptFormatDefinition ReceiptFormatDefinitionCodec.read(String json)`.
- Produces: `String ReceiptFormatDefinitionCodec.write(ReceiptFormatDefinition definition)`.
- Produces: `ProfileValidationResult ReceiptFormatDefinitionValidator.validate(ReceiptFormatDefinition definition, NormalizedReceiptDocument example)`.

- [ ] **Step 1: Write failing schema tests**

Cover schema version `1`, unknown fields, missing anchors, invalid capture groups, regex length over `1024`, unsafe payment/total collisions, and a valid multiline item rule.

```java
ProfileValidationResult result = validator.validate(definition, document);
assertThat(result.valid()).isTrue();
assertThat(result.errors()).isEmpty();
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ReceiptFormatDefinitionValidatorTests test`

Expected: compilation fails for missing schema records and validator.

- [ ] **Step 3: Implement records, strict Jackson codec, and validation**

Use a closed top-level record:

```java
public record ReceiptFormatDefinition(
        int schemaVersion,
        List<ProfileAnchor> anchors,
        List<ProfileFieldRule> fields,
        List<ProfileItemRule> itemRules,
        List<ProfileLineRule> lineRules) {
    public ReceiptFormatDefinition {
        anchors = List.copyOf(anchors == null ? List.of() : anchors);
        fields = List.copyOf(fields == null ? List.of() : fields);
        itemRules = List.copyOf(itemRules == null ? List.of() : itemRules);
        lineRules = List.copyOf(lineRules == null ? List.of() : lineRules);
    }
}
```

Configure the dedicated reader to fail on unknown properties. Compile regexes during validation and reject item matches against known total, tax, TSE, and payment markers.

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ReceiptFormatDefinitionValidatorTests test`

Expected: valid profile passes; every unsafe/unknown case returns a structured validation error.

- [ ] **Step 5: Commit Task 3**

```bash
git add backend/src/main/java/de/ebon/parser/profile backend/src/test/java/de/ebon/parser/profile/ReceiptFormatDefinitionValidatorTests.java
git commit -m "feat(parser): validate declarative format profiles"
```

### Task 4: Interpret profiles and produce complete line traces

**Files:**
- Create: `backend/src/main/java/de/ebon/parser/profile/ParsedLineTrace.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileInterpretationResult.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileParseOutcome.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ReceiptFormatProfileInterpreter.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileParseQualityGate.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ReceiptFormatProfileInterpreterTests.java`
- Test: `backend/src/test/resources/corpus/profile/format_profile_multiline.txt`
- Test: `backend/src/test/resources/corpus/profile/format_profile_multiline.profile.json`
- Test: `backend/src/test/resources/corpus/profile/format_profile_multiline.expected.json`
- Test: `backend/src/test/resources/corpus/profile/format_profile_unresolved.txt`
- Test: `backend/src/test/resources/corpus/profile/format_profile_unresolved.profile.json`
- Test: `backend/src/test/resources/corpus/profile/format_profile_unresolved.expected.json`

**Interfaces:**
- Produces: `ProfileInterpretationResult interpret(ReceiptFormatDefinition definition, NormalizedReceiptDocument document)`.
- Produces: `ProfileParseOutcome validate(ProfileInterpretationResult result)` containing the `ReceiptParseResult` and immutable `List<ParsedLineTrace>`.

- [ ] **Step 1: Write failing interpreter and quality-gate tests**

Assert one classification per relevant line, correct multiline item merge, contiguous indices, `PARSED` for complete coverage, and `PARSE_REVIEW` when one price-like line is `UNRESOLVED`. Each receipt text gets a matching `.expected.json`; the profile interpreter parameterized test consumes the receipt text, profile JSON, and expected JSON together.

```java
assertThat(result.traces()).extracting(ParsedLineTrace::lineNumber).doesNotHaveDuplicates();
assertThat(qualityGate.validate(result).parseResult().parseStatus()).isEqualTo(ParseStatus.PARSE_REVIEW);
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ReceiptFormatProfileInterpreterTests test`

Expected: compilation fails because interpreter and trace types are missing.

- [ ] **Step 3: Implement deterministic interpretation and coverage rules**

Use ordered rule evaluation: required anchors, field rules, item-region rules, explicit line classifiers, then unresolved detection. Never classify a price-like line as `IGNORED_SAFE` unless an explicit validated rule matches it.

```java
ParseStatus status = unresolvedRelevantLines.isEmpty()
        ? receiptValidator.validate(parsedReceipt).parseStatus()
        : requiredFieldsUsable(parsedReceipt) ? ParseStatus.PARSE_REVIEW : ParseStatus.PARSE_ERROR;
```

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=ReceiptFormatProfileInterpreterTests test`

Expected: both new profile fixtures pass with the intended status and trace coverage.

- [ ] **Step 5: Commit Task 4**

```bash
git add backend/src/main/java/de/ebon/parser/profile backend/src/test/java/de/ebon/parser/profile backend/src/test/resources/corpus/profile
git commit -m "feat(parser): interpret receipt format profiles"
```

### Task 5: Fix Legacy partial-rule composition and route active profiles

**Files:**
- Modify: `backend/src/main/java/de/ebon/parser/RuleBasedReceiptParser.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/ProfileAwareReceiptParser.java`
- Create: `backend/src/main/java/de/ebon/parser/profile/AppliedProfile.java`
- Modify: `backend/src/main/java/de/ebon/parser/ReceiptParserService.java`
- Modify: `backend/src/main/java/de/ebon/parser/ReceiptParseResult.java`
- Modify: `backend/src/main/java/de/ebon/parser/ReceiptParseApplier.java`
- Test: `backend/src/test/java/de/ebon/parser/RuleBasedReceiptParserEdgeTests.java`
- Test: `backend/src/test/java/de/ebon/parser/profile/ProfileAwareReceiptParserTests.java`
- Test: `backend/src/test/java/de/ebon/parser/ReceiptParseApplierTests.java`

**Interfaces:**
- Produces: `ProfileAwareReceiptParser#parse(String rawText)` returning the existing receipt result plus optional profile metadata/traces.
- Adds: `AppliedProfile(Long profileId, int version, FormatProfileScope scope, String fingerprint)`.
- Consumes: `ReceiptFormatProfileRepository`, normalizer, identifier, codec, interpreter, and Legacy parser.

- [ ] **Step 1: Add failing tests for partial dynamic rules and routing**

Create a receipt where the generic parser finds one item and an accepted dynamic rule finds a second item. Assert both appear once. Add routing cases for branch profile, store profile, and Legacy fallback.

```java
assertThat(parser.parse(rawText).receipt().items())
        .extracting(ParsedReceiptItem::description)
        .containsExactly("Generic item", "Dynamic item");
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=RuleBasedReceiptParserEdgeTests,ProfileAwareReceiptParserTests,ReceiptParseApplierTests test`

Expected: partial-rule assertion fails because `parseDynamicItems` currently runs only when `items.isEmpty()`; profile routing types are missing.

- [ ] **Step 3: Implement deduplicating composition and profile routing**

Replace the zero-item fallback with composition keyed by normalized description, total, quantity, and source line. Route profiles in this order: branch+fingerprint, store+fingerprint, Legacy. Extend `ReceiptParseResult` with immutable optional `AppliedProfile` and trace list while retaining compatibility constructors.

```java
List<ParsedReceiptItem> combined = new ArrayList<>(items);
for (ParsedReceiptItem candidate : parseDynamicItems(lines, storeName)) {
    if (combined.stream().noneMatch(existing -> sameParsedItem(existing, candidate))) {
        combined.add(reindex(candidate, combined.size()));
    }
}
return combined;
```

- [ ] **Step 4: Run focused tests and confirm GREEN**

Run: `cd backend && mvn -Dtest=RuleBasedReceiptParserEdgeTests,ProfileAwareReceiptParserTests,ReceiptParseApplierTests test`

Expected: profile precedence, Legacy fallback, trace persistence, and partial dynamic-rule composition all pass.

- [ ] **Step 5: Commit Task 5**

```bash
git add backend/src/main/java/de/ebon/parser backend/src/test/java/de/ebon/parser
git commit -m "fix(parser): compose partial dynamic item rules"
```

### Task 6: Propagate review/profile metadata through backend contracts

**Files:**
- Modify: `backend/src/main/java/de/ebon/api/dto/ReceiptDto.java`
- Modify: `backend/src/main/java/de/ebon/api/dto/ReceiptItemDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/ParseTraceLineDto.java`
- Modify: `backend/src/main/java/de/ebon/api/service/ReceiptApiService.java`
- Modify: `backend/src/main/java/de/ebon/api/ReceiptsController.java`
- Test: `backend/src/test/java/de/ebon/api/ReceiptApiContractTests.java`
- Modify: `ebon-specification.md`

**Interfaces:**
- Produces: `GET /api/receipts/{id}/parse-trace` returning `List<ParseTraceLineDto>`.
- Extends: `ReceiptDto` with `formatProfileId`, `formatProfileVersion`, and `unresolvedLineCount`.
- Extends: `ReceiptItemDto` with `extractionStatus`.

- [ ] **Step 1: Write failing API contract tests**

Assert `PARSE_REVIEW` serializes, trace results contain no prompt/raw AI response, and unresolved items are marked `NEEDS_REVIEW`.

```java
mockMvc.perform(get("/api/receipts/{id}/parse-trace", receiptId).header(AUTHORIZATION, bearer()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].lineType").value("UNRESOLVED"));
```

- [ ] **Step 2: Run tests and confirm RED**

Run: `cd backend && mvn -Dtest=ReceiptApiContractTests test`

Expected: compilation or 404 failure for the new DTO/endpoint.

- [ ] **Step 3: Implement DTO mapping and update the specification**

Map trace text as the existing receipt line excerpt only; never return profile prompts or AI responses. Update F-02, ReceiptDTO, ReceiptItemDTO, endpoint tables, and acceptance criteria for `PARSE_REVIEW`, profile metadata, and extraction status.

- [ ] **Step 4: Run focused and full backend verification**

Run: `cd backend && mvn -Dtest=ReceiptApiContractTests test && mvn verify`

Expected: focused tests pass, then full backend build reports `BUILD SUCCESS`.

- [ ] **Step 5: Commit Task 6**

```bash
git add backend/src/main/java/de/ebon/api backend/src/test/java/de/ebon/api ebon-specification.md
git commit -m "feat(api): expose parse review traces"
```

### Task 7: Foundation verification gate

**Files:**
- No planned source changes; modify only defects demonstrated by verification.

**Interfaces:**
- Produces: a green, reviewable parser foundation for the lifecycle plan.
- Consumes: Tasks 1–6.

- [ ] **Step 1: Run parser-focused tests**

Run: `cd backend && mvn -Dtest='de.ebon.parser.**,de.ebon.persistence.**' test`

Expected: all selected tests pass; no real external calls occur.

- [ ] **Step 2: Run full backend verification**

Run: `cd backend && mvn verify`

Expected: `BUILD SUCCESS` with zero failures.

- [ ] **Step 3: Validate Docker configuration**

Run: `docker compose config`

Expected: exit code `0` and no missing required variable errors.

- [ ] **Step 4: Check formatting and repository scope**

Run: `git diff --check` and `git status --short`

Expected: no whitespace errors; only intentional foundation changes are present.

- [ ] **Step 5: Close the foundation gate**

If any verification command fails, return to the task that owns the failing behavior, add or tighten its regression test, fix that task, and repeat Steps 1–4. When all commands pass, create no empty verification commit.
