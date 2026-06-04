import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Check,
  ChevronLeft,
  ChevronRight,
  FileText,
  Loader2,
  Pencil,
  Plus,
  RefreshCw,
  RotateCcw,
  Save,
  Search,
  Trash2,
  X
} from "lucide-react";

import { CategorySourceBadge, DeleteReasonBadge, ParseStatusBadge } from "@/components/receipt-badges";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { formatCurrency, formatDate, formatDateTime, formatNumber, formatPercent, formatTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  CategoryDTO,
  AiCategorizationRejectionReason,
  CategorySource,
  PageResponse,
  ParseStatus,
  ReceiptDTO,
  ReceiptItemDTO,
  ReceiptItemUpdateRequest,
  ReceiptListParams,
  ReceiptUpdateRequest
} from "@/lib/types";

type SortKey = NonNullable<ReceiptListParams["sortBy"]>;

interface ReceiptsPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
  selectedReceiptId: number | null;
}

interface ReceiptFilters {
  status: ParseStatus | "";
  store: string;
  dateFrom: string;
  dateTo: string;
  includeDeleted: boolean;
}

interface ReceiptDraft {
  receiptDate: string;
  receiptTime: string;
  storeName: string;
  storeBranch: string;
  totalAmount: string;
  currency: string;
  bonusBalance: string;
  bonusPoints: string;
  bonusType: string;
  items: ReceiptItemDraft[];
}

interface ReceiptItemDraft {
  clientId: string;
  id: number | null;
  description: string;
  quantity: string;
  unit: string;
  unitPrice: string;
  totalPrice: string;
  discountAmount: string;
  categoryId: string;
  markedForDeletion: boolean;
}

const pageSize = 20;
const statusOptions: Array<{ value: ParseStatus | ""; label: string }> = [
  { value: "", label: "Alle Status" },
  { value: "PARSED", label: "Geparst" },
  { value: "PARSE_ERROR", label: "Parse-Fehler" },
  { value: "MANUALLY_EDITED", label: "Bearbeitet" },
  { value: "PENDING", label: "Ausstehend" }
];

export function ReceiptsPage({ apiClient, hasApiToken, selectedReceiptId }: ReceiptsPageProps) {
  const [filters, setFilters] = useState<ReceiptFilters>({
    status: "",
    store: "",
    dateFrom: "",
    dateTo: "",
    includeDeleted: false
  });
  const [page, setPage] = useState(0);
  const [sortBy, setSortBy] = useState<SortKey>("receiptDate");
  const [sortDir, setSortDir] = useState<"asc" | "desc">("desc");
  const [receipts, setReceipts] = useState<PageResponse<ReceiptDTO> | null>(null);
  const [selectedReceipt, setSelectedReceipt] = useState<ReceiptDTO | null>(null);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [draft, setDraft] = useState<ReceiptDraft | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [reparsing, setReparsing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [applyingSuggestionId, setApplyingSuggestionId] = useState<number | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [overwriteManualEdits, setOverwriteManualEdits] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const categoriesById = useMemo(
    () => new Map(categories.map((category) => [category.id, category])),
    [categories]
  );

  const loadList = useCallback(async () => {
    if (!hasApiToken) {
      setReceipts(null);
      return;
    }

    setListLoading(true);
    setError(null);

    try {
      const response = await apiClient.receipts({
        page,
        size: pageSize,
        sortBy,
        sortDir,
        status: filters.status,
        store: filters.store.trim() || undefined,
        dateFrom: filters.dateFrom || undefined,
        dateTo: filters.dateTo || undefined,
        includeDeleted: filters.includeDeleted
      });
      setReceipts(response);
    } catch (loadError) {
      setError(toUserMessage(loadError));
    } finally {
      setListLoading(false);
    }
  }, [apiClient, filters, hasApiToken, page, sortBy, sortDir]);

  const loadCategories = useCallback(async () => {
    if (!hasApiToken) {
      setCategories([]);
      return;
    }

    try {
      setCategories(await apiClient.categories(false));
    } catch (loadError) {
      setError(toUserMessage(loadError));
    }
  }, [apiClient, hasApiToken]);

  const loadReceipt = useCallback(async () => {
    if (!hasApiToken || selectedReceiptId === null) {
      setSelectedReceipt(null);
      setDraft(null);
      setEditMode(false);
      return;
    }

    setDetailLoading(true);
    setError(null);

    try {
      const response = await apiClient.receipt(selectedReceiptId);
      setSelectedReceipt(response);
      setDraft(toDraft(response));
      setOverwriteManualEdits(false);
    } catch (loadError) {
      setSelectedReceipt(null);
      setDraft(null);
      setError(toUserMessage(loadError));
    } finally {
      setDetailLoading(false);
    }
  }, [apiClient, hasApiToken, selectedReceiptId]);

  useEffect(() => {
    void loadCategories();
  }, [loadCategories]);

  useEffect(() => {
    void loadList();
  }, [loadList]);

  useEffect(() => {
    void loadReceipt();
  }, [loadReceipt]);

  function updateFilters(nextFilters: Partial<ReceiptFilters>) {
    setFilters((current) => ({ ...current, ...nextFilters }));
    setPage(0);
  }

  function changeSort(nextSortBy: SortKey) {
    if (sortBy === nextSortBy) {
      setSortDir((current) => (current === "asc" ? "desc" : "asc"));
    } else {
      setSortBy(nextSortBy);
      setSortDir("asc");
    }
    setPage(0);
  }

  async function triggerSync() {
    setSyncing(true);
    setError(null);
    setNotice(null);

    try {
      await apiClient.triggerSync();
      setNotice("Sync wurde gestartet.");
      await loadList();
    } catch (syncError) {
      setError(toUserMessage(syncError));
    } finally {
      setSyncing(false);
    }
  }

  async function saveDraft() {
    if (!selectedReceipt || !draft) {
      return;
    }

    setSaving(true);
    setError(null);
    setNotice(null);

    try {
      const request = toUpdateRequest(draft, selectedReceipt);
      const updated = await apiClient.updateReceipt(selectedReceipt.id, request);
      setSelectedReceipt(updated);
      setDraft(toDraft(updated));
      setEditMode(false);
      setNotice("Bon gespeichert.");
      await loadList();
    } catch (saveError) {
      setError(toUserMessage(saveError));
    } finally {
      setSaving(false);
    }
  }

  async function applyAiSuggestion(item: ReceiptItemDTO) {
    if (!item.aiSuggestion?.categoryId) {
      return;
    }

    setApplyingSuggestionId(item.id);
    setError(null);
    setNotice(null);

    try {
      await apiClient.updateReceiptItem(item.id, {
        categoryId: item.aiSuggestion.categoryId,
        categorySource: "MANUAL"
      });
      setNotice("KI-Vorschlag wurde manuell übernommen.");
      await loadReceipt();
      await loadList();
    } catch (suggestionError) {
      setError(toUserMessage(suggestionError));
    } finally {
      setApplyingSuggestionId(null);
    }
  }

  async function reparseSelectedReceipt() {
    if (!selectedReceipt) {
      return;
    }

    setReparsing(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await apiClient.reparseReceipt(selectedReceipt.id, overwriteManualEdits);
      setSelectedReceipt(updated);
      setDraft(toDraft(updated));
      setEditMode(false);
      setNotice("Bon wurde erneut geparst.");
      await loadList();
    } catch (reparseError) {
      setError(toUserMessage(reparseError));
    } finally {
      setReparsing(false);
    }
  }

  async function deleteSelectedReceipt() {
    if (!selectedReceipt || !window.confirm("Diesen Bon wirklich löschen?")) {
      return;
    }

    setDeleting(true);
    setError(null);
    setNotice(null);

    try {
      await apiClient.deleteReceipt(selectedReceipt.id);
      setNotice("Bon wurde gelöscht.");
      window.location.hash = "#/receipts";
      await loadList();
    } catch (deleteError) {
      setError(toUserMessage(deleteError));
    } finally {
      setDeleting(false);
    }
  }

  if (!hasApiToken) {
    return (
      <Card>
        <CardContent className="flex min-h-72 flex-col items-center justify-center gap-3 text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-md bg-zinc-100 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-200">
            <FileText className="h-5 w-5" />
          </span>
          <div>
            <h2 className="text-base font-semibold text-zinc-950 dark:text-zinc-50">API-Token erforderlich</h2>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Danach können Bons geladen und bearbeitet werden.</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {error ? <Message tone="error" text={error} /> : null}
      {notice ? <Message tone="success" text={notice} /> : null}

      <div className="grid gap-4 xl:grid-cols-[minmax(480px,0.9fr)_minmax(0,1.1fr)]">
        <ReceiptListPanel
          filters={filters}
          listLoading={listLoading}
          onFilterChange={updateFilters}
          onPageChange={setPage}
          onReceiptSelect={(id) => {
            window.location.hash = `#/receipts/${id}`;
          }}
          onSortChange={changeSort}
          onSync={triggerSync}
          page={page}
          receipts={receipts}
          selectedReceiptId={selectedReceiptId}
          sortBy={sortBy}
          sortDir={sortDir}
          syncing={syncing}
        />

        <ReceiptDetailPanel
          applyingSuggestionId={applyingSuggestionId}
          categories={categories}
          categoriesById={categoriesById}
          deleting={deleting}
          detailLoading={detailLoading}
          draft={draft}
          editMode={editMode}
          onApplyAiSuggestion={applyAiSuggestion}
          onBack={() => {
            window.location.hash = "#/receipts";
          }}
          onCancelEdit={() => {
            setDraft(selectedReceipt ? toDraft(selectedReceipt) : null);
            setEditMode(false);
          }}
          onDeleteReceipt={deleteSelectedReceipt}
          onDraftChange={setDraft}
          onEdit={() => setEditMode(true)}
          onReparse={reparseSelectedReceipt}
          onSave={saveDraft}
          onSetOverwriteManualEdits={setOverwriteManualEdits}
          overwriteManualEdits={overwriteManualEdits}
          receipt={selectedReceipt}
          reparsing={reparsing}
          saving={saving}
        />
      </div>
    </div>
  );
}

function ReceiptListPanel({
  filters,
  listLoading,
  onFilterChange,
  onPageChange,
  onReceiptSelect,
  onSortChange,
  onSync,
  page,
  receipts,
  selectedReceiptId,
  sortBy,
  sortDir,
  syncing
}: {
  filters: ReceiptFilters;
  listLoading: boolean;
  onFilterChange: (filters: Partial<ReceiptFilters>) => void;
  onPageChange: (page: number) => void;
  onReceiptSelect: (id: number) => void;
  onSortChange: (sortBy: SortKey) => void;
  onSync: () => void;
  page: number;
  receipts: PageResponse<ReceiptDTO> | null;
  selectedReceiptId: number | null;
  sortBy: SortKey;
  sortDir: "asc" | "desc";
  syncing: boolean;
}) {
  return (
    <Card>
      <CardHeader className="space-y-3">
        <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle>Bons</CardTitle>
          <Button disabled={syncing} onClick={onSync} size="sm">
            {syncing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            Sync starten
          </Button>
        </div>
        <div className="grid gap-2 md:grid-cols-[minmax(120px,0.8fr)_minmax(180px,1fr)_repeat(2,minmax(130px,0.8fr))]">
          <select
            className={inputClassName}
            onChange={(event) => onFilterChange({ status: event.target.value as ParseStatus | "" })}
            value={filters.status}
          >
            {statusOptions.map((option) => (
              <option key={option.value || "all"} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <div className="relative">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
            <Input
              className="pl-9"
              onChange={(event) => onFilterChange({ store: event.target.value })}
              placeholder="Geschäft"
              value={filters.store}
            />
          </div>
          <Input
            aria-label="Datum von"
            onChange={(event) => onFilterChange({ dateFrom: event.target.value })}
            type="date"
            value={filters.dateFrom}
          />
          <Input
            aria-label="Datum bis"
            onChange={(event) => onFilterChange({ dateTo: event.target.value })}
            type="date"
            value={filters.dateTo}
          />
        </div>
        <label className="flex items-center gap-2 text-sm text-zinc-600 dark:text-zinc-300">
          <input
            checked={filters.includeDeleted}
            className="h-4 w-4 rounded border-zinc-300 text-zinc-950"
            onChange={(event) => onFilterChange({ includeDeleted: event.target.checked })}
            type="checkbox"
          />
          Gelöschte Bons anzeigen
        </label>
      </CardHeader>
      <CardContent className="p-0">
        {listLoading ? (
          <div className="space-y-2 p-4">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : receipts?.content.length ? (
          <div className="overflow-x-auto">
            <table className="w-full min-w-[760px] text-sm">
              <thead>
                <tr className="border-b border-zinc-100 text-left text-xs uppercase text-zinc-500 dark:border-zinc-900 dark:text-zinc-400">
                  <SortableHeader active={sortBy === "receiptDate"} direction={sortDir} label="Datum" onClick={() => onSortChange("receiptDate")} />
                  <SortableHeader active={sortBy === "storeName"} direction={sortDir} label="Geschäft" onClick={() => onSortChange("storeName")} />
                  <SortableHeader active={sortBy === "totalAmount"} direction={sortDir} label="Betrag" onClick={() => onSortChange("totalAmount")} right />
                  <th className="px-3 py-2 font-medium">Positionen</th>
                  <SortableHeader active={sortBy === "parseStatus"} direction={sortDir} label="Status" onClick={() => onSortChange("parseStatus")} />
                  <SortableHeader active={sortBy === "importedAt"} direction={sortDir} label="Import" onClick={() => onSortChange("importedAt")} />
                </tr>
              </thead>
              <tbody>
                {receipts.content.map((receipt) => (
                  <tr
                    className={cn(
                      "cursor-pointer border-b border-zinc-100 last:border-0 hover:bg-zinc-50 dark:border-zinc-900 dark:hover:bg-zinc-900/60",
                      selectedReceiptId === receipt.id && "bg-zinc-100 dark:bg-zinc-900",
                      receipt.deletedAt && "opacity-70"
                    )}
                    key={receipt.id}
                    onClick={() => onReceiptSelect(receipt.id)}
                  >
                    <td className="px-3 py-3">{formatDate(receipt.receiptDate)}</td>
                    <td className="px-3 py-3">
                      <div className="font-medium text-zinc-950 dark:text-zinc-50">{receipt.storeName ?? "Unbekannt"}</div>
                      <div className="text-xs text-zinc-500 dark:text-zinc-400">{receipt.storeBranch ?? "-"}</div>
                    </td>
                    <td className="px-3 py-3 text-right font-medium">{formatCurrency(receipt.totalAmount)}</td>
                    <td className="px-3 py-3">{formatNumber(receipt.items.length)}</td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1">
                        <ParseStatusBadge status={receipt.parseStatus} />
                        {receipt.deleteReason ? <DeleteReasonBadge reason={receipt.deleteReason} /> : null}
                      </div>
                    </td>
                    <td className="px-3 py-3">{formatDateTime(receipt.importedAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : (
          <EmptyState text="Keine Bons gefunden" />
        )}
        <div className="flex flex-col gap-2 border-t border-zinc-100 px-4 py-3 text-sm dark:border-zinc-900 md:flex-row md:items-center md:justify-between">
          <span className="text-zinc-500 dark:text-zinc-400">
            {formatNumber(receipts?.totalElements)} Bons · Seite {formatNumber((receipts?.page ?? 0) + 1)} von {formatNumber(receipts?.totalPages ?? 1)}
          </span>
          <div className="flex gap-2">
            <Button disabled={page <= 0 || listLoading} onClick={() => onPageChange(Math.max(page - 1, 0))} size="sm" variant="secondary">
              <ChevronLeft className="h-4 w-4" />
              Zurück
            </Button>
            <Button
              disabled={listLoading || !receipts || page >= receipts.totalPages - 1}
              onClick={() => onPageChange(page + 1)}
              size="sm"
              variant="secondary"
            >
              Weiter
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </CardContent>
    </Card>
  );
}

function ReceiptDetailPanel({
  applyingSuggestionId,
  categories,
  categoriesById,
  deleting,
  detailLoading,
  draft,
  editMode,
  onApplyAiSuggestion,
  onBack,
  onCancelEdit,
  onDeleteReceipt,
  onDraftChange,
  onEdit,
  onReparse,
  onSave,
  onSetOverwriteManualEdits,
  overwriteManualEdits,
  receipt,
  reparsing,
  saving
}: {
  applyingSuggestionId: number | null;
  categories: CategoryDTO[];
  categoriesById: Map<number, CategoryDTO>;
  deleting: boolean;
  detailLoading: boolean;
  draft: ReceiptDraft | null;
  editMode: boolean;
  onApplyAiSuggestion: (item: ReceiptItemDTO) => void;
  onBack: () => void;
  onCancelEdit: () => void;
  onDeleteReceipt: () => void;
  onDraftChange: (draft: ReceiptDraft) => void;
  onEdit: () => void;
  onReparse: () => void;
  onSave: () => void;
  onSetOverwriteManualEdits: (overwrite: boolean) => void;
  overwriteManualEdits: boolean;
  receipt: ReceiptDTO | null;
  reparsing: boolean;
  saving: boolean;
}) {
  if (detailLoading) {
    return (
      <Card>
        <CardContent className="space-y-3">
          <Skeleton className="h-8 w-56" />
          <Skeleton className="h-20 w-full" />
          <Skeleton className="h-64 w-full" />
        </CardContent>
      </Card>
    );
  }

  if (!receipt || !draft) {
    return (
      <Card>
        <CardContent className="flex min-h-72 flex-col items-center justify-center gap-3 text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-md bg-zinc-100 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-200">
            <FileText className="h-5 w-5" />
          </span>
          <div>
            <h2 className="text-base font-semibold text-zinc-950 dark:text-zinc-50">Bon auswählen</h2>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Die Detailansicht erscheint hier.</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  const hasManualItems = receipt.items.some((item) => item.isManuallyEdited) || receipt.parseStatus === "MANUALLY_EDITED";

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="space-y-3">
          <div className="flex flex-col gap-3 lg:flex-row lg:items-start lg:justify-between">
            <div className="min-w-0">
              <button className="mb-2 text-sm text-zinc-500 hover:text-zinc-950 dark:text-zinc-400 dark:hover:text-zinc-50" onClick={onBack}>
                Zur Bon-Liste
              </button>
              {editMode ? (
                <div className="grid gap-2 md:grid-cols-2">
                  <Input
                    aria-label="Geschäft"
                    onChange={(event) => onDraftChange({ ...draft, storeName: event.target.value })}
                    placeholder="Geschäft"
                    value={draft.storeName}
                  />
                  <Input
                    aria-label="Filiale"
                    onChange={(event) => onDraftChange({ ...draft, storeBranch: event.target.value })}
                    placeholder="Filiale"
                    value={draft.storeBranch}
                  />
                </div>
              ) : (
                <>
                  <h2 className="truncate text-lg font-semibold text-zinc-950 dark:text-zinc-50">{receipt.storeName ?? "Unbekannter Bon"}</h2>
                  <p className="text-sm text-zinc-500 dark:text-zinc-400">{receipt.storeBranch ?? "Keine Filiale"}</p>
                </>
              )}
            </div>
            <div className="flex flex-wrap gap-2">
              {editMode ? (
                <>
                  <Button disabled={saving} onClick={onSave} size="sm">
                    {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
                    Speichern
                  </Button>
                  <Button disabled={saving} onClick={onCancelEdit} size="sm" variant="secondary">
                    <X className="h-4 w-4" />
                    Abbrechen
                  </Button>
                </>
              ) : (
                <>
                  <Button onClick={onEdit} size="sm" variant="secondary">
                    <Pencil className="h-4 w-4" />
                    Bearbeiten
                  </Button>
                  <Button disabled={reparsing || (hasManualItems && !overwriteManualEdits)} onClick={onReparse} size="sm" variant="secondary">
                    {reparsing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RotateCcw className="h-4 w-4" />}
                    Erneut parsen
                  </Button>
                  <Button disabled={deleting} onClick={onDeleteReceipt} size="sm" variant="danger">
                    {deleting ? <Loader2 className="h-4 w-4 animate-spin" /> : <Trash2 className="h-4 w-4" />}
                    Löschen
                  </Button>
                </>
              )}
            </div>
          </div>
          {hasManualItems && !editMode ? (
            <label className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
              <input
                checked={overwriteManualEdits}
                className="mt-0.5 h-4 w-4"
                onChange={(event) => onSetOverwriteManualEdits(event.target.checked)}
                type="checkbox"
              />
              Manuell editierte Positionen beim Re-Parse überschreiben.
            </label>
          ) : null}
        </CardHeader>
        <CardContent className="space-y-4">
          {editMode ? (
            <ReceiptMetadataEditor draft={draft} onDraftChange={onDraftChange} />
          ) : (
            <ReceiptMetadata receipt={receipt} />
          )}

          {receipt.parseErrorMessage ? (
            <div className="flex gap-2 rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
              <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" />
              {receipt.parseErrorMessage}
            </div>
          ) : null}
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
          <CardTitle>Positionen</CardTitle>
          {editMode ? (
            <Button onClick={() => onDraftChange({ ...draft, items: [...draft.items, emptyDraftItem()] })} size="sm" variant="secondary">
              <Plus className="h-4 w-4" />
              Position hinzufügen
            </Button>
          ) : null}
        </CardHeader>
        <CardContent className="p-0">
          {editMode ? (
            <ReceiptItemsEditor categories={categories} draft={draft} onDraftChange={onDraftChange} />
          ) : (
            <ReceiptItemsTable
              applyingSuggestionId={applyingSuggestionId}
              categoriesById={categoriesById}
              items={receipt.items}
              onApplyAiSuggestion={onApplyAiSuggestion}
            />
          )}
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Rohtext</CardTitle>
        </CardHeader>
        <CardContent>
          <details>
            <summary className="cursor-pointer text-sm font-medium text-zinc-700 dark:text-zinc-200">Rohtext anzeigen</summary>
            <pre className="mt-3 max-h-96 overflow-auto rounded-md bg-zinc-950 p-3 text-xs leading-relaxed text-zinc-50">
              {receipt.rawText || "Kein Rohtext vorhanden."}
            </pre>
          </details>
        </CardContent>
      </Card>
    </div>
  );
}

function ReceiptMetadata({ receipt }: { receipt: ReceiptDTO }) {
  return (
    <div className="grid gap-3 text-sm md:grid-cols-2 xl:grid-cols-4">
      <Metric label="Datum" value={`${formatDate(receipt.receiptDate)} · ${formatTime(receipt.receiptTime)}`} />
      <Metric label="Gesamtbetrag" value={formatCurrency(receipt.totalAmount)} />
      <Metric label="Import" value={formatDateTime(receipt.importedAt)} />
      <div>
        <p className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Status</p>
        <div className="mt-1 flex flex-wrap gap-1">
          <ParseStatusBadge status={receipt.parseStatus} />
          {receipt.deleteReason ? <DeleteReasonBadge reason={receipt.deleteReason} /> : null}
        </div>
      </div>
      <Metric label="Bonus" value={formatBonus(receipt)} />
      <Metric label="Paperless-ID" value={receipt.paperlessDocumentId?.toString() ?? "-"} />
    </div>
  );
}

function ReceiptMetadataEditor({ draft, onDraftChange }: { draft: ReceiptDraft; onDraftChange: (draft: ReceiptDraft) => void }) {
  return (
    <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
      <Field label="Datum">
        <Input onChange={(event) => onDraftChange({ ...draft, receiptDate: event.target.value })} type="date" value={draft.receiptDate} />
      </Field>
      <Field label="Uhrzeit">
        <Input onChange={(event) => onDraftChange({ ...draft, receiptTime: event.target.value })} step="1" type="time" value={draft.receiptTime} />
      </Field>
      <Field label="Gesamtbetrag">
        <Input onChange={(event) => onDraftChange({ ...draft, totalAmount: event.target.value })} step="0.01" type="number" value={draft.totalAmount} />
      </Field>
      <Field label="Währung">
        <Input maxLength={3} onChange={(event) => onDraftChange({ ...draft, currency: event.target.value.toUpperCase() })} value={draft.currency} />
      </Field>
      <Field label="Bonusguthaben">
        <Input onChange={(event) => onDraftChange({ ...draft, bonusBalance: event.target.value })} step="0.01" type="number" value={draft.bonusBalance} />
      </Field>
      <Field label="Bonuspunkte">
        <Input onChange={(event) => onDraftChange({ ...draft, bonusPoints: event.target.value })} step="0.01" type="number" value={draft.bonusPoints} />
      </Field>
      <Field label="Bonustyp">
        <Input onChange={(event) => onDraftChange({ ...draft, bonusType: event.target.value })} value={draft.bonusType} />
      </Field>
    </div>
  );
}

function ReceiptItemsTable({
  applyingSuggestionId,
  categoriesById,
  items,
  onApplyAiSuggestion
}: {
  applyingSuggestionId: number | null;
  categoriesById: Map<number, CategoryDTO>;
  items: ReceiptItemDTO[];
  onApplyAiSuggestion: (item: ReceiptItemDTO) => void;
}) {
  if (!items.length) {
    return <EmptyState text="Keine Positionen" />;
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[920px] text-sm">
        <thead>
          <tr className="border-b border-zinc-100 text-left text-xs uppercase text-zinc-500 dark:border-zinc-900 dark:text-zinc-400">
            <th className="px-3 py-2 font-medium">#</th>
            <th className="px-3 py-2 font-medium">Beschreibung</th>
            <th className="px-3 py-2 text-right font-medium">Menge</th>
            <th className="px-3 py-2 text-right font-medium">Einzelpreis</th>
            <th className="px-3 py-2 text-right font-medium">Gesamt</th>
            <th className="px-3 py-2 text-right font-medium">Rabatt</th>
            <th className="px-3 py-2 font-medium">Kategorie</th>
          </tr>
        </thead>
        <tbody>
          {items.map((item) => (
            <tr className="border-b border-zinc-100 align-top last:border-0 dark:border-zinc-900" key={item.id}>
              <td className="px-3 py-3 text-zinc-500">{item.positionIndex + 1}</td>
              <td className="px-3 py-3">
                <div className="font-medium text-zinc-950 dark:text-zinc-50">{item.description}</div>
                {item.isManuallyEdited ? <div className="mt-1 text-xs text-zinc-500 dark:text-zinc-400">Manuell bearbeitet</div> : null}
              </td>
              <td className="px-3 py-3 text-right">
                {item.quantity ? `${formatNumber(item.quantity)} ${item.unit ?? ""}`.trim() : "-"}
              </td>
              <td className="px-3 py-3 text-right">{item.unitPrice == null ? "-" : formatCurrency(item.unitPrice)}</td>
              <td className="px-3 py-3 text-right font-medium">{formatCurrency(item.totalPrice)}</td>
              <td className="px-3 py-3 text-right">{item.discountAmount == null ? "-" : formatCurrency(item.discountAmount)}</td>
              <td className="px-3 py-3">
                <div className="space-y-2">
                  <CategoryCell category={item.categoryId ? categoriesById.get(item.categoryId) : null} item={item} />
                  {item.categoryId && item.categorySource ? <CategorySourceBadge source={item.categorySource} /> : null}
                  {!item.categoryId && item.aiSuggestion ? (
                    <AiSuggestionHint
                      applying={applyingSuggestionId === item.id}
                      item={item}
                      onApply={() => onApplyAiSuggestion(item)}
                    />
                  ) : null}
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ReceiptItemsEditor({
  categories,
  draft,
  onDraftChange
}: {
  categories: CategoryDTO[];
  draft: ReceiptDraft;
  onDraftChange: (draft: ReceiptDraft) => void;
}) {
  const visibleItems = draft.items.filter((item) => !item.markedForDeletion);

  function updateItem(clientId: string, changes: Partial<ReceiptItemDraft>) {
    onDraftChange({
      ...draft,
      items: draft.items.map((item) => (item.clientId === clientId ? { ...item, ...changes } : item))
    });
  }

  function markDeleted(clientId: string) {
    onDraftChange({
      ...draft,
      items: draft.items.map((item) => (item.clientId === clientId ? { ...item, markedForDeletion: true } : item))
    });
  }

  return (
    <div className="space-y-3 p-4">
      {visibleItems.map((item, index) => (
        <div className="rounded-md border border-zinc-200 p-3 dark:border-zinc-800" key={item.clientId}>
          <div className="mb-3 flex items-center justify-between gap-2">
            <span className="text-sm font-medium text-zinc-700 dark:text-zinc-200">Position {index + 1}</span>
            <Button onClick={() => markDeleted(item.clientId)} size="sm" variant="ghost">
              <Trash2 className="h-4 w-4" />
              Entfernen
            </Button>
          </div>
          <div className="grid gap-3 lg:grid-cols-[minmax(240px,2fr)_repeat(4,minmax(100px,0.8fr))]">
            <Field label="Beschreibung">
              <Textarea
                className="min-h-20"
                maxLength={512}
                onChange={(event) => updateItem(item.clientId, { description: event.target.value })}
                value={item.description}
              />
            </Field>
            <Field label="Menge">
              <Input onChange={(event) => updateItem(item.clientId, { quantity: event.target.value })} step="0.001" type="number" value={item.quantity} />
            </Field>
            <Field label="Einheit">
              <Input onChange={(event) => updateItem(item.clientId, { unit: event.target.value })} value={item.unit} />
            </Field>
            <Field label="Einzelpreis">
              <Input onChange={(event) => updateItem(item.clientId, { unitPrice: event.target.value })} step="0.01" type="number" value={item.unitPrice} />
            </Field>
            <Field label="Gesamtpreis">
              <Input required onChange={(event) => updateItem(item.clientId, { totalPrice: event.target.value })} step="0.01" type="number" value={item.totalPrice} />
            </Field>
          </div>
          <div className="mt-3 grid gap-3 md:grid-cols-[minmax(130px,0.8fr)_minmax(220px,1.2fr)]">
            <Field label="Rabatt">
              <Input onChange={(event) => updateItem(item.clientId, { discountAmount: event.target.value })} step="0.01" type="number" value={item.discountAmount} />
            </Field>
            <Field label="Kategorie">
              <select className={inputClassName} onChange={(event) => updateItem(item.clientId, { categoryId: event.target.value })} value={item.categoryId}>
                <option value="">Ohne Kategorie</option>
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>
          </div>
        </div>
      ))}
      {!visibleItems.length ? <EmptyState text="Keine Positionen im Entwurf" /> : null}
    </div>
  );
}

function CategoryCell({ category, item }: { category: CategoryDTO | null | undefined; item: ReceiptItemDTO }) {
  if (!item.categoryId) {
    return <Badge>Ohne Kategorie</Badge>;
  }

  return (
    <span
      className="inline-flex items-center rounded-full border px-2 py-0.5 text-xs font-medium"
      style={{
        backgroundColor: `${category?.colorHex ?? "#71717a"}1A`,
        color: category?.colorHex ?? "#52525b",
        borderColor: category?.colorHex ?? "#d4d4d8"
      }}
    >
      {item.categoryName ?? category?.name ?? "Kategorie"}
    </span>
  );
}

function AiSuggestionHint({ applying, item, onApply }: { applying: boolean; item: ReceiptItemDTO; onApply: () => void }) {
  const suggestion = item.aiSuggestion;
  if (!suggestion) {
    return null;
  }

  return (
    <div className="rounded-md border border-amber-200 bg-amber-50 p-2 text-xs text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
      <div>
        KI-Vorschlag: {suggestion.categoryName ?? "unbekannte Kategorie"}
        {suggestion.confidence == null ? "" : ` (${formatPercent(suggestion.confidence * 100)})`}
      </div>
      <div className="mt-1 text-amber-700 dark:text-amber-300">{rejectionReasonLabel(suggestion.rejectionReason)}</div>
      {suggestion.categoryId ? (
        <Button className="mt-2" disabled={applying} onClick={onApply} size="sm" variant="secondary">
          {applying ? <Loader2 className="h-4 w-4 animate-spin" /> : <Check className="h-4 w-4" />}
          Übernehmen
        </Button>
      ) : null}
    </div>
  );
}

function SortableHeader({
  active,
  direction,
  label,
  onClick,
  right = false
}: {
  active: boolean;
  direction: "asc" | "desc";
  label: string;
  onClick: () => void;
  right?: boolean;
}) {
  const Icon = !active ? ArrowUpDown : direction === "asc" ? ArrowUp : ArrowDown;
  return (
    <th className={cn("px-3 py-2 font-medium", right && "text-right")}>
      <button className={cn("inline-flex items-center gap-1", right && "justify-end")} onClick={onClick}>
        {label}
        <Icon className="h-3.5 w-3.5" />
      </button>
    </th>
  );
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <p className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</p>
      <p className="mt-1 font-medium text-zinc-950 dark:text-zinc-50">{value}</p>
    </div>
  );
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="space-y-1 text-sm">
      <span className="block text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</span>
      {children}
    </label>
  );
}

function Message({ text, tone }: { text: string; tone: "error" | "success" }) {
  return (
    <div
      className={
        tone === "error"
          ? "rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-200"
          : "rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200"
      }
    >
      {text}
    </div>
  );
}

function EmptyState({ text }: { text: string }) {
  return <div className="m-4 rounded-md border border-dashed border-zinc-200 px-4 py-8 text-center text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">{text}</div>;
}

function toDraft(receipt: ReceiptDTO): ReceiptDraft {
  return {
    receiptDate: receipt.receiptDate ?? "",
    receiptTime: receipt.receiptTime?.slice(0, 8) ?? "",
    storeName: receipt.storeName ?? "",
    storeBranch: receipt.storeBranch ?? "",
    totalAmount: toInputNumber(receipt.totalAmount),
    currency: receipt.currency ?? "EUR",
    bonusBalance: toInputNumber(receipt.bonusBalance),
    bonusPoints: toInputNumber(receipt.bonusPoints),
    bonusType: receipt.bonusType ?? "",
    items: receipt.items.map(toItemDraft)
  };
}

function toItemDraft(item: ReceiptItemDTO): ReceiptItemDraft {
  return {
    clientId: `existing-${item.id}`,
    id: item.id,
    description: item.description,
    quantity: toInputNumber(item.quantity),
    unit: item.unit ?? "",
    unitPrice: toInputNumber(item.unitPrice),
    totalPrice: toInputNumber(item.totalPrice),
    discountAmount: toInputNumber(item.discountAmount),
    categoryId: item.categoryId == null ? "" : String(item.categoryId),
    markedForDeletion: false
  };
}

function emptyDraftItem(): ReceiptItemDraft {
  return {
    clientId: crypto.randomUUID(),
    id: null,
    description: "",
    quantity: "",
    unit: "",
    unitPrice: "",
    totalPrice: "",
    discountAmount: "",
    categoryId: "",
    markedForDeletion: false
  };
}

function toUpdateRequest(draft: ReceiptDraft, original: ReceiptDTO): ReceiptUpdateRequest {
  const activeItems = draft.items.filter((item) => !item.markedForDeletion);
  return {
    receiptDate: emptyToNull(draft.receiptDate),
    receiptTime: emptyToNull(draft.receiptTime),
    storeName: emptyToNull(draft.storeName),
    storeBranch: emptyToNull(draft.storeBranch),
    totalAmount: optionalDecimal(draft.totalAmount),
    currency: emptyToNull(draft.currency),
    bonusBalance: optionalDecimal(draft.bonusBalance),
    bonusPoints: optionalDecimal(draft.bonusPoints),
    bonusType: emptyToNull(draft.bonusType),
    items: activeItems.map((item, index) => toItemUpdateRequest(item, original, index))
  };
}

function toItemUpdateRequest(item: ReceiptItemDraft, original: ReceiptDTO, index: number): ReceiptItemUpdateRequest {
  const originalItem = item.id == null ? null : original.items.find((candidate) => candidate.id === item.id) ?? null;
  const categoryChanged = originalItem == null
    ? item.categoryId !== ""
    : item.categoryId !== (originalItem.categoryId == null ? "" : String(originalItem.categoryId));
  const categoryId = item.categoryId === "" ? null : Number(item.categoryId);
  const totalPrice = optionalDecimal(item.totalPrice);

  if (!item.description.trim() || totalPrice == null) {
    throw new Error("Jede Position benötigt Beschreibung und Gesamtpreis.");
  }

  return stripUndefined({
    id: item.id ?? undefined,
    positionIndex: index,
    description: item.description.trim(),
    quantity: optionalDecimal(item.quantity),
    unit: emptyToNull(item.unit),
    unitPrice: optionalDecimal(item.unitPrice),
    totalPrice,
    discountAmount: optionalDecimal(item.discountAmount),
    categoryId: categoryChanged ? categoryId : undefined,
    categorySource: categoryChanged ? categorySourceFor(categoryId) : undefined
  });
}

function stripUndefined<T extends Record<string, unknown>>(value: T): T {
  return Object.fromEntries(Object.entries(value).filter(([, entryValue]) => entryValue !== undefined)) as T;
}

function categorySourceFor(categoryId: number | null): CategorySource | null {
  return categoryId == null ? null : "MANUAL";
}

function emptyToNull(value: string): string | null {
  const trimmed = value.trim();
  return trimmed ? trimmed : null;
}

function optionalDecimal(value: string): number | null {
  const trimmed = value.trim().replace(",", ".");
  if (!trimmed) {
    return null;
  }
  const parsed = Number(trimmed);
  return Number.isFinite(parsed) ? parsed : null;
}

function toInputNumber(value: number | null | undefined): string {
  return value == null ? "" : String(value);
}

function formatBonus(receipt: ReceiptDTO): string {
  const parts = [
    receipt.bonusType,
    receipt.bonusPoints == null ? null : `${formatNumber(receipt.bonusPoints)} Punkte`,
    receipt.bonusBalance == null ? null : formatCurrency(receipt.bonusBalance)
  ].filter(Boolean);
  return parts.length ? parts.join(" · ") : "-";
}

function rejectionReasonLabel(reason: AiCategorizationRejectionReason | null): string {
  if (reason === "LOW_CONFIDENCE") {
    return "Nicht automatisch übernommen wegen niedriger Konfidenz.";
  }

  if (reason === "UNKNOWN_CATEGORY") {
    return "Nicht übernommen, weil die Kategorie unbekannt ist.";
  }

  if (reason === "INVALID_RESPONSE") {
    return "Nicht übernommen, weil die KI-Antwort nicht gültig war.";
  }

  return "Nicht automatisch übernommen.";
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

const inputClassName = cn(
  "h-10 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm text-zinc-950 shadow-sm",
  "focus:border-zinc-400 focus:outline-none focus:ring-2 focus:ring-zinc-200",
  "dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-50 dark:focus:border-zinc-700 dark:focus:ring-zinc-800"
);
