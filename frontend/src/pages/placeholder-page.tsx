import type { ComponentType } from "react";

import { Card, CardContent } from "@/components/ui/card";

interface PlaceholderPageProps {
  icon: ComponentType<{ className?: string }>;
  title: string;
}

export function PlaceholderPage({ icon: Icon, title }: PlaceholderPageProps) {
  return (
    <Card>
      <CardContent className="flex min-h-72 flex-col items-center justify-center gap-3 text-center">
        <span className="flex h-11 w-11 items-center justify-center rounded-md bg-zinc-100 text-zinc-700 dark:bg-zinc-900 dark:text-zinc-200">
          <Icon className="h-5 w-5" />
        </span>
        <div>
          <h2 className="text-base font-semibold text-zinc-950 dark:text-zinc-50">{title}</h2>
          <p className="mt-1 text-sm text-zinc-500 dark:text-zinc-400">In Vorbereitung</p>
        </div>
      </CardContent>
    </Card>
  );
}
