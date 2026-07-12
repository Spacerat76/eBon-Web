import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ProductsPage } from "@/pages/products-page";
import type { ApiClient } from "@/lib/api";
import type { PageResponse, ProductPriceObservationDTO, ProductPriceReportDTO, ProductReviewItemDTO, SearchResultDTO } from "@/lib/types";

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
  regularPrice: 2.29,
  normalizedUnitPrice: 1.99,
  normalizedUnit: "l",
  includedInComparison: true,
  outlier: true,
  excluded: false,
  exclusionReason: null
};

const excludedPriceObservation: ProductPriceObservationDTO = {
  ...priceObservation,
  receiptItemId: 45,
  description: "Haferdrink Barista doppelt",
  includedInComparison: false,
  excluded: true,
  exclusionReason: "Doppelt erfasst"
};

const changePreview = {
  affectedItemsCount: 3,
  affectedStores: ["dm", "REWE"],
  dateFrom: "2026-05-01",
  dateTo: "2026-06-20",
  previousProductFamilyId: 10,
  previousProductFamilyName: "Haferdrink",
  newProductFamilyId: 11,
  newProductFamilyName: "Pflanzendrink",
  previousProductVariantId: null,
  previousProductVariantName: null,
  newProductVariantId: null,
  newProductVariantName: null,
  reportImpact: "Preisreports werden für die Zielstruktur neu berechnet."
};

const assignedSearchResult: SearchResultDTO = {
  receiptId: 9,
  receiptItemId: 44,
  receiptDate: "2026-06-20",
  storeName: "dm",
  description: "Haferdrink bestätigt",
  totalPrice: 1.79,
  categoryId: 2,
  categoryName: "Milchprodukte und Eier",
  highlights: [],
  productFamilyId: 10,
  productFamilyName: "Haferdrink",
  productVariantId: null,
  productVariantName: null,
  productAssignmentSource: "MANUAL",
  productAssignmentStatus: "CONFIRMED",
  normalizedUnitPrice: 1.79,
  normalizedUnit: "l"
};

function searchPage(content: SearchResultDTO[], page: number, totalPages: number): PageResponse<SearchResultDTO> {
  return { content, page, size: 20, totalElements: totalPages, totalPages, sortBy: "receiptDate", sortDir: "desc" };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((nextResolve, nextReject) => {
    resolve = nextResolve;
    reject = nextReject;
  });
  return { promise, reject, resolve };
}

function apiClient(options: {
  reviewItems?: ProductReviewItemDTO[];
  families?: Awaited<ReturnType<ApiClient["productFamilies"]>>;
  variants?: Awaited<ReturnType<ApiClient["productVariants"]>>;
  rules?: Awaited<ReturnType<ApiClient["productRules"]>>;
  priceObservations?: ProductPriceObservationDTO[];
  searchResults?: PageResponse<SearchResultDTO>[];
} = {}) {
  const families = options.families ?? [{ id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 1, assignedItemsCount: 42, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }];
  const variants = options.variants ?? [{ id: 20, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 1 l", unitQuantity: 1, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 1, totalUnit: "l", gtin: null, isActive: true, assignedItemsCount: 18 }];
  const search = vi.fn();
  (options.searchResults ?? [searchPage([assignedSearchResult], 0, 1)]).forEach((result) => search.mockResolvedValueOnce(result));
  return {
    productReview: vi.fn().mockResolvedValue({ content: options.reviewItems ?? [reviewItem], page: 0, size: 30, totalElements: options.reviewItems?.length ?? 1, totalPages: 1, sortBy: "reviewPriority", sortDir: "desc" }),
    productFamilies: vi.fn().mockResolvedValue(families),
    productVariants: vi.fn().mockResolvedValue(variants),
    productRules: vi.fn().mockResolvedValue(options.rules ?? []),
    categories: vi.fn().mockResolvedValue([{ id: 2, name: "Milchprodukte und Eier", colorHex: "#00838F", icon: "milk", isActive: true, sortOrder: 1, assignedItemsCount: 1 }]),
    createProductFamily: vi.fn().mockResolvedValue(families[0]),
    updateProductFamily: vi.fn().mockResolvedValue(families[0]),
    createProductVariant: vi.fn().mockResolvedValue(variants[0]),
    updateProductVariant: vi.fn().mockResolvedValue(variants[0]),
    acceptProductReview: vi.fn().mockResolvedValue({ ...reviewItem, assignmentSource: "MANUAL", assignmentStatus: "CONFIRMED" }),
    correctProductReview: vi.fn().mockResolvedValue({ ...reviewItem, assignmentSource: "MANUAL", assignmentStatus: "CONFIRMED" }),
    productFamilyPrices: vi.fn().mockResolvedValue(priceReport),
    productFamilyPriceObservations: vi.fn().mockResolvedValue({ content: options.priceObservations ?? [priceObservation, excludedPriceObservation], page: 0, size: 50, totalElements: options.priceObservations?.length ?? 2, totalPages: 1, sortBy: "receiptDate", sortDir: "desc" }),
    excludeProductPriceObservation: vi.fn().mockResolvedValue({ ...priceObservation, excluded: true, includedInComparison: false, exclusionReason: "Doppelt erfasst" }),
    includeProductPriceObservation: vi.fn().mockResolvedValue(priceObservation),
    previewProductFamilyMerge: vi.fn().mockResolvedValue(changePreview),
    applyProductFamilyMerge: vi.fn().mockResolvedValue(changePreview),
    previewProductFamilySplit: vi.fn().mockResolvedValue({ ...changePreview, affectedItemsCount: 1 }),
    applyProductFamilySplit: vi.fn().mockResolvedValue({ ...changePreview, affectedItemsCount: 1 }),
    search
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
    expect(screen.getByText("3 passende offene Positionen inkl. dieser")).toBeInTheDocument();
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
        { id: 6, name: "Fluconazol Accord 50 mg", defaultCategoryId: 6, defaultCategoryName: "Gesundheit", isActive: true, variantCount: 0, assignedItemsCount: 0, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 11, name: "Filetraeucherling", defaultCategoryId: 1, defaultCategoryName: "Fleisch und Wurst", isActive: true, variantCount: 0, assignedItemsCount: 0, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
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
        { id: 6, name: "Fluconazol Accord 50 mg", defaultCategoryId: 6, defaultCategoryName: "Gesundheit", isActive: true, variantCount: 0, assignedItemsCount: 0, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 1, assignedItemsCount: 42, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
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

  it("closes product dialogs with Escape and restores trigger focus", async () => {
    const user = userEvent.setup();
    render(<ProductsPage apiClient={apiClient()} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    const trigger = screen.getByRole("button", { name: "Korrigieren" });
    await user.click(trigger);
    expect(screen.getByRole("dialog", { name: "Produktzuordnung prüfen" })).toBeInTheDocument();

    await user.keyboard("{Escape}");

    expect(screen.queryByRole("dialog", { name: "Produktzuordnung prüfen" })).not.toBeInTheDocument();
    expect(trigger).toHaveFocus();
  });

  it("separates family, variant, rule, and structure master data into focused tabs", async () => {
    const user = userEvent.setup();
    render(<ProductsPage apiClient={apiClient()} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Familien" }));

    expect(screen.getByRole("heading", { name: "Produktfamilien" })).toBeInTheDocument();
    expect(screen.getAllByText("Haferdrink").length).toBeGreaterThan(0);
    expect(screen.getByText(/1 Variante · 42 Zuordnungen/)).toBeInTheDocument();
    expect(screen.getByText("Kategorie: Milchprodukte und Eier")).toBeInTheDocument();
    expect(screen.getByText("aktiv")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Produktvarianten" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Produktregeln" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Varianten" }));
    expect(screen.getByRole("heading", { name: "Produktvarianten" })).toBeInTheDocument();
    await user.selectOptions(screen.getByLabelText("Variantenfamilie"), "10");
    expect(screen.getByText("Haferdrink 1 l")).toBeInTheDocument();
    expect(screen.getByText(/Gesamtmenge 1 l · 18 Zuordnungen/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Produktfamilien" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Regeln" }));
    expect(screen.getByRole("heading", { name: "Produktregeln" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Familien zusammenführen" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    expect(screen.getByRole("heading", { name: "Familien zusammenführen" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Varianten zusammenführen" })).toBeInTheDocument();
    expect(screen.getByText(/Manuell bestätigte Zuordnungen bleiben geschützt/)).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "Produktregeln" })).not.toBeInTheDocument();
  });

  it("edits all product family master data fields", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Familien" }));
    await user.click(screen.getByRole("button", { name: "Familie Haferdrink bearbeiten" }));
    await user.clear(screen.getByLabelText("Name der Produktfamilie"));
    await user.type(screen.getByLabelText("Name der Produktfamilie"), "Haferdrink Bio");
    await user.selectOptions(screen.getByLabelText("Standardkategorie"), "");
    await user.click(screen.getByLabelText("Produktfamilie aktiv"));
    await user.click(screen.getByRole("button", { name: "Familie aktualisieren" }));

    await waitFor(() => expect(api.updateProductFamily).toHaveBeenCalledWith(10, {
      name: "Haferdrink Bio",
      defaultCategoryId: null,
      isActive: false
    }));
  });

  it("announces product master-data mutation failures without an unhandled rejection", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    vi.mocked(api.createProductFamily).mockRejectedValueOnce(new Error("Familie konnte nicht gespeichert werden"));
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Familien" }));
    await user.type(screen.getByLabelText("Neue Produktfamilie"), "Fehlerfamilie");
    await user.click(screen.getByRole("button", { name: "Anlegen" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Familie konnte nicht gespeichert werden");
  });

  it("gives every repeated product administration action an object-specific accessible name", async () => {
    const user = userEvent.setup();
    const api = apiClient({
      families: [
        { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 2, assignedItemsCount: 42, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 11, name: "Pflanzendrink", defaultCategoryId: null, defaultCategoryName: null, isActive: false, variantCount: 0, assignedItemsCount: 0, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
      ],
      variants: [
        { id: 20, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 1 l", unitQuantity: 1, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 1, totalUnit: "l", gtin: null, isActive: true, assignedItemsCount: 18 },
        { id: 21, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 0,5 l", unitQuantity: 0.5, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 0.5, totalUnit: "l", gtin: null, isActive: false, assignedItemsCount: 4 }
      ],
      rules: [
        { id: 30, productFamilyId: 10, productFamilyName: "Haferdrink", productVariantId: 20, productVariantName: "Haferdrink 1 l", storeName: "dm", matchType: "EXACT", matchValue: "Haferdrink Barista", priority: 100, isActive: true },
        { id: 31, productFamilyId: 10, productFamilyName: "Haferdrink", productVariantId: 21, productVariantName: "Haferdrink 0,5 l", storeName: null, matchType: "CONTAINS", matchValue: "Haferdrink Natur", priority: 110, isActive: false }
      ]
    });
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Familien" }));
    expect(screen.getByRole("button", { name: "Familie Haferdrink deaktivieren" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Familie Pflanzendrink aktivieren" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Deaktivieren" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Aktivieren" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Varianten" }));
    await user.selectOptions(screen.getByLabelText("Variantenfamilie"), "10");
    expect(screen.getByRole("button", { name: "Variante Haferdrink: Haferdrink 1 l bearbeiten" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Variante Haferdrink: Haferdrink 1 l deaktivieren" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Variante Haferdrink: Haferdrink 0,5 l bearbeiten" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Variante Haferdrink: Haferdrink 0,5 l aktivieren" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Bearbeiten" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Deaktivieren" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("tab", { name: "Regeln" }));
    expect(screen.getByRole("button", { name: "Produktregel Haferdrink Barista für dm deaktivieren" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Produktregel Haferdrink Barista für dm anwenden" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Produktregel Haferdrink Natur für alle Geschäfte aktivieren" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Produktregel Haferdrink Natur für alle Geschäfte anwenden" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Anwenden" })).not.toBeInTheDocument();
  });

  it("keeps merge and split previews explicit about impact and protected assignments", async () => {
    const user = userEvent.setup();
    const api = apiClient({
      families: [
        { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 1, assignedItemsCount: 42, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 11, name: "Pflanzendrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 0, assignedItemsCount: 5, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
      ]
    });
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    const familyMerge = screen.getByRole("heading", { name: "Familien zusammenführen" }).closest("section")!;
    await user.selectOptions(within(familyMerge).getByLabelText("Quellfamilie"), "10");
    await user.selectOptions(within(familyMerge).getByLabelText("Zielfamilie"), "11");
    await user.click(within(familyMerge).getByRole("button", { name: "Vorschau berechnen" }));
    expect(await within(familyMerge).findByText("3 Positionen in 2 Stores werden geändert.")).toBeInTheDocument();
    expect(within(familyMerge).getByText("dm, REWE")).toBeInTheDocument();
    expect(within(familyMerge).getByText("01.05.2026 – 20.06.2026")).toBeInTheDocument();
    expect(within(familyMerge).getByText(changePreview.reportImpact)).toBeInTheDocument();

    expect(await screen.findByText("Haferdrink bestätigt")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Position Haferdrink bestätigt aus Bon 9 (Position 44) trennen" }));
    const splitDialog = screen.getByRole("dialog");
    await user.type(within(splitDialog).getByLabelText("Neuer Produktname"), "Haferdrink Spezial");
    await user.click(within(splitDialog).getByRole("button", { name: "Vorschau berechnen" }));
    expect(await within(splitDialog).findByText("1 Position wird umgehängt.")).toBeInTheDocument();
    expect(within(splitDialog).getByText("dm, REWE")).toBeInTheDocument();
    expect(within(splitDialog).getByText("01.05.2026 – 20.06.2026")).toBeInTheDocument();
    expect(within(splitDialog).getByText(changePreview.reportImpact)).toBeInTheDocument();
    expect(within(splitDialog).getByText(/geschützte Zuordnungen/)).toBeInTheDocument();
  });

  it("invalidates a family merge preview when its source or target changes", async () => {
    const user = userEvent.setup();
    const api = apiClient({
      families: [
        { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 1, assignedItemsCount: 42, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 11, name: "Pflanzendrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 0, assignedItemsCount: 5, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 12, name: "Sojadrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 0, assignedItemsCount: 2, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
      ]
    });
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    const familyMerge = screen.getByRole("heading", { name: "Familien zusammenführen" }).closest("section")!;
    await user.selectOptions(within(familyMerge).getByLabelText("Quellfamilie"), "10");
    await user.selectOptions(within(familyMerge).getByLabelText("Zielfamilie"), "11");
    await user.click(within(familyMerge).getByRole("button", { name: "Vorschau berechnen" }));
    expect(await within(familyMerge).findByRole("button", { name: "Zusammenführen bestätigen" })).toBeInTheDocument();

    await user.selectOptions(within(familyMerge).getByLabelText("Zielfamilie"), "12");

    expect(within(familyMerge).queryByRole("button", { name: "Zusammenführen bestätigen" })).not.toBeInTheDocument();
    expect(within(familyMerge).getByRole("button", { name: "Vorschau berechnen" })).toBeInTheDocument();
    expect(api.applyProductFamilyMerge).not.toHaveBeenCalled();
  });

  it("ignores a stale merge preview response after the request inputs change", async () => {
    const user = userEvent.setup();
    const pending = deferred<typeof changePreview>();
    const api = apiClient({
      families: [
        { id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 1, assignedItemsCount: 42, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 11, name: "Pflanzendrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 0, assignedItemsCount: 5, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" },
        { id: 12, name: "Sojadrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, variantCount: 0, assignedItemsCount: 2, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }
      ]
    });
    vi.mocked(api.previewProductFamilyMerge).mockReturnValueOnce(pending.promise);
    render(<ProductsPage apiClient={api} hasApiToken />);
    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    const card = screen.getByRole("heading", { name: "Familien zusammenführen" }).closest("section")!;
    await user.selectOptions(within(card).getByLabelText("Quellfamilie"), "10");
    await user.selectOptions(within(card).getByLabelText("Zielfamilie"), "11");
    await user.click(within(card).getByRole("button", { name: "Vorschau berechnen" }));
    await user.selectOptions(within(card).getByLabelText("Zielfamilie"), "12");
    pending.resolve(changePreview);

    await waitFor(() => expect(api.previewProductFamilyMerge).toHaveBeenCalledTimes(1));
    expect(within(card).queryByRole("button", { name: "Zusammenführen bestätigen" })).not.toBeInTheDocument();
  });

  it("invalidates a split preview when the new product changes", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    await user.click(await screen.findByRole("button", { name: "Position Haferdrink bestätigt aus Bon 9 (Position 44) trennen" }));
    const dialog = screen.getByRole("dialog");
    const name = within(dialog).getByLabelText("Neuer Produktname");
    await user.type(name, "Haferdrink Spezial");
    await user.click(within(dialog).getByRole("button", { name: "Vorschau berechnen" }));
    expect(await within(dialog).findByRole("button", { name: "Trennung bestätigen" })).toBeEnabled();

    await user.type(name, " Neu");

    expect(within(dialog).getByRole("button", { name: "Trennung bestätigen" })).toBeDisabled();
    expect(within(dialog).getByRole("button", { name: "Vorschau berechnen" })).toBeInTheDocument();
    expect(api.applyProductFamilySplit).not.toHaveBeenCalled();
  });

  it("ignores a stale split preview response and reports preview errors", async () => {
    const user = userEvent.setup();
    const pending = deferred<typeof changePreview>();
    const api = apiClient();
    vi.mocked(api.previewProductFamilySplit).mockReturnValueOnce(pending.promise);
    render(<ProductsPage apiClient={api} hasApiToken />);
    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    await user.click(await screen.findByRole("button", { name: "Position Haferdrink bestätigt aus Bon 9 (Position 44) trennen" }));
    const dialog = screen.getByRole("dialog");
    const input = within(dialog).getByLabelText("Neuer Produktname");
    await user.type(input, "Haferdrink Spezial");
    await user.click(within(dialog).getByRole("button", { name: "Vorschau berechnen" }));
    expect(within(dialog).getByRole("button", { name: "Vorschau berechnen" })).toBeDisabled();
    await user.type(input, " Neu");
    pending.resolve(changePreview);
    await waitFor(() => expect(within(dialog).getByRole("button", { name: "Trennung bestätigen" })).toBeDisabled());

    vi.mocked(api.previewProductFamilySplit).mockRejectedValueOnce(new Error("Split-Vorschau fehlgeschlagen"));
    await user.click(within(dialog).getByRole("button", { name: "Vorschau berechnen" }));
    expect(await within(dialog).findByRole("alert")).toHaveTextContent("Split-Vorschau fehlgeschlagen");
  });

  it("disables master-data controls while a mutation is pending", async () => {
    const user = userEvent.setup();
    const pending = deferred<Awaited<ReturnType<ApiClient["createProductFamily"]>>>();
    const api = apiClient();
    vi.mocked(api.createProductFamily).mockReturnValueOnce(pending.promise);
    render(<ProductsPage apiClient={api} hasApiToken />);
    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Familien" }));
    await user.type(screen.getByLabelText("Neue Produktfamilie"), "Neue Familie");
    const create = screen.getByRole("button", { name: "Anlegen" });
    await user.click(create);
    expect(create).toBeDisabled();
    await user.click(create);
    expect(api.createProductFamily).toHaveBeenCalledTimes(1);
    pending.resolve((await api.productFamilies())[0]);
  });

  it("finds confirmed split candidates through paginated product search", async () => {
    const user = userEvent.setup();
    const secondResult = { ...assignedSearchResult, receiptItemId: 55, description: "Haferdrink nächste Seite" };
    const api = apiClient({ searchResults: [searchPage([assignedSearchResult], 0, 2), searchPage([secondResult], 1, 2)] });
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    expect(await screen.findByText("Haferdrink bestätigt")).toBeInTheDocument();
    expect(screen.getByText(/dm · Haferdrink · Bestätigt/)).toBeInTheDocument();
    expect(api.search).toHaveBeenCalledWith(expect.objectContaining({ productFamilyId: 10, page: 0, size: 20 }));

    await user.click(screen.getByRole("button", { name: "Weitere Positionen" }));
    expect(await screen.findByText("Haferdrink nächste Seite")).toBeInTheDocument();
    expect(api.search).toHaveBeenLastCalledWith(expect.objectContaining({ productFamilyId: 10, page: 1, size: 20 }));
  });

  it("gives identical split descriptions collision-free action names", async () => {
    const user = userEvent.setup();
    const secondResult = { ...assignedSearchResult, receiptId: 10, receiptItemId: 55 };
    const api = apiClient({ searchResults: [searchPage([assignedSearchResult, secondResult], 0, 1)] });
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Struktur" }));
    expect(await screen.findAllByText("Haferdrink bestätigt")).toHaveLength(2);
    expect(screen.getByRole("button", { name: "Position Haferdrink bestätigt aus Bon 9 (Position 44) trennen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Position Haferdrink bestätigt aus Bon 10 (Position 55) trennen" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Trennen" })).not.toBeInTheDocument();
  });

  it("shows complete price analysis and keeps exclusion reversible", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<ProductsPage apiClient={api} hasApiToken />);

    await screen.findByText("Haferdrink Barista");
    await user.click(screen.getByRole("tab", { name: "Preisvergleich" }));

    expect(await screen.findByText("Produktpreisvergleich")).toBeInTheDocument();
    expect(await screen.findByText("Letzter Preis (EUR/l)")).toBeInTheDocument();
    expect(await screen.findByText("Historisches Minimum (EUR/l)")).toBeInTheDocument();
    expect(screen.getAllByText("Durchschnitt").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Median").length).toBeGreaterThan(0);
    expect(screen.getAllByText("3 Beobachtungen").length).toBeGreaterThan(0);
    expect(screen.getByRole("heading", { name: "Preisverlauf" })).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Vergleich nach Geschäft" })).toBeInTheDocument();
    expect(screen.getAllByText("1,99 €").length).toBeGreaterThan(0);
    expect(screen.getAllByText("2,29 €").length).toBeGreaterThan(0);
    expect(screen.getAllByText("1,99 € / l").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Ausreißer").length).toBeGreaterThan(0);
    expect(screen.getByText("Doppelt erfasst")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Preisbeobachtung Haferdrink Barista doppelt vom 20.6.2026 bei dm (Position 45) wieder aufnehmen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Preisbeobachtung Haferdrink Barista vom 20.6.2026 bei dm (Position 44) ausschließen" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Wieder aufnehmen" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Ausschließen" })).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Preisbeobachtung Haferdrink Barista doppelt vom 20.6.2026 bei dm (Position 45) wieder aufnehmen" }));
    await waitFor(() => expect(api.includeProductPriceObservation).toHaveBeenCalledWith(45));

    await user.click(screen.getByRole("button", { name: "Preisbeobachtung Haferdrink Barista vom 20.6.2026 bei dm (Position 44) ausschließen" }));
    expect(screen.getByRole("dialog", { name: "Preisbeobachtung ausschließen" })).toBeInTheDocument();
    await user.type(screen.getByLabelText("Ausschlussgrund"), "Doppelt erfasst");
    await user.click(within(screen.getByRole("dialog")).getByRole("button", { name: "Ausschließen" }));

    await waitFor(() => expect(api.excludeProductPriceObservation).toHaveBeenCalledWith(44, "Doppelt erfasst"));
  });
});
