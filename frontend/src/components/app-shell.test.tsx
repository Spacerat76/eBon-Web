import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Boxes, Home, ReceiptText, Settings } from "lucide-react";
import { describe, expect, it, vi } from "vitest";

import { AppShell } from "@/components/app-shell";
import { SessionAccess } from "@/components/session-access";

const navigation = [
  { href: "#/", label: "Übersicht", icon: Home, group: "workspace" as const },
  { href: "#/receipts", label: "Bons", icon: ReceiptText, group: "workspace" as const },
  { href: "#/products", label: "Produkte", icon: Boxes, group: "workspace" as const, count: 12 },
  { href: "#/settings", label: "Einstellungen", icon: Settings, group: "manage" as const }
];

describe("AppShell", () => {
  it("groups navigation and marks nested receipt routes active", () => {
    render(
      <AppShell navigation={navigation} route="/receipts/42" utility={<button>Zugriff</button>}>
        <p>Bon</p>
      </AppShell>
    );

    expect(screen.getByText("Arbeitsbereich")).toBeInTheDocument();
    expect(screen.getByText("Verwalten")).toBeInTheDocument();
    for (const link of screen.getAllByRole("link", { name: "Bons" })) {
      expect(link).toHaveAttribute("aria-current", "page");
    }
    expect(screen.queryByLabelText("API-Token")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Zugriff" })).toBeInTheDocument();
  });

  it("shows accessible task counts in desktop and mobile navigation", () => {
    render(
      <AppShell navigation={navigation} route="/">
        <p>Übersicht</p>
      </AppShell>
    );

    const countBadges = screen.getAllByText("12");
    expect(countBadges).toHaveLength(2);
    for (const badge of countBadges) {
      expect(badge).toHaveAccessibleName("12 offene Aufgaben");
    }
  });

  it("keeps the session token entry reachable when no token exists", async () => {
    const onTokenChange = vi.fn();
    render(<SessionAccess apiToken="" onTokenChange={onTokenChange} />);

    await userEvent.click(screen.getByRole("button", { name: "API-Zugriff einrichten" }));
    await userEvent.type(screen.getByLabelText("APP_API_TOKEN"), "session-token");
    await userEvent.click(screen.getByRole("button", { name: "Für diese Sitzung verwenden" }));

    expect(onTokenChange).toHaveBeenCalledWith("session-token");
  });

  it("allows an active session token to be replaced or removed", async () => {
    const onTokenChange = vi.fn();
    render(<SessionAccess apiToken="existing-token" onTokenChange={onTokenChange} />);

    await userEvent.click(screen.getByRole("button", { name: "API-Zugriff aktiv" }));
    expect(screen.getByLabelText("APP_API_TOKEN")).toHaveValue("");

    await userEvent.type(screen.getByLabelText("APP_API_TOKEN"), "replacement-token");
    await userEvent.click(screen.getByRole("button", { name: "Für diese Sitzung verwenden" }));
    expect(onTokenChange).toHaveBeenCalledWith("replacement-token");

    await userEvent.click(screen.getByRole("button", { name: "API-Zugriff aktiv" }));
    await userEvent.click(screen.getByRole("button", { name: "Token entfernen" }));
    expect(onTokenChange).toHaveBeenLastCalledWith("");
  });
});
