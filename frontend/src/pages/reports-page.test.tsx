import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { ApiClient } from "@/lib/api";
import type { ReportFilters } from "@/lib/types";
import { ReportsPage } from "@/pages/reports-page";

function apiClient() {
  return {
    categories: vi.fn().mockResolvedValue([
      { id: 1, name: "Lebensmittel", colorHex: "#2563eb", icon: "basket", isActive: true, sortOrder: 1, assignedItemsCount: 4 },
      { id: 2, name: "Haushalt", colorHex: "#16a34a", icon: "home", isActive: true, sortOrder: 2, assignedItemsCount: 2 }
    ]),
    productFamilies: vi.fn().mockResolvedValue([
      { id: 10, name: "Haferdrink", defaultCategoryId: 1, defaultCategoryName: "Lebensmittel", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
    ]),
    productVariants: vi.fn().mockResolvedValue([
      { id: 20, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 1 l", unitQuantity: 1, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 1, totalUnit: "l", gtin: null, isActive: true }
    ]),
    reportByCategory: vi.fn().mockResolvedValue([
      { categoryId: 1, categoryName: "Lebensmittel", total: 42.5 }
    ]),
    reportByPeriod: vi.fn().mockResolvedValue([{ periodStart: "2026-06-01", period: "Juni 2026", total: 42.5 }]),
    reportByStore: vi.fn().mockResolvedValue([{ storeName: "REWE", total: 42.5, receiptCount: 2 }]),
    topItems: vi.fn().mockResolvedValue([{ description: "Haferdrink", total: 12.5, count: 5 }]),
    topProducts: vi.fn().mockResolvedValue([{ productFamilyId: 10, productFamilyName: "Haferdrink", total: 12.5, count: 5 }]),
    bonusReport: vi.fn().mockResolvedValue([{ bonusType: "PAYBACK", totalPoints: 20, totalEarnedBalance: 0.2 }]),
    downloadReportCsv: vi.fn().mockResolvedValue(new Blob(["csv"]))
  };
}

describe("ReportsPage", () => {
  beforeEach(() => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    vi.setSystemTime(new Date(2026, 6, 12, 12));
    vi.stubGlobal("URL", {
      ...URL,
      createObjectURL: vi.fn(() => "blob:report"),
      revokeObjectURL: vi.fn()
    });
    vi.spyOn(HTMLAnchorElement.prototype, "click").mockImplementation(() => undefined);
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("presents every report type as an accessible analysis tab", async () => {
    render(<ReportsPage apiClient={apiClient() as unknown as ApiClient} hasApiToken />);

    expect(await screen.findByRole("heading", { level: 1, name: "Reports" })).toBeInTheDocument();
    const tabList = screen.getByRole("tablist");
    for (const label of ["Kategorie", "Zeitraum", "Geschäft", "Top-Artikel", "Top-Produkte", "Bonus"]) {
      expect(within(tabList).getByRole("tab", { name: label })).toBeInTheDocument();
    }
    expect(within(tabList).getByRole("tab", { name: "Kategorie" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByRole("region", { name: "Diagramm" })).toBeInTheDocument();
    expect(screen.getByRole("table", { name: "Reportdaten" })).toBeInTheDocument();
  });

  it("keeps report loading and CSV export on the same active filter state", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const api = apiClient();
    render(<ReportsPage apiClient={api as unknown as ApiClient} hasApiToken />);

    await screen.findByText("42,50 €");
    await user.selectOptions(screen.getByLabelText("Zeitraum"), "custom");
    await user.clear(screen.getByLabelText("Von"));
    await user.type(screen.getByLabelText("Von"), "2026-04-01");
    await user.clear(screen.getByLabelText("Bis"));
    await user.type(screen.getByLabelText("Bis"), "2026-06-30");
    await user.selectOptions(screen.getByLabelText("Kategorien"), ["1", "2"]);
    await user.type(screen.getByLabelText("Geschäft"), "REWE");
    await user.selectOptions(screen.getByLabelText("Gruppe"), "week");
    await user.selectOptions(screen.getByLabelText("Produktfamilie"), "10");
    await user.selectOptions(screen.getByLabelText("Produktvariante"), "20");

    const expectedFilters: ReportFilters = {
      dateFrom: "2026-04-01",
      dateTo: "2026-06-30",
      categoryIds: [1, 2],
      store: "REWE",
      groupBy: "week",
      productFamilyId: 10,
      productVariantId: 20
    };
    await waitFor(() => expect(api.reportByCategory).toHaveBeenLastCalledWith(expectedFilters));

    await user.click(screen.getByRole("button", { name: "CSV exportieren" }));
    expect(api.downloadReportCsv).toHaveBeenCalledWith("by-category", expectedFilters);
  });

  it("retains report-specific grouping and top-product sorting controls", async () => {
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    const api = apiClient();
    render(<ReportsPage apiClient={api as unknown as ApiClient} hasApiToken />);

    await screen.findByText("42,50 €");
    await user.click(screen.getByRole("tab", { name: "Top-Produkte" }));
    await user.selectOptions(screen.getByLabelText("Top-Produkte sortieren nach"), "count");

    await waitFor(() => expect(api.topProducts).toHaveBeenLastCalledWith(expect.objectContaining({
      size: 20,
      topProductSort: "count"
    })));
    expect(screen.getByRole("option", { name: "Kaufhäufigkeit" })).toBeInTheDocument();
  });
});
