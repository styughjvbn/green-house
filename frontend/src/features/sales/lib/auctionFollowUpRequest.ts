import type {
  AuctionArrivalCancellationPayload,
  AuctionArrivalPayload,
  AuctionFollowUpPayload,
} from "../api/types";

type WithKey<T> = T & { idempotencyKey: string };
export type AuctionFollowUpRequest =
  | { operation: "FOLLOW_UP"; payload: WithKey<AuctionFollowUpPayload> }
  | { operation: "ARRIVAL"; payload: WithKey<AuctionArrivalPayload> }
  | {
      operation: "ARRIVAL_CANCEL";
      arrivalId: number;
      payload: WithKey<AuctionArrivalCancellationPayload>;
    };
export type AuctionFollowUpInput =
  | {
      operation: "FOLLOW_UP";
      payload: Omit<AuctionFollowUpPayload, "idempotencyKey">;
    }
  | {
      operation: "ARRIVAL";
      payload: Omit<AuctionArrivalPayload, "idempotencyKey">;
    }
  | {
      operation: "ARRIVAL_CANCEL";
      arrivalId: number;
      payload: Omit<AuctionArrivalCancellationPayload, "idempotencyKey">;
    };
type StorageAccess = Pick<Storage, "getItem" | "setItem" | "removeItem">;

// An unresolved command keeps both its identity and its exact input across reloads.
export function createAuctionFollowUpRequests(
  createKey: () => string,
  storage: () => StorageAccess | null,
) {
  const listeners = new Set<() => void>();
  const notify = () => listeners.forEach((listener) => listener());
  const memory = new Map<number, AuctionFollowUpRequest>();
  const scope = (lotId: number) => `greenhouse:auction-follow-up:v1:${lotId}`;
  function read(lotId: number): AuctionFollowUpRequest | null {
    if (memory.has(lotId)) return memory.get(lotId)!;
    try {
      const raw = storage()?.getItem(scope(lotId));
      if (!raw) return null;
      const value = JSON.parse(raw) as AuctionFollowUpRequest;
      if (
        !["FOLLOW_UP", "ARRIVAL", "ARRIVAL_CANCEL"].includes(value.operation) ||
        !value.payload?.idempotencyKey ||
        (value.operation === "ARRIVAL_CANCEL" &&
          !Number.isSafeInteger(value.arrivalId))
      )
        return null;
      memory.set(lotId, value);
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
    prepare(
      lotId: number,
      input: AuctionFollowUpInput,
    ): AuctionFollowUpRequest {
      const existing = read(lotId);
      if (existing) return existing;
      const request = structuredClone({
        ...input,
        payload: { ...input.payload, idempotencyKey: createKey() },
      }) as AuctionFollowUpRequest;
      memory.set(lotId, request);
      try {
        storage()?.setItem(scope(lotId), JSON.stringify(request));
      } catch {
        /* Same screen retains the input. */
      }
      notify();
      return request;
    },
    complete(lotId: number, key: string) {
      if (read(lotId)?.payload.idempotencyKey !== key) return;
      memory.delete(lotId);
      try {
        storage()?.removeItem(scope(lotId));
      } catch {
        /* Browser storage is optional. */
      }
      notify();
    },
  };
}
