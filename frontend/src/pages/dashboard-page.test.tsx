import { render, screen } from "@testing-library/react";
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

function apiClient() {
  return {
    dashboard: vi.fn().mockResolvedValue(dashboard),
    syncLog: vi.fn().mockResolvedValue({
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
    reportByCategory: vi.fn().mockResolvedValue([
      { categoryId: 1, categoryName: "Lebensmittel", total: 97.6 }
    ]),
    bonusReport: vi.fn().mockResolvedValue([
      { bonusType: "PAYBACK", totalPoints: 25, totalEarnedBalance: 0.25 }
    ]),
    triggerSync: vi.fn().mockResolvedValue({ message: "Sync gestartet" })
  } as unknown as ApiClient;
}

describe("DashboardPage", () => {
  it("preserves dashboard information while establishing the new hierarchy", async () => {
    const user = userEvent.setup();
    render(<DashboardPage apiClient={apiClient()} hasApiToken />);

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

    expect(screen.getByRole("status")).toHaveTextContent("Sync bereit");
    expect(screen.getByRole("status")).toHaveTextContent("Entfernt: 2");
    expect(screen.getByRole("status")).toHaveTextContent("Fehler: 1");
    expect(screen.getByText("Lebensmittel")).toBeInTheDocument();
    expect(screen.getByText("REWE")).toBeInTheDocument();
    expect(screen.getAllByText("PAYBACK")).toHaveLength(1);
    expect(screen.getByText("+3 / -2")).toBeInTheDocument();
    expect(screen.getByRole("combobox")).toHaveValue("currentMonth");

    await user.click(screen.getByText("Ohne Kategorie"));
    expect(window.location.hash).toBe("#/search?uncategorizedOnly=true");
  });
});
