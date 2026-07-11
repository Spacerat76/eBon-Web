import type { JSX, ReactNode } from "react";

import { Button } from "@/components/ui/button";

export function ConfirmDialog({
  cancelLabel = "Abbrechen",
  children,
  confirmDisabled = false,
  confirmLabel = "Bestätigen",
  destructive = false,
  onCancel,
  onConfirm,
  open,
  title
}: {
  cancelLabel?: string;
  children?: ReactNode;
  confirmDisabled?: boolean;
  confirmLabel?: string;
  destructive?: boolean;
  onCancel: () => void;
  onConfirm: () => void;
  open: boolean;
  title: string;
}): JSX.Element | null {
  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/50 p-4">
      <div aria-modal="true" className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-5 shadow-xl dark:border-zinc-800 dark:bg-zinc-950" role="dialog">
        <h2 className="text-lg font-semibold text-zinc-950 dark:text-zinc-50">{title}</h2>
        {children ? <div className="mt-2 text-sm text-zinc-600 dark:text-zinc-400">{children}</div> : null}
        <div className="mt-5 flex justify-end gap-2">
          <Button onClick={onCancel} variant="secondary">
            {cancelLabel}
          </Button>
          <Button disabled={confirmDisabled} onClick={onConfirm} variant={destructive ? "danger" : "primary"}>
            {confirmLabel}
          </Button>
        </div>
      </div>
    </div>
  );
}
