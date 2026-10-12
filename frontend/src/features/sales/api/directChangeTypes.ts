import type { components } from "@/shared/api/generated/openapi";
type Schema = components["schemas"];
type State = Required<Schema["DirectSaleChangeResponse"]>;
type Portion = NonNullable<State["portions"]>[number];
type HistoryPage = Required<State["history"]>;
export type DirectPricePortion = Required<Portion>;
export type DirectChangeHistory = Required<
  NonNullable<HistoryPage["content"]>[number]
>;
export type DirectSaleChangeState = Omit<
  State,
  "portions" | "returnedPortions" | "history"
> & {
  portions: DirectPricePortion[];
  returnedPortions: DirectPricePortion[];
  history: Omit<HistoryPage, "content"> & { content: DirectChangeHistory[] };
};
export type DirectChangePayload = Schema["DirectSaleChangeRequest"];
export type DirectChangeCancellationPayload =
  Schema["CancelDirectSaleChangeRequest"];
export type DirectChangeType = NonNullable<DirectChangeHistory["type"]>;
