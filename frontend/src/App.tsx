import { useEffect, useMemo, useState } from "react";
import { BarChart3, Home, ReceiptText, Search, Settings, SlidersHorizontal } from "lucide-react";

import { AppShell, type NavigationItem } from "@/components/app-shell";
import { ApiClient } from "@/lib/api";
import { DashboardPage } from "@/pages/dashboard-page";
import { PlaceholderPage } from "@/pages/placeholder-page";

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
      {route === "/" ? (
        <DashboardPage apiClient={apiClient} hasApiToken={Boolean(apiToken.trim())} />
      ) : (
        <PlaceholderPage
          icon={routeIcon(route)}
          title={routeTitle(route)}
        />
      )}
    </AppShell>
  );
}

function normalizeHash(hash: string): string {
  const route = hash.replace(/^#/, "") || "/";
  return route.startsWith("/") ? route : `/${route}`;
}

function routeTitle(route: string): string {
  return navigation.find((item) => item.href === `#${route}`)?.label ?? "Dashboard";
}

function routeIcon(route: string) {
  return navigation.find((item) => item.href === `#${route}`)?.icon ?? SlidersHorizontal;
}
