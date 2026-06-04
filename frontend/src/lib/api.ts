import type {
  ApiErrorResponse,
  DashboardDTO,
  MessageResponse,
  PageResponse,
  ReceiptDTO,
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

  receipts(page = 0, size = 5): Promise<PageResponse<ReceiptDTO>> {
    return this.request(`/receipts?page=${page}&size=${size}&sortBy=receiptDate&sortDir=desc`);
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

async function toClientError(response: Response): Promise<ApiClientError> {
  const fallbackMessage = `API-Anfrage fehlgeschlagen (${response.status}).`;

  try {
    const details = (await response.json()) as ApiErrorResponse;
    return new ApiClientError(details.message || fallbackMessage, response.status, details);
  } catch {
    return new ApiClientError(fallbackMessage, response.status, null);
  }
}
