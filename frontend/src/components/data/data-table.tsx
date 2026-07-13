import type { JSX, ReactNode } from "react";
import { ChevronLeft, ChevronRight } from "lucide-react";

import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { cn } from "@/lib/utils";

export function DataTableFrame({ children, className }: { children: ReactNode; className?: string }): JSX.Element {
  return (
    <Card className={cn("overflow-hidden", className)}>
      <div className="overflow-x-auto">{children}</div>
    </Card>
  );
}

export function PaginationBar({
  onPageChange,
  page,
  totalPages
}: {
  onPageChange: (page: number) => void;
  page: number;
  totalPages: number;
}): JSX.Element {
  const safeTotalPages = Math.max(1, totalPages);
  return (
    <nav aria-label="Seitennavigation" className="flex items-center justify-between gap-3 border-t border-zinc-200 px-4 py-3 dark:border-zinc-800">
      <p className="text-sm text-zinc-600 tabular-nums dark:text-zinc-400">
        Seite {page + 1} von {safeTotalPages}
      </p>
      <div className="flex gap-2">
        <Button aria-label="Vorherige Seite" disabled={page <= 0} onClick={() => onPageChange(page - 1)} size="icon" variant="secondary">
          <ChevronLeft aria-hidden="true" className="h-4 w-4" />
        </Button>
        <Button
          aria-label="Nächste Seite"
          disabled={page + 1 >= safeTotalPages}
          onClick={() => onPageChange(page + 1)}
          size="icon"
          variant="secondary"
        >
          <ChevronRight aria-hidden="true" className="h-4 w-4" />
        </Button>
      </div>
    </nav>
  );
}
