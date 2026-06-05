import { lazy, Suspense, useCallback, useEffect, useMemo, useState } from "react";
import { ArrowDownRight, ArrowUpRight, Loader2, RefreshCw, Tags, WalletCards } from "lucide-react";

import { ParseStatusBadge } from "@/components/receipt-badges";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import type { ApiClient } from "@/lib/api";
import { ApiClientError } from "@/lib/api";
import { formatCurrency, formatDate, formatDateTime, formatNumber, formatPercent } from "@/lib/format";
import type { BonusReportDTO, DashboardDTO, ReportByCategoryDTO, SyncLogDTO, SyncStatusDTO } from "@/lib/types";

const CategoryChart = lazy(() => import("@/components/category-chart").then((module) => ({ default: module.CategoryChart })));

interface DashboardPageProps {
  apiClient: ApiClient;
  hasApiToken: boolean;
}

const chartColors = ["#2563eb", "#16a34a", "#eab308", "#dc2626", "#7c3aed", "#0891b2", "#ea580c", "#475569"];
type DashboardRange = "currentMonth" | "lastQuarter" | "lastYear" | "custom";

export function DashboardPage({ apiClient, hasApiToken }: DashboardPageProps) {
  const [dashboard, setDashboard] = useState<DashboardDTO | null>(null);
  const [categoryData, setCategoryData] = useState<ReportByCategoryDTO[]>([]);
  const [bonusData, setBonusData] = useState<BonusReportDTO[]>([]);
  const [syncLog, setSyncLog] = useState<SyncLogDTO[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  const [syncTriggering, setSyncTriggering] = useState(false);
  const [range, setRange] = useState<DashboardRange>("currentMonth");
  const [customDateFrom, setCustomDateFrom] = useState("");
  const [customDateTo, setCustomDateTo] = useState("");

  const loadDashboard = useCallback(async () => {
    if (!hasApiToken) {
      setDashboard(null);
      setCategoryData([]);
      setBonusData([]);
      setSyncLog([]);
      setError(null);
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const rangeFilter = range === "custom"
        ? { dateFrom: customDateFrom || undefined, dateTo: customDateTo || undefined }
        : rangeFor(range);
      const [dashboardResponse, syncLogResponse, categoryResponse, bonusResponse] = await Promise.all([
        apiClient.dashboard(),
        apiClient.syncLog(0, 5),
        apiClient.reportByCategory(rangeFilter),
        apiClient.bonusReport(rangeFilter)
      ]);
      setDashboard(dashboardResponse);
      setCategoryData(categoryResponse);
      setBonusData(bonusResponse);
      setSyncLog(syncLogResponse.content);
    } catch (loadError) {
      setError(toUserMessage(loadError));
    } finally {
      setLoading(false);
    }
  }, [apiClient, customDateFrom, customDateTo, hasApiToken, range]);

  useEffect(() => {
    void loadDashboard();
  }, [loadDashboard]);

  const monthDelta = useMemo(() => {
    if (!dashboard || dashboard.previousMonthTotal === 0) {
      return dashboard?.currentMonthTotal ? 100 : 0;
    }

    return ((dashboard.currentMonthTotal - dashboard.previousMonthTotal) / dashboard.previousMonthTotal) * 100;
  }, [dashboard]);

  async function triggerSync() {
    setSyncTriggering(true);
    setError(null);

    try {
      await apiClient.triggerSync();
      await loadDashboard();
    } catch (triggerError) {
      setError(toUserMessage(triggerError));
    } finally {
      setSyncTriggering(false);
    }
  }

  if (!hasApiToken) {
    return (
      <Card>
        <CardContent className="flex min-h-72 flex-col items-center justify-center gap-3 text-center">
          <span className="flex h-11 w-11 items-center justify-center rounded-md bg-zinc-100 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-200">
            <WalletCards className="h-5 w-5" />
          </span>
          <div>
            <h2 className="text-base font-semibold text-zinc-950 dark:text-zinc-50">API-Token erforderlich</h2>
            <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">Geschützte Backend-Daten werden danach geladen.</p>
          </div>
        </CardContent>
      </Card>
    );
  }

  return (
    <div className="space-y-4">
      {error ? (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700 dark:border-red-900 dark:bg-red-950 dark:text-red-200">
          {error}
        </div>
      ) : null}

      <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
        <SyncStatusBanner status={dashboard?.lastSyncStatus ?? null} loading={loading} />
        <Button disabled={loading || syncTriggering} onClick={triggerSync}>
          {syncTriggering ? <Loader2 className="h-4 w-4 animate-spin" /> : <RefreshCw className="h-4 w-4" />}
          Sync starten
        </Button>
      </div>

      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-6">
        <KpiCard
          icon={WalletCards}
          loading={loading}
          title="Aktueller Monat"
          value={formatCurrency(dashboard?.currentMonthTotal)}
        />
        <KpiCard
          icon={WalletCards}
          loading={loading}
          title="Vormonat"
          value={formatCurrency(dashboard?.previousMonthTotal)}
        />
        <KpiCard
          icon={WalletCards}
          loading={loading}
          title="Aktuelles Jahr"
          value={formatCurrency(dashboard?.currentYearTotal)}
        />
        <KpiCard
          icon={monthDelta >= 0 ? ArrowUpRight : ArrowDownRight}
          loading={loading}
          tone={monthDelta > 0 ? "red" : "green"}
          title="Delta zum Vormonat"
          value={formatPercent(monthDelta)}
        />
        <KpiCard
          icon={Tags}
          loading={loading}
          onClick={() => {
            window.location.hash = "#/search?uncategorizedOnly=true";
          }}
          title="Ohne Kategorie"
          value={formatNumber(dashboard?.uncategorizedItemsCount)}
        />
        <KpiCard
          icon={WalletCards}
          loading={loading}
          title="Bonus neu"
          value={formatBonusSummary(bonusData)}
        />
      </section>

      <section className="grid gap-4 xl:grid-cols-[minmax(0,1fr)_minmax(420px,0.95fr)]">
        <Card>
          <CardHeader className="space-y-3">
            <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between">
              <CardTitle>Ausgaben nach Kategorie</CardTitle>
              <RangeControls
                customDateFrom={customDateFrom}
                customDateTo={customDateTo}
                onCustomDateFromChange={setCustomDateFrom}
                onCustomDateToChange={setCustomDateTo}
                onRangeChange={setRange}
                range={range}
              />
            </div>
          </CardHeader>
          <CardContent className="min-h-80">
            {loading ? (
              <Skeleton className="h-64 w-full" />
            ) : categoryData.length ? (
              <div className="grid gap-4 lg:grid-cols-[minmax(220px,0.8fr)_minmax(0,1fr)]">
                <div className="h-64">
                  <Suspense fallback={<Skeleton className="h-64 w-full" />}>
                    <CategoryChart colors={chartColors} data={categoryData} />
                  </Suspense>
                </div>
                <div className="overflow-hidden rounded-md border border-zinc-200 dark:border-zinc-800">
                  <table className="w-full text-sm">
                    <tbody>
                      {categoryData.slice(0, 8).map((entry, index) => (
                        <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-900" key={`${entry.categoryId}-${entry.categoryName}`}>
                          <td className="px-3 py-2">
                            <span className="inline-flex items-center gap-2">
                              <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: chartColors[index % chartColors.length] }} />
                              {entry.categoryName}
                            </span>
                          </td>
                          <td className="px-3 py-2 text-right font-medium">{formatCurrency(entry.total)}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              </div>
            ) : (
              <EmptyState text="Keine Monatsdaten" />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Letzte Bons</CardTitle>
          </CardHeader>
          <CardContent className="overflow-x-auto">
            {loading ? (
              <div className="space-y-2">
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
              </div>
            ) : dashboard?.recentReceipts.length ? (
              <table className="w-full min-w-[520px] text-sm">
                <thead>
                  <tr className="border-b border-zinc-100 text-left text-xs uppercase text-zinc-500 dark:border-zinc-900 dark:text-zinc-400">
                    <th className="px-3 py-2 font-medium">Datum</th>
                    <th className="px-3 py-2 font-medium">Geschäft</th>
                    <th className="px-3 py-2 text-right font-medium">Betrag</th>
                    <th className="px-3 py-2 font-medium">Status</th>
                  </tr>
                </thead>
                <tbody>
                  {dashboard.recentReceipts.map((receipt) => (
                    <tr
                      className="cursor-pointer border-b border-zinc-100 last:border-0 hover:bg-zinc-50 dark:border-zinc-900 dark:hover:bg-zinc-900/60"
                      key={receipt.id}
                      onClick={() => {
                        window.location.hash = `#/receipts/${receipt.id}`;
                      }}
                    >
                      <td className="px-3 py-2">{formatDate(receipt.receiptDate)}</td>
                      <td className="px-3 py-2">
                        <div className="font-medium text-zinc-900 dark:text-zinc-100">{receipt.storeName ?? "Unbekannt"}</div>
                        <div className="text-xs text-zinc-500 dark:text-zinc-400">{receipt.storeBranch ?? "-"}</div>
                      </td>
                      <td className="px-3 py-2 text-right font-medium">{formatCurrency(receipt.totalAmount)}</td>
                      <td className="px-3 py-2">
                        <ParseStatusBadge status={receipt.parseStatus} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <EmptyState text="Keine Bons" />
            )}
          </CardContent>
        </Card>
      </section>

      <section className="grid gap-4 xl:grid-cols-2">
        <Card>
          <CardHeader>
            <CardTitle>Bonus neu im Zeitraum</CardTitle>
          </CardHeader>
          <CardContent>
            {bonusData.length ? (
              <div className="space-y-2">
                {bonusData.map((bonus) => (
                  <div className="flex items-center justify-between rounded-md border border-zinc-200 px-3 py-2 text-sm dark:border-zinc-800" key={bonus.bonusType}>
                    <span className="font-medium">{bonus.bonusType}</span>
                    <span className="text-zinc-600 dark:text-zinc-300">
                      {formatNumber(bonus.totalPoints)} Punkte · {formatCurrency(bonus.totalEarnedBalance)}
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <EmptyState text="Keine Bonusdaten" />
            )}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Sync-Log</CardTitle>
          </CardHeader>
          <CardContent>
            {syncLog.length ? (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[480px] text-sm">
                  <tbody>
                    {syncLog.map((log) => (
                      <tr className="border-b border-zinc-100 last:border-0 dark:border-zinc-900" key={log.id}>
                        <td className="px-3 py-2">{formatDateTime(log.startedAt)}</td>
                        <td className="px-3 py-2">
                          <SyncBadge status={log.status} />
                        </td>
                        <td className="px-3 py-2 text-right text-zinc-600 dark:text-zinc-300">
                          +{log.newDocumentsCount} / -{log.removedDocumentsCount}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <EmptyState text="Kein Sync-Log" />
            )}
          </CardContent>
        </Card>
      </section>
    </div>
  );
}

function KpiCard({
  icon: Icon,
  loading,
  onClick,
  title,
  value,
  tone = "neutral"
}: {
  icon: typeof WalletCards;
  loading: boolean;
  onClick?: () => void;
  title: string;
  value: string;
  tone?: "neutral" | "green" | "red";
}) {
  return (
    <Card
      className={onClick ? "cursor-pointer hover:border-zinc-300 dark:hover:border-zinc-700" : undefined}
      onClick={onClick}
    >
      <CardContent className="flex items-center gap-3">
        <span
          className={
            tone === "green"
              ? "flex h-10 w-10 items-center justify-center rounded-md bg-emerald-50 text-emerald-700 dark:bg-emerald-950 dark:text-emerald-200"
              : tone === "red"
                ? "flex h-10 w-10 items-center justify-center rounded-md bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-200"
                : "flex h-10 w-10 items-center justify-center rounded-md bg-zinc-100 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-200"
          }
        >
          <Icon className="h-5 w-5" />
        </span>
        <div className="min-w-0">
          <p className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">{title}</p>
          {loading ? <Skeleton className="mt-1 h-6 w-24" /> : <p className="truncate text-lg font-semibold">{value}</p>}
        </div>
      </CardContent>
    </Card>
  );
}

function RangeControls({
  customDateFrom,
  customDateTo,
  onCustomDateFromChange,
  onCustomDateToChange,
  onRangeChange,
  range
}: {
  customDateFrom: string;
  customDateTo: string;
  onCustomDateFromChange: (value: string) => void;
  onCustomDateToChange: (value: string) => void;
  onRangeChange: (value: DashboardRange) => void;
  range: DashboardRange;
}) {
  return (
    <div className="flex flex-wrap items-center gap-2">
      <select
        className="h-9 rounded-md border border-zinc-200 bg-white px-2 text-sm dark:border-zinc-800 dark:bg-zinc-950"
        onChange={(event) => onRangeChange(event.target.value as DashboardRange)}
        value={range}
      >
        <option value="currentMonth">Aktueller Monat</option>
        <option value="lastQuarter">Letztes Quartal</option>
        <option value="lastYear">Letztes Jahr</option>
        <option value="custom">Benutzerdefiniert</option>
      </select>
      {range === "custom" ? (
        <>
          <input
            className="h-9 rounded-md border border-zinc-200 bg-white px-2 text-sm dark:border-zinc-800 dark:bg-zinc-950"
            onChange={(event) => onCustomDateFromChange(event.target.value)}
            type="date"
            value={customDateFrom}
          />
          <input
            className="h-9 rounded-md border border-zinc-200 bg-white px-2 text-sm dark:border-zinc-800 dark:bg-zinc-950"
            onChange={(event) => onCustomDateToChange(event.target.value)}
            type="date"
            value={customDateTo}
          />
        </>
      ) : null}
    </div>
  );
}

function SyncStatusBanner({ loading, status }: { loading: boolean; status: SyncStatusDTO | null }) {
  if (loading) {
    return (
      <div className="flex min-h-12 flex-1 items-center gap-3 rounded-md border border-zinc-200 bg-white px-4 py-3 text-sm dark:border-zinc-800 dark:bg-zinc-950">
        <Loader2 className="h-4 w-4 animate-spin text-zinc-500" />
        Dashboard wird geladen
      </div>
    );
  }

  const tone = status?.isSyncing ? "yellow" : status?.lastSyncStatus === "FAILED" ? "red" : "green";
  const text = status?.isSyncing
    ? "Sync läuft"
    : status?.lastSyncStatus === "FAILED"
      ? "Letzter Sync fehlgeschlagen"
      : "Sync bereit";

  return (
    <div className="flex min-h-12 flex-1 flex-wrap items-center gap-3 rounded-md border border-zinc-200 bg-white px-4 py-3 text-sm dark:border-zinc-800 dark:bg-zinc-950">
      <SyncBadge status={status?.lastSyncStatus ?? null} syncing={status?.isSyncing ?? false} />
      <span className="font-medium">{text}</span>
      <span className="text-zinc-500 dark:text-zinc-400">Entfernt: {status?.removedDocumentsCount ?? 0}</span>
      <span className="text-zinc-500 dark:text-zinc-400">Fehler: {status?.errorCount ?? 0}</span>
      <span className={tone === "red" ? "text-red-600 dark:text-red-300" : tone === "yellow" ? "text-amber-600 dark:text-amber-300" : "text-zinc-500 dark:text-zinc-400"}>
        {formatDateTime(status?.lastSyncAt)}
      </span>
    </div>
  );
}

function SyncBadge({ status, syncing = false }: { status: SyncStatusDTO["lastSyncStatus"]; syncing?: boolean }) {
  if (syncing) {
    return <Badge tone="yellow">Läuft</Badge>;
  }

  if (status === "SUCCESS") {
    return <Badge tone="green">Erfolgreich</Badge>;
  }

  if (status === "FAILED") {
    return <Badge tone="red">Fehler</Badge>;
  }

  return <Badge>Kein Sync</Badge>;
}

function EmptyState({ text }: { text: string }) {
  return <div className="rounded-md border border-dashed border-zinc-200 px-4 py-8 text-center text-sm text-zinc-500 dark:border-zinc-800 dark:text-zinc-400">{text}</div>;
}

function formatBonusSummary(bonusSummary: BonusReportDTO[]): string {
  if (!bonusSummary.length) {
    return formatCurrency(0);
  }

  const totalBalance = bonusSummary.reduce((sum, entry) => sum + (entry.totalEarnedBalance ?? 0), 0);
  return formatCurrency(totalBalance);
}

function rangeFor(range: DashboardRange): { dateFrom: string; dateTo: string } {
  const today = new Date();
  const end = toDateInput(today);
  if (range === "lastYear") {
    const start = new Date(today);
    start.setFullYear(start.getFullYear() - 1);
    return { dateFrom: toDateInput(start), dateTo: end };
  }
  if (range === "lastQuarter") {
    const start = new Date(today);
    start.setMonth(start.getMonth() - 3);
    return { dateFrom: toDateInput(start), dateTo: end };
  }
  const start = new Date(today.getFullYear(), today.getMonth(), 1);
  return { dateFrom: toDateInput(start), dateTo: end };
}

function toDateInput(date: Date): string {
  return date.toISOString().slice(0, 10);
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
