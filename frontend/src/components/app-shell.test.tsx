import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { Home, ReceiptText } from "lucide-react";
import { describe, expect, it, vi } from "vitest";

import { AppShell } from "@/components/app-shell";

const navigation = [
  { href: "#/", label: "Dashboard", icon: Home },
  { href: "#/receipts", label: "Bons", icon: ReceiptText }
];

function ControlledAppShell({ onTokenChange }: { onTokenChange: (token: string) => void }) {
  const [apiToken, setApiToken] = useState("existing");

  function handleTokenChange(token: string) {
    setApiToken(token);
    onTokenChange(token);
  }

  return (
    <AppShell apiToken={apiToken} navigation={navigation} onTokenChange={handleTokenChange} route="/receipts/42?tab=items">
      <p>Receipt content</p>
    </AppShell>
  );
}

describe("AppShell", () => {
  it("marks receipt detail routes as active and forwards token edits", async () => {
    const user = userEvent.setup();
    const onTokenChange = vi.fn();

    render(<ControlledAppShell onTokenChange={onTokenChange} />);

    expect(screen.getByRole("heading", { name: "Bons" })).toBeInTheDocument();
    expect(screen.getByText("Receipt content")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Bons" })).toHaveLength(2);
    for (const activeLink of screen.getAllByRole("link", { name: "Bons" })) {
      expect(activeLink).toHaveAttribute("aria-current", "page");
    }

    await user.clear(screen.getByLabelText("API-Token"));
    await user.type(screen.getByLabelText("API-Token"), "new-token");
    expect(onTokenChange).toHaveBeenLastCalledWith("new-token");

    await user.click(screen.getByRole("button", { name: "Leeren" }));
    expect(onTokenChange).toHaveBeenLastCalledWith("");
  });

  it("uses the matching navigation title for a top-level route", () => {
    render(
      <AppShell apiToken="" navigation={navigation} onTokenChange={vi.fn()} route="/">
        <p>Dashboard content</p>
      </AppShell>
    );

    expect(screen.getByRole("heading", { name: "Dashboard" })).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "Dashboard" })[0]).toHaveAttribute("aria-current", "page");
  });
});
