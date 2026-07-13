import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { CheckCircle2, Download, FileCheck2, Loader2, Plus, RotateCcw, Save, Trash2, Upload } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Input } from "@/components/ui/input";
import { SecretInput } from "@/components/ui/secret-input";
import { Skeleton } from "@/components/ui/skeleton";
import { PageTabs } from "@/components/layout/page-tabs";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { CategoryIcon } from "@/lib/category-icons";
import { formatCurrency, formatDate, formatTime } from "@/lib/format";
import { useUnsavedChanges } from "@/lib/unsaved-changes";
import type {
  BackupValidationReportDTO,
  CategorizationRuleDTO,
  CategorizationRuleRequest,
  CategoryDTO,
  CategoryIconDTO,
  CategoryRequest,
  DataMaintenanceResultDTO,
  PageResponse,
  ParseRuleSuggestionDTO,
  ParseRuleSuggestionReceiptContextDTO,
  ProductDataResetResultDTO,
  RuleMatchField,
  RuleMatchType,
  ReparseScope,
  SettingsDTO,
  SystemInfoDTO
} from "@/lib/types";

interface SettingsPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
  initialSection?: SettingsSection;
}

export type SettingsSection = "connections" | "ai-parser" | "categories" | "rules" | "parser-suggestions" | "backup" | "maintenance" | "system";

const settingsSections: { id: SettingsSection; label: string }[] = [
  { id: "connections", label: "Verbindungen" },
  { id: "ai-parser", label: "KI & Parser" },
  { id: "categories", label: "Kategorien" },
  { id: "rules", label: "Kategorisierungsregeln" },
  { id: "parser-suggestions", label: "Parser-Regelvorschläge" },
  { id: "backup", label: "Backup & Restore" },
  { id: "maintenance", label: "Datenwartung" },
  { id: "system", label: "Systeminformationen" }
];

const RESTORE_CONFIRMATION = "RESTORE_BACKUP";

const emptySettings: SettingsDTO = {
  paperlessBaseUrl: "",
  paperlessPublicBaseUrl: "",
  paperlessDocumentUrlTemplate: "",
  paperlessApiToken: "",
  paperlessEbonTag: "",
  openRouterApiKey: "",
  openRouterBaseUrl: "",
  openRouterModel: "",
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

const emptyCategory: CategoryRequest = {
  name: "",
  colorHex: "#71717a",
  icon: "",
  sortOrder: 100,
  isActive: true
};

const emptyRule: CategorizationRuleRequest = {
  categoryId: 0,
  matchField: "DESCRIPTION",
  matchType: "CONTAINS",
  matchValue: "",
  storeName: null,
  priority: 100,
  isActive: true,
  applyToExisting: false
};

export function SettingsPage({ apiClient, hasApiToken, initialSection = "connections" }: SettingsPageProps) {
  const [section, setSection] = useState<SettingsSection>(initialSection);
  const [settings, setSettings] = useState<SettingsDTO>(emptySettings);
  const [savedSettings, setSavedSettings] = useState<SettingsDTO | null>(null);
  const [systemInfo, setSystemInfo] = useState<SystemInfoDTO | null>(null);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [categoryIcons, setCategoryIcons] = useState<CategoryIconDTO[]>([]);
  const [rules, setRules] = useState<CategorizationRuleDTO[]>([]);
  const [parserSuggestions, setParserSuggestions] = useState<PageResponse<ParseRuleSuggestionDTO> | null>(null);
  const [includeInactive, setIncludeInactive] = useState(false);
  const [categoryDraft, setCategoryDraft] = useState<CategoryRequest>(emptyCategory);
  const [savedCategoryDraft, setSavedCategoryDraft] = useState<CategoryRequest>(emptyCategory);
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [ruleDraft, setRuleDraft] = useState<CategorizationRuleRequest>(emptyRule);
  const [savedRuleDraft, setSavedRuleDraft] = useState<CategorizationRuleRequest>(emptyRule);
  const [editingRuleId, setEditingRuleId] = useState<number | null>(null);
  const [rulePreview, setRulePreview] = useState<number | null>(null);
  const [overwriteManualEdits, setOverwriteManualEdits] = useState(false);
  const [resetConfirmation, setResetConfirmation] = useState("");
  const [productResetConfirmation, setProductResetConfirmation] = useState("");
  const [resetDialog, setResetDialog] = useState<"receipts" | "products" | null>(null);
  const [backupFile, setBackupFile] = useState<File | null>(null);
  const [backupInputResetKey, setBackupInputResetKey] = useState(0);
  const [backupValidation, setBackupValidation] = useState<BackupValidationReportDTO | null>(null);
  const [restoreConfirmation, setRestoreConfirmation] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const activeCategories = useMemo(() => categories.filter((category) => category.isActive), [categories]);
  const hasUnsavedSettingsInput = Boolean(
    (savedSettings !== null && JSON.stringify(settings) !== JSON.stringify(savedSettings))
    || JSON.stringify(categoryDraft) !== JSON.stringify(savedCategoryDraft)
    || JSON.stringify(ruleDraft) !== JSON.stringify(savedRuleDraft)
    || resetConfirmation
    || productResetConfirmation
    || restoreConfirmation
    || backupFile
  );
  useUnsavedChanges(hasUnsavedSettingsInput);

  useEffect(() => {
    setSection(initialSection);
  }, [initialSection]);

  const loadSettings = useCallback(async () => {
    if (!hasApiToken) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const [settingsResponse, categoryResponse, ruleResponse, iconResponse, systemInfoResponse, parserSuggestionResponse] = await Promise.all([
        apiClient.settings(),
        apiClient.categories(includeInactive),
        apiClient.rules(),
        apiClient.categoryIcons(),
        apiClient.systemInfo(),
        apiClient.parseRuleSuggestions({ status: "" })
      ]);
      setSettings(settingsResponse);
      setSavedSettings(settingsResponse);
      setCategories(categoryResponse);
      setRules(ruleResponse);
      setCategoryIcons(iconResponse);
      setSystemInfo(systemInfoResponse);
      setParserSuggestions(parserSuggestionResponse);
      if (categoryResponse.length && ruleDraft.categoryId === 0) {
        const initializedRule = { ...ruleDraft, categoryId: categoryResponse[0].id };
        setRuleDraft(initializedRule);
        setSavedRuleDraft(initializedRule);
      }
    } catch (loadError) {
      setError(toUserMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [apiClient, hasApiToken, includeInactive, ruleDraft.categoryId]);

  useEffect(() => {
    void loadSettings();
  }, [loadSettings]);

  async function saveSettings() {
    setSaving(true);
    setError(null);
    setFeedback(null);

    try {
      const { paperlessApiToken, openRouterApiKey, ...nonSecretSettings } = settings;
      const changedPaperlessToken = changedSecret(paperlessApiToken ?? "");
      const changedOpenRouterKey = changedSecret(openRouterApiKey ?? "");
      const request = {
        ...nonSecretSettings,
        ...(changedPaperlessToken ? { paperlessApiToken: changedPaperlessToken } : {}),
        ...(changedOpenRouterKey ? { openRouterApiKey: changedOpenRouterKey } : {})
      } as SettingsDTO;
      const updated = await apiClient.updateSettings(request);
      setSettings(updated);
      setSavedSettings(updated);
      setFeedback("Einstellungen gespeichert.");
    } catch (saveError) {
      setError(toUserMessage(saveError));
    } finally {
      setSaving(false);
    }
  }

  async function testConnection(target: "PAPERLESS" | "OPENROUTER") {
    setSaving(true);
    setError(null);
    setFeedback(null);

    try {
      const response = await apiClient.testSettingsConnection(target);
      if (response.success) {
        setFeedback(response.message);
      } else {
        setError(response.message);
      }
    } catch (testError) {
      setError(toUserMessage(testError));
    } finally {
      setSaving(false);
    }
  }

  async function reparseAll() {
    await runMaintenance(() => apiClient.reparseAllReceipts(overwriteManualEdits));
  }

  async function resetImportedReceipts() {
    await runMaintenance(() => apiClient.resetImportedReceipts(resetConfirmation));
    setResetConfirmation("");
    setResetDialog(null);
  }

  async function resetProductData() {
    setSaving(true);
    setError(null);
    setFeedback(null);
    try {
      const result = await apiClient.resetProductData(productResetConfirmation);
      setFeedback(productResetSummary(result));
      setProductResetConfirmation("");
      setResetDialog(null);
    } catch (maintenanceError) {
      setError(toUserMessage(maintenanceError));
    } finally {
      setSaving(false);
    }
  }

  async function downloadBackup() {
    setSaving(true);
    setError(null);
    setFeedback(null);

    try {
      const backup = await apiClient.downloadBackup();
      saveBlob(backup.blob, backup.filename);
      setFeedback("Backup heruntergeladen. API-Schluessel sind darin nicht enthalten und muessen nach einem Restore neu gesetzt werden.");
    } catch (backupError) {
      setError(toUserMessage(backupError));
    } finally {
      setSaving(false);
    }
  }

  async function validateBackup() {
    if (!backupFile) {
      setError("Bitte waehle zuerst eine Backup-ZIP-Datei aus.");
      return;
    }

    setSaving(true);
    setError(null);
    setFeedback(null);

    try {
      const validation = await apiClient.validateBackup(backupFile);
      setBackupValidation(validation);
      if (validation.valid) {
        setFeedback("Backup-Dry-Run erfolgreich. Die Datenbank wurde dabei nicht veraendert.");
      } else {
        setError("Backup-Dry-Run fehlgeschlagen. Pruefe die Validierungsfehler.");
      }
    } catch (validationError) {
      setBackupValidation(null);
      setError(toUserMessage(validationError));
    } finally {
      setSaving(false);
    }
  }

  async function restoreBackup() {
    if (!backupFile) {
      setError("Bitte waehle zuerst eine Backup-ZIP-Datei aus.");
      return;
    }
    if (restoreConfirmation !== RESTORE_CONFIRMATION) {
      setError(`Gib zur Bestaetigung exakt ${RESTORE_CONFIRMATION} ein.`);
      return;
    }

    setSaving(true);
    setError(null);
    setFeedback(null);

    try {
      const result = await apiClient.restoreBackup(backupFile);
      setBackupFile(null);
      setBackupValidation(null);
      setRestoreConfirmation("");
      setBackupInputResetKey((current) => current + 1);
      setFeedback(`${result.message} Maskierte API-Schluessel muessen danach in den Einstellungen neu gesetzt werden.`);
      await loadSettings();
    } catch (restoreError) {
      setError(toUserMessage(restoreError));
    } finally {
      setSaving(false);
    }
  }

  function changeBackupFile(file: File | null) {
    setBackupFile(file);
    setBackupValidation(null);
    setRestoreConfirmation("");
    setFeedback(null);
    setError(null);
  }

  async function runMaintenance(action: () => Promise<DataMaintenanceResultDTO>) {
    setSaving(true);
    setError(null);
    setFeedback(null);

    try {
      const result = await action();
      setFeedback(`${result.message} Verarbeitet: ${result.processedReceipts}, übersprungen: ${result.skippedManualReceipts}, gelöscht: ${result.deletedReceipts}.`);
    } catch (maintenanceError) {
      setError(toUserMessage(maintenanceError));
    } finally {
      setSaving(false);
    }
  }

  async function saveCategory() {
    setSaving(true);
    setError(null);

    try {
      if (editingCategoryId == null) {
        await apiClient.createCategory(normalizeCategory(categoryDraft));
      } else {
        await apiClient.updateCategory(editingCategoryId, normalizeCategory(categoryDraft));
      }
      setCategoryDraft(emptyCategory);
      setSavedCategoryDraft(emptyCategory);
      setEditingCategoryId(null);
      await loadSettings();
      setFeedback("Kategorie gespeichert.");
    } catch (categoryError) {
      setError(toUserMessage(categoryError));
    } finally {
      setSaving(false);
    }
  }

  async function patchCategory(category: CategoryDTO, isActive: boolean) {
    setSaving(true);
    setError(null);
    try {
      await apiClient.patchCategory(category.id, { isActive });
      await loadSettings();
    } catch (patchError) {
      setError(toUserMessage(patchError));
    } finally {
      setSaving(false);
    }
  }

  async function deleteCategory(category: CategoryDTO) {
    setSaving(true);
    setError(null);
    try {
      const response = await apiClient.deleteCategory(category.id);
      await loadSettings();
      setFeedback(response.message);
    } catch (deleteError) {
      setError(toUserMessage(deleteError));
    } finally {
      setSaving(false);
    }
  }

  function editCategory(category: CategoryDTO) {
    setEditingCategoryId(category.id);
    const nextDraft = {
      name: category.name,
      colorHex: category.colorHex,
      icon: category.icon ?? "",
      sortOrder: category.sortOrder,
      isActive: category.isActive
    };
    setCategoryDraft(nextDraft);
    setSavedCategoryDraft(nextDraft);
  }

  async function saveRule() {
    setSaving(true);
    setError(null);
    setRulePreview(null);
    try {
      const request = normalizeRule(ruleDraft);
      if (editingRuleId == null) {
        await apiClient.createRule(request);
      } else {
        await apiClient.updateRule(editingRuleId, request);
      }
      const nextDraft = { ...emptyRule, categoryId: activeCategories[0]?.id ?? 0 };
      setRuleDraft(nextDraft);
      setSavedRuleDraft(nextDraft);
      setEditingRuleId(null);
      await loadSettings();
      setFeedback("Regel gespeichert.");
    } catch (ruleError) {
      setError(toUserMessage(ruleError));
    } finally {
      setSaving(false);
    }
  }

  async function previewRule() {
    setSaving(true);
    setError(null);
    try {
      const response = await apiClient.previewRule({
        categoryId: ruleDraft.categoryId || null,
        matchField: ruleDraft.matchField,
        matchType: ruleDraft.matchType,
        matchValue: ruleDraft.matchValue,
        storeName: ruleDraft.storeName
      });
      setRulePreview(response.matchingItemsCount);
    } catch (previewError) {
      setError(toUserMessage(previewError));
    } finally {
      setSaving(false);
    }
  }

  async function applyRule(rule: CategorizationRuleDTO) {
    setSaving(true);
    setError(null);
    try {
      const response = await apiClient.applyRule(rule.id);
      setFeedback(`${response.changedItemsCount} Positionen aktualisiert.`);
    } catch (applyError) {
      setError(toUserMessage(applyError));
    } finally {
      setSaving(false);
    }
  }

  async function deleteRule(rule: CategorizationRuleDTO) {
    setSaving(true);
    setError(null);
    try {
      await apiClient.deleteRule(rule.id);
      await loadSettings();
      setFeedback("Regel gelöscht.");
    } catch (deleteError) {
      setError(toUserMessage(deleteError));
    } finally {
      setSaving(false);
    }
  }

  async function acceptParserSuggestion(suggestion: ParseRuleSuggestionDTO, reparseScope: ReparseScope) {
    setSaving(true);
    setError(null);
    try {
      await apiClient.acceptParseRuleSuggestion(suggestion.id, {
        suggestion: {
          storeName: suggestion.storeName,
          ruleType: suggestion.ruleType,
          matchRegex: suggestion.matchRegex,
          extractGroup: suggestion.extractGroup,
          confidence: suggestion.confidence,
          problemDescription: suggestion.problemDescription,
          solutionRationale: suggestion.solutionRationale
        },
        reparseScope
      });
      await loadSettings();
      setFeedback("Parser-Regelvorschlag übernommen.");
    } catch (acceptError) {
      setError(toUserMessage(acceptError));
    } finally {
      setSaving(false);
    }
  }

  async function rejectParserSuggestion(suggestion: ParseRuleSuggestionDTO) {
    const reason = window.prompt("Ablehnungsgrund", "Nicht passend");
    if (!reason) {
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await apiClient.rejectParseRuleSuggestion(suggestion.id, reason);
      await loadSettings();
      setFeedback("Parser-Regelvorschlag abgelehnt.");
    } catch (rejectError) {
      setError(toUserMessage(rejectError));
    } finally {
      setSaving(false);
    }
  }

  async function exportParserRules() {
    setSaving(true);
    setError(null);
    try {
      const draft = await apiClient.exportParseRuleSuggestionMigration();
      setFeedback(`Migration erzeugt: ${draft.filename}`);
    } catch (exportError) {
      setError(toUserMessage(exportError));
    } finally {
      setSaving(false);
    }
  }

  function editRule(rule: CategorizationRuleDTO) {
    setEditingRuleId(rule.id);
    setRulePreview(null);
    const nextDraft = {
      categoryId: rule.categoryId,
      matchField: rule.matchField,
      matchType: rule.matchType,
      matchValue: rule.matchValue,
      storeName: rule.storeName ?? null,
      priority: rule.priority,
      isActive: rule.isActive,
      applyToExisting: false
    };
    setRuleDraft(nextDraft);
    setSavedRuleDraft(nextDraft);
  }

  if (!hasApiToken) {
    return (
      <Card>
        <CardContent className="flex min-h-72 flex-col items-center justify-center text-center">
          <h2 className="text-base font-semibold">API-Token erforderlich</h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Danach können Einstellungen geladen werden.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {error ? <ErrorBox message={error} /> : null}
      {feedback ? <SuccessBox message={feedback} /> : null}

      <PageTabs active={section} onChange={setSection} tabs={settingsSections} />

      {loading ? <Skeleton className="h-96 w-full" /> : null}
      {!loading && section === "connections" ? (
        <ConnectionSettings
          onSave={saveSettings}
          onSettingsChange={setSettings}
          onTestConnection={testConnection}
          saving={saving}
          settings={settings}
        />
      ) : null}
      {!loading && section === "ai-parser" ? <AiParserSettings onSave={saveSettings} onSettingsChange={setSettings} saving={saving} settings={settings} /> : null}
      {!loading && section === "categories" ? (
        <CategorySettings
          categoryDraft={categoryDraft}
          categoryIcons={categoryIcons}
          categories={categories}
          editingCategoryId={editingCategoryId}
          includeInactive={includeInactive}
          onCancelEdit={() => {
            setEditingCategoryId(null);
            setCategoryDraft(emptyCategory);
            setSavedCategoryDraft(emptyCategory);
          }}
          onCategoryDraftChange={setCategoryDraft}
          onDelete={deleteCategory}
          onEdit={editCategory}
          onIncludeInactiveChange={setIncludeInactive}
          onPatchActive={patchCategory}
          onSave={saveCategory}
          saving={saving}
        />
      ) : null}
      {!loading && section === "rules" ? (
        <RuleSettings
          categories={activeCategories}
          editingRuleId={editingRuleId}
          onApply={applyRule}
          onCancelEdit={() => {
            setEditingRuleId(null);
            setRulePreview(null);
            const nextDraft = { ...emptyRule, categoryId: activeCategories[0]?.id ?? 0 };
            setRuleDraft(nextDraft);
            setSavedRuleDraft(nextDraft);
          }}
          onDelete={deleteRule}
          onEdit={editRule}
          onPreview={previewRule}
          onRuleDraftChange={setRuleDraft}
          onSave={saveRule}
          previewCount={rulePreview}
          ruleDraft={ruleDraft}
          rules={rules}
          saving={saving}
        />
      ) : null}
      {!loading && section === "parser-suggestions" ? (
        <ParserSuggestionSettings
          onAccept={acceptParserSuggestion}
          onExport={exportParserRules}
          onLoadDetail={(id) => apiClient.parseRuleSuggestion(id)}
          onReject={rejectParserSuggestion}
          saving={saving}
          suggestions={parserSuggestions?.content ?? []}
        />
      ) : null}
      {!loading && section === "backup" ? (
        <BackupSettings
          backupFile={backupFile}
          fileInputResetKey={backupInputResetKey}
          confirmation={restoreConfirmation}
          onConfirmationChange={setRestoreConfirmation}
          onDownload={downloadBackup}
          onFileChange={changeBackupFile}
          onRestore={restoreBackup}
          onValidate={validateBackup}
          saving={saving}
          validation={backupValidation}
        />
      ) : null}
      {!loading && section === "maintenance" ? (
        <MaintenanceSettings
          onOverwriteManualEditsChange={setOverwriteManualEdits}
          onProductResetConfirmationChange={setProductResetConfirmation}
          onReparseAll={reparseAll}
          onRequestProductReset={() => setResetDialog("products")}
          onRequestReceiptReset={() => setResetDialog("receipts")}
          onResetConfirmationChange={setResetConfirmation}
          overwriteManualEdits={overwriteManualEdits}
          productResetConfirmation={productResetConfirmation}
          resetConfirmation={resetConfirmation}
          saving={saving}
        />
      ) : null}
      {!loading && section === "system" ? <SystemSettings systemInfo={systemInfo} /> : null}

      <ConfirmDialog
        confirmLabel={resetDialog === "products" ? "Produktdaten endgültig löschen" : "Bon-Daten endgültig löschen"}
        destructive
        onCancel={() => setResetDialog(null)}
        onConfirm={() => void (resetDialog === "products" ? resetProductData() : resetImportedReceipts())}
        open={resetDialog !== null}
        title={resetDialog === "products" ? "Produktdaten zurücksetzen?" : "Importierte Bon-Daten zurücksetzen?"}
      >
        Diese Aktion wird transaktional ausgeführt und kann nicht rückgängig gemacht werden.
      </ConfirmDialog>
    </div>
  );
}

function ConnectionSettings({ onSave, onSettingsChange, onTestConnection, saving, settings }: {
  onSave: () => void;
  onSettingsChange: (settings: SettingsDTO) => void;
  onTestConnection: (target: "PAPERLESS" | "OPENROUTER") => void;
  saving: boolean;
  settings: SettingsDTO;
}) {
  return (
    <div className="grid gap-4 xl:grid-cols-2">
      <Card>
        <CardHeader><CardTitle>Paperless-NGX</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <Field label="Paperless-NGX URL"><Input onChange={(event) => onSettingsChange({ ...settings, paperlessBaseUrl: event.target.value })} value={settings.paperlessBaseUrl ?? ""} /></Field>
          <Field label="Paperless API-Token" help="Maskierte Tokens bleiben unverändert, solange kein neuer Wert eingegeben wird.">
            <SecretInput aria-label="Paperless API-Token" masked={settings.paperlessApiToken === "********"} onChangeValue={(value) => onSettingsChange({ ...settings, paperlessApiToken: value })} value={settings.paperlessApiToken ?? ""} />
          </Field>
          <Field label="Paperless Web-URL"><Input onChange={(event) => onSettingsChange({ ...settings, paperlessPublicBaseUrl: event.target.value })} value={settings.paperlessPublicBaseUrl ?? ""} /></Field>
          <Field label="Dokument-URL-Vorlage"><Input onChange={(event) => onSettingsChange({ ...settings, paperlessDocumentUrlTemplate: event.target.value })} placeholder="http://paperless.local/documents/{paperlessDocumentId}/details" value={settings.paperlessDocumentUrlTemplate ?? ""} /></Field>
          <Field label="eBon Tag"><Input onChange={(event) => onSettingsChange({ ...settings, paperlessEbonTag: event.target.value })} value={settings.paperlessEbonTag ?? ""} /></Field>
          <Field label="Sync-Intervall Minuten"><Input min={1} onChange={(event) => onSettingsChange({ ...settings, syncIntervalMinutes: Number(event.target.value) })} type="number" value={settings.syncIntervalMinutes ?? 60} /></Field>
          <Button disabled={saving} onClick={() => onTestConnection("PAPERLESS")} variant="secondary">Paperless testen</Button>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>OpenRouter</CardTitle></CardHeader>
        <CardContent className="space-y-3">
          <Field label="OpenRouter API-Key" help="Der gespeicherte Schlüssel wird niemals im Klartext angezeigt.">
            <SecretInput aria-label="OpenRouter API-Key" masked={settings.openRouterApiKey === "********"} onChangeValue={(value) => onSettingsChange({ ...settings, openRouterApiKey: value })} value={settings.openRouterApiKey ?? ""} />
          </Field>
          <Field label="OpenRouter URL"><Input onChange={(event) => onSettingsChange({ ...settings, openRouterBaseUrl: event.target.value })} value={settings.openRouterBaseUrl ?? ""} /></Field>
          <Field label="OpenRouter Modell"><Input onChange={(event) => onSettingsChange({ ...settings, openRouterModel: event.target.value })} value={settings.openRouterModel ?? ""} /></Field>
          <Field label="Währung"><Input maxLength={3} onChange={(event) => onSettingsChange({ ...settings, currency: event.target.value.toUpperCase() })} value={settings.currency ?? "EUR"} /></Field>
          <Button disabled={saving} onClick={() => onTestConnection("OPENROUTER")} variant="secondary">OpenRouter testen</Button>
        </CardContent>
      </Card>
      <div className="flex justify-end xl:col-span-2">
        <Button disabled={saving} onClick={onSave}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}Speichern</Button>
      </div>
    </div>
  );
}

function AiParserSettings({ onSave, onSettingsChange, saving, settings }: {
  onSave: () => void;
  onSettingsChange: (settings: SettingsDTO) => void;
  saving: boolean;
  settings: SettingsDTO;
}) {
  const confidence = settings.aiCategorizationMinConfidence ?? 0.9;
  return (
    <div className="space-y-4">
      <Card>
        <CardHeader><CardTitle>KI-Kategorisierung & Produkthistorie</CardTitle></CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-3">
          <Field label="KI-Kategorisierung Mindest-Konfidenz"><Input max={1} min={0} onChange={(event) => onSettingsChange({ ...settings, aiCategorizationMinConfidence: Number(event.target.value) })} step={0.001} type="number" value={confidence} /></Field>
          <Field label="Mindestens bestätigte Treffer"><Input min={1} onChange={(event) => onSettingsChange({ ...settings, productHistoryMinConfirmedMatches: Number(event.target.value) })} type="number" value={settings.productHistoryMinConfirmedMatches ?? 3} /></Field>
          <Field label="Erforderlicher Variantenanteil"><Input max={1} min={0} onChange={(event) => onSettingsChange({ ...settings, productHistoryMinVariantShare: Number(event.target.value) })} step={0.001} type="number" value={settings.productHistoryMinVariantShare ?? 0.9} /></Field>
        </CardContent>
      </Card>
      <Card>
        <CardHeader><CardTitle>KI-Parsing-Fallback</CardTitle></CardHeader>
        <CardContent className="grid gap-3 md:grid-cols-2">
          <label className="flex items-center gap-2 text-sm"><input aria-label="KI-Parsing aktiv" checked={settings.aiParsingFallbackEnabled ?? true} onChange={(event) => onSettingsChange({ ...settings, aiParsingFallbackEnabled: event.target.checked })} type="checkbox" />KI-Parsing aktiv</label>
          <label className="flex items-center gap-2 text-sm"><input aria-label="Lokale Debug-Snippets speichern" checked={settings.aiParsingStoreDebugSnippets ?? false} onChange={(event) => onSettingsChange({ ...settings, aiParsingStoreDebugSnippets: event.target.checked })} type="checkbox" />Lokale Debug-Snippets speichern</label>
          <Field label="Parsing-Modell"><Input onChange={(event) => onSettingsChange({ ...settings, aiParsingModel: event.target.value })} value={settings.aiParsingModel ?? ""} /></Field>
          <Field label="Parsing Max Tokens"><Input min={1} onChange={(event) => onSettingsChange({ ...settings, aiParsingMaxTokens: Number(event.target.value) })} type="number" value={settings.aiParsingMaxTokens ?? 2500} /></Field>
          <Field label="Parsing Temperature"><Input max={2} min={0} onChange={(event) => onSettingsChange({ ...settings, aiParsingTemperature: Number(event.target.value) })} step={0.1} type="number" value={settings.aiParsingTemperature ?? 0} /></Field>
          <Field label="Parsing Mindest-Konfidenz"><Input max={1} min={0} onChange={(event) => onSettingsChange({ ...settings, aiParsingMinConfidence: Number(event.target.value) })} step={0.001} type="number" value={settings.aiParsingMinConfidence ?? 0.9} /></Field>
          <Field label="Sync-Call-Limit"><Input min={0} onChange={(event) => onSettingsChange({ ...settings, aiParsingSyncCallLimit: Number(event.target.value) })} type="number" value={settings.aiParsingSyncCallLimit ?? 25} /></Field>
          <Field help="FULL_TEXT überträgt den vollständigen Bontext an OpenRouter. Ein manueller Reparse mit FULL_TEXT erfordert eine zusätzliche Bestätigung." helpId="ai-parsing-text-mode-help" label="Textmodus"><select aria-describedby="ai-parsing-text-mode-help" aria-label="Textmodus" className={selectClassName} onChange={(event) => onSettingsChange({ ...settings, aiParsingTextMode: event.target.value as "MINIMIZED" | "FULL_TEXT" })} value={settings.aiParsingTextMode ?? "MINIMIZED"}><option value="MINIMIZED">MINIMIZED</option><option value="FULL_TEXT">FULL_TEXT</option></select></Field>
        </CardContent>
      </Card>
      <div className="flex justify-end"><Button disabled={saving} onClick={onSave}>{saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}Speichern</Button></div>
    </div>
  );
}

function MaintenanceSettings({ onOverwriteManualEditsChange, onProductResetConfirmationChange, onReparseAll, onRequestProductReset, onRequestReceiptReset, onResetConfirmationChange, overwriteManualEdits, productResetConfirmation, resetConfirmation, saving }: {
  onOverwriteManualEditsChange: (value: boolean) => void;
  onProductResetConfirmationChange: (value: string) => void;
  onReparseAll: () => void;
  onRequestProductReset: () => void;
  onRequestReceiptReset: () => void;
  onResetConfirmationChange: (value: string) => void;
  overwriteManualEdits: boolean;
  productResetConfirmation: string;
  resetConfirmation: string;
  saving: boolean;
}) {
  return <div className="space-y-4">
    <Card><CardHeader><CardTitle>Bons neu verarbeiten</CardTitle></CardHeader><CardContent className="space-y-3"><label className="flex items-center gap-2 text-sm"><input checked={overwriteManualEdits} onChange={(event) => onOverwriteManualEditsChange(event.target.checked)} type="checkbox" />Manuell editierte Positionen überschreiben</label><Button disabled={saving} onClick={onReparseAll} variant="secondary"><RotateCcw className="h-4 w-4" />Alle Bons erneut parsen</Button></CardContent></Card>
    <div className="grid gap-4 xl:grid-cols-2">
      <DangerCard description="Löscht importierte Bons, Positionen sowie zugehörige Parser-, KI- und Sync-Daten. Kategorien, Regeln, Produktstammdaten, Einstellungen und Backups bleiben erhalten." title="Importierte Bon-Daten zurücksetzen">
        <Field label="Bon-Daten Bestätigung"><Input onChange={(event) => onResetConfirmationChange(event.target.value)} placeholder="DELETE_IMPORTED_RECEIPTS" value={resetConfirmation} /></Field>
        <Button disabled={saving || resetConfirmation !== "DELETE_IMPORTED_RECEIPTS"} onClick={onRequestReceiptReset} variant="danger"><Trash2 className="h-4 w-4" />Importierte Bon-Daten löschen</Button>
      </DangerCard>
      <DangerCard description="Löscht Produktfamilien, Varianten, Produktregeln, Reviewstatus, Zuordnungen und Preis-Ausschlüsse. Importierte Bons, Kategorien und Kategorisierungsregeln bleiben erhalten." title="Produktdaten zurücksetzen">
        <Field label="Produktdaten Bestätigung"><Input onChange={(event) => onProductResetConfirmationChange(event.target.value)} placeholder="DELETE_PRODUCT_DATA" value={productResetConfirmation} /></Field>
        <Button disabled={saving || productResetConfirmation !== "DELETE_PRODUCT_DATA"} onClick={onRequestProductReset} variant="danger"><Trash2 className="h-4 w-4" />Produktdaten löschen</Button>
      </DangerCard>
    </div>
  </div>;
}

function DangerCard({ children, description, title }: { children: ReactNode; description: string; title: string }) {
  return <Card className="border-red-200 dark:border-red-900"><CardHeader><CardTitle>{title}</CardTitle></CardHeader><CardContent className="space-y-3"><p className="text-sm text-zinc-600 dark:text-zinc-300">{description}</p>{children}</CardContent></Card>;
}

function SystemSettings({ systemInfo }: { systemInfo: SystemInfoDTO | null }) {
  return <Card><CardHeader><CardTitle>Systeminformationen</CardTitle></CardHeader><CardContent><dl className="grid gap-2 text-sm sm:grid-cols-[180px_1fr]"><dt className="text-zinc-500">Software-Version</dt><dd className="font-medium">{systemInfo?.version ?? "unbekannt"}</dd></dl></CardContent></Card>;
}
function BackupSettings({
  backupFile,
  confirmation,
  fileInputResetKey,
  onConfirmationChange,
  onDownload,
  onFileChange,
  onRestore,
  onValidate,
  saving,
  validation
}: {
  backupFile: File | null;
  confirmation: string;
  fileInputResetKey: number;
  onConfirmationChange: (value: string) => void;
  onDownload: () => void;
  onFileChange: (file: File | null) => void;
  onRestore: () => void;
  onValidate: () => void;
  saving: boolean;
  validation: BackupValidationReportDTO | null;
}) {
  const restoreAllowed = Boolean(backupFile && validation?.valid && confirmation === RESTORE_CONFIRMATION);

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
      <Card>
        <CardHeader>
          <CardTitle>Backup erstellen</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="space-y-2 text-sm text-zinc-700 dark:text-zinc-200">
            <p>
              Erstellt eine ZIP-Datei mit Kategorien, Regeln, Bons, Positionen, KI-Logs, Sync-Logs und
              Anwendungseinstellungen.
            </p>
            <p>
              API-Schluessel werden nicht exportiert. Paperless- und OpenRouter-Schluessel muessen nach einem Restore
              neu gesetzt werden.
            </p>
          </div>
          <Button disabled={saving} onClick={onDownload}>
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Download className="h-4 w-4" />}
            Backup herunterladen
          </Button>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Restore</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-md border border-amber-200 bg-amber-50/60 p-3 text-sm text-amber-900 dark:border-amber-900 dark:bg-amber-950/20 dark:text-amber-200">
            Restore ersetzt die aktuelle Anwendungsdatenbank vollständig mit dem Inhalt der Backup-Datei. Währenddessen
            sind Schreibzugriffe gesperrt.
          </div>
          <Field
            help="ZIP-Datei aus einem eBon-Backup. Vor dem Restore zuerst den Dry-Run ausführen; dabei wird die Datenbank nicht verändert."
            label="Backup-ZIP"
          >
            <Input
              aria-label="Backup-ZIP"
              accept=".zip,application/zip"
              key={fileInputResetKey}
              onChange={(event) => onFileChange(event.target.files?.[0] ?? null)}
              type="file"
            />
          </Field>
          {backupFile ? (
            <div className="text-xs text-zinc-500 dark:text-zinc-400">
              Ausgewählt: {backupFile.name} ({Math.round(backupFile.size / 1024)} KB)
            </div>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button disabled={saving || !backupFile} onClick={onValidate} size="sm" variant="secondary">
              <FileCheck2 className="h-4 w-4" />
              Dry-Run prüfen
            </Button>
          </div>
          <Field
            help="Restore ersetzt alle aktuellen Anwendungsdaten durch das Backup. Gib den Bestätigungstext exakt ein, um den Button freizuschalten."
            label="Restore-Bestätigung"
          >
            <Input
              aria-label="Restore-Bestätigung"
              onChange={(event) => onConfirmationChange(event.target.value)}
              placeholder={RESTORE_CONFIRMATION}
              value={confirmation}
            />
          </Field>
          <Button disabled={saving || !restoreAllowed} onClick={onRestore} variant="danger">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Upload className="h-4 w-4" />}
            Backup wiederherstellen
          </Button>
          {!validation?.valid && backupFile ? (
            <div className="text-xs text-zinc-500 dark:text-zinc-400">
              Restore wird erst nach erfolgreichem Dry-Run und exakter Bestätigung freigeschaltet.
            </div>
          ) : null}
        </CardContent>
      </Card>

      {validation ? (
        <Card className="xl:col-span-2">
          <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
            <CardTitle>Dry-Run Ergebnis</CardTitle>
            {validation.valid ? <Badge tone="green">Valide</Badge> : <Badge tone="red">Fehlerhaft</Badge>}
          </CardHeader>
          <CardContent className="space-y-4">
            <div className="text-sm text-zinc-600 dark:text-zinc-300">
              Manifest-Version: {validation.manifestVersion ?? "unbekannt"}
            </div>
            {validation.errors.length ? (
              <ValidationMessageList tone="red" title="Fehler" values={validation.errors} />
            ) : null}
            {validation.warnings.length ? (
              <ValidationMessageList tone="amber" title="Warnungen" values={validation.warnings} />
            ) : null}
            <div className="overflow-x-auto">
              <table className="w-full min-w-[620px] text-sm">
                <thead>
                  <tr className="border-b border-zinc-100 text-left text-xs uppercase text-zinc-500 dark:border-zinc-900 dark:text-zinc-400">
                    <th className="px-3 py-2 font-medium">Bereich</th>
                    <th className="px-3 py-2 text-right font-medium">Datensätze</th>
                    <th className="px-3 py-2 text-right font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {validation.tables.map((table) => (
                    <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-900" key={table.name}>
                      <td className="px-3 py-2 font-medium">{backupTableLabel(table.name)}</td>
                      <td className="px-3 py-2 text-right">{table.recordCount}</td>
                      <td className="px-3 py-2 text-right">
                        {table.valid ? <Badge tone="green">OK</Badge> : <Badge tone="red">Fehler</Badge>}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}

function ParserSuggestionSettings({
  onAccept,
  onExport,
  onLoadDetail,
  onReject,
  saving,
  suggestions
}: {
  onAccept: (suggestion: ParseRuleSuggestionDTO, reparseScope: ReparseScope) => void;
  onExport: () => void;
  onLoadDetail: (id: number) => Promise<ParseRuleSuggestionDTO>;
  onReject: (suggestion: ParseRuleSuggestionDTO) => void;
  saving: boolean;
  suggestions: ParseRuleSuggestionDTO[];
}) {
  const [scopeById, setScopeById] = useState<Record<number, ReparseScope>>({});
  const [detailById, setDetailById] = useState<Record<number, ParseRuleSuggestionDTO>>({});
  const [detailErrorById, setDetailErrorById] = useState<Record<number, string>>({});
  const [loadingDetailId, setLoadingDetailId] = useState<number | null>(null);

  async function ensureDetail(suggestion: ParseRuleSuggestionDTO) {
    if (suggestion.receiptContext || detailById[suggestion.id] || loadingDetailId === suggestion.id) {
      return;
    }

    setLoadingDetailId(suggestion.id);
    setDetailErrorById((current) => ({ ...current, [suggestion.id]: "" }));
    try {
      const detail = await onLoadDetail(suggestion.id);
      setDetailById((current) => ({ ...current, [suggestion.id]: detail }));
    } catch (detailError) {
      setDetailErrorById((current) => ({ ...current, [suggestion.id]: toUserMessage(detailError) }));
    } finally {
      setLoadingDetailId((current) => current === suggestion.id ? null : current);
    }
  }

  return (
    <Card>
      <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
        <CardTitle>Parser-Regelvorschläge</CardTitle>
        <Button disabled={saving} onClick={onExport} size="sm" variant="secondary">
          <Download className="h-4 w-4" />
          Migration exportieren
        </Button>
      </CardHeader>
      <CardContent className="space-y-4">
        {!suggestions.length ? <div className="rounded-md border border-dashed border-zinc-200 px-4 py-8 text-center text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">Keine Parser-Regelvorschläge</div> : null}
        {suggestions.map((suggestion) => {
          const scope = scopeById[suggestion.id] ?? "NONE";
          const detail = detailById[suggestion.id] ?? suggestion;
          const context = detail.receiptContext;
          return (
            <div className="rounded-md border border-zinc-200 p-3 text-sm dark:border-zinc-800" key={suggestion.id}>
              <div className="mb-2 flex flex-wrap items-center gap-2">
                <Badge tone={suggestion.status === "OPEN" ? "blue" : suggestion.status === "ACCEPTED" ? "green" : "red"}>{suggestion.status}</Badge>
                <Badge tone={suggestion.validationStatus === "VALID" ? "green" : "yellow"}>{suggestion.validationStatus}</Badge>
                <span className="font-medium">{suggestion.storeName ?? "Generisch"} · {suggestion.ruleType}</span>
              </div>
              <div className="grid gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
                <div>
                  <div className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Auslöser / Problem</div>
                  <p className="mt-1 text-zinc-700 dark:text-zinc-200">{suggestion.problemDescription}</p>
                </div>
                <div>
                  <div className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Lösungsbegründung</div>
                  <p className="mt-1 text-zinc-700 dark:text-zinc-200">{suggestion.solutionRationale}</p>
                </div>
              </div>
              <pre className="mt-3 overflow-auto rounded-md bg-zinc-950 p-2 text-xs text-zinc-50">{suggestion.matchRegex}</pre>
              {suggestion.validationMessage ? <div className="mt-2 text-xs text-amber-700 dark:text-amber-300">{suggestion.validationMessage}</div> : null}
              <details
                className="mt-3 rounded-md border border-zinc-200 bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-950/40"
                onToggle={(event) => {
                  if (event.currentTarget.open) {
                    void ensureDetail(suggestion);
                  }
                }}
              >
                <summary className="cursor-pointer px-3 py-2 font-medium text-zinc-700 dark:text-zinc-200">
                  Quelltext und Parsing-Ergebnis anzeigen
                </summary>
                <div className="border-t border-zinc-200 p-3 dark:border-zinc-800">
                  {loadingDetailId === suggestion.id ? (
                    <div className="flex items-center gap-2 text-sm text-zinc-500 dark:text-zinc-400">
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Bon-Kontext wird geladen...
                    </div>
                  ) : null}
                  {detailErrorById[suggestion.id] ? <ErrorBox message={detailErrorById[suggestion.id]} /> : null}
                  {context ? <ParserSuggestionReceiptContext context={context} /> : null}
                  {!context && loadingDetailId !== suggestion.id && !detailErrorById[suggestion.id] ? (
                    <div className="text-sm text-zinc-500 dark:text-zinc-400">Kein Bon-Kontext verfügbar.</div>
                  ) : null}
                </div>
              </details>
              {suggestion.status === "OPEN" ? (
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <select
                    className={selectClassName}
                    onChange={(event) => setScopeById((current) => ({ ...current, [suggestion.id]: event.target.value as ReparseScope }))}
                    value={scope}
                  >
                    <option value="NONE">Kein sofortiger Reparse</option>
                    <option value="CURRENT_RECEIPT">Aktueller Bon</option>
                    <option value="PARSE_ERROR_BY_STORE">Parse-Fehler gleicher Store</option>
                    <option value="ALL_PARSE_ERROR">Alle Parse-Fehler</option>
                  </select>
                  <Button disabled={saving || suggestion.validationStatus !== "VALID"} onClick={() => onAccept(suggestion, scope)} size="sm">
                    Übernehmen
                  </Button>
                  <Button disabled={saving} onClick={() => onReject(suggestion)} size="sm" variant="danger">
                    Ablehnen
                  </Button>
                </div>
              ) : null}
            </div>
          );
        })}
      </CardContent>
    </Card>
  );
}

function ParserSuggestionReceiptContext({ context }: { context: ParseRuleSuggestionReceiptContextDTO }) {
  return (
    <div className="grid gap-3 xl:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)]">
      <div>
        <div className="mb-1 text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Quelltext</div>
        <pre className="max-h-96 overflow-auto whitespace-pre-wrap rounded-md border border-zinc-200 bg-white p-3 text-xs leading-relaxed text-zinc-800 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100">
          {context.rawText || "Kein Quelltext gespeichert."}
        </pre>
      </div>
      <div className="min-w-0">
        <div className="mb-1 text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Aktuelles Parsing-Ergebnis</div>
        <div className="space-y-3 rounded-md border border-zinc-200 bg-white p-3 dark:border-zinc-800 dark:bg-zinc-950">
          <div className="grid gap-2 sm:grid-cols-2 lg:grid-cols-3">
            <ParserContextMetric label="Geschäft" value={context.storeName ?? "-"} />
            <ParserContextMetric label="Filiale" value={context.storeBranch ?? "-"} />
            <ParserContextMetric label="Datum" value={`${formatDate(context.receiptDate)} · ${formatTime(context.receiptTime)}`} />
            <ParserContextMetric label="Gesamtbetrag" value={context.totalAmount == null ? "-" : formatCurrency(context.totalAmount)} />
            <ParserContextMetric label="Status" value={context.parseStatus} />
            <ParserContextMetric label="Quelle" value={context.parseSource ?? "-"} />
          </div>
          <div className="overflow-auto rounded-md border border-zinc-200 dark:border-zinc-800">
            <table className="min-w-full divide-y divide-zinc-200 text-left text-xs dark:divide-zinc-800">
              <thead className="bg-zinc-50 text-zinc-500 dark:bg-zinc-900 dark:text-zinc-400">
                <tr>
                  <th className="px-2 py-2">#</th>
                  <th className="px-2 py-2">Beschreibung</th>
                  <th className="px-2 py-2 text-right">Menge</th>
                  <th className="px-2 py-2">Einheit</th>
                  <th className="px-2 py-2 text-right">Einzelpreis</th>
                  <th className="px-2 py-2 text-right">Gesamt</th>
                  <th className="px-2 py-2 text-right">Rabatt</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-100 dark:divide-zinc-900">
                {context.items.length ? context.items.map((item) => (
                  <tr key={`${item.positionIndex}-${item.description}`}>
                    <td className="px-2 py-2 text-zinc-500 dark:text-zinc-400">{item.positionIndex + 1}</td>
                    <td className="max-w-72 px-2 py-2">{item.description}</td>
                    <td className="px-2 py-2 text-right">{formatOptionalNumber(item.quantity)}</td>
                    <td className="px-2 py-2">{item.unit ?? "-"}</td>
                    <td className="px-2 py-2 text-right">{formatOptionalCurrency(item.unitPrice)}</td>
                    <td className="px-2 py-2 text-right font-medium">{formatOptionalCurrency(item.totalPrice)}</td>
                    <td className="px-2 py-2 text-right">{formatOptionalCurrency(item.discountAmount)}</td>
                  </tr>
                )) : (
                  <tr>
                    <td className="px-2 py-4 text-center text-zinc-500 dark:text-zinc-400" colSpan={7}>Keine Positionen gespeichert.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>
    </div>
  );
}

function ParserContextMetric({ label, value }: { label: string; value: string }) {
  return (
    <div className="min-w-0 rounded-md bg-zinc-50 px-2 py-1.5 dark:bg-zinc-900">
      <div className="text-[11px] font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</div>
      <div className="truncate text-sm text-zinc-900 dark:text-zinc-50" title={value}>{value}</div>
    </div>
  );
}

function formatOptionalCurrency(value: number | null | undefined): string {
  return value == null ? "-" : formatCurrency(value);
}

function formatOptionalNumber(value: number | null | undefined): string {
  return value == null ? "-" : new Intl.NumberFormat("de-DE", { maximumFractionDigits: 3 }).format(value);
}

function ValidationMessageList({ title, tone, values }: { title: string; tone: "amber" | "red"; values: string[] }) {
  const className = tone === "red"
    ? "border-red-200 bg-red-50 text-red-700 dark:border-red-900 dark:bg-red-950/20 dark:text-red-200"
    : "border-amber-200 bg-amber-50 text-amber-800 dark:border-amber-900 dark:bg-amber-950/20 dark:text-amber-200";

  return (
    <div className={`rounded-md border px-3 py-2 text-sm ${className}`}>
      <div className="font-medium">{title}</div>
      <ul className="mt-1 list-disc space-y-1 pl-5">
        {values.map((value) => <li key={value}>{value}</li>)}
      </ul>
    </div>
  );
}

function CategorySettings({
  categories,
  categoryDraft,
  categoryIcons,
  editingCategoryId,
  includeInactive,
  onCancelEdit,
  onCategoryDraftChange,
  onDelete,
  onEdit,
  onIncludeInactiveChange,
  onPatchActive,
  onSave,
  saving
}: {
  categories: CategoryDTO[];
  categoryDraft: CategoryRequest;
  categoryIcons: CategoryIconDTO[];
  editingCategoryId: number | null;
  includeInactive: boolean;
  onCancelEdit: () => void;
  onCategoryDraftChange: (draft: CategoryRequest) => void;
  onDelete: (category: CategoryDTO) => void;
  onEdit: (category: CategoryDTO) => void;
  onIncludeInactiveChange: (value: boolean) => void;
  onPatchActive: (category: CategoryDTO, isActive: boolean) => void;
  onSave: () => void;
  saving: boolean;
}) {
  return (
    <div className="grid gap-4 xl:grid-cols-[360px_minmax(0,1fr)]">
      <Card>
        <CardHeader>
          <CardTitle>{editingCategoryId == null ? "Neue Kategorie" : "Kategorie bearbeiten"}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Field help="Sichtbarer Kategoriename für Auswertungen, Filter und manuelle Zuordnung." label="Name">
            <Input onChange={(event) => onCategoryDraftChange({ ...categoryDraft, name: event.target.value })} value={categoryDraft.name} />
          </Field>
          <Field help="Farbe für Charts und Kategorieanzeige." label="Farbe">
            <Input onChange={(event) => onCategoryDraftChange({ ...categoryDraft, colorHex: event.target.value })} type="color" value={categoryDraft.colorHex ?? "#71717a"} />
          </Field>
          <Field help="Nur feste, backend-validierte Icons sind auswählbar. Das verhindert beliebige Icon-Namen oder HTML/SVG-Fragmente." label="Icon">
            <select
              className={selectClassName}
              onChange={(event) => onCategoryDraftChange({ ...categoryDraft, icon: event.target.value || null })}
              value={categoryDraft.icon ?? ""}
            >
              <option value="">Kein Icon</option>
              {categoryIcons.map((icon) => (
                <option key={icon.value} value={icon.value}>{icon.label}</option>
              ))}
            </select>
          </Field>
          <Field help="Kleinere Werte erscheinen weiter oben." label="Sortierung">
            <Input onChange={(event) => onCategoryDraftChange({ ...categoryDraft, sortOrder: Number(event.target.value) })} type="number" value={categoryDraft.sortOrder ?? 0} />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input checked={categoryDraft.isActive ?? true} onChange={(event) => onCategoryDraftChange({ ...categoryDraft, isActive: event.target.checked })} type="checkbox" />
            Aktiv
          </label>
          <div className="flex gap-2">
            <Button disabled={saving || !categoryDraft.name.trim()} onClick={onSave} size="sm">
              <Plus className="h-4 w-4" />
              Speichern
            </Button>
            {editingCategoryId == null ? null : <Button onClick={onCancelEdit} size="sm" variant="secondary">Abbrechen</Button>}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <CardTitle>Kategorien</CardTitle>
          <label className="flex items-center gap-2 text-sm">
            <input checked={includeInactive} onChange={(event) => onIncludeInactiveChange(event.target.checked)} type="checkbox" />
            Inaktive anzeigen
          </label>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full min-w-[680px] text-sm">
            <tbody>
              {categories.map((category) => (
                <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-900" key={category.id}>
                  <td className="px-3 py-2">
                    <span className="inline-flex items-center gap-2">
                      <span className="h-3 w-3 rounded-full" style={{ backgroundColor: category.colorHex }} />
                      <span style={{ color: category.colorHex }}>
                        <CategoryIcon icon={category.icon} />
                      </span>
                      <span className="font-medium">{category.name}</span>
                    </span>
                  </td>
                  <td className="px-3 py-2">{category.isActive ? <Badge tone="green">Aktiv</Badge> : <Badge>Inaktiv</Badge>}</td>
                  <td className="px-3 py-2 text-right">{category.assignedItemsCount} Positionen</td>
                  <td className="px-3 py-2 text-right">
                    <div className="flex justify-end gap-2">
                      <Button onClick={() => onEdit(category)} size="sm" variant="secondary">Bearbeiten</Button>
                      <Button onClick={() => onPatchActive(category, !category.isActive)} size="sm" variant="secondary">{category.isActive ? "Deaktivieren" : "Aktivieren"}</Button>
                      {category.assignedItemsCount === 0 ? <Button onClick={() => onDelete(category)} size="sm" variant="danger">Löschen</Button> : null}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}

function RuleSettings({
  categories,
  editingRuleId,
  onApply,
  onCancelEdit,
  onDelete,
  onEdit,
  onPreview,
  onRuleDraftChange,
  onSave,
  previewCount,
  ruleDraft,
  rules,
  saving
}: {
  categories: CategoryDTO[];
  editingRuleId: number | null;
  onApply: (rule: CategorizationRuleDTO) => void;
  onCancelEdit: () => void;
  onDelete: (rule: CategorizationRuleDTO) => void;
  onEdit: (rule: CategorizationRuleDTO) => void;
  onPreview: () => void;
  onRuleDraftChange: (draft: CategorizationRuleRequest) => void;
  onSave: () => void;
  previewCount: number | null;
  ruleDraft: CategorizationRuleRequest;
  rules: CategorizationRuleDTO[];
  saving: boolean;
}) {
  return (
    <div className="grid gap-4 xl:grid-cols-[420px_minmax(0,1fr)]">
      <Card>
        <CardHeader>
          <CardTitle>{editingRuleId == null ? "Neue Regel" : "Regel bearbeiten"}</CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <Field help="Kategorie, die passende Positionen durch diese Regel erhalten." label="Zielkategorie">
            <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, categoryId: Number(event.target.value) })} value={ruleDraft.categoryId || categories[0]?.id || 0}>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>{category.name}</option>
              ))}
            </select>
          </Field>
          <div className="grid gap-3 md:grid-cols-2">
            <Field help="Position prüft den Artikeltext, Geschäft prüft den Laden-Namen." label="Match-Feld">
              <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchField: event.target.value as RuleMatchField, storeName: event.target.value === "STORE_NAME" ? null : ruleDraft.storeName })} value={ruleDraft.matchField}>
                <option value="DESCRIPTION">Position</option>
                <option value="STORE_NAME">Geschäft</option>
              </select>
            </Field>
            <Field help="Exakt ist am strengsten, Regex erlaubt Muster für komplexe Fälle." label="Match-Typ">
              <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchType: event.target.value as RuleMatchType })} value={ruleDraft.matchType}>
                <option value="CONTAINS">Enthält</option>
                <option value="STARTS_WITH">Beginnt mit</option>
                <option value="ENDS_WITH">Endet mit</option>
                <option value="EXACT">Exakt</option>
                <option value="REGEX">Regex</option>
              </select>
            </Field>
          </div>
          <Field help="Suchtext oder Regex. Regeln sollten so präzise sein, dass sie keine falschen Produkte treffen." label="Match-Wert">
            <Input onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchValue: event.target.value })} value={ruleDraft.matchValue} />
          </Field>
          {ruleDraft.matchField === "DESCRIPTION" ? (
            <Field help="Optional: Die Regel greift nur, wenn der Händlername exakt übereinstimmt." label="Nur bei Händler (optional)">
              <Input onChange={(event) => onRuleDraftChange({ ...ruleDraft, storeName: event.target.value })} value={ruleDraft.storeName ?? ""} />
            </Field>
          ) : null}
          <Field help="Niedrigste Priorität gewinnt. Spezifische Regeln sollten kleinere Werte haben als breite Fallback-Regeln." label="Priorität">
            <Input onChange={(event) => onRuleDraftChange({ ...ruleDraft, priority: Number(event.target.value) })} type="number" value={ruleDraft.priority ?? 100} />
          </Field>
          <label className="flex items-center gap-2 text-sm">
            <input checked={ruleDraft.isActive ?? true} onChange={(event) => onRuleDraftChange({ ...ruleDraft, isActive: event.target.checked })} type="checkbox" />
            Aktiv
          </label>
          <label className="flex items-center gap-2 text-sm">
            <input checked={Boolean(ruleDraft.applyToExisting)} onChange={(event) => onRuleDraftChange({ ...ruleDraft, applyToExisting: event.target.checked })} type="checkbox" />
            Auf bestehende passende Positionen anwenden
          </label>
          {previewCount == null ? null : <Badge tone="blue">{previewCount} Treffer in Vorschau</Badge>}
          <div className="flex flex-wrap gap-2">
            <Button disabled={saving || !ruleDraft.matchValue.trim() || !ruleDraft.categoryId} onClick={onSave} size="sm">
              Speichern
            </Button>
            <Button disabled={saving || !ruleDraft.matchValue.trim()} onClick={onPreview} size="sm" variant="secondary">Vorschau</Button>
            {editingRuleId == null ? null : <Button onClick={onCancelEdit} size="sm" variant="secondary">Abbrechen</Button>}
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Regeln</CardTitle>
        </CardHeader>
        <CardContent className="overflow-x-auto">
          <table className="w-full min-w-[860px] text-sm">
            <thead>
              <tr className="border-b border-zinc-100 text-left text-xs uppercase text-zinc-500 dark:border-zinc-900 dark:text-zinc-400">
                <th className="px-3 py-2 font-medium">Priorität</th>
                <th className="px-3 py-2 font-medium">Regel</th>
                <th className="px-3 py-2 font-medium">Kategorie</th>
                <th className="px-3 py-2 font-medium">Status</th>
                <th className="px-3 py-2 text-right font-medium">Aktion</th>
              </tr>
            </thead>
            <tbody>
              {rules.map((rule) => (
                <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-900" key={rule.id}>
                  <td className="px-3 py-2">{rule.priority}</td>
                  <td className="px-3 py-2">
                    <div className="font-medium">{matchFieldLabel(rule.matchField)} · {matchTypeLabel(rule.matchType)}</div>
                    <div className="text-xs text-zinc-500 dark:text-zinc-400">{rule.matchValue}</div>
                    {rule.storeName ? <div className="text-xs text-zinc-500 dark:text-zinc-400">Nur bei Händler: {rule.storeName}</div> : null}
                  </td>
                  <td className="px-3 py-2">{rule.categoryName}</td>
                  <td className="px-3 py-2">{rule.isActive ? <Badge tone="green">Aktiv</Badge> : <Badge>Inaktiv</Badge>}</td>
                  <td className="px-3 py-2">
                    <div className="flex justify-end gap-2">
                      <Button onClick={() => onEdit(rule)} size="sm" variant="secondary">Bearbeiten</Button>
                      <Button onClick={() => onApply(rule)} size="sm" variant="secondary">Anwenden</Button>
                      <Button onClick={() => onDelete(rule)} size="sm" variant="danger">Löschen</Button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </CardContent>
      </Card>
    </div>
  );
}

function normalizeCategory(category: CategoryRequest): CategoryRequest {
  return {
    ...category,
    icon: category.icon?.trim() || null,
    colorHex: category.colorHex || "#71717a",
    name: category.name.trim()
  };
}

function normalizeRule(rule: CategorizationRuleRequest): CategorizationRuleRequest {
  return {
    ...rule,
    matchValue: rule.matchValue.trim(),
    storeName: rule.storeName?.trim() || null,
    applyToExisting: Boolean(rule.applyToExisting)
  };
}

function changedSecret(value: string): string | undefined {
  const trimmed = value.trim();
  return trimmed && trimmed !== "********" ? trimmed : undefined;
}

function productResetSummary(result: ProductDataResetResultDTO): string {
  return `${result.message} ${result.clearedAssignments} Zuordnungen, ${result.deletedAssignmentLogs} Protokolle, ${result.deletedProductRules} Regeln, ${result.deletedProductVariants} Varianten und ${result.deletedProductFamilies} Familien gelöscht.`;
}

function backupTableLabel(name: string): string {
  const labels: Record<string, string> = {
    ai_categorization_log: "KI-Kategorisierung",
    app_settings: "Einstellungen",
    categories: "Kategorien",
    categorization_rules: "Kategorisierungsregeln",
    product_assignment_log: "Produktzuordnungsprotokoll",
    product_families: "Produktfamilien",
    product_rules: "Produktregeln",
    product_variants: "Produktvarianten",
    parse_rules: "Parser-Regeln",
    receipt_items: "Bon-Positionen",
    receipts: "Bons",
    sync_log: "Sync-Protokolle",
    sync_log_entry: "Sync-Protokolldetails"
  };
  return labels[name] ?? name;
}

function saveBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  document.body.appendChild(link);
  link.click();
  document.body.removeChild(link);
  URL.revokeObjectURL(url);
}

function matchFieldLabel(value: RuleMatchField): string {
  return value === "DESCRIPTION" ? "Position" : "Geschäft";
}

function matchTypeLabel(value: RuleMatchType): string {
  return {
    CONTAINS: "enthält",
    STARTS_WITH: "beginnt mit",
    ENDS_WITH: "endet mit",
    EXACT: "exakt",
    REGEX: "Regex"
  }[value];
}

function Field({ children, help, helpId, label }: { children: ReactNode; help?: string; helpId?: string; label: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</span>
      {children}
      {help ? <span className="mt-1 block text-xs leading-5 text-zinc-500 dark:text-zinc-400" id={helpId}>{help}</span> : null}
    </label>
  );
}

function ErrorBox({ message }: { message: string }) {
  return <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-200">{message}</div>;
}

function SuccessBox({ message }: { message: string }) {
  return (
    <div className="flex items-center gap-2 rounded-md border border-emerald-200 bg-emerald-50 px-4 py-3 text-sm text-emerald-700 dark:border-emerald-900 dark:bg-emerald-950 dark:text-emerald-200">
      <CheckCircle2 className="h-4 w-4" />
      {message}
    </div>
  );
}

function toUserMessage(error: unknown): string {
  if (error instanceof ApiClientError) {
    return error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Die Anfrage konnte nicht verarbeitet werden.";
}

const selectClassName = "min-h-10 w-full rounded-md border border-zinc-200 bg-white px-3 py-2 text-sm text-zinc-950 shadow-sm dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-50";
