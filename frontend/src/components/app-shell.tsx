import { useEffect, useRef, useState, type ComponentType, type ReactNode } from "react";
import { Menu, ReceiptText, X } from "lucide-react";

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
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const mobileMenuRef = useRef<HTMLElement>(null);
  const primaryMobileItems = navigation.slice(0, 4);
  const additionalMobileItems = navigation.slice(4);

  useEffect(() => {
    setMobileMenuOpen(false);
  }, [routePath]);

  useEffect(() => {
    if (!mobileMenuOpen) {
      return;
    }

    mobileMenuRef.current?.focus();
    const closeOnEscape = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        setMobileMenuOpen(false);
      }
    };
    document.addEventListener("keydown", closeOnEscape);
    return () => document.removeEventListener("keydown", closeOnEscape);
  }, [mobileMenuOpen]);

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
            headingLevel={1}
            title={activeTitle(routePath, navigation)}
          />
        </div>

        <main className="px-4 py-4 pb-24 md:px-6 lg:pb-6">{children}</main>
      </div>

      {mobileMenuOpen && additionalMobileItems.length > 0 ? (
        <nav
          aria-label="Weitere Navigation"
          className="fixed inset-x-3 bottom-20 z-30 rounded-xl border border-zinc-200 bg-white p-2 shadow-xl dark:border-zinc-800 dark:bg-zinc-950 lg:hidden"
          id="mobile-more-navigation"
          ref={mobileMenuRef}
          tabIndex={-1}
        >
          <div className="grid gap-1">
            {additionalMobileItems.map((item) => (
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
        </nav>
      ) : null}

      <nav
        aria-label="Mobile Navigation"
        className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950 lg:hidden"
      >
        {primaryMobileItems.map((item) => {
          const Icon = item.icon;
          const active = isNavigationActive(item.href, routePath);
          return (
            <a
              aria-current={active ? "page" : undefined}
              className={cn(
                "relative flex h-16 min-w-0 flex-col items-center justify-center gap-1 px-1 text-xs font-medium",
                active ? "text-zinc-950 dark:text-zinc-50" : "text-zinc-500 dark:text-zinc-400"
              )}
              href={item.href}
              key={item.href}
            >
              <Icon className="h-5 w-5" />
              <span>{item.label}</span>
              {item.count === undefined ? null : (
                <span
                  aria-label={`${item.count} offene Aufgaben`}
                  className="absolute right-2 top-1 rounded-full bg-zinc-200 px-1.5 py-0.5 text-[10px] tabular-nums text-zinc-800 dark:bg-zinc-800 dark:text-zinc-200"
                >
                  {item.count}
                </span>
              )}
            </a>
          );
        })}
        <button
          aria-controls="mobile-more-navigation"
          aria-expanded={mobileMenuOpen}
          aria-label={mobileMenuOpen ? "Weitere Navigation schließen" : "Weitere Navigation öffnen"}
          className="flex h-16 min-w-0 flex-col items-center justify-center gap-1 px-1 text-xs font-medium text-zinc-500 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-inset focus-visible:ring-blue-600 dark:text-zinc-400 dark:focus-visible:ring-blue-400"
          onClick={() => setMobileMenuOpen((open) => !open)}
          type="button"
        >
          {mobileMenuOpen ? <X aria-hidden="true" className="h-5 w-5" /> : <Menu aria-hidden="true" className="h-5 w-5" />}
          <span>Mehr</span>
        </button>
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
