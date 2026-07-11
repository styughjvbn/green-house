"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import type { OrchidGroup } from "@/entities/farm/types";

type OrchidClipboardContextValue = {
  copiedOrchidGroup: OrchidGroup | null;
  pasteSourceOrchidGroup: OrchidGroup | null;
  copyOrchidGroup: (orchidGroup: OrchidGroup) => void;
  clearCopiedOrchidGroup: () => void;
  clearPasteSource: () => void;
  openPaste: () => boolean;
};

const OrchidClipboardContext =
  createContext<OrchidClipboardContextValue | null>(null);

export function OrchidClipboardProvider({ children }: { children: ReactNode }) {
  const [copiedOrchidGroup, setCopiedOrchidGroup] =
    useState<OrchidGroup | null>(null);
  const [pasteSourceOrchidGroup, setPasteSourceOrchidGroup] =
    useState<OrchidGroup | null>(null);

  const copyOrchidGroup = useCallback((orchidGroup: OrchidGroup) => {
    setCopiedOrchidGroup(orchidGroup);
    setPasteSourceOrchidGroup(null);
  }, []);

  const clearCopiedOrchidGroup = useCallback(() => {
    setCopiedOrchidGroup(null);
    setPasteSourceOrchidGroup(null);
  }, []);

  const clearPasteSource = useCallback(() => {
    setPasteSourceOrchidGroup(null);
  }, []);

  const openPaste = useCallback(() => {
    if (!copiedOrchidGroup) {
      return false;
    }
    setPasteSourceOrchidGroup(copiedOrchidGroup);
    return true;
  }, [copiedOrchidGroup]);

  const value = useMemo(
    () => ({
      copiedOrchidGroup,
      pasteSourceOrchidGroup,
      copyOrchidGroup,
      clearCopiedOrchidGroup,
      clearPasteSource,
      openPaste,
    }),
    [
      copiedOrchidGroup,
      pasteSourceOrchidGroup,
      copyOrchidGroup,
      clearCopiedOrchidGroup,
      clearPasteSource,
      openPaste,
    ],
  );

  return (
    <OrchidClipboardContext.Provider value={value}>
      {children}
    </OrchidClipboardContext.Provider>
  );
}

export function useOrchidClipboard() {
  const context = useContext(OrchidClipboardContext);
  if (!context) {
    throw new Error(
      "useOrchidClipboard must be used within OrchidClipboardProvider",
    );
  }
  return context;
}
