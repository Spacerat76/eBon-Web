import type { JSX, ReactNode } from "react";

import { Button } from "@/components/ui/button";
import { ModalDialog } from "@/components/ui/modal-dialog";

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
  return (
    <ModalDialog onClose={onCancel} open={open} title={title}>
        {children ? <div className="mt-2 text-sm text-zinc-600 dark:text-zinc-400">{children}</div> : null}
        <div className="mt-5 flex justify-end gap-2">
          <Button onClick={onCancel} variant="secondary">
            {cancelLabel}
          </Button>
          <Button disabled={confirmDisabled} onClick={onConfirm} variant={destructive ? "danger" : "primary"}>
            {confirmLabel}
          </Button>
        </div>
    </ModalDialog>
  );
}
