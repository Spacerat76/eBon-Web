import type { JSX, ReactNode } from "react";
import { AlertCircle, CheckCircle2, Info, TriangleAlert } from "lucide-react";

import { cn } from "@/lib/utils";

type StatusBannerTone = "info" | "success" | "warning" | "error";

const tones: Record<StatusBannerTone, string> = {
  info: "border-blue-200 bg-blue-50 text-blue-950 dark:border-blue-900 dark:bg-blue-950/40 dark:text-blue-100",
  success: "border-emerald-200 bg-emerald-50 text-emerald-950 dark:border-emerald-900 dark:bg-emerald-950/40 dark:text-emerald-100",
  warning: "border-amber-200 bg-amber-50 text-amber-950 dark:border-amber-900 dark:bg-amber-950/40 dark:text-amber-100",
  error: "border-red-200 bg-red-50 text-red-950 dark:border-red-900 dark:bg-red-950/40 dark:text-red-100"
};

const icons = {
  info: Info,
  success: CheckCircle2,
  warning: TriangleAlert,
  error: AlertCircle
};

export function StatusBanner({
  action,
  children,
  className,
  title,
  tone = "info"
}: {
  action?: ReactNode;
  children?: ReactNode;
  className?: string;
  title: string;
  tone?: StatusBannerTone;
}): JSX.Element {
  const Icon = icons[tone];
  return (
    <div className={cn("flex items-start gap-3 rounded-xl border p-4", tones[tone], className)} role={tone === "error" ? "alert" : "status"}>
      <Icon aria-hidden="true" className="mt-0.5 h-5 w-5 shrink-0" />
      <div className="min-w-0 flex-1">
        <p className="text-sm font-semibold">{title}</p>
        {children ? <div className="mt-1 text-sm opacity-80">{children}</div> : null}
      </div>
      {action ? <div className="shrink-0">{action}</div> : null}
    </div>
  );
}
