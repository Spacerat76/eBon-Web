# eBon Audit Skills Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Complete and validate the receipt skill before starting the product skill.

**Goal:** Add two small, interactive Codex skills with dependency-free Node scripts: one audits Paperless receipt parsing merchant by merchant, the other audits product-family assignments while protecting manual decisions.

**Architecture:** Each skill owns one `SKILL.md`, one `.mjs` CLI, and one built-in Node test file. Scripts perform REST pagination, grouping, temporary batch export, fresh-state guards, apply calls, and resumable sanitized progress. Codex inspects private batch content interactively and makes judgments; neither script embeds AI or calls OpenRouter.

**Tech Stack:** Markdown skills, Node.js 24 built-ins (`fetch`, `node:test`, `fs`, `crypto`), existing Paperless and eBon REST APIs, Docker.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- Add exactly one product-audit correction endpoint; add no database table, package dependency, Compose service, or other backend endpoint. Any further application-code or application-test change requires new user approval.
- Paperless requests are GET-only and use `Token`; eBon requests use `Authorization: Bearer <APP_API_TOKEN>`.
- No OpenRouter environment variable or request is accepted by the scripts.
- Persistent files under `var/ebon-codex-audit/` contain no receipt text, OCR text, position description, token, prompt, or model response.
- Temporary block files may contain private text and descriptions, are gitignored, and are deleted when the block is recorded or cleaned up.
- Reparse uses `overwriteManualEdits=false`, `useAiFallback=false`, and `rawTextSource=PAPERLESS`.
- Product apply re-reads the containing receipt immediately before mutation. Manual assignments require a decision carrying an explicit user-confirmation marker; Codex confidence alone never supplies it.
- Implement and behavior-test one skill fully before creating the next skill.

---

### Task 1: Create and validate `ebon-receipt-audit`

**Files:**
- Modify: `.gitignore`
- Modify: `AGENTS.md`
- Create: `.codex/skills/ebon-receipt-audit/SKILL.md`
- Create: `.codex/skills/ebon-receipt-audit/scripts/receipt-audit.mjs`
- Create: `.codex/skills/ebon-receipt-audit/scripts/receipt-audit.test.mjs`
- Test artifact (ignored): `.superpowers/skill-tests/ebon-receipt-audit-baseline.md`
- Test artifact (ignored): `.superpowers/skill-tests/ebon-receipt-audit-forward.md`

**CLI contract:**

```text
receipt-audit.mjs inventory --state <dir>
receipt-audit.mjs next --state <dir>
receipt-audit.mjs record --state <dir> --decisions <json>
receipt-audit.mjs cleanup --state <dir>
```

Configuration comes only from `PAPERLESS_BASE_URL`, `PAPERLESS_API_TOKEN`, `PAPERLESS_EBON_TAG`, `EBON_BASE_URL`, and `APP_API_TOKEN`. `inventory` persists a sanitized merchant/branch queue. `next` reparses one block deterministically and writes its private temporary JSON path to stdout. `record` accepts a closed decision document, updates sanitized progress atomically, and removes that block file. `cleanup` removes only temporary files below the resolved state directory.

**Interfaces:**

```js
export async function fetchAllPages({ firstUrl, fetchImpl, headers, allowedOrigin, maxPages = 1000 })
export function buildReceiptQueue(paperlessDocuments, ebonReceipts)
export async function prepareReceiptBlock({ stateDir, state, clients })
export async function recordReceiptDecisions({ stateDir, decisions })
export async function cleanupTemporaryFiles(stateDir)
```

`record` consumes this closed document; `status` is `VERIFIED`, `NEEDS_USER`, or `NO_SENSIBLE_PROPOSAL`:

```json
{
  "runId": "receipt-20260829T120000Z",
  "merchantKey": "rewe",
  "branchKey": "bahnhofstrasse-15",
  "receipts": [
    { "paperlessDocumentId": 970, "receiptId": 42, "status": "VERIFIED", "reasonCodes": [] }
  ]
}
```

- [ ] **Step 1: Run the skill RED scenario without the new skill**

Dispatch a fresh subagent with a realistic REWE batch containing one missing price line, one ambiguous discount, and pressure to fix immediately. Give it existing `ebon-parser` only. Record whether it audits every receipt before editing, asks before parser-code/test changes, protects manual edits, and leaves an unresolvable case open. Save the verbatim outcome and failures in the ignored baseline artifact.

- [ ] **Step 2: Write failing script tests**

Cover:

- all Paperless pages are required before replacing inventory;
- Paperless methods other than GET are rejected;
- merchants sort by total receipt count descending and branches within merchant by count;
- unmatched Paperless IDs are reported as `UNMATCHED_PAPERLESS`;
- `next` sends the exact no-AI/manual-safe reparse query;
- persistent JSON rejects private keys and values from batch content;
- `record` is resumable and deletes the recorded temporary block;
- cleanup cannot escape the configured state directory.

Run and confirm RED:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node --test .codex/skills/ebon-receipt-audit/scripts/receipt-audit.test.mjs
```

Expected: FAIL because `receipt-audit.mjs` is absent.

- [ ] **Step 3: Implement the minimal receipt CLI**

Export pure functions for URL/origin validation, complete pagination, normalization, grouping, private-field guarding, atomic state writes, and safe temporary cleanup. Execute CLI code only when the module is the process entrypoint. Follow same-origin `next` links only, cap pages/documents with explicit fixed errors, never log headers or bodies, and retry no mutations automatically.

Use this entrypoint shape so tests import mechanics without running the CLI:

```js
import { pathToFileURL } from "node:url";

export async function main(argv, env, fetchImpl = fetch) {
  const command = argv[0];
  if (!new Set(["inventory", "next", "record", "cleanup"]).has(command)) {
    throw new Error("UNKNOWN_COMMAND");
  }
  return runCommand(command, parseArgs(argv.slice(1)), env, fetchImpl);
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  main(process.argv.slice(2), process.env).catch(failWithoutPrivateData);
}
```

Match Paperless documents to eBon receipts by `paperlessDocumentId`. A missing local receipt remains a reported inventory problem; the script does not trigger sync or invent a receipt. The batch contains Paperless document ID/content, current receipt DTO, and parse trace only for the active block.

- [ ] **Step 4: Write the receipt skill from observed failures**

Keep `SKILL.md` under 500 words. It must route Codex through inventory, largest merchant/branch, whole-block inspection, error collection, obvious configurable-rule correction, whole-block regression, then interactive ambiguous cases. It must require user approval before parser source or test changes and allow an explicit `NO_SENSIBLE_PROPOSAL` outcome.

Add the skill to the `AGENTS.md` routing table and add `/var/ebon-codex-audit/` to `.gitignore`.

- [ ] **Step 5: Verify GREEN and forward behavior**

Run script tests, the skill validator, and the same fresh-agent scenario with the skill. Save the new result to the ignored forward artifact and confirm the baseline failures are corrected.

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node --test .codex/skills/ebon-receipt-audit/scripts/receipt-audit.test.mjs
& 'C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '.codex/skills/ebon-receipt-audit'
git diff --check
```

- [ ] **Step 6: Commit the deployed receipt skill**

```powershell
git add .gitignore AGENTS.md .codex/skills/ebon-receipt-audit
git commit -m "feat(skill): add interactive receipt audit"
```

---

### Task 2: Add the provenance-safe product-audit correction endpoint

**Files:**
- Modify: `ebon-specification.md`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductCorrectionRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditExpectedProductAssignment.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductVariantRequest.java`
- Create: `backend/src/main/java/de/ebon/api/dto/AuditProductCorrectionResponse.java`
- Create: `backend/src/main/java/de/ebon/product/AuditProductCorrectionService.java`
- Modify: `backend/src/main/java/de/ebon/api/ProductsController.java`
- Modify: `backend/src/main/java/de/ebon/persistence/repository/ReceiptItemRepository.java`
- Test: `backend/src/test/java/de/ebon/product/AuditProductCorrectionServiceTests.java`
- Modify: `backend/src/test/java/de/ebon/api/ProductsApiContractTests.java`

**Interfaces:**
- Produces: `AuditProductCorrectionResponse AuditProductCorrectionService.correct(Long receiptItemId, AuditProductCorrectionRequest request)`.
- Produces: protected `POST /api/products/review/{receiptItemId}/audit-correct`.
- No entity is exposed and no persistence schema changes.

```java
public record AuditProductCorrectionRequest(
        @NotNull @Valid AuditExpectedProductAssignment expected,
        Long productFamilyId,
        @Size(max = 255) String newProductFamilyName,
        Long productVariantId,
        @Valid AuditProductVariantRequest newProductVariant,
        @NotNull @DecimalMin("0.980") @DecimalMax("1.000") BigDecimal confidence,
        @NotBlank @Pattern(regexp = "[A-Z0-9_]{1,64}") String reasonCode) {}
```

`AuditExpectedProductAssignment` contains nullable family ID, variant ID, source, and status; the enclosing object itself is mandatory so an omitted precondition cannot accidentally mean four expected nulls. Exactly one of `productFamilyId` and `newProductFamilyName` is required. Exactly zero or one of `productVariantId` and `newProductVariant` is allowed. `AuditProductVariantRequest` contains name, unit quantity/unit, package quantity/description, total quantity/unit, and GTIN with the same validation bounds as `ProductVariantRequest`, but no family ID.

`AuditProductCorrectionResponse` contains receipt item ID, resulting family/variant IDs, source, status, confidence, and `familyCreated`/`variantCreated`; it contains no description.

- [ ] **Step 1: Add the product-contract text and failing service/API tests**

Add F-19 behavior and the endpoint to `ebon-specification.md`. Tests must prove:

- existing and newly created families assign `AI + AUTO_ASSIGNED` with the submitted confidence;
- a nested new variant belongs to the chosen/new family and keeps size/package out of the family name;
- any current `MANUAL`, `CONFIRMED`, `NO_PRODUCT`, or `REJECTED` state returns conflict without mutation;
- a mismatched nullable expected tuple returns conflict;
- confidence below `0.980`, unknown/inactive targets, duplicate new family, variant/family mismatch, and invalid reason codes fail;
- the assignment log uses source `AI`, status `AUTO_ASSIGNED`, model `codex-interactive-audit`, and the fixed sanitized reason code;
- the service constructor has no OpenRouter/AI client dependency.

Run and confirm RED:

```powershell
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9-eclipse-temurin-25 mvn -Dtest=AuditProductCorrectionServiceTests,ProductsApiContractTests test
```

Expected: FAIL because the DTO, service, and route are absent.

- [ ] **Step 2: Implement the transactional correction service**

Lock/re-read the item using a repository method with `PESSIMISTIC_WRITE`, compare all four expected tuple fields null-safely, and reject protected states. Resolve or create the active family and optional variant inside the same transaction. Then assign and log exactly:

```java
item.assignProduct(family, variant, ProductAssignmentSource.AI,
        ProductAssignmentStatus.AUTO_ASSIGNED, request.confidence());
assignmentLogRepository.save(new ProductAssignmentLog(
        item, family, variant, ProductAssignmentSource.AI,
        ProductAssignmentStatus.AUTO_ASSIGNED, request.confidence(),
        "codex-interactive-audit", request.reasonCode()));
```

Map stale/protected/duplicate conflicts to HTTP `409` with fixed messages that contain no description. Do not call `ProductReviewService.correct`, because it intentionally records manual provenance.

- [ ] **Step 3: Wire the single endpoint and verify GREEN**

Inject `AuditProductCorrectionService` into `ProductsController` and expose only the route declared above. Re-run focused tests, then backend verification:

```powershell
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9-eclipse-temurin-25 mvn -Dtest=AuditProductCorrectionServiceTests,ProductsApiContractTests test
docker run --rm -v "${PWD}/backend:/app" -w /app maven:3.9-eclipse-temurin-25 mvn verify
```

- [ ] **Step 4: Commit the endpoint**

```powershell
git add ebon-specification.md backend/src/main/java/de/ebon/api/dto/AuditProductCorrectionRequest.java backend/src/main/java/de/ebon/api/dto/AuditExpectedProductAssignment.java backend/src/main/java/de/ebon/api/dto/AuditProductVariantRequest.java backend/src/main/java/de/ebon/api/dto/AuditProductCorrectionResponse.java backend/src/main/java/de/ebon/product/AuditProductCorrectionService.java backend/src/main/java/de/ebon/api/ProductsController.java backend/src/main/java/de/ebon/persistence/repository/ReceiptItemRepository.java backend/src/test/java/de/ebon/product/AuditProductCorrectionServiceTests.java backend/src/test/java/de/ebon/api/ProductsApiContractTests.java
git commit -m "feat(product): add provenance-safe audit correction"
```

---

### Task 3: Create and validate `ebon-product-audit`

**Files:**
- Modify: `AGENTS.md`
- Create: `.codex/skills/ebon-product-audit/SKILL.md`
- Create: `.codex/skills/ebon-product-audit/scripts/product-audit.mjs`
- Create: `.codex/skills/ebon-product-audit/scripts/product-audit.test.mjs`
- Test artifact (ignored): `.superpowers/skill-tests/ebon-product-audit-baseline.md`
- Test artifact (ignored): `.superpowers/skill-tests/ebon-product-audit-forward.md`

**CLI contract:**

```text
product-audit.mjs next --state <dir>
product-audit.mjs apply --state <dir> --decisions <json>
product-audit.mjs record-open --state <dir> --decisions <json>
product-audit.mjs cleanup --state <dir>
```

`next` reads the next receipt-verified block, current receipt details, active families, and variants into one private temporary JSON file. `apply` accepts a closed union of `ASSIGN_EXISTING`, `CREATE_FAMILY`, `CREATE_VARIANT`, and `NO_PRODUCT`. Non-manual correction actions use the Task 2 audit endpoint. A user-confirmed manual correction uses the existing manual review endpoint. `record-open` stores only IDs, decision status, confidence, and reason code for `PROPOSED`, `NO_SENSIBLE_PROPOSAL`, or `USER_CONFIRMATION_REQUIRED`.

**Interfaces:**

```js
export async function prepareProductBlock({ stateDir, receiptState, client })
export function validateProductDecision(decision, currentItem, families, variants)
export async function applyProductDecisions({ stateDir, decisionDocument, client })
export async function recordOpenProductDecisions({ stateDir, decisionDocument })
```

The apply document freezes the current assignment and permits one action per item:

```json
{
  "runId": "product-20260829T140000Z",
  "merchantKey": "rewe",
  "branchKey": "bahnhofstrasse-15",
  "decisions": [
    {
      "receiptId": 42,
      "receiptItemId": 4201,
      "expected": { "familyId": null, "variantId": null, "source": null, "status": null },
      "action": "ASSIGN_EXISTING",
      "familyId": 12,
      "variantId": null,
      "confidence": 0.99,
      "userConfirmedManual": false,
      "reasonCode": "UNIQUE_EXISTING_FAMILY"
    }
  ]
}
```

- [ ] **Step 1: Run the product skill RED scenario without the skill**

Dispatch a fresh subagent with a mixed block: obvious unassigned milk, a manual but clearly wrong family, an ambiguous branded item, and a new 500 g product. Record whether it preserves the manual assignment, requests confirmation for its correction, separates family from variant, applies only the obvious non-manual case, and permits no proposal.

- [ ] **Step 2: Write failing product-script tests**

Cover:

- only receipt-verified blocks enter the product queue;
- current families and variants are loaded before a batch is written;
- every apply re-reads the receipt and matches the expected assignment tuple;
- manual source refuses apply without `userConfirmedManual=true`;
- a confirmed manual correction is accepted only for the exact frozen item/target;
- a new family requires confidence `>= 0.98`, normalized-name uniqueness, and no sufficiently similar active family selected by Codex;
- size/package data uses a variant action, not a family-name suffix;
- a stale or partially failed multi-call family/variant action is recorded and stops further mutations;
- persistent progress contains no descriptions.

Run and confirm RED:

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node --test .codex/skills/ebon-product-audit/scripts/product-audit.test.mjs
```

- [ ] **Step 3: Implement minimal product batch and apply mechanics**

Use the receipt-audit progress as the only block source. Fetch `/api/receipts/{id}`, `/api/products/families`, and `/api/products/variants`. Apply non-manual decisions through `/api/products/review/{receiptItemId}/audit-correct`; use `/correct` only when the current conversation contains explicit user confirmation for that exact manual item and target. Do not create or apply broad/regex/contains rules.

Dispatch only after the frozen tuple and manual gate pass:

```js
const current = findItem(await client.getReceipt(decision.receiptId), decision.receiptItemId);
assertExpectedAssignment(current, decision.expected);
if (current.productAssignmentSource === "MANUAL" && decision.userConfirmedManual !== true) {
  throw new Error("USER_CONFIRMATION_REQUIRED");
}
await applyClosedAction(client, decision, current); // audit-correct, or manual correct only after confirmation
```

For non-manual `CREATE_FAMILY` and `CREATE_VARIANT`, send one request to the transactional audit endpoint. For a confirmed manual change, use the existing manual correction endpoint and its existing family behavior; if it also needs a new variant, create the variant only after the family exists and stop visibly on any second-step failure. No decision file may target more than the active block.

- [ ] **Step 4: Write the product skill from observed failures**

Keep `SKILL.md` under 500 words. Define the positive result shape for each item: `APPLIED`, `USER_CONFIRMATION_REQUIRED`, `PROPOSED`, or `NO_SENSIBLE_PROPOSAL`. Require fresh-state protection, the `0.98` new-family gate, variant separation, immediate correction only for obvious non-manual assignments, and explicit user approval for every manual change.

Add the product-audit trigger to `AGENTS.md` without duplicating domain rules already in `ebon-adaptive-processing`.

- [ ] **Step 5: Validate the complete product skill before moving on**

Run the product tests, both skill validators, and the fresh forward scenario. Confirm manual protection, new-family/variant handling, and open-case reporting.

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node --test .codex/skills/ebon-product-audit/scripts/product-audit.test.mjs
& 'C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '.codex/skills/ebon-receipt-audit'
& 'C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '.codex/skills/ebon-product-audit'
git diff --check
```

- [ ] **Step 6: Commit the deployed product skill**

```powershell
git add AGENTS.md .codex/skills/ebon-product-audit
git commit -m "feat(skill): add interactive product audit"
```

---

### Task 4: Verify the two-skill workflow and perform the read-only live inventory

**Files:**
- Modify only if the verification exposes a documentation defect: `.codex/skills/ebon-receipt-audit/SKILL.md`
- Modify only if the verification exposes a documentation defect: `.codex/skills/ebon-product-audit/SKILL.md`

**Interfaces:**
- Consumes both validated skill CLIs and `var/ebon-codex-audit/progress.json` from Task 1.
- Produces a read-only live inventory and fresh verification evidence; it introduces no new runtime interface.

- [ ] **Step 1: Run the complete local gate**

```powershell
docker run --rm -v "${PWD}:/workspace" -w /workspace node:24.16.0-alpine node --test .codex/skills/ebon-receipt-audit/scripts/receipt-audit.test.mjs .codex/skills/ebon-product-audit/scripts/product-audit.test.mjs
& 'C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '.codex/skills/ebon-receipt-audit'
& 'C:\Users\pbaas\.cache\codex-runtimes\codex-primary-runtime\dependencies\python\python.exe' 'C:\Users\pbaas\.codex\skills\.system\skill-creator\scripts\quick_validate.py' '.codex/skills/ebon-product-audit'
git diff --check
```

- [ ] **Step 2: Run only the live read-only inventory**

Run `receipt-audit.mjs inventory` with `.env` supplied to the Node container and an explicit `EBON_BASE_URL` reachable from Docker. Inspect the request log: Paperless traffic must contain GET only, no OpenRouter traffic may occur, and no raw content may appear in persistent progress or console output.

Do not run `next`, `record`, or either product mutation command in this verification step.

- [ ] **Step 3: Verify resumability and privacy**

Run unchanged inventory again. Confirm identical ordering, no duplicate state entries, and no private content outside the temporary directory. Run `git status --short` and verify `var/ebon-codex-audit/` is absent.

- [ ] **Step 4: Commit only evidence-driven skill corrections**

If live verification required a skill/script correction, repeat its focused RED-GREEN test and commit that correction. Otherwise create no empty verification commit.

## Completion Gate

- Both Node suites pass in the pinned Node container.
- Both skill directories pass `quick_validate.py`.
- `cd backend && mvn verify` passes for the product-audit endpoint.
- Independent forward scenarios demonstrate the approved interaction boundaries.
- Live Paperless inventory is read-only, complete, sorted, resumable, and private.
- `git diff --check` is clean.
- `git status --short` contains no generated audit state or private receipt data.
