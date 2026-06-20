# Paperless Raw Text Reparse Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the single-receipt reparse flow compare Paperless text and require an explicit user decision before replacing stored raw text.

**Architecture:** A small Paperless client method fetches one document by ID. The receipt service exposes a prompt-free status DTO and accepts an explicit raw-text source only for individual reparses. The React detail flow performs a preflight request and renders a confirmation dialog before submitting the selected source.

**Tech Stack:** Spring Boot 4, Java 25, JUnit 5, Mockito, React 19, TypeScript, Vite, Selenium mock API.

---

### Task 1: Document the external contract

**Files:**
- Modify: `ebon-specification.md`
- Modify: `prompts/phase-07-rest-api-contracts.md`
- Modify: `prompts/phase-09-receipts-ui.md`

- [ ] Add the `PaperlessRawTextStatusDTO` states `UNCHANGED`, `CHANGED`, and `UNAVAILABLE`.
- [ ] Define `rawTextSource=STORED|PAPERLESS` for the individual reparse endpoint, with `STORED` as the default.
- [ ] State that bulk reparse never refreshes Paperless content and that neither status nor logs return raw-text comparison material.

### Task 2: Add the backend contract with red-green tests

**Files:**
- Create: `backend/src/main/java/de/ebon/api/dto/PaperlessRawTextStatusDto.java`
- Create: `backend/src/main/java/de/ebon/api/dto/RawTextSource.java`
- Modify: `backend/src/main/java/de/ebon/paperless/PaperlessClient.java`
- Modify: `backend/src/main/java/de/ebon/paperless/PaperlessRestClient.java`
- Modify: `backend/src/main/java/de/ebon/persistence/model/Receipt.java`
- Modify: `backend/src/main/java/de/ebon/api/service/ReceiptApiService.java`
- Modify: `backend/src/main/java/de/ebon/api/ReceiptsController.java`
- Test: `backend/src/test/java/de/ebon/api/service/ReceiptApiServiceTests.java`
- Test: `backend/src/test/java/de/ebon/api/ReceiptApiContractTests.java`

- [ ] Write tests that expect `CHANGED` for different content and `UNCHANGED` for CRLF/LF-only differences.
- [ ] Run `mvn "-Dtest=ReceiptApiServiceTests,ReceiptApiContractTests" test` and confirm the new tests fail because the status API and source selection do not exist.
- [ ] Implement `fetchDocumentById`, the status DTO/service method, and explicit source selection.
- [ ] Ensure the `PAPERLESS` branch sets the new text before calling the parser and relies on the enclosing transaction for rollback.
- [ ] Add contract coverage for authenticated access and an `UNAVAILABLE` response without raw text.

### Task 3: Add the receipt-detail confirmation flow

**Files:**
- Modify: `frontend/src/lib/types.ts`
- Modify: `frontend/src/lib/api.ts`
- Modify: `frontend/src/lib/mock-api.ts`
- Modify: `frontend/src/pages/receipts-page.tsx`
- Test: `frontend/e2e/smoke.mjs`

- [ ] Add TypeScript types for the status and source enum.
- [ ] Extend the mock API for the preflight endpoint.
- [ ] Replace the direct detail reparse call with status check, decision dialog, and explicit source selection.
- [ ] Cover the changed-text confirmation and stored-text path in the smoke test without using real Paperless data.

### Task 4: Verify the complete flow

**Files:**
- Verify: `backend/`
- Verify: `frontend/`

- [ ] Run `cd backend && mvn verify`.
- [ ] Run `cd frontend && npm run build`.
- [ ] Run the focused frontend E2E command and `git diff --check`.
- [ ] Start the Compose stack and manually exercise both dialog choices against a local Paperless document.
