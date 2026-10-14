import type {
  DirectReturnArrivalPayload,
  DirectArrivalCancellationPayload,
  DirectChangePayload,
  DirectChangeCancellationPayload,
} from "../api/directChangeTypes";
export type DirectChangeInput =
  | {
      operation: "ARRIVAL";
      returnId: number;
      payload: DirectReturnArrivalPayload;
    }
  | {
      operation: "ARRIVAL_CANCEL";
      arrivalId: number;
      payload: DirectArrivalCancellationPayload;
    }
  | { operation: "PRICE_ADJUSTMENT" | "RETURN"; payload: DirectChangePayload }
  | {
      operation: "CANCEL";
      changeId: number;
      payload: DirectChangeCancellationPayload;
    };
export type DirectChangePendingRequest = DirectChangeInput & { key: string };
type RequestStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

// Only unresolved command input is persisted; server data remains in React Query.
export function createDirectChangeRequests(
  createKey: () => string,
  storage: () => RequestStorage | null,
) {
  const memory = new Map<number, DirectChangePendingRequest>();
  const listeners = new Set<() => void>();
  const scope = (id: number) => `greenhouse:direct-change:v1:${id}`;
  const notify = () => listeners.forEach((listener) => listener());
  function read(id: number): DirectChangePendingRequest | null {
    if (memory.has(id)) return memory.get(id)!;
    try {
      const raw = storage()?.getItem(scope(id));
      if (!raw) return null;
      const request = JSON.parse(raw) as DirectChangePendingRequest;
      if (
        !request.key ||
        !request.payload ||
        ![
          "PRICE_ADJUSTMENT",
          "RETURN",
          "CANCEL",
          "ARRIVAL",
          "ARRIVAL_CANCEL",
        ].includes(request.operation) ||
        (request.operation === "CANCEL" &&
          !Number.isSafeInteger(request.changeId)) ||
        (request.operation === "ARRIVAL" &&
          !Number.isSafeInteger(request.returnId)) ||
        (request.operation === "ARRIVAL_CANCEL" &&
          !Number.isSafeInteger(request.arrivalId))
      )
        return null;
      memory.set(id, request);
      return request;
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
    prepare(id: number, input: DirectChangeInput): DirectChangePendingRequest {
      const previous = read(id);
      if (previous) return previous;
      const request = structuredClone({ ...input, key: createKey() });
      memory.set(id, request);
      try {
        storage()?.setItem(scope(id), JSON.stringify(request));
      } catch {
        /* In-memory retry remains available. */
      }
      notify();
      return request;
    },
    complete(id: number, key: string) {
      if (read(id)?.key !== key) return;
      memory.delete(id);
      try {
        storage()?.removeItem(scope(id));
      } catch {
        /* Optional browser storage. */
      }
      notify();
    },
  };
}
