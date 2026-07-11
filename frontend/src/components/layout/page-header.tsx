import type { JSX, ReactNode } from "react";

import { cn } from "@/lib/utils";

export function PageHeader({
  actions,
  className,
  context,
  description,
  title
}: {
  actions?: ReactNode;
  className?: string;
  context?: string;
  description?: ReactNode;
  title: string;
}): JSX.Element {
  return (
    <header className={cn("flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between", className)}>
      <div className="min-w-0">
        {context ? <p className="mb-1 text-xs font-medium uppercase tracking-wide text-blue-700 dark:text-blue-300">{context}</p> : null}
        <h1 className="text-2xl font-semibold tracking-tight text-zinc-950 dark:text-zinc-50">{title}</h1>
        {description ? <div className="mt-1 text-sm text-zinc-600 dark:text-zinc-400">{description}</div> : null}
      </div>
      {actions ? <div className="flex shrink-0 flex-wrap items-center gap-2">{actions}</div> : null}
    </header>
  );
}
