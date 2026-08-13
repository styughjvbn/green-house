"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { RuntimeContext } from "@/shared/api/runtimeContext";

const RuntimeContextValue = createContext<RuntimeContext | null>(null);

export function RuntimeContextProvider({
  children,
  value,
}: {
  children: ReactNode;
  value: RuntimeContext | null;
}) {
  return (
    <RuntimeContextValue.Provider value={value}>
      {children}
    </RuntimeContextValue.Provider>
  );
}

export function useRuntimeContext() {
  const value = useContext(RuntimeContextValue);
  if (!value) {
    throw new Error("농장 업무일자 정보를 불러오지 못했습니다.");
  }
  return value;
}
