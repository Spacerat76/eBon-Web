import type { HTMLAttributes, ReactNode } from "react";

import { cn } from "@/lib/utils";

export function Card({ children, className, ...props }: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return (
    <section
      className={cn("rounded-lg border border-zinc-200 bg-white shadow-sm dark:border-zinc-800 dark:bg-zinc-950", className)}
      {...props}
    >
      {children}
    </section>
  );
}

export function CardHeader({ children, className, ...props }: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return (
    <div className={cn("border-b border-zinc-100 px-4 py-3 dark:border-zinc-900", className)} {...props}>
      {children}
    </div>
  );
}

export function CardTitle({ children, className, ...props }: HTMLAttributes<HTMLHeadingElement> & { children: ReactNode }) {
  return (
    <h2 className={cn("text-sm font-semibold text-zinc-950 dark:text-zinc-50", className)} {...props}>
      {children}
    </h2>
  );
}

export function CardContent({ children, className, ...props }: HTMLAttributes<HTMLDivElement> & { children: ReactNode }) {
  return (
    <div className={cn("p-4", className)} {...props}>
      {children}
    </div>
  );
}
