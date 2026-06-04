import type { ComponentType, ReactNode } from "react";
import { CircleDollarSign, KeyRound } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { cn } from "@/lib/utils";

export interface NavigationItem {
  href: string;
  label: string;
  icon: ComponentType<{ className?: string }>;
}

interface AppShellProps {
  apiToken: string;
  children: ReactNode;
  navigation: NavigationItem[];
  onTokenChange: (token: string) => void;
  route: string;
}

export function AppShell({ apiToken, children, navigation, onTokenChange, route }: AppShellProps) {
  return (
    <div className="min-h-screen bg-zinc-50 text-zinc-950 dark:bg-zinc-950 dark:text-zinc-50">
      <aside className="fixed inset-y-0 left-0 z-20 hidden w-64 border-r border-zinc-200 bg-white px-4 py-4 dark:border-zinc-800 dark:bg-zinc-950 lg:block">
        <a className="mb-6 flex items-center gap-2 rounded-md px-2 py-2" href="#/">
          <span className="flex h-9 w-9 items-center justify-center rounded-md bg-zinc-950 text-white dark:bg-zinc-50 dark:text-zinc-950">
            <CircleDollarSign className="h-5 w-5" />
          </span>
          <span>
            <span className="block text-sm font-semibold">eBon-Web</span>
            <span className="block text-xs text-zinc-500 dark:text-zinc-400">Ausgaben & Bons</span>
          </span>
        </a>

        <nav className="space-y-1">
          {navigation.map((item) => (
            <NavigationLink
              active={item.href === `#${route}`}
              href={item.href}
              icon={item.icon}
              key={item.href}
              label={item.label}
            />
          ))}
        </nav>
      </aside>

      <div className="lg:pl-64">
        <header className="sticky top-0 z-10 border-b border-zinc-200 bg-white/90 px-4 py-3 backdrop-blur dark:border-zinc-800 dark:bg-zinc-950/90">
          <div className="flex flex-col gap-3 md:flex-row md:items-center md:justify-between">
            <div>
              <p className="text-xs font-medium uppercase text-zinc-500 dark:text-zinc-400">eBon-Web</p>
              <h1 className="text-xl font-semibold tracking-normal text-zinc-950 dark:text-zinc-50">{activeTitle(route, navigation)}</h1>
            </div>
            <form className="flex w-full gap-2 md:w-auto" onSubmit={(event) => event.preventDefault()}>
              <label className="sr-only" htmlFor="api-token">
                API-Token
              </label>
              <div className="relative w-full md:w-80">
                <KeyRound className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-zinc-400" />
                <Input
                  autoComplete="off"
                  className="pl-9"
                  id="api-token"
                  onChange={(event) => onTokenChange(event.target.value)}
                  placeholder="APP_API_TOKEN"
                  type="password"
                  value={apiToken}
                />
              </div>
              <Button onClick={() => onTokenChange("")} variant="secondary">
                Leeren
              </Button>
            </form>
          </div>
        </header>

        <main className="px-4 py-4 pb-24 md:px-6 lg:pb-6">{children}</main>
      </div>

      <nav className="fixed inset-x-0 bottom-0 z-20 grid grid-cols-5 border-t border-zinc-200 bg-white dark:border-zinc-800 dark:bg-zinc-950 lg:hidden">
        {navigation.map((item) => {
          const Icon = item.icon;
          const active = item.href === `#${route}`;
          return (
            <a
              aria-current={active ? "page" : undefined}
              className={cn(
                "flex h-16 flex-col items-center justify-center gap-1 text-xs font-medium",
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
  href,
  icon: Icon,
  label
}: {
  active: boolean;
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
      <Icon className="h-4 w-4" />
      {label}
    </a>
  );
}

function activeTitle(route: string, navigation: NavigationItem[]): string {
  return navigation.find((item) => item.href === `#${route}`)?.label ?? "Dashboard";
}
