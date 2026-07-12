import { useCallback, useEffect, useMemo, useState, type ReactNode } from "react";
import { Bar, BarChart, Cell, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Download, Loader2 } from "lucide-react";

import { DataTableFrame } from "@/components/data/data-table";
import { FilterBar } from "@/components/data/filter-bar";
import { StatusBanner } from "@/components/feedback/status-banner";
import { PageHeader } from "@/components/layout/page-header";
import { PageTabs } from "@/components/layout/page-tabs";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Skeleton } from "@/components/ui/skeleton";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { formatCurrency, formatNumber } from "@/lib/format";
import type { BonusReportDTO, CategoryDTO, ProductFamilyDTO, ProductVariantDTO, ReportByCategoryDTO, ReportByPeriodDTO, ReportByStoreDTO, ReportFilters, TopItemReportDTO, TopProductReportDTO } from "@/lib/types";

interface ReportsPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
}

type ReportTab = "category" | "period" | "store" | "topItems" | "topProducts" | "bonus";
type RangePreset = "currentMonth" | "lastQuarter" | "currentYear" | "previousYear" | "custom";
type ReportRow = ReportByCategoryDTO | ReportByPeriodDTO | ReportByStoreDTO | TopItemReportDTO | TopProductReportDTO | BonusReportDTO;

const tabs: Array<{ id: ReportTab; label: string }> = [
  { id: "category", label: "Kategorie" },
  { id: "period", label: "Zeitraum" },
  { id: "store", label: "Geschäft" },
  { id: "topItems", label: "Top-Artikel" },
  { id: "topProducts", label: "Top-Produkte" },
  { id: "bonus", label: "Bonus" }
];

const chartColors = ["#2563eb", "#16a34a", "#eab308", "#dc2626", "#7c3aed", "#0891b2", "#ea580c", "#475569"];

export function ReportsPage({ apiClient, hasApiToken }: ReportsPageProps) {
  const [categories, setCategories] = useState<CategoryDTO[]>([]);
  const [families, setFamilies] = useState<ProductFamilyDTO[]>([]);
  const [variants, setVariants] = useState<ProductVariantDTO[]>([]);
  const [tab, setTab] = useState<ReportTab>("category");
  const [preset, setPreset] = useState<RangePreset>("currentMonth");
  const [filters, setFilters] = useState<ReportFilters>(() => ({
    ...rangeFor("currentMonth"),
    groupBy: "month",
    categoryIds: []
  }));
  const [rows, setRows] = useState<ReportRow[]>([]);
  const [loading, setLoading] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadReport = useCallback(async () => {
    if (!hasApiToken) {
      setRows([]);
      setCategories([]);
      setFamilies([]);
      setVariants([]);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const [categoryResponse, familyResponse, variantResponse, reportResponse] = await Promise.all([
        apiClient.categories(false),
        apiClient.productFamilies(),
        apiClient.productVariants(),
        loadTab(apiClient, tab, filters)
      ]);
      setCategories(categoryResponse);
      setFamilies(familyResponse);
      setVariants(variantResponse);
      setRows(reportResponse);
    } catch (loadError) {
      setError(toUserMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [apiClient, filters, hasApiToken, tab]);

  useEffect(() => {
    void loadReport();
  }, [loadReport]);

  const title = useMemo(() => tabs.find((entry) => entry.id === tab)?.label ?? "Report", [tab]);
  const visibleVariants = filters.productFamilyId == null
    ? variants
    : variants.filter((variant) => variant.productFamilyId === filters.productFamilyId);

  function updatePreset(nextPreset: RangePreset) {
    setPreset(nextPreset);
    if (nextPreset !== "custom") {
      setFilters((current) => ({ ...current, ...rangeFor(nextPreset) }));
    }
  }

  function resetFilters() {
    setPreset("currentMonth");
    setFilters({
      ...rangeFor("currentMonth"),
      categoryIds: [],
      groupBy: "month"
    });
  }

  async function exportCsv() {
    setExporting(true);
    setError(null);

    try {
      const type = reportExportType(tab);
      const blob = await apiClient.downloadReportCsv(type, filters);
      downloadBlob(blob, `ebon-report-${type}.csv`);
    } catch (exportError) {
      setError(toUserMessage(exportError));
    } finally {
      setExporting(false);
    }
  }

  if (!hasApiToken) {
    return (
      <Card>
        <CardContent className="flex min-h-72 flex-col items-center justify-center text-center">
          <h2 className="text-base font-semibold">API-Token erforderlich</h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Danach können Reports geladen werden.</p>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      <PageHeader
        actions={(
          <Button disabled={exporting} onClick={exportCsv} size="sm" variant="secondary">
            {exporting ? <Loader2 aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Download aria-hidden="true" className="h-4 w-4" />}
            CSV exportieren
          </Button>
        )}
        context="Analyse"
        description="Ausgaben, Käufe und Bonuswerte mit einem gemeinsamen Filtersatz auswerten."
        title="Reports"
      />

      {error ? (
        <StatusBanner title="Report konnte nicht geladen werden" tone="error">
          {error}
        </StatusBanner>
      ) : null}

      <PageTabs active={tab} onChange={setTab} tabs={tabs} />

      <FilterBar>
          <div className="grid min-w-0 flex-1 gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-[190px_150px_150px_minmax(180px,1fr)_180px_120px]">
            <Field label="Zeitraum">
              <select className={selectClassName} onChange={(event) => updatePreset(event.target.value as RangePreset)} value={preset}>
                <option value="currentMonth">Aktueller Monat</option>
                <option value="lastQuarter">Letztes Quartal</option>
                <option value="currentYear">Aktuelles Jahr</option>
                <option value="previousYear">Vorheriges Jahr</option>
                <option value="custom">Benutzerdefiniert</option>
              </select>
            </Field>
            <Field label="Von">
              <Input
                onChange={(event) => {
                  setPreset("custom");
                  setFilters((current) => ({ ...current, dateFrom: event.target.value || undefined }));
                }}
                type="date"
                value={filters.dateFrom ?? ""}
              />
            </Field>
            <Field label="Bis">
              <Input
                onChange={(event) => {
                  setPreset("custom");
                  setFilters((current) => ({ ...current, dateTo: event.target.value || undefined }));
                }}
                type="date"
                value={filters.dateTo ?? ""}
              />
            </Field>
            <Field label="Kategorien">
              <select
                className={selectClassName}
                multiple
                onChange={(event) => setFilters((current) => ({
                  ...current,
                  categoryIds: Array.from(event.target.selectedOptions).map((option) => Number(option.value))
                }))}
                value={(filters.categoryIds ?? []).map(String)}
              >
                {categories.map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </Field>
            <Field label="Geschäft">
              <Input onChange={(event) => setFilters((current) => ({ ...current, store: event.target.value || undefined }))} placeholder="optional" value={filters.store ?? ""} />
            </Field>
            <Field label="Gruppe">
              <select className={selectClassName} onChange={(event) => setFilters((current) => ({ ...current, groupBy: event.target.value as ReportFilters["groupBy"] }))} value={filters.groupBy ?? "month"}>
                <option value="day">Tag</option>
                <option value="week">Woche</option>
                <option value="month">Monat</option>
                <option value="year">Jahr</option>
              </select>
            </Field>
          </div>
          {tab === "topProducts" ? <div className="w-full sm:w-64"><Field label="Top-Produkte sortieren nach"><select className={selectClassName} onChange={(event) => setFilters((current) => ({ ...current, topProductSort: event.target.value as "total" | "count" }))} value={filters.topProductSort ?? "total"}><option value="total">Ausgaben</option><option value="count">Kaufhäufigkeit</option></select></Field></div> : null}
          <div className="grid w-full gap-3 sm:grid-cols-2 xl:w-auto xl:min-w-[420px]">
            <Field label="Produktfamilie">
              <select
                className={selectClassName}
                onChange={(event) => setFilters((current) => ({
                  ...current,
                  productFamilyId: event.target.value ? Number(event.target.value) : null,
                  productVariantId: null
                }))}
                value={filters.productFamilyId ?? ""}
              >
                <option value="">Alle Produktfamilien</option>
                {families.map((family) => <option key={family.id} value={family.id}>{family.name}</option>)}
              </select>
            </Field>
            <Field label="Produktvariante">
              <select
                className={selectClassName}
                onChange={(event) => setFilters((current) => ({ ...current, productVariantId: event.target.value ? Number(event.target.value) : null }))}
                value={filters.productVariantId ?? ""}
              >
                <option value="">Alle Varianten</option>
                {visibleVariants.map((variant) => <option key={variant.id} value={variant.id}>{variant.name}</option>)}
              </select>
            </Field>
          </div>
          <p aria-live="polite" className="w-full text-xs text-zinc-500 dark:text-zinc-400">
            Aktive Auswertung: {title} · {filters.dateFrom ?? "offen"} bis {filters.dateTo ?? "offen"}
          </p>
      </FilterBar>

      {loading ? (
        <StatusBanner ariaLabel="Report wird geladen" busy title="Report wird geladen">
          Reportdaten werden aktualisiert.
        </StatusBanner>
      ) : !error && rows.length === 0 ? (
        <StatusBanner
          action={<Button onClick={resetFilters} size="sm" variant="secondary">Filter zurücksetzen</Button>}
          ariaLabel="Keine Reportdaten"
          title="Keine Reportdaten"
        >
          Filter anpassen oder zurücksetzen, um andere Ergebnisse anzuzeigen.
        </StatusBanner>
      ) : null}

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(420px,0.9fr)]">
        <Card aria-label="Diagramm" role="region">
          <CardHeader>
            <CardTitle>{title}</CardTitle>
          </CardHeader>
          <CardContent className="h-80">
            {loading ? <Skeleton aria-hidden="true" className="h-full w-full" /> : !error && rows.length ? <ReportChart rows={rows} tab={tab} /> : null}
          </CardContent>
        </Card>

        <section aria-labelledby="report-table-title" className="min-w-0">
          <CardHeader>
            <CardTitle id="report-table-title">Tabelle</CardTitle>
          </CardHeader>
          {loading ? <Card><CardContent><Skeleton aria-hidden="true" className="h-48 w-full" /></CardContent></Card> : !error && rows.length ? (
            <DataTableFrame><ReportTable rows={rows} tab={tab} /></DataTableFrame>
          ) : <Card><CardContent className="min-h-48">{null}</CardContent></Card>}
        </section>
      </section>
    </div>
  );
}

function reportExportType(tab: ReportTab): "by-category" | "by-period" | "by-store" | "top-items" | "top-products" | "bonus" {
  if (tab === "category") {
    return "by-category";
  }
  if (tab === "period") {
    return "by-period";
  }
  if (tab === "store") {
    return "by-store";
  }
  if (tab === "topItems") {
    return "top-items";
  }
  if (tab === "topProducts") {
    return "top-products";
  }
  return "bonus";
}

async function loadTab(apiClient: ApiClient, tab: ReportTab, filters: ReportFilters): Promise<ReportRow[]> {
  switch (tab) {
    case "category":
      return apiClient.reportByCategory(filters);
    case "period":
      return apiClient.reportByPeriod(filters);
    case "store":
      return apiClient.reportByStore(filters);
    case "topItems":
      return apiClient.topItems({ ...filters, size: 20 });
    case "topProducts":
      return apiClient.topProducts({ ...filters, size: 20 });
    case "bonus":
      return apiClient.bonusReport(filters);
  }
}

function ReportChart({ rows, tab }: { rows: ReportRow[]; tab: ReportTab }) {
  if (tab === "category") {
    const data = rows as ReportByCategoryDTO[];
    return (
      <ResponsiveContainer height="100%" minHeight={1} minWidth={1} width="99%">
        <PieChart>
          <Pie data={data} dataKey="total" innerRadius={64} nameKey="categoryName" outerRadius={112}>
            {data.map((entry, index) => <Cell fill={chartColors[index % chartColors.length]} key={`${entry.categoryName}-${entry.total}`} />)}
          </Pie>
          <Tooltip formatter={(value) => formatCurrency(Number(value))} />
        </PieChart>
      </ResponsiveContainer>
    );
  }

  const data = chartData(rows, tab);
  return (
    <ResponsiveContainer height="100%" minHeight={1} minWidth={1} width="99%">
      <BarChart data={data}>
        <XAxis dataKey="label" hide={data.length > 10} />
        <YAxis width={72} />
        <Tooltip formatter={(value) => tab === "bonus" && String(value).includes(".") ? formatCurrency(Number(value)) : formatCurrency(Number(value))} />
        <Bar dataKey="value" fill="#2563eb" radius={[4, 4, 0, 0]} />
      </BarChart>
    </ResponsiveContainer>
  );
}

function ReportTable({ rows, tab }: { rows: ReportRow[]; tab: ReportTab }) {
  const headings = tableHeadings(tab);
  return (
    <table aria-label="Reportdaten" className="w-full min-w-[520px] text-sm">
      <caption className="sr-only">Daten der aktiven Auswertung {tabs.find((entry) => entry.id === tab)?.label}</caption>
      <thead className="border-b border-zinc-200 bg-zinc-50 text-left text-xs uppercase tracking-wide text-zinc-500 dark:border-zinc-800 dark:bg-zinc-900/40 dark:text-zinc-400">
        <tr>
          <th className="px-4 py-3" scope="col">{headings[0]}</th>
          <th className="px-4 py-3 text-right" scope="col">{headings[1]}</th>
          {headings[2] ? <th className="px-4 py-3 text-right" scope="col">{headings[2]}</th> : null}
        </tr>
      </thead>
      <tbody>
        {rows.map((row, index) => {
          const cells = tableCells(row, tab);
          return (
            <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-900" key={index}>
              <td className="px-4 py-3 font-medium">{cells[0]}</td>
              <td className="px-4 py-3 text-right tabular-nums">{cells[1]}</td>
              {headings[2] ? <td className="px-4 py-3 text-right tabular-nums text-zinc-500 dark:text-zinc-400">{cells[2] ?? "–"}</td> : null}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}

function tableHeadings(tab: ReportTab): string[] {
  if (tab === "category") return ["Kategorie", "Ausgaben"];
  if (tab === "period") return ["Zeitraum", "Ausgaben"];
  if (tab === "store") return ["Geschäft", "Ausgaben", "Bons"];
  if (tab === "topItems") return ["Artikel", "Ausgaben", "Käufe"];
  if (tab === "topProducts") return ["Produktfamilie", "Ausgaben", "Käufe"];
  return ["Bonustyp", "Punkte", "Guthaben"];
}

function chartData(rows: ReportRow[], tab: ReportTab): Array<{ label: string; value: number }> {
  return rows.slice(0, 20).map((row) => {
    if (tab === "period") {
      const value = row as ReportByPeriodDTO;
      return { label: value.period, value: value.total };
    }
    if (tab === "store") {
      const value = row as ReportByStoreDTO;
      return { label: value.storeName, value: value.total };
    }
    if (tab === "topItems") {
      const value = row as TopItemReportDTO;
      return { label: value.description, value: value.total };
    }
    if (tab === "topProducts") {
      const value = row as TopProductReportDTO;
      return { label: value.productFamilyName, value: value.total };
    }
    const value = row as BonusReportDTO;
    return { label: value.bonusType, value: value.totalEarnedBalance ?? value.totalPoints ?? 0 };
  });
}

function tableCells(row: ReportRow, tab: ReportTab): string[] {
  if (tab === "category") {
    const value = row as ReportByCategoryDTO;
    return [value.categoryName, formatCurrency(value.total)];
  }
  if (tab === "period") {
    const value = row as ReportByPeriodDTO;
    return [value.period, formatCurrency(value.total)];
  }
  if (tab === "store") {
    const value = row as ReportByStoreDTO;
    return [value.storeName, formatCurrency(value.total), `${formatNumber(value.receiptCount)} Bons`];
  }
  if (tab === "topItems") {
    const value = row as TopItemReportDTO;
    return [value.description, formatCurrency(value.total), `${formatNumber(value.count)}×`];
  }
  if (tab === "topProducts") {
    const value = row as TopProductReportDTO;
    return [value.productFamilyName, formatCurrency(value.total), `${formatNumber(value.count)}×`];
  }
  const value = row as BonusReportDTO;
  return [value.bonusType, `${formatNumber(value.totalPoints)} Punkte`, formatCurrency(value.totalEarnedBalance)];
}

function rangeFor(preset: RangePreset): Pick<ReportFilters, "dateFrom" | "dateTo"> {
  const today = new Date();
  const end = toDateInput(today);
  if (preset === "currentYear") {
    return {
      dateFrom: toDateInput(new Date(today.getFullYear(), 0, 1)),
      dateTo: toDateInput(new Date(today.getFullYear(), 11, 31))
    };
  }
  if (preset === "previousYear") {
    return {
      dateFrom: toDateInput(new Date(today.getFullYear() - 1, 0, 1)),
      dateTo: toDateInput(new Date(today.getFullYear() - 1, 11, 31))
    };
  }
  if (preset === "lastQuarter") {
    const start = new Date(today);
    start.setMonth(start.getMonth() - 3);
    return { dateFrom: toDateInput(start), dateTo: end };
  }
  const start = new Date(today.getFullYear(), today.getMonth(), 1);
  return { dateFrom: toDateInput(start), dateTo: end };
}

function toDateInput(date: Date): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function downloadBlob(blob: Blob, filename: string) {
  const url = URL.createObjectURL(blob);
  const anchor = document.createElement("a");
  anchor.href = url;
  anchor.download = filename;
  anchor.click();
  URL.revokeObjectURL(url);
}

function Field({ children, label }: { children: ReactNode; label: string }) {
  return (
    <label className="block text-sm">
      <span className="mb-1 block text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{label}</span>
      {children}
    </label>
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
