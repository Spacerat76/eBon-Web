import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { ApiClient } from "@/lib/api";
import type { SettingsDTO } from "@/lib/types";
import { SettingsPage } from "@/pages/settings-page";

const maskedSettings: SettingsDTO = {
  paperlessBaseUrl: "http://paperless:8000",
  paperlessPublicBaseUrl: "https://paperless.example.test",
  paperlessDocumentUrlTemplate: null,
  paperlessApiToken: "********",
  paperlessEbonTag: "eBon",
  openRouterApiKey: "********",
  openRouterBaseUrl: "https://openrouter.ai/api/v1",
  openRouterModel: "openai/gpt-oss-20b",
  aiCategorizationMinConfidence: 0.9,
  aiParsingFallbackEnabled: true,
  aiParsingModel: "openai/gpt-oss-20b",
  aiParsingMaxTokens: 2500,
  aiParsingTemperature: 0,
  aiParsingMinConfidence: 0.9,
  aiParsingSyncCallLimit: 25,
  aiParsingTextMode: "MINIMIZED",
  aiParsingStoreDebugSnippets: false,
  syncIntervalMinutes: 60,
  currency: "EUR",
  productHistoryMinConfirmedMatches: 3,
  productHistoryMinVariantShare: 0.9
};

function apiClient() {
  return {
    settings: vi.fn().mockResolvedValue(maskedSettings),
    updateSettings: vi.fn().mockImplementation((request: SettingsDTO) => Promise.resolve({ ...maskedSettings, ...request })),
    testSettingsConnection: vi.fn().mockResolvedValue({ target: "PAPERLESS", success: true, message: "Verbindung erfolgreich." }),
    categories: vi.fn().mockResolvedValue([
      { id: 1, name: "Lebensmittel", colorHex: "#2563eb", icon: "basket", isActive: true, sortOrder: 1, assignedItemsCount: 2 }
    ]),
    categoryIcons: vi.fn().mockResolvedValue([{ value: "basket", label: "Einkaufskorb" }]),
    rules: vi.fn().mockResolvedValue([
      { id: 3, categoryId: 1, categoryName: "Lebensmittel", matchField: "DESCRIPTION", matchType: "CONTAINS", matchValue: "MILCH", priority: 10, isActive: true }
    ]),
    parseRuleSuggestions: vi.fn().mockResolvedValue({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    systemInfo: vi.fn().mockResolvedValue({ version: "1.7.0" }),
    reparseAllReceipts: vi.fn().mockResolvedValue({ message: "Neu geparst.", totalReceipts: 4, processedReceipts: 4, skippedManualReceipts: 0, deletedReceipts: 0, deletedSyncLogs: 0 }),
    resetImportedReceipts: vi.fn().mockResolvedValue({ message: "Bon-Daten gelöscht.", totalReceipts: 4, processedReceipts: 0, skippedManualReceipts: 0, deletedReceipts: 4, deletedSyncLogs: 2 }),
    resetProductData: vi.fn().mockResolvedValue({ message: "Produktdaten gelöscht.", clearedAssignments: 6, deletedAssignmentLogs: 4, deletedProductRules: 2, deletedProductVariants: 3, deletedProductFamilies: 2, clearedPriceExclusions: 1 }),
    downloadBackup: vi.fn().mockResolvedValue({ blob: new Blob(["zip"]), filename: "ebon-backup.zip" }),
    validateBackup: vi.fn().mockResolvedValue({ valid: true, manifestVersion: "1", errors: [], warnings: [], tables: [{ name: "receipts", recordCount: 4, valid: true }] }),
    restoreBackup: vi.fn().mockResolvedValue({ message: "Restore erfolgreich.", validation: { valid: true, manifestVersion: "1", errors: [], warnings: [], tables: [] } })
  };
}

describe("SettingsPage", () => {
  it("exposes all eight approved task-based sections", async () => {
    render(<SettingsPage apiClient={apiClient() as unknown as ApiClient} hasApiToken />);

    const tabList = await screen.findByRole("tablist");
    for (const label of ["Verbindungen", "KI & Parser", "Kategorien", "Kategorisierungsregeln", "Parser-Regelvorschläge", "Backup & Restore", "Datenwartung", "Systeminformationen"]) {
      expect(within(tabList).getByRole("tab", { name: label })).toBeInTheDocument();
    }

    const user = userEvent.setup();
    await user.click(within(tabList).getByRole("tab", { name: "Kategorien" }));
    expect(screen.getByRole("heading", { name: "Kategorien" })).toBeInTheDocument();
    await user.click(within(tabList).getByRole("tab", { name: "Kategorisierungsregeln" }));
    expect(screen.getByText("MILCH")).toBeInTheDocument();
    await user.click(within(tabList).getByRole("tab", { name: "Parser-Regelvorschläge" }));
    expect(screen.getByText("Keine Parser-Regelvorschläge")).toBeInTheDocument();
    await user.click(within(tabList).getByRole("tab", { name: "Systeminformationen" }));
    expect(screen.getByText("1.7.0")).toBeInTheDocument();
  });

  it("keeps masked secrets unchanged and tests both connections", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<SettingsPage apiClient={api as unknown as ApiClient} hasApiToken />);

    expect(await screen.findByLabelText("Paperless API-Token")).toHaveValue("");
    expect(screen.getByLabelText("Paperless API-Token")).toHaveAttribute("placeholder", "Unverändert");
    expect(screen.getByLabelText("OpenRouter API-Key")).toHaveValue("");

    await user.click(screen.getByRole("button", { name: "Paperless testen" }));
    expect(api.testSettingsConnection).toHaveBeenCalledWith("PAPERLESS");
    await user.click(screen.getByRole("button", { name: "OpenRouter testen" }));
    expect(api.testSettingsConnection).toHaveBeenCalledWith("OPENROUTER");

    await user.click(screen.getByRole("button", { name: "Speichern" }));
    await waitFor(() => expect(api.updateSettings).toHaveBeenCalled());
    const request = api.updateSettings.mock.calls[0][0] as Record<string, unknown>;
    expect(request).not.toHaveProperty("paperlessApiToken");
    expect(request).not.toHaveProperty("openRouterApiKey");
    expect(Object.values(request)).not.toContain("********");
  });

  it("keeps the complete AI parsing controls in their own section", async () => {
    const user = userEvent.setup();
    render(<SettingsPage apiClient={apiClient() as unknown as ApiClient} hasApiToken />);
    await user.click(await screen.findByRole("tab", { name: "KI & Parser" }));

    expect(screen.getByLabelText("KI-Parsing aktiv")).toBeChecked();
    expect(screen.getByLabelText("Lokale Debug-Snippets speichern")).not.toBeChecked();
    expect(screen.getByLabelText("Parsing-Modell")).toHaveValue("openai/gpt-oss-20b");
    expect(screen.getByLabelText("Parsing Max Tokens")).toHaveValue(2500);
    expect(screen.getByLabelText("Parsing Mindest-Konfidenz")).toHaveValue(0.9);
    expect(screen.getByLabelText("Sync-Call-Limit")).toHaveValue(25);
    expect(screen.getByLabelText("Textmodus")).toHaveValue("MINIMIZED");
    expect(screen.getByLabelText("Textmodus")).toHaveAttribute("aria-describedby", "ai-parsing-text-mode-help");
    expect(screen.getByText("FULL_TEXT überträgt den vollständigen Bontext an OpenRouter. Ein manueller Reparse mit FULL_TEXT erfordert eine zusätzliche Bestätigung.")).toBeInTheDocument();
  });

  it("registers unsaved settings with the global beforeunload guard and clears it after save", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<SettingsPage apiClient={api as unknown as ApiClient} hasApiToken />);
    const url = await screen.findByDisplayValue("http://paperless:8000");
    await user.type(url, "/changed");
    const dirtyEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(dirtyEvent);
    expect(dirtyEvent.defaultPrevented).toBe(true);

    await user.click(screen.getByRole("button", { name: "Speichern" }));
    await waitFor(() => expect(api.updateSettings).toHaveBeenCalled());
    const cleanEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(cleanEvent);
    expect(cleanEvent.defaultPrevented).toBe(false);
  });

  it("guards category, rule, and maintenance confirmation drafts as real unsaved input", async () => {
    const user = userEvent.setup();
    render(<SettingsPage apiClient={apiClient() as unknown as ApiClient} hasApiToken />);
    await user.click(await screen.findByRole("tab", { name: "Kategorien" }));
    const categoryCard = screen.getByRole("heading", { name: "Neue Kategorie" }).closest("section");
    await user.type(within(categoryCard as HTMLElement).getByRole("textbox"), "Neue Kategorie");
    const categoryEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(categoryEvent);
    expect(categoryEvent.defaultPrevented).toBe(true);

    await user.click(screen.getByRole("tab", { name: "Kategorisierungsregeln" }));
    const ruleCard = screen.getByRole("heading", { name: "Neue Regel" }).closest("section");
    await user.type(within(ruleCard as HTMLElement).getByRole("textbox"), "MILCH");
    const ruleEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(ruleEvent);
    expect(ruleEvent.defaultPrevented).toBe(true);

    await user.click(screen.getByRole("tab", { name: "Datenwartung" }));
    await user.type(screen.getByLabelText("Bon-Daten Bestätigung"), "DELETE");
    const confirmationEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(confirmationEvent);
    expect(confirmationEvent.defaultPrevented).toBe(true);
  });

  it("follows section changes from application navigation", async () => {
    const api = apiClient();
    const { rerender } = render(<SettingsPage apiClient={api as unknown as ApiClient} hasApiToken initialSection="connections" />);
    expect(await screen.findByRole("tab", { name: "Verbindungen" })).toHaveAttribute("aria-selected", "true");

    rerender(<SettingsPage apiClient={api as unknown as ApiClient} hasApiToken initialSection="categories" />);
    expect(screen.getByRole("tab", { name: "Kategorien" })).toHaveAttribute("aria-selected", "true");
  });

  it("requires a successful backup dry-run and the existing phrase before restore", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<SettingsPage apiClient={api as unknown as ApiClient} hasApiToken />);
    await user.click(await screen.findByRole("tab", { name: "Backup & Restore" }));

    const file = new File(["backup"], "backup.zip", { type: "application/zip" });
    await user.upload(screen.getByLabelText("Backup-ZIP"), file);
    await user.click(screen.getByRole("button", { name: "Dry-Run prüfen" }));
    await waitFor(() => expect(api.validateBackup).toHaveBeenCalledWith(file));
    expect(await screen.findByText("Dry-Run Ergebnis")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Restore-Bestätigung"), "RESTORE_BACKUP");
    await user.click(screen.getByRole("button", { name: "Backup wiederherstellen" }));
    await waitFor(() => expect(api.restoreBackup).toHaveBeenCalledWith(file));
  });

  it("keeps receipt and product resets separate with their established phrases and result summaries", async () => {
    const user = userEvent.setup();
    const api = apiClient();
    render(<SettingsPage apiClient={api as unknown as ApiClient} hasApiToken />);
    await user.click(await screen.findByRole("tab", { name: "Datenwartung" }));

    expect(screen.getByText("Importierte Bon-Daten zurücksetzen")).toBeInTheDocument();
    expect(screen.getByText("Produktdaten zurücksetzen")).toBeInTheDocument();

    await user.type(screen.getByLabelText("Bon-Daten Bestätigung"), "DELETE_IMPORTED_RECEIPTS");
    await user.click(screen.getByRole("button", { name: "Importierte Bon-Daten löschen" }));
    await user.click(screen.getByRole("button", { name: "Bon-Daten endgültig löschen" }));
    await waitFor(() => expect(api.resetImportedReceipts).toHaveBeenCalledWith("DELETE_IMPORTED_RECEIPTS"));
    expect(await screen.findByText(/Bon-Daten gelöscht/)).toBeInTheDocument();

    await user.type(screen.getByLabelText("Produktdaten Bestätigung"), "DELETE_PRODUCT_DATA");
    await user.click(screen.getByRole("button", { name: "Produktdaten löschen" }));
    await user.click(screen.getByRole("button", { name: "Produktdaten endgültig löschen" }));
    await waitFor(() => expect(api.resetProductData).toHaveBeenCalledWith("DELETE_PRODUCT_DATA"));
    expect(await screen.findByText(/6 Zuordnungen/)).toBeInTheDocument();
  });
});
