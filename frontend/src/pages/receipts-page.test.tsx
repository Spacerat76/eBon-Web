import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ApiClient } from "@/lib/api";
import type { ParseRuleSuggestionDTO, ReceiptDTO } from "@/lib/types";
import { ReceiptsPage } from "@/pages/receipts-page";

const receipt: ReceiptDTO = {
  id: 17,
  paperlessDocumentId: 117,
  paperlessDocumentUrl: "https://paperless.example/documents/117/details",
  importedAt: "2026-07-11T19:30:00Z",
  receiptDate: "2026-07-11",
  receiptTime: "18:45:00",
  storeName: "REWE",
  storeBranch: "Innenstadt",
  totalAmount: 3.49,
  currency: "EUR",
  bonusBalance: null,
  bonusPoints: 10,
  bonusType: "PAYBACK",
  parseStatus: "PARSED",
  parseSource: "AI",
  parseErrorMessage: null,
  aiParsingSummary: {
    lastStatus: "SUCCESS",
    lastTrigger: "MANUAL_REPARSE",
    modelUsed: "test-model",
    overallConfidence: 0.98,
    hasOpenRuleSuggestions: true
  },
  deletedAt: null,
  deleteReason: null,
  rawText: "PRIVATE RECEIPT RAW TEXT",
  items: [
    {
      id: 23,
      receiptId: 17,
      positionIndex: 0,
      description: "Coca Cola Zero",
      quantity: 1,
      unit: "Stück",
      unitPrice: 3.49,
      totalPrice: 3.49,
      discountAmount: null,
      categoryId: 4,
      categoryName: "Getränke",
      categorySource: "RULE",
      isManuallyEdited: false,
      aiSuggestion: null,
      productFamilyId: 8,
      productFamilyName: "Coca Cola Zero",
      productVariantId: 9,
      productVariantName: "0,5 l Flasche",
      productAssignmentSource: "RULE",
      productAssignmentStatus: "AUTO_ASSIGNED",
      productAssignmentConfidence: 0.99,
      computedUnitPrice: 6.98,
      computedUnitPriceUnit: "l",
      excludeFromProductPriceComparison: false,
      productPriceExclusionReason: null
    }
  ]
};

const ruleSuggestion: ParseRuleSuggestionDTO = {
  id: 41,
  receiptId: 17,
  aiParsingLogId: 31,
  storeName: "REWE",
  ruleType: "ITEM_PATTERN",
  matchRegex: "(?<item>.*)",
  extractGroup: "item",
  confidence: 0.9,
  trigger: "MANUAL_REPARSE",
  problemDescription: "Artikelzeile nicht erkannt",
  solutionRationale: "Erkennt das REWE-Format",
  validationStatus: "VALID",
  validationMessage: null,
  status: "OPEN",
  rejectionReason: null,
  acceptedParseRuleId: null,
  receiptContext: null
};

function apiClient() {
  return {
    receipts: vi.fn().mockResolvedValue({
      content: [receipt],
      page: 0,
      size: 20,
      totalElements: 1,
      totalPages: 1,
      sortBy: "receiptDate",
      sortDir: "desc"
    }),
    receipt: vi.fn().mockResolvedValue(receipt),
    categories: vi.fn().mockResolvedValue([
      { id: 4, name: "Getränke", colorHex: "#2563eb", icon: "cup-soda", sortOrder: 1, isActive: true }
    ]),
    aiParsingLog: vi.fn().mockResolvedValue([
      {
        id: 31,
        receiptId: 17,
        trigger: "MANUAL_REPARSE",
        status: "SUCCESS",
        modelUsed: "test-model",
        startedAt: "2026-07-11T19:31:00Z",
        finishedAt: "2026-07-11T19:31:01Z",
        durationMs: 1000,
        overallConfidence: 0.98,
        parseErrorBefore: null,
        failureReason: null,
        fieldConfidence: {},
        warnings: [],
        promptSnippet: null,
        responseSnippet: null
      }
    ]),
    parseRuleSuggestions: vi.fn().mockResolvedValue({
      content: [ruleSuggestion],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1
    }),
    parseRuleSuggestion: vi.fn().mockResolvedValue({
      ...ruleSuggestion,
      receiptContext: {
        receiptId: 17,
        paperlessDocumentId: 117,
        rawText: "KONTEXT ROHTEXT",
        parseStatus: "PARSED",
        parseSource: "AI",
        receiptDate: "2026-07-11",
        receiptTime: "18:45:00",
        storeName: "REWE",
        storeBranch: "Innenstadt",
        totalAmount: 3.49,
        currency: "EUR",
        items: [{ positionIndex: 0, description: "Coca Cola Zero", quantity: 1, unit: "Stück", unitPrice: 3.49, totalPrice: 3.49, discountAmount: null }]
      }
    }),
    triggerSync: vi.fn().mockResolvedValue({ message: "Sync gestartet" }),
    paperlessRawTextStatus: vi.fn().mockResolvedValue({ status: "UNCHANGED" }),
    reparseReceipt: vi.fn().mockResolvedValue(receipt),
    updateReceipt: vi.fn().mockResolvedValue(receipt),
    updateReceiptItem: vi.fn().mockResolvedValue(receipt.items[0]),
    deleteReceipt: vi.fn().mockResolvedValue(undefined),
    updateParseRuleSuggestion: vi.fn().mockResolvedValue(undefined),
    acceptParseRuleSuggestion: vi.fn().mockResolvedValue(undefined),
    rejectParseRuleSuggestion: vi.fn().mockResolvedValue(undefined)
  };
}

describe("ReceiptsPage", () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.location.hash = "#/receipts";
    vi.stubGlobal("scrollTo", vi.fn());
  });

  it("renders the full-width receipt list with approved columns and controls", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={null} />);

    const table = await screen.findByRole("table");
    const listColumns = ["Datum", "Geschäft", "Betrag", "Positionen", "Status", "Import"];
    expect(within(table).getAllByRole("columnheader").map((header) => header.textContent?.trim())).toEqual(listColumns);
    expect(screen.getByRole("combobox")).toHaveValue("");
    expect(screen.getByPlaceholderText("Geschäft")).toBeInTheDocument();
    expect(screen.getByLabelText("Datum von")).toBeInTheDocument();
    expect(screen.getByLabelText("Datum bis")).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Gelöschte Bons anzeigen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Sync starten" })).toBeInTheDocument();

    fireEvent.change(screen.getByPlaceholderText("Geschäft"), { target: { value: "REWE" } });
    await waitFor(() => expect(api.receipts).toHaveBeenLastCalledWith(expect.objectContaining({ store: "REWE" })));
    Object.defineProperty(window, "scrollY", { configurable: true, value: 640 });
    await user.click(within(await screen.findByRole("table")).getByText("REWE"));

    expect(window.location.hash).toBe("#/receipts/17");
    expect(JSON.parse(sessionStorage.getItem("ebon.receiptListState") ?? "null")).toEqual({
      filters: { status: "", store: "REWE", dateFrom: "", dateTo: "", includeDeleted: false },
      page: 0,
      sortBy: "receiptDate",
      sortDir: "desc",
      scrollY: 640
    });
    expect(sessionStorage.getItem("ebon.receiptListState")).not.toContain(receipt.rawText);
  });

  it("renders receipt detail actions, badges, item context, and all approved tabs", async () => {
    const user = userEvent.setup();
    render(<ReceiptsPage apiClient={apiClient() as unknown as ApiClient} hasApiToken selectedReceiptId={17} />);

    expect(await screen.findByRole("heading", { name: "REWE" })).toBeInTheDocument();
    for (const action of ["Bearbeiten", "Erneut parsen", "Löschen"]) {
      expect(screen.getByRole("button", { name: action })).toBeInTheDocument();
    }
    expect(screen.getByRole("link", { name: /Paperless #117/ })).toHaveAttribute("href", receipt.paperlessDocumentUrl);
    expect(screen.getAllByText("per KI geparst").length).toBeGreaterThan(0);
    const summary = screen.getByLabelText("Bon-Zusammenfassung");
    expect(within(summary).getByText(/11\.07\.2026.*18:45/)).toBeInTheDocument();
    expect(within(summary).getByText(/3,49/)).toBeInTheDocument();
    expect(within(summary).getByText(/PAYBACK.*10 Punkte/)).toBeInTheDocument();

    const detailTabs = ["Positionen", "Bon-Daten", "Rohtext", "KI-Protokoll", "Regelvorschläge"];
    expect(screen.getAllByRole("tab").map((tab) => tab.textContent?.replace(/\d+$/, "").trim())).toEqual(detailTabs);
    expect(screen.getAllByText("Coca Cola Zero").length).toBeGreaterThan(0);
    expect(screen.getByText("0,5 l Flasche")).toBeInTheDocument();
    expect(screen.getByText("Getränke")).toBeInTheDocument();
    expect(screen.getByText("Zuordnungsquelle: Regel")).toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Bon-Daten" }));
    expect(screen.getAllByText("Gesamtbetrag").length).toBeGreaterThan(1);
    await user.click(screen.getByRole("tab", { name: "Rohtext" }));
    expect(screen.getByText(receipt.rawText ?? "")).toBeInTheDocument();
    expect(within(summary).getByText(/11\.07\.2026.*18:45/)).toBeInTheDocument();
    expect(within(summary).getByText(/PAYBACK.*10 Punkte/)).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "KI-Protokoll" }));
    expect(screen.getByText("test-model")).toBeInTheDocument();
    await user.click(screen.getByRole("tab", { name: "Regelvorschläge" }));
    expect(screen.getByText("Artikelzeile nicht erkannt")).toBeInTheDocument();
  });

  it("sorts, paginates, and synchronizes the receipt list", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    api.receipts.mockResolvedValue({
      content: [receipt], page: 0, size: 20, totalElements: 41, totalPages: 3, sortBy: "receiptDate", sortDir: "desc"
    });
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={null} />);

    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: "Geschäft" }));
    await waitFor(() => expect(api.receipts).toHaveBeenCalledWith(expect.objectContaining({ sortBy: "storeName", sortDir: "asc", page: 0 })));

    await user.click(screen.getByRole("button", { name: "Weiter" }));
    await waitFor(() => expect(api.receipts).toHaveBeenCalledWith(expect.objectContaining({ page: 1 })));

    await user.click(screen.getByRole("button", { name: "Sync starten" }));
    await waitFor(() => expect(api.triggerSync).toHaveBeenCalledTimes(1));
    expect(api.receipts.mock.calls.length).toBeGreaterThanOrEqual(4);
  });

  it("keeps edit, reparse, and delete actions wired to their existing API paths", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    vi.spyOn(window, "confirm").mockReturnValue(true);
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={17} />);

    await screen.findByRole("heading", { name: "REWE" });
    await user.click(screen.getByRole("button", { name: "Bearbeiten" }));
    const storeInput = screen.getByLabelText("Geschäft");
    await user.clear(storeInput);
    await user.type(storeInput, "Geändert");
    await user.click(screen.getByRole("button", { name: "Abbrechen" }));
    expect(screen.queryByDisplayValue("Geändert")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Erneut parsen" }));
    await waitFor(() => expect(api.paperlessRawTextStatus).toHaveBeenCalledWith(17));
    await waitFor(() => expect(api.reparseReceipt).toHaveBeenCalledWith(17, false, true, null, false, "STORED"));

    await user.click(screen.getByRole("button", { name: "Löschen" }));
    await waitFor(() => expect(api.deleteReceipt).toHaveBeenCalledWith(17));
    expect(window.location.hash).toBe("#/receipts");
  });

  it("shows the sticky action bar only while editing, restores the draft on cancel, and saves changes", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={17} />);

    await screen.findByRole("heading", { name: "REWE" });
    expect(screen.queryByText("Ungespeicherte Änderungen")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Bearbeiten" }));
    expect(screen.getByText("Ungespeicherte Änderungen")).toBeInTheDocument();
    const storeInput = screen.getByLabelText("Geschäft");
    await user.clear(storeInput);
    await user.type(storeInput, "Geändert");
    await user.click(screen.getByRole("button", { name: "Abbrechen" }));

    expect(screen.queryByText("Ungespeicherte Änderungen")).not.toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Bearbeiten" }));
    expect(screen.getByLabelText("Geschäft")).toHaveValue("REWE");
    await user.clear(screen.getByLabelText("Geschäft"));
    await user.type(screen.getByLabelText("Geschäft"), "REWE Markt");
    await user.click(screen.getByRole("button", { name: "Änderungen speichern" }));

    await waitFor(() => expect(api.updateReceipt).toHaveBeenCalledWith(17, expect.objectContaining({ storeName: "REWE Markt" })));
  });

  it("keeps the manual-overwrite confirmation available outside edit mode", async () => {
    const manualReceipt = {
      ...receipt,
      parseStatus: "MANUALLY_EDITED" as const,
      items: [{ ...receipt.items[0], isManuallyEdited: true }]
    };
    const api = apiClient();
    api.receipt.mockResolvedValue(manualReceipt);
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={17} />);

    expect(await screen.findByRole("checkbox", { name: "Manuell editierte Positionen beim Re-Parse überschreiben." })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Erneut parsen" })).toBeDisabled();
  });

  it("shows and wires complete parser rule suggestion review controls", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    vi.spyOn(window, "prompt").mockReturnValue("Nicht passend");
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={17} />);

    await screen.findByRole("heading", { name: "REWE" });
    await user.click(screen.getByRole("tab", { name: "Regelvorschläge" }));
    expect(screen.getByText("Auslöser: Manueller Reparse")).toBeInTheDocument();
    expect(screen.getByText("Regex: (?<item>.*)")).toBeInTheDocument();
    expect(screen.getByText("Extract-Gruppe: item")).toBeInTheDocument();
    expect(screen.getByText("Bon #17 · REWE")).toBeInTheDocument();
    expect(screen.getByText("Artikelzeile nicht erkannt")).toBeInTheDocument();
    expect(screen.getByText("Erkennt das REWE-Format")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Vorschlag bearbeiten" }));
    const regexInput = screen.getByLabelText("Regex");
    await user.clear(regexInput);
    await user.type(regexInput, "(?<position>.+)");
    await user.click(screen.getByRole("button", { name: "Änderungen speichern" }));
    await waitFor(() => expect(api.updateParseRuleSuggestion).toHaveBeenCalledWith(41, expect.objectContaining({ matchRegex: "(?<position>.+)" })));

    await user.selectOptions(screen.getByLabelText("Reparse-Umfang"), "CURRENT_RECEIPT");
    await user.click(screen.getByRole("button", { name: "Akzeptieren" }));
    await waitFor(() => expect(api.acceptParseRuleSuggestion).toHaveBeenCalledWith(41, expect.objectContaining({ reparseScope: "CURRENT_RECEIPT" })));

    await user.click(screen.getByRole("button", { name: "Ablehnen" }));
    await waitFor(() => expect(api.rejectParseRuleSuggestion).toHaveBeenCalledWith(41, "Nicht passend"));
  });

  it("loads every suggestion page, hydrates all receipt matches, and renders their real contexts", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    const globalSuggestions = Array.from({ length: 100 }, (_, index) => ({
      ...ruleSuggestion,
      id: 1000 + index,
      receiptId: 999
    }));
    const laterMatches = [
      { ...ruleSuggestion, id: 51 },
      { ...ruleSuggestion, id: 52, matchRegex: "(?<total>SUMME.*)", extractGroup: "total", ruleType: "TOTAL_PATTERN" as const }
    ];
    api.parseRuleSuggestions.mockImplementation(({ page }: { page?: number }) => Promise.resolve({
      content: page === 0 ? globalSuggestions : laterMatches,
      page: page ?? 0,
      size: 100,
      totalElements: 102,
      totalPages: 2
    }));
    api.parseRuleSuggestion.mockImplementation((id: number) => {
      const suggestion = laterMatches.find((candidate) => candidate.id === id)!;
      return Promise.resolve({
        ...suggestion,
        receiptContext: {
          receiptId: 17,
          paperlessDocumentId: 117,
          rawText: `KONTEXT ${id}`,
          parseStatus: "PARSED",
          parseSource: "AI",
          receiptDate: "2026-07-11",
          receiptTime: "18:45:00",
          storeName: "REWE",
          storeBranch: id === 51 ? "Innenstadt" : "Bahnhof",
          totalAmount: 3.49,
          currency: "EUR",
          items: [{ positionIndex: 0, description: `Beispielposition ${id}`, quantity: 1, unit: "Stück", unitPrice: 3.49, totalPrice: 3.49, discountAmount: null }]
        }
      });
    });

    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={17} />);
    await screen.findByRole("heading", { name: "REWE" });
    await user.click(screen.getByRole("tab", { name: "Regelvorschläge" }));

    await waitFor(() => expect(api.parseRuleSuggestions).toHaveBeenCalledWith({ page: 0, size: 100 }));
    await waitFor(() => expect(api.parseRuleSuggestions).toHaveBeenCalledWith({ page: 1, size: 100 }));
    await waitFor(() => expect(api.parseRuleSuggestion).toHaveBeenCalledTimes(2));
    expect(api.parseRuleSuggestion).toHaveBeenCalledWith(51);
    expect(api.parseRuleSuggestion).toHaveBeenCalledWith(52);
    expect(await screen.findByText("Beispielposition 51")).toBeInTheDocument();
    expect(screen.getByText("Beispielposition 52")).toBeInTheDocument();
    expect(screen.getByText(/REWE · Innenstadt/)).toBeInTheDocument();
    expect(screen.getByText(/REWE · Bahnhof/)).toBeInTheDocument();
    expect(screen.getAllByText(/11\.07\.2026.*18:45/).length).toBeGreaterThanOrEqual(2);
    const contexts = screen.getAllByLabelText("Bon-Kontext 17");
    expect(contexts).toHaveLength(2);
    for (const context of contexts) {
      expect(within(context).getByText("Geparst")).toBeInTheDocument();
      expect(within(context).getByText("per KI geparst")).toBeInTheDocument();
    }
  });

  it("restores only validated list state and scroll position after list data loads", async () => {
    sessionStorage.setItem("ebon.receiptListState", JSON.stringify({
      filters: { status: "PARSED", store: "REWE", dateFrom: "2026-07-01", dateTo: "2026-07-31", includeDeleted: true, secret: "nope" },
      page: 2,
      sortBy: "storeName",
      sortDir: "asc",
      scrollY: 375,
      rawText: receipt.rawText
    }));
    const api = apiClient();
    render(<ReceiptsPage apiClient={api as unknown as ApiClient} hasApiToken selectedReceiptId={null} />);

    await waitFor(() => expect(api.receipts).toHaveBeenCalledWith(expect.objectContaining({
      status: "PARSED",
      store: "REWE",
      dateFrom: "2026-07-01",
      dateTo: "2026-07-31",
      includeDeleted: true,
      page: 2,
      sortBy: "storeName",
      sortDir: "asc"
    })));
    await waitFor(() => expect(window.scrollTo).toHaveBeenCalledWith({ top: 375 }));
    expect(sessionStorage.getItem("ebon.receiptListState")).not.toContain(receipt.rawText);
  });
});
