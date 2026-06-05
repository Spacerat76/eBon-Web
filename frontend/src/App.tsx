import { lazy, Suspense, useEffect, useMemo, useState } from "react";
import { BarChart3, Home, ReceiptText, Search, Settings, SlidersHorizontal } from "lucide-react";

import { AppShell, type NavigationItem } from "@/components/app-shell";
import { ApiClient } from "@/lib/api";
import { DashboardPage } from "@/pages/dashboard-page";
import { PlaceholderPage } from "@/pages/placeholder-page";

const ReceiptsPage = lazy(() => import("@/pages/receipts-page").then((module) => ({ default: module.ReceiptsPage })));
const ReportsPage = lazy(() => import("@/pages/reports-page").then((module) => ({ default: module.ReportsPage })));
const SearchPage = lazy(() => import("@/pages/search-page").then((module) => ({ default: module.SearchPage })));
const SettingsPage = lazy(() => import("@/pages/settings-page").then((module) => ({ default: module.SettingsPage })));

const TOKEN_STORAGE_KEY = "ebon.sessionApiToken";

const navigation: NavigationItem[] = [
  { href: "#/", label: "Dashboard", icon: Home },
  { href: "#/receipts", label: "Bons", icon: ReceiptText },
  { href: "#/search", label: "Suche", icon: Search },
  { href: "#/reports", label: "Reports", icon: BarChart3 },
  { href: "#/settings", label: "Einstellungen", icon: Settings }
];

export default function App() {
  const [route, setRoute] = useState(() => normalizeHash(window.location.hash));
  const [apiToken, setApiToken] = useState(() => sessionStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const routePath = pathFromRoute(route);
  const routeParams = paramsFromRoute(route);
  const selectedReceiptId = receiptIdFromRoute(routePath);

  useEffect(() => {
    const onHashChange = () => setRoute(normalizeHash(window.location.hash));
    window.addEventListener("hashchange", onHashChange);
    return () => window.removeEventListener("hashchange", onHashChange);
  }, []);

  const apiClient = useMemo(() => new ApiClient(() => apiToken.trim() || null), [apiToken]);

  function handleTokenChange(nextToken: string) {
    setApiToken(nextToken);

    if (nextToken.trim()) {
      sessionStorage.setItem(TOKEN_STORAGE_KEY, nextToken.trim());
    } else {
      sessionStorage.removeItem(TOKEN_STORAGE_KEY);
    }
  }

  return (
    <AppShell
      apiToken={apiToken}
      navigation={navigation}
      onTokenChange={handleTokenChange}
      route={route}
    >
      <Suspense fallback={<div className="rounded-md border border-zinc-200 bg-white p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-400">Ansicht wird geladen...</div>}>
        {routePath === "/" ? (
          <DashboardPage apiClient={apiClient} hasApiToken={Boolean(apiToken.trim())} />
        ) : routePath === "/receipts" || selectedReceiptId !== null ? (
          <ReceiptsPage
            apiClient={apiClient}
            hasApiToken={Boolean(apiToken.trim())}
            selectedReceiptId={selectedReceiptId}
          />
        ) : routePath === "/search" ? (
          <SearchPage
            apiClient={apiClient}
            hasApiToken={Boolean(apiToken.trim())}
            initialUncategorizedOnly={routeParams.get("uncategorizedOnly") === "true"}
          />
        ) : routePath === "/reports" ? (
          <ReportsPage apiClient={apiClient} hasApiToken={Boolean(apiToken.trim())} />
        ) : routePath === "/settings" ? (
          <SettingsPage apiClient={apiClient} hasApiToken={Boolean(apiToken.trim())} />
        ) : (
          <PlaceholderPage
            icon={routeIcon(routePath)}
            title={routeTitle(routePath)}
          />
        )}
      </Suspense>
    </AppShell>
  );
}

function normalizeHash(hash: string): string {
  const route = hash.replace(/^#/, "") || "/";
  return route.startsWith("/") ? route : `/${route}`;
}

function pathFromRoute(route: string): string {
  return route.split("?")[0] || "/";
}

function paramsFromRoute(route: string): URLSearchParams {
  const query = route.includes("?") ? route.slice(route.indexOf("?") + 1) : "";
  return new URLSearchParams(query);
}

function routeTitle(route: string): string {
  if (route === "/receipts" || route.startsWith("/receipts/")) {
    return "Bons";
  }

  return navigation.find((item) => item.href === `#${route}`)?.label ?? "Dashboard";
}

function routeIcon(route: string) {
  if (route === "/receipts" || route.startsWith("/receipts/")) {
    return ReceiptText;
  }

  return navigation.find((item) => item.href === `#${route}`)?.icon ?? SlidersHorizontal;
}

function receiptIdFromRoute(route: string): number | null {
  const match = /^\/receipts\/(\d+)$/.exec(route);
  return match ? Number(match[1]) : null;
}
