import { useCallback, useEffect, useState, type ReactNode } from "react";
import { ArrowDown, ArrowUp, Loader2, Search } from "lucide-react";

import { ActiveFilterChip, FilterBar } from "@/components/data/filter-bar";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { formatCurrency, formatDate, formatNumber } from "@/lib/format";
import type { CategoryDTO, PageResponse, ProductFamilyDTO, ProductVariantDTO, SearchParams, SearchResultDTO } from "@/lib/types";

interface SearchPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
  initialUncategorizedOnly?: boolean;
}

const pageSize = 20;

type SearchFilterKey =
  | "q"
  | "store"
  | "dateFrom"
  | "dateTo"
  | "categoryIds"
  | "productFamilyId"
  | "productVariantId"
  | "amountMin"
  | "amountMax"
  | "uncategorizedOnly";

export function SearchPage({ apiClient, hasApiToken, initialUncategorizedOnly = false }: SearchPageProps) {
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [families, setFamilies] = useState<ProductFamilyDTO[]>([]);
  const [variants, setVariants] = useState<ProductVariantDTO[]>([]);
  const [results, setResults] = useState<PageResponse<SearchResultDTO> | null>(null);
  const [filters, setFilters] = useState<SearchParams>({
    page: 0,
    size: pageSize,
    sortBy: "receiptDate",
    sortDir: "desc",
    uncategorizedOnly: initialUncategorizedOnly
  });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [advancedFiltersOpen, setAdvancedFiltersOpen] = useState(false);

  useEffect(() => {
    setFilters((current) => ({
      ...current,
      page: 0,
      uncategorizedOnly: initialUncategorizedOnly,
      categoryIds: initialUncategorizedOnly ? [] : current.categoryIds
    }));
  }, [initialUncategorizedOnly]);

  const loadSearch = useCallback(async () => {
    if (!hasApiToken) {
      setResults(null);
      setCategories([]);
      setFamilies([]);
      setVariants([]);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const [categoryResponse, resultResponse, familyResponse, variantResponse] = await Promise.all([
        apiClient.categories(false),
        apiClient.search(filters),
        apiClient.productFamilies(),
        apiClient.productVariants()
      ]);
      setCategories(categoryResponse);
      setResults(resultResponse);
      setFamilies(familyResponse);
      setVariants(variantResponse);
    } catch (loadError) {
      setError(toUserMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [apiClient, filters, hasApiToken]);

  useEffect(() => {
    void loadSearch();
  }, [loadSearch]);

  function updateFilter(next: Partial<SearchParams>) {
    setFilters((current) => ({ ...current, ...next, page: 0 }));
  }

  function toggleSort(sortBy: NonNullable<SearchParams["sortBy"]>) {
    setFilters((current) => ({
      ...current,
      page: 0,
      sortBy,
      sortDir: current.sortBy === sortBy && current.sortDir === "desc" ? "asc" : "desc"
    }));
  }

  function clearFilter(key: SearchFilterKey) {
    switch (key) {
      case "q":
      case "store":
      case "dateFrom":
      case "dateTo":
      case "categoryIds":
        updateFilter({ [key]: undefined });
        break;
      case "productFamilyId":
      case "productVariantId":
      case "amountMin":
      case "amountMax":
        updateFilter({ [key]: null });
        break;
      case "uncategorizedOnly":
        updateFilter({ uncategorizedOnly: false });
        break;
    }
  }

  const visibleVariants = filters.productFamilyId == null
    ? variants
    : variants.filter((variant) => variant.productFamilyId === filters.productFamilyId);
  const activeFilters = getActiveFilters(filters, categories, families, variants);

  if (!hasApiToken) {
    return <AuthRequired />;
  }

  return (
    <div className="space-y-4">
      {error ? <ErrorBox message={error} /> : null}

      <Card>
        <CardHeader>
          <CardTitle>Suche</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <FilterBar>
            <div className="grid min-w-0 flex-1 gap-3 lg:grid-cols-[minmax(260px,1fr)_180px_150px_150px_190px_auto]">
              <Field label="Suchtext">
                <Input
                  onChange={(event) => updateFilter({ q: event.target.value || undefined })}
                  placeholder="Artikel oder Geschäft"
                  value={filters.q ?? ""}
                />
              </Field>
              <Field label="Geschäft">
                <Input
                  onChange={(event) => updateFilter({ store: event.target.value || undefined })}
                  placeholder="REWE, dm..."
                  value={filters.store ?? ""}
                />
              </Field>
              <Field label="Von">
                <Input onChange={(event) => updateFilter({ dateFrom: event.target.value || undefined })} type="date" value={filters.dateFrom ?? ""} />
              </Field>
              <Field label="Bis">
                <Input onChange={(event) => updateFilter({ dateTo: event.target.value || undefined })} type="date" value={filters.dateTo ?? ""} />
              </Field>
              <label className="mt-6 flex h-10 items-center gap-2 rounded-md border border-zinc-200 px-3 text-sm dark:border-zinc-800">
                <input
                  checked={Boolean(filters.uncategorizedOnly)}
                  onChange={(event) => updateFilter({
                    uncategorizedOnly: event.target.checked,
                    categoryIds: event.target.checked ? [] : filters.categoryIds
                  })}
                  type="checkbox"
                />
                Ohne Kategorie
              </label>
              <Button aria-expanded={advancedFiltersOpen} onClick={() => setAdvancedFiltersOpen((open) => !open)} variant="secondary">
                Weitere Filter
              </Button>
            </div>
          </FilterBar>

          {advancedFiltersOpen ? <div className="grid gap-3 rounded-xl border border-zinc-200 p-3 lg:grid-cols-[minmax(220px,1fr)_minmax(220px,1fr)_minmax(220px,1fr)_140px_140px] dark:border-zinc-800">
            <Field label="Kategorien">
              <select
                className={selectClassName}
                disabled={filters.uncategorizedOnly}
                multiple
                onChange={(event) => updateFilter({
                  categoryIds: Array.from(event.target.selectedOptions).map((option) => Number(option.value)),
                  uncategorizedOnly: false
                })}
                value={(filters.categoryIds ?? []).map(String)}
              >
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Produktfamilie">
              <select
                className={selectClassName}
                onChange={(event) => updateFilter({
                  productFamilyId: event.target.value ? Number(event.target.value) : null,
                  productVariantId: null
                })}
                value={filters.productFamilyId ?? ""}
              >
                <option value="">Alle Produktfamilien</option>
                {families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
              </select>
            </Field>
            <Field label="Produktvariante">
              <select
                className={selectClassName}
                onChange={(event) => updateFilter({ productVariantId: event.target.value ? Number(event.target.value) : null })}
                value={filters.productVariantId ?? ""}
              >
                <option value="">Alle Varianten</option>
                {visibleVariants.map((variant) => <option key={variant.id} value={variant.id}>{variant.name}</option>)}
              </select>
            </Field>
            <Field label="Betrag von">
              <Input onChange={(event) => updateFilter({ amountMin: numberOrNull(event.target.value) })} step="0.01" type="number" value={filters.amountMin ?? ""} />
            </Field>
            <Field label="Betrag bis">
              <Input onChange={(event) => updateFilter({ amountMax: numberOrNull(event.target.value) })} step="0.01" type="number" value={filters.amountMax ?? ""} />
            </Field>
          </div> : null}

          {activeFilters.length ? (
            <div aria-label="Aktive Filter" className="flex flex-wrap gap-2">
              {activeFilters.map((filter) => (
                <ActiveFilterChip key={filter.key} label={filter.label} onRemove={() => clearFilter(filter.key)} />
              ))}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <CardTitle>Suchergebnisse</CardTitle>
          <span className="text-sm text-zinc-500 dark:text-zinc-400">
            {formatNumber(results?.totalElements)} Positionen
          </span>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          {loading ? (
            <div className="space-y-2">
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
              <Skeleton className="h-10 w-full" />
            </div>
          ) : results?.content.length ? (
            <table className="w-full min-w-[940px] text-sm">
              <thead>
                <tr className="border-b border-zinc-100 text-left text-xs uppercase text-zinc-500 dark:border-zinc-900 dark:text-zinc-400">
                  <SortableHeader active={filters.sortBy === "receiptDate"} direction={filters.sortDir ?? "desc"} label="Datum" onClick={() => toggleSort("receiptDate")} />
                  <SortableHeader active={filters.sortBy === "storeName"} direction={filters.sortDir ?? "desc"} label="Geschäft" onClick={() => toggleSort("storeName")} />
                  <SortableHeader active={filters.sortBy === "description"} direction={filters.sortDir ?? "desc"} label="Position" onClick={() => toggleSort("description")} />
                  <SortableHeader active={filters.sortBy === "totalPrice"} direction={filters.sortDir ?? "desc"} label="Betrag" onClick={() => toggleSort("totalPrice")} right />
                  <th className="px-3 py-2 font-medium">Kategorie</th>
                  <th className="px-3 py-2 font-medium">Produkt</th>
                </tr>
              </thead>
              <tbody>
                {results.content.map((result) => (
                  <tr
                    className="cursor-pointer border-b border-zinc-100 last:border-0 hover:bg-zinc-50 dark:border-zinc-900 dark:hover:bg-zinc-900/60"
                    key={result.receiptItemId}
                    onClick={() => {
                      window.location.hash = `#/receipts/${result.receiptId}`;
                    }}
                  >
                    <td className="px-3 py-2">{formatDate(result.receiptDate)}</td>
                    <td className="px-3 py-2">{result.storeName ?? "Unbekannt"}</td>
                    <td className="px-3 py-2">
                      <Highlighted text={result.description} terms={result.highlights} />
                    </td>
                    <td className="px-3 py-2 text-right font-medium">{formatCurrency(result.totalPrice)}</td>
                    <td className="px-3 py-2">
                      {result.categoryId == null ? <Badge>Ohne Kategorie</Badge> : <Badge tone="blue">{result.categoryName}</Badge>}
                    </td>
                    <td className="px-3 py-2">
                      {result.productFamilyName ? <><div className="font-medium">{result.productFamilyName}</div><div className="text-xs text-zinc-500">{result.productVariantName ?? "Variante offen"}{result.normalizedUnitPrice == null ? "" : ` · ${formatCurrency(result.normalizedUnitPrice)} / ${result.normalizedUnit}`}</div></> : <span className="text-zinc-500">Ohne Produkt</span>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          ) : (
            <EmptyState text="Keine Treffer" />
          )}
        </CardContent>
        <div className="flex flex-col gap-2 border-t border-zinc-100 px-4 py-3 text-sm dark:border-zinc-900 md:flex-row md:items-center md:justify-between">
          <span className="text-zinc-500 dark:text-zinc-400">
            Seite {formatNumber((results?.page ?? 0) + 1)} von {formatNumber(results?.totalPages ?? 1)}
          </span>
          <div className="flex gap-2">
            <Button disabled={(filters.page ?? 0) <= 0 || loading} onClick={() => setFilters((current) => ({ ...current, page: Math.max((current.page ?? 0) - 1, 0) }))} size="sm" variant="secondary">
              Zurück
            </Button>
            <Button disabled={loading || !results || (filters.page ?? 0) >= results.totalPages - 1} onClick={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) + 1 }))} size="sm" variant="secondary">
              Weiter
            </Button>
          </div>
        </div>
      </Card>
    </div>
  );
}

function SortableHeader({ active, direction, label, onClick, right = false }: { active: boolean; direction: "asc" | "desc"; label: string; onClick: () => void; right?: boolean }) {
  return (
    <th className={`px-3 py-2 font-medium ${right ? "text-right" : ""}`}>
      <button className={`inline-flex items-center gap-1 ${right ? "justify-end" : ""}`} onClick={onClick}>
        {label}
        {active ? direction === "asc" ? <ArrowUp className="h-3 w-3" /> : <ArrowDown className="h-3 w-3" /> : null}
      </button>
    </th>
  );
}

function Highlighted({ terms, text }: { terms: string[]; text: string }) {
  const term = terms.find((entry) => entry.trim());
  if (!term) {
    return <>{text}</>;
  }

  const index = text.toLowerCase().indexOf(term.toLowerCase());
  if (index < 0) {
    return <>{text}</>;
  }

  return (
    <>
      {text.slice(0, index)}
      <mark className="rounded bg-amber-100 px-0.5 text-amber-900 dark:bg-amber-900 dark:text-amber-100">{text.slice(index, index + term.length)}</mark>
      {text.slice(index + term.length)}
    </>
  );
}

function AuthRequired() {
  return (
    <Card>
      <CardContent className="flex min-h-72 flex-col items-center justify-center gap-3 text-center">
        <Search className="h-8 w-8 text-zinc-500" />
        <div>
          <h2 className="text-base font-semibold">API-Token erforderlich</h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Danach kann die Suche geladen werden.</p>
        </div>
      </CardContent>
    </Card>
  );
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</span>
      {children}
    </label>
  );
}

function EmptyState({ text }: { text: string }) {
  return <div className="rounded-md border border-dashed border-zinc-200 px-4 py-8 text-center text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">{text}</div>;
}

function ErrorBox({ message }: { message: string }) {
  return <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-200">{message}</div>;
}

function numberOrNull(value: string): number | null {
  return value === "" ? null : Number(value);
}

function getActiveFilters(
  filters: SearchParams,
  categories: CategoryDTO[],
  families: ProductFamilyDTO[],
  variants: ProductVariantDTO[]
): Array<{ key: SearchFilterKey; label: string }> {
  const active: Array<{ key: SearchFilterKey; label: string }> = [];
  if (filters.q) active.push({ key: "q", label: `Suchtext: ${filters.q}` });
  if (filters.store) active.push({ key: "store", label: `Geschäft: ${filters.store}` });
  if (filters.dateFrom) active.push({ key: "dateFrom", label: `Von: ${filters.dateFrom}` });
  if (filters.dateTo) active.push({ key: "dateTo", label: `Bis: ${filters.dateTo}` });
  if (filters.categoryIds?.length) {
    const names = filters.categoryIds.map((id) => categories.find((category) => category.id === id)?.name ?? `#${id}`);
    active.push({ key: "categoryIds", label: `Kategorie: ${names.join(", ")}` });
  }
  if (filters.productFamilyId != null) {
    const name = families.find((family) => family.id === filters.productFamilyId)?.name ?? `#${filters.productFamilyId}`;
    active.push({ key: "productFamilyId", label: `Produktfamilie: ${name}` });
  }
  if (filters.productVariantId != null) {
    const name = variants.find((variant) => variant.id === filters.productVariantId)?.name ?? `#${filters.productVariantId}`;
    active.push({ key: "productVariantId", label: `Produktvariante: ${name}` });
  }
  if (filters.amountMin != null) active.push({ key: "amountMin", label: `Betrag von: ${filters.amountMin}` });
  if (filters.amountMax != null) active.push({ key: "amountMax", label: `Betrag bis: ${filters.amountMax}` });
  if (filters.uncategorizedOnly) active.push({ key: "uncategorizedOnly", label: "Ohne Kategorie" });
  return active;
}

function toUserMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Die Anfrage konnte nicht verarbeitet werden.";
}

const selectClassName = "min-h-10 w-full rounded-md border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-950 shadow-sm dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-50";
