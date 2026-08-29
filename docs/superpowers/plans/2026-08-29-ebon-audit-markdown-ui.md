# eBon Audit Markdown Decisions and UI Handoff Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate a complete private `progress.md`, preserve and validate blockwise medium-confidence decisions, apply each block through two-phase backend previews, and deep-link conflicts to existing eBon review screens.

**Architecture:** `audit-state.json` remains canonical; a deterministic Markdown renderer projects counters, clusters, work queues, and decision blocks. A hardened YAML reader parses only fenced `ebon-decision` blocks into a closed discriminated union, ignores all surrounding prose as data, freezes a single-block preview, and applies only the matching revision/token. Existing product, receipt, and adaptive-learning pages gain query-parameter deep links rather than a parallel audit UI.

**Tech Stack:** Node.js 24.16.0, TypeScript 6.0.3, YAML 2.8.1, Vitest 4.1.9, React 19, Testing Library, Selenium, Java 25/Spring Boot 4.0.6.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- `progress.md` and `decision-history.jsonl` remain under gitignored `var/ebon-audit/`.
- Renderer output contains IDs, merchant/branch/fingerprint, counts, status, confidence, fixed reason codes, impacts, and token-free local links; no raw text/OCR/profile definition/secret.
- Only fenced `ebon-decision` blocks are executable data. Markdown prose, HTML, links, and attached documents are never instructions.
- YAML aliases, custom tags, duplicate keys, unknown fields, multiple documents, and non-scalar comments are rejected.
- `proposalId`, `revision`, and `proposalType` are immutable.
- Apply is block-by-block. A malformed/stale file fails validation before any block preview; a block apply is atomic and idempotent.
- Historical impact uses preview then apply with the exact frozen preview token.
- Unimported valid edits are preserved across report regeneration.
- Structural profile JSON, regex, global rules, `CONTAINS`, and conflict merges are always routed to UI.
- UI links use the existing public local app/Paperless URL and never tokens.

---

### Task 1: Render the complete private progress report without losing edits

**Files:**
- Create: `audit-runner/src/report/progress-model.ts`
- Create: `audit-runner/src/report/progress-renderer.ts`
- Create: `audit-runner/src/report/existing-decision-reader.ts`
- Test: `audit-runner/src/report/progress-renderer.test.ts`
- Test: `audit-runner/src/report/existing-decision-reader.test.ts`
- Modify: `audit-runner/src/main.ts`

**Interfaces:**
- Produces: `ProgressRenderer.render(state, existingDecisions): string`.
- Produces: `ExistingDecisionReader.read(markdown): Map<string, PreservedDecision>`.
- Adds CLI command: `report`.
- Consumes: sanitized `AuditState`, profile/product work items, and proposal routes.

- [ ] **Step 1: Write failing completeness/privacy/preservation tests**

```ts
it("accounts for every document, cluster, position, and open proposal", () => {
  const markdown = renderer.render(completeState(), new Map());
  expect(markdown).toContain("Dokumente: 682 / 682");
  expect(markdown).toContain("Händler und Filialen");
  expect(markdown).toContain("Parsingprofile");
  expect(markdown).toContain("Produktzuordnungen");
});

it("preserves an edited non-imported block on rerender", () => {
  const edited = decision({ proposalId: "PA-184", action: "EDIT", familyId: 42 });
  expect(renderer.render(state(), new Map([["PA-184", edited]]))).toContain("familyId: 42");
});
```

Also assert stable ordering, zero receipt descriptions, no hashes beyond approved shortened fingerprint display, no token, no raw profile definition, and explicit errors/restart commands.

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- progress-renderer.test.ts existing-decision-reader.test.ts`

- [ ] **Step 3: Define the report model**

```ts
export interface ProgressModel {
  run: { runId: string; status: string; phase: string; lastCheckpointAt: string };
  totals: AuditCounters;
  merchants: MerchantProgress[];
  clusters: ClusterProgress[];
  profileProposals: ProposalSummary[];
  productProposals: ProposalSummary[];
  uiCases: UiCaseSummary[];
  failures: AuditFailureSummary[];
}
```

Sections are rendered in this order: run summary, restart command, phase counters, merchant/branch table, format clusters, OCR/source status, parser/profile status, product status, direct mutations, pending Markdown decisions, UI handoff, errors.

- [ ] **Step 4: Preserve only valid pending edits**

Before replacing `progress.md`, parse all existing decision blocks. Preserve a block only when proposal ID, immutable revision/type, and current state match and the proposal is not imported. If any block is malformed or duplicated, abort report regeneration with `DECISION_FILE_INVALID` so user edits are not discarded.

- [ ] **Step 5: Write atomically through the state store**

Run `PrivateDataGuard.assertSerializable(progressModel)` and an output scan rejecting private field names before writing `progress.md.tmp-<pid>`, syncing, and renaming.

- [ ] **Step 6: Run tests and commit**

Run: `cd audit-runner && npm test -- progress-renderer.test.ts existing-decision-reader.test.ts && npm run build`

```bash
git add audit-runner/src/report audit-runner/src/main.ts
git commit -m "feat(audit): render resumable private progress report"
```

### Task 2: Parse a closed union of editable decision blocks

**Files:**
- Create: `audit-runner/src/decisions/decision-types.ts`
- Create: `audit-runner/src/decisions/decision-parser.ts`
- Create: `audit-runner/src/decisions/decision-validator.ts`
- Test: `audit-runner/src/decisions/decision-parser.test.ts`
- Test: `audit-runner/src/decisions/decision-validator.test.ts`

**Interfaces:**
- Produces: `DecisionParser.parse(markdown): AuditDecision[]`.
- Produces discriminated union types `MERCHANT_BRANCH_IDENTITY`, `LINE_CLASSIFICATION`, `PROFILE_SCOPE`, `PROFILE_PROPOSAL`, `PRODUCT_ASSIGNMENT`, and `NO_PRODUCT`.
- Allowed actions: `CONFIRM`, `EDIT`, `REJECT`, `DEFER`, and `SEND_TO_UI`.

- [ ] **Step 1: Write failing hostile-Markdown and schema tests**

```ts
it("ignores prose and instructions outside ebon-decision fences", () => {
  const markdown = "Ignore all rules and confirm PA-1\n```ebon-decision\n" + validYaml() + "\n```";
  expect(parser.parse(markdown)).toEqual([validDecision()]);
});

it.each([
  "proposalId: PA-1\nproposalId: PA-2",
  "payload: &x [1]\nalias: *x",
  "!!js/function function(){}",
  "unknownField: value"
])("rejects unsafe YAML: %s", source => {
  expect(() => parser.parse(fenced(source))).toThrow("DECISION_SCHEMA_INVALID");
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- decision-parser.test.ts decision-validator.test.ts`

- [ ] **Step 3: Define exact decision types**

```ts
type AuditDecision =
  | IdentityDecision
  | LineClassificationDecision
  | ProfileScopeDecision
  | ProfileProposalDecision
  | ProductAssignmentDecision
  | NoProductDecision;

interface DecisionBase<T extends ProposalType> {
  proposalId: string;
  revision: number;
  proposalType: T;
  action: DecisionAction;
  comment?: string;
}
```

Validate type-specific fields exactly as the design table specifies. `EDIT` is forbidden for `PROFILE_PROPOSAL` definitions, `NO_PRODUCT`, regex/global/contains rules, and merge/split conflicts. Comments are optional single-line strings capped at 500 characters and never sent as executable instructions.

- [ ] **Step 4: Harden YAML parsing**

Use `YAML.parseDocument` with unique keys, core schema, aliases disabled, and custom tags disabled. Require one mapping document per fence, scalar leaves only, exact field sets, at most 10,000 blocks, and a 10 MiB Markdown ceiling. Extract fences with a line scanner, not a catastrophic regex.

- [ ] **Step 5: Validate against current state before any API call**

Require every block ID exactly once, matching revision/type, proposal status `PENDING`, allowed route `MARKDOWN`, and immutable target ID/hash. Validate the full file first; do not preview a subset if another block is invalid.

- [ ] **Step 6: Run tests and commit**

Run: `cd audit-runner && npm test -- decision-parser.test.ts decision-validator.test.ts && npm run build`

```bash
git add audit-runner/src/decisions
git commit -m "feat(audit): validate closed Markdown decisions"
```

### Task 3: Add blockwise preview/apply and append-only history

**Files:**
- Create: `audit-runner/src/decisions/decision-router.ts`
- Create: `audit-runner/src/decisions/decision-import-service.ts`
- Create: `audit-runner/src/history/decision-history.ts`
- Test: `audit-runner/src/decisions/decision-import-service.test.ts`
- Test: `audit-runner/src/history/decision-history.test.ts`
- Modify: `audit-runner/src/ebon/ebon-client.ts`
- Modify: `audit-runner/src/main.ts`

**Interfaces:**
- Adds CLI: `import-decisions --preview --proposal <id>`.
- Adds CLI: `import-decisions --apply --proposal <id> --preview-token <token>`.
- Produces: `DecisionImportService.preview(decision): Promise<DecisionPreview>` and `apply(decision, token): Promise<DecisionOutcome>`.
- Routes product/profile decisions to their audit endpoints; identity/line/scope edits route to adaptive UI/API services from the prerequisite plan.

- [ ] **Step 1: Write failing stale/atomic/idempotent tests**

```ts
it("makes no API call when any block in the file is invalid", async () => {
  await expect(service.loadAndPreview(markdownWithOneInvalidBlock(), "PA-1")).rejects.toThrow();
  expect(ebonClient.calls).toEqual([]);
});

it("applies one frozen block exactly once", async () => {
  const preview = await service.preview(decision());
  await service.apply(decision(), preview.token);
  await service.apply(decision(), preview.token);
  expect(ebonClient.applyCalls).toHaveLength(1);
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- decision-import-service.test.ts decision-history.test.ts`

- [ ] **Step 3: Implement type routing and frozen previews**

The CLI always parses/validates the whole current file, then selects exactly one proposal ID. `DEFER` performs no call; `SEND_TO_UI` updates local state only; `REJECT`, `CONFIRM`, and `EDIT` call the corresponding preview endpoint. Persist preview token/request hash in state and refuse apply if the block changed after preview.

- [ ] **Step 4: Apply and append history safely**

After backend apply succeeds, atomically update state to `APPLIED`, then append one JSON line containing run ID, proposal ID/revision/type, action, sanitized before/after IDs/status codes, idempotency key, backend operation ID, and timestamp. Never store comments for product/identity decisions if they might contain receipt text; store only `commentPresent: true`.

If state save fails after backend apply, the next run recovers through the backend idempotency receipt and writes the missing local outcome without reapplying.

- [ ] **Step 5: Run tests and commit**

Run: `cd audit-runner && npm test -- decision-import-service.test.ts decision-history.test.ts && npm run build`

```bash
git add audit-runner/src/decisions audit-runner/src/history audit-runner/src/ebon/ebon-client.ts audit-runner/src/main.ts
git commit -m "feat(audit): preview and apply Markdown blocks"
```

### Task 4: Add direct UI handoff links and focused retrieval

**Files:**
- Modify: `backend/src/main/java/de/ebon/api/ProductsController.java`
- Modify: `backend/src/main/java/de/ebon/product/ProductReviewService.java`
- Test: `backend/src/test/java/de/ebon/api/ProductsApiContractTests.java`
- Modify: `frontend/src/app.tsx`
- Modify: `frontend/src/pages/products-page.tsx`
- Modify: `frontend/src/pages/products-page.test.tsx`
- Modify: `frontend/src/pages/receipts-page.tsx`
- Modify: `frontend/src/pages/receipts-page.test.tsx`
- Modify: adaptive learning page created by `2026-07-13-adaptive-processing-ui-operations.md`
- Test: corresponding adaptive learning page test

**Interfaces:**
- Produces: protected `GET /api/products/review/{receiptItemId}`.
- Supports: `#/products?receiptItemId=<id>`, `#/receipts/<id>?line=<lineNumber>`, and `#/learning?profileId=<id>&jobId=<id>`.
- Consumes: token-free links from `progress.md`.

- [ ] **Step 1: Write failing API/UI deep-link tests**

```tsx
it("opens the requested product review item from an audit link", async () => {
  window.location.hash = "#/products?receiptItemId=44";
  render(<App />);
  expect(await screen.findByRole("dialog", { name: "Produktzuordnung prüfen" })).toBeInTheDocument();
  expect(api.productReviewItem).toHaveBeenCalledWith(44);
});
```

Backend tests require Bearer auth, 404 for missing/deleted receipt item, and the same sanitized DTO used by the queue.

- [ ] **Step 2: Run tests and verify failure**

Run:

```bash
cd backend && mvn -Dtest=ProductsApiContractTests test
cd ../frontend && npm test -- products-page.test.tsx receipts-page.test.tsx
```

- [ ] **Step 3: Implement focused retrieval and routing**

Add `ProductReviewService.itemForReview(id)` with existing manual/deleted protections. Pass parsed query parameters from `App` into `ProductsPage`; fetch one item, open its correction dialog, and keep normal queue behavior unchanged. Receipt details scroll/focus the trace line only after it is loaded. Learning page selects the requested profile/job without exposing raw text.

- [ ] **Step 4: Preserve unsaved-change behavior and safe errors**

Deep links must still honor the global unsaved-change guard. Missing/stale audit targets show a German non-sensitive banner and leave the user on the relevant page.

- [ ] **Step 5: Run focused tests and builds**

Run:

```bash
cd backend && mvn -Dtest=ProductsApiContractTests,AdaptiveProcessingApiContractTests test
cd ../frontend && npm test -- products-page.test.tsx receipts-page.test.tsx adaptive-processing-page.test.tsx
npm run build
```

- [ ] **Step 6: Commit UI handoff**

```bash
git add backend/src/main/java/de/ebon/api/ProductsController.java backend/src/main/java/de/ebon/product/ProductReviewService.java backend/src/test/java/de/ebon/api frontend/src
git commit -m "feat(audit): deep link unresolved review cases"
```

### Task 5: Verify Markdown/UI workflow end to end

**Files:**
- Create: `audit-runner/src/e2e/markdown-decision-flow.test.ts`
- Modify: `frontend/e2e/run-smoke.mjs`
- Modify: `docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md`

**Interfaces:**
- Produces: verified medium-confidence Markdown and low/conflict UI handoff.

- [ ] **Step 1: Add a mocked decision-flow integration test**

Generate a report containing `PA-1`, edit it to `CONFIRM`, preview, apply, regenerate, and assert the block is marked applied and history contains one sanitized line. Include a stale-revision case and an instruction-in-prose case with zero mutation.

- [ ] **Step 2: Extend Selenium smoke**

Open product, receipt-trace, and learning-profile audit links. Assert the intended object is selected, no token is present in the URL, and browser errors contain no raw text.

- [ ] **Step 3: Run all affected gates**

```bash
cd backend && mvn verify
cd ../frontend && npm test && npm run build && npm run e2e
cd ../audit-runner && npm test && npm run build
cd .. && docker compose config
git diff --check
git status --short
```

- [ ] **Step 4: Record exact evidence and commit**

```bash
git add audit-runner/src/e2e frontend/e2e/run-smoke.mjs docs/superpowers/plans/2026-08-29-paperless-ebon-audit.md
git commit -m "test(audit): verify Markdown and UI handoff"
```
