import type {
  ApiErrorResponse,
  BonusReportDTO,
  CategorizationRuleApplyResponse,
  CategorizationRuleDTO,
  CategorizationRulePreviewRequest,
  CategorizationRulePreviewResponse,
  CategorizationRuleRequest,
  CategoryDTO,
  CategoryPatchRequest,
  CategoryRequest,
  DashboardDTO,
  DataMaintenanceResultDTO,
  MessageResponse,
  PageResponse,
  ReceiptDTO,
  ReceiptItemCreateRequest,
  ReceiptItemDTO,
  ReceiptItemUpdateRequest,
  ReceiptListParams,
  ReceiptUpdateRequest,
  ReportByCategoryDTO,
  ReportByPeriodDTO,
  ReportByStoreDTO,
  ReportFilters,
  SearchParams,
  SearchResultDTO,
  SettingsConnectionTestResponse,
  SettingsDTO,
  SyncLogDTO,
  SyncStatusDTO,
  TopItemReportDTO
} from "@/lib/types";

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

  updateReceipt(id: number, request: ReceiptUpdateRequest): Promise<ReceiptDTO> {
    return this.request(`/receipts/${id}`, {
      method: "PUT",
      body: JSON.stringify(request)
    });
  }

  reparseReceipt(id: number, overwriteManualEdits: boolean): Promise<ReceiptDTO> {
    return this.request(`/receipts/${id}/reparse?overwriteManualEdits=${overwriteManualEdits ? "true" : "false"}`, {
      method: "POST"
    });
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

  reparseAllReceipts(overwriteManualEdits: boolean): Promise<DataMaintenanceResultDTO> {
    return this.request(`/receipts/reparse?overwriteManualEdits=${overwriteManualEdits ? "true" : "false"}`, {
      method: "POST"
    });
  }

  resetImportedReceipts(confirmation: string): Promise<DataMaintenanceResultDTO> {
    return this.request("/admin/data-reset/imported-receipts", {
      method: "POST",
      body: JSON.stringify({ confirmation })
    });
  }

  private async request<T>(path: string, init: RequestInit = {}): Promise<T> {
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
