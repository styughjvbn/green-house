import type {
  ManualPaymentPayload,
  CancelUnassignedReceiptPayload,
} from "../api/types";

type Input =
  | {
      operation: "RECEIVE";
      payload: Omit<ManualPaymentPayload, "idempotencyKey">;
    }
  | {
      operation: "CANCEL";
      receiptId: number;
      payload: Omit<CancelUnassignedReceiptPayload, "idempotencyKey">;
    };
export type ReceiptRequest =
  | { operation: "RECEIVE"; payload: ManualPaymentPayload }
  | {
      operation: "CANCEL";
      receiptId: number;
      payload: CancelUnassignedReceiptPayload;
    };
type StorageAccess = Pick<Storage, "getItem" | "setItem" | "removeItem">;

export function createReceiptRequests(
  createKey: () => string,
  storage: () => StorageAccess | null,
) {
  const memory = new Map<number, ReceiptRequest>();
  const listeners = new Set<() => void>();
  const scope = (partnerId: number) =>
    `greenhouse:unassigned-receipt:v1:${partnerId}`;
  function read(partnerId: number): ReceiptRequest | null {
    if (memory.has(partnerId)) return memory.get(partnerId)!;
    try {
      const raw = storage()?.getItem(scope(partnerId));
      if (!raw) return null;
      const value = JSON.parse(raw) as ReceiptRequest;
      if (
        !value.payload?.idempotencyKey ||
        !["RECEIVE", "CANCEL"].includes(value.operation) ||
        (value.operation === "CANCEL" && !Number.isSafeInteger(value.receiptId))
      )
        return null;
      memory.set(partnerId, value);
      return value;
    } catch {
      return null;
    }
  }
  return {
    read,
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    prepare(partnerId: number, input: Input): ReceiptRequest {
      const existing = read(partnerId);
      if (existing) return existing;
      const request = structuredClone({
        ...input,
        payload: { ...input.payload, idempotencyKey: createKey() },
      }) as ReceiptRequest;
      memory.set(partnerId, request);
      try {
        storage()?.setItem(scope(partnerId), JSON.stringify(request));
      } catch {
        /* Memory retains input. */
      }
      listeners.forEach((listener) => listener());
      return request;
    },
    complete(partnerId: number, key: string) {
      if (read(partnerId)?.payload.idempotencyKey !== key) return;
      memory.delete(partnerId);
      try {
        storage()?.removeItem(scope(partnerId));
      } catch {
        /* Storage is optional. */
      }
      listeners.forEach((listener) => listener());
    },
  };
}
