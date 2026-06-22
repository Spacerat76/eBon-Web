import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CategoryIcon, isKnownCategoryIcon } from "@/lib/category-icons";

describe("category icons", () => {
  it("recognizes only icons from the curated allowlist", () => {
    expect(isKnownCategoryIcon("apple")).toBe(true);
    expect(isKnownCategoryIcon("<svg onload=alert(1)>")).toBe(false);
    expect(isKnownCategoryIcon(null)).toBe(false);
  });

  it("renders a known icon and preserves supplied styling", () => {
    const { container } = render(<CategoryIcon className="text-red-500" icon="apple" />);
    const icon = container.querySelector("svg");

    expect(icon).toHaveClass("lucide-apple");
    expect(icon).toHaveClass("text-red-500");
  });

  it("falls back to the safe tag icon for unknown or absent values", () => {
    const unknown = render(<CategoryIcon icon="untrusted-icon" />);
    expect(unknown.container.querySelector("svg")).toHaveClass("lucide-tag");

    const absent = render(<CategoryIcon icon={null} />);
    expect(absent.container.querySelector("svg")).toHaveClass("lucide-tag");
  });
});
