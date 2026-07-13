import type { JSX } from "react";

import { cn } from "@/lib/utils";

export interface PageTab<T extends string> {
  id: T;
  label: string;
  count?: number;
}

export function PageTabs<T extends string>({
  active,
  onChange,
  tabs
}: {
  active: T;
  onChange: (id: T) => void;
  tabs: PageTab<T>[];
}): JSX.Element {
  return (
    <div className="flex gap-1 overflow-x-auto border-b border-zinc-200 dark:border-zinc-800" role="tablist">
      {tabs.map((tab) => {
        const selected = tab.id === active;
        return (
          <button
            aria-selected={selected}
            className={cn(
              "flex min-h-10 shrink-0 items-center gap-2 border-b-2 px-3 text-sm font-medium transition",
              selected
                ? "border-blue-600 text-blue-700 dark:border-blue-400 dark:text-blue-300"
                : "border-transparent text-zinc-600 hover:border-zinc-300 hover:text-zinc-950 dark:text-zinc-400 dark:hover:border-zinc-700 dark:hover:text-zinc-50"
            )}
            key={tab.id}
            onClick={() => onChange(tab.id)}
            role="tab"
            type="button"
          >
            {tab.label}
            {tab.count === undefined ? null : (
              <span aria-hidden="true" className="rounded-full bg-zinc-100 px-2 py-0.5 text-xs tabular-nums dark:bg-zinc-800">
                {tab.count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}
