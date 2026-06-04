const currencyFormatter = new Intl.NumberFormat("de-DE", {
  style: "currency",
  currency: "EUR"
});

const numberFormatter = new Intl.NumberFormat("de-DE", {
  maximumFractionDigits: 2
});

const dateFormatter = new Intl.DateTimeFormat("de-DE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric"
});

const dateTimeFormatter = new Intl.DateTimeFormat("de-DE", {
  day: "2-digit",
  month: "2-digit",
  year: "numeric",
  hour: "2-digit",
  minute: "2-digit"
});

const timeFormatter = new Intl.DateTimeFormat("de-DE", {
  hour: "2-digit",
  minute: "2-digit"
});

export function formatCurrency(value: number | null | undefined): string {
  return currencyFormatter.format(value ?? 0);
}

export function formatNumber(value: number | null | undefined): string {
  return numberFormatter.format(value ?? 0);
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }

  return dateFormatter.format(new Date(value));
}

export function formatDateTime(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }

  return dateTimeFormatter.format(new Date(value));
}

export function formatTime(value: string | null | undefined): string {
  if (!value) {
    return "-";
  }

  const date = new Date(`1970-01-01T${value}`);
  return Number.isNaN(date.getTime()) ? value : timeFormatter.format(date);
}

export function formatPercent(value: number): string {
  return `${numberFormatter.format(value)} %`;
}
