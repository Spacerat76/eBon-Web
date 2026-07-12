import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ProductsPage } from "@/pages/products-page";
import type { ApiClient } from "@/lib/api";
import type { ProductPriceObservationDTO, ProductPriceReportDTO, ProductReviewItemDTO } from "@/lib/types";

const reviewItem: ProductReviewItemDTO = {
  receiptItemId: 44,
  receiptId: 9,
  receiptDate: "2026-06-20",
  storeName: "dm",
  storeBranch: "Neuss",
  description: "Haferdrink Barista",
  quantity: 1,
  unit: "l",
  unitPrice: 1.79,
  totalPrice: 1.79,
  categoryId: 2,
  categoryName: "Milchprodukte und Eier",
  currentProductFamilyId: null,
  currentProductFamilyName: null,
  currentProductVariantId: null,
  currentProductVariantName: null,
  suggestedProductFamilyId: 10,
  suggestedProductFamilyName: "Haferdrink",
  suggestedProductVariantId: 20,
  suggestedProductVariantName: "Haferdrink 1 l",
  assignmentSource: "AI",
  assignmentStatus: "NEEDS_REVIEW",
  confidence: 0.72,
  reason: "LOW_CONFIDENCE",
  possibleRetroactiveItems: 3
};

const priceReport: ProductPriceReportDTO = {
  scope: "FAMILY",
  productFamilyId: 10,
  productFamilyName: "Haferdrink",
  productVariantId: null,
  productVariantName: null,
  primaryPriceBasis: "NORMALIZED_UNIT_PRICE",
  statistics: [{ priceUnit: "l", latestPrice: 1.99, latestReceiptDate: "2026-06-20", minimumPrice: 1.59, averagePrice: 1.79, medianPrice: 1.79, observationCount: 3 }],
  stores: [{ storeName: "REWE", storeBranch: null, label: "REWE", priceUnit: "l", latestPrice: 1.99, latestReceiptDate: "2026-06-20", minimumPrice: 1.59, averagePrice: 1.79, medianPrice: 1.79, observationCount: 3 }],
  trend: [{ receiptItemId: 44, receiptDate: "2026-06-20", label: "REWE", price: 1.99, priceUnit: "l", outlier: false }],
  variants: [{ productVariantId: 20, productVariantName: "Haferdrink 1 l", latestEffectivePrice: 1.99, minimumEffectivePrice: 1.59, observationCount: 3 }]
};

const priceObservation: ProductPriceObservationDTO = {
  receiptItemId: 44,
  receiptId: 9,
  receiptDate: "2026-06-20",
  storeName: "dm",
  storeBranch: "Neuss",
  description: "Haferdrink Barista",
  productFamilyId: 10,
  productFamilyName: "Haferdrink",
  productVariantId: 20,
  productVariantName: "Haferdrink 1 l",
  assignmentSource: "RULE",
  assignmentStatus: "AUTO_ASSIGNED",
  effectivePrice: 1.99,
  regularPrice: null,
  normalizedUnitPrice: 1.99,
  normalizedUnit: "l",
  includedInComparison: true,
  outlier: false,
  excluded: false,
  exclusionReason: null
};

function apiClient(options: {
  reviewItems?: ProductReviewItemDTO[];
  families?: Awaited<ReturnType<ApiClient["productFamilies"]>>;
  variants?: Awaited<ReturnType<ApiClient["productVariants"]>>;
} = {}) {
  const families = options.families ?? [{ id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }];
  const variants = options.variants ?? [{ id: 20, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 1 l", unitQuantity: 1, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 1, totalUnit: "l", gtin: null, isActive: true }];
  return {
    productReview: vi.fn().mockResolvedValue({ content: options.reviewItems ?? [reviewItem], page: 0, size: 30, totalElements: options.reviewItems?.length ?? 1, totalPages: 1, sortBy: "reviewPriority", sortDir: "desc" }),
    productFamilies: vi.fn().mockResolvedValue(families),
    productVariants: vi.fn().mockResolvedValue(variants),
    productRules: vi.fn().mockResolvedValue([]),
    categories: vi.fn().mockResolvedValue([{ id: 2, name: "Milchprodukte und Eier", colorHex: "#00838F", icon: "milk", isActive: true, sortOrder: 1, assignedItemsCount: 1 }]),
    acceptProductReview: vi.fn().mockResolvedValue({ ...reviewItem, assignmentSource: "MANUAL", assignmentStatus: "CONFIRMED" }),
    correctProductReview: vi.fn().mockResolvedValue({ ...reviewItem, assignmentSource: "MANUAL", assignmentStatus: "CONFIRMED" }),
    productFamilyPrices: vi.fn().mockResolvedValue(priceReport),
    productFamilyPriceObservations: vi.fn().mockResolvedValue({ content: [priceObservation], page: 0, size: 50, totalElements: 1, totalPages: 1, sortBy: "receiptDate", sortDir: "desc" }),
    excludeProductPriceObservation: vi.fn().mockResolvedValue({ ...priceObservation, excluded: true, includedInComparison: false, exclusionReason: "Doppelt erfasst" }),
    includeProductPriceObservation: vi.fn().mockResolvedValue(priceObservation)
  } as unknown as ApiClient;
}

describe("ProductsPage", () => {
  it("opens the focused review queue with count, receipt context, impact, and labeled decisions", async () => {
    render(<ProductsPage apiClient={apiClient()} hasApiToken />);

    const openTab = await screen.findByRole("tab", { name: "Offen" });
    expect(openTab).toHaveAttribute("aria-selected", "true");
    expect(within(openTab).getByText("1")).toBeInTheDocument();

    expect(await screen.findByRole("heading", { name: "Bon-Kontext" })).toBeInTheDocument();
    expect(screen.getByText("dm · Neuss")).toBeInTheDocument();
    expect(screen.getByText("3 ähnliche offene Positionen")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Übernehmen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Korrigieren" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Als kein Produkt markieren" })).toHaveTextContent("Kein Produkt");
    expect(screen.getByRole("button", { name: "Vorschlag ablehnen" })).toHaveTextContent("Ablehnen");
  });

  it("loads the review queue, applies filters, and confirms an AI proposal", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ProductsPage apiClient={api} hasApiToken />);

    expect(await screen.findByText("Haferdrink Barista")).toBeInTheDocument();
    expect(screen.getByText("LOW_CONFIDENCE")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Store filtern"), "dm");
    await user.click(screen.getByRole("button", { name: "Filtern" }));
    await waitFor(() => expect(api.productReview).toHaveBeenLastCalledWith(expect.objectContaining({ store: "dm", status: "NEEDS_REVIEW" })));

    await user.click(screen.getByRole("button", { name: "Übernehmen" }));
    await waitFor(() => expect(api.acceptProductReview).toHaveBeenCalledWith(44));
  });

  it("defaults missing-family review corrections to creating one family for matching store positions", async () => {
    const user = userEvent.setup();
    const filetraeucherlingReview: ProductReviewItemDTO = {
      ...reviewItem,
      description: "FILETRAEUCHERL.",
      quantity: 0.188,
      unit: "kg",
      unitPrice: null,
      totalPrice: 3.74,
      currentProductFamilyId: null,
      currentProductFamilyName: null,
      currentProductVariantId: null,
      currentProductVariantName: null,
      suggestedProductFamilyId: null,
      suggestedProductFamilyName: null,
      suggestedProductVariantId: null,
      suggestedProductVariantName: null,
      assignmentSource: null,
      confidence: null,
      possibleRetroactiveItems: 18
    };
    const api = apiClient({
      reviewItems: [filetraeucherlingReview],
      families: [
        { id: 6, name: "Fluconazol Accord 50 mg", defaultCategoryId: 6, defaultCategoryName: "Gesundheit", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 11, name: "Filetraeucherling", defaultCategoryId: 1, defaultCategoryName: "Fleisch und Wurst", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
      ],
      variants: []
    });
    render(<ProductsPage apiClient={api} hasApiToken />);

    expect(await screen.findByText("FILETRAEUCHERL.")).toBeInTheDocument();
    expect(screen.getByText("Produktfamilie offen")).toBeInTheDocument();
    expect(screen.queryByText("Variante offen")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Korrigieren" }));
    expect(await screen.findByRole("button", { name: "Neue Familie" })).toBeInTheDocument();
    expect(screen.getByLabelText("Name der neuen Produktfamilie")).toHaveValue("FILETRAEUCHERL.");
    expect(screen.getByLabelText(/Gleiche offene Positionen/)).toBeChecked();
    await user.click(screen.getByRole("button", { name: "Familie anlegen und zuordnen" }));

    await waitFor(() => expect(api.correctProductReview).toHaveBeenCalledWith(44, {
      newProductFamilyName: "FILETRAEUCHERL.",
      productVariantId: null,
      applyToSameStoreDescription: true
    }));
  });

  it("keeps existing-family correction available as an explicit alternative", async () => {
    const user = userEvent.setup();
    const api = apiClient({
      reviewItems: [{ ...reviewItem, suggestedProductFamilyId: null, suggestedProductFamilyName: null, suggestedProductVariantId: null, suggestedProductVariantName: null }],
      families: [
        { id: 6, name: "Fluconazol Accord 50 mg", defaultCategoryId: 6, defaultCategoryName: "Gesundheit", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
      ]
    });
    render(<ProductsPage apiClient={api} hasApiToken />);

    expect(await screen.findByText("Haferdrink Barista")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Korrigieren" }));
    await user.click(await screen.findByRole("button", { name: "Vorhandene Familie" }));
    const familySelect = screen.getByLabelText("Produktfamilie");
    expect(within(familySelect).queryByRole("option", { name: "Fluconazol Accord 50 mg" })).not.toBeInTheDocument();
    expect(within(familySelect).getByRole("option", { name: "Haferdrink" })).toBeInTheDocument();

    await user.selectOptions(familySelect, "10");
    await user.selectOptions(screen.getByLabelText("Produktvariante"), "20");
    await user.click(screen.getByRole("button", { name: "Zuordnung bestätigen" }));

    await waitFor(() => expect(api.correctProductReview).toHaveBeenCalledWith(44, {
      productFamilyId: 10,
      productVariantId: 20,
      applyToSameStoreDescription: true
    }));
  });

  it("shows normalized product prices and requires a reason before excluding an observation", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Preisvergleich" }));

    expect(await screen.findByText("Produktpreisvergleich")).toBeInTheDocument();
    expect(await screen.findByText("Historisches Minimum (EUR/l)")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Ausschließen" }));
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Ausschlussgrund"), "Doppelt erfasst");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Ausschließen" }));

    await waitFor(() => expect(api.excludeProductPriceObservation).toHaveBeenCalledWith(44, "Doppelt erfasst"));
  });
});
