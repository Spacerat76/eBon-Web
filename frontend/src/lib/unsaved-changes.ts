import { useEffect, useRef } from "react";

const dirtyOwners = new Set<symbol>();

export function hasUnsavedChanges(): boolean {
  return dirtyOwners.size > 0;
}

export function useUnsavedChanges(dirty: boolean): void {
  const owner = useRef(Symbol("unsaved-changes"));

  useEffect(() => {
    const token = owner.current;
    if (dirty) dirtyOwners.add(token);
    else dirtyOwners.delete(token);
    return () => {
      dirtyOwners.delete(token);
    };
  }, [dirty]);

  useEffect(() => {
    if (!dirty) return;
    const preventUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = "";
    };
    window.addEventListener("beforeunload", preventUnload);
    return () => window.removeEventListener("beforeunload", preventUnload);
  }, [dirty]);
}
