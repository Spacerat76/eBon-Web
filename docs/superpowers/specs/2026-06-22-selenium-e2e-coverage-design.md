# Selenium E2E Coverage Design

## Goal

Make the frontend code exercised by the existing Selenium smoke flow visible as a separate browser-coverage report, without changing the unit-test coverage gate.

## Decision

Use Istanbul instrumentation only for the dedicated E2E coverage command. Vite starts with the existing mock API plus a coverage flag, Selenium executes the normal smoke flow, and the runner writes `window.__coverage__` to an NYC temporary file. `nyc report` then produces terminal, HTML, and LCOV output.

## Why This Approach

- It works with the current Chrome and Edge Selenium setup.
- It keeps normal development, production builds, and the regular E2E test free of instrumentation.
- It reuses the existing smoke flow and mock data, so no Paperless, OpenRouter, database, API token, or private receipt is needed.
- It produces standard LCOV output for later CI upload or consolidation.

Chrome DevTools Protocol coverage was not selected because it couples the test implementation more closely to Chromium and makes source-level LCOV reporting less direct. Replacing Selenium with another E2E runner is out of scope.

## Command and Output

`npm run e2e:coverage` will:

1. Set `EBON_E2E_COVERAGE=true`.
2. Start the local Vite server with `VITE_EBON_MOCK_API=true` and Istanbul instrumentation.
3. Run the existing Selenium smoke workflow.
4. Save raw browser data under `frontend/.nyc_output/`.
5. Generate terminal, HTML, and LCOV reports under `frontend/coverage-e2e/`.

Both output directories are local artifacts and excluded from Git.

## Quality Gate

There is intentionally no E2E coverage threshold in this first step. The report establishes the actual coverage produced by the current smoke flow. Vitest remains the enforced 50-percent unit/component coverage gate for the explicit core source set.

## Verification

- A small Node test proves browser coverage is written only when the Selenium driver returns a non-empty Istanbul object.
- `npm run e2e:coverage` produces `coverage-e2e/index.html` and `coverage-e2e/lcov.info` from mock-only browser tests.
- `npm run e2e`, `npm run test:coverage`, and `npm run build` remain successful.
