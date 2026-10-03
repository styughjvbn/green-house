"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { RuntimeContext } from "@/shared/api/runtimeContext";
import type { AppEnvironment } from "@/shared/config/appEnvironment";

type ApplicationRuntimeContext = {
  appEnvironment: AppEnvironment;
  businessContext: RuntimeContext | null;
};

const RuntimeContextValue = createContext<ApplicationRuntimeContext | null>(
  null,
);

export function RuntimeContextProvider({
  appEnvironment,
  children,
  value,
}: {
  appEnvironment: AppEnvironment;
  children: ReactNode;
  value: RuntimeContext | null;
}) {
  return (
    <RuntimeContextValue.Provider
      value={{ appEnvironment, businessContext: value }}
    >
      {children}
    </RuntimeContextValue.Provider>
  );
}

export function useRuntimeContext() {
  const value = useContext(RuntimeContextValue);
  if (!value?.businessContext) {
    throw new Error("농장 업무일자 정보를 불러오지 못했습니다.");
  }
  return value.businessContext;
}

export function useAppEnvironment() {
  const value = useContext(RuntimeContextValue);
  if (!value) {
    throw new Error("애플리케이션 실행 환경 정보를 불러오지 못했습니다.");
  }
  return value.appEnvironment;
}
