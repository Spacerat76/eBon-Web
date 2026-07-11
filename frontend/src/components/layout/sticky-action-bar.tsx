import type { JSX } from "react";
import { LoaderCircle, Save } from "lucide-react";

import { Button } from "@/components/ui/button";

export function StickyActionBar({
  message,
  onCancel,
  onSave,
  saveDisabled = false,
  saving
}: {
  message: string;
  onCancel: () => void;
  onSave: () => void;
  saveDisabled?: boolean;
  saving: boolean;
}): JSX.Element {
  return (
    <div className="sticky bottom-4 z-30 flex flex-col gap-3 rounded-xl border border-zinc-200 bg-white/95 p-3 shadow-lg backdrop-blur sm:flex-row sm:items-center sm:justify-between dark:border-zinc-800 dark:bg-zinc-950/95">
      <p aria-live="polite" className="text-sm font-medium text-zinc-700 dark:text-zinc-300">
        {message}
      </p>
      <div className="flex items-center justify-end gap-2">
        <Button disabled={saving} onClick={onCancel} variant="secondary">
          Abbrechen
        </Button>
        <Button disabled={saveDisabled || saving} onClick={onSave}>
          {saving ? <LoaderCircle aria-hidden="true" className="h-4 w-4 animate-spin" /> : <Save aria-hidden="true" className="h-4 w-4" />}
          {saving ? "Wird gespeichert …" : "Änderungen speichern"}
        </Button>
      </div>
    </div>
  );
}
