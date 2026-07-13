import { lazy, Suspense, useEffect, useMemo, useRef, useState } from "react";
import { BarChart3, Boxes, Home, ReceiptText, Search, Settings, SlidersHorizontal, Tags } from "lucide-react";

import { AppShell, type NavigationItem } from "@/components/app-shell";
import { SessionAccess } from "@/components/session-access";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { ApiClient } from "@/lib/api";
import { hasUnsavedChanges } from "@/lib/unsaved-changes";
import { DashboardPage } from "@/pages/dashboard-page";
import { PlaceholderPage } from "@/pages/placeholder-page";

const ReceiptsPage = lazy(() => import("@/pages/receipts-page").then((module) => ({ default: module.ReceiptsPage })));
const ReportsPage = lazy(() => import("@/pages/reports-page").then((module) => ({ default: module.ReportsPage })));
const SearchPage = lazy(() => import("@/pages/search-page").then((module) => ({ default: module.SearchPage })));
const SettingsPage = lazy(() => import("@/pages/settings-page").then((module) => ({ default: module.SettingsPage })));
const ProductsPage = lazy(() => import("@/pages/products-page").then((module) => ({ default: module.ProductsPage })));

const TOKEN_STORAGE_KEY = "ebon.sessionApiToken";
const HISTORY_INDEX_KEY = "ebonIndex";

type PendingNavigation =
  | { kind: "push"; route: string }
  | { kind: "history"; route: string; delta: number };

const navigation: NavigationItem[] = [
  { href: "#/", label: "Übersicht", icon: Home, group: "workspace" },
  { href: "#/receipts", label: "Bons", icon: ReceiptText, group: "workspace" },
  { href: "#/search", label: "Suche", icon: Search, group: "workspace" },
  { href: "#/products", label: "Produkte", icon: Boxes, group: "workspace" },
  { href: "#/reports", label: "Berichte", icon: BarChart3, group: "workspace" },
  { href: "#/settings/categories", label: "Kategorien & Regeln", icon: Tags, group: "manage" },
  { href: "#/settings", label: "Einstellungen", icon: Settings, group: "manage" }
];

export default function App() {
  const [route, setRoute] = useState(() => normalizeHash(window.location.hash));
  const [pendingNavigation, setPendingNavigation] = useState<PendingNavigation | null>(null);
  const acceptedRoute = useRef(route);
  const currentHistoryIndex = useRef(historyIndex(window.history.state) ?? 0);
  const restoringHistory = useRef(false);
  const restoreCandidate = useRef<PendingNavigation | null>(null);
  const allowNextHistoryNavigation = useRef(false);
  const allowedHistoryIndex = useRef<number | null>(null);
  const ignoreNextHashChange = useRef(false);
  const [apiToken, setApiToken] = useState(() => sessionStorage.getItem(TOKEN_STORAGE_KEY) ?? "");
  const routePath = pathFromRoute(route);
  const routeParams = paramsFromRoute(route);
  const selectedReceiptId = receiptIdFromRoute(routePath);

  useEffect(() => {
    if (historyIndex(window.history.state) === null) {
      window.history.replaceState(withHistoryIndex(window.history.state, currentHistoryIndex.current), "", window.location.href);
    }

    const acceptRoute = (nextRoute: string, nextIndex: number) => {
      currentHistoryIndex.current = nextIndex;
      acceptedRoute.current = nextRoute;
      setRoute(nextRoute);
    };

    const onPopState = (event: PopStateEvent) => {
      const nextRoute = normalizeHash(window.location.hash);
      const statedIndex = historyIndex(event.state);
      const nextIndex = statedIndex ?? (allowNextHistoryNavigation.current ? allowedHistoryIndex.current : null) ?? currentHistoryIndex.current;
      ignoreNextHashChange.current = true;

      if (allowNextHistoryNavigation.current) {
        allowNextHistoryNavigation.current = false;
        allowedHistoryIndex.current = null;
        if (historyIndex(event.state) === null) {
          window.history.replaceState(withHistoryIndex(event.state, nextIndex), "", window.location.href);
        }
        acceptRoute(nextRoute, nextIndex);
        setPendingNavigation(null);
        return;
      }

      if (restoringHistory.current) {
        restoringHistory.current = false;
        setPendingNavigation(restoreCandidate.current);
        restoreCandidate.current = null;
        return;
      }

      if (!hasUnsavedChanges()) {
        acceptRoute(nextRoute, nextIndex);
        return;
      }

      const delta = nextIndex - currentHistoryIndex.current;
      if (delta === 0) return;
      restoreCandidate.current = { kind: "history", route: nextRoute, delta };
      restoringHistory.current = true;
      window.history.go(-delta);
    };

    const onHashChange = () => {
      if (ignoreNextHashChange.current) {
        ignoreNextHashChange.current = false;
        return;
      }
      const nextRoute = normalizeHash(window.location.hash);
      if (nextRoute === acceptedRoute.current) return;
      if (hasUnsavedChanges()) {
        restoreCandidate.current = { kind: "history", route: nextRoute, delta: 1 };
        restoringHistory.current = true;
        window.history.back();
        return;
      }
      const nextIndex = currentHistoryIndex.current + 1;
      window.history.replaceState(withHistoryIndex(window.history.state, nextIndex), "", window.location.href);
      acceptRoute(nextRoute, nextIndex);
    };
    window.addEventListener("popstate", onPopState);
    window.addEventListener("hashchange", onHashChange);
    return () => {
      window.removeEventListener("popstate", onPopState);
      window.removeEventListener("hashchange", onHashChange);
    };
  }, []);

  function requestNavigation(href: string) {
    const nextRoute = normalizeHash(href);
    if (nextRoute === acceptedRoute.current) return;
    if (hasUnsavedChanges()) {
      setPendingNavigation({ kind: "push", route: nextRoute });
      return;
    }
    pushRoute(nextRoute);
  }

  function pushRoute(nextRoute: string) {
    const nextIndex = currentHistoryIndex.current + 1;
    window.history.pushState(withHistoryIndex(window.history.state, nextIndex), "", `#${nextRoute}`);
    currentHistoryIndex.current = nextIndex;
    acceptedRoute.current = nextRoute;
    setRoute(nextRoute);
  }

  function confirmNavigation() {
    if (!pendingNavigation) return;
    if (pendingNavigation.kind === "push") {
      setPendingNavigation(null);
      pushRoute(pendingNavigation.route);
      return;
    }
    allowNextHistoryNavigation.current = true;
    allowedHistoryIndex.current = currentHistoryIndex.current + pendingNavigation.delta;
    window.history.go(pendingNavigation.delta);
  }

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
      navigation={navigation}
      onNavigate={requestNavigation}
      route={route}
      utility={<SessionAccess apiToken={apiToken} onTokenChange={handleTokenChange} />}
    >
      <Suspense fallback={<div className="rounded-md border border-zinc-200 bg-white p-4 text-sm text-zinc-500 dark:border-zinc-800 dark:bg-zinc-950 dark:text-zinc-400">Ansicht wird geladen...</div>}>
        {routePath === "/" ? (
          <DashboardPage apiClient={apiClient} hasApiToken={Boolean(apiToken.trim())} />
        ) : routePath === "/receipts" || selectedReceiptId !== null ? (
          <ReceiptsPage
            apiClient={apiClient}
            hasApiToken={Boolean(apiToken.trim())}
            key={selectedReceiptId === null ? "receipt-list" : `receipt-${selectedReceiptId}`}
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
        ) : routePath === "/products" ? (
          <ProductsPage apiClient={apiClient} hasApiToken={Boolean(apiToken.trim())} />
        ) : routePath === "/settings" || routePath === "/settings/categories" ? (
          <SettingsPage
            apiClient={apiClient}
            hasApiToken={Boolean(apiToken.trim())}
            initialSection={settingsSectionFromRoute(routePath, routeParams.get("section"))}
          />
        ) : (
          <PlaceholderPage
            icon={routeIcon(routePath)}
            title={routeTitle(routePath)}
          />
        )}
      </Suspense>
      <ConfirmDialog
        cancelLabel="Hier bleiben"
        confirmLabel="Änderungen verwerfen"
        destructive
        onCancel={() => setPendingNavigation(null)}
        onConfirm={confirmNavigation}
        open={pendingNavigation !== null}
        title="Ungespeicherte Änderungen verwerfen?"
      >
        Beim Wechsel der Ansicht gehen die noch nicht gespeicherten Eingaben verloren.
      </ConfirmDialog>
    </AppShell>
  );
}

function historyIndex(state: unknown): number | null {
  if (!state || typeof state !== "object") return null;
  const value = (state as Record<string, unknown>)[HISTORY_INDEX_KEY];
  return typeof value === "number" ? value : null;
}

function withHistoryIndex(state: unknown, index: number): Record<string, unknown> {
  return { ...(state && typeof state === "object" ? state as Record<string, unknown> : {}), [HISTORY_INDEX_KEY]: index };
}

function settingsSectionFromRoute(routePath: string, section: string | null): "categories" | "rules" | "connections" {
  if (routePath === "/settings/categories") {
    return "categories";
  }
  return section === "categories" || section === "rules" ? section : "connections";
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

  return navigation.find((item) => item.href === `#${route}`)?.label ?? "Übersicht";
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
