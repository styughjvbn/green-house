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

export type DirectReturnArrivalPayload = Schema["DirectReturnArrivalRequest"];
export type DirectArrivalCancellationPayload =
  Schema["CancelDirectReturnArrivalRequest"];
export type DirectReturnArrival = Required<
  Schema["DirectReturnArrivalResponse"]
>;
type ArrivalState = Required<Schema["DirectReturnArrivalState"]>;
export type DirectPendingReturn = Required<Schema["PendingReturn"]>;
export type DirectReturnArrivalState = Omit<
  ArrivalState,
  "pendingReturns" | "history"
> & {
  pendingReturns: DirectPendingReturn[];
  history: Omit<Required<ArrivalState["history"]>, "content"> & {
    content: DirectReturnArrival[];
  };
};
