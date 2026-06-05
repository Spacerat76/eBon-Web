import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { CheckCircle2, Loader2, Plus, RotateCcw, Save, Trash2 } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import type {
  CategorizationRuleDTO,
  CategorizationRuleRequest,
  CategoryDTO,
  CategoryRequest,
  DataMaintenanceResultDTO,
  RuleMatchField,
  RuleMatchType,
  SettingsDTO
} from "@/lib/types";

interface SettingsPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
}

type SettingsTab = "general" | "categories" | "rules";

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
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [rules, setRules] = useState<CategorizationRuleDTO[]>([]);
  const [includeInactive, setIncludeInactive] = useState(false);
  const [categoryDraft, setCategoryDraft] = useState<CategoryRequest>(emptyCategory);
  const [editingCategoryId, setEditingCategoryId] = useState<number | null>(null);
  const [ruleDraft, setRuleDraft] = useState<CategorizationRuleRequest>(emptyRule);
  const [editingRuleId, setEditingRuleId] = useState<number | null>(null);
  const [rulePreview, setRulePreview] = useState<number | null>(null);
  const [overwriteManualEdits, setOverwriteManualEdits] = useState(false);
  const [resetConfirmation, setResetConfirmation] = useState("");
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
      const [settingsResponse, categoryResponse, ruleResponse] = await Promise.all([
        apiClient.settings(),
        apiClient.categories(includeInactive),
        apiClient.rules()
      ]);
      setSettings(settingsResponse);
      setCategories(categoryResponse);
      setRules(ruleResponse);
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
        />
      ) : null}
      {!loading && tab === "categories" ? (
        <CategorySettings
          categoryDraft={categoryDraft}
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
  settings
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
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Paperless-NGX URL">
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessBaseUrl: event.target.value })} value={settings.paperlessBaseUrl ?? ""} />
            </Field>
            <Field label="Paperless API-Token">
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessApiToken: event.target.value })} type="password" value={settings.paperlessApiToken ?? ""} />
            </Field>
            <Field label="Paperless Web-URL">
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessPublicBaseUrl: event.target.value })} value={settings.paperlessPublicBaseUrl ?? ""} />
            </Field>
            <Field label="Dokument-URL-Vorlage">
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessDocumentUrlTemplate: event.target.value })} value={settings.paperlessDocumentUrlTemplate ?? ""} />
            </Field>
            <Field label="eBon Tag">
              <Input onChange={(event) => onSettingsChange({ ...settings, paperlessEbonTag: event.target.value })} value={settings.paperlessEbonTag ?? ""} />
            </Field>
            <Field label="OpenRouter API-Key">
              <Input onChange={(event) => onSettingsChange({ ...settings, openRouterApiKey: event.target.value })} type="password" value={settings.openRouterApiKey ?? ""} />
            </Field>
            <Field label="OpenRouter URL">
              <Input onChange={(event) => onSettingsChange({ ...settings, openRouterBaseUrl: event.target.value })} value={settings.openRouterBaseUrl ?? ""} />
            </Field>
            <Field label="OpenRouter Modell">
              <Input onChange={(event) => onSettingsChange({ ...settings, openRouterModel: event.target.value })} value={settings.openRouterModel ?? ""} />
            </Field>
            <Field label="Sync-Intervall Minuten">
              <Input onChange={(event) => onSettingsChange({ ...settings, syncIntervalMinutes: Number(event.target.value) })} min={1} type="number" value={settings.syncIntervalMinutes ?? 60} />
            </Field>
            <Field label="Währung">
              <Input maxLength={3} onChange={(event) => onSettingsChange({ ...settings, currency: event.target.value.toUpperCase() })} value={settings.currency ?? "EUR"} />
            </Field>
          </div>

          <div className="rounded-md border border-zinc-200 p-3 dark:border-zinc-800">
            <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
              <div>
                <div className="text-sm font-medium">KI-Konfidenz: {Math.round(confidence * 1000) / 10} %</div>
                <div className="text-xs text-zinc-500 dark:text-zinc-400">Niedriger automatisiert mehr, höher lässt mehr Positionen ohne Kategorie offen.</div>
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

function CategorySettings({
  categories,
  categoryDraft,
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
          <Field label="Name">
            <Input onChange={(event) => onCategoryDraftChange({ ...categoryDraft, name: event.target.value })} value={categoryDraft.name} />
          </Field>
          <Field label="Farbe">
            <Input onChange={(event) => onCategoryDraftChange({ ...categoryDraft, colorHex: event.target.value })} type="color" value={categoryDraft.colorHex ?? "#71717a"} />
          </Field>
          <Field label="Icon">
            <Input onChange={(event) => onCategoryDraftChange({ ...categoryDraft, icon: event.target.value })} value={categoryDraft.icon ?? ""} />
          </Field>
          <Field label="Sortierung">
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
          <Field label="Zielkategorie">
            <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, categoryId: Number(event.target.value) })} value={ruleDraft.categoryId || categories[0]?.id || 0}>
              {categories.map((category) => (
                <option key={category.id} value={category.id}>{category.name}</option>
              ))}
            </select>
          </Field>
          <div className="grid gap-3 md:grid-cols-2">
            <Field label="Match-Feld">
              <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchField: event.target.value as RuleMatchField })} value={ruleDraft.matchField}>
                <option value="DESCRIPTION">Position</option>
                <option value="STORE_NAME">Geschäft</option>
              </select>
            </Field>
            <Field label="Match-Typ">
              <select className={selectClassName} onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchType: event.target.value as RuleMatchType })} value={ruleDraft.matchType}>
                <option value="CONTAINS">Enthält</option>
                <option value="STARTS_WITH">Beginnt mit</option>
                <option value="ENDS_WITH">Endet mit</option>
                <option value="EXACT">Exakt</option>
                <option value="REGEX">Regex</option>
              </select>
            </Field>
          </div>
          <Field label="Match-Wert">
            <Input onChange={(event) => onRuleDraftChange({ ...ruleDraft, matchValue: event.target.value })} value={ruleDraft.matchValue} />
          </Field>
          <Field label="Priorität">
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

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</span>
      {children}
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
