import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { PageTabs } from "@/components/layout/page-tabs";
import { StickyActionBar } from "@/components/layout/sticky-action-bar";
import { SecretInput } from "@/components/ui/secret-input";

describe("shared redesign components", () => {
  it("changes the active page tab through the typed callback", async () => {
    const onChange = vi.fn();
    render(
      <PageTabs
        active="items"
        onChange={onChange}
        tabs={[
          { id: "items", label: "Positionen" },
          { id: "raw", label: "Rohtext" }
        ]}
      />
    );
    await userEvent.click(screen.getByRole("tab", { name: "Rohtext" }));
    expect(onChange).toHaveBeenCalledWith("raw");
  });

  it("keeps save and cancel actions available in the sticky action bar", () => {
    render(<StickyActionBar message="3 Felder geändert" onCancel={vi.fn()} onSave={vi.fn()} saving={false} />);
    expect(screen.getByRole("button", { name: "Abbrechen" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Änderungen speichern" })).toBeInTheDocument();
  });

  it("does not send the masked placeholder as a changed secret", async () => {
    const onChange = vi.fn();
    render(<SecretInput aria-label="API-Key" masked value="********" onChangeValue={onChange} />);
    expect(screen.getByLabelText("API-Key")).toHaveValue("");
    await userEvent.type(screen.getByLabelText("API-Key"), "new-secret");
    expect(onChange).toHaveBeenLastCalledWith("new-secret");
  });
});
