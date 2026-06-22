import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import App from "@/App";

vi.mock("@/pages/dashboard-page", () => ({
  DashboardPage: ({ hasApiToken }: { hasApiToken: boolean }) => <p>Dashboard page: {String(hasApiToken)}</p>
}));

vi.mock("@/pages/receipts-page", () => ({
  ReceiptsPage: ({ selectedReceiptId }: { selectedReceiptId: number | null }) => <p>Receipts page: {selectedReceiptId ?? "list"}</p>
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

    await user.type(screen.getByLabelText("API-Token"), " local-token ");
    expect(sessionStorage.getItem("ebon.sessionApiToken")).toBe("local-token");
    expect(await screen.findByText("Dashboard page: true")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Leeren" }));
    expect(sessionStorage.getItem("ebon.sessionApiToken")).toBeNull();
  });

  it("maps receipt, search, report, settings, and fallback hashes to the expected screen", async () => {
    render(<App />);

    navigate("#/receipts/42");
    expect(await screen.findByText("Receipts page: 42")).toBeInTheDocument();

    navigate("#/search?uncategorizedOnly=true");
    expect(await screen.findByText("Search page: uncategorized=true")).toBeInTheDocument();

    navigate("#/reports");
    expect(await screen.findByText("Reports page")).toBeInTheDocument();

    navigate("#/settings");
    expect(await screen.findByText("Settings page")).toBeInTheDocument();

    navigate("#unknown");
    expect(await screen.findByText("Placeholder: Dashboard")).toBeInTheDocument();
  });
});
