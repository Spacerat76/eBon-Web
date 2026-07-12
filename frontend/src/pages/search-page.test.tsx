import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { ApiClient } from "@/lib/api";
import { SearchPage } from "@/pages/search-page";

function apiClient() {
  return {
    categories: vi.fn().mockResolvedValue([
      { id: 4, name: "Getränke", colorHex: "#2563eb", icon: "cup-soda", sortOrder: 1, isActive: true }
    ]),
    productFamilies: vi.fn().mockResolvedValue([
      { id: 8, name: "Coca Cola Zero", defaultCategoryId: 4, defaultCategoryName: "Getränke", isActive: true, variantCount: 1, assignedItemsCount: 3 }
    ]),
    productVariants: vi.fn().mockResolvedValue([
      { id: 9, productFamilyId: 8, productFamilyName: "Coca Cola Zero", name: "0,5 l Flasche", unitQuantity: 0.5, unit: "l", packageQuantity: 1, packageDescription: "Flasche", totalQuantity: 0.5, gtin: null, isActive: true, assignedItemsCount: 2 }
    ]),
    search: vi.fn().mockResolvedValue({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
      sortBy: "receiptDate",
      sortDir: "desc"
    })
  };
}

describe("SearchPage", () => {
  it("submits every visible and advanced search filter and renders removable active-filter chips", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<SearchPage apiClient={api as unknown as ApiClient} hasApiToken />);

    await screen.findByText("Keine Treffer");
    await user.type(screen.getByLabelText("Suchtext"), "Milch");
    await user.type(screen.getByLabelText("Geschäft"), "REWE");
    await user.type(screen.getByLabelText("Von"), "2026-07-01");
    await user.type(screen.getByLabelText("Bis"), "2026-07-31");
    await user.click(screen.getByRole("button", { name: "Weitere Filter" }));
    await user.selectOptions(screen.getByLabelText("Kategorien"), "4");
    await user.selectOptions(screen.getByLabelText("Produktfamilie"), "8");
    await user.selectOptions(screen.getByLabelText("Produktvariante"), "9");
    await user.type(screen.getByLabelText("Betrag von"), "1");
    await user.type(screen.getByLabelText("Betrag bis"), "5");
    await user.click(screen.getByRole("checkbox", { name: "Ohne Kategorie" }));

    await waitFor(() => expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({
      q: "Milch",
      store: "REWE",
      dateFrom: "2026-07-01",
      dateTo: "2026-07-31",
      categoryIds: [4],
      productFamilyId: 8,
      productVariantId: 9,
      amountMin: 1,
      amountMax: 5,
      uncategorizedOnly: true,
      page: 0,
      size: 20,
      sortBy: "receiptDate",
      sortDir: "desc"
    })));

    for (const label of [
      "Suchtext: Milch",
      "Geschäft: REWE",
      "Von: 2026-07-01",
      "Bis: 2026-07-31",
      "Kategorie: Getränke",
      "Produktfamilie: Coca Cola Zero",
      "Produktvariante: 0,5 l Flasche",
      "Betrag von: 1",
      "Betrag bis: 5",
      "Ohne Kategorie"
    ]) {
      expect(screen.getByRole("button", { name: `${label} entfernen` })).toBeInTheDocument();
    }

    await user.click(screen.getByRole("button", { name: "Geschäft: REWE entfernen" }));
    await waitFor(() => expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({
      q: "Milch",
      store: undefined,
      categoryIds: [4],
      productFamilyId: 8,
      productVariantId: 9,
      uncategorizedOnly: true
    })));
  });
});
