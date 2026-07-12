import type { ComponentType, ReactNode } from "react";
import { ReceiptText } from "lucide-react";

import { PageHeader } from "@/components/layout/page-header";
import { cn } from "@/lib/utils";

export interface NavigationItem {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
  group: "workspace" | "manage";
  count?: number;
}

interface AppShellProps {
  children: ReactNode;
  navigation: NavigationItem[];
  route: string;
  utility?: ReactNode;
}

const navigationGroups = [
  { id: "workspace", label: "Arbeitsbereich" },
  { id: "manage", label: "Verwalten" }
] as const;

export function AppShell({ children, navigation, route, utility }: AppShellProps) {
  const routePath = pathFromRoute(route);

  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-950 dark:bg-zinc-950 dark:text-zinc-50">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 border-r border-zinc-200 bg-white px-4 py-4 dark:border-zinc-800 dark:bg-zinc-950 lg:block">
        <a className="mb-6 flex items-center gap-2 rounded-md px-2 py-2" href="#/">
          <span className="flex h-9 w-9 items-center justify-center rounded-md bg-zinc-950 text-white dark:bg-zinc-50 dark:text-zinc-950">
            <ReceiptText className="h-5 w-5" />
          </span>
          <span>
            <span className="block text-sm font-semibold tracking-normal">eBon Expense Tracker</span>
            <span className="block text-xs text-zinc-500 dark:text-zinc-400">Bons, Ausgaben, Kategorien</span>
          </span>
        </a>

        <nav aria-label="Hauptnavigation" className="space-y-6">
          {navigationGroups.map((group) => {
            const items = navigation.filter((item) => item.group === group.id);
            if (items.length === 0) {
              return null;
            }

            return (
              <section aria-labelledby={`navigation-${group.id}`} key={group.id}>
                <h2
                  className="mb-2 px-3 text-xs font-semibold uppercase tracking-wide text-zinc-500 dark:text-zinc-400"
                  id={`navigation-${group.id}`}
                >
                  {group.label}
                </h2>
                <div className="space-y-1">
                  {items.map((item) => (
                    <NavigationLink
                      active={isNavigationActive(item.href, routePath)}
                      count={item.count}
                      href={item.href}
                      icon={item.icon}
                      key={item.href}
                      label={item.label}
                    />
                  ))}
                </div>
              </section>
            );
          })}
        </nav>
      </aside>

      <div className="lg:pl-64">
        <div className="sticky top-0 z-10 border-b border-zinc-200 bg-white/90 px-4 py-3 backdrop-blur dark:border-zinc-800 dark:bg-zinc-950/90 md:px-6">
          <PageHeader
            actions={utility}
            className="items-center sm:items-center"
            context="eBon Expense Tracker"
            title={activeTitle(routePath, navigation)}
          />
        </div>

        <main className="px-4 py-4 pb-24 md:px-6 lg:pb-6">{children}</main>
      </div>

      <nav
        aria-label="Mobile Navigation"
        className="fixed inset-x-0 bottom-0 z-20 flex overflow-x-auto border-t border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950 lg:hidden"
      >
        {navigation.map((item) => {
          const Icon = item.icon;
          const active = isNavigationActive(item.href, routePath);
          return (
            <a
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex h-16 min-w-20 flex-1 flex-col items-center justify-center gap-1 px-2 text-xs font-medium",
                active ? "text-zinc-950 dark:text-zinc-50" : "text-zinc-500 dark:text-zinc-400"
              )}
              href={item.href}
              key={item.href}
            >
              <Icon className="h-5 w-5" />
              <span>{item.label}</span>
            </a>
          );
        })}
      </nav>
    </div>
  );
}

function NavigationLink({
  active,
  count,
  href,
  icon: Icon,
  label
}: {
  active: boolean;
  count?: number;
  href: string;
  icon: ComponentType<{ className?: string }>;
  label: string;
}) {
  return (
    <a
      aria-current={active ? "page" : undefined}
      className={cn(
        "flex items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition",
        active
          ? "bg-zinc-950 text-white dark:bg-zinc-50 dark:text-zinc-950"
          : "text-zinc-600 hover:bg-zinc-100 hover:text-zinc-950 dark:text-zinc-300 dark:hover:bg-zinc-900 dark:hover:text-zinc-50"
      )}
      href={href}
    >
      <Icon className="h-4 w-4 shrink-0" />
      <span className="min-w-0 flex-1">{label}</span>
      {count === undefined ? null : (
        <span
          aria-label={`${count} offene Aufgaben`}
          className="rounded-full bg-zinc-200 px-2 py-0.5 text-xs tabular-nums text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200"
        >
          {count}
        </span>
      )}
    </a>
  );
}

function activeTitle(route: string, navigation: NavigationItem[]): string {
  if (route.startsWith("/receipts/")) {
    return "Bons";
  }

  return navigation.find((item) => item.href === `#${route}`)?.label ?? "Übersicht";
}

function pathFromRoute(route: string): string {
  return route.split("?")[0] || "/";
}

function isNavigationActive(href: string, route: string): boolean {
  if (href === "#/receipts" && route.startsWith("/receipts/")) {
    return true;
  }
  return href === `#${route}`;
}
