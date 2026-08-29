# eBon Audit Runner and Inventory Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a one-shot TypeScript audit runner with atomic local state, single-writer locking, complete read-only Paperless inventory, stable source hashes, and incremental invalidation.

**Architecture:** A standalone Node.js CLI runs as an opt-in Docker Compose service and talks to Paperless and eBon over HTTP. It persists only sanitized state in `var/ebon-audit/`; Paperless text and originals stay in memory or temporary directories and never enter the state files.

**Tech Stack:** Node.js 24.16.0, TypeScript 6.0.3, Vitest 4.1.9, YAML 2.8.1, Docker Compose, native `fetch`, Node `crypto` and `fs/promises`.

**Spec:** `docs/superpowers/specs/2026-08-29-paperless-ebon-audit-design.md`

## Global Constraints

- Paperless calls are GET-only and authenticated with the Paperless token, never the eBon app token.
- The eBon app token is sent only to the configured eBon API origin.
- Reject a complete inventory if any page fails, repeats, changes origin, or returns an invalid document.
- Never serialize Paperless `content`, original bytes, OCR text, tokens, or full HTTP errors.
- Use `open(lock, "wx")` for single-writer exclusion and atomic temporary-file rename for state/report writes.
- `audit-state.json` is canonical; `progress.md` is generated later as a projection.
- The runner has no OpenRouter dependency, configuration property, client, or network call.
- Every task ends with focused tests and a separate commit.

---

### Task 1: Add the audit contract and TypeScript CLI skeleton

**Files:**
- Modify: `ebon-specification.md`
- Modify: `.gitignore`
- Create: `audit-runner/package.json`
- Create: `audit-runner/package-lock.json`
- Create: `audit-runner/tsconfig.json`
- Create: `audit-runner/vitest.config.ts`
- Create: `audit-runner/src/main.ts`
- Create: `audit-runner/src/config.ts`
- Test: `audit-runner/src/config.test.ts`

**Interfaces:**
- Produces: `AuditConfig loadAuditConfig(NodeJS.ProcessEnv env)`.
- Produces: CLI commands `inventory`, `resume`, and `status`; later plans add `verify`, `import-decisions`, and `report`.
- Consumes: environment variables `AUDIT_WORK_DIR`, `EBON_API_BASE_URL`, `APP_API_TOKEN`, `PAPERLESS_BASE_URL`, `PAPERLESS_API_TOKEN`, and `PAPERLESS_EBON_TAG`.

- [ ] **Step 1: Extend the product contract before code**

Add an “Interaktiver Paperless-eBon-Audit” subsection to `ebon-specification.md` defining GET-only Paperless access, file-backed local state, Codex-only audit decisions, no OpenRouter in audit mode, diagnostic-only OCR, and the `var/ebon-audit/` privacy boundary. Add `var/ebon-audit/` to `.gitignore`.

- [ ] **Step 2: Write the failing configuration tests**

```ts
it("requires separate Paperless and eBon credentials", () => {
  expect(() => loadAuditConfig({ AUDIT_WORK_DIR: "/audit" })).toThrow("PAPERLESS_BASE_URL");
});

it("does not accept or expose OpenRouter configuration", () => {
  const config = loadAuditConfig(validEnv({ OPENROUTER_API_KEY: "must-not-be-read" }));
  expect(Object.keys(config)).not.toContain("openRouterApiKey");
});
```

- [ ] **Step 3: Run the test and confirm the missing module failure**

Run: `cd audit-runner && npm test -- config.test.ts`

Expected: FAIL because `loadAuditConfig` and the package skeleton do not exist.

- [ ] **Step 4: Create the pinned package and configuration boundary**

Use exact dependency versions and commit the generated lockfile:

```json
{
  "name": "ebon-audit-runner",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "build": "tsc -p tsconfig.json --noEmit",
    "start": "tsx src/main.ts",
    "test": "vitest run"
  },
  "dependencies": {
    "tsx": "4.20.6",
    "yaml": "2.8.1"
  },
  "devDependencies": {
    "@types/node": "24.10.1",
    "typescript": "6.0.3",
    "vitest": "4.1.9"
  }
}
```

Define the configuration without any OpenRouter field:

```ts
export interface AuditConfig {
  workDir: string;
  ebonApiBaseUrl: URL;
  appApiToken: string;
  paperlessBaseUrl: URL;
  paperlessApiToken: string;
  paperlessTag: string;
  maxDocumentsPerRun: number;
  maxMutationsPerRun: number;
}
```

Validate URLs, nonblank tokens, positive limits, and reject an `EBON_API_BASE_URL` whose pathname is outside `/api`.

- [ ] **Step 5: Implement command dispatch without work logic**

`main.ts` parses exactly one supported command, loads configuration, and exits nonzero with a sanitized German error for unknown commands. Do not print configuration values or exception objects.

- [ ] **Step 6: Run focused tests and build**

Run: `cd audit-runner && npm test -- config.test.ts && npm run build`

Expected: configuration tests PASS and TypeScript exits `0`.

- [ ] **Step 7: Commit the contract and skeleton**

```bash
git add ebon-specification.md .gitignore audit-runner
git commit -m "feat(audit): scaffold isolated audit runner"
```

### Task 2: Persist versioned state atomically and enforce one writer

**Files:**
- Create: `audit-runner/src/state/audit-state.ts`
- Create: `audit-runner/src/state/state-store.ts`
- Create: `audit-runner/src/state/audit-lock.ts`
- Test: `audit-runner/src/state/state-store.test.ts`
- Test: `audit-runner/src/state/audit-lock.test.ts`

**Interfaces:**
- Produces: `AuditStateStore.load(): Promise<AuditState>` and `save(state: AuditState): Promise<void>`.
- Produces: `AuditLock.acquire(): Promise<AsyncDisposable>`.
- Produces: schema version `1` and phase enum `INVENTORY | SOURCE_VERIFY | MERCHANT_BRANCH_DISCOVERY | PARSE_VERIFY | PROFILE_OPTIMIZATION | PRODUCT_VERIFY | REPORT`.

- [ ] **Step 1: Write failing atomicity and lock tests**

```ts
it("keeps the previous state when the temporary write fails", async () => {
  await store.save(stateWithRun("run-1"));
  fileSystem.failNextRename();
  await expect(store.save(stateWithRun("run-2"))).rejects.toThrow();
  await expect(store.load()).resolves.toMatchObject({ runId: "run-1" });
});

it("rejects a second live writer", async () => {
  await using first = await lock.acquire();
  await expect(lock.acquire()).rejects.toThrow("Audit läuft bereits");
});
```

- [ ] **Step 2: Run tests and verify they fail**

Run: `cd audit-runner && npm test -- state-store.test.ts audit-lock.test.ts`

Expected: FAIL because state and lock types are absent.

- [ ] **Step 3: Define the closed state schema**

```ts
export interface AuditState {
  schemaVersion: 1;
  runId: string;
  runStatus: "RUNNING" | "PAUSED" | "FAILED" | "COMPLETED";
  phase: AuditPhase;
  inventoryAcceptedAt: string | null;
  inventoryFingerprint: string | null;
  documents: Record<string, AuditDocumentState>;
  proposals: Record<string, AuditProposalState>;
  counters: AuditCounters;
  lastCheckpointAt: string;
}

export interface AuditDocumentState {
  paperlessDocumentId: number;
  paperlessModifiedAt: string | null;
  contentHash: string;
  originalHash: string | null;
  inputFingerprint: string;
  merchantKey: string | null;
  branchKey: string | null;
  layoutFingerprint: string | null;
  fingerprintVersion: number | null;
  phaseStatus: Partial<Record<AuditPhase, "PENDING" | "RUNNING" | "DONE" | "FAILED" | "INVALIDATED">>;
  proposalIds: string[];
  errorCode: string | null;
}
```

Write explicit runtime validators; reject unknown schema versions and unknown keys rather than silently accepting drift.

- [ ] **Step 4: Implement atomic storage and lock cleanup**

Write `audit-state.json.tmp-<pid>` in the same directory, `fsync` the file, rename it over `audit-state.json`, then `fsync` the directory when supported. Acquire `lock` with exclusive create and store only PID plus start timestamp. Release in `Symbol.asyncDispose`; a stale lock is removed only with an explicit `--recover-stale-lock` command after verifying the PID is absent.

- [ ] **Step 5: Run focused tests and build**

Run: `cd audit-runner && npm test -- state-store.test.ts audit-lock.test.ts && npm run build`

Expected: all state/lock tests PASS.

- [ ] **Step 6: Commit state and locking**

```bash
git add audit-runner/src/state
git commit -m "feat(audit): persist resumable audit state"
```

### Task 3: Implement a strict GET-only Paperless client

**Files:**
- Create: `audit-runner/src/paperless/paperless-types.ts`
- Create: `audit-runner/src/paperless/paperless-client.ts`
- Create: `audit-runner/src/http/safe-fetch.ts`
- Test: `audit-runner/src/paperless/paperless-client.test.ts`

**Interfaces:**
- Produces: `PaperlessAuditClient.listTaggedDocuments(): Promise<PaperlessAuditDocument[]>`.
- Produces: `PaperlessAuditClient.downloadOriginal(documentId: number): Promise<PaperlessOriginal>`.
- Produces: `PaperlessOriginal` as `{ bytes: Uint8Array; mediaType: string; fileName: string | null }`, which callers must not persist.

- [ ] **Step 1: Write failing pagination and method-safety tests**

```ts
it("accepts a snapshot only after every same-origin page succeeds", async () => {
  server.page(1, { next: "/api/documents/?page=2", results: [document(10)] });
  server.page(2, { next: null, results: [document(11)] });
  await expect(client.listTaggedDocuments()).resolves.toHaveLength(2);
  expect(server.requests).toSatisfyAll(request => request.method === "GET");
});

it("rejects cross-origin next links and duplicate pages", async () => {
  server.page(1, { next: "https://attacker.invalid/api/documents/?page=2", results: [] });
  await expect(client.listTaggedDocuments()).rejects.toThrow("PAPERLESS_PAGINATION_INVALID");
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- paperless-client.test.ts`

Expected: FAIL because the client does not exist.

- [ ] **Step 3: Implement validated pagination**

Request `/api/documents/?tags__name__iexact=<encoded>&page_size=100&ordering=-created`. Resolve `next` against the configured base URL, require identical origin and an `/api/documents/` path, remember visited URLs, validate unique positive IDs, and return results only after the terminal page succeeds.

The response type includes only fields consumed in memory:

```ts
export interface PaperlessAuditDocument {
  id: number;
  created: string | null;
  modified: string | null;
  content: string;
  originalFileName: string | null;
}
```

Download originals from `GET /api/documents/{id}/download/`. Enforce a configurable byte ceiling before buffering and sanitize `Content-Disposition` filenames to a basename.

- [ ] **Step 4: Add a hard method guard**

`safePaperlessFetch` accepts no method argument and always issues `GET`. Tests scan `audit-runner/src` for `POST|PUT|PATCH|DELETE` adjacent to the Paperless client and verify only GET requests reached the mock server.

- [ ] **Step 5: Run tests and commit**

Run: `cd audit-runner && npm test -- paperless-client.test.ts && npm run build`

```bash
git add audit-runner/src/http audit-runner/src/paperless
git commit -m "feat(audit): inventory Paperless through GET only"
```

### Task 4: Build complete inventory hashing and incremental invalidation

**Files:**
- Create: `audit-runner/src/inventory/source-hash.ts`
- Create: `audit-runner/src/inventory/inventory-service.ts`
- Create: `audit-runner/src/inventory/invalidation.ts`
- Test: `audit-runner/src/inventory/inventory-service.test.ts`
- Test: `audit-runner/src/inventory/invalidation.test.ts`
- Modify: `audit-runner/src/main.ts`

**Interfaces:**
- Produces: `InventoryService.scan(previous: AuditState): Promise<InventoryResult>`.
- Produces: `InvalidationPlanner.plan(previous, current, versions): InvalidationPlan`.
- Consumes: complete Paperless document list and original downloads from Task 3.

- [ ] **Step 1: Write failing inventory replacement tests**

```ts
it("does not replace the accepted inventory after a later page failure", async () => {
  const previous = stateWithAcceptedDocuments([10, 11]);
  paperless.failPage(2);
  await expect(service.scan(previous)).rejects.toThrow("PAPERLESS_INVENTORY_INCOMPLETE");
  expect(await stateStore.load()).toEqual(previous);
});

it("invalidates product phases when parser input changes", () => {
  expect(plan(previousDoc, changedContentDoc, versions).phases).toContain("PRODUCT_VERIFY");
});
```

- [ ] **Step 2: Run tests and verify failure**

Run: `cd audit-runner && npm test -- inventory-service.test.ts invalidation.test.ts`

Expected: FAIL because the inventory planner is absent.

- [ ] **Step 3: Implement privacy-safe hashes**

Use SHA-256 over normalized CRLF/LF Paperless content for `contentHash`, raw original bytes for `originalHash`, and this versioned input fingerprint:

```ts
sha256(JSON.stringify({
  contentHash,
  originalHash,
  parserContractVersion,
  fingerprintVersion,
  auditSchemaVersion: 1
}))
```

Never log the hash preimage. Hashes are identifiers, not security claims.

- [ ] **Step 4: Implement full-snapshot reconciliation**

Build the new document map in memory. Keep previously known documents missing from the current complete snapshot as `OUT_OF_SCOPE`, not deleted, and do not call eBon delete endpoints. Apply `maxDocumentsPerRun` only after accepting and storing the complete inventory so pagination safety is not weakened.

- [ ] **Step 5: Wire `inventory`, `resume`, and `status`**

`inventory` creates a new run/checkpoint. `resume` continues pending documents under the existing run ID. `status` reads state without acquiring the mutation lock and prints only counts, phases, and sanitized error codes.

- [ ] **Step 6: Run focused and package tests**

Run: `cd audit-runner && npm test && npm run build`

Expected: all runner tests PASS; unchanged second inventory reports zero invalidated documents.

- [ ] **Step 7: Commit inventory and invalidation**

```bash
git add audit-runner/src/inventory audit-runner/src/main.ts
git commit -m "feat(audit): track incremental Paperless inventory"
```

### Task 5: Package the one-shot Compose audit service

**Files:**
- Create: `audit-runner/Dockerfile`
- Create: `audit-runner/.dockerignore`
- Modify: `docker-compose.yml`
- Modify: `.env.example`
- Modify: `README.md`
- Test: `audit-runner/src/no-openrouter.test.ts`

**Interfaces:**
- Produces: `docker compose --profile audit run --rm audit inventory`.
- Mounts: repository-local `./var/ebon-audit:/audit`.
- Depends on: healthy `backend`; Paperless remains an external GET-only service.

- [ ] **Step 1: Write the failing OpenRouter-absence test**

```ts
it("contains no OpenRouter runtime path", async () => {
  const source = await readAllSourceFiles(new URL(".", import.meta.url));
  expect(source).not.toMatch(/OPENROUTER|openrouter\.ai|AiReceiptParsingClient/i);
});
```

- [ ] **Step 2: Create the pinned Docker image**

Use `node:24.16.0-bookworm-slim`, `npm ci`, a non-root user, and an entrypoint of `npm start --`. OCR packages are added in Audit Plan 2, not here.

- [ ] **Step 3: Add the opt-in Compose service**

```yaml
  audit:
    profiles: ["audit"]
    build:
      context: ./audit-runner
    environment:
      AUDIT_WORK_DIR: /audit
      EBON_API_BASE_URL: http://backend:8080/api
      APP_API_TOKEN: ${APP_API_TOKEN:-change_me_local_dev_token}
      PAPERLESS_BASE_URL: ${PAPERLESS_BASE_URL:-http://localhost:8000}
      PAPERLESS_API_TOKEN: ${PAPERLESS_API_TOKEN:-change_me_paperless_token}
      PAPERLESS_EBON_TAG: ${PAPERLESS_EBON_TAG:-eBON}
    volumes:
      - ./var/ebon-audit:/audit
    depends_on:
      backend:
        condition: service_healthy
```

Do not pass any `OPENROUTER_*` variable to this service.

- [ ] **Step 4: Document fake example settings and commands**

Add `AUDIT_MAX_DOCUMENTS_PER_RUN`, `AUDIT_MAX_MUTATIONS_PER_RUN`, and `AUDIT_MAX_ORIGINAL_BYTES` to `.env.example` with safe development values. Document `inventory`, `status`, and `resume`; state that the directory is private and must not be attached to bug reports.

- [ ] **Step 5: Verify the complete plan surface**

Run:

```bash
cd audit-runner && npm test && npm run build
cd .. && docker compose config
docker compose --profile audit build audit
git diff --check
git status --short
```

Expected: tests/build/config/image build succeed; rendered audit environment contains no `OPENROUTER_*`; Git does not list `var/ebon-audit/`.

- [ ] **Step 6: Commit Compose integration**

```bash
git add audit-runner/Dockerfile audit-runner/.dockerignore docker-compose.yml .env.example README.md
git commit -m "feat(audit): run inventory as isolated compose job"
```
