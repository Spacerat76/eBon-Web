# Frontend Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add reproducible unit/component test coverage to the frontend, with an initial 50-percent quality gate for the core frontend surface.

**Architecture:** Vitest runs in `jsdom` and reuses Vite aliases. V8 produces terminal, HTML, and LCOV reports. The 50-percent threshold applies to testable application modules (`App`, API client, formatting, icon mapping, shell, and status badges); generated UI primitives and page-level workflow coverage remain covered by Selenium while their focused tests are expanded incrementally.

**Tech Stack:** Vitest, `@vitest/coverage-v8`, React Testing Library, `@testing-library/user-event`, jsdom, existing Vite and Selenium tooling.

---

### Task 1: Add Vitest and Coverage Infrastructure

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Create: `frontend/vitest.config.ts`
- Create: `frontend/src/test/setup.ts`

- [x] **Step 1: Add the failing test command contract**

Add the scripts before any tests exist:

```json
"test": "vitest run --passWithNoTests",
"test:coverage": "vitest run --coverage --passWithNoTests"
```

Run: `npm run test`

Expected: FAIL because `vitest` is not installed.

- [x] **Step 2: Install the test dependencies**

Run:

```bash
npm install -D vitest @vitest/coverage-v8 jsdom @testing-library/react @testing-library/jest-dom @testing-library/user-event
```

- [x] **Step 3: Configure the test runtime and coverage gate**

Create `frontend/vitest.config.ts` with Vite-compatible React, Tailwind, and alias configuration. Configure `jsdom`, `src/test/setup.ts`, V8 reports (`text`, `html`, `lcov`), `all: true`, and a 50-percent threshold for lines, functions, branches, and statements over this explicit core source set:

```ts
include: [
  "src/App.tsx",
  "src/components/app-shell.tsx",
  "src/components/receipt-badges.tsx",
  "src/lib/api.ts",
  "src/lib/category-icons.tsx",
  "src/lib/format.ts"
]
```

Create `frontend/src/test/setup.ts` importing `@testing-library/jest-dom/vitest` and cleaning up after every test.

- [x] **Step 4: Verify the empty infrastructure works**

Run: `npm run test`

Expected: PASS with zero tests discovered.

Run: `npm run test:coverage`

Expected: FAIL because the explicit source set has zero coverage, proving the threshold is enforced.

### Task 2: Test Pure Frontend Logic

**Files:**
- Create: `frontend/src/lib/format.test.ts`
- Create: `frontend/src/lib/api.test.ts`
- Create: `frontend/src/lib/category-icons.test.tsx`

- [x] **Step 1: Write failing formatting tests**

Cover German currency, missing dates, invalid date-time input, and valid time formatting:

```ts
expect(formatCurrency(12.5)).toBe("12,50 €");
expect(formatDate(null)).toBe("-");
expect(formatDateTimeParts("invalid")).toEqual({ date: "invalid", time: "" });
expect(formatTime("17:42:00")).toMatch(/17:42/);
```

Run: `npx vitest run src/lib/format.test.ts`

Expected: FAIL before the test file exists.

- [x] **Step 2: Add API client tests with mocked `fetch`**

Test these observable contracts without a real backend:

```ts
await client.receipts({ page: 2, size: 10, uncategorizedOnly: true });
expect(fetch).toHaveBeenCalledWith(
  "/api/receipts?page=2&size=10&sortBy=receiptDate&sortDir=desc&uncategorizedOnly=true",
  expect.objectContaining({ headers: expect.any(Headers) })
);
```

Also verify bearer-token propagation, `204` handling, JSON error mapping to `ApiClientError`, and filename extraction from `Content-Disposition`.

- [x] **Step 3: Add category-icon tests**

Render known and unknown icon names and assert that known names render their accessible SVG while unknown names fall back to the `Tag` icon without rendering unsafe input.

- [x] **Step 4: Run the focused logic tests**

Run:

```bash
npx vitest run src/lib/format.test.ts src/lib/api.test.ts src/lib/category-icons.test.tsx
```

Expected: PASS.

### Task 3: Test App Shell and Status Presentation

**Files:**
- Create: `frontend/src/components/app-shell.test.tsx`
- Create: `frontend/src/components/receipt-badges.test.tsx`
- Create: `frontend/src/App.test.tsx`

- [x] **Step 1: Write a failing app-shell interaction test**

Render `AppShell` with a controlled token state. Assert the active navigation link has `aria-current="page"`, entering a token calls `onTokenChange`, and the clear button emits an empty token.

Run: `npx vitest run src/components/app-shell.test.tsx`

Expected: FAIL before the test file exists.

- [x] **Step 2: Write receipt-badge state tests**

Render each parse, category-source, and delete-reason state. Assert user-facing German labels and exclude any raw enum-only output.

- [x] **Step 3: Write app routing/token persistence tests**

Render `App` with the existing mock API enabled. Assert the dashboard loads from `#/`, navigation to `#/receipts` renders the receipt page, `#/search?uncategorizedOnly=true` passes through the intended route, and typing/removing the API token updates `sessionStorage`.

- [x] **Step 4: Run the focused component tests**

Run:

```bash
npx vitest run src/components/app-shell.test.tsx src/components/receipt-badges.test.tsx src/App.test.tsx
```

Expected: PASS.

### Task 4: Enforce Coverage in CI and Verify the Complete Frontend

**Files:**
- Modify: `.github/workflows/ci.yml`

- [x] **Step 1: Add the coverage job step**

Insert this step after dependency installation and before the production build:

```yaml
- name: Frontend unit tests and coverage
  run: npm run test:coverage
  working-directory: frontend
```

- [x] **Step 2: Run the final frontend commands**

Run:

```bash
npm run test
npm run test:coverage
npm run build
npm run e2e
```

Expected: all commands pass, `coverage/index.html` and `coverage/lcov.info` exist, and every enforced metric is at least 50 percent.

- [x] **Step 3: Validate the workflow and diff**

Run:

```bash
docker compose config
git diff --check
```

Expected: both commands pass.

- [ ] **Step 4: Commit the implementation**

Run:

```bash
git add frontend/package.json frontend/package-lock.json frontend/vitest.config.ts frontend/src/test/setup.ts frontend/src/**/*.test.* .github/workflows/ci.yml
git commit -m "Add frontend coverage tests"
```

## Plan Self-Review

- The plan covers the approved V8/Vitest stack, the 50-percent gate, reports, focused business-logic tests, component tests, CI, and preservation of Selenium.
- The coverage scope is explicit and excludes only generated UI primitives and large page workflows that remain protected by Selenium until dedicated tests expand the measured source set.
- Commands are concrete and do not require real credentials or external application services.
