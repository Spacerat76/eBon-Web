import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { CheckCircle2, Download, FileCheck2, Loader2, Plus, RotateCcw, Save, Trash2, Upload } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { CategoryIcon } from "@/lib/category-icons";
import type {
  BackupValidationReportDTO,
  CategorizationRuleDTO,
  CategorizationRuleRequest,
  CategoryDTO,
  CategoryIconDTO,
  CategoryRequest,
  DataMaintenanceResultDTO,
  RuleMatchField,
  RuleMatchType,
  SettingsDTO,
  SystemInfoDTO
} from "@/lib/types";

interface SettingsPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
}

type SettingsTab = "general" | "categories" | "rules" | "backup";

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
  syncIntervalMinutes: 60,
  currency: "EUR"
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
  priority: 100,
  isActive: true,
  applyToExisting: false
};

export function SettingsPage({ apiClient, hasApiToken }: SettingsPageProps) {
  const [tab, setTab] = useState<SettingsTab>("general");
  const [settings, setSettings] = useState<SettingsDTO>(emptySettings);
  const [systemInfo, setSystemInfo] = useState<SystemInfoDTO | null>(null);
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [categoryIcons, setCategoryIcons] = useState<CategoryIconDTO[]>([]);
  const [rules, setRules] = useState<CategorizationRuleDTO[]>([]);
  const [includeInactive, setIncludeInactive] = useState(false);
  const [categoryDraft, setCategoryDraft] = useState<CategoryRequest>(emptyCategory);
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [ruleDraft, setRuleDraft] = useState<CategorizationRuleRequest>(emptyRule);
  const [editingRuleId, setEditingRuleId] = useState<number | null>(null);
  const [rulePreview, setRulePreview] = useState<number | null>(null);
  const [overwriteManualEdits, setOverwriteManualEdits] = useState(false);
  const [resetConfirmation, setResetConfirmation] = useState("");
  const [backupFile, setBackupFile] = useState<File | null>(null);
  const [backupValidation, setBackupValidation] = useState<BackupValidationReportDTO | null>(null);
  const [restoreConfirmation, setRestoreConfirmation] = useState("");
  const [feedback, setFeedback] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  const activeCategories = useMemo(() => categories.filter((category) => category.isActive), [categories]);

  const loadSettings = useCallback(async () => {
    if (!hasApiToken) {
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const [settingsResponse, categoryResponse, ruleResponse, iconResponse, systemInfoResponse] = await Promise.all([
        apiClient.settings(),
        apiClient.categories(includeInactive),
        apiClient.rules(),
        apiClient.categoryIcons(),
        apiClient.systemInfo()
      ]);
      setSettings(settingsResponse);
      setCategories(categoryResponse);
      setRules(ruleResponse);
      setCategoryIcons(iconResponse);
      setSystemInfo(systemInfoResponse);
      if (categoryResponse.length && ruleDraft.categoryId === 0) {
        setRuleDraft((current) => ({ ...current, categoryId: categoryResponse[0].id }));
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
      const updated = await apiClient.updateSettings(settings);
      setSettings(updated);
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
      setBackupValidation(result.validation);
      setRestoreConfirmation("");
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
    setCategoryDraft({
      name: category.name,
      colorHex: category.colorHex,
      icon: category.icon ?? "",
      sortOrder: category.sortOrder,
      isActive: category.isActive
    });
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
      setRuleDraft({ ...emptyRule, categoryId: activeCategories[0]?.id ?? 0 });
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
        matchValue: ruleDraft.matchValue
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

  function editRule(rule: CategorizationRuleDTO) {
    setEditingRuleId(rule.id);
    setRulePreview(null);
    setRuleDraft({
      categoryId: rule.categoryId,
      matchField: rule.matchField,
      matchType: rule.matchType,
      matchValue: rule.matchValue,
      priority: rule.priority,
      isActive: rule.isActive,
      applyToExisting: false
    });
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

      <div className="flex flex-wrap gap-2">
        <Button onClick={() => setTab("general")} size="sm" variant={tab === "general" ? "primary" : "secondary"}>Allgemein</Button>
        <Button onClick={() => setTab("categories")} size="sm" variant={tab === "categories" ? "primary" : "secondary"}>Kategorien</Button>
        <Button onClick={() => setTab("rules")} size="sm" variant={tab === "rules" ? "primary" : "secondary"}>Regeln</Button>
        <Button onClick={() => setTab("backup")} size="sm" variant={tab === "backup" ? "primary" : "secondary"}>Backup</Button>
      </div>

      {loading ? <Skeleton className="h-96 w-full" /> : null}
      {!loading && tab === "general" ? (
        <GeneralSettings
          onReparseAll={reparseAll}
          onResetImportedReceipts={resetImportedReceipts}
          onSave={saveSettings}
          onSettingsChange={setSettings}
          onTestConnection={testConnection}
          onOverwriteManualEditsChange={setOverwriteManualEdits}
          onResetConfirmationChange={setResetConfirmation}
          overwriteManualEdits={overwriteManualEdits}
          resetConfirmation={resetConfirmation}
          saving={saving}
          settings={settings}
          systemInfo={systemInfo}
        />
      ) : null}
      {!loading && tab === "categories" ? (
        <CategorySettings
          categoryDraft={categoryDraft}
          categoryIcons={categoryIcons}
          categories={categories}
          editingCategoryId={editingCategoryId}
          includeInactive={includeInactive}
          onCancelEdit={() => {
            setEditingCategoryId(null);
            setCategoryDraft(emptyCategory);
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
      {!loading && tab === "rules" ? (
        <RuleSettings
          categories={activeCategories}
          editingRuleId={editingRuleId}
          onApply={applyRule}
          onCancelEdit={() => {
            setEditingRuleId(null);
            setRulePreview(null);
            setRuleDraft({ ...emptyRule, categoryId: activeCategories[0]?.id ?? 0 });
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
      {!loading && tab === "backup" ? (
        <BackupSettings
          backupFile={backupFile}
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
    </div>
  );
}

function GeneralSettings({
  onOverwriteManualEditsChange,
  onReparseAll,
  onResetConfirmationChange,
  onResetImportedReceipts,
  onSave,
  onSettingsChange,
  onTestConnection,
  overwriteManualEdits,
  resetConfirmation,
  saving,
  settings,
  systemInfo
}: {
  onOverwriteManualEditsChange: (value: boolean) => void;
  onReparseAll: () => void;
  onResetConfirmationChange: (value: string) => void;
  onResetImportedReceipts: () => void;
  onSave: () => void;
  onSettingsChange: (settings: SettingsDTO) => void;
  onTestConnection: (target: "PAPERLESS" | "OPENROUTER") => void;
  overwriteManualEdits: boolean;
  resetConfirmation: string;
  saving: boolean;
  settings: SettingsDTO;
  systemInfo: SystemInfoDTO | null;
}) {
  const confidence = settings.aiCategorizationMinConfidence ?? 0.9;

  return (
    <div className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_420px]">
      <Card>
        <CardHeader className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
          <CardTitle>Allgemein</CardTitle>
          <Button disabled={saving} onClick={onSave} size="sm">
            {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <Save className="h-4 w-4" />}
            Speichern
          </Button>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="flex flex-col gap-1 rounded-md border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm dark:border-zinc-800 dark:bg-zinc-900/40">
            <span className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">Software-Version</span>
            <span className="font-medium">{systemInfo?.version ?? "unbekannt"}</span>
          </div>
          <div className="grid gap-3 md:grid-cols-2">
            <Field
              help="Backend-Adresse für Paperless-API-Aufrufe. Im Docker-Netz kann das z. B. http://paperless:8001 sein; im lokalen Netzwerk auch eine IP-Adresse."
              label="Paperless-NGX URL"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessBaseUrl: event.target.value })} value={settings.paperlessBaseUrl ?? ""} />
            </Field>
            <Field
              help="Token für die Paperless-API. Wird nur an das Backend gesendet und in der UI maskiert angezeigt."
              label="Paperless API-Token"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessApiToken: event.target.value })} type="password" value={settings.paperlessApiToken ?? ""} />
            </Field>
            <Field
              help="Browser-Adresse für Links aus eBon zu Paperless-Dokumenten. Diese URL muss von deinem Browser erreichbar sein."
              label="Paperless Web-URL"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessPublicBaseUrl: event.target.value })} value={settings.paperlessPublicBaseUrl ?? ""} />
            </Field>
            <Field
              help="Optional. Überschreibt den automatisch erzeugten Paperless-Link. Nutze {paperlessDocumentId} als Platzhalter, z. B. http://paperless.local/documents/{paperlessDocumentId}/details."
              label="Dokument-URL-Vorlage"
            >
              <Input
                onChange={(event) => onSettingsChange({ ...settings, paperlessDocumentUrlTemplate: event.target.value })}
                placeholder="http://paperless.local/documents/{paperlessDocumentId}/details"
                value={settings.paperlessDocumentUrlTemplate ?? ""}
              />
            </Field>
            <Field
              help="Nur Paperless-Dokumente mit diesem Tag werden synchronisiert. Der Tag-Name muss exakt zu Paperless passen."
              label="eBon Tag"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessEbonTag: event.target.value })} value={settings.paperlessEbonTag ?? ""} />
            </Field>
            <Field
              help="Optionaler Schlüssel für KI-Funktionen. Ohne Schlüssel bleiben KI-Parsing und KI-Kategorisierung deaktiviert."
              label="OpenRouter API-Key"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, openRouterApiKey: event.target.value })} type="password" value={settings.openRouterApiKey ?? ""} />
            </Field>
            <Field
              help="Basis-URL des KI-Anbieters. Normalerweise kann der Standardwert verwendet werden."
              label="OpenRouter URL"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, openRouterBaseUrl: event.target.value })} value={settings.openRouterBaseUrl ?? ""} />
            </Field>
            <Field
              help="Modellname für KI-Parsing und KI-Kategorisierung. Änderungen wirken auf zukünftige KI-Aufrufe."
              label="OpenRouter Modell"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, openRouterModel: event.target.value })} value={settings.openRouterModel ?? ""} />
            </Field>
            <Field
              help="Abstand für automatische Paperless-Syncs. Manuelle Syncs sind davon unabhängig."
              label="Sync-Intervall Minuten"
            >
              <Input onChange={(event) => onSettingsChange({ ...settings, syncIntervalMinutes: Number(event.target.value) })} min={1} type="number" value={settings.syncIntervalMinutes ?? 60} />
            </Field>
            <Field
              help="Standardwährung für Anzeige und neue manuelle Werte, z. B. EUR."
              label="Währung"
            >
              <Input maxLength={3} onChange={(event) => onSettingsChange({ ...settings, currency: event.target.value.toUpperCase() })} value={settings.currency ?? "EUR"} />
            </Field>
          </div>

          <div className="rounded-md border border-zinc-200 p-3 dark:border-zinc-800">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <div className="text-sm font-medium">KI-Konfidenz: {Math.round(confidence * 1000) / 10} %</div>
              <div className="text-xs text-zinc-500 dark:text-zinc-400">Niedriger automatisiert mehr, höher lässt mehr Positionen ohne Kategorie offen. Abgelehnte KI-Vorschläge bleiben in der UI sichtbar.</div>
              </div>
              <Input className="md:w-32" max={1} min={0} onChange={(event) => onSettingsChange({ ...settings, aiCategorizationMinConfidence: Number(event.target.value) })} step={0.001} type="number" value={confidence} />
            </div>
            <input
              className="mt-3 w-full"
              max={1}
              min={0}
              onChange={(event) => onSettingsChange({ ...settings, aiCategorizationMinConfidence: Number(event.target.value) })}
              step={0.001}
              type="range"
              value={confidence}
            />
          </div>

          <div className="flex flex-wrap gap-2">
            <Button disabled={saving} onClick={() => onTestConnection("PAPERLESS")} size="sm" variant="secondary">Paperless testen</Button>
            <Button disabled={saving} onClick={() => onTestConnection("OPENROUTER")} size="sm" variant="secondary">OpenRouter testen</Button>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Datenwartung</CardTitle>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="rounded-md border border-zinc-200 p-3 dark:border-zinc-800">
            <label className="mb-3 flex items-start gap-2 text-sm">
              <input checked={overwriteManualEdits} className="mt-1" onChange={(event) => onOverwriteManualEditsChange(event.target.checked)} type="checkbox" />
              Manuell editierte Positionen überschreiben
            </label>
            <Button disabled={saving} onClick={onReparseAll} variant="secondary">
              <RotateCcw className="h-4 w-4" />
              Alle Bons erneut parsen
            </Button>
          </div>

          <div className="rounded-md border border-red-200 bg-red-50/50 p-4 dark:border-red-900 dark:bg-red-950/20">
            <div className="space-y-2 text-sm text-zinc-700 dark:text-zinc-200">
              <div className="font-medium text-red-700 dark:text-red-300">Importierte Bon-Daten zurücksetzen</div>
              <p>
                Löscht alle importierten Bons inklusive Positionen, Parse-Ergebnissen, Kategoriezuordnungen,
                KI-Vorschlägen und Sync-Protokollen. Kategorien, Regeln, Einstellungen und Datenbankmigrationen
                bleiben erhalten.
              </p>
              <p>
                Gib zur Bestätigung exakt <code className="rounded bg-white px-1.5 py-0.5 font-mono text-xs dark:bg-zinc-900">DELETE_IMPORTED_RECEIPTS</code> ein.
              </p>
            </div>
            <div className="mt-3">
              <Field label="Bestätigungstext">
                <Input
                  onChange={(event) => onResetConfirmationChange(event.target.value)}
                  placeholder="DELETE_IMPORTED_RECEIPTS"
                  value={resetConfirmation}
                />
              </Field>
            </div>
            <Button className="mt-3" disabled={saving || resetConfirmation !== "DELETE_IMPORTED_RECEIPTS"} onClick={onResetImportedReceipts} variant="danger">
              <Trash2 className="h-4 w-4" />
              Importierte Bon-Daten löschen
            </Button>
          </div>
        </CardContent>
      </Card>
    </div>
  );
}

function BackupSettings({
  backupFile,
  confirmation,
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
              accept=".zip,application/zip"
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
              <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchField: event.target.value as RuleMatchField })} value={ruleDraft.matchField}>
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
    applyToExisting: Boolean(rule.applyToExisting)
  };
}

function backupTableLabel(name: string): string {
  const labels: Record<string, string> = {
    ai_categorization_log: "KI-Kategorisierung",
    app_settings: "Einstellungen",
    categories: "Kategorien",
    categorization_rules: "Kategorisierungsregeln",
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

function Field({ children, help, label }: { children: ReactNode; help?: string; label: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</span>
      {children}
      {help ? <span className="mt-1 block text-xs leading-5 text-zinc-500 dark:text-zinc-400">{help}</span> : null}
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
