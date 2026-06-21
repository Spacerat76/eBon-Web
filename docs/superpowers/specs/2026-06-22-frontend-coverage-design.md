# Frontend Coverage Design

## Goal

Introduce reproducible frontend code coverage without replacing the existing Selenium smoke tests.

## Decision

Use Vitest with the V8 coverage provider and React Testing Library. Coverage is enforced by `npm run test:coverage` with an initial minimum of 50 percent for statements, branches, functions, and lines.

## Scope

- Add a Vitest configuration that reuses the existing Vite aliases and runs tests in `jsdom`.
- Add `test` and `test:coverage` npm scripts.
- Produce terminal, HTML, and LCOV coverage reports under `frontend/coverage/`.
- Test pure frontend logic first: API client behavior, formatting helpers, and category-icon mapping.
- Add focused component tests for representative loading, error, and populated states using the existing mock API.
- Keep Selenium as the separate browser-level smoke suite; it does not contribute to the coverage percentage.
- Extend CI to execute the coverage command without real API tokens or Paperless/OpenRouter access.

## Exclusions

- No real backend calls in unit or component tests.
- No visual snapshots or production receipt data.
- No change to frontend behavior solely to increase coverage.

## Verification

1. `npm run test` executes the frontend test suite.
2. `npm run test:coverage` writes coverage reports and fails below any 50 percent threshold.
3. `npm run build` and `npm run e2e` remain successful.
4. The GitHub Actions workflow runs the coverage command.
