# eBon UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the current frontend presentation with the approved “Refined Current” UI while preserving every existing workflow, datum, validation rule, and safety confirmation.

**Architecture:** Build a small set of typed layout and interaction primitives, migrate the application shell, then migrate one domain page at a time without changing `ApiClient` or DTO contracts. Each page keeps its current data-loading and mutation logic while its JSX is decomposed into focused sections using the shared primitives.

**Tech Stack:** React 19.2.7, TypeScript 6.0.3, Vite 8.0.16, Tailwind CSS 4.3.0, lucide-react 1.17.0, Recharts 3.8.1, Vitest 4.1.9, Testing Library, Selenium WebDriver.

## Global Constraints

- The canonical design is `docs/superpowers/specs/2026-07-11-ebon-ui-redesign-design.md`.
- The canonical product contract remains `ebon-specification.md`, especially F-19 and sections 8, 9, 14, 16, and 17.
- UI language is German; desktop and tablet are primary; mobile remains usable.
- Light and dark mode follow `prefers-color-scheme`.
- Preserve every current filter, table column, status, action, validation, preview, and confirmation unless the approved design explicitly relocates it.
- Keep `Authorization: Bearer <APP_API_TOKEN>` behavior unchanged and never persist the masked value `********`.
- Do not introduce a frontend router or state-management dependency.
- Do not call real Paperless-NGX or OpenRouter services from tests.
- After each task run the focused Vitest file and `npm run build` from `frontend/`.
- Keep unrelated user changes intact and commit only files belonging to the completed task.

---

## File and Responsibility Map

### New shared files

- `frontend/src/components/layout/page-header.tsx` — context label, title, page actions.
- `frontend/src/components/layout/page-tabs.tsx` — accessible tab list driven by string ids.
- `frontend/src/components/layout/sticky-action-bar.tsx` — persistent save/cancel footer.
- `frontend/src/components/feedback/status-banner.tsx` — semantic success/warning/error/info strip.
- `frontend/src/components/data/filter-bar.tsx` — labeled filter region and active-filter chips.
- `frontend/src/components/data/data-table.tsx` — table shell, horizontal overflow, empty/loading regions, pagination.
- `frontend/src/components/ui/secret-input.tsx` — masked-secret semantics that never emit `********` as a replacement value.
- `frontend/src/components/ui/confirm-dialog.tsx` — accessible confirmation dialog for high-impact mutations.
- `frontend/src/components/session-access.tsx` — session-only API-token entry outside the daily page header.
- `frontend/src/components/shared-components.test.tsx` — contract tests for the new primitives.

### Existing files to migrate

- `frontend/src/styles.css` — design tokens, focus defaults, tabular financial numerals.
- `frontend/src/components/ui/button.tsx` — approved blue primary action and consistent focus ring.
- `frontend/src/components/ui/card.tsx` — restrained panel styling.
- `frontend/src/components/app-shell.tsx` — grouped desktop navigation and mobile behavior; no global token field.
- `frontend/src/App.tsx` — navigation metadata and routing context only.
- `frontend/src/pages/dashboard-page.tsx` — approved information-preserving dashboard hierarchy.
- `frontend/src/pages/receipts-page.tsx` — full-width list/detail modes and tabbed receipt detail.
- `frontend/src/pages/search-page.tsx` — primary filters, secondary-filter disclosure, chips, results.
- `frontend/src/pages/products-page.tsx` — review workspace and tabbed master-data management.
- `frontend/src/pages/product-price-comparison.tsx` — analysis header, metric row, trend, comparison, observations.
- `frontend/src/pages/reports-page.tsx` — shared report filter state and analysis layout.
- `frontend/src/pages/settings-page.tsx` — task-based settings navigation and isolated danger zone.
- `frontend/e2e/smoke.mjs` — updated navigation and critical workflow selectors.

---

### Task 1: Design Tokens and Shared UI Primitives

**Files:**
- Create: `frontend/src/components/layout/page-header.tsx`
- Create: `frontend/src/components/layout/page-tabs.tsx`
- Create: `frontend/src/components/layout/sticky-action-bar.tsx`
- Create: `frontend/src/components/feedback/status-banner.tsx`
- Create: `frontend/src/components/data/filter-bar.tsx`
- Create: `frontend/src/components/data/data-table.tsx`
- Create: `frontend/src/components/ui/secret-input.tsx`
- Create: `frontend/src/components/ui/confirm-dialog.tsx`
- Create: `frontend/src/components/shared-components.test.tsx`
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/components/ui/button.tsx`
- Modify: `frontend/src/components/ui/card.tsx`

**Interfaces:**
- Produces: `PageHeader`, `PageTabs<T extends string>`, `StickyActionBar`, `StatusBanner`, `FilterBar`, `ActiveFilterChip`, `DataTableFrame`, `PaginationBar`, `SecretInput`, `ConfirmDialog`.
- Consumes: existing `Button`, `Input`, `Card`, `cn`, and lucide-react icons.

- [ ] **Step 1: Write failing shared-component tests**

```tsx
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { PageTabs } from "@/components/layout/page-tabs";
import { StickyActionBar } from "@/components/layout/sticky-action-bar";
import { SecretInput } from "@/components/ui/secret-input";

describe("shared redesign components", () => {
  it("changes the active page tab through the typed callback", async () => {
    const onChange = vi.fn();
    render(<PageTabs active="items" onChange={onChange} tabs={[{ id: "items", label: "Positionen" }, { id: "raw", label: "Rohtext" }]} />);
    await userEvent.click(screen.getByRole("tab", { name: "Rohtext" }));
    expect(onChange).toHaveBeenCalledWith("raw");
  });

  it("keeps save and cancel actions available in the sticky action bar", () => {
    render(<StickyActionBar message="3 Felder geändert" onCancel={vi.fn()} onSave={vi.fn()} saving={false} />);
    expect(screen.getByRole("button", { name: "Abbrechen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Änderungen speichern" })).toBeInTheDocument();
  });

  it("does not send the masked placeholder as a changed secret", async () => {
    const onChange = vi.fn();
    render(<SecretInput aria-label="API-Key" masked value="********" onChangeValue={onChange} />);
    expect(screen.getByLabelText("API-Key")).toHaveValue("");
    await userEvent.type(screen.getByLabelText("API-Key"), "new-secret");
    expect(onChange).toHaveBeenLastCalledWith("new-secret");
  });
});
```

- [ ] **Step 2: Run the test and confirm the primitives are missing**

Run: `cd frontend && npm test -- src/components/shared-components.test.tsx`

Expected: FAIL with module-resolution errors for the new component files.

- [ ] **Step 3: Implement the primitive contracts**

Implement these exact public signatures:

```tsx
import type { InputHTMLAttributes } from "react";

export interface PageTab<T extends string> { id: T; label: string; count?: number }
export function PageTabs<T extends string>(props: { active: T; onChange: (id: T) => void; tabs: PageTab<T>[] }): JSX.Element;

export function StickyActionBar(props: {
  message: string;
  onCancel: () => void;
  onSave: () => void;
  saveDisabled?: boolean;
  saving: boolean;
}): JSX.Element;

export function SecretInput(props: Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> & {
  masked: boolean;
  onChangeValue: (value: string) => void;
  value: string;
}): JSX.Element;
```

`PageTabs` must render `role="tablist"`, buttons with `role="tab"`, and `aria-selected`. `SecretInput` must display an empty field with placeholder `Unverändert` when `masked` is true and the supplied value is `********`. `ConfirmDialog` must render nothing when closed, use `role="dialog"`, `aria-modal="true"`, and distinct cancel/confirm buttons.

Add named CSS variables in `styles.css`:

```css
@theme {
  --font-sans: Inter, ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  --color-brand-50: #eff6ff;
  --color-brand-100: #dbeafe;
  --color-brand-600: #2563eb;
  --color-brand-700: #1d4ed8;
  --color-canvas: #f5f7fb;
  --color-panel: #ffffff;
  --color-ink: #172033;
  --color-muted: #667085;
}

@layer utilities {
  .tabular-nums { font-variant-numeric: tabular-nums; }
}
```

Update `Button` primary classes to blue and `Card` to use `rounded-xl`, a neutral border, and no heavier than `shadow-sm`.

- [ ] **Step 4: Run focused tests and build**

Run: `cd frontend && npm test -- src/components/shared-components.test.tsx && npm run build`

Expected: all shared-component tests PASS and Vite build exits 0.

- [ ] **Step 5: Commit the shared foundation**

```bash
git add frontend/src/styles.css frontend/src/components/ui frontend/src/components/layout frontend/src/components/feedback frontend/src/components/data frontend/src/components/shared-components.test.tsx
git commit -m "feat(frontend): add refined UI foundations"
```

### Task 2: Application Shell and Navigation

**Files:**
- Modify: `frontend/src/components/app-shell.tsx`
- Modify: `frontend/src/components/app-shell.test.tsx`
- Create: `frontend/src/components/session-access.tsx`
- Modify: `frontend/src/App.tsx`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: `PageHeader` from Task 1.
- Produces: grouped `NavigationItem` metadata with optional `group` and `count`; `SessionAccess` keeps session authentication reachable without a permanent token field in the daily page header.

- [ ] **Step 1: Replace shell tests with the approved navigation contract**

```tsx
const navigation = [
  { href: "#/", label: "Übersicht", icon: Home, group: "workspace" as const },
  { href: "#/receipts", label: "Bons", icon: ReceiptText, group: "workspace" as const },
  { href: "#/products", label: "Produkte", icon: Boxes, group: "workspace" as const, count: 12 },
  { href: "#/settings", label: "Einstellungen", icon: Settings, group: "manage" as const }
];

it("groups navigation and marks nested receipt routes active", () => {
  render(<AppShell navigation={navigation} route="/receipts/42" utility={<button>Zugriff</button>}><p>Bon</p></AppShell>);
  expect(screen.getByText("Arbeitsbereich")).toBeInTheDocument();
  expect(screen.getByText("Verwalten")).toBeInTheDocument();
  for (const link of screen.getAllByRole("link", { name: "Bons" })) {
    expect(link).toHaveAttribute("aria-current", "page");
  }
  expect(screen.getByText("12")).toHaveAccessibleName("12 offene Aufgaben");
  expect(screen.queryByLabelText("API-Token")).not.toBeInTheDocument();
  expect(screen.getByRole("button", { name: "Zugriff" })).toBeInTheDocument();
});

it("keeps the session token entry reachable when no token exists", async () => {
  const onTokenChange = vi.fn();
  render(<SessionAccess apiToken="" onTokenChange={onTokenChange} />);
  await userEvent.click(screen.getByRole("button", { name: "API-Zugriff einrichten" }));
  await userEvent.type(screen.getByLabelText("APP_API_TOKEN"), "session-token");
  await userEvent.click(screen.getByRole("button", { name: "Für diese Sitzung verwenden" }));
  expect(onTokenChange).toHaveBeenCalledWith("session-token");
});
```

- [ ] **Step 2: Run the shell tests and confirm failure**

Run: `cd frontend && npm test -- src/components/app-shell.test.tsx src/App.test.tsx`

Expected: FAIL because grouping/count metadata is unsupported and the token field still renders.

- [ ] **Step 3: Implement grouped shell navigation**

Use this interface:

```tsx
export interface NavigationItem {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  group: "workspace" | "manage";
  count?: number;
}

interface AppShellProps {
  children: ReactNode;
  navigation: NavigationItem[];
  route: string;
  utility?: ReactNode;
}
```

Render a persistent labeled sidebar at `lg`, a compact mobile navigation, group labels “Arbeitsbereich” and “Verwalten,” and an accessible count badge. Remove token props and the permanent token form from `AppShell`. Render the optional `utility` in the shell header.

Implement `SessionAccess` with this interface:

```tsx
export function SessionAccess(props: {
  apiToken: string;
  onTokenChange: (token: string) => void;
}): JSX.Element;
```

When no token exists it renders “API-Zugriff einrichten”; when a token exists it renders “API-Zugriff aktiv.” Activating either control opens a dialog with an `APP_API_TOKEN` password input, “Für diese Sitzung verwenden,” “Token entfernen,” and “Abbrechen.” Continue storing the token only in `sessionStorage` through the existing `App.tsx` handler. Pass `<SessionAccess apiToken={apiToken} onTokenChange={handleTokenChange} />` through the shell `utility` prop. This keeps first-run access possible without exposing the secret field on every page. Rename the visible navigation label “Dashboard” to “Übersicht.”

- [ ] **Step 4: Run shell tests and build**

Run: `cd frontend && npm test -- src/components/app-shell.test.tsx src/App.test.tsx && npm run build`

Expected: tests PASS; build exits 0; no API-token input appears in the shell.

- [ ] **Step 5: Commit the shell**

```bash
git add frontend/src/App.tsx frontend/src/App.test.tsx frontend/src/components/app-shell.tsx frontend/src/components/app-shell.test.tsx frontend/src/components/session-access.tsx
git commit -m "feat(frontend): redesign application shell"
```

### Task 3: Dashboard Information Hierarchy

**Files:**
- Modify: `frontend/src/pages/dashboard-page.tsx`
- Create: `frontend/src/pages/dashboard-page.test.tsx`
- Modify: `frontend/src/components/category-chart.tsx`

**Interfaces:**
- Consumes: `PageHeader`, `StatusBanner`, `DataTableFrame`; existing `DashboardDTO`, `SyncStatusDTO`, `BonusReportDTO`, and `ReportByCategoryDTO` remain unchanged.
- Produces: dashboard links `#/search?uncategorizedOnly=true` and `#/receipts/:id` unchanged.

- [ ] **Step 1: Write an information-parity dashboard test**

Mock `ApiClient` methods and assert the rendered page includes all six metric labels, sync data, category analysis, recent receipts, bonus detail, sync log, and period control. Also click “Ohne Kategorie” and assert `window.location.hash` becomes `#/search?uncategorizedOnly=true`.

Use these expected labels exactly:

```ts
const requiredLabels = [
  "Aktueller Monat",
  "Vormonat",
  "Aktuelles Jahr",
  "Delta zum Vormonat",
  "Ohne Kategorie",
  "Bonus neu",
  "Ausgaben nach Kategorie",
  "Letzte Bons",
  "Bonus neu im Zeitraum",
  "Sync-Log"
];
```

- [ ] **Step 2: Run the dashboard test and confirm structural assertions fail**

Run: `cd frontend && npm test -- src/pages/dashboard-page.test.tsx`

Expected: FAIL for the new page header/status-strip semantics before migration.

- [ ] **Step 3: Migrate dashboard JSX without changing loading logic**

Keep `loadDashboard`, `triggerSync`, `rangeFor`, and DTO handling unchanged. Start the page with this header:

```tsx
<PageHeader
  actions={(
    <DashboardActions
      customDateFrom={customDateFrom}
      customDateTo={customDateTo}
      loading={loading}
      onCustomDateFromChange={setCustomDateFrom}
      onCustomDateToChange={setCustomDateTo}
      onRangeChange={setRange}
      onSync={triggerSync}
      range={range}
      syncTriggering={syncTriggering}
    />
  )}
  context="Übersicht / Finanzen"
  title="Finanzübersicht"
/>
```

Implement `DashboardActions` in the same file by moving the current `RangeControls` markup and Sync button into one component with the props shown above. After the header, render `SyncStatusBanner`; the six existing `KpiCard` instances in `grid gap-3 sm:grid-cols-2 xl:grid-cols-6`; category analysis and recent receipts in `grid gap-4 xl:grid-cols-[minmax(0,1.12fr)_minmax(24rem,0.88fr)]`; and bonus plus sync log in `grid gap-4 xl:grid-cols-2`. Keep category chart and category table together. Keep all current empty/loading/error states.

- [ ] **Step 4: Run dashboard tests and build**

Run: `cd frontend && npm test -- src/pages/dashboard-page.test.tsx && npm run build`

Expected: test PASS and build exits 0.

- [ ] **Step 5: Commit dashboard migration**

```bash
git add frontend/src/pages/dashboard-page.tsx frontend/src/pages/dashboard-page.test.tsx frontend/src/components/category-chart.tsx
git commit -m "feat(frontend): refine dashboard hierarchy"
```

### Task 4: Receipt List and Detail Navigation

**Files:**
- Modify: `frontend/src/pages/receipts-page.tsx`
- Create: `frontend/src/pages/receipts-page.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: existing `selectedReceiptId`; `PageHeader`, `PageTabs`, `DataTableFrame`.
- Produces: `ReceiptDetailTab = "items" | "data" | "raw" | "ai" | "suggestions"` and session-scoped list-view restoration.

- [ ] **Step 1: Add receipt list/detail parity tests**

Create tests that assert:

```ts
const listColumns = ["Datum", "Geschäft", "Betrag", "Positionen", "Status", "Import"];
const detailTabs = ["Positionen", "Bon-Daten", "Rohtext", "KI-Protokoll", "Regelvorschläge"];
```

The list test must verify status/store/date/include-deleted controls and Sync action. The detail test must verify Paperless, reparse, edit, delete, parse-source badge, product family/variant, category/source, and all five tabs.

- [ ] **Step 2: Run receipt tests and confirm the new detail tabs fail**

Run: `cd frontend && npm test -- src/pages/receipts-page.test.tsx`

Expected: FAIL because receipt detail is not yet organized into the approved tabs.

- [ ] **Step 3: Introduce full-width list/detail modes**

Replace the permanent two-column list/detail grid with:

```tsx
return selectedReceiptId === null ? (
  <ReceiptListPanel
    filters={filters}
    listLoading={listLoading}
    onFilterChange={updateFilters}
    onPageChange={setPage}
    onReceiptSelect={openReceipt}
    onSortChange={changeSort}
    onSync={triggerSync}
    page={page}
    receipts={receipts}
    selectedReceiptId={null}
    sortBy={sortBy}
    sortDir={sortDir}
    syncing={syncing}
  />
) : (
  <ReceiptDetailPanel
    applyingSuggestionId={applyingSuggestionId}
    categories={categories}
    categoriesById={categoriesById}
    deleting={deleting}
    detailLoading={detailLoading}
    draft={draft}
    editMode={editMode}
    onApplyAiSuggestion={applyAiSuggestion}
    onBack={returnToReceiptList}
    onCancelEdit={cancelReceiptEdit}
    onDeleteReceipt={deleteSelectedReceipt}
    onDraftChange={setDraft}
    onEdit={() => setEditMode(true)}
    onReparse={startReparseSelectedReceipt}
    onSave={saveDraft}
    onSetOverwriteManualEdits={setOverwriteManualEdits}
    overwriteManualEdits={overwriteManualEdits}
    receipt={selectedReceipt}
    aiParsingLogs={aiParsingLogs}
    parseRuleSuggestions={parseRuleSuggestions}
    reparsing={reparsing}
    saving={saving}
  />
);
```

Define `openReceipt`, `returnToReceiptList`, and `cancelReceiptEdit` in `ReceiptsPage`: `openReceipt` stores list state then changes the hash; `returnToReceiptList` changes the hash to `#/receipts`; `cancelReceiptEdit` restores `toDraft(selectedReceipt)` and disables edit mode. Keep all existing API calls, reparse decision logic, deletion, AI suggestion adoption, draft conversion, and validation unchanged. Store list view state before navigation:

```ts
const RECEIPT_LIST_STATE_KEY = "ebon.receiptListState";
sessionStorage.setItem(RECEIPT_LIST_STATE_KEY, JSON.stringify({ filters, page, sortBy, sortDir, scrollY: window.scrollY }));
```

Restore only validated properties on mount and call `window.scrollTo({ top: scrollY })` after list data loads. Do not persist raw receipt data or secrets.

- [ ] **Step 4: Run receipt tests and build**

Run: `cd frontend && npm test -- src/pages/receipts-page.test.tsx && npm run build`

Expected: tests PASS and build exits 0.

- [ ] **Step 5: Commit receipt navigation**

```bash
git add frontend/src/pages/receipts-page.tsx frontend/src/pages/receipts-page.test.tsx frontend/src/App.tsx
git commit -m "feat(frontend): redesign receipt navigation"
```

### Task 5: Receipt Editing and Search Filters

**Files:**
- Modify: `frontend/src/pages/receipts-page.tsx`
- Modify: `frontend/src/pages/receipts-page.test.tsx`
- Modify: `frontend/src/pages/search-page.tsx`
- Create: `frontend/src/pages/search-page.test.tsx`

**Interfaces:**
- Consumes: `StickyActionBar`, `FilterBar`, `ActiveFilterChip`.
- Produces: `SearchFilterKey` labels for removable chips; all `SearchParams` fields remain unchanged.

- [ ] **Step 1: Add editing and search interaction tests**

Tests must verify the sticky action bar appears only in edit mode, cancel restores the original draft, save calls `updateReceipt`, and manual overwrite confirmation remains available. Search tests must set text, store, date, category, family, variant, amount, and uncategorized-only filters, then assert the corresponding `apiClient.search` arguments.

- [ ] **Step 2: Run tests and confirm sticky/chip assertions fail**

Run: `cd frontend && npm test -- src/pages/receipts-page.test.tsx src/pages/search-page.test.tsx`

Expected: FAIL for the new sticky action bar and active-filter chip UI.

- [ ] **Step 3: Migrate edit and search presentation**

Use `StickyActionBar` exactly as follows:

```tsx
{editMode ? (
  <StickyActionBar
    message={saving ? "Änderungen werden gespeichert" : "Ungespeicherte Änderungen"}
    onCancel={onCancelEdit}
    onSave={onSave}
    saveDisabled={saving}
    saving={saving}
  />
) : null}
```

Keep every existing receipt field and item field. In search, keep text, store, date range, and uncategorized visible; move category, family, variant, and amount range into an expandable “Weitere Filter” region. Derive chips from the actual `SearchParams` state and clear one field per chip without resetting unrelated filters.

- [ ] **Step 4: Run focused tests and build**

Run: `cd frontend && npm test -- src/pages/receipts-page.test.tsx src/pages/search-page.test.tsx && npm run build`

Expected: tests PASS and build exits 0.

- [ ] **Step 5: Commit receipt editing and search**

```bash
git add frontend/src/pages/receipts-page.tsx frontend/src/pages/receipts-page.test.tsx frontend/src/pages/search-page.tsx frontend/src/pages/search-page.test.tsx
git commit -m "feat(frontend): improve receipt editing and search"
```

### Task 6: Product Review Workspace

**Files:**
- Modify: `frontend/src/pages/products-page.tsx`
- Modify: `frontend/src/pages/products-page.test.tsx`

**Interfaces:**
- Consumes: existing `ProductReviewFilters`, correction/split/rule DTOs, `PageTabs`, `FilterBar`, `ConfirmDialog`.
- Produces: `ProductPageTab = "review" | "families" | "variants" | "rules" | "structure" | "prices"`.

- [ ] **Step 1: Extend product tests for the focused queue**

Keep current tests and add assertions that the default tab is “Offen,” the count is visible, the selected review item exposes receipt context and similar-item impact, and the labeled actions “Übernehmen,” “Korrigieren,” “Kein Produkt,” and “Ablehnen” remain available.

- [ ] **Step 2: Run product tests and confirm the workspace assertions fail**

Run: `cd frontend && npm test -- src/pages/products-page.test.tsx`

Expected: existing behavior tests may pass, but new tab/selected-context assertions FAIL.

- [ ] **Step 3: Restructure review JSX while preserving mutations**

Introduce the tab union and render review as a list/context split at `xl`, stacking on smaller screens. Keep all filters: store, family, category, source, status, date range, and confidence maximum. Keep every existing mutation handler. Replace the seven-icon action strip with labeled primary actions and an overflow area for split, rule suggestion, and clear assignment; every icon-only fallback must retain its existing `aria-label` and `title`.

- [ ] **Step 4: Run product tests and build**

Run: `cd frontend && npm test -- src/pages/products-page.test.tsx && npm run build`

Expected: tests PASS and build exits 0.

- [ ] **Step 5: Commit product review migration**

```bash
git add frontend/src/pages/products-page.tsx frontend/src/pages/products-page.test.tsx
git commit -m "feat(frontend): focus product review workflow"
```

### Task 7: Product Master Data and Price Comparison

**Files:**
- Modify: `frontend/src/pages/products-page.tsx`
- Modify: `frontend/src/pages/products-page.test.tsx`
- Modify: `frontend/src/pages/product-price-comparison.tsx`

**Interfaces:**
- Consumes: `ProductPageTab` from Task 6 and all existing product/price DTOs.
- Produces: separate family, variant, rule, structure, and price tab bodies.

- [ ] **Step 1: Add master-data and price information-parity tests**

Assert family and variant tabs distinguish family name, variant name, total quantity/unit, active state, and assignment count. Keep current merge/split preview assertions. Extend price assertions to require Latest, Historical Minimum, Average, Median, observation count, trend, store table, effective/regular price, unit price, outlier state, exclusion reason, and reversible re-inclusion.

- [ ] **Step 2: Run product tests and confirm tab-parity assertions fail**

Run: `cd frontend && npm test -- src/pages/products-page.test.tsx`

Expected: FAIL before master-data cards are separated into tabs.

- [ ] **Step 3: Move master-data sections into dedicated tabs**

Keep all existing create/update/toggle/merge/split/rule functions unchanged. Render exactly one master-data concern at a time. Put `ProductPriceComparison` in the `prices` tab. Place merge and split under `structure`, retaining the preview text for affected items, stores, report impact, and protected assignments.

- [ ] **Step 4: Run product tests and build**

Run: `cd frontend && npm test -- src/pages/products-page.test.tsx && npm run build`

Expected: tests PASS and build exits 0.

- [ ] **Step 5: Commit product administration and prices**

```bash
git add frontend/src/pages/products-page.tsx frontend/src/pages/products-page.test.tsx frontend/src/pages/product-price-comparison.tsx
git commit -m "feat(frontend): organize products and price analysis"
```

### Task 8: Reports Analysis Layout

**Files:**
- Modify: `frontend/src/pages/reports-page.tsx`
- Create: `frontend/src/pages/reports-page.test.tsx`

**Interfaces:**
- Consumes: existing report DTOs, `PageTabs`, `FilterBar`, `DataTableFrame`.
- Produces: one shared filter state controlling chart, table, and CSV export.

- [ ] **Step 1: Write report filter/export parity tests**

Assert tabs for current report types, period choices, custom dates, category, store, grouping, top-product sort, family, variant, chart, table, and CSV action. Change a filter and verify both the report API request and export request receive the same filter object.

- [ ] **Step 2: Run report tests and confirm new tab semantics fail**

Run: `cd frontend && npm test -- src/pages/reports-page.test.tsx`

Expected: FAIL for `role="tab"` and shared active-filter presentation.

- [ ] **Step 3: Migrate reports to the shared analysis frame**

Keep report loading, chart selection, table selection, and CSV behavior unchanged. Render `PageHeader`, `PageTabs`, `FilterBar`, one chart panel, and one table panel. Financial cells use `tabular-nums`; empty/loading/error states use shared primitives.

- [ ] **Step 4: Run report tests and build**

Run: `cd frontend && npm test -- src/pages/reports-page.test.tsx && npm run build`

Expected: tests PASS and build exits 0.

- [ ] **Step 5: Commit reports migration**

```bash
git add frontend/src/pages/reports-page.tsx frontend/src/pages/reports-page.test.tsx
git commit -m "feat(frontend): redesign reports workspace"
```

### Task 9: Task-Based Settings, Backup, and Data Maintenance

**Files:**
- Modify: `frontend/src/pages/settings-page.tsx`
- Create: `frontend/src/pages/settings-page.test.tsx`
- Modify: `frontend/src/App.tsx`

**Interfaces:**
- Consumes: `SecretInput`, `ConfirmDialog`, `PageTabs`, existing settings/backup/maintenance DTOs.
- Produces: `SettingsSection = "connections" | "ai-parser" | "categories" | "rules" | "parser-suggestions" | "backup" | "maintenance" | "system"`.

- [ ] **Step 1: Write settings safety and navigation tests**

Tests must verify all eight section labels, Paperless/OpenRouter connection tests, masked-token behavior, AI parsing controls, categories/rules/parser suggestions, backup validate/dry-run/restore, receipt-data reset, separate product-data reset, and system version. Assert `updateSettings` never receives `********`.

- [ ] **Step 2: Run settings tests and confirm section semantics fail**

Run: `cd frontend && npm test -- src/pages/settings-page.test.tsx`

Expected: FAIL because current tabs do not expose the approved task-based sections or safe `SecretInput` contract.

- [ ] **Step 3: Restructure settings without changing API behavior**

Introduce the exact section union. Move fields and actions to their approved sections. Use `SecretInput` for Paperless and OpenRouter secrets. Before constructing the update request, omit unchanged masked secrets:

```ts
function changedSecret(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed && trimmed !== "********" ? trimmed : undefined;
}
```

Keep backup validation/dry-run/restore and maintenance handlers intact. Place imported-receipt reset and product-data reset in separate danger-zone cards with their existing confirmation phrases and preview results.

- [ ] **Step 4: Run settings tests and build**

Run: `cd frontend && npm test -- src/pages/settings-page.test.tsx && npm run build`

Expected: tests PASS and build exits 0.

- [ ] **Step 5: Commit settings migration**

```bash
git add frontend/src/pages/settings-page.tsx frontend/src/pages/settings-page.test.tsx frontend/src/App.tsx
git commit -m "feat(frontend): organize settings and data safety"
```

### Task 10: Responsive, Accessibility, and Information-Parity Audit

**Files:**
- Modify: `frontend/src/styles.css`
- Modify: `frontend/src/components/app-shell.tsx`
- Modify: `frontend/src/components/shared-components.test.tsx`
- Modify: `frontend/e2e/smoke.mjs`
- Modify: `frontend/src/App.test.tsx`

**Interfaces:**
- Consumes: all migrated pages and shared primitives.
- Produces: stable E2E selectors based on roles/names and a complete frontend redesign.

- [ ] **Step 1: Add accessibility and responsive contract tests**

Assert one main heading per route, labeled navigation, visible focusable mobile menu control, accessible dialogs, tab roles, explicit badge text, and no duplicate action names without a scoped region. Add a test that iterates the main hashes and confirms each renders its canonical title.

- [ ] **Step 2: Update the Selenium smoke flow before styling fixes**

Use role/name selectors where the driver helper supports them and stable attributes elsewhere. The smoke flow must cover:

1. Open Übersicht and verify all six metric labels.
2. Navigate to Bons, filter, open a receipt, switch to Rohtext, enter edit mode, and cancel.
3. Navigate to Suche and apply uncategorized-only.
4. Navigate to Produkte and open review and prices.
5. Navigate to Berichte and switch report tabs.
6. Navigate to Einstellungen and open Connections, Backup, and Data Maintenance without executing destructive actions.

- [ ] **Step 3: Run unit tests and observe remaining failures**

Run: `cd frontend && npm test`

Expected: any remaining failures identify missing accessible names, route titles, or responsive controls; no backend integration is required.

- [ ] **Step 4: Complete responsive and accessibility styling**

Ensure sidebar labels collapse only below the approved breakpoint, mobile navigation keeps all destinations reachable, tables use `overflow-x-auto`, sticky actions do not cover content, dialogs fit within the viewport, and focus rings are visible in both themes. Add bottom padding equal to the mobile navigation/sticky-action height where needed.

- [ ] **Step 5: Run the full frontend verification**

Run: `cd frontend && npm test && npm run build && npm run e2e`

Expected: Vitest reports 0 failures, TypeScript/Vite build exits 0, and Selenium smoke exits 0 using the mock API without real tokens.

- [ ] **Step 6: Run the information-parity checklist**

Compare every page against sections 7–11 of `docs/superpowers/specs/2026-07-11-ebon-ui-redesign-design.md`. Record no missing filters, columns, statuses, actions, previews, confirmations, or safety copy. If a gap is found, add a focused failing test before fixing it.

- [ ] **Step 7: Commit final responsive and E2E hardening**

```bash
git add frontend/src frontend/e2e/smoke.mjs
git commit -m "test(frontend): verify redesigned UI workflows"
```

---

## Completion Gate

The redesign is complete only when:

- All ten tasks are committed in order.
- `cd frontend && npm test` passes with zero failures.
- `cd frontend && npm run build` exits 0.
- `cd frontend && npm run e2e` exits 0 against the mock API.
- Desktop, tablet, and mobile layouts have been manually checked in light and dark mode.
- The information-parity audit finds no removed current behavior.
- No real token, receipt, Paperless data, OpenRouter prompt, or raw AI response appears in source, fixtures, screenshots, or logs.
