import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { ApiClient } from "@/lib/api";

function jsonResponse(body: unknown, status = 200, headers: HeadersInit = {}): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "Content-Type": "application/json",
      ...headers
    }
  });
}

describe("ApiClient", () => {
  const client = new ApiClient(() => "test-token");
  let fetchMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal("fetch", fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("serializes receipt filters, applies defaults, and sends the bearer token", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ content: [] }));

    await client.receipts({
      page: 2,
      size: 10,
      status: "PARSED",
      includeDeleted: true,
      uncategorizedOnly: true
    });

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/receipts?page=2&size=10&sortBy=receiptDate&sortDir=desc&status=PARSED&includeDeleted=true&uncategorizedOnly=true");
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer test-token");
  });

  it("sends JSON mutations with their documented HTTP methods", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ id: 12 }))
      .mockResolvedValueOnce(jsonResponse({ id: 7 }))
      .mockResolvedValueOnce(jsonResponse({ message: "started" }));

    const receiptUpdate = {
      receiptDate: "2026-06-22",
      receiptTime: "17:42:00",
      storeName: "REWE",
      storeBranch: null,
      totalAmount: 12.5,
      currency: "EUR",
      bonusBalance: null,
      bonusPoints: null,
      bonusType: null
    };

    await client.updateReceipt(12, receiptUpdate);
    await client.updateReceiptItem(7, { categoryId: null, categorySource: null });
    await client.triggerSync();

    const [, receiptInit] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(receiptInit.method).toBe("PUT");
    expect(receiptInit.body).toBe(JSON.stringify(receiptUpdate));
    expect(new Headers(receiptInit.headers).get("Content-Type")).toBe("application/json");

    const [itemUrl, itemInit] = fetchMock.mock.calls[1] as [string, RequestInit];
    expect(itemUrl).toBe("/api/receipt-items/7");
    expect(itemInit.method).toBe("PATCH");

    const [syncUrl, syncInit] = fetchMock.mock.calls[2] as [string, RequestInit];
    expect(syncUrl).toBe("/api/sync/trigger");
    expect(syncInit.method).toBe("POST");
  });

  it("builds explicit reparse and search query contracts", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ id: 12 })).mockResolvedValueOnce(jsonResponse({ content: [] }));

    await client.reparseReceipt(12, true, false, "FULL_TEXT", true, "PAPERLESS");
    await client.search({ q: "Bio Milch", categoryIds: [3, 8], amountMin: 2.5, page: 1, size: 5, sortDir: "asc" });

    expect(fetchMock.mock.calls[0][0]).toBe(
      "/api/receipts/12/reparse?overwriteManualEdits=true&useAiFallback=false&aiTextMode=FULL_TEXT&confirmFullText=true&rawTextSource=PAPERLESS"
    );
    expect(fetchMock.mock.calls[1][0]).toBe(
      "/api/search?q=Bio+Milch&categoryIds=3%2C8&amountMin=2.5&page=1&size=5&sortBy=receiptDate&sortDir=asc"
    );
  });

  it("returns undefined for successful no-content deletes", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(client.deleteReceipt(11)).resolves.toBeUndefined();
    expect(fetchMock.mock.calls[0][0]).toBe("/api/receipts/11");
    expect(fetchMock.mock.calls[0][1]).toMatchObject({ method: "DELETE" });
  });

  it("converts JSON and non-JSON API errors into a stable error type", async () => {
    fetchMock
      .mockResolvedValueOnce(jsonResponse({ message: "Token fehlt", status: 401, error: "Unauthorized", timestamp: "now", path: "/api/sync" }, 401))
      .mockResolvedValueOnce(new Response("gateway unavailable", { status: 503 }));

    await expect(client.triggerSync()).rejects.toMatchObject({
      name: "ApiClientError",
      status: 401,
      message: "Token fehlt"
    });
    await expect(client.dashboard()).rejects.toMatchObject({
      name: "ApiClientError",
      status: 503,
      message: "API-Anfrage fehlgeschlagen (503).",
      details: null
    });
  });

  it("downloads report and backup files with authenticated requests and safe filenames", async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(new Blob(["category report"]), { status: 200 }))
      .mockResolvedValueOnce(
        new Response(new Blob(["backup"]), {
          status: 200,
          headers: { "content-disposition": "attachment; filename=ebon-2026.zip" }
        })
      );

    await expect(client.downloadReportCsv("by-category", { dateFrom: "2026-01-01", store: "REWE" })).resolves.toBeInstanceOf(Blob);
    await expect(client.downloadBackup()).resolves.toMatchObject({ filename: "ebon-2026.zip" });

    expect(fetchMock.mock.calls[0][0]).toBe("/api/reports/by-category/export?dateFrom=2026-01-01&store=REWE");
    expect(new Headers((fetchMock.mock.calls[1][1] as RequestInit).headers).get("Authorization")).toBe("Bearer test-token");
  });

  it("uploads backup files as multipart data without a manual content type", async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse({ valid: true }));
    const file = new File(["backup"], "backup.zip", { type: "application/zip" });

    await client.validateBackup(file);

    const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
    expect(url).toBe("/api/backup/validate");
    expect(init.method).toBe("POST");
    expect(init.body).toBeInstanceOf(FormData);
    expect(new Headers(init.headers).has("Content-Type")).toBe(false);
    expect(new Headers(init.headers).get("Authorization")).toBe("Bearer test-token");
  });
});
