import { useEffect, useId, useRef, type JSX, type ReactNode } from "react";

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
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCancelRef = useRef(onCancel);
  const titleId = useId();
  onCancelRef.current = onCancel;

  useEffect(() => {
    if (!open) {
      return;
    }

    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const dialog = dialogRef.current;
    const focusableSelector =
      'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const focusableElements = () => Array.from(dialog?.querySelectorAll<HTMLElement>(focusableSelector) ?? []);
    (focusableElements()[0] ?? dialog)?.focus();

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onCancelRef.current();
        return;
      }

      if (event.key !== "Tab") {
        return;
      }

      const elements = focusableElements();
      const first = elements[0];
      const last = elements.at(-1);
      if (!first || !last) {
        event.preventDefault();
        dialog?.focus();
        return;
      }

      const focusIsOutside = !dialog?.contains(document.activeElement);
      if (event.shiftKey && (document.activeElement === first || focusIsOutside)) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || focusIsOutside)) {
        event.preventDefault();
        first.focus();
      }
    };

    document.addEventListener("keydown", handleKeyDown);
    return () => {
      document.removeEventListener("keydown", handleKeyDown);
      previouslyFocused?.focus();
    };
  }, [open]);

  if (!open) {
    return null;
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/50 p-4">
      <div
        aria-labelledby={titleId}
        aria-modal="true"
        className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-5 shadow-xl dark:border-zinc-800 dark:bg-zinc-950"
        ref={dialogRef}
        role="dialog"
        tabIndex={-1}
      >
        <h2 className="text-lg font-semibold text-zinc-950 dark:text-zinc-50" id={titleId}>
          {title}
        </h2>
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
