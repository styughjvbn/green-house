import { fetchApi } from "./client";
import { cache } from "react";

export type RuntimeContext = {
  businessDate: string;
  timeZone: string;
};

export const getRuntimeContext = cache(() =>
  fetchApi<RuntimeContext>("/auth/context"),
);
