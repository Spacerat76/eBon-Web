import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import {
  AlertTriangle,
  ArrowDown,
  ArrowUp,
  ArrowUpDown,
  Check,
  ChevronLeft,
  ChevronRight,
  ExternalLink,
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
import { DataTableFrame } from "@/components/data/data-table";
import { PageHeader } from "@/components/layout/page-header";
import { PageTabs } from "@/components/layout/page-tabs";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { CategoryIcon } from "@/lib/category-icons";
import { formatCurrency, formatDate, formatDateTime, formatDateTimeParts, formatNumber, formatPercent, formatTime } from "@/lib/format";
import { cn } from "@/lib/utils";
import type {
  CategoryDTO,
  AiParsingLogDTO,
  AiCategorizationRejectionReason,
  CategorySource,
  PageResponse,
  PaperlessRawTextStatus,
  ParseStatus,
  ParseRuleSuggestionDTO,
  ParseRuleSuggestionUpdateRequest,
  ProductAssignmentSource,
  ProductAssignmentStatus,
  ReparseScope,
  ReceiptDTO,
  ReceiptItemDTO,
  ReceiptItemUpdateRequest,
  ReceiptListParams,
  ReceiptUpdateRequest
} from "@/lib/types";

type SortKey = NonNullable<ReceiptListParams["sortBy"]>;

export type ReceiptDetailTab = "items" | "data" | "raw" | "ai" | "suggestions";

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

interface ReceiptListState {
  filters: ReceiptFilters;
  page: number;
  sortBy: SortKey;
  sortDir: "asc" | "desc";
  scrollY: number | null;
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
const RECEIPT_LIST_STATE_KEY = "ebon.receiptListState";
const defaultFilters: ReceiptFilters = {
  status: "",
  store: "",
  dateFrom: "",
  dateTo: "",
  includeDeleted: false
};
const statusOptions: Array<{ value: ParseStatus | ""; label: string }> = [
  { value: "", label: "Alle Status" },
  { value: "PARSED", label: "Geparst" },
  { value: "PARSE_ERROR", label: "Parse-Fehler" },
  { value: "MANUALLY_EDITED", label: "Bearbeitet" },
  { value: "PENDING", label: "Ausstehend" }
];

export function ReceiptsPage({ apiClient, hasApiToken, selectedReceiptId }: ReceiptsPageProps) {
  const [initialListState] = useState(readReceiptListState);
  const [filters, setFilters] = useState<ReceiptFilters>(initialListState.filters);
  const [page, setPage] = useState(initialListState.page);
  const [sortBy, setSortBy] = useState<SortKey>(initialListState.sortBy);
  const [sortDir, setSortDir] = useState<"asc" | "desc">(initialListState.sortDir);
  const pendingScrollY = useRef(initialListState.scrollY);
  const [receipts, setReceipts] = useState<PageResponse<ReceiptDTO> | null>(null);
  const [selectedReceipt, setSelectedReceipt] = useState<ReceiptDTO | null>(null);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [aiParsingLogs, setAiParsingLogs] = useState<AiParsingLogDTO[]>([]);
  const [parseRuleSuggestions, setParseRuleSuggestions] = useState<ParseRuleSuggestionDTO[]>([]);
  const [draft, setDraft] = useState<ReceiptDraft | null>(null);
  const [listLoading, setListLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [syncing, setSyncing] = useState(false);
  const [reparsing, setReparsing] = useState(false);
  const [deleting, setDeleting] = useState(false);
  const [applyingSuggestionId, setApplyingSuggestionId] = useState<number | null>(null);
  const [processingRuleSuggestionId, setProcessingRuleSuggestionId] = useState<number | null>(null);
  const [editMode, setEditMode] = useState(false);
  const [overwriteManualEdits, setOverwriteManualEdits] = useState(false);
  const [paperlessRawTextStatus, setPaperlessRawTextStatus] = useState<PaperlessRawTextStatus | null>(null);
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
      const [response, logs, suggestions] = await Promise.all([
        apiClient.receipt(selectedReceiptId),
        apiClient.aiParsingLog(selectedReceiptId),
        apiClient.parseRuleSuggestions({ size: 50 })
      ]);
      setSelectedReceipt(response);
      setAiParsingLogs(logs);
      setParseRuleSuggestions(suggestions.content.filter((suggestion) => suggestion.receiptId === selectedReceiptId));
      setDraft(toDraft(response));
      setOverwriteManualEdits(false);
    } catch (loadError) {
      setSelectedReceipt(null);
      setAiParsingLogs([]);
      setParseRuleSuggestions([]);
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

  useEffect(() => {
    if (selectedReceiptId === null && !listLoading && receipts && pendingScrollY.current !== null) {
      window.scrollTo({ top: pendingScrollY.current });
      pendingScrollY.current = null;
    }
  }, [listLoading, receipts, selectedReceiptId]);

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

  function openReceipt(id: number) {
    sessionStorage.setItem(RECEIPT_LIST_STATE_KEY, JSON.stringify({
      filters,
      page,
      sortBy,
      sortDir,
      scrollY: window.scrollY
    }));
    window.location.hash = `#/receipts/${id}`;
  }

  function returnToReceiptList() {
    window.location.hash = "#/receipts";
  }

  function cancelReceiptEdit() {
    setDraft(selectedReceipt ? toDraft(selectedReceipt) : null);
    setEditMode(false);
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

  async function updateParserSuggestion(id: number, request: ParseRuleSuggestionUpdateRequest) {
    setProcessingRuleSuggestionId(id);
    setError(null);
    setNotice(null);
    try {
      await apiClient.updateParseRuleSuggestion(id, request);
      setNotice("Parser-Regelvorschlag gespeichert.");
      await loadReceipt();
    } catch (updateError) {
      setError(toUserMessage(updateError));
    } finally {
      setProcessingRuleSuggestionId(null);
    }
  }

  async function acceptParserSuggestion(suggestion: ParseRuleSuggestionDTO, reparseScope: ReparseScope) {
    setProcessingRuleSuggestionId(suggestion.id);
    setError(null);
    setNotice(null);
    try {
      await apiClient.acceptParseRuleSuggestion(suggestion.id, {
        suggestion: toParseRuleSuggestionUpdateRequest(suggestion),
        reparseScope
      });
      setNotice("Parser-Regelvorschlag übernommen.");
      await loadReceipt();
    } catch (acceptError) {
      setError(toUserMessage(acceptError));
    } finally {
      setProcessingRuleSuggestionId(null);
    }
  }

  async function rejectParserSuggestion(suggestion: ParseRuleSuggestionDTO) {
    const reason = window.prompt("Ablehnungsgrund", "Nicht passend");
    if (!reason) {
      return;
    }
    setProcessingRuleSuggestionId(suggestion.id);
    setError(null);
    setNotice(null);
    try {
      await apiClient.rejectParseRuleSuggestion(suggestion.id, reason);
      setNotice("Parser-Regelvorschlag abgelehnt.");
      await loadReceipt();
    } catch (rejectError) {
      setError(toUserMessage(rejectError));
    } finally {
      setProcessingRuleSuggestionId(null);
    }
  }

  async function startReparseSelectedReceipt() {
    if (!selectedReceipt) {
      return;
    }

    setReparsing(true);
    setError(null);
    setNotice(null);

    try {
      const status = await apiClient.paperlessRawTextStatus(selectedReceipt.id);
      if (status.status === "CHANGED" || status.status === "UNAVAILABLE") {
        setPaperlessRawTextStatus(status.status);
        return;
      }
      await reparseSelectedReceipt("STORED");
    } catch (reparseError) {
      setError(toUserMessage(reparseError));
    } finally {
      setReparsing(false);
    }
  }

  async function reparseSelectedReceipt(rawTextSource: "STORED" | "PAPERLESS") {
    if (!selectedReceipt) {
      return;
    }

    setReparsing(true);
    setError(null);
    setNotice(null);

    try {
      const updated = await apiClient.reparseReceipt(
        selectedReceipt.id,
        overwriteManualEdits,
        true,
        null,
        false,
        rawTextSource
      );
      setSelectedReceipt(updated);
      setDraft(toDraft(updated));
      setEditMode(false);
      setNotice(rawTextSource === "PAPERLESS"
        ? "Neuer Paperless-Rohtext wurde übernommen und der Bon erneut geparst."
        : "Bon wurde erneut geparst.");
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

      {selectedReceiptId === null ? (
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
          onAcceptParserSuggestion={acceptParserSuggestion}
          onBack={returnToReceiptList}
          onCancelEdit={cancelReceiptEdit}
          onDeleteReceipt={deleteSelectedReceipt}
          onDraftChange={setDraft}
          onEdit={() => setEditMode(true)}
          onReparse={startReparseSelectedReceipt}
          onRejectParserSuggestion={rejectParserSuggestion}
          onSave={saveDraft}
          onSetOverwriteManualEdits={setOverwriteManualEdits}
          onUpdateParserSuggestion={updateParserSuggestion}
          overwriteManualEdits={overwriteManualEdits}
          receipt={selectedReceipt}
          aiParsingLogs={aiParsingLogs}
          parseRuleSuggestions={parseRuleSuggestions}
          processingRuleSuggestionId={processingRuleSuggestionId}
          reparsing={reparsing}
          saving={saving}
        />
      )}
      <PaperlessRawTextDecisionDialog
        onCancel={() => setPaperlessRawTextStatus(null)}
        onUsePaperless={() => {
          setPaperlessRawTextStatus(null);
          void reparseSelectedReceipt("PAPERLESS");
        }}
        onUseStored={() => {
          setPaperlessRawTextStatus(null);
          void reparseSelectedReceipt("STORED");
        }}
        status={paperlessRawTextStatus}
      />
    </div>
  );
}

function PaperlessRawTextDecisionDialog({
  onCancel,
  onUsePaperless,
  onUseStored,
  status
}: {
  onCancel: () => void;
  onUsePaperless: () => void;
  onUseStored: () => void;
  status: PaperlessRawTextStatus | null;
}) {
  if (status === null) {
    return null;
  }

  const isUnavailable = status === "UNAVAILABLE";
  return (
    <div aria-labelledby="paperless-raw-text-dialog-title" aria-modal="true" className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/45 p-4" role="dialog">
      <Card className="w-full max-w-lg">
        <CardHeader>
          <CardTitle id="paperless-raw-text-dialog-title">Paperless-Rohtext prüfen</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <p className="text-sm text-zinc-700 dark:text-zinc-300">
            {isUnavailable
              ? "Paperless ist momentan nicht erreichbar. Der Bon kann nur mit dem gespeicherten Rohtext erneut geparst werden."
              : "Paperless-Rohtext wurde seit dem Import geändert. Welcher Text soll für den erneuten Parse verwendet werden?"}
          </p>
          <div className="flex flex-wrap justify-end gap-2">
            <Button onClick={onCancel} variant="ghost">Abbrechen</Button>
            <Button onClick={onUseStored} variant="secondary">Gespeicherten Rohtext verwenden</Button>
            {!isUnavailable ? <Button onClick={onUsePaperless}>Neuen Rohtext übernehmen und parsen</Button> : null}
          </div>
        </CardContent>
      </Card>
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
    <div className="space-y-4">
      <PageHeader
        actions={(
          <Button disabled={syncing} onClick={onSync} size="sm">
            {syncing ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
            Sync starten
          </Button>
        )}
        context="Bons / Liste"
        description="Importierte Bons filtern, sortieren und öffnen."
        title="Bon-Liste"
      />
      <Card>
        <CardContent className="space-y-3 p-4">
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
        </CardContent>
      </Card>
      <DataTableFrame>
        {listLoading ? (
          <div className="space-y-2 p-4">
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
            <Skeleton className="h-12 w-full" />
          </div>
        ) : receipts?.content.length ? (
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
                      <a
                        aria-label={`Bon ${receipt.storeName ?? "Unbekannt"} vom ${formatDate(receipt.receiptDate)} öffnen`}
                        className="font-medium text-zinc-950 hover:underline dark:text-zinc-50"
                        href={`#/receipts/${receipt.id}`}
                        onClick={(event) => {
                          event.preventDefault();
                          event.stopPropagation();
                          onReceiptSelect(receipt.id);
                        }}
                      >
                        {receipt.storeName ?? "Unbekannt"}
                      </a>
                      <div className="text-xs text-zinc-500 dark:text-zinc-400">{receipt.storeBranch ?? "-"}</div>
                      <PaperlessLink className="mt-1" receipt={receipt} />
                    </td>
                    <td className="px-3 py-3 text-right font-medium">{formatCurrency(receipt.totalAmount)}</td>
                    <td className="px-3 py-3">{formatNumber(receipt.items.length)}</td>
                    <td className="px-3 py-3">
                      <div className="flex flex-wrap gap-1">
                        <ParseStatusBadge status={receipt.parseStatus} />
                        {receipt.deleteReason ? <DeleteReasonBadge reason={receipt.deleteReason} /> : null}
                      </div>
                    </td>
                    <td className="px-3 py-3">
                      <ImportedAtCell value={receipt.importedAt} />
                    </td>
                  </tr>
                ))}
              </tbody>
          </table>
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
      </DataTableFrame>
    </div>
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
  onAcceptParserSuggestion,
  onApplyAiSuggestion,
  onBack,
  onCancelEdit,
  onDeleteReceipt,
  onDraftChange,
  onEdit,
  onReparse,
  onRejectParserSuggestion,
  onSave,
  onSetOverwriteManualEdits,
  onUpdateParserSuggestion,
  overwriteManualEdits,
  receipt,
  aiParsingLogs,
  parseRuleSuggestions,
  processingRuleSuggestionId,
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
  onAcceptParserSuggestion: (suggestion: ParseRuleSuggestionDTO, scope: ReparseScope) => void;
  onApplyAiSuggestion: (item: ReceiptItemDTO) => void;
  onBack: () => void;
  onCancelEdit: () => void;
  onDeleteReceipt: () => void;
  onDraftChange: (draft: ReceiptDraft) => void;
  onEdit: () => void;
  onReparse: () => void;
  onRejectParserSuggestion: (suggestion: ParseRuleSuggestionDTO) => void;
  onSave: () => void;
  onSetOverwriteManualEdits: (overwrite: boolean) => void;
  onUpdateParserSuggestion: (id: number, request: ParseRuleSuggestionUpdateRequest) => void;
  overwriteManualEdits: boolean;
  receipt: ReceiptDTO | null;
  aiParsingLogs: AiParsingLogDTO[];
  parseRuleSuggestions: ParseRuleSuggestionDTO[];
  processingRuleSuggestionId: number | null;
  reparsing: boolean;
  saving: boolean;
}) {
  const [activeTab, setActiveTab] = useState<ReceiptDetailTab>("items");

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
  const detailTabs: Array<{ id: ReceiptDetailTab; label: string; count?: number }> = [
    { id: "items", label: "Positionen", count: receipt.items.length },
    { id: "data", label: "Bon-Daten" },
    { id: "raw", label: "Rohtext" },
    { id: "ai", label: "KI-Protokoll", count: aiParsingLogs.length },
    { id: "suggestions", label: "Regelvorschläge", count: parseRuleSuggestions.length }
  ];

  return (
    <div className="space-y-4">
      {editMode ? (
        <div className="sticky top-[4.75rem] z-20 flex flex-col gap-3 rounded-md border border-zinc-200 bg-white/95 px-4 py-3 shadow-sm backdrop-blur dark:border-zinc-800 dark:bg-zinc-950/95 md:flex-row md:items-center md:justify-between">
          <div>
            <div className="text-sm font-medium text-zinc-950 dark:text-zinc-50">Bearbeitungsmodus</div>
            <div className="text-xs text-zinc-500 dark:text-zinc-400">Änderungen werden erst beim Speichern übernommen.</div>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button disabled={saving} onClick={onSave} size="sm">
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
              Speichern
            </Button>
            <Button disabled={saving} onClick={onCancelEdit} size="sm" variant="secondary">
              <X className="h-4 w-4" />
              Abbrechen
            </Button>
          </div>
        </div>
      ) : null}

      <div className="space-y-3">
        <button className="text-sm text-zinc-500 hover:text-zinc-950 dark:text-zinc-400 dark:hover:text-zinc-50" onClick={onBack}>
          Zur Bon-Liste
        </button>
        <PageHeader
          actions={(
            <>
              {editMode ? (
                <Badge tone="blue">Bearbeitung aktiv</Badge>
              ) : (
                <>
                  <Button onClick={() => { setActiveTab("data"); onEdit(); }} size="sm" variant="secondary">
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
            </>
          )}
          context="Bons / Detail"
          description={receipt.storeBranch ?? "Keine Filiale"}
          title={receipt.storeName ?? "Unbekannter Bon"}
        />
        <div className="flex flex-wrap items-center gap-2">
          <ParseStatusBadge status={receipt.parseStatus} />
          {receipt.parseSource === "AI" ? <Badge tone="blue">per KI geparst</Badge> : null}
          {receipt.parseSource === "RULE" ? <Badge>Regelparser</Badge> : null}
          {receipt.deleteReason ? <DeleteReasonBadge reason={receipt.deleteReason} /> : null}
          <PaperlessLink receipt={receipt} />
        </div>
        <div aria-label="Bon-Zusammenfassung" className="grid gap-3 rounded-md border border-zinc-200 bg-white p-3 text-sm sm:grid-cols-3 dark:border-zinc-800 dark:bg-zinc-950">
          <Metric label="Datum / Uhrzeit" value={`${formatDate(receipt.receiptDate)} · ${formatTime(receipt.receiptTime)}`} />
          <Metric label="Gesamtbetrag" value={formatCurrency(receipt.totalAmount)} />
          <Metric label="Bonus" value={formatBonus(receipt)} />
        </div>
        <PageTabs active={activeTab} onChange={setActiveTab} tabs={detailTabs} />
      </div>

      {hasManualItems && !editMode ? (
        <Card>
          <CardContent className="p-3">
            <label className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 px-3 py-2 text-sm text-amber-800 dark:border-amber-900 dark:bg-amber-950 dark:text-amber-200">
              <input
                checked={overwriteManualEdits}
                className="mt-0.5 h-4 w-4"
                onChange={(event) => onSetOverwriteManualEdits(event.target.checked)}
                type="checkbox"
              />
              Manuell editierte Positionen beim Re-Parse überschreiben.
            </label>
          </CardContent>
        </Card>
      ) : null}

      {activeTab === "data" ? (
        <Card>
          <CardHeader><CardTitle>Bon-Daten</CardTitle></CardHeader>
          <CardContent className="space-y-4">
          {editMode ? (
            <>
              <div className="grid gap-2 md:grid-cols-2">
                <Input aria-label="Geschäft" onChange={(event) => onDraftChange({ ...draft, storeName: event.target.value })} placeholder="Geschäft" value={draft.storeName} />
                <Input aria-label="Filiale" onChange={(event) => onDraftChange({ ...draft, storeBranch: event.target.value })} placeholder="Filiale" value={draft.storeBranch} />
              </div>
              <ReceiptMetadataEditor draft={draft} onDraftChange={onDraftChange} />
            </>
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
      ) : null}

      {activeTab === "items" ? <Card>
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
      </Card> : null}

      {activeTab === "raw" ? <Card>
        <CardHeader>
          <CardTitle>Rohtext</CardTitle>
        </CardHeader>
        <CardContent>
          <pre className="max-h-96 overflow-auto rounded-md bg-zinc-950 p-3 text-xs leading-relaxed text-zinc-50">
            {receipt.rawText || "Kein Rohtext vorhanden."}
          </pre>
        </CardContent>
      </Card> : null}

      {activeTab === "ai" ? <AiParsingLogPanel logs={aiParsingLogs} receipt={receipt} /> : null}
      {activeTab === "suggestions" ? (
        <ParseRuleSuggestionsPanel
          onAccept={onAcceptParserSuggestion}
          onReject={onRejectParserSuggestion}
          onUpdate={onUpdateParserSuggestion}
          processingId={processingRuleSuggestionId}
          suggestions={parseRuleSuggestions}
        />
      ) : null}
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
          {receipt.parseSource === "AI" ? <Badge tone="blue">per KI geparst</Badge> : null}
          {receipt.parseSource === "RULE" ? <Badge>Regelparser</Badge> : null}
          {receipt.deleteReason ? <DeleteReasonBadge reason={receipt.deleteReason} /> : null}
        </div>
      </div>
      <Metric label="Bonus" value={formatBonus(receipt)} />
      <div>
        <p className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Paperless-ID</p>
        <div className="mt-1">
          <PaperlessLink receipt={receipt} />
        </div>
      </div>
    </div>
  );
}

function AiParsingLogPanel({ logs, receipt }: { logs: AiParsingLogDTO[]; receipt: ReceiptDTO }) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>KI-Protokoll</CardTitle>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex flex-wrap gap-2 text-sm">
          {receipt.parseSource === "AI" ? <Badge tone="blue">per KI geparst</Badge> : null}
          {receipt.aiParsingSummary ? (
            <>
              <Badge tone={receipt.aiParsingSummary.lastStatus === "SUCCESS" ? "green" : "yellow"}>{aiParsingStatusLabel(receipt.aiParsingSummary.lastStatus)}</Badge>
              {receipt.aiParsingSummary.overallConfidence == null ? null : <Badge>{formatPercent(receipt.aiParsingSummary.overallConfidence * 100)}</Badge>}
              {receipt.aiParsingSummary.hasOpenRuleSuggestions ? <Badge tone="yellow">Offene Regelvorschläge</Badge> : null}
            </>
          ) : null}
        </div>

        {logs.length ? (
            <div className="space-y-2">
              {logs.map((log) => (
                <div className="rounded-md border border-zinc-200 p-3 text-sm dark:border-zinc-800" key={log.id}>
                  <div className="flex flex-wrap gap-2">
                    <Badge tone={log.status === "SUCCESS" ? "green" : "yellow"}>{aiParsingStatusLabel(log.status)}</Badge>
                    <Badge>{log.trigger}</Badge>
                    {log.modelUsed ? <Badge>{log.modelUsed}</Badge> : null}
                  </div>
                  <div className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">
                    {formatDateTime(log.startedAt)} · {log.durationMs == null ? "Dauer unbekannt" : `${log.durationMs} ms`}
                  </div>
                  {log.failureReason ? <div className="mt-2 text-sm text-amber-700 dark:text-amber-300">{log.failureReason}</div> : null}
                  {log.warnings.length ? <div className="mt-2 text-xs text-zinc-500 dark:text-zinc-400">Warnungen: {log.warnings.join("; ")}</div> : null}
                </div>
              ))}
            </div>
        ) : <EmptyState text="Kein KI-Protokoll vorhanden" />}
      </CardContent>
    </Card>
  );
}

function ParseRuleSuggestionsPanel({
  onAccept,
  onReject,
  onUpdate,
  processingId,
  suggestions
}: {
  onAccept: (suggestion: ParseRuleSuggestionDTO, scope: ReparseScope) => void;
  onReject: (suggestion: ParseRuleSuggestionDTO) => void;
  onUpdate: (id: number, request: ParseRuleSuggestionUpdateRequest) => void;
  processingId: number | null;
  suggestions: ParseRuleSuggestionDTO[];
}) {
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editDraft, setEditDraft] = useState<ParseRuleSuggestionUpdateRequest | null>(null);
  const [scopeById, setScopeById] = useState<Record<number, ReparseScope>>({});

  function startEditing(suggestion: ParseRuleSuggestionDTO) {
    setEditingId(suggestion.id);
    setEditDraft(toParseRuleSuggestionUpdateRequest(suggestion));
  }

  return (
    <Card>
      <CardHeader><CardTitle>Regelvorschläge</CardTitle></CardHeader>
      <CardContent>
        {suggestions.length ? <div className="space-y-2">
          {suggestions.map((suggestion) => {
            const scope = scopeById[suggestion.id] ?? "NONE";
            const contextReceiptId = suggestion.receiptContext?.receiptId ?? suggestion.receiptId;
            const contextStore = suggestion.receiptContext?.storeName ?? suggestion.storeName;
            const editing = editingId === suggestion.id && editDraft !== null;
            return <div className="rounded-md border border-zinc-200 p-3 text-sm dark:border-zinc-800" key={suggestion.id}>
              <div className="flex flex-wrap gap-2">
                <Badge tone={suggestion.status === "OPEN" ? "blue" : suggestion.status === "ACCEPTED" ? "green" : "red"}>{suggestion.status}</Badge>
                <Badge tone={suggestion.validationStatus === "VALID" ? "green" : "yellow"}>{suggestion.validationStatus}</Badge>
                <span className="font-medium">{suggestion.ruleType}</span>
              </div>
              <div className="mt-3 grid gap-2 text-xs text-zinc-600 md:grid-cols-2 dark:text-zinc-300">
                <div>Auslöser: {parseSuggestionTriggerLabel(suggestion.trigger)}</div>
                <div>{contextReceiptId == null ? "Bon-Kontext nicht verfügbar" : `Bon #${contextReceiptId}`} · {contextStore ?? "Store nicht angegeben"}</div>
                <div className="break-all">Regex: {suggestion.matchRegex || "Nicht angegeben"}</div>
                <div>Extract-Gruppe: {suggestion.extractGroup ?? "Nicht angegeben"}</div>
              </div>
              <div className="mt-3 text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Parser-Problem</div>
              <div className="mt-1 text-zinc-700 dark:text-zinc-200">{suggestion.problemDescription || "Keine Problembeschreibung vorhanden."}</div>
              <div className="mt-3 text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Lösungsbegründung</div>
              <div className="mt-1 text-zinc-700 dark:text-zinc-200">{suggestion.solutionRationale || "Keine Lösungsbegründung vorhanden."}</div>
              {suggestion.validationMessage ? <div className="mt-2 text-xs text-amber-700 dark:text-amber-300">{suggestion.validationMessage}</div> : null}

              {editing && editDraft ? (
                <div className="mt-3 grid gap-3 rounded-md border border-zinc-200 p-3 md:grid-cols-2 dark:border-zinc-800">
                  <Field label="Store">
                    <Input onChange={(event) => setEditDraft({ ...editDraft, storeName: emptyToNull(event.target.value) })} value={editDraft.storeName ?? ""} />
                  </Field>
                  <Field label="Regeltyp">
                    <select className={inputClassName} onChange={(event) => setEditDraft({ ...editDraft, ruleType: event.target.value as ParseRuleSuggestionDTO["ruleType"] })} value={editDraft.ruleType}>
                      {(["DATE_PATTERN", "STORE_PATTERN", "ITEM_PATTERN", "TOTAL_PATTERN", "BONUS_PATTERN"] as const).map((type) => <option key={type} value={type}>{type}</option>)}
                    </select>
                  </Field>
                  <Field label="Regex">
                    <Input onChange={(event) => setEditDraft({ ...editDraft, matchRegex: event.target.value })} value={editDraft.matchRegex} />
                  </Field>
                  <Field label="Extract-Gruppe">
                    <Input onChange={(event) => setEditDraft({ ...editDraft, extractGroup: emptyToNull(event.target.value) })} value={editDraft.extractGroup ?? ""} />
                  </Field>
                  <Field label="Konfidenz">
                    <Input max="1" min="0" onChange={(event) => setEditDraft({ ...editDraft, confidence: optionalDecimal(event.target.value) })} step="0.01" type="number" value={editDraft.confidence ?? ""} />
                  </Field>
                  <div />
                  <Field label="Parser-Problem">
                    <Textarea onChange={(event) => setEditDraft({ ...editDraft, problemDescription: event.target.value })} value={editDraft.problemDescription} />
                  </Field>
                  <Field label="Lösungsbegründung">
                    <Textarea onChange={(event) => setEditDraft({ ...editDraft, solutionRationale: event.target.value })} value={editDraft.solutionRationale} />
                  </Field>
                  <div className="flex gap-2 md:col-span-2">
                    <Button disabled={processingId === suggestion.id} onClick={() => { onUpdate(suggestion.id, editDraft); setEditingId(null); setEditDraft(null); }} size="sm">Änderungen speichern</Button>
                    <Button onClick={() => { setEditingId(null); setEditDraft(null); }} size="sm" variant="secondary">Bearbeitung abbrechen</Button>
                  </div>
                </div>
              ) : null}

              {suggestion.status === "OPEN" ? <div className="mt-3 flex flex-wrap items-center gap-2">
                <Button disabled={processingId === suggestion.id} onClick={() => startEditing(suggestion)} size="sm" variant="secondary">Vorschlag bearbeiten</Button>
                <label className="text-xs font-medium text-zinc-600 dark:text-zinc-300">
                  <span className="sr-only">Reparse-Umfang</span>
                  <select aria-label="Reparse-Umfang" className={inputClassName} onChange={(event) => setScopeById((current) => ({ ...current, [suggestion.id]: event.target.value as ReparseScope }))} value={scope}>
                    <option value="NONE">Kein sofortiger Reparse</option>
                    <option value="CURRENT_RECEIPT">Aktueller Bon</option>
                    <option value="PARSE_ERROR_BY_STORE">Parse-Fehler gleicher Store</option>
                    <option value="ALL_PARSE_ERROR">Alle Parse-Fehler</option>
                  </select>
                </label>
                <Button disabled={processingId === suggestion.id || suggestion.validationStatus !== "VALID"} onClick={() => onAccept(suggestion, scope)} size="sm">Akzeptieren</Button>
                <Button disabled={processingId === suggestion.id} onClick={() => onReject(suggestion)} size="sm" variant="danger">Ablehnen</Button>
              </div> : null}
            </div>
          })}
        </div> : <EmptyState text="Keine Regelvorschläge vorhanden" />}
      </CardContent>
    </Card>
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
            <th className="px-3 py-2 font-medium">Produkt</th>
            <th className="px-3 py-2 text-right font-medium">Einheitspreis</th>
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
              <td className="px-3 py-3">
                <div className="space-y-1">
                  {item.productAssignmentStatus === "NO_PRODUCT" ? (
                    <Badge>Kein Produkt</Badge>
                  ) : item.productFamilyName ? (
                    <div>
                      <div className="font-medium text-zinc-950 dark:text-zinc-50">{item.productFamilyName}</div>
                      {item.productVariantName ? <div className="text-xs text-zinc-500 dark:text-zinc-400">{item.productVariantName}</div> : null}
                      {item.productAssignmentStatus ? <ProductAssignmentBadge status={item.productAssignmentStatus} /> : null}
                    </div>
                  ) : item.productAssignmentStatus === "NEEDS_REVIEW" ? (
                    <a className="inline-flex" href="#/products" title="In der Produkt-Prüfliste korrigieren">
                      <Badge tone="yellow">Prüfung nötig</Badge>
                    </a>
                  ) : (
                    <span className="text-zinc-500 dark:text-zinc-400">-</span>
                  )}
                  {item.productAssignmentSource ? <ProductAssignmentSourceBadge source={item.productAssignmentSource} /> : null}
                </div>
              </td>
              <td className="px-3 py-3 text-right">
                {item.computedUnitPrice == null || item.computedUnitPriceUnit == null
                  ? "-"
                  : `${formatCurrency(item.computedUnitPrice)} / ${item.computedUnitPriceUnit}`}
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
      <CategoryIcon className="mr-1 h-3.5 w-3.5" icon={category?.icon} />
      {item.categoryName ?? category?.name ?? "Kategorie"}
    </span>
  );
}

function ProductAssignmentBadge({ status }: { status: ProductAssignmentStatus }) {
  const label = {
    CONFIRMED: "Bestätigt",
    AUTO_ASSIGNED: "Automatisch",
    NEEDS_REVIEW: "Prüfung nötig",
    REJECTED: "Abgelehnt",
    NO_PRODUCT: "Kein Produkt"
  }[status];
  const tone = status === "NEEDS_REVIEW" || status === "REJECTED" ? "yellow" : "blue";
  return <Badge tone={tone}>{label}</Badge>;
}

function ProductAssignmentSourceBadge({ source }: { source: ProductAssignmentSource }) {
  const label = {
    RULE: "Regel",
    AI: "KI",
    MANUAL: "Manuell",
    HISTORY: "Historie"
  }[source];
  return <Badge>Zuordnungsquelle: {label}</Badge>;
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

function ImportedAtCell({ value }: { value: string | null | undefined }) {
  const parts = formatDateTimeParts(value);
  return (
    <div>
      <div>{parts.date}</div>
      {parts.time ? <div className="text-xs text-zinc-500 dark:text-zinc-400">{parts.time}</div> : null}
    </div>
  );
}

function PaperlessLink({ className, receipt }: { className?: string; receipt: ReceiptDTO }) {
  const label = receipt.paperlessDocumentId == null ? "-" : `#${receipt.paperlessDocumentId}`;

  if (!receipt.paperlessDocumentId || !receipt.paperlessDocumentUrl) {
    return <span className={cn("text-xs text-zinc-500 dark:text-zinc-400", className)}>Paperless {label}</span>;
  }

  return (
    <a
      className={cn("inline-flex items-center gap-1 text-xs font-medium text-sky-700 hover:text-sky-900 dark:text-sky-300 dark:hover:text-sky-200", className)}
      href={receipt.paperlessDocumentUrl}
      onClick={(event) => event.stopPropagation()}
      rel="noreferrer"
      target="_blank"
    >
      Paperless {label}
      <ExternalLink className="h-3 w-3" />
    </a>
  );
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

function aiParsingStatusLabel(status: AiParsingLogDTO["status"]): string {
  return {
    SUCCESS: "KI-Parse übernommen",
    FAILED: "KI-Parse fehlgeschlagen",
    SKIPPED_LIMIT: "Sync-Limit erreicht",
    INVALID_RESPONSE: "KI-Antwort ungültig",
    LOW_CONFIDENCE: "Konfidenz zu niedrig",
    DISABLED: "KI-Parsing deaktiviert",
    NO_API_KEY: "API-Key fehlt"
  }[status];
}

function parseSuggestionTriggerLabel(trigger: ParseRuleSuggestionDTO["trigger"]): string {
  return {
    SYNC_AUTO: "Automatischer Sync",
    MANUAL_REPARSE: "Manueller Reparse",
    MANUAL_REPARSE_FORCE_FULL_TEXT: "Manueller Volltext-Reparse",
    BULK_REPARSE: "Sammel-Reparse",
    SETTINGS_TEST: "Einstellungstest"
  }[trigger];
}

function toParseRuleSuggestionUpdateRequest(suggestion: ParseRuleSuggestionDTO): ParseRuleSuggestionUpdateRequest {
  return {
    storeName: suggestion.storeName,
    ruleType: suggestion.ruleType,
    matchRegex: suggestion.matchRegex,
    extractGroup: suggestion.extractGroup,
    confidence: suggestion.confidence,
    problemDescription: suggestion.problemDescription,
    solutionRationale: suggestion.solutionRationale
  };
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

function readReceiptListState(): ReceiptListState {
  const fallback: ReceiptListState = {
    filters: defaultFilters,
    page: 0,
    sortBy: "receiptDate",
    sortDir: "desc",
    scrollY: null
  };
  const stored = sessionStorage.getItem(RECEIPT_LIST_STATE_KEY);
  if (!stored) {
    return fallback;
  }

  try {
    const value: unknown = JSON.parse(stored);
    if (!isRecord(value)) {
      sessionStorage.removeItem(RECEIPT_LIST_STATE_KEY);
      return fallback;
    }
    const storedFilters = isRecord(value.filters) ? value.filters : {};
    const status = isParseStatusFilter(storedFilters.status) ? storedFilters.status : "";
    const state: ReceiptListState = {
      filters: {
        status,
        store: validString(storedFilters.store, 255),
        dateFrom: validDateFilter(storedFilters.dateFrom),
        dateTo: validDateFilter(storedFilters.dateTo),
        includeDeleted: storedFilters.includeDeleted === true
      },
      page: validNonNegativeInteger(value.page),
      sortBy: isSortKey(value.sortBy) ? value.sortBy : "receiptDate",
      sortDir: value.sortDir === "asc" ? "asc" : "desc",
      scrollY: validScrollY(value.scrollY)
    };
    sessionStorage.setItem(RECEIPT_LIST_STATE_KEY, JSON.stringify(state));
    return state;
  } catch {
    sessionStorage.removeItem(RECEIPT_LIST_STATE_KEY);
    return fallback;
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null && !Array.isArray(value);
}

function isParseStatusFilter(value: unknown): value is ReceiptFilters["status"] {
  return value === "" || value === "PENDING" || value === "PARSED" || value === "PARSE_ERROR" || value === "MANUALLY_EDITED";
}

function isSortKey(value: unknown): value is SortKey {
  return value === "receiptDate" || value === "importedAt" || value === "storeName" || value === "totalAmount" || value === "parseStatus";
}

function validString(value: unknown, maxLength: number): string {
  return typeof value === "string" && value.length <= maxLength ? value : "";
}

function validDateFilter(value: unknown): string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) ? value : "";
}

function validNonNegativeInteger(value: unknown): number {
  return typeof value === "number" && Number.isSafeInteger(value) && value >= 0 ? value : 0;
}

function validScrollY(value: unknown): number | null {
  return typeof value === "number" && Number.isFinite(value) && value >= 0 ? value : null;
}

const inputClassName = cn(
  "h-10 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm text-zinc-950 shadow-sm",
  "focus:border-zinc-400 focus:outline-none focus:ring-2 focus:ring-zinc-200",
  "dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-50 dark:focus:border-zinc-700 dark:focus:ring-zinc-800"
);
