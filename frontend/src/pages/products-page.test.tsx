import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { ProductsPage } from "@/pages/products-page";
import type { ApiClient } from "@/lib/api";
import type { ProductReviewItemDTO } from "@/lib/types";

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

function apiClient() {
  return {
    productReview: vi.fn().mockResolvedValue({ content: [reviewItem], page: 0, size: 30, totalElements: 1, totalPages: 1, sortBy: "reviewPriority", sortDir: "desc" }),
    productFamilies: vi.fn().mockResolvedValue([{ id: 10, name: "Haferdrink", defaultCategoryId: 2, defaultCategoryName: "Milchprodukte und Eier", isActive: true, createdAt: "2026-06-01T00:00:00Z", updatedAt: "2026-06-01T00:00:00Z" }]),
    productVariants: vi.fn().mockResolvedValue([{ id: 20, productFamilyId: 10, productFamilyName: "Haferdrink", name: "Haferdrink 1 l", unitQuantity: 1, unit: "l", packageQuantity: 1, packageDescription: null, totalQuantity: 1, totalUnit: "l", gtin: null, isActive: true }]),
    productRules: vi.fn().mockResolvedValue([]),
    categories: vi.fn().mockResolvedValue([{ id: 2, name: "Milchprodukte und Eier", colorHex: "#00838F", icon: "milk", isActive: true, sortOrder: 1, assignedItemsCount: 1 }]),
    acceptProductReview: vi.fn().mockResolvedValue({ ...reviewItem, assignmentSource: "MANUAL", assignmentStatus: "CONFIRMED" })
  } as unknown as ApiClient;
}

describe("ProductsPage", () => {
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
});
