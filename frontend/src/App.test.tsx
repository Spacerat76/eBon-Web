import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";
import { useUnsavedChanges } from "@/lib/unsaved-changes";

vi.mock("@/pages/dashboard-page", () => ({
  DashboardPage: ({ hasApiToken }: { hasApiToken: boolean }) => <p>Dashboard page: {String(hasApiToken)}</p>
}));

vi.mock("@/pages/receipts-page", () => ({
  ReceiptsPage: ({ selectedReceiptId }: { selectedReceiptId: number | null }) => {
    const dirty = selectedReceiptId === 42;
    useUnsavedChanges(dirty);
    return <p>Receipts page: {selectedReceiptId ?? "list"}</p>;
  }
}));

vi.mock("@/pages/search-page", () => ({
  SearchPage: ({ initialUncategorizedOnly }: { initialUncategorizedOnly: boolean }) => (
    <p>Search page: uncategorized={String(initialUncategorizedOnly)}</p>
  )
}));

vi.mock("@/pages/reports-page", () => ({
  ReportsPage: () => <p>Reports page</p>
}));

vi.mock("@/pages/settings-page", () => ({
  SettingsPage: () => <p>Settings page</p>
}));

vi.mock("@/pages/products-page", () => ({
  ProductsPage: () => <p>Products page</p>
}));

vi.mock("@/pages/placeholder-page", () => ({
  PlaceholderPage: ({ title }: { title: string }) => <p>Placeholder: {title}</p>
}));

function navigate(hash: string) {
  window.location.hash = hash;
  window.dispatchEvent(new HashChangeEvent("hashchange"));
}

describe("App routing and local token handling", () => {
  beforeEach(() => {
    sessionStorage.clear();
    window.location.hash = "#/";
  });

  it("renders the dashboard and persists a non-empty API token only for this browser session", async () => {
    const user = userEvent.setup();
    render(<App />);

    expect(await screen.findByText("Dashboard page: false")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Übersicht" })).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "API-Zugriff einrichten" }));
    await user.type(screen.getByLabelText("APP_API_TOKEN"), " local-token ");
    await user.click(screen.getByRole("button", { name: "Für diese Sitzung verwenden" }));
    expect(sessionStorage.getItem("ebon.sessionApiToken")).toBe("local-token");
    expect(await screen.findByText("Dashboard page: true")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "API-Zugriff aktiv" }));
    await user.click(screen.getByRole("button", { name: "Token entfernen" }));
    expect(sessionStorage.getItem("ebon.sessionApiToken")).toBeNull();
  });

  it("maps receipt, search, report, product, settings, and fallback hashes to the expected screen", async () => {
    render(<App />);

    navigate("#/receipts/41");
    expect(await screen.findByText("Receipts page: 41")).toBeInTheDocument();

    navigate("#/search?uncategorizedOnly=true");
    expect(await screen.findByText("Search page: uncategorized=true")).toBeInTheDocument();

    navigate("#/reports");
    expect(await screen.findByText("Reports page")).toBeInTheDocument();

    navigate("#/settings");
    expect(await screen.findByText("Settings page")).toBeInTheDocument();

    navigate("#/products");
    expect(await screen.findByText("Products page")).toBeInTheDocument();

    navigate("#unknown");
    expect(await screen.findByText("Placeholder: Übersicht")).toBeInTheDocument();
  });

  it("keeps the current route when dirty navigation is cancelled and follows it after confirmation", async () => {
    const user = userEvent.setup();
    window.location.hash = "#/receipts/42";
    render(<App />);
    expect(await screen.findByText("Receipts page: 42")).toBeInTheDocument();

    window.location.hash = "#/reports";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
    const dialog = await screen.findByRole("dialog", { name: "Ungespeicherte Änderungen verwerfen?" });
    expect(window.location.hash).toBe("#/receipts/42");
    await user.click(within(dialog).getByRole("button", { name: "Hier bleiben" }));
    expect(screen.getByText("Receipts page: 42")).toBeInTheDocument();

    window.location.hash = "#/reports";
    window.dispatchEvent(new HashChangeEvent("hashchange"));
    await user.click(await screen.findByRole("button", { name: "Änderungen verwerfen" }));
    await waitFor(() => expect(screen.getByText("Reports page")).toBeInTheDocument());
  });

  it.each([
    ["#/", "Übersicht"],
    ["#/receipts", "Bons"],
    ["#/search", "Suche"],
    ["#/products", "Produkte"],
    ["#/reports", "Berichte"],
    ["#/settings/categories", "Kategorien & Regeln"],
    ["#/settings", "Einstellungen"]
  ])("renders one canonical main heading for %s", async (hash, title) => {
    navigate(hash);
    render(<App />);

    const headings = await screen.findAllByRole("heading", { level: 1 });
    expect(headings).toHaveLength(1);
    expect(headings[0]).toHaveAccessibleName(title);
  });
});
