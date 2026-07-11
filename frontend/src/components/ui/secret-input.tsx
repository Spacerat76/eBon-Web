import { useEffect, useState, type InputHTMLAttributes, type JSX } from "react";

import { Input } from "@/components/ui/input";

export function SecretInput({
  masked,
  onChangeValue,
  placeholder,
  type = "password",
  value,
  ...props
}: Omit<InputHTMLAttributes<HTMLInputElement>, "onChange" | "value"> & {
  masked: boolean;
  onChangeValue: (value: string) => void;
  value: string;
}): JSX.Element {
  const unchanged = masked && value === "********";
  const displayValue = unchanged ? "" : value;
  const [draft, setDraft] = useState(displayValue);

  useEffect(() => {
    setDraft(displayValue);
  }, [displayValue]);

  return (
    <Input
      {...props}
      onChange={(event) => {
        const nextValue = event.currentTarget.value;
        setDraft(nextValue);
        onChangeValue(nextValue);
      }}
      placeholder={unchanged ? "Unverändert" : placeholder}
      type={type}
      value={draft}
    />
  );
}
