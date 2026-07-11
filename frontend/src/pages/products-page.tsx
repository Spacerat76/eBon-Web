import { Check, CircleOff, Eraser, GitFork, Loader2, Pencil, Plus, RefreshCw, Sparkles, X } from "lucide-react";
import { useEffect, useMemo, useState } from "react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import type { ApiClient } from "@/lib/api";
import { ProductPriceComparison } from "@/pages/product-price-comparison";
import type {
  CategoryDTO,
  PageResponse,
  ProductAssignmentCorrectionRequest,
  ProductAssignmentSource,
  ProductAssignmentStatus,
  ProductChangePreviewDTO,
  ProductFamilyDTO,
  ProductReviewItemDTO,
  ProductReviewParams,
  ProductRuleDTO,
  ProductRuleSuggestionDTO,
  ProductVariantDTO,
  ProductVariantRequest
} from "@/lib/types";

type ProductTab = "review" | "master-data" | "prices";
type ReviewFilters = Omit<ProductReviewParams, "page" | "size" | "store" | "status" | "confidenceMax"> & {
  store: string;
  status: "" | ProductAssignmentStatus;
  confidenceMax: string;
};

const defaultFilters: ReviewFilters = { store: "", status: "NEEDS_REVIEW", confidenceMax: "" };

export function ProductsPage({ apiClient, hasApiToken }: { apiClient: ApiClient; hasApiToken: boolean }) {
  const [activeTab, setActiveTab] = useState<ProductTab>("review");
  const [review, setReview] = useState<PageResponse<ProductReviewItemDTO> | null>(null);
  const [families, setFamilies] = useState<ProductFamilyDTO[]>([]);
  const [variants, setVariants] = useState<ProductVariantDTO[]>([]);
  const [rules, setRules] = useState<ProductRuleDTO[]>([]);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [filters, setFilters] = useState<ReviewFilters>(defaultFilters);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [selected, setSelected] = useState<ProductReviewItemDTO | null>(null);
  const [splitItem, setSplitItem] = useState<ProductReviewItemDTO | null>(null);
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
      setMessage(label);
      setSelected(null);
      setSplitItem(null);
      setSuggestion(null);
      setSuggestionItemId(null);
      await load();
    } catch (error) {
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
          <h1 className="text-xl font-semibold text-zinc-950 dark:text-zinc-50">Produktzuordnung prüfen</h1>
        </div>
        <Button onClick={() => void load()} size="sm" variant="secondary">
          {loading ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
          Daten neu laden
        </Button>
      </div>

      {message ? <div className="rounded-md border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-700 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-200">{message}</div> : null}

      <div className="flex gap-2 border-b border-zinc-200 dark:border-zinc-800">
        <button className={tabClass(activeTab === "review")} onClick={() => setActiveTab("review")}>Prüfliste</button>
        <button className={tabClass(activeTab === "master-data")} onClick={() => setActiveTab("master-data")}>Pflege</button>
        <button className={tabClass(activeTab === "prices")} onClick={() => setActiveTab("prices")}>Preisvergleich</button>
      </div>

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
          onSplit={setSplitItem}
          onSubmitFilters={applyFilters}
          onSuggestRule={(item) => void proposeRule(item)}
          review={review}
        />
      ) : activeTab === "master-data" ? (
        <MasterData apiClient={apiClient} families={families} onChanged={load} rules={rules} variants={variants} variantsByFamily={variantsByFamily} />
      ) : (
        <ProductPriceComparison apiClient={apiClient} families={families} variants={variants} />
      )}

      {selected ? <CorrectionDialog families={families} item={selected} loading={loading} onCancel={() => setSelected(null)} onConfirm={(request) => void action("Produktzuordnung korrigiert.", () => apiClient.correctProductReview(selected.receiptItemId, request))} onNoProduct={() => void action("Position als keine Produktposition markiert.", () => apiClient.markProductReviewNoProduct(selected.receiptItemId))} variantsByFamily={variantsByFamily} /> : null}
      {splitItem ? <SplitDialog apiClient={apiClient} item={splitItem} loading={loading} onCancel={() => setSplitItem(null)} onConfirm={(label, callback) => void action(label, callback)} /> : null}
      {suggestion && suggestionItemId != null ? <RuleSuggestionDialog suggestion={suggestion} loading={loading} onCancel={() => { setSuggestion(null); setSuggestionItemId(null); }} onConfirm={(applyToExisting) => void action("Produktregel gespeichert.", () => apiClient.acceptProductRuleSuggestion(suggestionItemId, { rule: suggestion.rule, applyToExisting, confirm: true }))} /> : null}
    </div>
  );
}

function ReviewTable({ categories, families, filters, loading, onAccept, onClear, onCorrect, onFiltersChange, onNoProduct, onPageChange, onReject, onResetFilters, onSplit, onSubmitFilters, onSuggestRule, review }: {
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
  onSplit: (item: ProductReviewItemDTO) => void;
  onSubmitFilters: () => void;
  onSuggestRule: (item: ProductReviewItemDTO) => void;
  review: PageResponse<ProductReviewItemDTO> | null;
}) {
  const change = (value: Partial<ReviewFilters>) => onFiltersChange({ ...filters, ...value });
  return <Card><CardHeader className="gap-3"><div className="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between"><CardTitle>Offene Produktzuordnungen</CardTitle><p className="text-sm text-zinc-500">Häufige und teure Fälle stehen oben.</p></div><div className="grid gap-2 md:grid-cols-3 xl:grid-cols-5"><Input aria-label="Store filtern" className="h-9" onChange={(event) => change({ store: event.target.value })} placeholder="Store" value={filters.store} /><select aria-label="Produktfamilie filtern" className={selectClass} onChange={(event) => change({ productFamilyId: event.target.value ? Number(event.target.value) : undefined })} value={filters.productFamilyId ?? ""}><option value="">Alle Familien</option>{families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}</select><select aria-label="Kategorie filtern" className={selectClass} onChange={(event) => change({ categoryId: event.target.value ? Number(event.target.value) : undefined })} value={filters.categoryId ?? ""}><option value="">Alle Kategorien</option>{categories.map((category) => <option key={category.id} value={category.id}>{category.name}</option>)}</select><select aria-label="Quelle filtern" className={selectClass} onChange={(event) => change({ source: (event.target.value || undefined) as ProductAssignmentSource | undefined })} value={filters.source ?? ""}><option value="">Alle Quellen</option><option value="RULE">Regel</option><option value="HISTORY">Historie</option><option value="AI">KI</option><option value="MANUAL">Manuell</option></select><select aria-label="Status filtern" className={selectClass} onChange={(event) => change({ status: event.target.value as "" | ProductAssignmentStatus })} value={filters.status}><option value="">Alle Status</option><option value="NEEDS_REVIEW">Prüfen</option><option value="AUTO_ASSIGNED">Automatisch</option><option value="CONFIRMED">Bestätigt</option><option value="REJECTED">Abgelehnt</option><option value="NO_PRODUCT">Kein Produkt</option></select><Input aria-label="Zeitraum von" className="h-9" onChange={(event) => change({ dateFrom: event.target.value || undefined })} type="date" value={filters.dateFrom ?? ""} /><Input aria-label="Zeitraum bis" className="h-9" onChange={(event) => change({ dateTo: event.target.value || undefined })} type="date" value={filters.dateTo ?? ""} /><Input aria-label="Maximale Konfidenz" className="h-9" max="1" min="0" onChange={(event) => change({ confidenceMax: event.target.value })} placeholder="Konfidenz bis" step="0.01" type="number" value={filters.confidenceMax} /><div className="flex gap-2"><Button onClick={onSubmitFilters} size="sm" variant="secondary">Filtern</Button><Button onClick={onResetFilters} size="sm" variant="ghost">Reset</Button></div></div></CardHeader><CardContent className="p-0"><div className="overflow-x-auto"><table className="min-w-[1240px] w-full text-left text-sm"><thead className="border-y border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800"><tr><th className="px-4 py-3">Bon</th><th className="px-4 py-3">Position</th><th className="px-4 py-3">Vorschlag</th><th className="px-4 py-3">Grund</th><th className="px-4 py-3">Auswirkung</th><th className="px-4 py-3 text-right">Aktion</th></tr></thead><tbody>{review?.content.map((item) => <tr className="border-b border-zinc-100 align-top dark:border-zinc-900" key={item.receiptItemId}><td className="px-4 py-3"><button className="text-left text-blue-700 hover:underline dark:text-blue-300" onClick={() => { window.location.hash = `#/receipts/${item.receiptId}`; }}>{formatDate(item.receiptDate)}<span className="block text-xs text-zinc-500">{item.storeName ?? "Unbekannt"}{item.storeBranch ? ` · ${item.storeBranch}` : ""}</span></button></td><td className="px-4 py-3"><div className="max-w-80 font-medium text-zinc-900 dark:text-zinc-100">{item.description}</div><div className="mt-1 text-xs text-zinc-500">{formatQuantity(item)} · {formatEuro(item.totalPrice)}</div></td><td className="px-4 py-3"><div>{item.suggestedProductFamilyName ?? item.currentProductFamilyName ?? "Ohne Vorschlag"}</div><div className="text-xs text-zinc-500">{productDetailLabel(item)}</div><div className="mt-1 flex gap-1"><Badge tone={item.assignmentStatus === "NEEDS_REVIEW" ? "yellow" : "blue"}>{labelStatus(item.assignmentStatus)}</Badge>{item.confidence == null ? null : <Badge>{Math.round(item.confidence * 100)} %</Badge>}</div></td><td className="px-4 py-3 text-xs text-zinc-600 dark:text-zinc-300">{item.reason ?? "Unklare Zuordnung"}</td><td className="px-4 py-3 text-xs text-zinc-600 dark:text-zinc-300">{item.possibleRetroactiveItems} ähnliche Positionen</td><td className="px-4 py-3"><div className="flex justify-end gap-1"><IconButton disabled={loading || item.suggestedProductFamilyId == null} label="Übernehmen" onClick={() => onAccept(item)}><Check className="h-4 w-4" /></IconButton><IconButton disabled={loading} label="Korrigieren" onClick={() => onCorrect(item)}><Pencil className="h-4 w-4" /></IconButton><IconButton disabled={loading || item.currentProductFamilyId == null} label="Aus Zuordnung abspalten" onClick={() => onSplit(item)}><GitFork className="h-4 w-4" /></IconButton><IconButton disabled={loading} label="Regel vorschlagen" onClick={() => onSuggestRule(item)}><Sparkles className="h-4 w-4" /></IconButton><IconButton disabled={loading} label="Als kein Produkt markieren" onClick={() => onNoProduct(item)}><CircleOff className="h-4 w-4" /></IconButton><IconButton disabled={loading} label="Vorschlag ablehnen" onClick={() => onReject(item)}><X className="h-4 w-4" /></IconButton><IconButton disabled={loading} label="Zuordnung entfernen" onClick={() => onClear(item)}><Eraser className="h-4 w-4" /></IconButton></div></td></tr>)}</tbody></table></div>{review?.content.length === 0 ? <p className="p-6 text-sm text-zinc-500">Keine offenen Produktzuordnungen.</p> : null}{review && review.totalPages > 1 ? <div className="flex items-center justify-between border-t border-zinc-200 px-4 py-3 text-sm dark:border-zinc-800"><span>Seite {review.page + 1} von {review.totalPages}</span><div className="flex gap-2"><Button disabled={loading || review.page === 0} onClick={() => onPageChange(review.page - 1)} size="sm" variant="secondary">Zurück</Button><Button disabled={loading || review.page + 1 >= review.totalPages} onClick={() => onPageChange(review.page + 1)} size="sm" variant="secondary">Weiter</Button></div></div> : null}</CardContent></Card>;
}

function MasterData({ apiClient, families, onChanged, rules, variants, variantsByFamily }: { apiClient: ApiClient; families: ProductFamilyDTO[]; onChanged: () => Promise<void>; rules: ProductRuleDTO[]; variants: ProductVariantDTO[]; variantsByFamily: Map<number, ProductVariantDTO[]> }) {
  const [familyName, setFamilyName] = useState("");
  const [variantFamilyId, setVariantFamilyId] = useState("");
  const [variantName, setVariantName] = useState("");
  const [variantSize, setVariantSize] = useState("");
  const [variantUnit, setVariantUnit] = useState("");
  const [editingVariant, setEditingVariant] = useState<ProductVariantDTO | null>(null);
  const [familySource, setFamilySource] = useState("");
  const [familyTarget, setFamilyTarget] = useState("");
  const [variantSource, setVariantSource] = useState("");
  const [variantTarget, setVariantTarget] = useState("");
  const [preview, setPreview] = useState<{ kind: "family" | "variant"; value: ProductChangePreviewDTO } | null>(null);
  const [rulePreview, setRulePreview] = useState<{ rule: ProductRuleDTO; matchingItemsCount: number } | null>(null);
  const [message, setMessage] = useState<string | null>(null);

  async function createFamily() { if (!familyName.trim()) return; await apiClient.createProductFamily({ name: familyName.trim(), isActive: true }); setFamilyName(""); await onChanged(); }
  function variantRequest(): ProductVariantRequest { return { productFamilyId: Number(variantFamilyId), name: variantName.trim(), totalQuantity: variantSize ? Number(variantSize) : null, totalUnit: variantUnit || null, isActive: true }; }
  async function saveVariant() { if (!variantFamilyId || !variantName.trim()) return; if (editingVariant) { await apiClient.updateProductVariant(editingVariant.id, { ...variantRequest(), isActive: editingVariant.isActive }); } else { await apiClient.createProductVariant(variantRequest()); } setEditingVariant(null); setVariantName(""); setVariantSize(""); setVariantUnit(""); await onChanged(); }
  async function previewFamilyMerge() { if (!familySource || !familyTarget || familySource === familyTarget) return; setPreview({ kind: "family", value: await apiClient.previewProductFamilyMerge({ sourceFamilyId: Number(familySource), targetFamilyId: Number(familyTarget) }) }); }
  async function previewVariantMerge() { if (!variantSource || !variantTarget || variantSource === variantTarget) return; setPreview({ kind: "variant", value: await apiClient.previewProductVariantMerge({ sourceVariantId: Number(variantSource), targetVariantId: Number(variantTarget) }) }); }
  async function applyMerge() { if (!preview) return; if (preview.kind === "family") { await apiClient.applyProductFamilyMerge({ sourceFamilyId: Number(familySource), targetFamilyId: Number(familyTarget), confirm: true }); } else { await apiClient.applyProductVariantMerge({ sourceVariantId: Number(variantSource), targetVariantId: Number(variantTarget), confirm: true }); } setMessage("Historische Zuordnungen wurden zusammengeführt."); setPreview(null); await onChanged(); }
  async function toggleFamily(family: ProductFamilyDTO) { await apiClient.updateProductFamily(family.id, { name: family.name, defaultCategoryId: family.defaultCategoryId, isActive: !family.isActive }); await onChanged(); }
  async function toggleVariant(variant: ProductVariantDTO) { await apiClient.updateProductVariant(variant.id, { productFamilyId: variant.productFamilyId, name: variant.name, unitQuantity: variant.unitQuantity, unit: variant.unit, packageQuantity: variant.packageQuantity, packageDescription: variant.packageDescription, totalQuantity: variant.totalQuantity, totalUnit: variant.totalUnit, gtin: variant.gtin, isActive: !variant.isActive }); await onChanged(); }
  async function toggleRule(rule: ProductRuleDTO) { await apiClient.updateProductRule(rule.id, { productFamilyId: rule.productFamilyId, productVariantId: rule.productVariantId, storeName: rule.storeName, matchType: rule.matchType, matchValue: rule.matchValue, priority: rule.priority, isActive: !rule.isActive }); await onChanged(); }
  async function previewRuleApply(rule: ProductRuleDTO) { const result = await apiClient.previewProductRule({ storeName: rule.storeName, matchType: rule.matchType, matchValue: rule.matchValue }); setRulePreview({ rule, matchingItemsCount: result.matchingItemsCount }); }
  async function applyRule() { if (!rulePreview) return; await apiClient.applyProductRule(rulePreview.rule.id); setRulePreview(null); setMessage("Produktregel wurde auf die Vorschau angewendet."); await onChanged(); }
  const visibleVariants = variantFamilyId ? variants.filter((variant) => variant.productFamilyId === Number(variantFamilyId)) : [];

  return <><div className="grid gap-4 xl:grid-cols-2"><Card><CardHeader><CardTitle>Produktfamilien</CardTitle></CardHeader><CardContent className="space-y-3"><div className="flex gap-2"><Input aria-label="Neue Produktfamilie" onChange={(event) => setFamilyName(event.target.value)} placeholder="Neue Produktfamilie" value={familyName} /><Button onClick={() => void createFamily()} size="sm"><Plus className="h-4 w-4" />Anlegen</Button></div><div className="max-h-72 overflow-auto rounded-md border border-zinc-200 dark:border-zinc-800">{families.map((family) => <div className="flex items-center justify-between border-b border-zinc-100 px-3 py-2 text-sm last:border-0 dark:border-zinc-900" key={family.id}><div><span className="font-medium">{family.name}</span><span className="ml-2 text-xs text-zinc-500">{variantsByFamily.get(family.id)?.length ?? 0} Varianten</span></div><div className="flex items-center gap-2"><Badge tone={family.isActive ? "green" : "yellow"}>{family.isActive ? "aktiv" : "inaktiv"}</Badge><Button onClick={() => void toggleFamily(family)} size="sm" variant="ghost">{family.isActive ? "Deaktivieren" : "Aktivieren"}</Button></div></div>)}</div></CardContent></Card><Card><CardHeader><CardTitle>Produktvarianten</CardTitle></CardHeader><CardContent className="space-y-3"><div className="grid gap-2 sm:grid-cols-2"><select aria-label="Variantenfamilie" className={selectClass} onChange={(event) => { setVariantFamilyId(event.target.value); setEditingVariant(null); setVariantName(""); setVariantSize(""); setVariantUnit(""); }} value={variantFamilyId}><option value="">Produktfamilie wählen</option>{families.filter((family) => family.isActive).map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}</select><Input aria-label="Variantenname" onChange={(event) => setVariantName(event.target.value)} placeholder="Variantenname" value={variantName} /><Input aria-label="Variantenmenge" min="0.001" onChange={(event) => setVariantSize(event.target.value)} placeholder="Gesamtmenge, z. B. 0.5" step="0.001" type="number" value={variantSize} /><Input aria-label="Varianteneinheit" onChange={(event) => setVariantUnit(event.target.value)} placeholder="Einheit, z. B. l" value={variantUnit} /></div><div className="flex gap-2"><Button disabled={!variantFamilyId} onClick={() => void saveVariant()} size="sm">{editingVariant ? "Variante aktualisieren" : "Variante anlegen"}</Button>{editingVariant ? <Button onClick={() => { setEditingVariant(null); setVariantName(""); setVariantSize(""); setVariantUnit(""); }} size="sm" variant="secondary">Abbrechen</Button> : null}</div>{variantFamilyId ? <div className="max-h-72 overflow-auto rounded-md border border-zinc-200 dark:border-zinc-800">{visibleVariants.map((variant) => <div className="flex items-center justify-between gap-2 border-b border-zinc-100 px-3 py-2 text-sm last:border-0 dark:border-zinc-900" key={variant.id}><div><span className="font-medium">{variant.name}</span><span className="ml-2 text-xs text-zinc-500">{variant.totalQuantity ? `${variant.totalQuantity} ${variant.totalUnit ?? ""}` : "ohne feste Menge"}</span></div><div className="flex items-center gap-1"><Badge tone={variant.isActive ? "green" : "yellow"}>{variant.isActive ? "aktiv" : "inaktiv"}</Badge><Button onClick={() => { setEditingVariant(variant); setVariantFamilyId(String(variant.productFamilyId)); setVariantName(variant.name); setVariantSize(variant.totalQuantity?.toString() ?? ""); setVariantUnit(variant.totalUnit ?? ""); }} size="sm" variant="ghost">Bearbeiten</Button><Button onClick={() => void toggleVariant(variant)} size="sm" variant="ghost">{variant.isActive ? "Deaktivieren" : "Aktivieren"}</Button></div></div>)}{visibleVariants.length === 0 ? <p className="p-3 text-sm text-zinc-500">Für diese Produktfamilie sind noch keine Varianten angelegt.</p> : null}</div> : <p className="rounded-md border border-zinc-200 p-3 text-sm text-zinc-500 dark:border-zinc-800">Wähle zuerst eine Produktfamilie. Danach werden nur deren Varianten angezeigt.</p>}</CardContent></Card><MergeCard families={families} kind="family" message={message} onPreview={() => void previewFamilyMerge()} onSourceChange={setFamilySource} onTargetChange={setFamilyTarget} preview={preview?.kind === "family" ? preview.value : null} source={familySource} target={familyTarget} onApply={() => void applyMerge()} /><MergeCard families={variants.filter((variant) => variant.isActive).map((variant) => ({ id: variant.id, name: `${variant.productFamilyName}: ${variant.name}` }))} kind="variant" message={message} onPreview={() => void previewVariantMerge()} onSourceChange={setVariantSource} onTargetChange={setVariantTarget} preview={preview?.kind === "variant" ? preview.value : null} source={variantSource} target={variantTarget} onApply={() => void applyMerge()} /><Card className="xl:col-span-2"><CardHeader><CardTitle>Produktregeln</CardTitle></CardHeader><CardContent className="p-0"><div className="overflow-x-auto"><table className="min-w-[760px] w-full text-left text-sm"><thead className="border-y border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800"><tr><th className="px-4 py-3">Produkt</th><th className="px-4 py-3">Regel</th><th className="px-4 py-3">Store</th><th className="px-4 py-3">Status</th><th className="px-4 py-3 text-right">Aktion</th></tr></thead><tbody>{rules.map((rule) => <tr className="border-b border-zinc-100 dark:border-zinc-900" key={rule.id}><td className="px-4 py-3">{rule.productFamilyName}{rule.productVariantName ? ` · ${rule.productVariantName}` : ""}</td><td className="px-4 py-3"><span className="font-mono text-xs">{rule.matchType}</span> {rule.matchValue}</td><td className="px-4 py-3">{rule.storeName ?? "Global"}</td><td className="px-4 py-3"><Badge tone={rule.isActive ? "green" : "yellow"}>{rule.isActive ? "aktiv" : "inaktiv"}</Badge></td><td className="px-4 py-3 text-right"><Button onClick={() => void toggleRule(rule)} size="sm" variant="ghost">{rule.isActive ? "Deaktivieren" : "Aktivieren"}</Button><Button disabled={!rule.isActive} onClick={() => void previewRuleApply(rule)} size="sm" variant="ghost">Anwenden</Button></td></tr>)}</tbody></table></div>{rules.length === 0 ? <p className="p-6 text-sm text-zinc-500">Noch keine Produktregeln. Erstelle einen Vorschlag in der Prüfliste.</p> : null}</CardContent></Card></div>{rulePreview ? <RuleApplyDialog matchingItemsCount={rulePreview.matchingItemsCount} onCancel={() => setRulePreview(null)} onConfirm={() => void applyRule()} rule={rulePreview.rule} /> : null}</>;
}

function MergeCard({ families, kind, message, onApply, onPreview, onSourceChange, onTargetChange, preview, source, target }: { families: Array<{ id: number; name: string }>; kind: "family" | "variant"; message: string | null; onApply: () => void; onPreview: () => void; onSourceChange: (value: string) => void; onTargetChange: (value: string) => void; preview: ProductChangePreviewDTO | null; source: string; target: string }) {
  const noun = kind === "family" ? "Familien" : "Varianten";
  return <Card><CardHeader><CardTitle>{noun} zusammenführen</CardTitle></CardHeader><CardContent className="space-y-3"><p className="text-sm text-zinc-500">Die Änderung wird zuerst nur berechnet. Erst die Bestätigung verändert historische Zuordnungen und schreibt einen Audit-Eintrag.</p><select aria-label={`Quell${kind === "family" ? "familie" : "variante"}`} className={selectClass} onChange={(event) => onSourceChange(event.target.value)} value={source}><option value="">Quelle</option>{families.map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select><select aria-label={`Ziel${kind === "family" ? "familie" : "variante"}`} className={selectClass} onChange={(event) => onTargetChange(event.target.value)} value={target}><option value="">Ziel</option>{families.map((entry) => <option key={entry.id} value={entry.id}>{entry.name}</option>)}</select>{preview == null ? <Button onClick={onPreview} size="sm" variant="secondary">Vorschau berechnen</Button> : <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100"><p>{preview.affectedItemsCount} Positionen in {preview.affectedStores.length || 0} Stores werden geändert.</p><p className="mt-1 text-xs">{preview.reportImpact}</p><div className="mt-2 flex gap-2"><Button onClick={onApply} size="sm" variant="danger">Zusammenführen bestätigen</Button></div></div>}{message ? <p className="text-xs text-zinc-500">{message}</p> : null}</CardContent></Card>;
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
  const [familySearch, setFamilySearch] = useState(initialFamilyName || item.description);
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
    <Dialog title="Produktzuordnung prüfen">
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

function SplitDialog({ apiClient, item, loading, onCancel, onConfirm }: { apiClient: ApiClient; item: ProductReviewItemDTO; loading: boolean; onCancel: () => void; onConfirm: (label: string, callback: () => Promise<unknown>) => void }) { const [name, setName] = useState(""); const [size, setSize] = useState(""); const [unit, setUnit] = useState(""); const [preview, setPreview] = useState<ProductChangePreviewDTO | null>(null); const variantSplit = item.currentProductVariantId != null; const canSplit = name.trim().length > 0; async function calculate() { if (!canSplit) return; if (variantSplit) { setPreview(await apiClient.previewProductVariantSplit({ sourceVariantId: item.currentProductVariantId!, receiptItemIds: [item.receiptItemId], newVariant: { productFamilyId: item.currentProductFamilyId!, name: name.trim(), totalQuantity: size ? Number(size) : null, totalUnit: unit || null, isActive: true } })); } else { setPreview(await apiClient.previewProductFamilySplit({ sourceFamilyId: item.currentProductFamilyId!, receiptItemIds: [item.receiptItemId], newFamily: { name: name.trim(), isActive: true } })); } } function apply() { if (variantSplit) { onConfirm("Produktvariante für die Position angelegt.", () => apiClient.applyProductVariantSplit({ sourceVariantId: item.currentProductVariantId!, receiptItemIds: [item.receiptItemId], newVariant: { productFamilyId: item.currentProductFamilyId!, name: name.trim(), totalQuantity: size ? Number(size) : null, totalUnit: unit || null, isActive: true }, confirm: true })); } else { onConfirm("Produktfamilie für die Position angelegt.", () => apiClient.applyProductFamilySplit({ sourceFamilyId: item.currentProductFamilyId!, receiptItemIds: [item.receiptItemId], newFamily: { name: name.trim(), isActive: true }, confirm: true })); } } return <Dialog title={variantSplit ? "Position in neue Variante trennen" : "Position in neue Familie trennen"}><p className="text-sm text-zinc-500">Nur diese Position wird nach Vorschau und Bestätigung umgehängt. Bestehende Regeln bleiben unverändert.</p><Input aria-label="Neuer Produktname" onChange={(event) => setName(event.target.value)} placeholder={variantSplit ? "Neue Variantenbezeichnung" : "Neue Familienbezeichnung"} value={name} />{variantSplit ? <div className="grid grid-cols-2 gap-2"><Input aria-label="Neue Variantenmenge" onChange={(event) => setSize(event.target.value)} placeholder="Menge" step="0.001" type="number" value={size} /><Input aria-label="Neue Varianteneinheit" onChange={(event) => setUnit(event.target.value)} placeholder="Einheit" value={unit} /></div> : null}{preview == null ? <Button disabled={!canSplit || loading} onClick={() => void calculate()} size="sm" variant="secondary">Vorschau berechnen</Button> : <div className="rounded-md border border-amber-200 bg-amber-50 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-100">{preview.affectedItemsCount} Position wird umgehängt.</div>}<DialogActions onCancel={onCancel} onConfirm={apply} disabled={!preview || loading} label="Trennung bestätigen" /></Dialog>; }

function RuleSuggestionDialog({ loading, onCancel, onConfirm, suggestion }: { loading: boolean; onCancel: () => void; onConfirm: (applyToExisting: boolean) => void; suggestion: ProductRuleSuggestionDTO }) { const [applyToExisting, setApplyToExisting] = useState(false); return <Dialog title="Produktregel bestätigen"><p className="text-sm text-zinc-700 dark:text-zinc-200"><span className="font-medium">{suggestion.rule.matchType}</span> "{suggestion.rule.matchValue}"</p><p className="text-sm text-zinc-500">Vorschau: {suggestion.preview.matchingItemsCount} Positionen passen zur Regel.</p><label className="flex items-start gap-2 text-sm"><input checked={applyToExisting} onChange={(event) => setApplyToExisting(event.target.checked)} type="checkbox" />Auf bestehende passende Positionen anwenden</label><DialogActions onCancel={onCancel} onConfirm={() => onConfirm(applyToExisting)} disabled={loading} label="Regel bestätigen" /></Dialog>; }

function RuleApplyDialog({ matchingItemsCount, onCancel, onConfirm, rule }: { matchingItemsCount: number; onCancel: () => void; onConfirm: () => void; rule: ProductRuleDTO }) { return <Dialog title="Produktregel rückwirkend anwenden"><p className="text-sm text-zinc-600 dark:text-zinc-300">{rule.matchType} "{rule.matchValue}" passt auf {matchingItemsCount} bestehende Positionen.</p><p className="text-sm text-zinc-500">Manuelle Bestätigungen und bereits geschützte Zuordnungen werden vom Backend nicht still überschrieben.</p><DialogActions onCancel={onCancel} onConfirm={onConfirm} disabled={false} label="Anwendung bestätigen" /></Dialog>; }

function Dialog({ children, title }: { children: React.ReactNode; title: string }) { return <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4"><Card className="w-full max-w-lg"><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent className="space-y-3">{children}</CardContent></Card></div>; }
function DialogActions({ disabled, label, onCancel, onConfirm }: { disabled: boolean; label: string; onCancel: () => void; onConfirm: () => void }) { return <div className="flex justify-end gap-2"><Button onClick={onCancel} size="sm" variant="secondary">Abbrechen</Button><Button disabled={disabled} onClick={onConfirm} size="sm">{label}</Button></div>; }
function IconButton({ children, disabled, label, onClick }: { children: React.ReactNode; disabled: boolean; label: string; onClick: () => void }) { return <button aria-label={label} className="flex h-8 w-8 items-center justify-center rounded-md border border-zinc-200 text-zinc-600 hover:bg-zinc-100 disabled:cursor-not-allowed disabled:opacity-40 dark:border-zinc-800 dark:text-zinc-300 dark:hover:bg-zinc-900" disabled={disabled} onClick={onClick} title={label} type="button">{children}</button>; }
function TokenHint() { return <Card><CardContent className="py-10 text-center text-sm text-zinc-500">Für Produktzuordnungen muss oben ein API-Token gesetzt sein.</CardContent></Card>; }
function tabClass(active: boolean) { return `border-b-2 px-3 py-2 text-sm font-medium ${active ? "border-zinc-950 text-zinc-950 dark:border-zinc-50 dark:text-zinc-50" : "border-transparent text-zinc-500 hover:text-zinc-950 dark:text-zinc-400 dark:hover:text-zinc-50"}`; }
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
function formatDate(value: string | null) { return value ? new Intl.DateTimeFormat("de-DE").format(new Date(`${value}T00:00:00`)) : "Datum offen"; }
function formatEuro(value: number | null) { return value == null ? "-" : new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(value); }
function formatQuantity(item: ProductReviewItemDTO) { return item.quantity == null ? "Menge offen" : `${item.quantity} ${item.unit ?? ""}`.trim(); }
function labelStatus(status: ProductReviewItemDTO["assignmentStatus"]) { return status === "NEEDS_REVIEW" ? "Prüfen" : status ?? "Offen"; }
