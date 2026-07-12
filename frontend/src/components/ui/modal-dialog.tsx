import { useEffect, useId, useRef, type JSX, type ReactNode } from "react";

export function ModalDialog({ children, open, onClose, title }: {
  children: ReactNode;
  open: boolean;
  onClose: () => void;
  title: string;
}): JSX.Element | null {
  const dialogRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);
  const titleId = useId();
  onCloseRef.current = onClose;

  useEffect(() => {
    if (!open) return;
    const previouslyFocused = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    const dialog = dialogRef.current;
    const selector = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])';
    const focusable = () => Array.from(dialog?.querySelectorAll<HTMLElement>(selector) ?? []);
    (focusable()[0] ?? dialog)?.focus();

    const onKeyDown = (event: KeyboardEvent) => {
      if (event.key === "Escape") {
        event.preventDefault();
        event.stopImmediatePropagation();
        onCloseRef.current();
        return;
      }
      if (event.key !== "Tab") return;
      const elements = focusable();
      const first = elements[0];
      const last = elements.at(-1);
      if (!first || !last) {
        event.preventDefault();
        dialog?.focus();
      } else if (event.shiftKey && (document.activeElement === first || !dialog?.contains(document.activeElement))) {
        event.preventDefault();
        last.focus();
      } else if (!event.shiftKey && (document.activeElement === last || !dialog?.contains(document.activeElement))) {
        event.preventDefault();
        first.focus();
      }
    };
    document.addEventListener("keydown", onKeyDown, true);
    return () => {
      document.removeEventListener("keydown", onKeyDown, true);
      previouslyFocused?.focus();
    };
  }, [open]);

  if (!open) return null;
  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-zinc-950/50 p-4">
      <div aria-labelledby={titleId} aria-modal="true" className="w-full max-w-md rounded-xl border border-zinc-200 bg-white p-5 shadow-xl dark:border-zinc-800 dark:bg-zinc-950" ref={dialogRef} role="dialog" tabIndex={-1}>
        <h2 className="text-lg font-semibold text-zinc-950 dark:text-zinc-50" id={titleId}>{title}</h2>
        {children}
      </div>
    </div>
  );
}
