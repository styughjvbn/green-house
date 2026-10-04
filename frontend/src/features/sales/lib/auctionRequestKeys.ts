type AuctionCommand = "RESULT" | "RETURN";
type RequestStorage = Pick<Storage, "getItem" | "setItem" | "removeItem">;

// Transport identities are local state. Keep them until the API acknowledges the operation.
export function createAuctionRequestKeys(
  createKey: () => string,
  storage: () => RequestStorage | null = () => null,
) {
  const pending = new Map<string, string>();
  const scope = (lotId: number, command: AuctionCommand) =>
    `greenhouse:auction-request:v1:${lotId}:${command}`;

  return {
    get(lotId: number, command: AuctionCommand) {
      const identity = scope(lotId, command);
      let key = pending.get(identity);
      if (key == null) {
        try {
          key = storage()?.getItem(identity) ?? createKey();
          storage()?.setItem(identity, key);
        } catch {
          // Storage may be disabled; keep retries safe for the current mounted screen.
          key ??= createKey();
        }
        pending.set(identity, key);
      }
      return key;
    },
    complete(lotId: number, command: AuctionCommand, key: string) {
      const identity = scope(lotId, command);
      if (pending.get(identity) === key) pending.delete(identity);
      try {
        if (storage()?.getItem(identity) === key)
          storage()?.removeItem(identity);
      } catch {
        // The in-memory identity has already been acknowledged.
      }
    },
  };
}
