# eBon UI Redesign – Design Specification

Date: 2026-07-11

Status: Approved for implementation planning

Design direction: Refined Current

## 1. Objective

Redesign the complete eBon frontend so it is more intuitive, usable, modern, and visually coherent without removing existing information or functionality. The redesign may reorganize content and workflows, but it must preserve the current domain behavior, API contracts, validation, safety confirmations, and operational information density.

The approved direction deliberately evolves the current UI instead of replacing it with a generic consumer dashboard. eBon remains a dense single-user expense and receipt tool for desktop and tablet, with a usable mobile presentation.

## 2. Non-goals

- No backend or API contract changes solely for visual polish.
- No removal of existing filters, status information, logs, review actions, report data, or settings.
- No new financial metrics that the backend does not already provide.
- No marketing-style landing page, decorative hero content, or whitespace that displaces operational data.
- No persistence of UI-only secrets or masked secret placeholders.
- No redesign of domain rules for parsing, categorization, products, backup, restore, or data reset.

## 3. Design principles

1. **Information first:** Keep dense, scannable tables and expose all existing data.
2. **One clear primary action:** Every page has one visually dominant next action; secondary and destructive actions remain distinct.
3. **Stable context:** Filters, sorting, pagination, selected tabs, and scroll position survive list-to-detail navigation where practical.
4. **Progressive disclosure:** Complex functions remain available but are grouped into tabs or task-based subsections instead of one long page.
5. **Semantic status:** Color reinforces text labels but never replaces them. AI, rule, manual, error, warning, and uncategorized states remain explicit.
6. **Safe mutation:** Merge, split, retroactive application, restore, reset, deletion, and full-text AI parsing retain preview and confirmation steps.
7. **Consistent interaction:** Tables, filters, empty states, loading states, toasts, dialogs, sticky actions, and pagination behave consistently across pages.
8. **System theme:** Light and dark mode follow the operating-system preference.

## 4. Visual language

- Neutral blue-gray surfaces with restrained use of blue for active navigation and primary actions.
- White or near-white work panels on a light gray canvas; equivalent high-contrast zinc surfaces in dark mode.
- Compact spacing suitable for operational work, with clearer grouping than the current uniformly boxed layout.
- Medium corner radii, subtle borders, and low-elevation shadows. Avoid glass effects, gradients as decoration, and oversized cards.
- Strong typographic hierarchy: compact context label, clear page title, readable table text, and tabular numerals for financial values.
- Existing category icons remain supported. Lucide icons remain the standard icon family.
- Status palettes:
  - Green: successful, active, included, rule-confirmed.
  - Amber: review required, uncertain, warning, inactive where attention is useful.
  - Red: failure, excluded, destructive confirmation.
  - Purple: AI-derived information.
  - Neutral gray: uncategorized, unavailable, no product, or inactive context.

## 5. Application shell and navigation

### Desktop

A persistent labeled sidebar provides the main navigation. It has two groups:

- **Arbeitsbereich:** Übersicht, Bons, Suche, Produkte, Berichte.
- **Verwalten:** Kategorien & Regeln, Einstellungen.

Small count badges appear only when they represent actionable work, such as open product reviews. The API token input is removed from the global header as a normal daily control and belongs in the appropriate connection/settings flow. The shell header contains page context, page title, optional filters, and page actions.

### Tablet and mobile

- Tablet may use a collapsible labeled sidebar while preserving the same hierarchy.
- Mobile uses a compact navigation drawer or bottom navigation for the most important destinations, with the remaining destinations in a menu.
- Tables may scroll horizontally when preserving column relationships is more usable than card conversion.
- Detail pages stack sections vertically, while primary actions remain sticky and reachable.
- No data or safety confirmation is omitted at smaller breakpoints.

## 6. Shared page anatomy

Pages use the same order when applicable:

1. Breadcrumb or compact context label.
2. Page title and primary/secondary actions.
3. Error, warning, success, or sync status.
4. Page-level filters and active-filter chips.
5. Metrics or review counts.
6. Primary table, chart, form, or work queue.
7. Secondary details, logs, or audit context.
8. Pagination or sticky save/cancel actions.

Async loading uses skeletons for stable layouts and spinners inside actions. Errors use user-safe messages without secrets or stack traces. Successful mutations use concise toasts. Empty states explain both the state and the next useful action when one exists.

## 7. Dashboard

The dashboard preserves all current content:

- Sync status, last sync time, removed count, error count, and manual sync action.
- Current month, previous month, current year, month delta, uncategorized item count, and newly earned bonus.
- Period control, including current month, last quarter, current year, previous year, and custom dates.
- Category chart and category amount table.
- Recent receipts with date, store, branch, amount, and parse status.
- Bonus detail by program/type.
- Sync log.

The approved layout keeps the current recognizable sequence but improves hierarchy:

- Page action and period control are in the page header.
- Sync becomes one compact status strip.
- Six metrics use one consistent row on large desktop and a responsive grid below it.
- Uncategorized count is an attention card linking directly to the filtered queue.
- Category analysis and recent receipts form the primary two-column area.
- Bonus and sync log form the secondary row.

## 8. Receipts and search

### Receipt list

The receipt list becomes a full-width work page instead of sharing a permanently narrow split view with details. It preserves:

- Parse-status, store, date-from/date-to, and include-deleted filters.
- Sortable date, store, total, parse status, and import columns.
- Position count, deleted state, pagination, selection, and sync action.

The list-to-detail transition preserves filter, sort, pagination, and scroll context when returning.

### Receipt detail

Receipt detail uses a full-width page with:

- Header actions for Paperless, reparse, edit, and delete.
- Summary for date/time, store/branch, total, bonus, parse status, parse source, and deletion context.
- Tabs for Positions, Receipt Data, Raw Text, AI Log, and Parser Rule Suggestions.

The Positions tab keeps category, category source, product family, product variant, assignment source/status, unit price, discounts, and AI suggestions visible. AI parsing logs remain prompt-free by default. Parser rule suggestions keep trigger, problem, rationale, validation, affected context, and acceptance controls.

### Receipt editing

- Receipt header fields, bonus fields, and receipt item fields remain editable.
- Add/remove item behavior remains available.
- Inline validation identifies missing descriptions, invalid amounts, and invalid totals.
- A sticky action bar shows unsaved state and provides Cancel and Save.
- Reparse preserves the Paperless raw-text decision dialog.
- Overwriting manual edits and FULL_TEXT AI parsing require explicit confirmation.

### Search

All current search criteria remain available: text, store, dates, categories, product family, product variant, amount range, and uncategorized-only. Frequently used criteria stay visible; secondary criteria may move under “Weitere Filter.” Active criteria appear as removable chips. Results retain sortable receipt date, store, description, amount, category, family/variant, normalized unit price, pagination, highlights, and navigation to the source receipt.

## 9. Product review and product management

### Product review queue

The review queue is a focused task page with filters for uncertainty, store, family, category, date, source, and status. Default ordering prioritizes high-value/high-frequency uncertain assignments as already defined by the backend.

The selected item shows receipt context, normalized text, price, quantity, current assignment, suggested family/variant, confidence/reason, and similar-item impact. Actions retain:

- Accept suggestion.
- Correct assignment using existing or new family/variant.
- Mark NO_PRODUCT.
- Reject suggestion.
- Clear assignment.
- Split to a new family or variant.
- Suggest a product rule.

The most frequent actions use labeled buttons. Rare operations may use icon buttons with tooltips. Creating a rule or applying a decision retroactively always shows a preview count and confirmation.

### Product master data

Product management is divided into tabs:

- Families.
- Variants.
- Product rules.
- Merge and split operations.

Families and variants use dense tables with activity status, category, assignment counts, and navigation to detail. Family and variant differences remain visible everywhere; unknown size never appears as a specific variant. Merge/split previews show affected receipt items, stores, report impact, and protected/manual assignments before confirmation.

## 10. Reports and product price comparison

Reports keep the existing tabs and filters for time range, category, store, grouping, top-product sorting, family, and variant. Chart and table use the same filter state. CSV export remains a page-level action.

Product price comparison retains:

- Family/variant scope.
- Date and store filters.
- Store versus store-and-branch grouping.
- Latest, minimum, average, median, and observation count.
- Trend chart.
- Variant summary.
- Store comparison table.
- Source price observations.
- Effective versus derivable regular price.
- Unit price.
- Outlier, included/excluded state, exclusion reason, and reversible inclusion.

Statistics always expose their comparable unit. The source observation table stays available below the overview so users can audit every aggregate.

## 11. Settings and administration

Settings use task-based subsections rather than one long form:

- Connections.
- AI & Parser.
- Categories.
- Categorization Rules.
- Parser Rule Suggestions.
- Backup & Restore.
- Data Maintenance.
- System Information.

Each subsection saves independently where current API behavior permits. Connection sections show a safe status and explicit Test action. Masked tokens remain unchanged unless the user enters a new value; `********` is never persisted.

Backup and restore show file selection, validation, dry-run results, affected record counts, secret reconfiguration warnings, and final confirmation. Data reset operations live in a visually separate danger zone. Imported-receipt reset and product-data reset remain distinct and describe exactly what is retained or deleted.

## 12. Component structure

The implementation should establish reusable frontend primitives without obscuring domain behavior:

- `AppShell`, `SidebarNavigation`, `MobileNavigation`, `PageHeader`.
- `StatusBanner`, `MetricCard`, `FilterBar`, `ActiveFilterChips`.
- `DataTable`, sortable header, pagination, empty/loading states.
- `PageTabs`, `StickyActionBar`, `ConfirmDialog`, `PreviewDialog`.
- Domain badges for parse, category source, product assignment, deletion, AI, and price inclusion.
- Form sections and secret fields with masked-value semantics.

Large domain pages should be split into focused page sections and dialogs. Existing API calls and DTO types remain centralized in the API and type modules.

## 13. State and data flow

- Page-level URL/hash state should represent the selected entity and important navigation context where feasible.
- Filter state remains local unless it is needed for direct navigation, such as the uncategorized queue.
- Data loading stays explicit per page; mutations refresh only the affected list/detail/report data.
- Optimistic updates are avoided for high-impact domain mutations. The UI waits for backend confirmation and then shows the authoritative result.
- Unsaved form state cannot be silently discarded by tab or route changes.

## 14. Accessibility

- All actions have visible labels or accessible names.
- Tables retain semantic headers and sortable-state announcements.
- Focus returns to the triggering control when dialogs close.
- Dialogs trap focus and support Escape where safe.
- Color is never the only status indicator.
- Text, borders, focus rings, and badges meet WCAG AA contrast targets in light and dark modes.
- Touch targets remain usable on tablet and mobile.

## 15. Error handling and safety

- API validation errors appear next to the relevant field where possible and in a page-level summary when necessary.
- Network and backend failures preserve entered form data.
- Reparse, merge, split, rule application, backup restore, reset, deletion, and outlier exclusion use domain-specific confirmation text.
- Full-text AI confirmation explicitly states that full receipt text will be transmitted.
- Secret-bearing URLs, tokens, prompts, and raw AI responses never appear in generic error messages or logs shown in the UI.

## 16. Verification strategy

### Automated

- Update component tests for the new shell, navigation, tabs, filter state, sticky actions, status badges, dialogs, and responsive behaviors.
- Retain or adapt tests for all current receipt, search, report, settings, product review, and price comparison behavior.
- Add tests proving no current filter/action is lost during the redesign.
- Add focused tests for masked-secret handling, destructive confirmations, FULL_TEXT confirmation, list-context restoration, and previews before high-impact mutations.
- Run `npm run build` and the existing frontend test suite.
- Run the Selenium E2E smoke flow after the redesign is integrated.

### Visual and manual

- Verify every main page at representative desktop, tablet, and mobile widths.
- Verify system light and dark themes.
- Check long store/product names, large financial values, empty datasets, loading, errors, many filter chips, and long receipt item lists.
- Check keyboard navigation, focus visibility, dialog focus, and sticky action reachability.
- Compare the redesigned page inventory against the current application and this specification to confirm information parity.

## 17. Implementation sequencing

Implement the redesign as reviewable increments:

1. Design tokens, shared primitives, and application shell.
2. Dashboard.
3. Receipt list, receipt detail, receipt editing, and search.
4. Product review and product management.
5. Reports and product price comparison.
6. Settings, backup/restore, parser suggestions, categories, rules, and data maintenance.
7. Responsive refinement, dark mode, accessibility, E2E updates, and final information-parity audit.

Each increment must compile and keep the existing application usable. Avoid a single all-at-once replacement.
