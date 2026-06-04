import type { ReactNode } from "react";

import { cn } from "@/lib/utils";

type BadgeTone = "neutral" | "green" | "yellow" | "red" | "blue";

interface BadgeProps {
  children: ReactNode;
  className?: string;
  tone?: BadgeTone;
}

const tones: Record<BadgeTone, string> = {
  neutral: "bg-zinc-100 text-zinc-700 ring-zinc-200 dark:bg-zinc-900 dark:text-zinc-200 dark:ring-zinc-800",
  green: "bg-emerald-50 text-emerald-700 ring-emerald-200 dark:bg-emerald-950 dark:text-emerald-200 dark:ring-emerald-900",
  yellow: "bg-amber-50 text-amber-700 ring-amber-200 dark:bg-amber-950 dark:text-amber-200 dark:ring-amber-900",
  red: "bg-red-50 text-red-700 ring-red-200 dark:bg-red-950 dark:text-red-200 dark:ring-red-900",
  blue: "bg-sky-50 text-sky-700 ring-sky-200 dark:bg-sky-950 dark:text-sky-200 dark:ring-sky-900"
};

export function Badge({ children, className, tone = "neutral" }: BadgeProps) {
  return (
    <span className={cn("inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ring-1", tones[tone], className)}>
      {children}
    </span>
  );
}
