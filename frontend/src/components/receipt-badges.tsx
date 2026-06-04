import { Badge } from "@/components/ui/badge";
import type { CategorySource, DeleteReason, ParseStatus } from "@/lib/types";

export function ParseStatusBadge({ status }: { status: ParseStatus }) {
  if (status === "PARSED") {
    return <Badge tone="green">Geparst</Badge>;
  }

  if (status === "PARSE_ERROR") {
    return <Badge tone="red">Parse-Fehler</Badge>;
  }

  if (status === "MANUALLY_EDITED") {
    return <Badge tone="blue">Bearbeitet</Badge>;
  }

  return <Badge tone="yellow">Ausstehend</Badge>;
}

export function CategorySourceBadge({ source }: { source: CategorySource }) {
  if (source === "MANUAL") {
    return <Badge tone="blue">Manuell</Badge>;
  }

  if (source === "AI") {
    return <Badge tone="yellow">KI</Badge>;
  }

  return <Badge tone="green">Regel</Badge>;
}

export function DeleteReasonBadge({ reason }: { reason: DeleteReason }) {
  if (reason === "TAG_REMOVED") {
    return <Badge tone="yellow">Tag entfernt</Badge>;
  }

  return <Badge tone="red">Gelöscht</Badge>;
}
