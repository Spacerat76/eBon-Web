import { describe, expect, it } from "vitest";

import {
  formatCurrency,
  formatDate,
  formatDateTime,
  formatDateTimeParts,
  formatNumber,
  formatPercent,
  formatTime
} from "@/lib/format";

describe("format helpers", () => {
  it("formats amounts with German number formatting and safe zero defaults", () => {
    expect(formatCurrency(12.5)).toContain("12,50");
    expect(formatCurrency(12.5)).toContain("€");
    expect(formatCurrency(null)).toContain("0,00");
    expect(formatNumber(1234.567)).toBe("1.234,57");
    expect(formatNumber(undefined)).toBe("0");
    expect(formatPercent(12.5)).toBe("12,5 %");
  });

  it("uses placeholders for absent dates and preserves invalid values for correction", () => {
    expect(formatDate(null)).toBe("-");
    expect(formatDateTime(undefined)).toBe("-");
    expect(formatDateTimeParts(null)).toEqual({ date: "-", time: "" });
    expect(formatDateTimeParts("not-a-date")).toEqual({ date: "not-a-date", time: "" });
  });

  it("formats valid dates and times while retaining invalid time input", () => {
    expect(formatDate("2026-06-22T12:00:00")).toMatch(/22\.06\.2026/);
    expect(formatDateTime("2026-06-22T17:42:00")).toMatch(/22\.06\.2026.*17:42/);
    expect(formatDateTimeParts("2026-06-22T17:42:00")).toEqual({ date: "22.06.2026", time: "17:42" });
    expect(formatTime(null)).toBe("-");
    expect(formatTime("17:42:00")).toBe("17:42");
    expect(formatTime("invalid")).toBe("invalid");
  });
});
