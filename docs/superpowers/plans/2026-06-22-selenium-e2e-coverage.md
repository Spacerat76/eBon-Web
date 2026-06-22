# Selenium E2E Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a mock-only Selenium E2E coverage report without introducing a coverage threshold.

**Architecture:** Vite conditionally instruments browser source with `vite-plugin-istanbul`. The Selenium runner passes the browser coverage object to a small filesystem helper; `nyc report` turns that raw object into terminal, HTML, and LCOV reports.

**Tech Stack:** Vite 8, Selenium WebDriver, Istanbul, NYC, Node test runner.

---

### Task 1: Add a Testable Browser-Coverage Writer

**Files:**
- Create: `frontend/e2e/coverage-output.test.mjs`
- Create: `frontend/e2e/coverage-output.mjs`

- [x] **Step 1: Write the failing helper tests**

Test that a non-empty coverage map returned from `driver.executeScript` is persisted as JSON, and that an absent map rejects with a useful error.

Run: `node --test e2e/coverage-output.test.mjs`

Expected: FAIL because `coverage-output.mjs` does not exist.

- [x] **Step 2: Implement the minimal helper**

Export `writeBrowserCoverage(driver, outputFile)`. It must read `window.__coverage__`, require at least one covered source, create the parent directory, and write valid JSON.

- [x] **Step 3: Verify the helper**

Run: `node --test e2e/coverage-output.test.mjs`

Expected: PASS.

### Task 2: Instrument the Dedicated E2E Server and Collect Coverage

**Files:**
- Modify: `frontend/vite.config.ts`
- Modify: `frontend/e2e/run-smoke.mjs`
- Modify: `frontend/e2e/smoke.mjs`

- [x] **Step 1: Verify the missing E2E coverage command fails**

Run: `npm run e2e:coverage`

Expected: FAIL because the script is not declared yet.

- [x] **Step 2: Enable Istanbul only under the E2E coverage flag**

Conditionally register `vite-plugin-istanbul` when `VITE_EBON_E2E_COVERAGE=true`. Include application source only and exclude tests and dependencies.

- [x] **Step 3: Refactor the smoke flow into an exported runner**

Export `runSmoke` from `e2e/smoke.mjs`, preserve all current browser assertions, and call `writeBrowserCoverage` after the assertions but before `driver.quit()` when an output file is supplied.

- [x] **Step 4: Configure coverage output in the Vite runner**

When `EBON_E2E_COVERAGE=true`, clean `frontend/.nyc_output/`, set `VITE_EBON_E2E_COVERAGE=true`, pass `selenium-e2e.json` to `runSmoke`, and fail if the file was not produced.

### Task 3: Report, Document, and Verify

**Files:**
- Modify: `frontend/package.json`
- Modify: `frontend/package-lock.json`
- Modify: `.gitignore`
- Modify: `README.md`

- [x] **Step 1: Add dependencies and scripts**

Add `vite-plugin-istanbul` and `nyc`. Add `npm run e2e:coverage`, which runs the instrumented smoke flow and reports `text`, `html`, and `lcov` coverage into `coverage-e2e/`.

- [x] **Step 2: Keep generated browser coverage out of Git**

Ignore `.nyc_output/` and `coverage-e2e/` under `frontend/`.

- [x] **Step 3: Document the separate reporting model**

Document local execution, output paths, mock-only behavior, and the absence of an E2E threshold in the README.

- [x] **Step 4: Run the complete verification set**

Run `npm ci`, `node --test e2e/coverage-output.test.mjs`, `npm run e2e:coverage`, `npm run e2e`, `npm run test:coverage`, `npm run build`, `docker compose config --quiet`, and `git diff --check`.

Expected: all commands pass; `coverage-e2e/index.html` and `coverage-e2e/lcov.info` exist; no external service or real credential is used.
