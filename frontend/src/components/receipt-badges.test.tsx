import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { CategorySourceBadge, DeleteReasonBadge, ParseStatusBadge } from "@/components/receipt-badges";

describe("receipt badges", () => {
  it("shows every parse status as a clear German user-facing label", () => {
    render(
      <>
        <ParseStatusBadge status="PARSED" />
        <ParseStatusBadge status="PARSE_ERROR" />
        <ParseStatusBadge status="MANUALLY_EDITED" />
        <ParseStatusBadge status="PENDING" />
      </>
    );

    expect(screen.getByText("Geparst")).toBeInTheDocument();
    expect(screen.getByText("Parse-Fehler")).toBeInTheDocument();
    expect(screen.getByText("Bearbeitet")).toBeInTheDocument();
    expect(screen.getByText("Ausstehend")).toBeInTheDocument();
  });

  it("distinguishes rule, AI, and manual category origins", () => {
    render(
      <>
        <CategorySourceBadge source="RULE" />
        <CategorySourceBadge source="AI" />
        <CategorySourceBadge source="MANUAL" />
      </>
    );

    expect(screen.getByText("Regel")).toBeInTheDocument();
    expect(screen.getByText("KI")).toBeInTheDocument();
    expect(screen.getByText("Manuell")).toBeInTheDocument();
  });

  it("explains whether a deleted receipt lost its Paperless tag or was deleted manually", () => {
    render(
      <>
        <DeleteReasonBadge reason="TAG_REMOVED" />
        <DeleteReasonBadge reason="USER_DELETED" />
      </>
    );

    expect(screen.getByText("Tag entfernt")).toBeInTheDocument();
    expect(screen.getByText("Gelöscht")).toBeInTheDocument();
  });
});
