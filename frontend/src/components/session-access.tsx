import { useState, type JSX } from "react";
import { KeyRound, ShieldCheck } from "lucide-react";

import { Button } from "@/components/ui/button";
import { ConfirmDialog } from "@/components/ui/confirm-dialog";
import { Input } from "@/components/ui/input";

export function SessionAccess({
  apiToken,
  onTokenChange
}: {
  apiToken: string;
  onTokenChange: (token: string) => void;
}): JSX.Element {
  const [open, setOpen] = useState(false);
  const [draftToken, setDraftToken] = useState("");
  const hasToken = Boolean(apiToken.trim());

  function closeDialog() {
    setDraftToken("");
    setOpen(false);
  }

  function saveToken() {
    const token = draftToken.trim();
    if (!token) {
      return;
    }
    onTokenChange(token);
    closeDialog();
  }

  function removeToken() {
    onTokenChange("");
    closeDialog();
  }

  return (
    <>
      <Button onClick={() => setOpen(true)} size="sm" variant={hasToken ? "secondary" : "primary"}>
        {hasToken ? <ShieldCheck className="h-4 w-4" /> : <KeyRound className="h-4 w-4" />}
        {hasToken ? "API-Zugriff aktiv" : "API-Zugriff einrichten"}
      </Button>

      <ConfirmDialog
        confirmDisabled={!draftToken.trim()}
        confirmLabel="Für diese Sitzung verwenden"
        onCancel={closeDialog}
        onConfirm={saveToken}
        open={open}
        title="API-Zugriff"
      >
        <p>Der Token bleibt ausschließlich in dieser Browsersitzung gespeichert.</p>
        <label className="mt-4 block font-medium text-zinc-950 dark:text-zinc-50" htmlFor="session-api-token">
          APP_API_TOKEN
        </label>
        <Input
          autoComplete="off"
          className="mt-1"
          id="session-api-token"
          onChange={(event) => setDraftToken(event.target.value)}
          type="password"
          value={draftToken}
        />
        <Button className="mt-4" onClick={removeToken} size="sm" variant="ghost">
          Token entfernen
        </Button>
      </ConfirmDialog>
    </>
  );
}
