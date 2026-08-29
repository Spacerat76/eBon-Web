import { Check, CircleOff, Eraser, GitFork, Loader2, Pencil, Plus, RefreshCw, Sparkles, X } from "lucide-react";
import { useEffect, useMemo, useRef, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { StatusBanner } from "@/components/feedback/status-banner";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { FilterBar } from "@/components/data/filter-bar";
import { PageTabs } from "@/components/layout/page-tabs";
import { Input } from "@/components/ui/input";
import { ModalDialog } from "@/components/ui/modal-dialog";
import type { ApiClient } from "@/lib/api";
import { ProductPriceComparison } from "@/pages/product-price-comparison";
import type {
  CategoryDTO,
  PageResponse,
  ProductAssignmentCorrectionRequest,
  ProductAssignmentSource,
  ProductAssignmentStatus,
  ProductChangePreviewDTO,
  ProductFamilyMergeRequest,
  ProductFamilyRequest,
  ProductFamilySplitRequest,
  ProductFamilyDTO,
  ProductReviewItemDTO,
  ProductReviewParams,
  ProductRuleDTO,
  ProductRuleSuggestionDTO,
  ProductVariantDTO,
  ProductVariantMergeRequest,
  ProductVariantRequest,
  ProductVariantSplitRequest,
  SearchResultDTO
} from "@/lib/types";

export type ProductPageTab = "review" | "families" | "variants" | "rules" | "structure" | "prices";
type ReviewFilters = Omit<ProductReviewParams, "page" | "size" | "store" | "status" | "confidenceMax"> & {
  store: string;
  status: "" | ProductAssignmentStatus;
  confidenceMax: string;
};
type SplitCandidate = Pick<SearchResultDTO, "receiptItemId" | "description" | "productFamilyId" | "productFamilyName" | "productVariantId" | "productVariantName">;

const defaultFilters: ReviewFilters = { store: "", status: "NEEDS_REVIEW", confidenceMax: "" };

export function ProductsPage({ apiClient, hasApiToken }: { apiClient: ApiClient; hasApiToken: boolean }) {
  const [activeTab, setActiveTab] = useState<ProductPageTab>("review");
  const [review, setReview] = useState<PageResponse<ProductReviewItemDTO> | null>(null);
  const [families, setFamilies] = useState<ProductFamilyDTO[]>([]);
  const [variants, setVariants] = useState<ProductVariantDTO[]>([]);
  const [rules, setRules] = useState<ProductRuleDTO[]>([]);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [filters, setFilters] = useState<ReviewFilters>(defaultFilters);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [pageMessageTone, setPageMessageTone] = useState<"success" | "error">("success");
  const [selected, setSelected] = useState<ProductReviewItemDTO | null>(null);
  const [splitItem, setSplitItem] = useState<SplitCandidate | null>(null);
  const [suggestion, setSuggestion] = useState<ProductRuleSuggestionDTO | null>(null);
  const [suggestionItemId, setSuggestionItemId] = useState<number | null>(null);

  const variantsByFamily = useMemo(() => {
    const grouped = new Map<number, ProductVariantDTO[]>();
    variants.forEach((variant) => grouped.set(variant.productFamilyId, [...(grouped.get(variant.productFamilyId) ?? []), variant]));
    return grouped;
  }, [variants]);

  async function load(nextPage = page, activeFilters = filters) {
    if (!hasApiToken) {
      setReview(null);
      return;
    }
    setLoading(true);
    try {
      const [nextReview, nextFamilies, nextVariants, nextRules, nextCategories] = await Promise.all([
        apiClient.productReview({
          ...activeFilters,
          page: nextPage,
          size: 30,
          store: activeFilters.store || undefined,
          status: activeFilters.status || undefined,
          confidenceMax: activeFilters.confidenceMax ? Number(activeFilters.confidenceMax) : undefined
        }),
        apiClient.productFamilies(),
        apiClient.productVariants(),
        apiClient.productRules(),
        apiClient.categories(false)
      ]);
      setReview(nextReview);
      setFamilies(nextFamilies);
      setVariants(nextVariants);
      setRules(nextRules);
      setCategories(nextCategories);
      setPage(nextReview.page);
    } catch (error) {
      setPageMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Produktdaten konnten nicht geladen werden.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load(0);
  }, [hasApiToken]);

  async function action(label: string, callback: () => Promise<unknown>) {
    setLoading(true);
    try {
      await callback();
      setPageMessageTone("success");
      setMessage(label);
      setSelected(null);
      setSplitItem(null);
      setSuggestion(null);
      setSuggestionItemId(null);
      await load();
    } catch (error) {
      setPageMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Aktion konnte nicht ausgeführt werden.");
      setLoading(false);
    }
  }

  async function proposeRule(item: ProductReviewItemDTO) {
    setLoading(true);
    try {
      setSuggestionItemId(item.receiptItemId);
      setSuggestion(await apiClient.suggestProductRule(item.receiptItemId, {
        matchType: "EXACT",
        storeSpecific: true,
        priority: 100
      }));
    } catch (error) {
      setPageMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Regelvorschlag konnte nicht erstellt werden.");
    } finally {
      setLoading(false);
    }
  }

  function applyFilters() {
    void load(0);
  }

  function resetFilters() {
    setFilters(defaultFilters);
    void load(0, defaultFilters);
  }

  if (!hasApiToken) {
    return <TokenHint />;
  }

  return (
    <div className="space-y-4">
      <div className="flex flex-col gap-3 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Produkte</p>
          <h2 className="text-xl font-semibold text-zinc-950 dark:text-zinc-50">Produktzuordnung prüfen</h2>
        </div>
        <Button onClick={() => void load()} size="sm" variant="secondary">
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
          Daten neu laden
        </Button>
      </div>

      {loading ? <StatusBanner ariaLabel="Produktdaten werden verarbeitet" busy title="Produktdaten werden verarbeitet" /> : null}
      {message ? <StatusBanner title={pageMessageTone === "error" ? "Produktaktion fehlgeschlagen" : "Produktaktion abgeschlossen"} tone={pageMessageTone}>{message}</StatusBanner> : null}

      <PageTabs
        active={activeTab}
        onChange={setActiveTab}
        tabs={[
          { id: "review", label: "Offen", count: review?.totalElements ?? 0 },
          { id: "families", label: "Familien" },
          { id: "variants", label: "Varianten" },
          { id: "rules", label: "Regeln" },
          { id: "structure", label: "Struktur" },
          { id: "prices", label: "Preisvergleich" }
        ]}
      />

      {activeTab === "review" ? (
        <ReviewTable
          categories={categories}
          families={families}
          filters={filters}
          loading={loading}
          onAccept={(item) => void action("Produktvorschlag übernommen.", () => apiClient.acceptProductReview(item.receiptItemId))}
          onClear={(item) => void action("Produktzuordnung entfernt.", () => apiClient.clearProductReviewAssignment(item.receiptItemId))}
          onCorrect={setSelected}
          onFiltersChange={setFilters}
          onNoProduct={(item) => void action("Position als keine Produktposition markiert.", () => apiClient.markProductReviewNoProduct(item.receiptItemId))}
          onPageChange={(nextPage) => void load(nextPage)}
          onReject={(item) => void action("Produktvorschlag abgelehnt.", () => apiClient.rejectProductReview(item.receiptItemId))}
          onResetFilters={resetFilters}
          onSubmitFilters={applyFilters}
          onSuggestRule={(item) => void proposeRule(item)}
          review={review}
        />
      ) : activeTab === "prices" ? (
        <ProductPriceComparison apiClient={apiClient} families={families} variants={variants} />
      ) : (
        <MasterData activeTab={activeTab} apiClient={apiClient} categories={categories} families={families} onChanged={load} onSplit={setSplitItem} rules={rules} variants={variants} />
      )}

      {selected ? <CorrectionDialog families={families} item={selected} loading={loading} onCancel={() => setSelected(null)} onConfirm={(request) => void action("Produktzuordnung korrigiert.", () => apiClient.correctProductReview(selected.receiptItemId, request))} onNoProduct={() => void action("Position als keine Produktposition markiert.", () => apiClient.markProductReviewNoProduct(selected.receiptItemId))} variantsByFamily={variantsByFamily} /> : null}
      {splitItem ? <SplitDialog apiClient={apiClient} item={splitItem} loading={loading} onCancel={() => setSplitItem(null)} onConfirm={(label, callback) => void action(label, callback)} /> : null}
      {suggestion && suggestionItemId != null ? <RuleSuggestionDialog suggestion={suggestion} loading={loading} onCancel={() => { setSuggestion(null); setSuggestionItemId(null); }} onConfirm={(applyToExisting) => void action("Produktregel gespeichert.", () => apiClient.acceptProductRuleSuggestion(suggestionItemId, { rule: suggestion.rule, applyToExisting, confirm: true }))} /> : null}
    </div>
  );
}

function ReviewTable({ categories, families, filters, loading, onAccept, onClear, onCorrect, onFiltersChange, onNoProduct, onPageChange, onReject, onResetFilters, onSubmitFilters, onSuggestRule, review }: {
  categories: CategoryDTO[];
  families: ProductFamilyDTO[];
  filters: ReviewFilters;
  loading: boolean;
  onAccept: (item: ProductReviewItemDTO) => void;
  onClear: (item: ProductReviewItemDTO) => void;
  onCorrect: (item: ProductReviewItemDTO) => void;
  onFiltersChange: (filters: ReviewFilters) => void;
  onNoProduct: (item: ProductReviewItemDTO) => void;
  onPageChange: (page: number) => void;
  onReject: (item: ProductReviewItemDTO) => void;
  onResetFilters: () => void;
  onSubmitFilters: () => void;
  onSuggestRule: (item: ProductReviewItemDTO) => void;
  review: PageResponse<ProductReviewItemDTO> | null;
}) {
  const [focusedItemId, setFocusedItemId] = useState<number | null>(null);
  const items = review?.content ?? [];
  const focusedItem = items.find((item) => item.receiptItemId === focusedItemId) ?? items[0] ?? null;
  const change = (value: Partial<ReviewFilters>) => onFiltersChange({ ...filters, ...value });

  useEffect(() => {
    if (items.length === 0) {
      setFocusedItemId(null);
    } else if (!items.some((item) => item.receiptItemId === focusedItemId)) {
      setFocusedItemId(items[0].receiptItemId);
    }
  }, [focusedItemId, items]);

  return (
    <div className="space-y-4">
      <FilterBar>
        <div className="grid min-w-0 flex-1 gap-2 md:grid-cols-3 xl:grid-cols-5">
          <Input aria-label="Store filtern" className="h-9" onChange={(event) => change({ store: event.target.value })} placeholder="Store" value={filters.store} />
          <select aria-label="Produktfamilie filtern" className={selectClass} onChange={(event) => change({ productFamilyId: event.target.value ? Number(event.target.value) : undefined })} value={filters.productFamilyId ?? ""}>
            <option value="">Alle Familien</option>
            {families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
          </select>
          <select aria-label="Kategorie filtern" className={selectClass} onChange={(event) => change({ categoryId: event.target.value ? Number(event.target.value) : undefined })} value={filters.categoryId ?? ""}>
            <option value="">Alle Kategorien</option>
            {categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}
          </select>
          <select aria-label="Quelle filtern" className={selectClass} onChange={(event) => change({ source: (event.target.value || undefined) as ProductAssignmentSource | undefined })} value={filters.source ?? ""}>
            <option value="">Alle Quellen</option>
            <option value="RULE">Regel</option>
            <option value="HISTORY">Historie</option>
            <option value="AI">KI</option>
            <option value="MANUAL">Manuell</option>
          </select>
          <select aria-label="Status filtern" className={selectClass} onChange={(event) => change({ status: event.target.value as "" | ProductAssignmentStatus })} value={filters.status}>
            <option value="">Alle Status</option>
            <option value="NEEDS_REVIEW">Prüfen</option>
            <option value="AUTO_ASSIGNED">Automatisch</option>
            <option value="CONFIRMED">Bestätigt</option>
            <option value="REJECTED">Abgelehnt</option>
            <option value="NO_PRODUCT">Kein Produkt</option>
          </select>
          <Input aria-label="Zeitraum von" className="h-9" onChange={(event) => change({ dateFrom: event.target.value || undefined })} type="date" value={filters.dateFrom ?? ""} />
          <Input aria-label="Zeitraum bis" className="h-9" onChange={(event) => change({ dateTo: event.target.value || undefined })} type="date" value={filters.dateTo ?? ""} />
          <Input aria-label="Maximale Konfidenz" className="h-9" max="1" min="0" onChange={(event) => change({ confidenceMax: event.target.value })} placeholder="Konfidenz bis" step="0.01" type="number" value={filters.confidenceMax} />
          <div className="flex gap-2">
            <Button onClick={onSubmitFilters} size="sm" variant="secondary">Filtern</Button>
            <Button onClick={onResetFilters} size="sm" variant="ghost">Reset</Button>
          </div>
        </div>
      </FilterBar>

      <div className="grid gap-4 xl:grid-cols-[minmax(0,0.9fr)_minmax(28rem,1.1fr)]">
        <Card>
          <CardHeader className="gap-1">
            <CardTitle>Offene Produktzuordnungen</CardTitle>
            <p className="text-sm text-zinc-500">Häufige und teure Fälle stehen oben.</p>
          </CardHeader>
          <CardContent className="p-0">
            <div className="divide-y divide-zinc-100 dark:divide-zinc-900">
              {items.map((item) => {
                const active = item.receiptItemId === focusedItem?.receiptItemId;
                return (
                  <button
                    aria-pressed={active}
                    className={`w-full px-4 py-3 text-left transition ${active ? "bg-blue-50 dark:bg-blue-950/40" : "hover:bg-zinc-50 dark:hover:bg-zinc-900"}`}
                    key={item.receiptItemId}
                    onClick={() => setFocusedItemId(item.receiptItemId)}
                    type="button"
                  >
                    <span className="flex items-start justify-between gap-3">
                      <span>
                        <span className="block font-medium text-zinc-950 dark:text-zinc-50">{item.description}</span>
                        <span className="mt-1 block text-xs text-zinc-500">{formatDate(item.receiptDate)} · {item.storeName ?? "Unbekannt"}</span>
                      </span>
                      {item.confidence == null ? null : <Badge>{Math.round(item.confidence * 100)} %</Badge>}
                    </span>
                    <span className="mt-2 block text-sm text-zinc-700 dark:text-zinc-200">{item.suggestedProductFamilyName ?? item.currentProductFamilyName ?? "Ohne Vorschlag"}</span>
                    <span className="block text-xs text-zinc-500">{productDetailLabel(item)}</span>
                  </button>
                );
              })}
            </div>
            {items.length === 0 ? <p className="p-6 text-sm text-zinc-500">Keine offenen Produktzuordnungen.</p> : null}
            {review && review.totalPages > 1 ? (
              <div className="flex items-center justify-between border-t border-zinc-200 px-4 py-3 text-sm dark:border-zinc-800">
                <span>Seite {review.page + 1} von {review.totalPages}</span>
                <div className="flex gap-2">
                  <Button disabled={loading || review.page === 0} onClick={() => onPageChange(review.page - 1)} size="sm" variant="secondary">Zurück</Button>
                  <Button disabled={loading || review.page + 1 >= review.totalPages} onClick={() => onPageChange(review.page + 1)} size="sm" variant="secondary">Weiter</Button>
                </div>
              </div>
            ) : null}
          </CardContent>
        </Card>

        {focusedItem ? (
          <Card>
            <CardHeader className="gap-2">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div>
                  <p className="text-xs font-medium uppercase tracking-wide text-zinc-500">Ausgewählte Position</p>
                  <CardTitle className="mt-1 text-base">Prüfung für {focusedItem.description}</CardTitle>
                </div>
                <div className="flex gap-1">
                  <Badge tone={focusedItem.assignmentStatus === "NEEDS_REVIEW" ? "yellow" : "blue"}>{labelStatus(focusedItem.assignmentStatus)}</Badge>
                  {focusedItem.confidence == null ? null : <Badge>{Math.round(focusedItem.confidence * 100)} %</Badge>}
                </div>
              </div>
            </CardHeader>
            <CardContent className="space-y-5">
              <section className="grid gap-3 rounded-lg border border-zinc-200 p-4 dark:border-zinc-800 sm:grid-cols-2">
                <div>
                  <h2 className="text-sm font-semibold text-zinc-950 dark:text-zinc-50">Bon-Kontext</h2>
                  <button className="mt-2 text-left text-sm text-blue-700 hover:underline dark:text-blue-300" onClick={() => { window.location.hash = `#/receipts/${focusedItem.receiptId}`; }} type="button">
                    {formatDate(focusedItem.receiptDate)}
                  </button>
                  <p className="text-sm text-zinc-600 dark:text-zinc-300">{focusedItem.storeName ?? "Unbekannt"}{focusedItem.storeBranch ? ` · ${focusedItem.storeBranch}` : ""}</p>
                  <p className="mt-2 text-sm text-zinc-600 dark:text-zinc-300">{formatQuantity(focusedItem)} · {formatEuro(focusedItem.totalPrice)}</p>
                  {focusedItem.categoryName ? <p className="mt-1 text-xs text-zinc-500">Kategorie: {focusedItem.categoryName}</p> : null}
                </div>
                <div>
                  <h2 className="text-sm font-semibold text-zinc-950 dark:text-zinc-50">Vorschlag</h2>
                  <p className="mt-2 text-sm font-medium">{focusedItem.suggestedProductFamilyName ?? focusedItem.currentProductFamilyName ?? "Ohne Vorschlag"}</p>
                  <p className="text-sm text-zinc-500">Details: {productDetailLabel(focusedItem)}</p>
                  <p className="mt-2 text-xs text-zinc-600 dark:text-zinc-300">{focusedItem.reason ?? "Unklare Zuordnung"}</p>
                </div>
              </section>

              <section className="rounded-lg bg-amber-50 p-4 dark:bg-amber-950/30">
                <p className="text-sm font-medium text-amber-950 dark:text-amber-100">{focusedItem.possibleRetroactiveItems} passende offene Positionen inkl. dieser</p>
                <p className="mt-1 text-xs text-amber-800 dark:text-amber-200">Eine Korrektur kann optional auf passende offene Positionen desselben Stores angewendet werden.</p>
              </section>

              <div className="flex flex-wrap gap-2">
                <Button disabled={loading || focusedItem.suggestedProductFamilyId == null} onClick={() => onAccept(focusedItem)} size="sm">
                  <Check className="h-4 w-4" />Übernehmen
                </Button>
                <Button disabled={loading} onClick={() => onCorrect(focusedItem)} size="sm" variant="secondary">
                  <Pencil className="h-4 w-4" />Korrigieren
                </Button>
                <Button aria-label="Als kein Produkt markieren" disabled={loading} onClick={() => onNoProduct(focusedItem)} size="sm" title="Als kein Produkt markieren" variant="secondary">
                  <CircleOff className="h-4 w-4" />Kein Produkt
                </Button>
                <Button aria-label="Vorschlag ablehnen" disabled={loading} onClick={() => onReject(focusedItem)} size="sm" title="Vorschlag ablehnen" variant="ghost">
                  <X className="h-4 w-4" />Ablehnen
                </Button>
              </div>

              <details className="rounded-lg border border-zinc-200 p-3 dark:border-zinc-800">
                <summary className="cursor-pointer text-sm font-medium text-zinc-700 dark:text-zinc-200">Weitere Aktionen</summary>
                <div className="mt-3 flex flex-wrap gap-2">
                  <IconButton disabled={loading} label="Regel vorschlagen" onClick={() => onSuggestRule(focusedItem)}><Sparkles className="h-4 w-4" /></IconButton>
                  <IconButton disabled={loading} label="Zuordnung entfernen" onClick={() => onClear(focusedItem)}><Eraser className="h-4 w-4" /></IconButton>
                </div>
              </details>
            </CardContent>
          </Card>
        ) : null}
      </div>
    </div>
  );
}

function MasterData({ activeTab, apiClient, categories, families, onChanged, onSplit, rules, variants }: { activeTab: Exclude<ProductPageTab, "review" | "prices">; apiClient: ApiClient; categories: CategoryDTO[]; families: ProductFamilyDTO[]; onChanged: () => Promise<void>; onSplit: (item: SplitCandidate) => void; rules: ProductRuleDTO[]; variants: ProductVariantDTO[] }) {
  const [familyName, setFamilyName] = useState("");
  const [variantFamilyId, setVariantFamilyId] = useState("");
  const [variantName, setVariantName] = useState("");
  const [variantSize, setVariantSize] = useState("");
  const [variantUnit, setVariantUnit] = useState("");
  const [editingVariant, setEditingVariant] = useState<ProductVariantDTO | null>(null);
  const [familySource, setFamilySourceState] = useState("");
  const [familyTarget, setFamilyTargetState] = useState("");
  const [variantSource, setVariantSourceState] = useState("");
  const [variantTarget, setVariantTargetState] = useState("");
  const mergeGeneration = useRef(0);
  const [preview, setPreview] = useState<
    | { kind: "family"; value: ProductChangePreviewDTO; request: ProductFamilyMergeRequest }
    | { kind: "variant"; value: ProductChangePreviewDTO; request: ProductVariantMergeRequest }
    | null
  >(null);
  const [rulePreview, setRulePreview] = useState<{ rule: ProductRuleDTO; matchingItemsCount: number } | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [messageTone, setMessageTone] = useState<"success" | "error" | "info">("info");
  const [mutationBusy, setMutationBusy] = useState(false);
  const [splitFamilyId, setSplitFamilyId] = useState("");
  const [splitVariantId, setSplitVariantId] = useState("");
  const [splitQuery, setSplitQuery] = useState("");
  const [splitStore, setSplitStore] = useState("");
  const [splitResults, setSplitResults] = useState<PageResponse<SearchResultDTO> | null>(null);
  const [splitLoading, setSplitLoading] = useState(false);
  const variantsByFamily = { get: (id: number) => ({ length: families.find((family) => family.id === id)?.variantCount ?? 0 }) };

  function invalidateMerge(setter: (value: string) => void, value: string) {
    mergeGeneration.current += 1;
    setter(value);
    setPreview(null);
    setMessage(null);
    setMessageTone("info");
  }
  const setFamilySource = (value: string) => invalidateMerge(setFamilySourceState, value);
  const setFamilyTarget = (value: string) => invalidateMerge(setFamilyTargetState, value);
  const setVariantSource = (value: string) => invalidateMerge(setVariantSourceState, value);
  const setVariantTarget = (value: string) => invalidateMerge(setVariantTargetState, value);

  async function perform(success: string, task: () => Promise<void>): Promise<boolean> {
    if (mutationBusy) return false;
    setMutationBusy(true);
    setMessage(null);
    try {
      await task();
      setMessageTone("success");
      setMessage(success);
      return true;
    } catch (error) {
      setMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Produktaktion konnte nicht ausgeführt werden.");
      return false;
    } finally {
      setMutationBusy(false);
    }
  }
  async function createFamily() { if (!familyName.trim()) return; await perform("Produktfamilie wurde angelegt.", async () => { await apiClient.createProductFamily({ name: familyName.trim(), isActive: true }); setFamilyName(""); await onChanged(); }); }
  function variantRequest(): ProductVariantRequest { return { productFamilyId: Number(variantFamilyId), name: variantName.trim(), totalQuantity: variantSize ? Number(variantSize) : null, totalUnit: variantUnit || null, isActive: true }; }
  async function saveVariant() { if (!variantFamilyId || !variantName.trim()) return; await perform(editingVariant ? "Produktvariante wurde aktualisiert." : "Produktvariante wurde angelegt.", async () => { if (editingVariant) { await apiClient.updateProductVariant(editingVariant.id, { ...variantRequest(), isActive: editingVariant.isActive }); } else { await apiClient.createProductVariant(variantRequest()); } setEditingVariant(null); setVariantName(""); setVariantSize(""); setVariantUnit(""); await onChanged(); }); }
  async function calculateMergePreview(kind: "family" | "variant") {
    const generation = mergeGeneration.current;
    setMessageTone("info");
    setMessage("Merge-Vorschau wird berechnet.");
    try {
      if (kind === "family") {
        const request = { sourceFamilyId: Number(familySource), targetFamilyId: Number(familyTarget) };
        const value = await apiClient.previewProductFamilyMerge(request);
        if (generation === mergeGeneration.current) setPreview({ kind, value, request });
      } else {
        const request = { sourceVariantId: Number(variantSource), targetVariantId: Number(variantTarget) };
        const value = await apiClient.previewProductVariantMerge(request);
        if (generation === mergeGeneration.current) setPreview({ kind, value, request });
      }
      if (generation === mergeGeneration.current) {
        setMessageTone("success");
        setMessage("Merge-Vorschau wurde berechnet.");
      }
    } catch (error) {
      if (generation === mergeGeneration.current) {
        setMessageTone("error");
        setMessage(error instanceof Error ? error.message : "Merge-Vorschau konnte nicht berechnet werden.");
      }
    }
  }
  async function previewFamilyMerge() { if (!familySource || !familyTarget || familySource === familyTarget) return; await calculateMergePreview("family"); }
  async function previewVariantMerge() { if (!variantSource || !variantTarget || variantSource === variantTarget) return; await calculateMergePreview("variant"); }
  async function applyMerge() { if (!preview) return; await perform("Historische Zuordnungen wurden zusammengeführt.", async () => { if (preview.kind === "family") { await apiClient.applyProductFamilyMerge({ ...preview.request, confirm: true }); setFamilySourceState(""); setFamilyTargetState(""); } else { await apiClient.applyProductVariantMerge({ ...preview.request, confirm: true }); setVariantSourceState(""); setVariantTargetState(""); } setPreview(null); await onChanged(); }); }
  async function toggleFamily(family: ProductFamilyDTO) { await perform("Produktfamilie wurde aktualisiert.", async () => { await apiClient.updateProductFamily(family.id, { name: family.name, defaultCategoryId: family.defaultCategoryId, isActive: !family.isActive }); await onChanged(); }); }
  async function updateFamily(family: ProductFamilyDTO, request: ProductFamilyRequest) { return perform("Produktfamilie wurde aktualisiert.", async () => { await apiClient.updateProductFamily(family.id, request); await onChanged(); }); }
  async function toggleVariant(variant: ProductVariantDTO) { await perform("Produktvariante wurde aktualisiert.", async () => { await apiClient.updateProductVariant(variant.id, { productFamilyId: variant.productFamilyId, name: variant.name, unitQuantity: variant.unitQuantity, unit: variant.unit, packageQuantity: variant.packageQuantity, packageDescription: variant.packageDescription, totalQuantity: variant.totalQuantity, totalUnit: variant.totalUnit, gtin: variant.gtin, isActive: !variant.isActive }); await onChanged(); }); }
  async function toggleRule(rule: ProductRuleDTO) { await perform("Produktregel wurde aktualisiert.", async () => { await apiClient.updateProductRule(rule.id, { productFamilyId: rule.productFamilyId, productVariantId: rule.productVariantId, storeName: rule.storeName, matchType: rule.matchType, matchValue: rule.matchValue, priority: rule.priority, isActive: !rule.isActive }); await onChanged(); }); }
  async function previewRuleApply(rule: ProductRuleDTO) { await perform("Regelvorschau wurde berechnet.", async () => { const result = await apiClient.previewProductRule({ storeName: rule.storeName, matchType: rule.matchType, matchValue: rule.matchValue }); setRulePreview({ rule, matchingItemsCount: result.matchingItemsCount }); }); }
  async function applyRule() { if (!rulePreview) return; await perform("Produktregel wurde auf die Vorschau angewendet.", async () => { await apiClient.applyProductRule(rulePreview.rule.id); setRulePreview(null); await onChanged(); }); }

  useEffect(() => {
    setPreview(null);
  }, [familySource, familyTarget, variantSource, variantTarget]);

  async function loadSplitCandidates(nextPage = 0, familyId = splitFamilyId, variantId = splitVariantId) {
    if (!familyId) { setSplitResults(null); return; }
    setSplitLoading(true);
    try {
      setSplitResults(await apiClient.search({
        q: splitQuery || undefined,
        store: splitStore || undefined,
        productFamilyId: Number(familyId),
        productVariantId: variantId ? Number(variantId) : null,
        page: nextPage,
        size: 20,
        sortBy: "receiptDate",
        sortDir: "desc"
      }));
    } catch (error) {
      setMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Produktpositionen konnten nicht geladen werden.");
    } finally {
      setSplitLoading(false);
    }
  }
  const visibleVariants = variantFamilyId ? variants.filter((variant) => variant.productFamilyId === Number(variantFamilyId)) : [];
  const familyAssignments = new Map(families.map((family) => [family.id, family.assignedItemsCount]));
  const variantAssignments = new Map(variants.map((variant) => [variant.id, variant.assignedItemsCount]));

  useEffect(() => {
    if (activeTab !== "structure") return;
    if (!splitFamilyId && families[0]) {
      const initialFamilyId = String(families[0].id);
      setSplitFamilyId(initialFamilyId);
      void loadSplitCandidates(0, initialFamilyId);
    }
  }, [activeTab, families, splitFamilyId]);

  return <>
    {mutationBusy ? <StatusBanner ariaLabel="Produktaktion wird ausgeführt" busy title="Produktaktion wird ausgeführt" /> : null}
    {message ? <StatusBanner title={messageTone === "error" ? "Produktaktion fehlgeschlagen" : messageTone === "info" ? "Produktaktion läuft" : "Produktaktion abgeschlossen"} tone={messageTone}>{message}</StatusBanner> : null}
    <fieldset className="contents min-w-0" disabled={mutationBusy}>
    {activeTab === "families" ? <FamilyEditor busy={mutationBusy} categories={categories} families={families} onSave={updateFamily} /> : null}
    {activeTab === "families" ? <Card><CardHeader><CardTitle>Produktfamilien</CardTitle></CardHeader><CardContent className="space-y-3"><div className="flex gap-2"><Input aria-label="Neue Produktfamilie" onChange={(event) => setFamilyName(event.target.value)} placeholder="Neue Produktfamilie" value={familyName} /><Button onClick={() => void createFamily()} size="sm"><Plus className="h-4 w-4" />Anlegen</Button></div><div className="divide-y divide-zinc-100 rounded-md border border-zinc-200 dark:divide-zinc-900 dark:border-zinc-800">{families.map((family) => { const variantCount = variantsByFamily.get(family.id)?.length ?? 0; return <div className="flex flex-col gap-2 px-3 py-3 text-sm sm:flex-row sm:items-center sm:justify-between" key={family.id}><div><p className="font-medium">{family.name}</p><p className="text-xs text-zinc-500">{variantCount} {variantCount === 1 ? "Variante" : "Varianten"} · {assignmentLabel(familyAssignments.get(family.id) ?? 0)}</p><p className="text-xs text-zinc-500">Kategorie: {family.defaultCategoryName ?? "Keine Standardkategorie"}</p></div><div className="flex items-center gap-2"><Badge tone={family.isActive ? "green" : "yellow"}>{family.isActive ? "aktiv" : "inaktiv"}</Badge><Button aria-label={`Familie ${family.name} ${family.isActive ? "deaktivieren" : "aktivieren"}`} onClick={() => void toggleFamily(family)} size="sm" variant="ghost">{family.isActive ? "Deaktivieren" : "Aktivieren"}</Button></div></div>; })}</div></CardContent></Card> : null}

    {activeTab === "variants" ? <Card><CardHeader><CardTitle>Produktvarianten</CardTitle></CardHeader><CardContent className="space-y-3"><div className="grid gap-2 sm:grid-cols-2"><select aria-label="Variantenfamilie" className={selectClass} onChange={(event) => { setVariantFamilyId(event.target.value); setEditingVariant(null); setVariantName(""); setVariantSize(""); setVariantUnit(""); }} value={variantFamilyId}><option value="">Produktfamilie wählen</option>{families.filter((family) => family.isActive).map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}</select><Input aria-label="Variantenname" onChange={(event) => setVariantName(event.target.value)} placeholder="Variantenname" value={variantName} /><Input aria-label="Variantenmenge" min="0.001" onChange={(event) => setVariantSize(event.target.value)} placeholder="Gesamtmenge, z. B. 0.5" step="0.001" type="number" value={variantSize} /><Input aria-label="Varianteneinheit" onChange={(event) => setVariantUnit(event.target.value)} placeholder="Einheit, z. B. l" value={variantUnit} /></div><div className="flex gap-2"><Button disabled={!variantFamilyId} onClick={() => void saveVariant()} size="sm">{editingVariant ? "Variante aktualisieren" : "Variante anlegen"}</Button>{editingVariant ? <Button onClick={() => { setEditingVariant(null); setVariantName(""); setVariantSize(""); setVariantUnit(""); }} size="sm" variant="secondary">Abbrechen</Button> : null}</div>{variantFamilyId ? <div className="divide-y divide-zinc-100 rounded-md border border-zinc-200 dark:divide-zinc-900 dark:border-zinc-800">{visibleVariants.map((variant) => { const variantLabel = `${variant.productFamilyName}: ${variant.name}`; return <div className="flex flex-col gap-2 px-3 py-3 text-sm sm:flex-row sm:items-center sm:justify-between" key={variant.id}><div><p className="font-medium">{variant.name}</p><p className="text-xs text-zinc-500">{variant.productFamilyName} · {variant.totalQuantity ? `Gesamtmenge ${variant.totalQuantity} ${variant.totalUnit ?? ""}` : "ohne feste Gesamtmenge"} · {assignmentLabel(variantAssignments.get(variant.id) ?? 0)}</p></div><div className="flex items-center gap-1"><Badge tone={variant.isActive ? "green" : "yellow"}>{variant.isActive ? "aktiv" : "inaktiv"}</Badge><Button aria-label={`Variante ${variantLabel} bearbeiten`} onClick={() => { setEditingVariant(variant); setVariantFamilyId(String(variant.productFamilyId)); setVariantName(variant.name); setVariantSize(variant.totalQuantity?.toString() ?? ""); setVariantUnit(variant.totalUnit ?? ""); }} size="sm" variant="ghost">Bearbeiten</Button><Button aria-label={`Variante ${variantLabel} ${variant.isActive ? "deaktivieren" : "aktivieren"}`} onClick={() => void toggleVariant(variant)} size="sm" variant="ghost">{variant.isActive ? "Deaktivieren" : "Aktivieren"}</Button></div></div>; })}{visibleVariants.length === 0 ? <p className="p-3 text-sm text-zinc-500">Für diese Produktfamilie sind noch keine Varianten angelegt.</p> : null}</div> : <p className="rounded-md border border-zinc-200 p-3 text-sm text-zinc-500 dark:border-zinc-800">Wähle zuerst eine Produktfamilie. Danach werden nur deren Varianten angezeigt.</p>}</CardContent></Card> : null}

    {activeTab === "rules" ? <Card><CardHeader><CardTitle>Produktregeln</CardTitle></CardHeader><CardContent className="p-0"><div className="overflow-x-auto"><table className="min-w-[760px] w-full text-left text-sm"><thead className="border-y border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800"><tr><th className="px-4 py-3">Produkt</th><th className="px-4 py-3">Regel</th><th className="px-4 py-3">Store</th><th className="px-4 py-3">Status</th><th className="px-4 py-3 text-right">Aktion</th></tr></thead><tbody>{rules.map((rule) => { const ruleLabel = `${rule.matchValue} für ${rule.storeName ?? "alle Geschäfte"}`; return <tr className="border-b border-zinc-100 dark:border-zinc-900" key={rule.id}><td className="px-4 py-3">{rule.productFamilyName}{rule.productVariantName ? ` · ${rule.productVariantName}` : ""}</td><td className="px-4 py-3"><span className="font-mono text-xs">{rule.matchType}</span> {rule.matchValue}</td><td className="px-4 py-3">{rule.storeName ?? "Global"}</td><td className="px-4 py-3"><Badge tone={rule.isActive ? "green" : "yellow"}>{rule.isActive ? "aktiv" : "inaktiv"}</Badge></td><td className="px-4 py-3 text-right"><Button aria-label={`Produktregel ${ruleLabel} ${rule.isActive ? "deaktivieren" : "aktivieren"}`} onClick={() => void toggleRule(rule)} size="sm" variant="ghost">{rule.isActive ? "Deaktivieren" : "Aktivieren"}</Button><Button aria-label={`Produktregel ${ruleLabel} anwenden`} disabled={!rule.isActive} onClick={() => void previewRuleApply(rule)} size="sm" variant="ghost">Anwenden</Button></td></tr>; })}</tbody></table></div>{rules.length === 0 ? <p className="p-6 text-sm text-zinc-500">Noch keine Produktregeln. Erstelle einen Vorschlag in der Prüfliste.</p> : null}</CardContent></Card> : null}

    {activeTab === "structure" ? <div className="grid gap-4 xl:grid-cols-2"><MergeCard families={families} kind="family" message={message} onPreview={() => void previewFamilyMerge()} onSourceChange={setFamilySource} onTargetChange={setFamilyTarget} preview={preview?.kind === "family" ? preview.value : null} source={familySource} target={familyTarget} onApply={() => void applyMerge()} /><MergeCard families={variants.filter((variant) => variant.isActive).map((variant) => ({ id: variant.id, name: `${variant.productFamilyName}: ${variant.name}` }))} kind="variant" message={message} onPreview={() => void previewVariantMerge()} onSourceChange={setVariantSource} onTargetChange={setVariantTarget} preview={preview?.kind === "variant" ? preview.value : null} source={variantSource} target={variantTarget} onApply={() => void applyMerge()} /><Card className="xl:col-span-2"><CardHeader><CardTitle>Produkte trennen</CardTitle></CardHeader><CardContent className="space-y-3"><p className="text-sm text-zinc-500">Suche über alle zugeordneten Bon-Positionen. Trennungen werden zuerst als Vorschau berechnet. Manuell bestätigte Zuordnungen bleiben geschützt.</p><div className="grid gap-2 md:grid-cols-2 xl:grid-cols-4"><select aria-label="Split-Produktfamilie" className={selectClass} onChange={(event) => { const id = event.target.value; setSplitFamilyId(id); setSplitVariantId(""); void loadSplitCandidates(0, id, ""); }} value={splitFamilyId}><option value="">Produktfamilie wählen</option>{families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}</select><select aria-label="Split-Produktvariante" className={selectClass} onChange={(event) => setSplitVariantId(event.target.value)} value={splitVariantId}><option value="">Alle Varianten und familienweite Positionen</option>{variants.filter((variant) => variant.productFamilyId === Number(splitFamilyId)).map((variant) => <option key={variant.id} value={variant.id}>{variant.name}</option>)}</select><Input aria-label="Split-Position suchen" onChange={(event) => setSplitQuery(event.target.value)} placeholder="Position suchen" value={splitQuery} /><Input aria-label="Split-Store filtern" onChange={(event) => setSplitStore(event.target.value)} placeholder="Store filtern" value={splitStore} /></div><Button disabled={!splitFamilyId || splitLoading} onClick={() => void loadSplitCandidates(0)} size="sm" variant="secondary">Positionen suchen</Button>{splitLoading ? <p className="text-sm text-zinc-500">Positionen werden geladen …</p> : splitResults?.content.length ? <><div className="divide-y divide-zinc-100 rounded-md border border-zinc-200 dark:divide-zinc-900 dark:border-zinc-800">{splitResults.content.map((item) => <div className="flex flex-col gap-2 px-3 py-2 text-sm sm:flex-row sm:items-center sm:justify-between" key={item.receiptItemId}><div><p className="font-medium">{item.description}</p><p className="text-xs text-zinc-500">{item.storeName ?? "Unbekannt"} · {item.productVariantName ?? item.productFamilyName} · {labelStatus(item.productAssignmentStatus)}</p></div><Button aria-label={`Position ${item.description} aus Bon ${item.receiptId} (Position ${item.receiptItemId}) trennen`} onClick={() => onSplit(item)} size="sm" variant="secondary"><GitFork className="h-4 w-4" />Trennen</Button></div>)}</div>{splitResults.totalPages > 1 ? <div className="flex items-center justify-between text-sm"><span>Seite {splitResults.page + 1} von {splitResults.totalPages}</span><div className="flex gap-2"><Button disabled={splitLoading || splitResults.page === 0} onClick={() => void loadSplitCandidates(splitResults.page - 1)} size="sm" variant="secondary">Vorherige Positionen</Button><Button disabled={splitLoading || splitResults.page + 1 >= splitResults.totalPages} onClick={() => void loadSplitCandidates(splitResults.page + 1)} size="sm" variant="secondary">Weitere Positionen</Button></div></div> : null}</> : <p className="rounded-md border border-zinc-200 p-3 text-sm text-zinc-500 dark:border-zinc-800">Keine zugeordnete Position für diese Auswahl.</p>}</CardContent></Card></div> : null}

    {rulePreview ? <RuleApplyDialog matchingItemsCount={rulePreview.matchingItemsCount} onCancel={() => setRulePreview(null)} onConfirm={() => void applyRule()} rule={rulePreview.rule} /> : null}
    </fieldset>
  </>;
}

function assignmentLabel(count: number): string {
  return `${count} ${count === 1 ? "Zuordnung" : "Zuordnungen"}`;
}

function FamilyEditor({ busy, categories, families, onSave }: { busy: boolean; categories: CategoryDTO[]; families: ProductFamilyDTO[]; onSave: (family: ProductFamilyDTO, request: ProductFamilyRequest) => Promise<boolean> }) {
  const [editing, setEditing] = useState<ProductFamilyDTO | null>(null);
  const [name, setName] = useState("");
  const [categoryId, setCategoryId] = useState("");
  const [active, setActive] = useState(true);

  function start(family: ProductFamilyDTO) {
    setEditing(family);
    setName(family.name);
    setCategoryId(family.defaultCategoryId == null ? "" : String(family.defaultCategoryId));
    setActive(family.isActive);
  }

  async function save() {
    if (!editing || !name.trim()) return;
    const saved = await onSave(editing, {
        name: name.trim(),
        defaultCategoryId: categoryId ? Number(categoryId) : null,
        isActive: active
      });
    if (saved) setEditing(null);
  }

  return <Card><CardHeader><CardTitle>Familienstammdaten bearbeiten</CardTitle></CardHeader><CardContent className="space-y-3">{busy ? <StatusBanner busy title="Produktfamilie wird gespeichert" /> : null}{editing ? <div className="grid gap-2 sm:grid-cols-2"><Input aria-label="Name der Produktfamilie" disabled={busy} onChange={(event) => setName(event.target.value)} value={name} /><select aria-label="Standardkategorie" className={selectClass} disabled={busy} onChange={(event) => setCategoryId(event.target.value)} value={categoryId}><option value="">Keine Standardkategorie</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select><label className="flex items-center gap-2 text-sm"><input aria-label="Produktfamilie aktiv" checked={active} disabled={busy} onChange={(event) => setActive(event.target.checked)} type="checkbox" />Produktfamilie aktiv</label><div className="flex gap-2"><Button disabled={busy || !name.trim()} onClick={() => void save()} size="sm">Familie aktualisieren</Button><Button disabled={busy} onClick={() => setEditing(null)} size="sm" variant="secondary">Abbrechen</Button></div></div> : <div className="flex flex-wrap gap-2">{families.map((family) => <Button aria-label={`Familie ${family.name} bearbeiten`} disabled={busy} key={family.id} onClick={() => start(family)} size="sm" variant="secondary">{family.name} bearbeiten</Button>)}</div>}</CardContent></Card>;
}

function MergeCard({ families, kind, message, onApply, onPreview, onSourceChange, onTargetChange, preview, source, target }: { families: Array<{ id: number; name: string }>; kind: "family" | "variant"; message: string | null; onApply: () => void; onPreview: () => void; onSourceChange: (value: string) => void; onTargetChange: (value: string) => void; preview: ProductChangePreviewDTO | null; source: string; target: string }) {
  const noun = kind === "family" ? "Familien" : "Varianten";
  const busy = message === "Merge-Vorschau wird berechnet.";
  return <Card><CardHeader><CardTitle>{noun} zusammenführen</CardTitle></CardHeader><CardContent className="space-y-3"><p className="text-sm text-zinc-500">Die Änderung wird zuerst nur berechnet. Erst die Bestätigung verändert historische Zuordnungen und schreibt einen Audit-Eintrag.</p><select aria-label={`Quell${kind === "family" ? "familie" : "variante"}`} className={selectClass} onChange={(event) => onSourceChange(event.target.value)} value={source}><option value="">Quelle</option>{families.map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select><select aria-label={`Ziel${kind === "family" ? "familie" : "variante"}`} className={selectClass} onChange={(event) => onTargetChange(event.target.value)} value={target}><option value="">Ziel</option>{families.map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select>{preview == null ? <Button disabled={busy} onClick={onPreview} size="sm" variant="secondary">Vorschau berechnen</Button> : <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100"><p>{preview.affectedItemsCount} Positionen in {preview.affectedStores.length || 0} Stores werden geändert.</p><PreviewDetails preview={preview} /><div className="mt-2 flex gap-2"><Button disabled={busy} onClick={onApply} size="sm" variant="danger">Zusammenführen bestätigen</Button></div></div>}{message ? <p className="text-xs text-zinc-500">{message}</p> : null}</CardContent></Card>;
}

function PreviewDetails({ preview }: { preview: ProductChangePreviewDTO }) {
  return <div className="mt-1 space-y-1 text-xs"><p>{preview.affectedStores.length ? preview.affectedStores.join(", ") : "Keine Stores betroffen"}</p>{preview.dateFrom || preview.dateTo ? <p>{preview.dateFrom ? formatDate(preview.dateFrom) : "Offener Beginn"} – {preview.dateTo ? formatDate(preview.dateTo) : "Offenes Ende"}</p> : null}<p>{preview.reportImpact}</p><p>Manuell bestätigte und geschützte Zuordnungen werden nicht still überschrieben.</p></div>;
}

function CorrectionDialog({ families, item, loading, onCancel, onConfirm, onNoProduct, variantsByFamily }: { families: ProductFamilyDTO[]; item: ProductReviewItemDTO; loading: boolean; onCancel: () => void; onConfirm: (request: ProductAssignmentCorrectionRequest) => void; onNoProduct: () => void; variantsByFamily: Map<number, ProductVariantDTO[]> }) {
  const initialFamilyId = item.suggestedProductFamilyId ?? item.currentProductFamilyId ?? "";
  const initialFamilyName = item.suggestedProductFamilyName ?? item.currentProductFamilyName ?? "";
  const hasInitialFamily = initialFamilyId !== "";
  const [mode, setMode] = useState<"new" | "existing" | "no-product">(
    hasInitialFamily ? "existing" : "new"
  );
  const [familyId, setFamilyId] = useState<number | "">(initialFamilyId);
  const [newFamilyName, setNewFamilyName] = useState(initialFamilyName || readableProductName(item.description));
  const [familySearch, setFamilySearch] = useState(initialFamilyName);
  const [variantId, setVariantId] = useState(item.suggestedProductVariantId ?? item.currentProductVariantId ?? 0);
  const [applyToSimilar, setApplyToSimilar] = useState(item.possibleRetroactiveItems > 1);
  const selectedFamilyId = familyId === "" ? null : familyId;
  const familyOptions = matchingFamilies(families, familySearch, selectedFamilyId);
  const variants = selectedFamilyId == null ? [] : variantsByFamily.get(selectedFamilyId) ?? [];
  const canSubmit = mode === "no-product" || (mode === "new" ? newFamilyName.trim().length > 0 : selectedFamilyId != null);
  function submit() {
    if (mode === "no-product") {
      onNoProduct();
      return;
    }
    onConfirm(mode === "new" ? {
      newProductFamilyName: newFamilyName.trim(),
      productVariantId: null,
      applyToSameStoreDescription: applyToSimilar
    } : {
      productFamilyId: selectedFamilyId!,
      productVariantId: variantId || null,
      applyToSameStoreDescription: applyToSimilar
    });
  }

  return (
    <Dialog onClose={onCancel} title="Produktzuordnung prüfen">
      <div className="rounded-md border border-zinc-200 p-3 text-sm dark:border-zinc-800">
        <div className="text-xs uppercase text-zinc-500">Bon-Position</div>
        <div className="mt-1 font-medium text-zinc-950 dark:text-zinc-50">{item.description}</div>
        <div className="mt-1 text-xs text-zinc-500">{item.storeName ?? "Store unbekannt"}{item.storeBranch ? ` · ${item.storeBranch}` : ""} · {formatQuantity(item)} · {formatEuro(item.totalPrice)}</div>
        {item.categoryName ? <div className="mt-1 text-xs text-zinc-500">Kategorie: {item.categoryName}</div> : null}
      </div>
      <div className="grid gap-2 sm:grid-cols-3">
        <button className={choiceClass(mode === "new")} onClick={() => setMode("new")} type="button">Neue Familie</button>
        <button className={choiceClass(mode === "existing")} onClick={() => setMode("existing")} type="button">Vorhandene Familie</button>
        <button className={choiceClass(mode === "no-product")} onClick={() => setMode("no-product")} type="button">Kein Produkt</button>
      </div>
      {mode === "new" ? (
        <>
          <Input
            aria-label="Name der neuen Produktfamilie"
            onChange={(event) => setNewFamilyName(event.target.value)}
            placeholder="Name der neuen Produktfamilie"
            value={newFamilyName}
          />
          <p className="text-xs text-zinc-500">Für gewogene Ware oder variable Mengen bleibt die Variante leer. Die Familie wird direkt angelegt und dieser Position zugeordnet.</p>
        </>
      ) : null}
      {mode === "existing" ? (
        <>
          <Input
            aria-label="Produktfamilie suchen"
            onChange={(event) => setFamilySearch(event.target.value)}
            placeholder="Vorhandene Produktfamilie suchen"
            value={familySearch}
          />
          <select
            aria-label="Produktfamilie"
            className={selectClass}
            onChange={(event) => {
              const nextFamilyId = event.target.value ? Number(event.target.value) : "";
              setFamilyId(nextFamilyId);
              setVariantId(0);
            }}
            value={familyId === "" ? "" : String(familyId)}
          >
            <option value="">Produktfamilie auswählen</option>
            {familyOptions.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
          </select>
          {familyOptions.length === 0 ? <p className="text-xs text-zinc-500">Kein Treffer. Lege stattdessen eine neue Familie an oder passe die Suche an.</p> : null}
          <select
            aria-label="Produktvariante"
            className={selectClass}
            disabled={selectedFamilyId == null}
            onChange={(event) => setVariantId(Number(event.target.value))}
            value={variantId}
          >
            <option value="0">{selectedFamilyId == null ? "Erst Familie wählen" : "Keine Variante"}</option>
            {variants.filter((variant) => variant.isActive).map((variant) => <option key={variant.id} value={variant.id}>{variant.name}</option>)}
          </select>
          <p className="text-xs text-zinc-500">Varianten sind nur für feste Größen oder Packungen nötig, z. B. 500 g, 1 l oder 6 Stück.</p>
        </>
      ) : null}
      {mode === "no-product" ? <p className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100">Für Rabatte, Zahlungszeilen oder technische Bon-Zeilen. Die Position erhält den Status NO_PRODUCT und wird nicht im Produktpreisvergleich verwendet.</p> : null}
      {item.possibleRetroactiveItems > 1 && mode !== "no-product" ? (
        <label className="flex items-start gap-2 rounded-md border border-zinc-200 p-3 text-sm dark:border-zinc-800">
          <input checked={applyToSimilar} onChange={(event) => setApplyToSimilar(event.target.checked)} type="checkbox" />
          <span>
            Gleiche offene Positionen in diesem Store ebenfalls zuordnen
            <span className="block text-xs text-zinc-500">{item.possibleRetroactiveItems} passende Positionen inkl. dieser Zeile. Manuell bestätigte Zuordnungen bleiben geschützt.</span>
          </span>
        </label>
      ) : null}
      <DialogActions onCancel={onCancel} onConfirm={submit} disabled={loading || !canSubmit} label={mode === "new" ? "Familie anlegen und zuordnen" : mode === "no-product" ? "Als kein Produkt markieren" : "Zuordnung bestätigen"} />
    </Dialog>
  );
}

function SplitDialog({ apiClient, item, loading, onCancel, onConfirm }: { apiClient: ApiClient; item: SplitCandidate; loading: boolean; onCancel: () => void; onConfirm: (label: string, callback: () => Promise<unknown>) => void }) {
  const [name, setName] = useState("");
  const [size, setSize] = useState("");
  const [unit, setUnit] = useState("");
  const [previewBusy, setPreviewBusy] = useState(false);
  const [previewError, setPreviewError] = useState<string | null>(null);
  const previewGeneration = useRef(0);
  const [preview, setPreview] = useState<
    | { kind: "family"; value: ProductChangePreviewDTO; request: ProductFamilySplitRequest }
    | { kind: "variant"; value: ProductChangePreviewDTO; request: ProductVariantSplitRequest }
    | null
  >(null);
  const variantSplit = item.productVariantId != null;
  const canSplit = name.trim().length > 0;
  async function calculate() {
    if (!canSplit || previewBusy || loading) return;
    const generation = previewGeneration.current;
    setPreviewBusy(true);
    setPreviewError(null);
    try {
      if (variantSplit) {
        const request = { sourceVariantId: item.productVariantId!, receiptItemIds: [item.receiptItemId], newVariant: { productFamilyId: item.productFamilyId!, name: name.trim(), totalQuantity: size ? Number(size) : null, totalUnit: unit || null, isActive: true } };
        const value = await apiClient.previewProductVariantSplit(request);
        if (generation === previewGeneration.current) setPreview({ kind: "variant", value, request });
      } else {
        const request = { sourceFamilyId: item.productFamilyId!, receiptItemIds: [item.receiptItemId], newFamily: { name: name.trim(), isActive: true } };
        const value = await apiClient.previewProductFamilySplit(request);
        if (generation === previewGeneration.current) setPreview({ kind: "family", value, request });
      }
    } catch (error) {
      if (generation === previewGeneration.current) setPreviewError(error instanceof Error ? error.message : "Split-Vorschau konnte nicht berechnet werden.");
    } finally {
      if (generation === previewGeneration.current) setPreviewBusy(false);
    }
  }
  function apply() {
    if (!preview) return;
    if (preview.kind === "variant") {
      onConfirm("Produktvariante für die Position angelegt.", () => apiClient.applyProductVariantSplit({ ...preview.request, confirm: true }));
    } else {
      onConfirm("Produktfamilie für die Position angelegt.", () => apiClient.applyProductFamilySplit({ ...preview.request, confirm: true }));
    }
  }
  function invalidate(setter: (value: string) => void, value: string) { previewGeneration.current += 1; setter(value); setPreview(null); setPreviewError(null); setPreviewBusy(false); }
  return <Dialog onClose={onCancel} title={variantSplit ? "Position in neue Variante trennen" : "Position in neue Familie trennen"}><p className="text-sm text-zinc-500">Nur diese Position wird nach Vorschau und Bestätigung umgehängt. Bestehende Regeln bleiben unverändert.</p><Input aria-label="Neuer Produktname" onChange={(event) => invalidate(setName, event.target.value)} placeholder={variantSplit ? "Neue Variantenbezeichnung" : "Neue Familienbezeichnung"} value={name} />{variantSplit ? <div className="grid grid-cols-2 gap-2"><Input aria-label="Neue Variantenmenge" onChange={(event) => invalidate(setSize, event.target.value)} placeholder="Menge" step="0.001" type="number" value={size} /><Input aria-label="Neue Varianteneinheit" onChange={(event) => invalidate(setUnit, event.target.value)} placeholder="Einheit" value={unit} /></div> : null}{previewBusy ? <StatusBanner busy title="Split-Vorschau wird berechnet" /> : null}{previewError ? <StatusBanner title="Split-Vorschau fehlgeschlagen" tone="error">{previewError}</StatusBanner> : null}{preview == null ? <Button disabled={!canSplit || loading || previewBusy} onClick={() => void calculate()} size="sm" variant="secondary">Vorschau berechnen</Button> : <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100"><p>{preview.value.affectedItemsCount} Position wird umgehängt.</p><PreviewDetails preview={preview.value} /></div>}<DialogActions onCancel={onCancel} onConfirm={apply} disabled={!preview || loading || previewBusy} label="Trennung bestätigen" /></Dialog>;
}

function RuleSuggestionDialog({ loading, onCancel, onConfirm, suggestion }: { loading: boolean; onCancel: () => void; onConfirm: (applyToExisting: boolean) => void; suggestion: ProductRuleSuggestionDTO }) { const [applyToExisting, setApplyToExisting] = useState(false); return <Dialog onClose={onCancel} title="Produktregel bestätigen"><p className="text-sm text-zinc-700 dark:text-zinc-200"><span className="font-medium">{suggestion.rule.matchType}</span> "{suggestion.rule.matchValue}"</p><p className="text-sm text-zinc-500">Vorschau: {suggestion.preview.matchingItemsCount} Positionen passen zur Regel.</p><label className="flex items-start gap-2 text-sm"><input checked={applyToExisting} onChange={(event) => setApplyToExisting(event.target.checked)} type="checkbox" />Auf bestehende passende Positionen anwenden</label><DialogActions onCancel={onCancel} onConfirm={() => onConfirm(applyToExisting)} disabled={loading} label="Regel bestätigen" /></Dialog>; }

function RuleApplyDialog({ matchingItemsCount, onCancel, onConfirm, rule }: { matchingItemsCount: number; onCancel: () => void; onConfirm: () => void; rule: ProductRuleDTO }) { return <Dialog onClose={onCancel} title="Produktregel rückwirkend anwenden"><p className="text-sm text-zinc-600 dark:text-zinc-300">{rule.matchType} "{rule.matchValue}" passt auf {matchingItemsCount} bestehende Positionen.</p><p className="text-sm text-zinc-500">Manuelle Bestätigungen und bereits geschützte Zuordnungen werden vom Backend nicht still überschrieben.</p><DialogActions onCancel={onCancel} onConfirm={onConfirm} disabled={false} label="Anwendung bestätigen" /></Dialog>; }

function Dialog({ children, onClose, title }: { children: React.ReactNode; onClose: () => void; title: string }) { return <ModalDialog onClose={onClose} open title={title}><div className="mt-3 space-y-3">{children}</div></ModalDialog>; }
function DialogActions({ disabled, label, onCancel, onConfirm }: { disabled: boolean; label: string; onCancel: () => void; onConfirm: () => void }) { return <div className="flex justify-end gap-2"><Button onClick={onCancel} size="sm" variant="secondary">Abbrechen</Button><Button disabled={disabled} onClick={onConfirm} size="sm">{label}</Button></div>; }
function IconButton({ children, disabled, label, onClick }: { children: React.ReactNode; disabled: boolean; label: string; onClick: () => void }) { return <button aria-label={label} className="flex h-8 w-8 items-center justify-center rounded-md border border-zinc-200 text-zinc-600 hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-900" disabled={disabled} onClick={onClick} title={label} type="button">{children}</button>; }
function TokenHint() { return <Card><CardContent className="py-10 text-center text-sm text-zinc-500">Für Produktzuordnungen muss oben ein API-Token gesetzt sein.</CardContent></Card>; }
function choiceClass(active: boolean) { return `rounded-md border px-3 py-2 text-left text-sm ${active ? "border-zinc-950 bg-zinc-950 text-white dark:border-zinc-50 dark:bg-zinc-50 dark:text-zinc-950" : "border-zinc-200 bg-white text-zinc-700 hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-200 dark:hover:bg-zinc-900"}`; }
const selectClass = "h-9 w-full rounded-md border border-zinc-300 bg-white px-2 text-sm dark:border-zinc-700 dark:bg-zinc-950";
function readableProductName(description: string) {
  return description
    .replace(/\s+/g, " ")
    .trim();
}
function productDetailLabel(item: ProductReviewItemDTO) {
  const hasFamily = item.suggestedProductFamilyName != null || item.currentProductFamilyName != null;
  if (!hasFamily) {
    return "Produktfamilie offen";
  }
  return item.suggestedProductVariantName ?? item.currentProductVariantName ?? "Keine Variante";
}
function matchingFamilies(families: ProductFamilyDTO[], search: string, selectedFamilyId: number | null) {
  const query = compactSearch(search);
  return families
    .filter((family) => family.isActive || family.id === selectedFamilyId)
    .filter((family) => {
      if (family.id === selectedFamilyId) {
        return true;
      }
      if (query.length === 0) {
        return true;
      }
      if (query.length < 2) {
        return false;
      }
      const name = compactSearch(family.name);
      return name.includes(query) || query.includes(name) || commonPrefixLength(name, query) >= 6;
    })
    .slice(0, 20);
}
function compactSearch(value: string) {
  return value
    .toLowerCase()
    .replaceAll("ä", "ae")
    .replaceAll("ö", "oe")
    .replaceAll("ü", "ue")
    .replaceAll("ß", "ss")
    .normalize("NFD")
    .replace(/\p{Diacritic}/gu, "")
    .replace(/[^a-z0-9]/g, "");
}
function commonPrefixLength(first: string, second: string) {
  let index = 0;
  while (index < first.length && index < second.length && first[index] === second[index]) {
    index++;
  }
  return index;
}
function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat("de-DE", { day: "2-digit", month: "2-digit", year: "numeric" }).format(new Date(`${value}T00:00:00`)) : "Datum offen"; }
function formatEuro(value: number | null) { return value == null ? "-" : new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(value); }
function formatQuantity(item: ProductReviewItemDTO) { return item.quantity == null ? "Menge offen" : `${item.quantity} ${item.unit ?? ""}`.trim(); }
function labelStatus(status: ProductAssignmentStatus | null) { return status === "NEEDS_REVIEW" ? "Prüfen" : status === "CONFIRMED" ? "Bestätigt" : status === "AUTO_ASSIGNED" ? "Automatisch" : status === "REJECTED" ? "Abgelehnt" : status === "NO_PRODUCT" ? "Kein Produkt" : "Offen"; }
