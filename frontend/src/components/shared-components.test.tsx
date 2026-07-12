import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { describe, expect, it, vi } from "vitest";
import { BarChart3, Boxes, Home, ReceiptText, Search, Settings, Tags } from "lucide-react";

import { AppShell, type NavigationItem } from "@/components/app-shell";
import { DataTableFrame, PaginationBar } from "@/components/data/data-table";
import { ActiveFilterChip, FilterBar } from "@/components/data/filter-bar";
import { StatusBanner } from "@/components/feedback/status-banner";
import { PageHeader } from "@/components/layout/page-header";
import { PageTabs } from "@/components/layout/page-tabs";
import { StickyActionBar } from "@/components/layout/sticky-action-bar";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { SecretInput } from "@/components/ui/secret-input";

describe("shared redesign components", () => {
  it("keeps the mobile bar focused and exposes every other destination through a focusable menu", async () => {
    const navigation: NavigationItem[] = [
      { href: "#/", label: "Übersicht", icon: Home, group: "workspace" },
      { href: "#/receipts", label: "Bons", icon: ReceiptText, group: "workspace" },
      { href: "#/search", label: "Suche", icon: Search, group: "workspace" },
      { href: "#/products", label: "Produkte", icon: Boxes, group: "workspace" },
      { href: "#/reports", label: "Berichte", icon: BarChart3, group: "workspace" },
      { href: "#/settings/categories", label: "Kategorien & Regeln", icon: Tags, group: "manage" },
      { href: "#/settings", label: "Einstellungen", icon: Settings, group: "manage" }
    ];
    render(
      <AppShell navigation={navigation} route="/">
        <p>Inhalt</p>
      </AppShell>
    );

    const mobileNavigation = screen.getByRole("navigation", { name: "Mobile Navigation" });
    expect(mobileNavigation.querySelectorAll("a")).toHaveLength(4);
    expect(screen.getByRole("button", { name: "Weitere Navigation öffnen" })).toBeVisible();

    await userEvent.click(screen.getByRole("button", { name: "Weitere Navigation öffnen" }));
    const menu = screen.getByRole("navigation", { name: "Weitere Navigation" });
    expect(menu).toBeVisible();
    expect(menu).toHaveFocus();
    expect(within(menu).getByRole("link", { name: "Berichte" })).toBeVisible();
    expect(within(menu).getByRole("link", { name: "Kategorien & Regeln" })).toBeVisible();
    expect(within(menu).getByRole("link", { name: "Einstellungen" })).toBeVisible();
  });

  it("keeps the shell title as the only main heading when a page has its own section header", () => {
    const navigation: NavigationItem[] = [
      { href: "#/", label: "Übersicht", icon: Home, group: "workspace" }
    ];
    render(
      <AppShell navigation={navigation} route="/">
        <PageHeader context="Übersicht / Finanzen" title="Finanzübersicht" />
      </AppShell>
    );

    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1 })).toHaveAccessibleName("Übersicht");
    expect(screen.getByRole("heading", { level: 2, name: "Finanzübersicht" })).toBeInTheDocument();
  });

  it("changes the active page tab through the typed callback", async () => {
    const onChange = vi.fn();
    render(
      <PageTabs
        active="items"
        onChange={onChange}
        tabs={[
          { id: "items", label: "Positionen" },
          { id: "raw", label: "Rohtext" }
        ]}
      />
    );
    await userEvent.click(screen.getByRole("tab", { name: "Rohtext" }));
    expect(onChange).toHaveBeenCalledWith("raw");
  });

  it("keeps save and cancel actions available in the sticky action bar", () => {
    render(<StickyActionBar message="3 Felder geändert" onCancel={vi.fn()} onSave={vi.fn()} saving={false} />);
    expect(screen.getByRole("button", { name: "Abbrechen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Änderungen speichern" })).toBeInTheDocument();
  });

  it("does not send the masked placeholder as a changed secret", async () => {
    const onChange = vi.fn();
    render(<SecretInput aria-label="API-Key" masked value="********" onChangeValue={onChange} />);
    expect(screen.getByLabelText("API-Key")).toHaveValue("");
    await userEvent.type(screen.getByLabelText("API-Key"), "new-secret");
    expect(onChange).toHaveBeenLastCalledWith("new-secret");
  });

  it("never reports the reserved mask when it is entered as a new secret", async () => {
    const onChange = vi.fn();
    render(<SecretInput aria-label="API-Key" masked value="********" onChangeValue={onChange} />);

    await userEvent.type(screen.getByLabelText("API-Key"), "********");

    expect(onChange).not.toHaveBeenCalledWith("********");
    expect(onChange).toHaveBeenLastCalledWith("");
    expect(screen.getByLabelText("API-Key")).toHaveValue("");
  });

  it("programmatically labels the confirmation dialog with its title", () => {
    render(<ConfirmDialog onCancel={vi.fn()} onConfirm={vi.fn()} open title="Änderung bestätigen" />);

    const dialog = screen.getByRole("dialog", { name: "Änderung bestätigen" });
    const title = screen.getByRole("heading", { name: "Änderung bestätigen" });
    expect(dialog).toHaveAttribute("aria-labelledby", title.id);
  });

  it("calls onCancel when Escape is pressed", async () => {
    const onCancel = vi.fn();
    render(<ConfirmDialog onCancel={onCancel} onConfirm={vi.fn()} open title="Änderung bestätigen" />);

    await userEvent.keyboard("{Escape}");

    expect(onCancel).toHaveBeenCalledOnce();
  });

  it("moves focus into the dialog and traps Tab in both directions", async () => {
    render(<ConfirmDialog onCancel={vi.fn()} onConfirm={vi.fn()} open title="Änderung bestätigen" />);
    const cancel = screen.getByRole("button", { name: "Abbrechen" });
    const confirm = screen.getByRole("button", { name: "Bestätigen" });

    expect(screen.getByRole("dialog")).toContainElement(document.activeElement as HTMLElement);
    cancel.focus();
    await userEvent.tab({ shift: true });
    expect(confirm).toHaveFocus();
    await userEvent.tab();
    expect(cancel).toHaveFocus();
  });

  it("returns focus to the opener after the dialog closes", async () => {
    function DialogHarness() {
      const [open, setOpen] = useState(false);
      return (
        <>
          <button onClick={() => setOpen(true)} type="button">
            Dialog öffnen
          </button>
          <ConfirmDialog onCancel={() => setOpen(false)} onConfirm={vi.fn()} open={open} title="Änderung bestätigen" />
        </>
      );
    }

    render(<DialogHarness />);
    const opener = screen.getByRole("button", { name: "Dialog öffnen" });
    await userEvent.click(opener);
    await userEvent.keyboard("{Escape}");

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
    expect(opener).toHaveFocus();
  });

  it("announces status content with the selected semantic role", () => {
    const { rerender } = render(<StatusBanner title="Synchronisiert">Alle Bons sind aktuell.</StatusBanner>);
    expect(screen.getByRole("status")).toHaveTextContent("Synchronisiert");
    expect(screen.getByRole("status")).toHaveTextContent("Alle Bons sind aktuell.");

    rerender(<StatusBanner title="Sync fehlgeschlagen" tone="error" />);
    expect(screen.getByRole("alert")).toHaveTextContent("Sync fehlgeschlagen");
  });

  it("groups filters and removes an active filter through its labeled action", async () => {
    const onRemove = vi.fn();
    render(
      <FilterBar>
        <label>
          Geschäft
          <input />
        </label>
        <ActiveFilterChip label="Geschäft: REWE" onRemove={onRemove} />
      </FilterBar>
    );

    expect(screen.getByLabelText("Geschäft")).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: "Geschäft: REWE entfernen" }));
    expect(onRemove).toHaveBeenCalledOnce();
  });

  it("moves through zero-based pages while disabling unavailable directions", async () => {
    const onPageChange = vi.fn();
    render(<PaginationBar onPageChange={onPageChange} page={0} totalPages={3} />);

    expect(screen.getByText("Seite 1 von 3")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Vorherige Seite" })).toBeDisabled();
    await userEvent.click(screen.getByRole("button", { name: "Nächste Seite" }));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  it("presents page context, actions, and tabular data through the shared frames", () => {
    render(
      <>
        <PageHeader actions={<button type="button">Bon hinzufügen</button>} context="Bons / Liste" title="Bons" />
        <DataTableFrame>
          <table>
            <tbody>
              <tr>
                <td>REWE</td>
              </tr>
            </tbody>
          </table>
        </DataTableFrame>
      </>
    );

    expect(screen.getByRole("heading", { name: "Bons" })).toBeInTheDocument();
    expect(screen.getByText("Bons / Liste")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Bon hinzufügen" })).toBeInTheDocument();
    expect(screen.getByRole("table")).toHaveTextContent("REWE");
  });
});
