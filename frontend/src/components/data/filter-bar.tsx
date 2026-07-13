import type { JSX, ReactNode } from "react";
import { SlidersHorizontal, X } from "lucide-react";

import { cn } from "@/lib/utils";

export function FilterBar({ children, className }: { children: ReactNode; className?: string }): JSX.Element {
  return (
    <div className={cn("flex flex-wrap items-end gap-3 rounded-xl border border-zinc-200 bg-white p-3 shadow-sm dark:border-zinc-800 dark:bg-zinc-950", className)}>
      <SlidersHorizontal aria-hidden="true" className="mb-2 h-4 w-4 shrink-0 text-zinc-500" />
      {children}
    </div>
  );
}

export function ActiveFilterChip({ label, onRemove }: { label: string; onRemove: () => void }): JSX.Element {
  return (
    <span className="inline-flex h-8 items-center gap-1 rounded-full bg-blue-50 pl-3 pr-1.5 text-xs font-medium text-blue-800 dark:bg-blue-950 dark:text-blue-200">
      {label}
      <button aria-label={`${label} entfernen`} className="rounded-full p-1 hover:bg-blue-100 dark:hover:bg-blue-900" onClick={onRemove} type="button">
        <X aria-hidden="true" className="h-3.5 w-3.5" />
      </button>
    </span>
  );
}
