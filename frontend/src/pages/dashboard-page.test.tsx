import { fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { ApiClient } from "@/lib/api";
import type { DashboardDTO } from "@/lib/types";
import { DashboardPage } from "@/pages/dashboard-page";

vi.mock("@/components/category-chart", () => ({
  CategoryChart: () => <div>Diagramm geladen</div>
}));

const dashboard: DashboardDTO = {
  currentMonthTotal: 152.4,
  previousMonthTotal: 140,
  currentYearTotal: 1240.5,
  currentMonthByCategory: [],
  bonusSummary: [],
  recentReceipts: [
    {
      id: 17,
      paperlessDocumentId: 117,
      paperlessDocumentUrl: null,
      importedAt: "2026-07-11T19:30:00Z",
      receiptDate: "2026-07-11",
      receiptTime: "18:45:00",
      storeName: "REWE",
      storeBranch: "Innenstadt",
      totalAmount: 34.8,
      currency: "EUR",
      bonusBalance: 0.25,
      bonusPoints: 25,
      bonusType: "PAYBACK",
      parseStatus: "PARSED",
      parseSource: "RULE",
      parseErrorMessage: null,
      aiParsingSummary: null,
      deletedAt: null,
      deleteReason: null,
      rawText: null,
      items: []
    }
  ],
  uncategorizedItemsCount: 4,
  lastSyncStatus: {
    lastSyncAt: "2026-07-11T20:00:00Z",
    lastSyncStatus: "SUCCESS",
    newDocumentsCount: 3,
    removedDocumentsCount: 2,
    errorCount: 1,
    isSyncing: false
  }
};

function apiClient(overrides: Partial<Record<"dashboard" | "syncLog" | "reportByCategory" | "bonusReport", unknown>> = {}) {
  const api = {
    dashboard: vi.fn().mockResolvedValue(overrides.dashboard ?? dashboard),
    syncLog: vi.fn().mockResolvedValue(overrides.syncLog ?? {
      content: [
        {
          id: 9,
          startedAt: "2026-07-11T20:00:00Z",
          finishedAt: "2026-07-11T20:00:05Z",
          status: "SUCCESS",
          newDocumentsCount: 3,
          removedDocumentsCount: 2,
          errorMessage: null
        }
      ],
      page: 0,
      size: 5,
      totalElements: 1,
      totalPages: 1
    }),
    reportByCategory: vi.fn().mockResolvedValue(overrides.reportByCategory ?? [
      { categoryId: 1, categoryName: "Lebensmittel", total: 97.6 }
    ]),
    bonusReport: vi.fn().mockResolvedValue(overrides.bonusReport ?? [
      { bonusType: "PAYBACK", totalPoints: 25, totalEarnedBalance: 0.25 }
    ]),
    triggerSync: vi.fn().mockResolvedValue({ message: "Sync gestartet" })
  };
  return api;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((resolvePromise, rejectPromise) => {
    resolve = resolvePromise;
    reject = rejectPromise;
  });
  return { promise, reject, resolve };
}

describe("DashboardPage", () => {
  it("preserves dashboard information while establishing the new hierarchy", async () => {
    const user = userEvent.setup();
    render(<DashboardPage apiClient={apiClient() as unknown as ApiClient} hasApiToken />);

    expect(await screen.findByRole("heading", { name: "Finanzübersicht" })).toBeInTheDocument();
    expect(screen.getByText("Übersicht / Finanzen")).toBeInTheDocument();

    const requiredLabels = [
      "Aktueller Monat",
      "Vormonat",
      "Aktuelles Jahr",
      "Delta zum Vormonat",
      "Ohne Kategorie",
      "Bonus neu",
      "Ausgaben nach Kategorie",
      "Letzte Bons",
      "Bonus neu im Zeitraum",
      "Sync-Log"
    ];
    for (const label of requiredLabels) {
      expect(screen.getAllByText(label).length).toBeGreaterThan(0);
    }

    const categoryCard = screen.getByRole("heading", { name: "Ausgaben nach Kategorie" }).closest("section");
    const recentCard = screen.getByRole("heading", { name: "Letzte Bons" }).closest("section");
    const bonusCard = screen.getByRole("heading", { name: "Bonus neu im Zeitraum" }).closest("section");
    const syncLogCard = screen.getByRole("heading", { name: "Sync-Log" }).closest("section");
    expect(categoryCard).toHaveClass("min-w-0");
    expect(recentCard).toHaveClass("min-w-0");
    expect(categoryCard?.parentElement).toHaveClass("min-w-0");
    expect(bonusCard).toHaveClass("min-w-0");
    expect(syncLogCard).toHaveClass("min-w-0");
    expect(syncLogCard?.parentElement).toHaveClass("min-w-0");

    expect(screen.getByRole("status")).toHaveTextContent("Sync bereit");
    expect(screen.getByRole("status")).toHaveTextContent("Neu: 3");
    expect(screen.getByRole("status")).toHaveTextContent("Entfernt: 2");
    expect(screen.getByRole("status")).toHaveTextContent("Fehler: 1");
    expect(screen.getByRole("status")).toHaveTextContent("11.07.2026");
    expect(screen.getByText("Lebensmittel")).toBeInTheDocument();
    expect(screen.getByText(/97,60/)).toBeInTheDocument();
    expect(screen.getByText("REWE")).toBeInTheDocument();
    expect(screen.getAllByText("PAYBACK")).toHaveLength(1);
    expect(screen.getByText(/25 Punkte · 0,25/)).toBeInTheDocument();
    expect(screen.getByText("+3 / -2")).toBeInTheDocument();
    const range = screen.getByRole("combobox");
    expect(range).toHaveValue("currentMonth");
    for (const option of ["Aktueller Monat", "Letztes Quartal", "Aktuelles Jahr", "Vorheriges Jahr", "Benutzerdefiniert"]) {
      expect(within(range).getByRole("option", { name: option })).toBeInTheDocument();
    }

    await user.click(screen.getByRole("link", { name: /Ohne Kategorie/ }));
    expect(window.location.hash).toBe("#/search?uncategorizedOnly=true");
  });

  it("uses custom period fields for category and bonus API filters", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<DashboardPage apiClient={api as unknown as ApiClient} hasApiToken />);
    await screen.findByText("Lebensmittel");

    await user.selectOptions(screen.getByRole("combobox"), "custom");
    fireEvent.change(screen.getByLabelText("Zeitraum von"), { target: { value: "2026-04-01" } });
    fireEvent.change(screen.getByLabelText("Zeitraum bis"), { target: { value: "2026-06-30" } });

    await waitFor(() => {
      expect(api.reportByCategory).toHaveBeenLastCalledWith({ dateFrom: "2026-04-01", dateTo: "2026-06-30" });
      expect(api.bonusReport).toHaveBeenLastCalledWith({ dateFrom: "2026-04-01", dateTo: "2026-06-30" });
    });
  });

  it("triggers sync, reloads the dashboard, and links recent receipts accessibly", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<DashboardPage apiClient={api as unknown as ApiClient} hasApiToken />);

    const receiptLink = await screen.findByRole("link", { name: /Bon REWE.*11\.07\.2026.*öffnen/ });
    expect(receiptLink).toHaveAttribute("href", "#/receipts/17");
    await user.click(receiptLink);
    expect(window.location.hash).toBe("#/receipts/17");

    await user.click(screen.getByRole("button", { name: "Sync starten" }));
    await waitFor(() => expect(api.triggerSync).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(api.dashboard).toHaveBeenCalledTimes(2));
  });

  it("shows loading placeholders instead of bonus and sync-log empty states", () => {
    const dashboardRequest = deferred<DashboardDTO>();
    const api = apiClient({ dashboard: dashboardRequest.promise });
    render(<DashboardPage apiClient={api as unknown as ApiClient} hasApiToken />);

    expect(screen.getByRole("status")).toHaveTextContent("Dashboard wird geladen");
    expect(screen.queryByText("Keine Bonusdaten")).not.toBeInTheDocument();
    expect(screen.queryByText("Kein Sync-Log")).not.toBeInTheDocument();
  });

  it("shows a neutral unavailable sync state after an initial load error", async () => {
    const api = apiClient();
    api.dashboard.mockRejectedValueOnce(new Error("Dashboard nicht erreichbar"));
    render(<DashboardPage apiClient={api as unknown as ApiClient} hasApiToken />);

    expect(await screen.findByText("Dashboard nicht erreichbar")).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("Sync-Status nicht verfügbar");
    expect(screen.queryByText("Sync bereit")).not.toBeInTheDocument();
    expect(screen.queryByText("Keine Bonusdaten")).not.toBeInTheDocument();
    expect(screen.queryByText("Kein Sync-Log")).not.toBeInTheDocument();
  });

  it("shows empty states only after a successful load without data", async () => {
    const api = apiClient({
      dashboard: { ...dashboard, recentReceipts: [] },
      syncLog: { content: [], page: 0, size: 5, totalElements: 0, totalPages: 0 },
      reportByCategory: [],
      bonusReport: []
    });
    render(<DashboardPage apiClient={api as unknown as ApiClient} hasApiToken />);

    expect(await screen.findByText("Keine Bonusdaten")).toBeInTheDocument();
    expect(screen.getByText("Kein Sync-Log")).toBeInTheDocument();
    expect(screen.getByText("Keine Bons")).toBeInTheDocument();
    expect(screen.getByText("Keine Monatsdaten")).toBeInTheDocument();
  });
});
