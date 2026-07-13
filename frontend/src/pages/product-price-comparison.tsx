import { Download, RotateCcw, TriangleAlert } from "lucide-react";
import { useEffect, useState } from "react";
import { CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { ModalDialog } from "@/components/ui/modal-dialog";
import { StatusBanner } from "@/components/feedback/status-banner";
import type { ApiClient } from "@/lib/api";
import type {
  PageResponse,
  ProductFamilyDTO,
  ProductPriceGrouping,
  ProductPriceObservationDTO,
  ProductPriceParams,
  ProductPriceReportDTO,
  ProductVariantDTO
} from "@/lib/types";

type PriceScope = "family" | "variant";

const selectClass = "h-9 w-full rounded-md border border-zinc-200 bg-white px-3 text-sm text-zinc-900 shadow-sm dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-100";

export function ProductPriceComparison({
  apiClient,
  families,
  variants
}: {
  apiClient: ApiClient;
  families: ProductFamilyDTO[];
  variants: ProductVariantDTO[];
}) {
  const [scope, setScope] = useState<PriceScope>("family");
  const [familyId, setFamilyId] = useState<number | null>(null);
  const [variantId, setVariantId] = useState<number | null>(null);
  const [filters, setFilters] = useState<ProductPriceParams>({ grouping: "STORE", includeExcluded: true });
  const [report, setReport] = useState<ProductPriceReportDTO | null>(null);
  const [observations, setObservations] = useState<PageResponse<ProductPriceObservationDTO> | null>(null);
  const [loading, setLoading] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [messageTone, setMessageTone] = useState<"success" | "error">("success");
  const [excludeItem, setExcludeItem] = useState<ProductPriceObservationDTO | null>(null);
  const [exclusionReason, setExclusionReason] = useState("");

  useEffect(() => {
    if (familyId == null && families[0]) {
      setFamilyId(families[0].id);
    }
    if (variantId == null && variants[0]) {
      setVariantId(variants[0].id);
    }
  }, [families, familyId, variantId, variants]);

  const selectedId = scope === "family" ? familyId : variantId;

  async function load() {
    if (selectedId == null) {
      setReport(null);
      setObservations(null);
      return;
    }
    setLoading(true);
    try {
      const observationParams = { ...filters, page: 0, size: 50 };
      const [nextReport, nextObservations] = scope === "family"
        ? await Promise.all([
            apiClient.productFamilyPrices(selectedId, filters),
            apiClient.productFamilyPriceObservations(selectedId, observationParams)
          ])
        : await Promise.all([
            apiClient.productVariantPrices(selectedId, filters),
            apiClient.productVariantPriceObservations(selectedId, observationParams)
          ]);
      setReport(nextReport);
      setObservations(nextObservations);
    } catch (error) {
      setMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Preisvergleich konnte nicht geladen werden.");
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    void load();
  }, [selectedId, scope]);

  async function exclude() {
    if (!excludeItem || !exclusionReason.trim()) {
      return;
    }
    setLoading(true);
    try {
      await apiClient.excludeProductPriceObservation(excludeItem.receiptItemId, exclusionReason.trim());
      setExcludeItem(null);
      setExclusionReason("");
      setMessageTone("success");
      setMessage("Preisbeobachtung wurde aus dem Vergleich ausgeschlossen.");
      await load();
    } catch (error) {
      setMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Ausschluss konnte nicht gespeichert werden.");
    } finally {
      setLoading(false);
    }
  }

  async function include(observation: ProductPriceObservationDTO) {
    setLoading(true);
    try {
      await apiClient.includeProductPriceObservation(observation.receiptItemId);
      setMessageTone("success");
      setMessage("Preisbeobachtung wird wieder berücksichtigt.");
      await load();
    } catch (error) {
      setMessageTone("error");
      setMessage(error instanceof Error ? error.message : "Wiederaufnahme konnte nicht gespeichert werden.");
    } finally {
      setLoading(false);
    }
  }

  async function downloadCsv() {
    if (selectedId == null) {
      return;
    }
    try {
      const blob = scope === "family"
        ? await apiClient.downloadProductFamilyPricesCsv(selectedId, filters)
        : await apiClient.downloadProductVariantPricesCsv(selectedId, filters);
      const url = URL.createObjectURL(blob);
      const link = document.createElement("a");
      link.href = url;
      link.download = scope === "family" ? "product-family-price-comparison.csv" : "product-variant-price-comparison.csv";
      link.click();
      URL.revokeObjectURL(url);
      setMessageTone("success");
      setMessage("CSV-Export wurde erstellt.");
    } catch (error) {
      setMessageTone("error");
      setMessage(error instanceof Error ? error.message : "CSV-Export konnte nicht erstellt werden.");
    }
  }

  const scopeOptions = scope === "family" ? families : variants;
  const selectedValue = selectedId == null ? "" : String(selectedId);

  return (
    <div className="space-y-4">
      <Card>
        <CardHeader className="gap-3">
          <div className="flex flex-col gap-2 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <CardTitle>Produktpreisvergleich</CardTitle>
              <p className="mt-1 text-sm text-zinc-500">Effektiv gezahlte Preise sind der Standard. Reguläre Preise erscheinen nur, wenn sie sicher ableitbar sind.</p>
            </div>
            <div className="flex gap-2">
              <Button disabled={selectedId == null} onClick={() => void downloadCsv()} size="sm" variant="secondary">
                <Download className="h-4 w-4" /> CSV
              </Button>
              <Button disabled={loading} onClick={() => void load()} size="sm" variant="secondary">
                <RotateCcw className="h-4 w-4" /> Aktualisieren
              </Button>
            </div>
          </div>
          <div className="grid gap-2 md:grid-cols-2 xl:grid-cols-6">
            <select aria-label="Vergleichsebene" className={selectClass} onChange={(event) => setScope(event.target.value as PriceScope)} value={scope}>
              <option value="family">Produktfamilie</option>
              <option value="variant">Produktvariante</option>
            </select>
            <select
              aria-label={scope === "family" ? "Produktfamilie" : "Produktvariante"}
              className={selectClass}
              onChange={(event) => scope === "family" ? setFamilyId(Number(event.target.value)) : setVariantId(Number(event.target.value))}
              value={selectedValue}
            >
              {scopeOptions.map((option) => <option key={option.id} value={option.id}>{option.name}</option>)}
            </select>
            <Input aria-label="Preisvergleich von" className="h-9" onChange={(event) => setFilters((current) => ({ ...current, dateFrom: event.target.value || undefined }))} type="date" value={filters.dateFrom ?? ""} />
            <Input aria-label="Preisvergleich bis" className="h-9" onChange={(event) => setFilters((current) => ({ ...current, dateTo: event.target.value || undefined }))} type="date" value={filters.dateTo ?? ""} />
            <Input aria-label="Preisvergleich Store" className="h-9" onChange={(event) => setFilters((current) => ({ ...current, store: event.target.value || undefined }))} placeholder="Store filtern" value={filters.store ?? ""} />
            <select aria-label="Store-Gruppierung" className={selectClass} onChange={(event) => setFilters((current) => ({ ...current, grouping: event.target.value as ProductPriceGrouping }))} value={filters.grouping ?? "STORE"}>
              <option value="STORE">Nach Geschäft</option>
              <option value="STORE_BRANCH">Nach Geschäft und Filiale</option>
            </select>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <label className="flex items-center gap-2 text-sm text-zinc-700 dark:text-zinc-300">
              <input checked={filters.includeExcluded ?? true} onChange={(event) => setFilters((current) => ({ ...current, includeExcluded: event.target.checked }))} type="checkbox" />
              Ausgeschlossene Beobachtungen anzeigen
            </label>
            <Button disabled={loading} onClick={() => void load()} size="sm">Filter anwenden</Button>
          </div>
        </CardHeader>
      </Card>

      {loading ? <StatusBanner ariaLabel="Preisaktion wird ausgeführt" busy title="Preisaktion wird ausgeführt" /> : null}
      {message ? <StatusBanner title={messageTone === "error" ? "Preisaktion fehlgeschlagen" : "Preisaktion abgeschlossen"} tone={messageTone}>{message}</StatusBanner> : null}

      {report ? <>
        <section className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
          {report.statistics.map((statistics) => <StatisticsCards key={statistics.priceUnit} statistics={statistics} />)}
        </section>

        <section className="grid gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(20rem,1fr)]">
          <Card className="min-w-0">
            <CardHeader><CardTitle>Preisverlauf</CardTitle></CardHeader>
            <CardContent className="min-w-0">
              {report.trend.length ? <div className="h-64 min-w-0"><ResponsiveContainer height={256} minHeight={256} minWidth={1} width="99%"><LineChart data={report.trend}><CartesianGrid strokeDasharray="3 3" /><XAxis dataKey="receiptDate" tick={{ fontSize: 12 }} /><YAxis tick={{ fontSize: 12 }} width={48} /><Tooltip formatter={(value) => value == null ? "-" : formatEuro(Number(value))} /><Line dataKey="price" dot={{ r: 3 }} name="Preis" stroke="#0f766e" strokeWidth={2} type="monotone" /></LineChart></ResponsiveContainer></div> : <EmptyState text="Für die gewählten Filter gibt es keine vergleichbaren Preisbeobachtungen." />}
            </CardContent>
          </Card>
          <Card>
            <CardHeader><CardTitle>Varianten</CardTitle></CardHeader>
            <CardContent className="space-y-3">
              {report.variants.length ? report.variants.map((variant) => <div className="flex items-start justify-between gap-3 text-sm" key={variant.productVariantId}><div><button className="text-left font-medium text-blue-700 hover:underline dark:text-blue-300" onClick={() => { setScope("variant"); setVariantId(variant.productVariantId); }}>{variant.productVariantName}</button><p className="text-xs text-zinc-500">{variant.observationCount} Beobachtungen</p></div><div className="text-right"><div>{formatEuro(variant.latestEffectivePrice)}</div><p className="text-xs text-zinc-500">Minimum {formatEuro(variant.minimumEffectivePrice)}</p></div></div>) : <p className="text-sm text-zinc-500">Diese Detailansicht zeigt den konkreten Positionspreis der Variante.</p>}
            </CardContent>
          </Card>
        </section>

        <Card>
          <CardHeader><CardTitle>Vergleich nach {filters.grouping === "STORE_BRANCH" ? "Geschäft und Filiale" : "Geschäft"}</CardTitle></CardHeader>
          <CardContent className="p-0"><div className="overflow-x-auto"><table className="min-w-[700px] w-full text-left text-sm"><caption className="sr-only">Preisvergleich nach Store mit letztem Preis, Minimum, Durchschnitt, Median und Beobachtungsanzahl</caption><thead className="border-y border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800"><tr><th className="px-4 py-3">Geschäft</th><th className="px-4 py-3">Einheit</th><th className="px-4 py-3 text-right">Letzter Preis</th><th className="px-4 py-3 text-right">Minimum</th><th className="px-4 py-3 text-right">Durchschnitt</th><th className="px-4 py-3 text-right">Median</th><th className="px-4 py-3 text-right">Anzahl</th></tr></thead><tbody>{report.stores.map((store) => <tr className="border-b border-zinc-100 dark:border-zinc-900" key={`${store.label}-${store.priceUnit}`}><td className="px-4 py-3 font-medium">{store.label}</td><td className="px-4 py-3">{displayUnit(store.priceUnit)}</td><td className="px-4 py-3 text-right tabular-nums">{formatPrice(store.latestPrice, store.priceUnit)}</td><td className="px-4 py-3 text-right tabular-nums">{formatPrice(store.minimumPrice, store.priceUnit)}</td><td className="px-4 py-3 text-right tabular-nums">{formatPrice(store.averagePrice, store.priceUnit)}</td><td className="px-4 py-3 text-right tabular-nums">{formatPrice(store.medianPrice, store.priceUnit)}</td><td className="px-4 py-3 text-right tabular-nums">{store.observationCount}</td></tr>)}</tbody></table></div>{report.stores.length === 0 ? <EmptyState text="Keine geeigneten Beobachtungen für diesen Vergleich." /> : null}</CardContent>
        </Card>

        <ObservationsTable loading={loading} observations={observations?.content ?? []} onExclude={setExcludeItem} onInclude={(observation) => void include(observation)} />
      </> : <EmptyState text={families.length ? "Preisvergleich wird geladen." : "Lege zuerst eine Produktfamilie und eine Zuordnung an."} />}

      {excludeItem ? <ExcludeDialog loading={loading} onCancel={() => { setExcludeItem(null); setExclusionReason(""); }} onConfirm={() => void exclude()} onReasonChange={setExclusionReason} reason={exclusionReason} /> : null}
    </div>
  );
}

function StatisticsCards({ statistics }: { statistics: ProductPriceReportDTO["statistics"][number] }) {
  const unit = statistics.priceUnit;
  return <>
    <Metric label={`Letzter Preis (${displayUnit(unit)})`} value={formatPrice(statistics.latestPrice, unit)} />
    <Metric label={`Historisches Minimum (${displayUnit(unit)})`} value={formatPrice(statistics.minimumPrice, unit)} />
    <Metric label="Durchschnitt" value={formatPrice(statistics.averagePrice, unit)} />
    <Metric label="Median" value={formatPrice(statistics.medianPrice, unit)} suffix={`${statistics.observationCount} Beobachtungen`} />
  </>;
}

function Metric({ label, value, suffix }: { label: string; value: string; suffix?: string }) {
  return <Card><CardContent className="p-4"><p className="text-xs font-medium uppercase text-zinc-500">{label}</p><p className="mt-1 text-xl font-semibold text-zinc-950 dark:text-zinc-50">{value}</p>{suffix ? <p className="mt-1 text-xs text-zinc-500">{suffix}</p> : null}</CardContent></Card>;
}

function ObservationsTable({
  loading,
  observations,
  onExclude,
  onInclude
}: {
  loading: boolean;
  observations: ProductPriceObservationDTO[];
  onExclude: (observation: ProductPriceObservationDTO) => void;
  onInclude: (observation: ProductPriceObservationDTO) => void;
}) {
  return <Card><CardHeader><CardTitle>Preisbeobachtungen</CardTitle><p className="text-sm text-zinc-500">Ausreißer und manuelle Ausschlüsse bleiben sichtbar und können jederzeit wieder aufgenommen werden.</p></CardHeader><CardContent className="p-0"><div className="overflow-x-auto"><table className="min-w-[1080px] w-full text-left text-sm"><caption className="sr-only">Auditierbare Preisbeobachtungen mit effektivem, regulärem und normalisiertem Einheitenpreis</caption><thead className="border-y border-zinc-200 text-xs uppercase text-zinc-500 dark:border-zinc-800"><tr><th className="px-4 py-3">Bon</th><th className="px-4 py-3">Position</th><th className="px-4 py-3 text-right">Effektiv</th><th className="px-4 py-3 text-right">Regulär</th><th className="px-4 py-3 text-right">Einheitenpreis</th><th className="px-4 py-3">Status</th><th className="px-4 py-3 text-right">Aktion</th></tr></thead><tbody>{observations.map((observation) => { const actionContext = `Preisbeobachtung ${observation.description} vom ${formatDate(observation.receiptDate)} bei ${observation.storeName ?? "unbekannt"} (Position ${observation.receiptItemId})`; return <tr className="border-b border-zinc-100 align-top dark:border-zinc-900" key={observation.receiptItemId}><td className="px-4 py-3"><button aria-label={`${actionContext} Bon öffnen`} className="text-left text-blue-700 hover:underline dark:text-blue-300" onClick={() => { window.location.hash = `#/receipts/${observation.receiptId}`; }}>{formatDate(observation.receiptDate)}<span className="block text-xs text-zinc-500">{observation.storeName}{observation.storeBranch ? ` · ${observation.storeBranch}` : ""}</span></button></td><td className="px-4 py-3"><div className="max-w-80 font-medium">{observation.description}</div><div className="text-xs text-zinc-500">{observation.productVariantName ?? observation.productFamilyName ?? "Ohne Produkt"}</div></td><td className="px-4 py-3 text-right tabular-nums">{formatEuro(observation.effectivePrice)}</td><td className="px-4 py-3 text-right tabular-nums">{formatEuro(observation.regularPrice)}</td><td className="px-4 py-3 text-right tabular-nums">{observation.normalizedUnitPrice == null ? "-" : formatPrice(observation.normalizedUnitPrice, observation.normalizedUnit)}</td><td className="px-4 py-3"><div className="flex flex-wrap gap-1">{observation.outlier ? <Badge tone="yellow"><TriangleAlert className="mr-1 h-3 w-3" />Ausreißer</Badge> : null}{observation.excluded ? <Badge tone="red">Ausgeschlossen</Badge> : <Badge tone={observation.includedInComparison ? "green" : "neutral"}>{observation.includedInComparison ? "Im Vergleich" : "Nicht geeignet"}</Badge>}</div>{observation.exclusionReason ? <p className="mt-1 max-w-44 text-xs text-zinc-500">{observation.exclusionReason}</p> : null}</td><td className="px-4 py-3 text-right">{observation.excluded ? <Button aria-label={`${actionContext} wieder aufnehmen`} disabled={loading} onClick={() => onInclude(observation)} size="sm" variant="secondary">Wieder aufnehmen</Button> : observation.includedInComparison ? <Button aria-label={`${actionContext} ausschließen`} disabled={loading} onClick={() => onExclude(observation)} size="sm" variant="secondary">Ausschließen</Button> : null}</td></tr>; })}</tbody></table></div>{observations.length === 0 ? <EmptyState text="Keine Preisbeobachtungen für diese Auswahl." /> : null}</CardContent></Card>;
}

function ExcludeDialog({ loading, onCancel, onConfirm, onReasonChange, reason }: { loading: boolean; onCancel: () => void; onConfirm: () => void; onReasonChange: (value: string) => void; reason: string }) {
  return <ModalDialog onClose={onCancel} open title="Preisbeobachtung ausschließen"><div className="mt-2 space-y-4"><p className="text-sm text-zinc-500">Der Ausschluss ändert keine Bon-Daten und kann später rückgängig gemacht werden.</p><Input aria-label="Ausschlussgrund" maxLength={200} onChange={(event) => onReasonChange(event.target.value)} placeholder="Grund für den Ausschluss" value={reason} /><div className="flex justify-end gap-2"><Button onClick={onCancel} size="sm" variant="ghost">Abbrechen</Button><Button disabled={loading || !reason.trim()} onClick={onConfirm} size="sm" variant="danger">Ausschließen</Button></div></div></ModalDialog>;
}

function EmptyState({ text }: { text: string }) {
  return <p className="p-6 text-sm text-zinc-500">{text}</p>;
}

function formatDate(value: string | null): string {
  return value ? new Intl.DateTimeFormat("de-DE").format(new Date(`${value}T00:00:00`)) : "Ohne Datum";
}

function formatEuro(value: number | null): string {
  return value == null ? "-" : new Intl.NumberFormat("de-DE", { style: "currency", currency: "EUR" }).format(value);
}

function formatPrice(value: number | null, unit: string | null): string {
  return value == null ? "-" : `${formatEuro(value)}${unit && unit !== "EUR" ? ` / ${unit}` : ""}`;
}

function displayUnit(unit: string): string {
  return unit === "EUR" ? "Preis" : `EUR/${unit}`;
}
