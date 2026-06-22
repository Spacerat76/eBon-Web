import type {
  ApiErrorResponse,
  AiParsingLogDTO,
  BackupRestoreResultDTO,
  BackupValidationReportDTO,
  BonusReportDTO,
  CategorizationRuleApplyResponse,
  CategorizationRuleDTO,
  CategorizationRulePreviewRequest,
  CategorizationRulePreviewResponse,
  CategorizationRuleRequest,
  CategoryDTO,
  CategoryIconDTO,
  CategoryPatchRequest,
  CategoryRequest,
  DashboardDTO,
  DataMaintenanceResultDTO,
  ProductAssignmentRunRequest,
  ProductAssignmentRunResponse,
  ProductDataResetResultDTO,
  ProductFamilyDTO,
  ProductFamilyRequest,
  ProductRuleDTO,
  ProductRulePreviewRequest,
  ProductRulePreviewResponse,
  ProductRuleRequest,
  ProductVariantDTO,
  ProductVariantRequest,
  MessageResponse,
  MigrationDraftDTO,
  PageResponse,
  PaperlessRawTextStatusDTO,
  ParseRuleSuggestionAcceptRequest,
  ParseRuleSuggestionDTO,
  ParseRuleSuggestionStatus,
  ParseRuleSuggestionUpdateRequest,
  ReceiptDTO,
  ReceiptItemCreateRequest,
  ReceiptItemDTO,
  ReceiptItemUpdateRequest,
  ReceiptListParams,
  ReceiptUpdateRequest,
  RawTextSource,
  ReportByCategoryDTO,
  ReportByPeriodDTO,
  ReportByStoreDTO,
  ReportFilters,
  SearchParams,
  SearchResultDTO,
  SettingsConnectionTestResponse,
  SettingsDTO,
  SystemInfoDTO,
  SyncLogDTO,
  SyncStatusDTO,
  TopItemReportDTO
} from "@/lib/types";
import { isMockApiEnabled, mockDownload, mockRequest, mockUploadFile } from "@/lib/mock-api";

export interface DownloadedFile {
  blob: Blob;
  filename: string;
}

export class ApiClientError extends Error {
  readonly status: number;
  readonly details: ApiErrorResponse | null;

  constructor(message: string, status: number, details: ApiErrorResponse | null) {
    super(message);
    this.name = "ApiClientError";
    this.status = status;
    this.details = details;
  }
}

export type TokenProvider = () => string | null;

export class ApiClient {
  private readonly tokenProvider: TokenProvider;

  constructor(tokenProvider: TokenProvider) {
    this.tokenProvider = tokenProvider;
  }

  dashboard(): Promise<DashboardDTO> {
    return this.request("/dashboard");
  }

  systemInfo(): Promise<SystemInfoDTO> {
    return this.request("/system/info");
  }

  syncStatus(): Promise<SyncStatusDTO> {
    return this.request("/sync/status");
  }

  triggerSync(): Promise<MessageResponse> {
    return this.request("/sync/trigger", { method: "POST" });
  }

  syncLog(page = 0, size = 5): Promise<PageResponse<SyncLogDTO>> {
    return this.request(`/sync/log?page=${page}&size=${size}`);
  }

  receipts(params: ReceiptListParams = {}): Promise<PageResponse<ReceiptDTO>> {
    const query = toQuery({
      page: params.page ?? 0,
      size: params.size ?? 20,
      sortBy: params.sortBy ?? "receiptDate",
      sortDir: params.sortDir ?? "desc",
      status: params.status || undefined,
      dateFrom: params.dateFrom || undefined,
      dateTo: params.dateTo || undefined,
      store: params.store || undefined,
      includeDeleted: params.includeDeleted ? "true" : undefined,
      uncategorizedOnly: params.uncategorizedOnly ? "true" : undefined
    });
    return this.request(`/receipts?${query}`);
  }

  receipt(id: number): Promise<ReceiptDTO> {
    return this.request(`/receipts/${id}`);
  }

  paperlessRawTextStatus(id: number): Promise<PaperlessRawTextStatusDTO> {
    return this.request(`/receipts/${id}/paperless-raw-text-status`);
  }

  updateReceipt(id: number, request: ReceiptUpdateRequest): Promise<ReceiptDTO> {
    return this.request(`/receipts/${id}`, {
      method: "PUT",
      body: JSON.stringify(request)
    });
  }

  reparseReceipt(
    id: number,
    overwriteManualEdits: boolean,
    useAiFallback = true,
    aiTextMode?: "MINIMIZED" | "FULL_TEXT" | null,
    confirmFullText = false,
    rawTextSource: RawTextSource = "STORED"
  ): Promise<ReceiptDTO> {
    return this.request(`/receipts/${id}/reparse?${toQuery({
      overwriteManualEdits: overwriteManualEdits ? "true" : "false",
      useAiFallback: useAiFallback ? "true" : "false",
      aiTextMode: aiTextMode || undefined,
      confirmFullText: confirmFullText ? "true" : "false",
      rawTextSource
    })}`, {
      method: "POST"
    });
  }

  aiParsingLog(receiptId: number): Promise<AiParsingLogDTO[]> {
    return this.request(`/receipts/${receiptId}/ai-parsing-log`);
  }

  deleteReceipt(id: number): Promise<void> {
    return this.request(`/receipts/${id}`, { method: "DELETE" });
  }

  updateReceiptItem(id: number, request: ReceiptItemUpdateRequest): Promise<ReceiptItemDTO> {
    return this.request(`/receipt-items/${id}`, {
      method: "PATCH",
      body: JSON.stringify(request)
    });
  }

  addReceiptItem(receiptId: number, request: ReceiptItemCreateRequest): Promise<ReceiptItemDTO> {
    return this.request(`/receipts/${receiptId}/items`, {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  deleteReceiptItem(id: number): Promise<void> {
    return this.request(`/receipt-items/${id}`, { method: "DELETE" });
  }

  categories(includeInactive = false): Promise<CategoryDTO[]> {
    return this.request(`/categories?includeInactive=${includeInactive ? "true" : "false"}`);
  }

  categoryIcons(): Promise<CategoryIconDTO[]> {
    return this.request("/categories/icons");
  }

  createCategory(request: CategoryRequest): Promise<CategoryDTO> {
    return this.request("/categories", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  updateCategory(id: number, request: CategoryRequest): Promise<CategoryDTO> {
    return this.request(`/categories/${id}`, {
      method: "PUT",
      body: JSON.stringify(request)
    });
  }

  patchCategory(id: number, request: CategoryPatchRequest): Promise<CategoryDTO> {
    return this.request(`/categories/${id}`, {
      method: "PATCH",
      body: JSON.stringify(request)
    });
  }

  deleteCategory(id: number): Promise<MessageResponse> {
    return this.request(`/categories/${id}`, { method: "DELETE" });
  }

  productFamilies(): Promise<ProductFamilyDTO[]> {
    return this.request("/products/families");
  }

  createProductFamily(request: ProductFamilyRequest): Promise<ProductFamilyDTO> {
    return this.request("/products/families", { method: "POST", body: JSON.stringify(request) });
  }

  updateProductFamily(id: number, request: ProductFamilyRequest): Promise<ProductFamilyDTO> {
    return this.request(`/products/families/${id}`, { method: "PUT", body: JSON.stringify(request) });
  }

  productVariants(productFamilyId?: number): Promise<ProductVariantDTO[]> {
    const query = productFamilyId === undefined ? "" : `?productFamilyId=${productFamilyId}`;
    return this.request(`/products/variants${query}`);
  }

  createProductVariant(request: ProductVariantRequest): Promise<ProductVariantDTO> {
    return this.request("/products/variants", { method: "POST", body: JSON.stringify(request) });
  }

  updateProductVariant(id: number, request: ProductVariantRequest): Promise<ProductVariantDTO> {
    return this.request(`/products/variants/${id}`, { method: "PUT", body: JSON.stringify(request) });
  }

  productRules(): Promise<ProductRuleDTO[]> {
    return this.request("/products/rules");
  }

  createProductRule(request: ProductRuleRequest): Promise<ProductRuleDTO> {
    return this.request("/products/rules", { method: "POST", body: JSON.stringify(request) });
  }

  updateProductRule(id: number, request: ProductRuleRequest): Promise<ProductRuleDTO> {
    return this.request(`/products/rules/${id}`, { method: "PUT", body: JSON.stringify(request) });
  }

  deleteProductRule(id: number): Promise<void> {
    return this.request(`/products/rules/${id}`, { method: "DELETE" });
  }

  previewProductRule(request: ProductRulePreviewRequest): Promise<ProductRulePreviewResponse> {
    return this.request("/products/rules/preview", { method: "POST", body: JSON.stringify(request) });
  }

  applyProductRule(id: number): Promise<ProductAssignmentRunResponse> {
    return this.request(`/products/rules/${id}/apply`, { method: "POST" });
  }

  runProductAssignments(request: ProductAssignmentRunRequest = {}): Promise<ProductAssignmentRunResponse> {
    return this.request("/products/assignments/run", { method: "POST", body: JSON.stringify(request) });
  }

  search(params: SearchParams): Promise<PageResponse<SearchResultDTO>> {
    return this.request(`/search?${toQuery({
      q: params.q || undefined,
      store: params.store || undefined,
      dateFrom: params.dateFrom || undefined,
      dateTo: params.dateTo || undefined,
      categoryIds: params.categoryIds?.length ? params.categoryIds.join(",") : undefined,
      uncategorizedOnly: params.uncategorizedOnly ? "true" : undefined,
      amountMin: params.amountMin ?? undefined,
      amountMax: params.amountMax ?? undefined,
      page: params.page ?? 0,
      size: params.size ?? 20,
      sortBy: params.sortBy ?? "receiptDate",
      sortDir: params.sortDir ?? "desc"
    })}`);
  }

  reportByCategory(params: ReportFilters): Promise<ReportByCategoryDTO[]> {
    return this.request(`/reports/by-category?${reportQuery(params)}`);
  }

  reportByPeriod(params: ReportFilters): Promise<ReportByPeriodDTO[]> {
    return this.request(`/reports/by-period?${reportQuery(params)}`);
  }

  reportByStore(params: ReportFilters): Promise<ReportByStoreDTO[]> {
    return this.request(`/reports/by-store?${reportQuery(params)}`);
  }

  topItems(params: ReportFilters): Promise<TopItemReportDTO[]> {
    return this.request(`/reports/top-items?${reportQuery(params)}`);
  }

  bonusReport(params: Pick<ReportFilters, "dateFrom" | "dateTo" | "store">): Promise<BonusReportDTO[]> {
    return this.request(`/reports/bonus?${toQuery({
      dateFrom: params.dateFrom || undefined,
      dateTo: params.dateTo || undefined,
      store: params.store || undefined
    })}`);
  }

  downloadReportCsv(type: "by-category" | "by-period" | "by-store" | "top-items" | "bonus", params: ReportFilters): Promise<Blob> {
    return this.download(`/reports/${type}/export?${reportQuery(params)}`);
  }

  settings(): Promise<SettingsDTO> {
    return this.request("/settings");
  }

  updateSettings(request: SettingsDTO): Promise<SettingsDTO> {
    return this.request("/settings", {
      method: "PUT",
      body: JSON.stringify(request)
    });
  }

  testSettingsConnection(target: "PAPERLESS" | "OPENROUTER"): Promise<SettingsConnectionTestResponse> {
    return this.request("/settings/test-connection", {
      method: "POST",
      body: JSON.stringify({ target })
    });
  }

  rules(): Promise<CategorizationRuleDTO[]> {
    return this.request("/categorization-rules");
  }

  createRule(request: CategorizationRuleRequest): Promise<CategorizationRuleDTO> {
    return this.request("/categorization-rules", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  updateRule(id: number, request: CategorizationRuleRequest): Promise<CategorizationRuleDTO> {
    return this.request(`/categorization-rules/${id}`, {
      method: "PUT",
      body: JSON.stringify(request)
    });
  }

  deleteRule(id: number): Promise<void> {
    return this.request(`/categorization-rules/${id}`, { method: "DELETE" });
  }

  applyRule(id: number): Promise<CategorizationRuleApplyResponse> {
    return this.request(`/categorization-rules/${id}/apply`, { method: "POST" });
  }

  previewRule(request: CategorizationRulePreviewRequest): Promise<CategorizationRulePreviewResponse> {
    return this.request("/categorization-rules/preview", {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  parseRuleSuggestions(params: {
    page?: number;
    size?: number;
    status?: ParseRuleSuggestionStatus | "";
    store?: string;
    validationStatus?: string;
  } = {}): Promise<PageResponse<ParseRuleSuggestionDTO>> {
    return this.request(`/parser/rule-suggestions?${toQuery({
      page: params.page ?? 0,
      size: params.size ?? 20,
      status: params.status || undefined,
      store: params.store || undefined,
      validationStatus: params.validationStatus || undefined
    })}`);
  }

  parseRuleSuggestion(id: number): Promise<ParseRuleSuggestionDTO> {
    return this.request(`/parser/rule-suggestions/${id}`);
  }

  updateParseRuleSuggestion(id: number, request: ParseRuleSuggestionUpdateRequest): Promise<ParseRuleSuggestionDTO> {
    return this.request(`/parser/rule-suggestions/${id}`, {
      method: "PUT",
      body: JSON.stringify(request)
    });
  }

  acceptParseRuleSuggestion(id: number, request: ParseRuleSuggestionAcceptRequest): Promise<ParseRuleSuggestionDTO> {
    return this.request(`/parser/rule-suggestions/${id}/accept`, {
      method: "POST",
      body: JSON.stringify(request)
    });
  }

  rejectParseRuleSuggestion(id: number, rejectionReason: string): Promise<ParseRuleSuggestionDTO> {
    return this.request(`/parser/rule-suggestions/${id}/reject`, {
      method: "POST",
      body: JSON.stringify({ rejectionReason })
    });
  }

  exportParseRuleSuggestionMigration(): Promise<MigrationDraftDTO> {
    return this.request("/parser/rule-suggestions/export-migration", { method: "POST" });
  }

  reparseAllReceipts(overwriteManualEdits: boolean): Promise<DataMaintenanceResultDTO> {
    return this.request(`/receipts/reparse?overwriteManualEdits=${overwriteManualEdits ? "true" : "false"}`, {
      method: "POST"
    });
  }

  resetProductData(confirmation: string): Promise<ProductDataResetResultDTO> {
    return this.request("/admin/data-reset/product-data", {
      method: "POST",
      body: JSON.stringify({ confirmation })
    });
  }

  resetImportedReceipts(confirmation: string): Promise<DataMaintenanceResultDTO> {
    return this.request("/admin/data-reset/imported-receipts", {
      method: "POST",
      body: JSON.stringify({ confirmation })
    });
  }

  downloadBackup(): Promise<DownloadedFile> {
    return this.downloadWithFilename("/backup/download", "ebon-backup.zip");
  }

  validateBackup(file: File): Promise<BackupValidationReportDTO> {
    return this.uploadFile("/backup/validate", file);
  }

  restoreBackup(file: File): Promise<BackupRestoreResultDTO> {
    return this.uploadFile("/backup/restore", file);
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
    if (isMockApiEnabled()) {
      return mockRequest<T>(path, init);
    }

    const headers = new Headers(init.headers);
    const token = this.tokenProvider();

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    if (init.body && !headers.has("Content-Type")) {
      headers.set("Content-Type", "application/json");
    }

    const response = await fetch(`/api${path}`, {
      ...init,
      headers
    });

    if (!response.ok) {
      throw await toClientError(response);
    }

    if (response.status === 204) {
      return undefined as T;
    }

    return response.json() as Promise<T>;
  }

  private async download(path: string): Promise<Blob> {
    if (isMockApiEnabled()) {
      return mockDownload(path);
    }

    const headers = new Headers();
    const token = this.tokenProvider();

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(`/api${path}`, { headers });

    if (!response.ok) {
      throw await toClientError(response);
    }

    return response.blob();
  }

  private async downloadWithFilename(path: string, fallbackFilename: string): Promise<DownloadedFile> {
    if (isMockApiEnabled()) {
      return {
        blob: await mockDownload(path),
        filename: fallbackFilename
      };
    }

    const headers = new Headers();
    const token = this.tokenProvider();

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(`/api${path}`, { headers });

    if (!response.ok) {
      throw await toClientError(response);
    }

    return {
      blob: await response.blob(),
      filename: filenameFromContentDisposition(response.headers.get("content-disposition"), fallbackFilename)
    };
  }

  private async uploadFile<T>(path: string, file: File): Promise<T> {
    if (isMockApiEnabled()) {
      return mockUploadFile<T>(path, file);
    }

    const headers = new Headers();
    const token = this.tokenProvider();

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const body = new FormData();
    body.set("file", file);

    const response = await fetch(`/api${path}`, {
      method: "POST",
      headers,
      body
    });

    if (!response.ok) {
      throw await toClientError(response);
    }

    return response.json() as Promise<T>;
  }
}

function reportQuery(params: ReportFilters): string {
  return toQuery({
    dateFrom: params.dateFrom || undefined,
    dateTo: params.dateTo || undefined,
    categoryIds: params.categoryIds?.length ? params.categoryIds.join(",") : undefined,
    store: params.store || undefined,
    groupBy: params.groupBy || undefined,
    size: params.size ?? undefined
  });
}

function toQuery(values: Record<string, string | number | undefined>): string {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== undefined) {
      params.set(key, String(value));
    }
  }
  return params.toString();
}

async function toClientError(response: Response): Promise<ApiClientError> {
  const fallbackMessage = `API-Anfrage fehlgeschlagen (${response.status}).`;

  try {
    const details = (await response.json()) as ApiErrorResponse;
    return new ApiClientError(details.message || fallbackMessage, response.status, details);
  } catch {
    return new ApiClientError(fallbackMessage, response.status, null);
  }
}

function filenameFromContentDisposition(header: string | null, fallback: string): string {
  if (!header) {
    return fallback;
  }
  const quoted = /filename="([^"]+)"/i.exec(header);
  if (quoted?.[1]) {
    return quoted[1];
  }
  const plain = /filename=([^;]+)/i.exec(header);
  return plain?.[1]?.trim() || fallback;
}
