import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { api } = vi.hoisted(() => ({
  api: {
    receipts: vi.fn(),
    receipt: vi.fn(),
    categories: vi.fn(),
    aiParsingLog: vi.fn(),
    parseRuleSuggestions: vi.fn(),
    triggerSync: vi.fn()
  }
}));

vi.mock("@/lib/api", () => ({
  ApiClient: class {
    constructor() {
      return api;
    }
  },
  ApiClientError: class extends Error {}
}));

vi.mock("@/pages/dashboard-page", () => ({ DashboardPage: () => <p>Dashboard</p> }));
vi.mock("@/pages/search-page", () => ({ SearchPage: () => <p>Suche</p> }));
vi.mock("@/pages/reports-page", () => ({ ReportsPage: () => <p>Reports</p> }));
vi.mock("@/pages/settings-page", () => ({ SettingsPage: () => <p>Einstellungen</p> }));
vi.mock("@/pages/products-page", () => ({ ProductsPage: () => <p>Produkte</p> }));

import App from "@/App";

const receipt = {
  id: 17,
  paperlessDocumentId: 117,
  paperlessDocumentUrl: null,
  importedAt: "2026-07-11T19:30:00Z",
  receiptDate: "2026-07-11",
  receiptTime: "18:45:00",
  storeName: "REWE",
  storeBranch: "Innenstadt",
  totalAmount: 3.49,
  currency: "EUR",
  bonusBalance: null,
  bonusPoints: null,
  bonusType: null,
  parseStatus: "PARSED",
  parseSource: "RULE",
  parseErrorMessage: null,
  aiParsingSummary: null,
  deletedAt: null,
  deleteReason: null,
  rawText: null,
  items: []
};

describe("receipt navigation through App", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
    sessionStorage.setItem("ebon.sessionApiToken", "test-token");
    window.location.hash = "#/receipts";
    vi.stubGlobal("scrollTo", vi.fn());
    api.receipts.mockResolvedValue({ content: [receipt], page: 0, size: 20, totalElements: 41, totalPages: 3 });
    api.receipt.mockResolvedValue(receipt);
    api.categories.mockResolvedValue([]);
    api.aiParsingLog.mockResolvedValue([]);
    api.parseRuleSuggestions.mockResolvedValue({ content: [], page: 0, size: 50, totalElements: 0, totalPages: 0 });
  });

  it("restores filter, sort, page, and scroll after list-detail-list remounts", async () => {
    const user = userEvent.setup();
    render(<App />);

    await screen.findByRole("table");
    fireEvent.change(screen.getByRole("combobox"), { target: { value: "PARSED" } });
    fireEvent.change(screen.getByPlaceholderText("Geschäft"), { target: { value: "REWE" } });
    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: "Geschäft" }));
    await screen.findByRole("table");
    await user.click(screen.getByRole("button", { name: "Weiter" }));
    await waitFor(() => expect(api.receipts).toHaveBeenCalledWith(expect.objectContaining({
      status: "PARSED", store: "REWE", sortBy: "storeName", sortDir: "asc", page: 1
    })));

    Object.defineProperty(window, "scrollY", { configurable: true, value: 520 });
    await user.click(within(await screen.findByRole("table")).getByRole("link", { name: /Bon REWE.*öffnen/ }));
    expect(await screen.findByRole("heading", { name: "REWE" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Zur Bon-Liste" }));
    await screen.findByRole("table");
    await waitFor(() => expect(api.receipts).toHaveBeenLastCalledWith(expect.objectContaining({
      status: "PARSED", store: "REWE", sortBy: "storeName", sortDir: "asc", page: 1
    })));
    expect(screen.getByRole("combobox")).toHaveValue("PARSED");
    expect(screen.getByPlaceholderText("Geschäft")).toHaveValue("REWE");
    await waitFor(() => expect(window.scrollTo).toHaveBeenCalledWith({ top: 520 }));
  });
});
