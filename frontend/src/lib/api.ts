import type {
  ApiErrorResponse,
  CategoryDTO,
  DashboardDTO,
  MessageResponse,
  PageResponse,
  ReceiptDTO,
  ReceiptItemCreateRequest,
  ReceiptItemDTO,
  ReceiptItemUpdateRequest,
  ReceiptListParams,
  ReceiptUpdateRequest,
  SyncLogDTO,
  SyncStatusDTO
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
      includeDeleted: params.includeDeleted ? "true" : undefined
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
